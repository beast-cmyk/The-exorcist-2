package com.possessedgrin.stage;

import java.util.EnumSet;
import com.possessedgrin.voice.Persona;
import com.possessedgrin.voice.Witness;
import com.possessedgrin.PossessedGrin;
import com.possessedgrin.PossessedGrinMod;
import com.possessedgrin.haunt.AtticSounds;
import com.possessedgrin.haunt.DemonIdolItem;
import com.possessedgrin.haunt.Haunting;
import com.possessedgrin.haunt.Invitations;
import com.possessedgrin.haunt.Rite;
import com.possessedgrin.haunt.SpiritBoard;
import com.possessedgrin.power.BodyJump;
import com.possessedgrin.voice.AiVoice;
import com.possessedgrin.power.PossessedPowers;
import com.possessedgrin.voice.HostSoul;
import ladysnake.requiem.api.v1.entity.MovementAlterer;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs each possession through its stages on the server: notices when one starts, moves it on, plays each turning
 * point (title, sounds, a shudder through the room, the trapped soul crying out), tells clients so they can draw
 * it, and lets it go when the possession ends.
 */
public final class StageDirector {
    /** Blood: dark red dust, dripping from a possessed face. */
    public static final DustParticleEffect BLOOD = new DustParticleEffect(new Vec3f(0.42F, 0.0F, 0.02F), 1.1F);

    /** If you're pulled out briefly (changing dimension, say) and come back into the same kind of mob, it resumes. */
    private static final int RESUME_GRACE = 100;

    private record Tracked(UUID rider, EntityType<?> type, int stage) {
    }

    private record Resume(EntityType<?> type, long start, long endedAt) {
    }

    private static final Map<Integer, Tracked> TRACKED = new HashMap<>();
    private static final UUID SPIDER_SPEED = UUID.fromString("5b0e7b8e-3a51-4c8f-9d2e-6f7d1c0a9e41");
    private static final Set<Persona> HUMANOIDS = EnumSet.of(Persona.VILLAGER, Persona.ILLAGER, Persona.WITCH, Persona.UNDEAD,
            Persona.SKELETON, Persona.PIGLIN);
    private static final Map<UUID, Resume> RESUME = new HashMap<>();
    /** Players whose body just died of being fed on: their next possession starts fresh. */
    private static final Set<UUID> SPENT = new HashSet<>();

