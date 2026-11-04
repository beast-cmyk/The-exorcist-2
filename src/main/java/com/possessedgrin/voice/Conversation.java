package com.possessedgrin.voice;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.haunt.Exorcism;
import com.possessedgrin.haunt.Rite;
import com.possessedgrin.haunt.SpiritBoard;
import com.possessedgrin.stage.Stages;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Talking to creatures while you're possessing something. Type in chat:
 * <ul>
 *   <li>to the creature you're looking at, or</li>
 *   <li>to one you name ("cow, are you scared?", "hey iron golem"), or</li>
 *   <li>to nobody in particular, and the soul trapped in your body answers instead.</li>
 * </ul>
 * Ask anything (how it feels, where it lives, what it sells, what it sees, what time it is, any secrets...) and it
 * answers in its own voice, using what it really knows. Taunt it and it reacts: monsters come for you, the meek run.
 * Once the possession is complete (stage III) they obey "follow me", "stay" and "attack".
 *
 * <p>You are the demon, so what you type doesn't appear as your own chat: it comes out of the body you're wearing,
 * in a voice that isn't its own (deep red, letters crawling as the possession deepens, a growl as you speak).
 * Creatures nearby who hear it react: villagers cry out, pray and run, animals bolt, golems attack, and cleric
 * exorcists answer you with the rite.
 */
public final class Conversation {
    private static final double TALK_RANGE = 24.0;

    private record Heard(UUID player, String message) {
    }

    private record Reply(ServerPlayerEntity player, @Nullable MobEntity speaker, @Nullable MobEntity host, Text text, int due) {
    }

    private record Follow(MobEntity mob, UUID player, long until) {
    }

    private static final double HEARING_RANGE = 48.0;
    private static final int OVERHEAR_COOLDOWN = 200;
    /** Who an ordinary village cleric is, for the AI. */
    static final String CLERIC = "a village cleric: a plain country priest who has blessed harvests and buried the dead, "
            + "and never in your life expected to face a real demon. You're frightened, but you hold your holy water and the old words of the rite, and you don't run.";

    private static final ConcurrentLinkedQueue<Heard> HEARD = new ConcurrentLinkedQueue<>();
    private static final List<Reply> REPLIES = new ArrayList<>();
    private static final Map<UUID, Follow> FOLLOWERS = new HashMap<>();
    /** Per creature: when it may react to overhearing you again. */
    private static final Map<UUID, Long> OVERHEARD = new HashMap<>();

    private Conversation() {
    }

