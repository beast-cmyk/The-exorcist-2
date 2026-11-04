package com.possessedgrin.haunt;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.PossessedGrinMod;
import com.possessedgrin.stage.StageDirector;
import com.possessedgrin.stage.Stages;
import com.possessedgrin.voice.AiVoice;
import com.possessedgrin.voice.Conversation;
import com.possessedgrin.voice.Feelings;
import com.possessedgrin.voice.Intent;
import com.possessedgrin.voice.Witness;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The full rite of exorcism, from the demon's side.
 * <ul>
 *   <li><b>The arrival:</b> stay in a body for a while in a village after dark, and one night an old priest comes
 *       out of the fog at the edge of the village with a lantern in his hand, and walks toward you.</li>
 *   <li><b>The rite:</b> he and a younger priest begin. A bar fills as they pray; holy water fills it faster.
 *       When it is full you are cast out.</li>
 *   <li><b>Faith:</b> each priest has faith, shown on the bar. Your taunts, the secrets you pull out of their
 *       heads, the head turn, the bile, telekinesis, your true face, a dead mother's voice: each one breaks a
 *       little of it. A priest with shaking faith prays weakly; a priest whose faith breaks runs. Break both and
 *       the rite fails.</li>
 *   <li><b>"Take me instead":</b> near the end, the younger priest steps forward and offers himself. Jump into him
 *       (K) and the old priest's faith shatters; he walks away into the dark, and the rite is over. Nobody dies.</li>
 * </ul>
 * Every word is original.
 */
public final class Rite {
    public static final String OLD_TAG = "possessedgrin_old_exorcist";
    public static final String YOUNG_TAG = "possessedgrin_young_priest";
    /** A priest whose faith is gone for good: he is just a frightened old man now, not an exorcist. */
    public static final String SHATTERED_TAG = "possessedgrin_shattered";
    private static final String OLD_NAME = "Father Ambrose Hale";
    private static final String YOUNG_NAME = "Father Tomas Reyna";

    private static final int MIN_POSSESSION = 2400;     // two minutes in the body before anyone comes
    private static final double PRAY_RANGE = 10.0;
    private static final float OLD_RATE = 0.0022F;      // per half second at full faith
    private static final float YOUNG_RATE = 0.0014F;
    private static final float BURN_BONUS = 0.025F;
    private static final int OFFER_TICKS = 300;
    private static final int COOLDOWN_AFTER = 12000;

    private static final String[] OLD_LINES = {
            "I have met you before. In another place, long ago. You lost then, too.",
            "Look at me. Not at the {host}. At me.",
            "You will tell me nothing I need to hear. Be silent and go.",
            "This body was lent to life, not to you. Give it back.",
            "Your tricks are old. So am I.",
            "Every hour you stay in it, you grow smaller. Leave while you can.",
            "Pray with me, Tomas. Don't listen to it. Pray."};
    private static final String[] YOUNG_LINES = {
            "Depart from this body. Depart from this house.",
            "I command you, in the name of everything you hate: go!",
            "Don't look at its eyes. Don't look...",
            "It's lying. It's lying. Keep going, Father.",
            "We're not afraid of you. You hear me? We're not afraid!"};
    private static final String[] OLD_LINES_SHAKEN = {
            "*takes a long breath, and starts the line again*",
            "Tomas. Tomas, stay with me. Read.",
            "*his hand is shaking; he hides it in his sleeve*",
            "I know what you're doing. It won't work. ...It won't."};
    private static final String[] YOUNG_LINES_SHAKEN = {
            "I can't hear it anymore, I can't, I...",
            "*his voice has gone thin and high* Depart. Please, just depart.",
            "Father, how does it know these things? How does it know?",
            "*stops, and has to find his place in the book again*"};
    /** Who the priests are, for the AI. Original characters. */
    private static final String OLD_ROLE = "Father Ambrose Hale, an old priest and exorcist in his seventies. You're tired, your "
            + "heart is weak and you take pills for it, and you have faced something like this once before, long ago, far away; "
            + "it nearly killed you then. You are quiet, patient, dry, sometimes gently funny, and immovable. You don't argue "
            + "with demons, because you know they lie: you keep to the rite and you protect the younger priest beside you. "
            + "Underneath the calm, you are very afraid, and very tired.";
    private static final String YOUNG_ROLE = "Father Tomas Reyna, a young priest. You're clever, exhausted and kind, and "
            + "privately you're no longer sure you believe. You carry guilt about someone you loved and weren't there for at "
            + "the end. You're brave in bursts and raw underneath, and when the demon gets under your skin you lose your "
            + "temper. You look up to the old priest more than you'd ever say.";
    private static final String[] SHAKEN_LINES = {
            "*his voice cracks halfway through the prayer*",
            "*the book shakes in his hands*",
            "...How does it know that?",
            "*he loses his place, and has to start the line again*",
            "Stop it. Stop it!"};
    private static final String[] BROKEN_OLD = {
            "*sits down heavily* I'm too old. God help me, I'm too old for this.",
            "*the lantern slips from his hand* No more. I can't hear any more of it."};
    private static final String[] BROKEN_YOUNG = {
            "I can't. I can't stay in this room with it.",
            "*backs away, white-faced* Don't. Don't use her voice. Don't!"};

