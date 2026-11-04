package com.possessedgrin.voice;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.stage.Stages;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * The built-in reply generator. It never needs the internet: it builds each reply from pieces, so the same question
 * rarely gets the same answer twice.
 * <ul>
 *   <li>An <b>opener</b> that fits the creature's mood (calm, nervous, terrified, hostile, proud, kin who
 *       recognise the body you're wearing, grieving, hopeful, curious or ashamed), which comes from its strongest
 *       feeling right now ({@link Feelings}), or else from the stage of the possession and the creature's nature.</li>
 *   <li>The <b>core</b> answer: real facts from the game, or a line in the creature's own voice.</li>
 *   <li>Sometimes an <b>aside</b> after it, in the same mood.</li>
 *   <li>For questions it has no answer to, it picks out what you asked about and answers about <i>that</i>, in
 *       its own voice ("Diamonds? Is diamonds gold? No? Then no.").</li>
 * </ul>
 * Every piece is original.
 */
public final class Phrasebook {
    public enum Mood {
        CALM("Uneasy but steady. You keep your guard up and answer carefully."),
        NERVOUS("Nervous and wary. Something is very wrong with it and you want this over."),
        TERRIFIED("Terrified. You're shaking and you just want it to leave you alone."),
        HOSTILE("Angry and hostile. You don't trust it and you're ready to fight."),
        PROUD("Unimpressed. You hold this little demon in contempt."),
        KIN("Heartbroken and confused. It's wearing one of your own, and part of you wants to help them."),
        GRIEVING("Grieving. Whatever is wearing that body, you can't stop thinking of who it used to be."),
        HOPEFUL("Fragile hope. You want so badly to believe this can end, or that they're still in there."),
        CURIOUS("Curious despite yourself. You're frightened, but you need to understand what it is."),
        ASHAMED("Ashamed. You've done something, or failed to, and it knows.");

        /** For the real AI: how the creature feels about you. */
        public final String feeling;

        Mood(String feeling) {
            this.feeling = feeling;
        }
    }