    public static void init() {
        // While you possess something, your chat doesn't go out as your own: the body says it, in the demon's voice
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (SpiritBoard.awaiting(sender.getUuid())) {
                // You are the spirit in the board: your answer goes to the planchette, not to chat
                HEARD.add(new Heard(sender.getUuid(), message.getContent().getString()));
                return false;
            }
            if (PossessionComponent.getHost(sender) == null) {
                return true;
            }
            HEARD.add(new Heard(sender.getUuid(), message.getContent().getString()));
            return false;
        });
        ServerTickEvents.END_SERVER_TICK.register(Conversation::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            HEARD.clear();
            REPLIES.clear();
            FOLLOWERS.clear();
            OVERHEARD.clear();
        });
    }

    private static void tick(MinecraftServer server) {
        Heard heard;
        while ((heard = HEARD.poll()) != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(heard.player());
            if (player != null) {
                hear(player, heard.message());
            }
        }

        int now = server.getTicks();
        Iterator<Reply> replies = REPLIES.iterator();
        while (replies.hasNext()) {
            Reply reply = replies.next();
            if (now < reply.due()) {
                continue;
            }
            replies.remove();
            if (reply.player().isDisconnected()) {
                continue;
            }
            MobEntity speaker = reply.speaker();
            if (speaker != null) {
                if (!speaker.isAlive()) {
                    continue;
                }
                if (reply.host() != null) {
                    speaker.getLookControl().lookAt(reply.host(), 30.0F, 30.0F);
                }
                if (speaker.getRandom().nextFloat() < 0.7F) {
                    speaker.playAmbientSound();
                }
            }
            reply.player().sendMessage(reply.text(), false);
        }

        if (now % 10 == 0) {
            tickFollowers(server);
        }
        if (now % 1200 == 0) {
            long worldTime = server.getOverworld().getTime();
            OVERHEARD.values().removeIf(until -> until < worldTime);
        }
    }

    // --- hearing ---------------------------------------------------------------------------------------------

    private static void hear(ServerPlayerEntity player, String message) {
        MobEntity host = PossessionComponent.getHost(player);
        if (message.isBlank() || SpiritBoard.answer(player, message)) {
            return;
        }
        if (host == null || !host.isAlive()) {
            // The body was lost between typing and speaking: say it plainly instead of swallowing it
            Text plain = Text.literal("<" + player.getName().getString() + "> " + message);
            for (ServerPlayerEntity other : player.server.getPlayerManager().getPlayerList()) {
                other.sendMessage(plain, false);
            }
            return;
        }
        if (Deception.speak(player, host, message)) {
            return;     // "~": the soul's own voice, a lie
        }
        int stage = Stages.of(host);
        speakAsDemon(player, host, stage, message);
        Rite.heard(player, message);
        String text = Intent.normalize(message);
        MobEntity listener = text.contains(" soul") ? null : addressee(player, host, text);
        if (listener == null) {
            HostSoul.answer(player, host, stage, message);
        } else {
            respond(player, host, listener, stage, message);
        }
        bystanders(player, host, listener, false, message);
    }

    // --- the demon's voice -----------------------------------------------------------------------------------

    /**
     * What you typed comes out of the body: still almost its own voice in stage I, then red and wrong with a few
     * letters crawling (II), then deep red with more of them crawling (III). The body growls as it speaks.
     */
    /** The body says this aloud, in the demon's voice. */
    public static void demonSays(ServerPlayerEntity player, MobEntity host, String line) {
        speakAsDemon(player, host, Stages.of(host), line);
    }

    private static void speakAsDemon(ServerPlayerEntity player, MobEntity host, int stage, String message) {
        ServerWorld world = (ServerWorld) host.world;
        Random random = world.getRandom();
        Formatting nameColor = stage >= Stages.OPPRESSION ? Formatting.DARK_RED : Formatting.GRAY;
        Formatting color = stage >= Stages.POSSESSION ? Formatting.DARK_RED : stage == Stages.OPPRESSION ? Formatting.RED : Formatting.GRAY;
        float crawl = stage >= Stages.POSSESSION ? 0.10F : stage == Stages.OPPRESSION ? 0.04F : 0.0F;

        MutableText text = Text.literal(PossessedGrin.label(host) + ": ").formatted(nameColor, Formatting.BOLD);
        StringBuilder run = new StringBuilder();
        boolean runCrawls = false;
        for (char c : message.toCharArray()) {
            boolean crawls = Character.isLetter(c) && random.nextFloat() < crawl;
            if (crawls != runCrawls && run.length() > 0) {
                text.append(styled(run.toString(), color, runCrawls));
                run.setLength(0);
            }
            runCrawls = crawls;
            run.append(c);
        }
        if (run.length() > 0) {
            text.append(styled(run.toString(), color, runCrawls));
        }

        for (ServerPlayerEntity listener : world.getPlayers()) {
            if (listener == player || listener.squaredDistanceTo(host) < HEARING_RANGE * HEARING_RANGE) {
                listener.sendMessage(text, false);
            }
        }
        if (player.world != world) {
            player.sendMessage(text, false);
        }

        host.playAmbientSound();
        if (stage >= Stages.OPPRESSION) {
            world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.ENTITY_WARDEN_LISTENING_ANGRY,
                    SoundCategory.HOSTILE, 0.6F, stage >= Stages.POSSESSION ? 0.5F : 0.65F);
        }
        if (stage >= Stages.POSSESSION) {
            world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.HOSTILE, 1.0F, 0.5F);
        }
    }

    private static Text styled(String part, Formatting color, boolean crawls) {
        return crawls ? Text.literal(part).formatted(color, Formatting.ITALIC, Formatting.OBFUSCATED)
                : Text.literal(part).formatted(color, Formatting.ITALIC);
    }

    // --- everyone else who heard it --------------------------------------------------------------------------

    /**
     * Creatures nearby that heard the demon speak (or laugh, or growl) react, one or two at a time: villagers cry
     * out, pray and run; animals bolt; golems attack; monsters go still; cleric exorcists answer with the rite.
     * The deeper the possession, the more of them react.
     */
    public static void bystanders(ServerPlayerEntity player, MobEntity host, @Nullable MobEntity listener, boolean loud) {
        bystanders(player, host, listener, loud, null);
    }

    /** Everyone nearby reacts to what the body just said ({@code heard}) or did (null: a taunt). */
    public static void bystanders(ServerPlayerEntity player, MobEntity host, @Nullable MobEntity listener, boolean loud, @Nullable String heard) {
        ServerWorld world = (ServerWorld) host.world;
        Random random = world.getRandom();
        int stage = Stages.of(host);
        long now = world.getTime();
        double range = loud ? 16.0 : 12.0;
        float chance = stage >= Stages.POSSESSION ? 0.9F : stage == Stages.OPPRESSION ? 0.6F : 0.3F;

        List<MobEntity> near = world.getEntitiesByClass(MobEntity.class, host.getBoundingBox().expand(range),
                m -> m != host && m != listener && m.isAlive() && !m.isSleeping() && !PossessedGrin.isPossessed(m));
        near.sort((a, b) -> Double.compare(a.squaredDistanceTo(host), b.squaredDistanceTo(host)));
        int reacted = 0;
        for (MobEntity mob : near) {
            if (reacted >= 2) {
                break;
            }
            if (now < OVERHEARD.getOrDefault(mob.getUuid(), 0L)) {
                continue;
            }
            Persona persona = Persona.of(mob);
            if (persona == Persona.BOSS) {
                continue;
            }
            if (Rite.isPriest(mob)) {
                continue;       // the priests answer it in the rite itself
            }
            if (Exorcism.isExorcist(mob)) {
                // An exorcist never lets the demon have the last word
                String rebuke = Exorcism.rebuke(mob, host);
                if (heard != null) {
                    AiVoice.say(player, host, mob, CLERIC, "You're the village cleric, facing a possessed " + PossessedGrin.kind(host)
                            + " with holy water in your hand.", "(It just spoke in a voice that isn't its own: \"" + heard
                            + "\". Answer it. You won't bargain with it.)", rebuke, 10 + random.nextInt(15));
                } else {
                    speak(player, host, mob, rebuke, 10 + random.nextInt(15));
                }
                OVERHEARD.put(mob.getUuid(), now + OVERHEAR_COOLDOWN);
                reacted++;
                continue;
            }
            if (random.nextFloat() > chance) {
                continue;
            }
            Feelings.feel(mob, stage >= Stages.OPPRESSION ? Feelings.Emotion.FEAR : Feelings.Emotion.DREAD, stage >= Stages.OPPRESSION ? 0.3F : 0.15F);
            String line = persona.line(stage >= Stages.OPPRESSION ? Persona.Topic.HORROR : Persona.Topic.OVERHEAR, random);
            if (heard != null && reacted == 0) {
                // The nearest one who heard it says what it really thinks, to the demon or to whoever's next to it
                AiVoice.say(player, host, mob, null, "A demon has taken over a " + PossessedGrin.kind(host) + " near you"
                                + (Kinship.isKin(mob, host) ? ", one of your own kind," : "") + " and you just overheard it.",
                        "(The possessed " + PossessedGrin.kind(host) + " just said, in a deep voice that isn't its own: \"" + heard
                                + "\". You weren't the one it was talking to. React: to it, to yourself, or to whoever is beside you.)",
                        line, 10 + random.nextInt(25));
            } else {
                speak(player, host, mob, line, 10 + random.nextInt(25));
            }
            if (stage >= Stages.OPPRESSION) {
                horrified(world, mob, host, persona);
            }
            OVERHEARD.put(mob.getUuid(), now + OVERHEAR_COOLDOWN);
            reacted++;
        }
    }

    /** What a creature does when it realises what it just heard. */
    private static void horrified(ServerWorld world, MobEntity mob, MobEntity host, Persona persona) {
        switch (persona) {
            case VILLAGER, ANIMAL -> {
                if (Kinship.isKin(mob, host) && persona == Persona.ANIMAL) {
                    return;     // its own kind freezes instead of running
                }
                flee(mob, host, 10.0, 1.4);
                if (mob instanceof VillagerEntity) {
                    world.sendEntityStatus(mob, (byte) 42);     // a villager's frightened sweat
                }
            }
            case GOLEM -> {
                if (!Kinship.isKin(mob, host)) {
                    mob.setTarget(host);
                }
            }
            case SPIDER, ENDERMAN, CREEPER -> flee(mob, host, 6.0, 1.2);
            default -> {
            }
        }
    }

    /** Your taunt (the taunt key) lands on a creature: it reacts as if you'd insulted it. */
    public static void taunted(ServerPlayerEntity player, MobEntity host, MobEntity mob) {
        taunted(player, host, mob, null);
    }

    /** The taunt key, aimed at this creature; {@code said}: the cruel thing the body said with it, if any. */
    public static void taunted(ServerPlayerEntity player, MobEntity host, MobEntity mob, @Nullable String said) {
        Random random = mob.getRandom();
        String what = "(The possessed " + PossessedGrin.kind(host) + " just laughed at you, hissed and growled, right in your face"
                + (said == null ? "." : ", and said: \"" + said + "\".") + ")";
        if (Rite.isPriest(mob)) {
            Rite.shaken(mob, 10.0F, "It taunted you to your face" + (said == null ? "." : ": \"" + said + "\""), null);
            return;
        }
        if (Exorcism.isExorcist(mob)) {
            String answer = Exorcism.answer(mob, host, Intent.TAUNT, random);
            AiVoice.say(player, host, mob, CLERIC, "You're the village cleric, facing a possessed " + PossessedGrin.kind(host)
                    + " with holy water in your hand.", what + " Your faith wavers.", answer, 15 + random.nextInt(10));
            return;
        }
        Persona persona = Persona.of(mob);
        tauntReaction((ServerWorld) mob.world, mob, host, persona);
        String fallback = persona.line(Persona.Topic.TAUNTED, random);
        boolean fights = persona.fighter() || (mob instanceof Angerable && persona != Persona.PET);
        AiVoice.say(player, host, mob, null, "A demon has taken over a " + PossessedGrin.kind(host) + " near you and is tormenting you.",
                what + (persona == Persona.BOSS ? " You sneer at it." : fights ? " You're going for it." : " You're backing away."),
                fallback, 15 + random.nextInt(15));
    }

    /** Who you're talking to: the creature you're looking at, else the nearest one you named, else nobody. */
    @Nullable
    private static MobEntity addressee(ServerPlayerEntity player, MobEntity host, String text) {
        MobEntity looked = lookedAt(player, host, TALK_RANGE);
        if (looked != null) {
            return looked;
        }
        MobEntity best = null;
        int bestLength = 0;
        for (MobEntity mob : player.world.getEntitiesByClass(MobEntity.class, host.getBoundingBox().expand(TALK_RANGE),
                m -> m != host && m.isAlive() && !PossessedGrin.isPossessed(m))) {
            String kind = PossessedGrin.kind(mob);
            String name = mob.hasCustomName() ? Intent.normalize(mob.getName().getString()).trim() : "";
            int length = 0;
            if (!name.isEmpty() && text.contains(" " + name)) {
                length = 100 + name.length();
            } else if (text.contains(" " + kind)) {
                length = kind.length();
            }
            if (length > bestLength || (length > 0 && length == bestLength && best != null
                    && mob.squaredDistanceTo(host) < best.squaredDistanceTo(host))) {
                best = mob;
                bestLength = length;
            }
        }
        return best;
    }

    @Nullable
    public static MobEntity lookedAt(ServerPlayerEntity player, MobEntity host, double range) {
        Entity hit = lookedAtEntity(player, host, range, e -> e instanceof MobEntity);
        return hit instanceof MobEntity mob ? mob : null;
    }

    @Nullable
    static Entity lookedAtEntity(ServerPlayerEntity player, MobEntity host, double range, java.util.function.Predicate<Entity> filter) {
        Vec3d eye = player.getCameraPosVec(1.0F);
        Vec3d look = player.getRotationVec(1.0F);
        Box area = player.getBoundingBox().stretch(look.multiply(range)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(player, eye, eye.add(look.multiply(range)), area,
                e -> e != host && e != player && e.isAlive() && !e.isSpectator() && filter.test(e), range * range);
        return hit == null ? null : hit.getEntity();
    }

    // --- answering -------------------------------------------------------------------------------------------

    private static void respond(ServerPlayerEntity player, MobEntity host, MobEntity mob, int stage, String message) {
        Persona persona = Persona.of(mob);
        Random random = mob.getRandom();
        Intent intent = Intent.of(message);
        ServerWorld world = (ServerWorld) mob.world;
        String line;

        Feelings.heard(mob, intent, message, Kinship.isKin(mob, host));
        if (Rite.isPriest(mob)) {
            Rite.answer(player, host, mob, message, intent);
            return;
        }
        if (Exorcism.isExorcist(mob)) {
            // You can't bargain with an exorcist, but you can try to break its faith
            String answer = Exorcism.answer(mob, host, intent, random);
            AiVoice.say(player, host, mob, CLERIC, "You're the village cleric, facing a possessed " + PossessedGrin.kind(host)
                    + " with holy water in your hand.", "(It said to you: \"" + message + "\"."
                    + (intent == Intent.TAUNT ? " It got under your skin: your faith wavers and you stumble over the words of the rite."
                    : " You won't bargain with it.") + ")", answer, 15 + random.nextInt(15));
            return;
        }
        boolean angry = isAfter(mob, host, player);
        String outcome = null;      // what the creature decided to do, so a real AI says the same thing
        if (intent.isCommand()) {
            line = command(player, host, mob, persona, stage, intent, random);
            if (stage < Stages.POSSESSION) {
                outcome = "You refuse: the demon's hold isn't strong enough to command you yet.";
            } else if (intent == Intent.ATTACK && !canFight(mob)) {
                outcome = "You can't fight anything, and you say so.";
            } else if (intent == Intent.ATTACK && mob.getTarget() == null) {
                outcome = "You ask what you're supposed to attack; the demon must look at it.";
            } else {
                outcome = "You obey the demon's command (" + intent.name().toLowerCase() + "), against your will.";
                Witness.record(mob, "The demon forced you to obey it.");
            }
        } else {
            line = switch (intent) {
                case TAUNT -> {
                    tauntReaction(world, mob, host, persona);
                    angry = persona.fighter() || (mob instanceof Angerable && persona != Persona.PET);
                    outcome = persona == Persona.BOSS ? "You sneer at its insult."
                            : angry ? "You are furious and attack the possessed body." : "You are frightened and run away from it.";
                    yield persona.line(Persona.Topic.TAUNTED, random);
                }
                case APOLOGY -> {
                    if (persona != Persona.BOSS && angry) {
                        Kinship.pacify(mob);
                        Kinship.unprovoke(mob);
                        angry = false;
                        outcome = "You accept the apology, warily, and stop attacking.";
                    }
                    yield persona.line(Persona.Topic.APOLOGY, random);
                }
                case FORGIVE -> persona.fighter()
                        ? persona.noise(random) + " Forgive me? For what? ...What have you seen?"
                        : "...thank you. I think. Please stop looking at me.";
                case HELP -> help(player, host, mob, persona, random);
                case GOODBYE -> persona.line(Persona.Topic.GOODBYE, random);
                case GREET -> persona.line(Persona.Topic.GREET, random);
                case IDENTITY -> persona.line(Persona.Topic.IDENTITY, random);
                case FEELINGS -> feelings(mob, persona, random);
                case FEAR -> persona.line(Persona.Topic.FEAR, random);
                case FAMILY -> persona.line(Persona.Topic.FAMILY, random);
                case FOOD -> persona.line(Persona.Topic.FOOD, random);
                case TRADE -> trade(mob, persona, random);
                case WORK -> work(mob, persona, random);
                case HOME -> home(mob, persona, random);
                case ENEMY -> enemy(mob, host, player, persona, random);
                case OWNER -> owner(mob, persona, random);
                case LOCATION -> location(mob, persona, random);
                case TIME -> flavor(persona, random) + Facts.timeOfDay(mob.world) + timeComment(mob, persona);
                case WEATHER -> flavor(persona, random) + Facts.weather(mob.world) + weatherComment(mob, persona);
                case SEES -> flavor(persona, random) + Facts.sees(mob, host, player);
                case OPINION -> opinion(player, host, mob, persona, stage, random);
                case SECRET -> {
                    String secret = Facts.secret(mob);
                    yield secret == null ? persona.line(Persona.Topic.SECRET_NONE, random)
                            : flavor(persona, random) + "I'll tell you a secret. " + secret;
                }
                default -> {
                    // Something it has no ready answer for: answer about whatever was asked, in its own voice
                    String aboutIt = Phrasebook.aboutTopic(persona, message, mob, host, random);
                    yield aboutIt != null && random.nextFloat() < 0.8F ? aboutIt : persona.line(Persona.Topic.UNKNOWN, random);
                }
            };
            if (intent == Intent.HELP && line.endsWith("I'm coming. Hold still.")) {
                outcome = "You are coming over to help the possessed body, because it is one of your own.";
            }
        }

        // The built-in generator: an opener and maybe an aside that fit its mood, so replies rarely repeat
        String core = line;
        Phrasebook.Mood mood = Feelings.mood(mob, Phrasebook.mood(persona, stage, Kinship.isKin(mob, host), angry));
        if (intent != Intent.TAUNT && !intent.isCommand()) {
            line = Phrasebook.dress(line, mood, random);
        }
        // With an API key, a real AI writes the reply instead (and this line is used if it can't).
        // For questions with a true answer, the AI is given that answer so it never makes one up.
        if (!AiVoice.ask(player, host, mob, persona, stage, intent, message, line, intent.isFactual() ? core : null,
                mood.feeling, outcome)) {
            speak(player, host, mob, line, 15 + random.nextInt(20));
        }
    }

    private static String command(ServerPlayerEntity player, MobEntity host, MobEntity mob, Persona persona, int stage,
                                  Intent intent, Random random) {
        if (stage < Stages.POSSESSION) {
            return persona.line(Persona.Topic.REFUSE, random);
        }
        switch (intent) {
            case FOLLOW -> {
                FOLLOWERS.put(mob.getUuid(), new Follow(mob, player.getUuid(), mob.world.getTime() + 2400));
                return persona.line(Persona.Topic.OBEY, random);
            }
            case STAY -> {
                FOLLOWERS.remove(mob.getUuid());
                mob.getNavigation().stop();
                mob.getBrain().forget(MemoryModuleType.WALK_TARGET);
                return persona.line(Persona.Topic.OBEY, random);
            }
            default -> {
                if (!canFight(mob)) {
                    return persona.line(Persona.Topic.CANT_FIGHT, random);
                }
                Entity hit = lookedAtEntity(player, host, 32.0, e -> e instanceof LivingEntity && e != mob);
                if (!(hit instanceof LivingEntity victim)) {
                    return persona.noise(random) + " Attack what? Look at it, and tell me again.";
                }
                Kinship.provoke(mob);
                mob.setTarget(victim);
                mob.getBrain().remember(MemoryModuleType.ATTACK_TARGET, victim);
                return persona.line(Persona.Topic.OBEY, random);
            }
        }
    }

    private static boolean canFight(MobEntity mob) {
        if (mob instanceof Monster || mob instanceof GolemEntity) {
            return true;
        }
        EntityAttributeInstance attack = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        return attack != null && attack.getBaseValue() > 0.0;
    }

    private static boolean isAfter(MobEntity mob, MobEntity host, PlayerEntity player) {
        LivingEntity target = mob.getTarget();
        Entity angry = Facts.angryAt(mob);
        return target == host || target == player || angry == host || angry == player
                || (mob instanceof Angerable angerable && host.getUuid().equals(angerable.getAngryAt()));
    }

    /** Monsters (and anything that can be angered) come for you; the meek turn and run. Bosses just sneer. */
    private static void tauntReaction(ServerWorld world, MobEntity mob, MobEntity host, Persona persona) {
        Witness.record(mob, "The demon mocked you to your face.");
        if (persona == Persona.BOSS) {
            return;
        }
        Kinship.provoke(mob);
        if (persona.fighter() || (mob instanceof Angerable && persona != Persona.PET)) {
            if (mob instanceof Angerable angerable) {
                angerable.setAngryAt(host.getUuid());
                angerable.chooseRandomAngerTime();
            }
            mob.setTarget(host);
            mob.getBrain().remember(MemoryModuleType.ATTACK_TARGET, host);
            world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        } else {
            flee(mob, host, 12.0, 1.5);
            if (mob instanceof VillagerEntity) {
                world.sendEntityStatus(mob, (byte) 13);     // the villager's angry-cloud puff
            }
        }
    }

    /** Run straight away from the possessed body. */
    /** Sends the creature running a few blocks straight away from {@code from}. */
    public static void flee(MobEntity mob, Entity from, double distance, double speed) {
        Vec3d away = mob.getPos().subtract(from.getPos());
        away = new Vec3d(away.x, 0.0, away.z);
        if (away.lengthSquared() < 1.0E-4) {
            away = new Vec3d(1.0, 0.0, 0.0);
        }
        Vec3d to = mob.getPos().add(away.normalize().multiply(distance));
        mob.getNavigation().startMovingTo(to.x, to.y, to.z, speed);
        mob.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(to, (float) (speed * 0.55), 1));
    }

    private static String help(ServerPlayerEntity player, MobEntity host, MobEntity mob, Persona persona, Random random) {
        if (Kinship.isKin(mob, host) && Kinship.comeHelp(mob, host, player)) {
            return persona.noise(random) + " I'm coming. Hold still.";
        }
        return persona.line(Persona.Topic.HELP, random);
    }

    private static String feelings(MobEntity mob, Persona persona, Random random) {
        String line = persona.line(Persona.Topic.FEELINGS, random);
        float health = mob.getHealth() / mob.getMaxHealth();
        if (health < 0.4F) {
            line = "Hurt. Badly. " + Facts.hearts(mob) + " hearts left... " + line;
        }
        return line;
    }

    private static String trade(MobEntity mob, Persona persona, Random random) {
        if (mob instanceof MerchantEntity merchant) {
            TradeOfferList offers = merchant.getOffers();
            List<String> deals = new ArrayList<>();
            for (TradeOffer offer : offers) {
                if (offer.isDisabled()) {
                    continue;
                }
                String price = Facts.itemName(offer.getAdjustedFirstBuyItem());
                if (!offer.getSecondBuyItem().isEmpty()) {
                    price += " and " + Facts.itemName(offer.getSecondBuyItem());
                }
                deals.add(Facts.itemName(offer.getSellItem()) + " for " + price);
                if (deals.size() == 3) {
                    break;
                }
            }
            if (deals.isEmpty()) {
                return flavor(persona, random) + "I've nothing to sell right now. Come back when I've restocked.";
            }
            return flavor(persona, random) + "I can do " + Facts.join(deals) + ". Though I'd rather not shake your hand.";
        }
        if (mob instanceof AbstractPiglinEntity) {
            return "Gold! Throw gold ingot, piglin gives stuff! Good stuff! Sometimes!";
        }
        return persona.noise(random) + " Trade? I'm a " + PossessedGrin.kind(mob) + ". I have nothing you'd want.";
    }

    private static String work(MobEntity mob, Persona persona, Random random) {
        if (mob instanceof VillagerEntity villager) {
            String job = villager.getVillagerData().getProfession().id();
            BlockPos site = Facts.remembered(mob, MemoryModuleType.JOB_SITE);
            if (job.equals("nitwit")) {
                return "I don't work. They won't let me near anything important.";
            }
            if (job.equals("none")) {
                return flavor(persona, random) + "No job yet. I'm looking for a workstation nobody's claimed.";
            }
            return flavor(persona, random) + "I'm the " + job.replace('_', ' ') + " here."
                    + (site != null ? " My workstation is " + Facts.direction(mob.getPos(), site) + "." : "");
        }
        return persona.line(Persona.Topic.WORK, random);
    }

    private static String home(MobEntity mob, Persona persona, Random random) {
        BlockPos home = Facts.remembered(mob, MemoryModuleType.HOME);
        if (home != null) {
            return flavor(persona, random) + "My bed's at " + Facts.where(home) + ", " + Facts.direction(mob.getPos(), home)
                    + ". Don't you dare follow me there.";
        }
        return persona.line(Persona.Topic.HOME_NONE, random);
    }

    private static String enemy(MobEntity mob, MobEntity host, PlayerEntity player, Persona persona, Random random) {
        String target = Facts.nameOf(mob.getTarget(), host, player);
        if (target != null) {
            return target.equals("you") ? "You. Who else?" : flavor(persona, random) + "Right now? " + target + ". I'm after " + target + ".";
        }
        String angry = Facts.nameOf(Facts.angryAt(mob), host, player);
        if (angry != null) {
            return angry.equals("you") ? "You. I haven't forgotten." : flavor(persona, random) + "I'm furious with " + angry + ".";
        }
        String attacker = Facts.nameOf(mob.getAttacker(), host, player);
        if (attacker != null) {
            return flavor(persona, random) + attacker + " hurt me. I won't forget it.";
        }
        return persona.line(Persona.Topic.FEAR, random);
    }

    private static String owner(MobEntity mob, Persona persona, Random random) {
        if (mob instanceof TameableEntity pet && pet.isTamed()) {
            LivingEntity owner = pet.getOwner();
            return owner != null ? "I belong to " + owner.getName().getString() + ". Always."
                    : "My person is far away. I'm waiting for them.";
        }
        if (mob instanceof AbstractHorseEntity horse && horse.isTame()) {
            UUID uuid = horse.getOwnerUuid();
            PlayerEntity owner = uuid == null ? null : mob.world.getPlayerByUuid(uuid);
            return owner != null ? "I carry " + owner.getName().getString() + ". Nobody else." : "Someone tamed me. They left.";
        }
        if (persona == Persona.GOLEM) {
            return "*clank* I belong to the village.";
        }
        return persona.noise(random) + " Nobody owns me.";
    }

    private static String location(MobEntity mob, Persona persona, Random random) {
        BlockPos pos = mob.getBlockPos();
        String line = flavor(persona, random) + "We're in the " + Facts.biome(mob.world, pos) + ", in " + Facts.dimension(mob.world)
                + ", at " + Facts.where(pos) + ".";
        BlockPos bell = Facts.remembered(mob, MemoryModuleType.MEETING_POINT);
        if (bell != null) {
            line += " The village bell is " + Facts.direction(mob.getPos(), bell) + ".";
        }
        return line;
    }

    private static String timeComment(MobEntity mob, Persona persona) {
        if (!Facts.hasSky(mob.world)) {
            return "";
        }
        if ((persona == Persona.UNDEAD || persona == Persona.SKELETON) && mob.world.isDay()) {
            return " The sun... it burns.";
        }
        if (persona == Persona.VILLAGER && !mob.world.isDay()) {
            return " I should be in bed with the door shut.";
        }
        return "";
    }

    private static String weatherComment(MobEntity mob, Persona persona) {
        if (persona == Persona.ENDERMAN && mob.world.isRaining()) {
            return " I hate it. It burns.";
        }
        if (persona == Persona.CREEPER && mob.world.isThundering()) {
            return " Ssss... lightning makes me feel... strong.";
        }
        return "";
    }

    private static String opinion(ServerPlayerEntity player, MobEntity host, MobEntity mob, Persona persona, int stage, Random random) {
        if (mob instanceof VillagerEntity villager) {
            int reputation = villager.getReputation(player);
            if (reputation >= 20) {
                return "You've been good to this village, " + player.getName().getString() + ". Which makes this so much harder to look at.";
            }
            if (reputation <= -20) {
                return "You hurt us before, " + player.getName().getString() + ". And now you come back wearing one of us?";
            }
        }
        if (Kinship.isKin(mob, host)) {
            return flavor(persona, random) + "You look like one of us. You're not. Not anymore.";
        }
        if (stage >= Stages.POSSESSION && random.nextBoolean()) {
            return flavor(persona, random) + "I think you're the worst thing I've ever seen, " + player.getName().getString() + ".";
        }
        return persona.line(Persona.Topic.OPINION, random);
    }

    /** Sometimes a mob's noise before a plain answer, so it sounds like itself. */
    private static String flavor(Persona persona, Random random) {
        return random.nextFloat() < 0.45F ? persona.noise(random) + " " : "";
    }

    // --- following -------------------------------------------------------------------------------------------

    private static void tickFollowers(MinecraftServer server) {
        Iterator<Follow> it = FOLLOWERS.values().iterator();
        while (it.hasNext()) {
            Follow follow = it.next();
            MobEntity mob = follow.mob();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(follow.player());
            MobEntity host = player == null ? null : PossessionComponent.getHost(player);
            if (!mob.isAlive() || mob.isRemoved() || host == null || host.world != mob.world || mob.world.getTime() > follow.until()) {
                it.remove();
                continue;
            }
            if (mob.squaredDistanceTo(host) > 9.0) {
                mob.getNavigation().startMovingTo(host, 1.15);
                mob.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(host, 0.7F, 2));
            }
        }
    }

    // --- speaking --------------------------------------------------------------------------------------------

    /** A creature says something to you after a short pause, looking at you as it does. */
    public static void speak(ServerPlayerEntity player, @Nullable MobEntity host, MobEntity mob, String line, int delay) {
        Persona persona = Persona.of(mob);
        String filled = fill(line, mob, host, player);
        REPLIES.add(new Reply(player, mob, host, format(PossessedGrin.label(mob), persona.color, filled), player.server.getTicks() + delay));
    }

    /** Something else (the trapped soul) says something after a pause. */
    static void say(ServerPlayerEntity player, Text text, int delay) {
        REPLIES.add(new Reply(player, null, null, text, player.server.getTicks() + delay));
    }

    public static String fill(String line, LivingEntity speaker, @Nullable MobEntity host, PlayerEntity player) {
        String owner = "my owner";
        if (speaker instanceof TameableEntity pet && pet.getOwner() != null) {
            owner = pet.getOwner().getName().getString();
        }
        return line.replace("{me}", PossessedGrin.kind(speaker))
                .replace("{host}", host != null ? PossessedGrin.kind(host) : "body")
                .replace("{player}", player.getName().getString())
                .replace("{owner}", owner);
    }

    /** {@code Name: *action* "words"}, with the action in grey italics. */
    static Text format(String name, Formatting color, String line) {
        MutableText text = Text.literal(name + ": ").formatted(color, Formatting.BOLD);
        String rest = line.trim();
        if (rest.startsWith("*")) {
            int end = rest.indexOf('*', 1);
            if (end > 0) {
                text.append(Text.literal(rest.substring(0, end + 1) + " ").formatted(Formatting.GRAY, Formatting.ITALIC));
                rest = rest.substring(end + 1).trim();
            }
        }
        if (!rest.isEmpty()) {
            text.append(Text.literal("\"" + rest + "\"").formatted(Formatting.WHITE));
        }
        return text;
    }
}