    private static final class State {
        final ServerBossBar bar;
        final UUID hostUuid;
        UUID oldPriest;
        UUID youngPriest;
        float oldFaith = 100.0F;
        float youngFaith = 70.0F;
        boolean oldBroken;
        boolean youngBroken;
        long oldShakenAt;
        long youngShakenAt;
        boolean praying;
        float progress;
        boolean offered;
        long offerUntil;
        long nextLine;
        /** What the demon last said, or did, and when: the priests answer it. */
        String lastWords;
        long lastWordsAt;
        String lastDeed;
        long lastDeedAt;
        long oldSpokeAt;
        long youngSpokeAt;

        State(UUID hostUuid) {
            this.hostUuid = hostUuid;
            this.bar = new ServerBossBar(Text.literal("The Rite of Exorcism"), BossBar.Color.WHITE, BossBar.Style.NOTCHED_10);
        }
    }

    /** Per possessing player. Server thread only. */
    private static final Map<UUID, State> RITES = new HashMap<>();
    private static final Map<UUID, Long> ARRIVE_AT = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private Rite() {
    }

    public static void tick(ServerWorld world) {
        long now = world.getTime();
        if (now % 10 != 3) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            MobEntity host = PossessionComponent.getHost(player);
            UUID id = player.getUuid();
            State state = RITES.get(id);
            if (state != null) {
                if (host == null || !host.isAlive() || !host.getUuid().equals(state.hostUuid)) {
                    afterJump(world, player, host, state);
                    continue;
                }
                run(world, player, host, state, now);
                continue;
            }
            if (host == null || !host.isAlive() || Stages.of(host) < Stages.OPPRESSION || now < COOLDOWN.getOrDefault(id, 0L)) {
                ARRIVE_AT.remove(id);
                continue;
            }
            if (Stages.elapsed(host) < MIN_POSSESSION || !world.isNight() || !inVillage(world, host)) {
                continue;
            }
            VillagerEntity waiting = find(world, host, OLD_TAG, 48.0);
            Long arrive = ARRIVE_AT.get(id);
            if (waiting == null && arrive == null) {
                ARRIVE_AT.put(id, now + 300 + world.getRandom().nextInt(500));
                player.sendMessage(Text.literal("Somewhere out past the last houses, a dog starts barking, then stops. Fog is coming in.")
                        .formatted(Formatting.GRAY, Formatting.ITALIC), false);
                continue;
            }
            if (waiting == null && now < arrive) {
                continue;
            }
            ARRIVE_AT.remove(id);
            VillagerEntity old = waiting != null ? waiting : arrive(world, player, host);
            if (old != null) {
                State fresh = new State(host.getUuid());
                fresh.oldPriest = old.getUuid();
                fresh.bar.setPercent(0.0F);
                RITES.put(id, fresh);
            }
        }
        if (now % 1200 == 3) {
            COOLDOWN.values().removeIf(until -> until < now);
        }
    }

    // --- the arrival -----------------------------------------------------------------------------------------

    @Nullable
    private static VillagerEntity arrive(ServerWorld world, ServerPlayerEntity player, MobEntity host) {
        Random random = world.getRandom();
        for (int tries = 0; tries < 10; tries++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 20.0 + random.nextInt(8);
            BlockPos column = new BlockPos(new Vec3d(host.getX() + Math.cos(angle) * distance, host.getY(), host.getZ() + Math.sin(angle) * distance));
            BlockPos ground = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
            if (Math.abs(ground.getY() - host.getY()) > 12 || !world.getBlockState(ground.down()).isSolidBlock(world, ground.down())) {
                continue;
            }
            VillagerEntity old = priest(world, ground, OLD_NAME, OLD_TAG, 5);
            if (old == null) {
                return null;
            }
            fog(world, old, 60);
            world.playSound(null, old.getX(), old.getY(), old.getZ(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.AMBIENT, 0.6F, 0.6F);
            StageDirector.title(player, Text.literal(" ").formatted(Formatting.GRAY),
                    Text.literal("Someone has come for you.").formatted(Formatting.GRAY, Formatting.ITALIC));
            player.sendMessage(Text.literal("Fog rolls in at the edge of the village. An old priest steps out of it with a lantern in his hand, "
                    + "and stands a long while looking up at where you are. Then he starts walking.").formatted(Formatting.GRAY, Formatting.ITALIC), false);
            Witness.seen(old, null, "You saw an old priest come out of the fog with a lantern, walking toward " + PossessedGrin.the(host) + ".");
            return old;
        }
        return null;
    }

    @Nullable
    private static VillagerEntity priest(ServerWorld world, BlockPos at, String name, String tag, int level) {
        VillagerEntity priest = EntityType.VILLAGER.create(world);
        if (priest == null) {
            return null;
        }
        priest.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.getRandom().nextFloat() * 360.0F, 0.0F);
        priest.setVillagerData(priest.getVillagerData().withProfession(VillagerProfession.CLERIC).withLevel(level));
        priest.setExperience(level >= 5 ? 250 : 70);
        priest.setCustomName(Text.literal(name));
        priest.setCustomNameVisible(true);
        priest.equipStack(EquipmentSlot.MAINHAND, new ItemStack(tag.equals(OLD_TAG) ? Items.LANTERN : Items.BOOK));
        priest.setPersistent();
        priest.addScoreboardTag(tag);
        world.spawnEntity(priest);
        return priest;
    }

    private static void fog(ServerWorld world, VillagerEntity at, int count) {
        world.spawnParticles(ParticleTypes.CLOUD, at.getX(), at.getY() + 0.4, at.getZ(), count, 2.0, 0.4, 2.0, 0.005);
    }

    // --- the rite --------------------------------------------------------------------------------------------

    private static void run(ServerWorld world, ServerPlayerEntity player, MobEntity host, State state, long now) {
        VillagerEntity old = entity(world, state.oldPriest);
        VillagerEntity young = entity(world, state.youngPriest);
        Random random = world.getRandom();
        if (!state.praying && (old == null || !old.isAlive())) {
            end(player, state, COOLDOWN_AFTER);     // he never made it
            return;
        }
        if (old == null || !old.isAlive()) {
            state.oldBroken = true;
        }
        if (far(old, host) && (young == null || far(young, host))) {
            end(player, state, 6000);
            player.sendMessage(Text.literal("You've left the priests far behind. The rite falls apart without you.")
                    .formatted(Formatting.GRAY, Formatting.ITALIC), false);
            return;
        }
        if (state.youngPriest != null && (young == null || !young.isAlive())) {
            state.youngBroken = true;
        }

        // Walking up through the village; fog follows the old man
        if (!state.oldBroken && !state.praying) {
            approach(old, host, 4);
            if (random.nextInt(3) == 0) {
                fog(world, old, 6);
            }
            if (old.distanceTo(host) < 9.0) {
                begin(world, player, host, state, old);
            }
        }
        if (!state.praying) {
            updateBar(state);
            return;
        }
        state.bar.addPlayer(player);        // no-op unless you came back after leaving the game

        // Both priests keep close, and keep their eyes on it
        if (!state.oldBroken) {
            approach(old, host, 4);
        }
        if (young != null && !state.youngBroken) {
            approach(young, host, state.offered && now < state.offerUntil ? 1 : 4);
        }

        // "Take me instead"
        if (state.offered && now < state.offerUntil) {
            updateBar(state);
            return;     // everyone is watching the young priest; the prayer stops
        }
        if (state.offered && state.offerUntil > 0 && now >= state.offerUntil) {
            state.offerUntil = 0;
            Invitations.revoke(state.youngPriest);
            if (young != null && !state.youngBroken) {
                AiVoice.say(player, host, young, YOUNG_ROLE, scene(host, state, false),
                        "(You offered yourself to it, and it didn't take you. You step back, shaking, and the rite has to go on.)",
                        "*steps back, shaking* It won't come. It won't let go.", 0);
            }
        }

        // The prayer: each priest adds to the rite, weaker as his faith goes
        float gained = 0.0F;
        if (!state.oldBroken && prays(old, host)) {
            gained += OLD_RATE * weight(state.oldFaith);
        }
        if (young != null && !state.youngBroken && prays(young, host)) {
            gained += YOUNG_RATE * weight(state.youngFaith);
        }
        state.progress = Math.min(1.0F, state.progress + gained);

        // The idol eats at them; faith slowly returns when you leave them be
        boolean idol = DemonIdolItem.carries(player, PossessedGrinMod.DEMON_IDOL);
        if (now - state.oldShakenAt > 200) {
            state.oldFaith = Math.min(100.0F, state.oldFaith + 0.2F);
        }
        if (now - state.youngShakenAt > 200) {
            state.youngFaith = Math.min(100.0F, state.youngFaith + 0.15F);
        }
        if (idol) {
            state.oldFaith -= 0.25F;
            state.youngFaith -= 0.35F;
            if (!state.oldBroken && state.oldFaith <= 0.0F && old != null) {
                broken(player, host, old, state, true);
            }
            if (!state.youngBroken && state.youngFaith <= 0.0F && young != null) {
                broken(player, host, young, state, false);
            }
        }

        if (now >= state.nextLine) {
            state.nextLine = now + 140 + random.nextInt(120);
            boolean oldSpeaks = young == null || state.youngBroken || (!state.oldBroken && random.nextBoolean());
            if (oldSpeaks && !state.oldBroken) {
                riteLine(player, host, old, state, true, now);
            } else if (young != null && !state.youngBroken) {
                riteLine(player, host, young, state, false, now);
            }
            world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.AMBIENT, 0.3F, 0.5F);
        }

        // Near the end, the younger one offers himself
        if (!state.offered && state.progress >= 0.8F && young != null && !state.youngBroken) {
            offer(world, player, host, state, young, now);
        }

        if (state.oldBroken && (state.youngPriest == null || state.youngBroken)) {
            fail(world, player, host, state);
            return;
        }
        if (state.progress >= 1.0F) {
            finish(world, player, host, state, old, young);
            return;
        }
        updateBar(state);
    }

    private static void begin(ServerWorld world, ServerPlayerEntity player, MobEntity host, State state, VillagerEntity old) {
        state.praying = true;
        state.bar.addPlayer(player);
        state.nextLine = world.getTime() + 40;
        VillagerEntity young = find(world, host, YOUNG_TAG, 40.0);
        if (young == null) {
            BlockPos beside = old.getBlockPos().offset(old.getHorizontalFacing().rotateYClockwise());
            young = priest(world, world.getBlockState(beside).isAir() ? beside : old.getBlockPos(), YOUNG_NAME, YOUNG_TAG, 3);
        }
        if (young != null) {
            state.youngPriest = young.getUuid();
        }
        StageDirector.title(player, Text.literal("The Rite of Exorcism").formatted(Formatting.WHITE),
                Text.literal("Break their faith, or be cast out.").formatted(Formatting.GRAY, Formatting.ITALIC));
        player.sendMessage(Text.literal("The old priest opens his book. A younger priest beside him lights a candle with shaking hands. "
                + "They begin.").formatted(Formatting.GRAY, Formatting.ITALIC), false);
        AiVoice.say(player, host, old, OLD_ROLE, scene(host, state, true),
                "(You've just walked into the room and seen it for the first time. You recognise what's in there. Say your first words to it.)",
                "So. It is you. I wondered if it would be.", 10);
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.AMBIENT, 0.7F, 0.5F);
        Haunting.shake(player, 1.0F, 30);
    }

    private static void offer(ServerWorld world, ServerPlayerEntity player, MobEntity host, State state, VillagerEntity young, long now) {
        state.offered = true;
        state.offerUntil = now + OFFER_TICKS;
        Invitations.invite(young);
        AiVoice.say(player, host, young, YOUNG_ROLE, scene(host, state, false),
                "(The rite is nearly done but it's killing everyone in this room, and you can't bear it. You step right up to it and "
                        + "offer it yourself instead: tell it to leave the body and come into you. Say it in your own words, all at once.)",
                "Enough! Leave it be. If you want a body so much, here: come into mine. I'm not afraid of you.", 0);
        StageDirector.title(player, Text.literal("He offers himself").formatted(Formatting.GOLD),
                Text.literal("Jump into " + PossessedGrin.label(young) + " (K), or let the moment pass.").formatted(Formatting.GRAY, Formatting.ITALIC));
        world.playSound(null, young.getX(), young.getY(), young.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 1.0F, 0.7F);
    }

    /** The possession moved, or ended, mid-rite. */
    private static void afterJump(ServerWorld world, ServerPlayerEntity player, @Nullable MobEntity host, State state) {
        VillagerEntity old = entity(world, state.oldPriest);
        if (host != null && state.youngPriest != null && host.getUuid().equals(state.youngPriest)) {
            // He invited you in, and you went
            end(player, state, 24000);
            StageDirector.title(player, Text.literal("Take me instead").formatted(Formatting.DARK_RED),
                    Text.literal("You go where you are invited.").formatted(Formatting.GRAY, Formatting.ITALIC));
            if (old != null && old.isAlive()) {
                old.addScoreboardTag(SHATTERED_TAG);
                AiVoice.say(player, host, old, OLD_ROLE, "The rite is over. The young priest, Tomas, offered himself to the demon, "
                                + "and it went into him. He is standing in front of you with its eyes.",
                        "(Your faith has just shattered. Say the last thing you say before you turn and walk away into the fog.)",
                        "No. Not him. Please, not him...", 10);
                Conversation.speak(player, host, old, "*the old priest's hands fall to his sides. He turns and walks away into the fog without another word.*", 80);
                Conversation.flee(old, host, 24.0, 0.5);
                fog(world, old, 30);
            }
            Witness.seen(host, null, "You saw the young priest offer himself to the demon, and the demon go into him.");
            return;
        }
        end(player, state, host == null ? COOLDOWN_AFTER : 6000);
        if (host != null && old != null && old.isAlive()) {
            player.sendMessage(Text.literal("You slip away from the rite. Behind you, the priests stand over an empty body.")
                    .formatted(Formatting.GRAY, Formatting.ITALIC), false);
        }
    }

    private static void finish(ServerWorld world, ServerPlayerEntity player, MobEntity host, State state,
                               @Nullable VillagerEntity old, @Nullable VillagerEntity young) {
        end(player, state, COOLDOWN_AFTER * 2);
        if (old != null && !state.oldBroken) {
            AiVoice.say(player, host, old, OLD_ROLE, scene(host, state, true),
                    "(It's done. The demon is gone; you felt it go. The body in front of you is just itself again. Say what you say now.)",
                    "It's over. It's gone. ...Open the windows.", 30);
        } else if (young != null) {
            AiVoice.say(player, host, young, YOUNG_ROLE, scene(host, state, false),
                    "(It's done. The demon is gone, and you finished the rite alone. Say what you say now.)",
                    "It's over. I think... I think it's over.", 30);
        }
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.AMBIENT, 1.2F, 0.7F);
        StageDirector.title(player, Text.literal("Cast out").formatted(Formatting.WHITE, Formatting.BOLD),
                Text.literal("The rite is finished. The body is free of you.").formatted(Formatting.GRAY, Formatting.ITALIC));
        Exorcism.castOut(world, host, player);
        Witness.seen(host, null, "You saw the priests cast the demon out of " + PossessedGrin.the(host) + ".");
    }

    private static void fail(ServerWorld world, ServerPlayerEntity player, MobEntity host, State state) {
        end(player, state, COOLDOWN_AFTER);
        StageDirector.title(player, Text.literal("The rite is broken").formatted(Formatting.DARK_RED),
                Text.literal("Their faith is gone. The body is still yours.").formatted(Formatting.RED, Formatting.ITALIC));
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 1.0F, 0.45F);
        Witness.seen(host, null, "You saw the priests break and run from " + PossessedGrin.the(host) + ".");
    }

    private static void end(ServerPlayerEntity player, State state, int cooldown) {
        state.bar.clearPlayers();
        RITES.remove(player.getUuid());
        COOLDOWN.put(player.getUuid(), player.world.getTime() + cooldown);
        if (state.youngPriest != null) {
            Invitations.revoke(state.youngPriest);
        }
    }

    // --- faith -----------------------------------------------------------------------------------------------

    /**
     * Something shook this creature ({@code cause}, in a sentence: what it saw or heard). If it is one of your
     * priests, it costs him faith, and he reacts in his own words ({@code fallback} without the AI; null for a stock
     * line).
     */
    public static void shaken(MobEntity mob, float amount, String cause, @Nullable String fallback) {
        for (Map.Entry<UUID, State> entry : RITES.entrySet()) {
            State state = entry.getValue();
            boolean isOld = mob.getUuid().equals(state.oldPriest) && !state.oldBroken;
            boolean isYoung = mob.getUuid().equals(state.youngPriest) && !state.youngBroken;
            if (!isOld && !isYoung || !state.praying) {
                continue;
            }
            ServerPlayerEntity rider = mob.getServer() == null ? null : mob.getServer().getPlayerManager().getPlayer(entry.getKey());
            MobEntity host = rider == null ? null : PossessionComponent.getHost(rider);
            long now = mob.world.getTime();
            float faith;
            if (isOld) {
                state.oldFaith -= amount;
                state.oldShakenAt = now;
                faith = state.oldFaith;
            } else {
                state.youngFaith -= amount * 1.3F;      // the younger one doubts more easily
                state.youngShakenAt = now;
                faith = state.youngFaith;
            }
            state.lastDeed = cause;
            state.lastDeedAt = now;
            Feelings.feel(mob, Feelings.Emotion.FEAR, amount / 40.0F);
            Feelings.feel(mob, Feelings.Emotion.DREAD, amount / 50.0F);
            if (mob.world instanceof ServerWorld world) {
                world.sendEntityStatus(mob, (byte) 42);
            }
            if (rider == null) {
                return;
            }
            if (faith <= 0.0F) {
                broken(rider, host, mob, state, isOld);
            } else if (now - (isOld ? state.oldSpokeAt : state.youngSpokeAt) > 80) {
                // He reacts, in his own words, unless he has only just spoken
                String stock = fallback != null ? fallback : SHAKEN_LINES[mob.getRandom().nextInt(SHAKEN_LINES.length)];
                if (fallback != null || mob.getRandom().nextInt(2) == 0 || AiVoice.enabled()) {
                    spoke(state, isOld, now);
                    AiVoice.say(rider, host, mob, isOld ? OLD_ROLE : YOUNG_ROLE, scene(host, state, isOld),
                            "(" + cause + " Your faith is now " + faithWord(faith) + ". React, the way you really would.)", stock, 10);
                }
            }
            updateBar(state);
            return;
        }
    }

    /** Every priest that can see the body when it does something terrible. */
    public static void witnessed(MobEntity host, float amount, String cause) {
        for (State state : RITES.values()) {
            if (!state.praying || !host.getUuid().equals(state.hostUuid) || !(host.world instanceof ServerWorld world)) {
                continue;
            }
            for (UUID id : new UUID[]{state.oldPriest, state.youngPriest}) {
                VillagerEntity priest = entity(world, id);
                if (priest != null && priest.canSee(host)) {
                    shaken(priest, amount, cause, null);
                }
            }
        }
    }

    /** The demon spoke (in chat). The priests will answer it in the rite. */
    public static void heard(ServerPlayerEntity player, String message) {
        State state = RITES.get(player.getUuid());
        if (state != null && state.praying) {
            state.lastWords = message;
            state.lastWordsAt = player.world.getTime();
            state.nextLine = Math.min(state.nextLine, player.world.getTime() + 30);     // it won't go unanswered
        }
    }

    /** The demon spoke straight to one of the priests. */
    public static void answer(ServerPlayerEntity player, MobEntity host, MobEntity priest, String message, Intent intent) {
        State state = RITES.get(player.getUuid());
        if (state == null) {
            return;
        }
        boolean isOld = priest.getUuid().equals(state.oldPriest);
        if (intent == Intent.TAUNT) {
            shaken(priest, 10.0F, "It spoke straight to you, cruelly: \"" + message + "\".", null);
            return;
        }
        long now = priest.world.getTime();
        spoke(state, isOld, now);
        state.lastWords = null;
        String fallback = Exorcism.answer(priest, host, intent, priest.getRandom());
        AiVoice.say(player, host, priest, isOld ? OLD_ROLE : YOUNG_ROLE, scene(host, state, isOld),
                "(It spoke straight to you: \"" + message + "\". Answer it, or refuse to listen, as you would.)", fallback, 15);
    }

    /** A line of the rite, in the priest's own words: praying, commanding, steadying the other, or answering it. */
    private static void riteLine(ServerPlayerEntity player, MobEntity host, VillagerEntity priest, State state, boolean isOld, long now) {
        if (now - (isOld ? state.oldSpokeAt : state.youngSpokeAt) < 80) {
            return;
        }
        spoke(state, isOld, now);
        float faith = isOld ? state.oldFaith : state.youngFaith;
        Random random = priest.getRandom();
        String[] stock = faith < 45.0F ? (isOld ? OLD_LINES_SHAKEN : YOUNG_LINES_SHAKEN) : (isOld ? OLD_LINES : YOUNG_LINES);
        String cue;
        if (state.lastWords != null && now - state.lastWordsAt < 400) {
            cue = "(The demon just said, through the body: \"" + state.lastWords + "\". Answer it, or refuse to and go on with the rite.)";
            state.lastWords = null;
        } else if (state.lastDeed != null && now - state.lastDeedAt < 300) {
            cue = "(A moment ago: " + state.lastDeed + " Go on with the rite, and let that show.)";
            state.lastDeed = null;
        } else {
            cue = "(Your next words in the rite: pray, command it to leave, steady your partner, or speak to the one trapped "
                    + "inside. One or two sentences. Don't repeat yourself.)";
        }
        AiVoice.say(player, host, priest, isOld ? OLD_ROLE : YOUNG_ROLE, scene(host, state, isOld), cue, stock[random.nextInt(stock.length)], 0);
    }

    /** What's going on in the room, from one priest's point of view. */
    private static String scene(@Nullable MobEntity host, State state, boolean isOld) {
        String body = host == null ? "a possessed body" : "a possessed " + PossessedGrin.kind(host);
        String partner = isOld
                ? state.youngPriest == null ? "You're alone." : state.youngBroken ? "Tomas, the young priest, broke and ran." : "Young Father Tomas is beside you, faith " + faithWord(state.youngFaith) + "."
                : state.oldBroken ? "Father Ambrose, the old priest, has broken. You're on your own." : "Old Father Ambrose is beside you, faith " + faithWord(state.oldFaith) + ".";
        float faith = isOld ? state.oldFaith : state.youngFaith;
        return "At night, in a village, you are performing the rite of exorcism over " + body + ". The rite is "
                + Math.round(state.progress * 100.0F) + "% done; when it's finished, the demon will be cast out. " + partner
                + " Your own faith: " + faithWord(faith) + ". In the rite you can use a few of the old words (\"depart\", "
                + "\"I command you\"), but keep them short and simple, in your own phrasing, never quoting any book or film. "
                + "When you talk to each other or to it, talk like an ordinary man.";
    }

    private static String faithWord(float faith) {
        return faith >= 75.0F ? "steady" : faith >= 50.0F ? "holding, but strained" : faith >= 25.0F ? "shaking badly; close to breaking"
                : "almost gone; you're barely holding on";
    }

    private static void spoke(State state, boolean isOld, long now) {
        if (isOld) {
            state.oldSpokeAt = now;
        } else {
            state.youngSpokeAt = now;
        }
    }

    private static void broken(ServerPlayerEntity rider, @Nullable MobEntity host, MobEntity priest, State state, boolean isOld) {
        Random random = priest.getRandom();
        String stock;
        if (isOld) {
            state.oldBroken = true;
            stock = BROKEN_OLD[random.nextInt(BROKEN_OLD.length)];
        } else {
            state.youngBroken = true;
            stock = BROKEN_YOUNG[random.nextInt(BROKEN_YOUNG.length)];
        }
        AiVoice.say(rider, host, priest, isOld ? OLD_ROLE : YOUNG_ROLE, scene(host, state, isOld),
                "(" + (state.lastDeed == null ? "It's too much." : state.lastDeed) + " Your faith has just broken. You can't do this "
                        + "any more. Say what breaks out of you as you give up and back away.)", stock, 5);
        priest.addScoreboardTag(SHATTERED_TAG);
        Feelings.feel(priest, Feelings.Emotion.SHAME, 0.7F);
        Feelings.feel(priest, Feelings.Emotion.FEAR, 0.6F);
        if (host != null) {
            Conversation.flee(priest, host, 20.0, 0.75);
        }
        rider.sendMessage(Text.literal(PossessedGrin.label(priest) + "'s faith breaks.").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
        Witness.record(priest, "Your faith broke in front of the demon, and you ran.");
    }

    /** Holy water burned the body: the rite gains ground. */
    public static void burned(ServerPlayerEntity rider) {
        State state = RITES.get(rider.getUuid());
        if (state != null && state.praying) {
            state.progress = Math.min(1.0F, state.progress + BURN_BONUS);
            updateBar(state);
        }
    }

    /** Is a priest offering himself to this player right now? */
    public static boolean offerOpen(UUID rider) {
        State state = RITES.get(rider);
        return state != null && state.offered && state.offerUntil > 0;
    }

    public static boolean isPriest(MobEntity mob) {
        for (State state : RITES.values()) {
            if (mob.getUuid().equals(state.oldPriest) && !state.oldBroken || mob.getUuid().equals(state.youngPriest) && !state.youngBroken) {
                return true;
            }
        }
        return false;
    }

    /** The body this rite is against starts the next possession at stage III when you jump into the priest. */
    public static boolean offeredBy(UUID rider, MobEntity target) {
        State state = RITES.get(rider);
        return state != null && state.offered && state.offerUntil > 0 && target.getUuid().equals(state.youngPriest);
    }

    public static void clear() {
        for (State state : RITES.values()) {
            state.bar.clearPlayers();
        }
        RITES.clear();
        ARRIVE_AT.clear();
        COOLDOWN.clear();
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private static float weight(float faith) {
        if (faith <= 0.0F) {
            return 0.0F;
        }
        return faith < 35.0F ? faith / 70.0F : faith / 100.0F;     // shaking faith, a weak prayer
    }

    private static boolean far(@Nullable VillagerEntity priest, MobEntity host) {
        return priest == null || priest.world != host.world || priest.distanceTo(host) > 64.0F;
    }

    private static boolean prays(VillagerEntity priest, MobEntity host) {
        priest.getLookControl().lookAt(host, 30.0F, 30.0F);
        return priest.distanceTo(host) <= PRAY_RANGE && priest.canSee(host);
    }

    private static void approach(@Nullable VillagerEntity priest, MobEntity host, int within) {
        if (priest == null) {
            return;
        }
        if (priest.distanceTo(host) > within + 1) {
            priest.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(host, 0.6F, within));
        }
    }

    private static void updateBar(State state) {
        state.bar.setPercent(Math.max(0.0F, Math.min(1.0F, state.progress)));
        String faith = "";
        if (state.praying) {
            faith = "   Hale: " + (state.oldBroken ? "broken" : "faith " + Math.max(0, Math.round(state.oldFaith)))
                    + (state.youngPriest == null ? "" : "   Reyna: " + (state.youngBroken ? "broken" : "faith " + Math.max(0, Math.round(state.youngFaith))));
        }
        state.bar.setName(Text.literal("The Rite of Exorcism" + faith));
        state.bar.setColor(state.progress >= 0.8F ? BossBar.Color.YELLOW : BossBar.Color.WHITE);
    }

    private static boolean inVillage(ServerWorld world, MobEntity host) {
        return world.getEntitiesByClass(VillagerEntity.class, host.getBoundingBox().expand(32.0), v -> v != host && !v.isBaby()).size() >= 3;
    }

    @Nullable
    private static VillagerEntity find(ServerWorld world, MobEntity host, String tag, double range) {
        for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class, host.getBoundingBox().expand(range),
                v -> v.isAlive() && v.getScoreboardTags().contains(tag) && !v.getScoreboardTags().contains(SHATTERED_TAG)
                        && !PossessedGrin.isPossessed(v))) {
            return villager;
        }
        return null;
    }

    @Nullable
    private static VillagerEntity entity(ServerWorld world, @Nullable UUID id) {
        return id != null && world.getEntity(id) instanceof VillagerEntity villager ? villager : null;
    }
}