    private static final Map<Mood, String[]> OPENERS = new EnumMap<>(Mood.class);
    private static final Map<Mood, String[]> ASIDES = new EnumMap<>(Mood.class);
    private static final Map<Persona, String[]> TOPIC_LINES = new EnumMap<>(Persona.class);
    private static final String[] SOUL_TOPIC_EARLY = {
            "{Topic}? How can I think about {topic} with you inside me?",
            "Don't ask me about {topic}! Just get out!",
            "{Topic}... I don't care about {topic}. I want my body back."};
    private static final String[] SOUL_TOPIC_LATE = {
            "...{topic}... I used to know what that was...",
            "...{topic}...? ...it's so far away now...",
            "...I can't remember {topic}... I can't remember anything..."};

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "you", "your", "youre", "yours", "i", "me", "my", "mine", "im", "ive", "id", "do", "does", "did",
            "is", "are", "was", "were", "be", "been", "am", "what", "whats", "why", "how", "who", "whos", "where", "when", "which",
            "can", "could", "would", "will", "should", "shall", "may", "might", "about", "think", "of", "to", "in", "on", "at",
            "for", "and", "or", "but", "it", "its", "that", "thats", "this", "these", "those", "there", "here", "have", "has",
            "had", "any", "some", "like", "know", "tell", "please", "hey", "ok", "okay", "so", "just", "really", "very", "we",
            "us", "our", "they", "them", "their", "he", "she", "him", "her", "his", "not", "no", "yes", "much", "many", "more",
            "most", "ever", "never", "get", "got", "go", "going", "want", "need", "if", "then", "with", "from", "as", "by",
            "dont", "cant", "wont", "isnt", "arent", "didnt", "doesnt", "all", "out", "up", "down", "over", "into", "too",
            "also", "say", "said", "thing", "things", "something", "anything", "everything", "one", "now", "still", "again",
            "make", "made", "let", "lets", "soul", "demon", "hello", "hi");

    private Phrasebook() {
    }

    public static Mood mood(Persona persona, int stage, boolean kin, boolean angry) {
        if (persona == Persona.BOSS) {
            return Mood.PROUD;
        }
        if (kin && !angry) {
            return Mood.KIN;
        }
        if (angry) {
            return Mood.HOSTILE;
        }
        if (persona.fighter()) {
            return stage >= Stages.POSSESSION && persona != Persona.ILLAGER ? Mood.NERVOUS : Mood.CALM;
        }
        return stage >= Stages.OPPRESSION ? Mood.TERRIFIED : Mood.NERVOUS;
    }

    /** Wraps a core answer in an opener and maybe an aside that fit the mood. */
    public static String dress(String core, Mood mood, Random random) {
        String line = core;
        boolean coreHasAction = core.startsWith("*");
        if (random.nextFloat() < 0.55F) {
            String opener = pick(OPENERS.get(mood), random);
            boolean openerIsAction = opener.startsWith("*");
            if (!opener.isEmpty() && !(openerIsAction && coreHasAction)) {
                line = opener + " " + line;
            }
        }
        if (random.nextFloat() < 0.4F && !line.endsWith("?")) {
            line = line + " " + pick(ASIDES.get(mood), random);
        }
        return line;
    }

    /** An answer about whatever you asked about, in the creature's voice; null if nothing stood out. */
    @Nullable
    public static String aboutTopic(Persona persona, String message, @Nullable LivingEntity speaker, @Nullable MobEntity host, Random random) {
        String topic = topic(message, speaker, host);
        return topic == null ? null : fillTopic(pick(TOPIC_LINES.get(persona), random), topic);
    }

    /** The trapped soul's version. */
    @Nullable
    public static String soulAboutTopic(String message, MobEntity host, boolean late, Random random) {
        String topic = topic(message, host, host);
        return topic == null ? null : fillTopic(pick(late ? SOUL_TOPIC_LATE : SOUL_TOPIC_EARLY, random), topic);
    }

    /** The most telling word in the message: the longest that isn't filler or the name of who's talking. */
    @Nullable
    static String topic(String message, @Nullable LivingEntity speaker, @Nullable MobEntity host) {
        String best = null;
        for (String word : Intent.normalize(message).trim().split(" ")) {
            if (word.length() < 3 || STOP_WORDS.contains(word) || word.chars().allMatch(Character::isDigit)) {
                continue;
            }
            if ((speaker != null && PossessedGrin.kind(speaker).contains(word)) || (host != null && PossessedGrin.kind(host).contains(word))) {
                continue;
            }
            if (best == null || word.length() > best.length()) {
                best = word;
            }
        }
        return best;
    }

    private static String fillTopic(String template, String topic) {
        String capital = Character.toUpperCase(topic.charAt(0)) + topic.substring(1);
        return template.replace("{Topic}", capital).replace("{topic}", topic);
    }

    private static String pick(String[] options, Random random) {
        return options[random.nextInt(options.length)];
    }

    static {
        OPENERS.put(Mood.CALM, new String[]{"", "Hm.", "Well.", "Let me think.", "Ah.", "Listen.", "*considers you*", "Right."});
        OPENERS.put(Mood.NERVOUS, new String[]{"Um.", "W-well...", "*glances around*", "*shifts uneasily*", "Er...",
                "*clears its throat*", "*keeps its distance*", "Oh. Um."});
        OPENERS.put(Mood.TERRIFIED, new String[]{"*shaking*", "P-please...", "*backs away*", "*voice cracking*",
                "*won't look at you*", "*whispering*", "*flinches*", "Oh no. Oh no."});
        OPENERS.put(Mood.HOSTILE, new String[]{"*snarls*", "Tch.", "Listen here, demon.", "*bares its teeth*", "You again.",
                "*glares*", "Back off.", "*growls*"});
        OPENERS.put(Mood.PROUD, new String[]{"...", "Hm.", "Small one.", "*does not look at you*", "Mortal thing.", "*a slow, heavy breath*"});
        OPENERS.put(Mood.KIN, new String[]{"*looks at you sadly*", "Brother...", "*sniffs at you, confused*",
                "It's still you in there... isn't it?", "*reaches toward you, then stops*", "Oh, look at you..."});

        OPENERS.put(Mood.GRIEVING, new String[]{"*swallows hard*", "*can't look at you for long*", "Oh...", "*voice thick*",
                "*wipes its eyes*", "I keep thinking...", "*very quietly*"});
        OPENERS.put(Mood.HOPEFUL, new String[]{"*takes a step closer, then stops*", "Is that... are you...", "*hardly daring to breathe*",
                "Wait.", "*searches your face*", "Please, let this be real."});
        OPENERS.put(Mood.CURIOUS, new String[]{"*tilts its head*", "Hm. Strange.", "*leans in, despite itself*", "What are you, really?",
                "*watching you closely*"});
        OPENERS.put(Mood.ASHAMED, new String[]{"*looks at the ground*", "I... I didn't...", "*face burning*", "Don't.",
                "*won't meet your eyes*"});

        ASIDES.put(Mood.GRIEVING, new String[]{"I miss who you were.", "This isn't fair.", "*sniffs*", "They didn't deserve this.",
                "Give them back. Please."});
        ASIDES.put(Mood.HOPEFUL, new String[]{"Tell me it's over.", "You're still in there, aren't you?", "Please be you.",
                "We can fix this. Can't we?"});
        ASIDES.put(Mood.CURIOUS, new String[]{"Where did you come from?", "Why this body?", "I've never seen anything like you.",
                "What do you want?"});
        ASIDES.put(Mood.ASHAMED, new String[]{"Don't tell anyone.", "How do you know that?", "Stop it.", "That was a long time ago."});

        ASIDES.put(Mood.CALM, new String[]{"Why do you ask?", "Not that it's your business.", "That's all I know.",
                "Strange question.", "Make of that what you will.", "Anything else?"});
        ASIDES.put(Mood.NERVOUS, new String[]{"Can I go now?", "Why are you looking at me like that?", "Please don't come closer.",
                "Is that what you wanted to hear?", "I really should be going.", "Your eyes are... never mind."});
        ASIDES.put(Mood.TERRIFIED, new String[]{"Please don't hurt me.", "I'll tell you anything.", "Just leave us alone.",
                "Please...", "Don't make me look at your eyes.", "I won't tell anyone, I swear.", "Please go away."});
        ASIDES.put(Mood.HOSTILE, new String[]{"Now get lost.", "And stay away from me.", "Don't push your luck.",
                "Count yourself lucky I'm talking at all.", "Next time I won't be so polite.", "Leave."});
        ASIDES.put(Mood.PROUD, new String[]{"Now be silent.", "Do not waste my time again.", "That is all you get.",
                "Remember who you speak to."});
        ASIDES.put(Mood.KIN, new String[]{"What happened to you?", "Let us help you.", "Come home. Please.",
                "You're scaring the others.", "We miss you.", "Fight it. Please fight it."});

        TOPIC_LINES.put(Persona.VILLAGER, new String[]{
                "{Topic}? Hrm. I'm a simple {me}; I know crops, not {topic}.",
                "Ask the librarian about {topic}. I just work here.",
                "{Topic}... my cousin talked about {topic} once. He was never right about anything.",
                "Why would something like you care about {topic}?",
                "{Topic}? We don't talk about {topic} in this village."});
        TOPIC_LINES.put(Persona.ILLAGER, new String[]{
                "{Topic}? If it's worth taking, we'll take it.",
                "I don't care about {topic}. I care about the next raid.",
                "{Topic}. The captain might know. I'm not asking him."});
        TOPIC_LINES.put(Persona.WITCH, new String[]{
                "{Topic}? Hee hee... I could brew you something for {topic}.",
                "Bring me {topic} and a spider eye, and we'll talk.",
                "Oh, {topic}. I cursed someone over {topic} once. Good times."});
        TOPIC_LINES.put(Persona.UNDEAD, new String[]{
                "{Topic}...? Hhhrr... can't... eat... {topic}.",
                "{Topic}... I remember... {topic}... from before...",
                "Hhhrr... {topic}... doesn't matter... when you're dead."});
        TOPIC_LINES.put(Persona.SKELETON, new String[]{
                "{Topic}? Clack. Can I shoot it?",
                "I had opinions about {topic} once. They rotted off with the rest of me.",
                "*rattle* {Topic}. Haven't thought about {topic} since I had a brain."});
        TOPIC_LINES.put(Persona.PIGLIN, new String[]{
                "{Topic}? Is {topic} gold? No? Then no.",
                "Hrnk. {Topic} worth gold? Piglin only cares if worth gold.",
                "Snrrk. {Topic}. Brutes would just hit {topic}."});
        TOPIC_LINES.put(Persona.CREEPER, new String[]{
                "Sssss... {topic}... sounds... explosive.",
                "{Topic}? I'd hug it. Just once.",
                "Ssss... I don't know {topic}. I only know waiting."});
        TOPIC_LINES.put(Persona.SPIDER, new String[]{
                "Chhh... {topic}. Is it slow? Is it warm?",
                "{Topic} would fit nicely in my web.",
                "Skitter. {Topic} means nothing in the dark."});
        TOPIC_LINES.put(Persona.ENDERMAN, new String[]{
                "...{topic}... I could move it somewhere else.",
                "...{topic} means nothing in the End.",
                "Hrrrm... {topic}... I saw one once. Then I didn't."});
        TOPIC_LINES.put(Persona.GOLEM, new String[]{
                "*grind* {Topic}: no threat detected.",
                "*clank* {Topic} is not in my orders.",
                "*grind* Query about {topic} logged. Ignored."});
        TOPIC_LINES.put(Persona.PET, new String[]{
                "*head tilt* {Topic}? Is {topic} a treat?",
                "{Topic}! I don't know what that is but I'm excited!",
                "*sniffs* Smells like you've been near {topic}. Or not. I'm a pet."});
        TOPIC_LINES.put(Persona.ANIMAL, new String[]{
                "*chews slowly* {Topic}? Never heard of it.",
                "Is {topic} something you can eat?",
                "{Topic}... the farmer says that word sometimes. I don't like it."});
        TOPIC_LINES.put(Persona.MONSTER, new String[]{
                "{Topic}? Never cared about {topic}.",
                "{Topic}... sounds like something to break.",
                "What do I care about {topic}? I live in the dark."});
        TOPIC_LINES.put(Persona.BOSS, new String[]{
                "{Topic}. Beneath my notice.",
                "I have watched {topic} turn to dust before.",
                "You speak of {topic} to ME?"});
    }
}
