package com.possessedgrin.voice;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.haunt.Exorcism;
import com.possessedgrin.haunt.Rite;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.Locale;

/**
 * Lying with the body's own voice.
 * <ul>
 *   <li><b>The innocent voice:</b> start a chat message with {@code ~} and it comes out in the trapped soul's own
 *       voice, small and scared and sweet, with none of the demon in it. Creatures that hear it believe it: they
 *       stop running and come closer. Exorcists don't believe a word.</li>
 *   <li><b>Mimicry:</b> speak as someone they lost (a mother, a father, a son, a sister...) and every creature that
 *       hears it is drawn toward the voice. A priest who hears his dead mother's voice loses a great deal of
 *       faith.</li>
 * </ul>
 */
public final class Deception {
    public static final String PREFIX = "~";
    private static final double RANGE = 14.0;
    private static final List<String> FAMILY = List.of("mother", "mom", "mum", "mama", "mommy", "father", "dad", "daddy", "papa",
            "son", "daughter", "brother", "sister", "wife", "husband", "child", "baby", "grandma", "grandpa", "granny");

    private static final String[] TRUSTING = {
            "Is that really you? Oh, thank goodness...",
            "You sound like yourself again. Are you alright?",
            "Shh, shh. It's alright. Come here, let me look at you.",
            "I knew you were still in there. I knew it.",
            "We'll get you help. I promise. Just stay with me."};
    private static final String[] WARY = {
            "*takes half a step closer, then stops* ...Is it you? Is it really you?",
            "That sounds like you. But your eyes...",
            "Don't. Don't do that. I don't know which of you is talking."};
    private static final String[] LURED = {
            "*stops dead* ...That voice. Where is that voice coming from?",
            "*turns, slowly* ...Is someone calling me?",
            "*whispers* That can't be. That can't be her...",
            "*walks toward the sound as if in a dream*"};
    private static final String[] PRIEST_UNMOVED = {
            "That is not its voice. That is yours.",
            "Wear whatever voice you like. I know who is speaking.",
            "A clever trick. It changes nothing."};

    private Deception() {
    }

    /** Speaks a {@code ~} message in the soul's voice. Returns false if it isn't one. */
    public static boolean speak(ServerPlayerEntity player, MobEntity host, String message) {
        if (!message.startsWith(PREFIX)) {
            return false;
        }
        String words = message.substring(PREFIX.length()).trim();
        if (words.isEmpty()) {
            return true;
        }
        ServerWorld world = (ServerWorld) host.world;
        Random random = world.getRandom();
        boolean mimicry = mimicry(words);
        String kind = PossessedGrin.kind(host);
        Rite.heard(player, "(in the body's own innocent voice) " + words);

        // Everyone in earshot hears an ordinary, frightened voice
        Persona persona = Persona.of(host);
        Text text = Conversation.format(PossessedGrin.label(host), persona.color, words);
        for (ServerPlayerEntity listener : world.getPlayers()) {
            if (listener == player || listener.squaredDistanceTo(host) < 48.0 * 48.0) {
                listener.sendMessage(text, false);
            }
        }
        host.playAmbientSound();
        if (mimicry) {
            world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, SoundCategory.HOSTILE, 0.5F, 0.6F);
        }
        player.sendMessage(Text.literal(mimicry ? "(You speak in the voice of someone they lost.)" : "(You speak in its own voice. Nothing of you shows.)")
                .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), true);

        MobEntity answerer = null;
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, host.getBoundingBox().expand(RANGE),
                m -> m != host && m.isAlive() && !PossessedGrin.isPossessed(m))) {
            if (Exorcism.isExorcist(mob) || Rite.isPriest(mob)) {
                if (mimicry) {
                    Rite.shaken(mob, 25.0F, "Out of the possessed body came the voice of someone you lost, someone you loved, saying: \""
                            + words + "\". It sounds exactly like them.", "*freezes* That voice... No. No, she's gone. You are not her!");
                    Witness.record(mob, "The demon spoke to you in the voice of someone you lost.");
                } else if (answerer == null && !Rite.isPriest(mob)) {
                    AiVoice.say(player, host, mob, Conversation.CLERIC, "You're the village cleric, facing a possessed " + kind + ".",
                            "(The demon is putting on the body's own innocent, frightened voice to say: \"" + words
                                    + "\". You don't believe a word of it.)", PRIEST_UNMOVED[random.nextInt(PRIEST_UNMOVED.length)], 20);
                    answerer = mob;
                }
                continue;
            }
            if (mob instanceof Monster) {
                continue;
            }
            if (mimicry) {
                // Drawn to the voice, as if in a dream
                come(mob, host, 1);
                Witness.record(mob, "You heard a voice you knew coming out of " + PossessedGrin.the(host) + ": the voice of someone you lost. \"" + words + "\"");
                if (answerer == null) {
                    answerer = mob;
                    AiVoice.say(player, host, mob, null, "A demon has taken over a " + kind + " near you.",
                            "(Out of the possessed body comes the voice of someone you lost, someone you loved, saying: \"" + words
                                    + "\". It sounds exactly like them. You can't help going toward it.)", LURED[random.nextInt(LURED.length)], 20);
                }
                continue;
            }
            boolean knows = knowsBetter(mob);
            if (!knows) {
                come(mob, host, 2);
            }
            Witness.record(mob, PossessedGrin.label(host) + " spoke in its own voice again, small and scared: \"" + words + "\". It sounded like itself.");
            if (answerer == null && mob.squaredDistanceTo(host) < 10.0 * 10.0) {
                answerer = mob;
                String[] lines = knows ? WARY : TRUSTING;
                AiVoice.say(player, host, mob, null, "A " + kind + " you know has been possessed by a demon"
                                + (Kinship.isKin(mob, host) ? "; it's one of your own" : "") + ".",
                        "(Just now it spoke in its own ordinary voice, small and scared, with nothing wrong in it: \"" + words + "\". "
                                + (knows ? "You want so badly to believe it's really them, but you've seen what it really is." : "You believe it's really them.")
                                + " Answer them.)", lines[random.nextInt(lines.length)], 20 + random.nextInt(15));
            }
        }
        return true;
    }

    /** Did this message name someone they lost? */
    private static boolean mimicry(String words) {
        for (String word : words.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (FAMILY.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Has it already seen what this body really is? Then it wavers instead of trusting. */
    private static boolean knowsBetter(MobEntity mob) {
        for (String memory : Witness.memories(mob)) {
            if (memory.contains("demon") || memory.contains("real face") || memory.contains("head twist")) {
                return true;
            }
        }
        return false;
    }

    private static void come(MobEntity mob, MobEntity host, int within) {
        mob.getNavigation().startMovingTo(host, 0.7);
        mob.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(host, 0.45F, within));
        mob.getLookControl().lookAt(host, 30.0F, 30.0F);
    }

    /** Should a {@code ~} line skip the demon's voice? Used before the usual chat handling. */
    public static boolean isInnocent(String message) {
        return message.startsWith(PREFIX);
    }
}
