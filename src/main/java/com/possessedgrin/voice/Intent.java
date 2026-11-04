package com.possessedgrin.voice;

import java.util.Locale;

/**
 * What you meant by a chat message, worked out from its words. Checked in order, so a taunt wins over a question
 * and "sorry" wins over "hello".
 *
 * <p>Keywords match whole words ("rain" doesn't match "brain"); a keyword ending in {@code *} matches the start of
 * a word ("apolog*" matches "apologise" and "apology"). Apostrophes are ignored, so write "youre", "ill", "its".
 */
public enum Intent {
    TAUNT("stupid", "idiot*", "ugly", "weak", "weakling", "pathetic", "loser", "dumb", "fool", "foolish", "coward*", "worthless",
            "useless", "ill kill", "kill you", "hate you", "noob", "trash", "you stink", "stinky", "shut up", "go die",
            "drop dead", "disgusting", "freak", "puny", "nobody likes you", "ill eat you", "fear me", "bow to me", "kneel",
            "worm", "maggot", "youre nothing", "scum", "filth*", "imbecile", "moron", "clown", "laugh at you",
            "gonna kill", "going to kill", "i will kill", "destroy you", "burn you", "rip you", "boo"),
    APOLOGY("sorry", "apolog*", "my bad", "pardon me", "i didnt mean", "forgive me"),
    FORGIVE("i forgive", "forgive you", "its okay", "its ok", "its fine", "i understand"),
    RELEASE("let you go", "let go", "leave your body", "set you free", "release*", "free you", "get out", "leave you"),
    FOLLOW("follow*", "come with me", "come here", "come along", "stay close", "with me"),
    STAY("stay", "wait", "stop following", "go away", "leave me alone", "sit", "halt"),
    ATTACK("attack*", "kill that", "kill him", "kill her", "kill it", "kill them", "get him", "get her", "get it",
            "get them", "fight*", "sic"),
    HELP("help*", "heal*", "save me", "assist*", "protect me"),
    GOODBYE("goodbye", "bye", "farewell", "see you", "see ya", "good night", "later"),
    GREET("hello", "hi", "hey", "greetings", "good morning", "good evening", "howdy", "yo", "sup", "hiya"),
    IDENTITY("who are you", "what are you", "your name", "who r u", "what r u", "introduce*"),
    FEELINGS("how are you", "how do you feel", "are you ok", "are you okay", "you ok", "you okay", "feeling*",
            "hows it going", "are you happy", "are you sad", "how are u", "you alright"),
    FEAR("afraid", "scared", "fear*", "frighten*", "terrif*"),
    TRADE("trade*", "sell*", "buy*", "emerald*", "price*", "offer*", "barter*", "deal"),
    WORK("job*", "work*", "profession", "do all day", "what do you do", "occupation"),
    HOME("where do you live", "where you live", "your home", "your house", "your bed", "where do you sleep", "home*"),
    ENEMY("enemy", "enemies", "who do you hate", "who do you hunt", "hunting", "your target", "who are you after",
            "angry at", "mad at", "rival*"),
    FAMILY("family", "mother", "father", "mom", "mum", "dad", "child*", "kid", "kids", "baby", "babies", "friend*",
            "brother*", "sister*", "herd", "pack", "wife", "husband", "parents"),
    OWNER("owner", "master", "who owns", "tamed", "belong to"),
    LOCATION("where am i", "where are we", "where is", "where are you", "which way", "direction*", "biome",
            "coordinates"),
    TIME("what time", "the time", "time is it", "what day", "is it night", "is it day", "how late", "clock"),
    WEATHER("weather", "rain*", "storm*", "thunder*", "sunny", "snow*"),
    FOOD("hungry", "food", "eat*", "favorite food", "dinner", "starving", "meal"),
    SEES("what do you see", "see anything", "can you see", "notice*", "anyone around", "whos around", "who is around",
            "nearby", "around here", "see"),
    OPINION("think of me", "think about me", "do you like me", "like me", "about me", "am i", "trust me", "love me"),
    SECRET("secret*", "tell me something", "hiding", "treasure*", "diamond*", "loot", "hidden", "what do you know"),
    QUESTION(),
    STATEMENT();

    private static final String[] QUESTION_WORDS = {"what", "why", "how", "who", "where", "when", "which", "can",
            "do", "does", "is", "are", "will", "would", "should", "could", "have", "did"};

    private final String[] keywords;

    Intent(String... keywords) {
        this.keywords = keywords;
    }

    /** Lower case, apostrophes dropped, everything else that isn't a letter or digit turned into single spaces. */
    public static String normalize(String message) {
        String lower = message.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "");
        StringBuilder out = new StringBuilder(" ");
        boolean space = true;
        for (char c : lower.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
                space = false;
            } else if (!space) {
                out.append(' ');
                space = true;
            }
        }
        if (!space) {
            out.append(' ');
        }
        return out.toString();
    }

    public static Intent of(String message) {
        String text = normalize(message);
        for (Intent intent : values()) {
            for (String keyword : intent.keywords) {
                if (matches(text, keyword)) {
                    return intent;
                }
            }
        }
        if (message.contains("?")) {
            return QUESTION;
        }
        for (String word : QUESTION_WORDS) {
            if (text.startsWith(" " + word + " ")) {
                return QUESTION;
            }
        }
        return STATEMENT;
    }

    static boolean matches(String normalizedText, String keyword) {
        return keyword.endsWith("*")
                ? normalizedText.contains(" " + keyword.substring(0, keyword.length() - 1))
                : normalizedText.contains(" " + keyword + " ");
    }

    /** Questions with a true answer the game knows (the real AI is told it, so it never makes one up). */
    public boolean isFactual() {
        return switch (this) {
            case TIME, WEATHER, LOCATION, HOME, WORK, TRADE, SEES, OWNER, ENEMY, SECRET -> true;
            default -> false;
        };
    }

    /** Commands are only obeyed once the possession is complete. */
    public boolean isCommand() {
        return this == FOLLOW || this == STAY || this == ATTACK;
    }
}
