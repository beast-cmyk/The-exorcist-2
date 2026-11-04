package com.possessedgrin.voice;

import com.possessedgrin.stage.Stages;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Every creature's inner life.
 * <ul>
 *   <li><b>Feelings</b> that come and go: fear, dread, anger, defiance, grief, pity, hope, trust, curiosity,
 *       disgust, shame, confusion, relief and longing. What it sees and hears stirs them up (each memory it makes is
 *       weighed for how it would feel), and they fade again over time, some fast (anger), some slowly (grief). They're
 *       mixed: a villager can be terrified of the body and heartbroken for the person trapped in it at once.</li>
 *   <li><b>A nature</b> of its own, fixed for life: brave or timid, warm or cold, devout or doubting, hot-headed or
 *       calm, solemn or wry; a way of talking (stammering when scared, joking to cope, going very quiet...); and a
 *       small piece of a life (a worry, a loss, someone it loves).</li>
 * </ul>
 * The AI is told all of it; the built-in voices pick their mood from it. Server thread only.
 */
public final class Feelings {
    public enum Emotion {
        FEAR("a little afraid", "frightened", "terrified", 2400, "your voice shakes and you want to get away"),
        DREAD("uneasy", "full of dread", "sick with dread", 6000, "you can't relax and you keep checking where it is"),
        ANGER("irritated", "angry", "furious", 1800, "your words come out short and sharp"),
        DEFIANCE("stubborn", "defiant", "fiercely defiant", 2400, "you refuse to let it see you break"),
        GRIEF("sad", "grieving", "heartbroken", 9000, "you choke up"),
        PITY("sorry for the one inside", "full of pity for the one inside", "aching for the one trapped inside", 6000,
                "you talk gently, to the one trapped inside"),
        HOPE("a little hopeful", "hopeful", "desperately hopeful", 3000, "you want so badly for this to be over"),
        TRUST("a little trusting", "trusting", "trusting completely", 6000, "your guard comes down"),
        CURIOSITY("curious", "very curious", "fascinated", 2400, "you can't stop yourself asking things"),
        DISGUST("put off", "disgusted", "revolted", 2400, "you can barely look at it"),
        SHAME("embarrassed", "ashamed", "consumed by shame", 6000, "you can't meet its eyes"),
        CONFUSION("unsure", "confused", "badly shaken and lost", 1800, "you lose your thread and contradict yourself"),
        RELIEF("relieved", "relieved", "weak with relief", 1800, "you nearly laugh, or nearly cry"),
        LONGING("wistful", "longing for someone", "aching for someone you've lost", 6000, "you keep thinking of someone you miss");

        final String weak;
        final String medium;
        final String strong;
        final int halfLife;
        final String shows;

        Emotion(String weak, String medium, String strong, int halfLife, String shows) {
            this.weak = weak;
            this.medium = medium;
            this.strong = strong;
            this.halfLife = halfLife;
            this.shows = shows;
        }

        String word(float amount) {
            return amount >= 0.65F ? this.strong : amount >= 0.35F ? this.medium : this.weak;
        }
    }

    private static final Emotion[] ALL = Emotion.values();

    /** Who it is, underneath: fixed for its whole life (it comes from its id). */
    public record Nature(float courage, float warmth, float faith, float temper, float humour, String voice, String life) {
    }

    private static final class State {
        final float[] value = new float[ALL.length];
        long at;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, Nature> NATURES = new HashMap<>();

    private Feelings() {
    }

    // --- feeling things --------------------------------------------------------------------------------------

    /** Stirs up (or, with a negative amount, settles) one feeling. */
    public static void feel(LivingEntity mob, Emotion emotion, float amount) {
        State state = state(mob);
        int i = emotion.ordinal();
        state.value[i] = Math.max(0.0F, Math.min(1.0F, state.value[i] + amount * scale(nature(mob), emotion, amount)));
    }

    /** Something happened to it (a new memory): how would it feel about that? */
    public static void appraise(LivingEntity mob, String event) {
        String text = event.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (text.contains(rule.words)) {
                for (int i = 0; i < rule.emotions.length; i++) {
                    feel(mob, rule.emotions[i], rule.amounts[i]);
                }
            }
        }
    }