    private StageDirector() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(StageDirector::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> resync(handler.player)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            Stages.server().clear();
            TRACKED.clear();
            RESUME.clear();
            SPENT.clear();
            Feeding.clear();
            Rite.clear();
            SpiritBoard.clear();
            AtticSounds.clear();
            Invitations.clear();
            BodyJump.clear();
        });
    }

    private static void tick(MinecraftServer server) {
        Map<Integer, Long> starts = Stages.server();
        Set<Integer> active = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            MobEntity host = PossessionComponent.getHost(player);
            if (host == null || !host.isAlive()) {
                continue;
            }
            int id = host.getId();
            active.add(id);
            long now = host.world.getTime();
            Long start = starts.get(id);

            if (start == null) {
                Resume resume = RESUME.remove(player.getUuid());
                long headStart = BodyJump.takeHeadStart(player.getUuid());
                if (Invitations.consume(host)) {
                    headStart = Math.max(headStart, Stages.OPPRESSION_AT);     // it opened the door itself
                }
                boolean resumed = headStart < 0 && resume != null && resume.type() == host.getType() && now - resume.endedAt() <= RESUME_GRACE;
                start = resumed ? resume.start() : now - Math.max(0L, headStart);
                int stage = Stages.stageAt(now - start);
                starts.put(id, start);
                TRACKED.put(id, new Tracked(player.getUuid(), host.getType(), stage));
                sync(server, id, now - start);
                if (!resumed) {
                    begin(player, host);
                    if (stage > Stages.INFESTATION) {
                        advance(player, host, stage);
                    }
                } else {
                    reapplyMovement(player);
                }
                continue;
            }

            Tracked tracked = TRACKED.get(id);
            int before = tracked == null ? Stages.INFESTATION : tracked.stage();
            boolean idol = DemonIdolItem.carries(player, PossessedGrinMod.DEMON_IDOL);
            if (before < Stages.POSSESSION && (Haunting.inSeance(player) || idol && now % 2 == 0)) {
                // A séance hurries the possession along (twice as fast); so does the idol (half again as fast)
                start = start - 1;
                starts.put(id, start);
                if (now % 40 == 0) {
                    sync(server, id, now - start);
                }
            }
            long elapsed = now - start;
            int stage = Stages.stageAt(elapsed);
            if (stage != before) {
                TRACKED.put(id, new Tracked(player.getUuid(), host.getType(), stage));
                sync(server, id, elapsed);
                advance(player, host, stage);
            } else if (now % 200 == 0) {
                sync(server, id, elapsed);    // keeps clients in step
            }
            ambience((ServerWorld) host.world, host, stage, now);
            boolean crawling = stage >= Stages.POSSESSION && player.isSneaking() && !player.getAbilities().flying;
            spiderSpeed(host, crawling);      // Requiem moves you at the body's speed...
            spiderSpeed(player, crawling);    // ...or yours, depending on the version: both get it
            if (!Feeding.tick(player, host, elapsed, now)) {
                continue;       // the body was fed on until it died
            }
            HostSoul.tick(player, host, stage, now);
        }

        // Nobody crawls fast outside a body
        if (server.getTicks() % 10 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!active.contains(hostIdOf(player))) {
                    spiderSpeed(player, false);
                }
            }
        }

        // Possessions that just ended
        Iterator<Map.Entry<Integer, Long>> it = starts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            int id = entry.getKey();
            if (active.contains(id)) {
                continue;
            }
            it.remove();
            Tracked tracked = TRACKED.remove(id);
            if (tracked != null) {
                long now = server.getOverworld().getTime();
                boolean spent = SPENT.remove(tracked.rider());
                if (!spent) {
                    RESUME.put(tracked.rider(), new Resume(tracked.type(), entry.getValue(), now));
                }
                HostSoul.release(server, tracked.rider(), spent);
                if (!spent) {
                    freed(server, id, tracked.rider());
                }
            }
            Feeding.forget(id);
            sync(server, id, -1);
        }
    }

    // --- the turning points ----------------------------------------------------------------------------------

    private static void begin(ServerPlayerEntity player, MobEntity host) {
        ServerWorld world = (ServerWorld) host.world;
        title(player,
                Text.literal("I. Infestation").formatted(Formatting.GRAY),
                Text.literal("Something has crawled inside " + PossessedGrin.the(host) + "...").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        sound(host, SoundEvents.AMBIENT_CAVE, 1.0F, 0.6F);
        sound(host, SoundEvents.PARTICLE_SOUL_ESCAPE, 1.0F, 0.5F);
        Haunting.shake(player, 0.4F, 15);
        world.spawnParticles(ParticleTypes.SOUL, host.getX(), host.getBodyY(0.6), host.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        player.sendMessage(Text.literal("Stage I, Infestation. Its soul is still fighting you. You can read minds (V) and talk to any creature in chat.")
                .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
        HostSoul.begin(player, host);
    }

    private static void advance(ServerPlayerEntity player, MobEntity host, int stage) {
        ServerWorld world = (ServerWorld) host.world;
        if (stage == Stages.OPPRESSION) {
            title(player,
                    Text.literal("II. Oppression").formatted(Formatting.DARK_RED),
                    Text.literal(capitalize(PossessedGrin.the(host)) + " thrashes against you. It is losing.").formatted(Formatting.GRAY, Formatting.ITALIC));
            sound(host, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 0.8F, 0.5F);
            sound(host, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.5F, 0.6F);
            Haunting.shake(player, 1.4F, 40);
            Haunting.rattle(world, host, 8.0, true);
            world.spawnParticles(ParticleTypes.SQUID_INK, host.getX(), host.getBodyY(0.5), host.getZ(), 25, 0.4, 0.6, 0.4, 0.05);
            world.spawnParticles(ParticleTypes.SOUL, host.getX(), host.getBodyY(0.6), host.getZ(), 20, 0.5, 0.6, 0.5, 0.03);
            player.sendMessage(Text.literal("Stage II, Oppression. Awakened: the head turn (H), poltergeist (G), levitation (double-tap jump), the demon's voice.")
                    .formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
            if (HUMANOIDS.contains(Persona.of(host))) {
                // The soul's last way to be heard: words rising out of its own skin
                player.sendMessage(Text.literal("Welts rise across " + PossessedGrin.the(host) + "'s belly, spelling two words: HELP ME.")
                        .formatted(Formatting.RED, Formatting.ITALIC), false);
                Witness.seen(host, null, "You saw words rise in welts on " + PossessedGrin.the(host) + "'s skin: HELP ME.");
            }
        } else if (stage == Stages.POSSESSION) {
            title(player,
                    Text.literal("III. POSSESSION").formatted(Formatting.DARK_RED, Formatting.BOLD),
                    Text.literal(capitalize(PossessedGrin.the(host)) + " is yours. Body and soul.").formatted(Formatting.RED, Formatting.ITALIC));
            sound(host, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.2F, 0.5F);
            sound(host, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.7F, 0.45F);
            sound(host, SoundEvents.ENTITY_WITHER_AMBIENT, 1.0F, 0.4F);
            Haunting.shake(player, 2.4F, 55);
            Haunting.rattle(world, host, 10.0, true);
            world.spawnParticles(BLOOD, host.getX(), host.getBodyY(0.8), host.getZ(), 40, 0.4, 0.4, 0.4, 0.0);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, host.getX(), host.getBodyY(0.5), host.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
            player.sendMessage(Text.literal("Stage III, Possession. Awakened: telekinesis (R), bile (B), unholy strength, the spider-walk (climb walls, crouch to crawl).")
                    .formatted(Formatting.RED, Formatting.ITALIC), false);
        }
        reapplyMovement(player);
        HostSoul.stageReached(player, host, stage);
        Witness.seen(host, null, stage >= Stages.POSSESSION
                ? "You saw " + PossessedGrin.the(host) + " shriek, its eyes blazing white, blood running from them."
                : "You saw " + PossessedGrin.the(host) + " convulse, and its eyes turn white.");
    }

    /**
     * The body you just left, alive: limp, dazed, and it remembers nothing of what happened, or of anything it said
     * to anyone while you wore it.
     */
    private static void freed(MinecraftServer server, int hostId, UUID rider) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getEntityById(hostId) instanceof MobEntity body && body.isAlive()) {
                spiderSpeed(body, false);
                body.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 400, 2));
                body.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 1200, 1));
                body.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0));
                Witness.forget(body.getUuid());
                AiVoice.forget(body.getUuid());
                Witness.record(body, "You woke up somewhere you don't remember going, weak and aching. There are hours missing.");
                world.spawnParticles(ParticleTypes.SOUL, body.getX(), body.getBodyY(0.6), body.getZ(), 8, 0.3, 0.4, 0.3, 0.01);
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(rider);
                if (player != null && player.world == world && player.squaredDistanceTo(body) < 48.0 * 48.0) {
                    player.sendMessage(Text.literal(capitalize(PossessedGrin.the(body)) + " sags, blinking, weak as a newborn. It remembers nothing.")
                            .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
                }
                return;
            }
        }
    }

    /** Stage III crawling is fast: crouched, the body scuttles at nearly walking speed. */
    private static void spiderSpeed(LivingEntity body, boolean on) {
        EntityAttributeInstance speed = body.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean has = speed.getModifier(SPIDER_SPEED) != null;
        if (on && !has) {
            speed.addTemporaryModifier(new EntityAttributeModifier(SPIDER_SPEED, "Spider-walk", 1.6, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!on && has) {
            speed.removeModifier(SPIDER_SPEED);
        }
    }

    private static int hostIdOf(ServerPlayerEntity player) {
        MobEntity host = PossessionComponent.getHost(player);
        return host == null ? Integer.MIN_VALUE : host.getId();
    }

    /** Small things that keep happening around a possessed body. */
    private static void ambience(ServerWorld world, MobEntity host, int stage, long now) {
        int offset = host.getId() & 7;
        if (stage >= Stages.POSSESSION && (now + offset) % 12 == 0) {
            // blood drips from the face
            world.spawnParticles(BLOOD, host.getX(), host.getEyeY() - 0.15, host.getZ(), 1, 0.12, 0.05, 0.12, 0.0);
        } else if (stage == Stages.OPPRESSION && (now + offset) % 35 == 0) {
            world.spawnParticles(ParticleTypes.SMOKE, host.getX(), host.getEyeY(), host.getZ(), 2, 0.15, 0.1, 0.15, 0.0);
        }
        if (stage >= Stages.OPPRESSION && (now + offset) % 160 == 0 && world.getRandom().nextInt(3) == 0) {
            sound(host, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.6F, 0.5F + world.getRandom().nextFloat() * 0.2F);
        }
        // The body coming apart: bits of rot fall off it, then flakes of bone
        float drain = Stages.drain(host);
        if (stage >= Stages.POSSESSION && drain >= 0.75F && (now + offset) % 15 == 0) {
            boolean bones = drain >= 0.9F;
            world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(bones ? Items.BONE : Items.ROTTEN_FLESH)),
                    host.getX(), host.getBodyY(0.6), host.getZ(), bones ? 3 : 2, host.getWidth() * 0.3, host.getHeight() * 0.25, host.getWidth() * 0.3, 0.02);
            if (bones) {
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.BONE_BLOCK.getDefaultState()),
                        host.getX(), host.getBodyY(0.5), host.getZ(), 4, host.getWidth() * 0.35, host.getHeight() * 0.3, host.getWidth() * 0.35, 0.0);
            }
            if (world.getRandom().nextInt(4) == 0) {
                sound(host, bones ? SoundEvents.ENTITY_SKELETON_STEP : SoundEvents.ENTITY_SLIME_SQUISH_SMALL, 0.4F, bones ? 0.6F : 0.5F);
            }
        }
    }

    /** The body may float from stage II: Requiem re-checks what this body is allowed to do. */
    private static void reapplyMovement(ServerPlayerEntity player) {
        try {
            MovementAlterer alterer = MovementAlterer.get(player);
            if (alterer != null) {
                alterer.applyConfig();
            }
        } catch (Throwable ignored) {
            // Older or newer Requiem without this method: levitation then starts with the next possession instead
        }
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private static void sync(MinecraftServer server, int hostId, long elapsed) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            send(player, hostId, elapsed);
        }
    }

    private static void resync(ServerPlayerEntity player) {
        for (Map.Entry<Integer, Long> entry : Stages.server().entrySet()) {
            send(player, entry.getKey(), Math.max(0L, player.world.getTime() - entry.getValue()));
        }
    }

    private static void send(ServerPlayerEntity player, int hostId, long elapsed) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(hostId);
        buf.writeVarLong(elapsed);
        ServerPlayNetworking.send(player, Stages.SYNC, buf);
    }

    public static void title(ServerPlayerEntity player, Text title, Text subtitle) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(8, 50, 25));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        player.networkHandler.sendPacket(new TitleS2CPacket(title));
    }

    private static void sound(MobEntity host, SoundEvent sound, float volume, float pitch) {
        host.world.playSound(null, host.getX(), host.getY(), host.getZ(), sound, SoundCategory.HOSTILE, volume, pitch);
    }

    static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** This player's body just died of being fed on; don't resume its possession on the next one. */
    static void spent(UUID rider) {
        SPENT.add(rider);
    }

    /** For other classes: is a stage number one of ours. */
    public static String label(int stage) {
        return PossessedPowers.roman(stage) + ". " + Stages.name(stage);
    }
}