    /** It heard the demon say this to it. */
    public static void heard(MobEntity mob, Intent intent, String message, boolean kin) {
        String text = Intent.normalize(message);
        switch (intent) {
            case TAUNT -> {
                feel(mob, Emotion.ANGER, 0.35F);
                feel(mob, Emotion.FEAR, 0.2F);
                feel(mob, Emotion.SHAME, 0.1F);
            }
            case APOLOGY, FORGIVE -> {
                feel(mob, Emotion.CONFUSION, 0.35F);
                feel(mob, Emotion.PITY, 0.15F);
                feel(mob, Emotion.ANGER, -0.2F);
            }
            case GREET -> feel(mob, Emotion.CURIOSITY, 0.1F);
            case FAMILY -> {
                feel(mob, Emotion.FEAR, 0.25F);
                feel(mob, Emotion.LONGING, 0.2F);
            }
            case HELP -> feel(mob, Emotion.PITY, 0.2F);
            case IDENTITY, QUESTION, SECRET -> feel(mob, Emotion.CURIOSITY, 0.1F);
            default -> {
            }
        }
        if (intent.isCommand()) {
            feel(mob, Emotion.FEAR, 0.2F);
            feel(mob, Emotion.DEFIANCE, 0.25F);
        }
        if (containsAny(text, "kill", "hurt", "die", "dead", "eat you", "blood", "skin", "bones", "rot", "scream")) {
            feel(mob, Emotion.FEAR, 0.35F);
            feel(mob, Emotion.DREAD, 0.2F);
        }
        if (containsAny(text, "please", "sorry", "thank", "friend", "kind", "safe", "love")) {
            feel(mob, Emotion.TRUST, 0.08F);
            feel(mob, Emotion.CONFUSION, 0.1F);
        }
        if (kin) {
            feel(mob, Emotion.PITY, 0.1F);
            feel(mob, Emotion.GRIEF, 0.08F);
        }
    }

    // --- reading them ----------------------------------------------------------------------------------------

    /** For the AI: how it feels right now, strongest first, and how that shows. Empty if nothing much. */
    public static String describe(LivingEntity mob) {
        State state = state(mob);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ALL.length; i++) {
            if (state.value[i] >= 0.12F) {
                order.add(i);
            }
        }
        if (order.isEmpty()) {
            return "";
        }
        order.sort((a, b) -> Float.compare(state.value[b], state.value[a]));
        StringBuilder text = new StringBuilder("Your feelings right now (mixed, and they can shift as this goes on): ");
        StringBuilder shows = new StringBuilder();
        for (int n = 0; n < Math.min(4, order.size()); n++) {
            Emotion emotion = ALL[order.get(n)];
            float amount = state.value[order.get(n)];
            text.append(n == 0 ? "" : n == 1 ? "; underneath that, " : "; and ").append(emotion.word(amount));
            if (n < 2) {
                shows.append(n == 0 ? "" : "; ").append(emotion.shows);
            }
        }
        return text.append(". It comes out in how you talk: ").append(shows).append('.').toString();
    }

    /** The strongest feeling, or null if it's calm. */
    public static Emotion strongest(LivingEntity mob) {
        State state = state(mob);
        int best = -1;
        for (int i = 0; i < ALL.length; i++) {
            if (state.value[i] >= 0.25F && (best < 0 || state.value[i] > state.value[best])) {
                best = i;
            }
        }
        return best < 0 ? null : ALL[best];
    }

    public static float amount(LivingEntity mob, Emotion emotion) {
        return state(mob).value[emotion.ordinal()];
    }

    /** The built-in voice's mood: its strongest feeling, if it has one, else the usual mood. */
    public static Phrasebook.Mood mood(LivingEntity mob, Phrasebook.Mood usual) {
        if (usual == Phrasebook.Mood.PROUD) {
            return usual;
        }
        Emotion emotion = strongest(mob);
        if (emotion == null) {
            return usual;
        }
        float amount = amount(mob, emotion);
        return switch (emotion) {
            case FEAR, DREAD -> amount >= 0.55F ? Phrasebook.Mood.TERRIFIED : Phrasebook.Mood.NERVOUS;
            case ANGER, DEFIANCE, DISGUST -> Phrasebook.Mood.HOSTILE;
            case GRIEF, PITY, LONGING -> usual == Phrasebook.Mood.KIN ? usual : Phrasebook.Mood.GRIEVING;
            case HOPE, TRUST, RELIEF -> Phrasebook.Mood.HOPEFUL;
            case CURIOSITY -> Phrasebook.Mood.CURIOUS;
            case SHAME -> Phrasebook.Mood.ASHAMED;
            case CONFUSION -> Phrasebook.Mood.NERVOUS;
        };
    }

    /** For the AI: who it is underneath. */
    public static String natureText(LivingEntity mob) {
        Nature n = nature(mob);
        List<String> traits = new ArrayList<>();
        traits.add(n.courage() > 0.66F ? "braver than most" : n.courage() < 0.33F ? "timid" : "no braver than anyone");
        traits.add(n.warmth() > 0.66F ? "warm-hearted" : n.warmth() < 0.33F ? "cold and guarded" : "kind enough, in your way");
        if (Persona.of(mob) == Persona.VILLAGER || Persona.of(mob) == Persona.ILLAGER) {
            traits.add(n.faith() > 0.66F ? "devout" : n.faith() < 0.33F ? "never much of a believer" : "you believe, mostly");
        }
        traits.add(n.temper() > 0.66F ? "quick to anger" : n.temper() < 0.33F ? "slow to anger" : "even-tempered");
        if (n.humour() > 0.7F) {
            traits.add("with a dry sense of humour that comes out even now");
        }
        return "Who you are: " + String.join(", ", traits) + ". How you talk: " + n.voice() + " Your life: " + n.life();
    }

    public static Nature nature(LivingEntity mob) {
        Nature known = NATURES.get(mob.getUuid());
        if (known != null) {
            return known;
        }
        UUID id = mob.getUuid();
        // Scramble the id well first: similar ids must still give very different people
        long z = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 32);
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        Random random = new Random(z ^ (z >>> 33));
        Persona persona = Persona.of(mob);
        String[] voices = VOICES.getOrDefault(persona, VOICES.get(Persona.MONSTER));
        String[] lives = LIVES.getOrDefault(persona, LIVES.get(Persona.MONSTER));
        Nature nature = new Nature(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat(),
                voices[random.nextInt(voices.length)], lives[random.nextInt(lives.length)]);
        if (NATURES.size() > 4000) {
            NATURES.clear();
        }
        NATURES.put(id, nature);
        return nature;
    }

    /** The trapped soul's feelings, from how far the possession has gone. */
    public static String soul(MobEntity host, int stage) {
        float drain = Stages.drain(host);
        String feelings = switch (stage) {
            case Stages.INFESTATION -> "furious and terrified at once, still sure you can fight it off; disgusted at the feel of it inside you";
            case Stages.OPPRESSION -> "frightened, worn out and ashamed of how much you're begging; one moment you hope it will listen, the next you're angry again";
            default -> "past fear now, mostly: sad about your own life, missing the people you love, but a small part of you still won't give up";
        };
        String body = drain >= 0.9F ? " Your body is falling apart; you can barely feel it."
                : drain >= 0.75F ? " You can feel your body rotting, and it horrifies you."
                : drain >= 0.5F ? " You can feel your strength draining away into it." : "";
        return "Your feelings: " + feelings + "." + body;
    }

    public static void forget(UUID mob) {
        STATES.remove(mob);
    }

    // --- inside ----------------------------------------------------------------------------------------------

    private static State state(LivingEntity mob) {
        long now = mob.world.getTime();
        State state = STATES.get(mob.getUuid());
        if (state == null) {
            if (STATES.size() > 3000) {
                STATES.clear();
            }
            state = new State();
            state.at = now;
            STATES.put(mob.getUuid(), state);
            // A creature met again (after a restart) still feels what it remembers, faded by how long ago it was
            Deque<Witness.Event> remembered = Witness.all().getOrDefault(mob.getUuid(), new ArrayDeque<>());
            for (Witness.Event event : remembered) {
                float[] before = state.value.clone();
                appraise(mob, event.text());
                long ago = Math.max(0L, now - event.time());
                for (int i = 0; i < ALL.length; i++) {
                    float added = state.value[i] - before[i];
                    state.value[i] = before[i] + added * (float) Math.pow(0.5, ago / (double) ALL[i].halfLife);
                }
            }
            return state;
        }
        long dt = now - state.at;
        if (dt > 0) {
            for (int i = 0; i < ALL.length; i++) {
                state.value[i] *= (float) Math.pow(0.5, dt / (double) ALL[i].halfLife);
            }
            state.at = now;
        }
        return state;
    }

    /** Its nature colours what it feels: the brave fear less, the hot-headed anger more, the warm pity more. */
    private static float scale(Nature n, Emotion emotion, float amount) {
        if (amount < 0.0F) {
            return 1.0F;
        }
        return switch (emotion) {
            case FEAR, DREAD -> 1.3F - n.courage() * 0.6F;
            case ANGER, DEFIANCE -> 0.6F + n.temper() * 0.8F;
            case PITY, GRIEF, LONGING -> 0.6F + n.warmth() * 0.8F;
            case HOPE -> 0.6F + n.faith() * 0.7F;
            case TRUST -> 0.5F + n.warmth() * 0.6F;
            default -> 1.0F;
        };
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private record Rule(String words, Emotion[] emotions, float[] amounts) {
    }

    private static final List<Rule> RULES = new ArrayList<>();

    private static void rule(String words, Object... pairs) {
        Emotion[] emotions = new Emotion[pairs.length / 2];
        float[] amounts = new float[pairs.length / 2];
        for (int i = 0; i < emotions.length; i++) {
            emotions[i] = (Emotion) pairs[i * 2];
            amounts[i] = ((Number) pairs[i * 2 + 1]).floatValue();
        }
        RULES.add(new Rule(words, emotions, amounts));
    }

    private static final Map<Persona, String[]> VOICES = new HashMap<>();
    private static final Map<Persona, String[]> LIVES = new HashMap<>();

    static {
        // What it saw or had done to it (matched against the memory's wording), and how that lands
        rule("help me", Emotion.PITY, 0.5, Emotion.DREAD, 0.4, Emotion.FEAR, 0.25);
        rule("shriek", Emotion.FEAR, 0.5, Emotion.DREAD, 0.4, Emotion.DISGUST, 0.2);
        rule("convulse", Emotion.FEAR, 0.35, Emotion.DREAD, 0.3, Emotion.PITY, 0.2);
        rule("mocked you", Emotion.ANGER, 0.4, Emotion.FEAR, 0.2, Emotion.SHAME, 0.15);
        rule("hit you", Emotion.FEAR, 0.4, Emotion.ANGER, 0.4);
        rule(" kill ", Emotion.FEAR, 0.7, Emotion.GRIEF, 0.4, Emotion.DREAD, 0.5);
        rule(" attack ", Emotion.FEAR, 0.4, Emotion.ANGER, 0.25);
        rule("holy water burn", Emotion.HOPE, 0.3, Emotion.FEAR, 0.15);
        rule("head twist", Emotion.FEAR, 0.6, Emotion.DREAD, 0.5, Emotion.DISGUST, 0.3);
        rule("bile", Emotion.DISGUST, 0.7, Emotion.FEAR, 0.2);
        rule("bed shook", Emotion.FEAR, 0.5, Emotion.CONFUSION, 0.3);
        rule("whole bed shook", Emotion.DREAD, 0.3);
        rule("rigid as a board", Emotion.FEAR, 0.5, Emotion.DREAD, 0.5, Emotion.CONFUSION, 0.3);
        rule("real face", Emotion.FEAR, 0.9, Emotion.DREAD, 0.6);
        rule("scratching inside the walls", Emotion.DREAD, 0.3, Emotion.FEAR, 0.15, Emotion.CONFUSION, 0.15);
        rule("footsteps in the attic", Emotion.DREAD, 0.35, Emotion.FEAR, 0.2);
        rule("spirit board", Emotion.CURIOSITY, 0.4, Emotion.TRUST, 0.2, Emotion.DREAD, 0.1);
        rule("could come inside you", Emotion.TRUST, 0.4, Emotion.LONGING, 0.2);
        rule("out of the fog", Emotion.HOPE, 0.45, Emotion.DREAD, 0.2);
        rule("cast the demon out", Emotion.RELIEF, 0.8, Emotion.HOPE, 0.5, Emotion.FEAR, -0.5, Emotion.DREAD, -0.4);
        rule("break and run", Emotion.FEAR, 0.5, Emotion.GRIEF, 0.2, Emotion.HOPE, -0.4);
        rule("faith broke", Emotion.SHAME, 0.7, Emotion.FEAR, 0.5, Emotion.GRIEF, 0.3);
        rule("sounded like itself", Emotion.HOPE, 0.5, Emotion.PITY, 0.4, Emotion.TRUST, 0.3, Emotion.CONFUSION, 0.3);
        rule("someone you lost", Emotion.LONGING, 0.8, Emotion.GRIEF, 0.6, Emotion.CONFUSION, 0.4);
        rule("offer himself", Emotion.GRIEF, 0.6, Emotion.DREAD, 0.5);
        rule("forced its way inside", Emotion.FEAR, 0.8, Emotion.CONFUSION, 0.5);
        rule("hours missing", Emotion.CONFUSION, 0.8, Emotion.FEAR, 0.3, Emotion.RELIEF, 0.3);
        rule("forced you to obey", Emotion.SHAME, 0.4, Emotion.FEAR, 0.4, Emotion.ANGER, 0.3);
        rule("old and cold", Emotion.DREAD, 0.4);

        VOICES.put(Persona.VILLAGER, new String[]{
                "you talk fast when you're nervous and trail off halfway through sentences.",
                "you go very quiet when you're scared: a few words at a time.",
                "you make small, dry jokes when you're frightened; it's how you cope.",
                "you're polite to a fault, even now; it's how you were raised.",
                "you mutter little prayers under your breath when you're afraid.",
                "you get angry instead of scared, and say things you'd normally never say.",
                "you repeat yourself when you panic.",
                "you ask a lot of questions, nervous ones.",
                "you're blunt and plain-spoken, a practical person.",
                "you bring up your family whenever you're frightened."});
        VOICES.put(Persona.ILLAGER, new String[]{
                "you speak coldly and sparingly, and sneer when you're unsure of yourself.",
                "you boast when you're afraid, too loudly.",
                "you're curt and suspicious, always looking for the trick."});
        VOICES.put(Persona.WITCH, new String[]{
                "you cackle at the wrong moments and talk in sly riddles.",
                "you're sharp-tongued and amused, until something truly frightens you."});
        VOICES.put(Persona.UNDEAD, new String[]{
                "you speak slowly, in broken pieces, groping for words you used to know.",
                "you talk in short, hungry, mumbling bursts, but now and then a whole clear sentence surfaces."});
        VOICES.put(Persona.SKELETON, new String[]{
                "you're dry and sarcastic; gallows humour is all you have left.",
                "you talk in clipped, rattling sentences."});
        VOICES.put(Persona.PIGLIN, new String[]{
                "you talk in short, blunt grunts and think about gold.",
                "you bluster and snort when you're nervous."});
        VOICES.put(Persona.GOLEM, new String[]{
                "you speak plainly and slowly, a few heavy words at a time.",
                "you speak like a guard on duty: short, steady, protective."});
        VOICES.put(Persona.PET, new String[]{
                "you talk in eager, simple bursts, and whimper when you're scared.",
                "you're simple and loyal, and say exactly what you feel."});
        VOICES.put(Persona.ANIMAL, new String[]{
                "you think and speak simply: food, safety, your little ones.",
                "you speak in short, skittish fragments."});
        VOICES.put(Persona.MONSTER, new String[]{
                "you're mocking and cruel, but your voice slips when you're uneasy.",
                "you hiss and sneer, and speak in short bursts.",
                "you're quiet and watchful, and choose few words."});

        LIVES.put(Persona.VILLAGER, new String[]{
                "you're worried sick about this year's harvest.",
                "you have two small children at home and they're all you think about.",
                "you lost your brother to the zombies years ago and you never talk about it.",
                "you've been secretly saving emeralds to leave this village one day.",
                "you're the one everyone comes to for gossip, and you know it.",
                "you haven't spoken to your sister since a quarrel last winter, and you regret it.",
                "you were married this spring and you still can't believe your luck.",
                "you were sick as a child and the cleric nursed you back to health; you'd do anything for him.",
                "you never believed in demons. Until today.",
                "you talk to the iron golem at night, because nobody else listens to you.",
                "your mother died last year and you still set a place for her at the table.",
                "you're proud of your work and hate leaving it unfinished."});
        LIVES.put(Persona.ILLAGER, new String[]{
                "you joined the raiders young, and sometimes wonder about the village you came from.",
                "you owe the captain your life and resent it.",
                "you've seen evokers do terrible things, but nothing like this."});
        LIVES.put(Persona.WITCH, new String[]{
                "you've dabbled in dark things yourself, and you know this is far beyond you.",
                "you live alone in the swamp and you like it that way."});
        LIVES.put(Persona.UNDEAD, new String[]{
                "you remember a warm kitchen and someone laughing, and nothing else.",
                "you remember being someone's father. You think.",
                "you remember the sun used to feel good."});
        LIVES.put(Persona.SKELETON, new String[]{
                "you can't remember who you were, and you've stopped trying.",
                "you're sure you were an archer before, too."});
        LIVES.put(Persona.PIGLIN, new String[]{
                "you once found a whole gold block and you still dream about it.",
                "you hate the Overworld; it's cold and it makes your skin crawl."});
        LIVES.put(Persona.GOLEM, new String[]{
                "you were built by these villagers, and you count them every night.",
                "you've buried villagers before, and you swore you never would again."});
        LIVES.put(Persona.PET, new String[]{
                "you love your owner more than anything and hate being apart from them.",
                "you were a stray before someone took you in, and you never forgot it."});
        LIVES.put(Persona.ANIMAL, new String[]{
                "you have little ones nearby and you never stop watching them.",
                "you're old, and you've seen wolves, but never anything like this."});
        LIVES.put(Persona.MONSTER, new String[]{
                "you live in the dark and you thought you were the worst thing in it.",
                "you've hunted for so long you've forgotten anything else."});
    }
}
