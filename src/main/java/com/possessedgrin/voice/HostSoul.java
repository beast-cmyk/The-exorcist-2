package com.possessedgrin.voice;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.stage.Stages;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The soul of the creature you're wearing is still in there with you. It speaks up every half minute or so: at
 * first it fights and demands you leave, then it bargains and begs forgiveness, and at the end it fades to a
 * whisper. Talk to it in chat (when you're not addressing anyone else, or say "soul") and it answers.
 */
public final class HostSoul {
    private enum Voice {PERSON, UNDEAD, BEAST, PET, GUARDIAN, BOSS}

    private enum Topic {S1, S2, S3, SCREAM2, SCREAM3, APOLOGY, FORGIVE, RELEASE, TAUNT, GREET, QUESTION, IDENTITY, FEELINGS, DRAIN, FINAL}

    /** Per voice, per topic: lines. For answers, [0] is what it says early on (stages I and II), [1] at the end. */
    private static final Map<Voice, Map<Topic, String[]>> LINES = new EnumMap<>(Voice.class);
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private HostSoul() {
    }

    private static Voice voiceOf(MobEntity host) {
        return switch (Persona.of(host)) {
            case VILLAGER, ILLAGER, WITCH, PIGLIN -> Voice.PERSON;
            case UNDEAD, SKELETON -> Voice.UNDEAD;
            case PET -> Voice.PET;
            case GOLEM -> Voice.GUARDIAN;
            case BOSS -> Voice.BOSS;
            default -> Voice.BEAST;
        };
    }

    // --- called by the stage director ------------------------------------------------------------------------

    public static void begin(ServerPlayerEntity player, MobEntity host) {
        NEXT.put(player.getUuid(), host.world.getTime() + 80);    // its first words, four seconds in
    }

    public static void tick(ServerPlayerEntity player, MobEntity host, int stage, long now) {
        Long next = NEXT.get(player.getUuid());
        if (next == null) {
            NEXT.put(player.getUuid(), now + 200);
            return;
        }
        if (now < next) {
            return;
        }
        Topic topic = stage >= Stages.POSSESSION ? Topic.S3 : stage == Stages.OPPRESSION ? Topic.S2 : Topic.S1;
        cry(player, host, stage, "(A while has passed in silence. Say whatever comes out of you now: to the demon, to yourself, "
                + "or to someone you love who can't hear you.)", pick(host, topic, -1, host.getRandom()), 0);
        NEXT.put(player.getUuid(), now + 500 + host.getRandom().nextInt(400));
    }

    public static void stageReached(ServerPlayerEntity player, MobEntity host, int stage) {
        Topic scream = stage >= Stages.POSSESSION ? Topic.SCREAM3 : Topic.SCREAM2;
        cry(player, host, stage, stage >= Stages.POSSESSION
                ? "(It has just taken all of you: you can't feel your body any more. You cry out.)"
                : "(It has just tightened its grip on you, hard, and your body convulsed. You cry out.)", pick(host, scream, -1, host.getRandom()), 20);
        NEXT.put(player.getUuid(), host.world.getTime() + 400);
    }

    public static void release(MinecraftServer server, UUID rider, boolean died) {
        NEXT.remove(rider);
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(rider);
        if (player != null && !died) {
            player.sendMessage(Text.literal("The soul you held slips free. Somewhere, something breathes again.")
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
        }
    }

    /** You've drawn half (then most) of its life: it feels it. */
    public static void drained(ServerPlayerEntity player, MobEntity host) {
        cry(player, host, Stages.of(host), "(You can feel it eating you: about " + Math.round(Stages.drain(host) * 100.0F)
                + "% of your life is gone, and your body is " + (Stages.drain(host) >= 0.75F ? "starting to rot" : "wasting away") + ". You feel it.)",
                pick(host, Topic.DRAIN, -1, host.getRandom()), 30);
        NEXT.put(player.getUuid(), host.world.getTime() + 400);
    }

    /** The body is spent. */
    public static void lastWords(ServerPlayerEntity player, MobEntity host) {
        cry(player, host, Stages.of(host), "(This is the end. Your body is falling apart and you're going. Your last words.)",
                pick(host, Topic.FINAL, -1, host.getRandom()), 0);
    }

    /** The soul speaks up on its own: in its own words with the AI, else {@code fallback}. */
    private static void cry(ServerPlayerEntity player, MobEntity host, int stage, String cue, String fallback, int delay) {
        if (!AiVoice.soulLine(player, host, stage, cue, fallback, line -> speak(player, host, line, 0))) {
            speak(player, host, fallback, delay);
        }
    }

    // --- talking to it ---------------------------------------------------------------------------------------

    public static void answer(ServerPlayerEntity player, MobEntity host, int stage, String message) {
        Random random = host.getRandom();
        Intent intent = Intent.of(message);
        Topic topic = switch (intent) {
            case APOLOGY -> Topic.APOLOGY;
            case FORGIVE -> Topic.FORGIVE;
            case RELEASE, GOODBYE -> Topic.RELEASE;
            case TAUNT -> Topic.TAUNT;
            case GREET -> Topic.GREET;
            case IDENTITY -> Topic.IDENTITY;
            case FEELINGS, FEAR -> Topic.FEELINGS;
            case STATEMENT -> null;
            default -> Topic.QUESTION;
        };
        int late = stage >= Stages.POSSESSION ? 1 : 0;
        String line = topic == null
                ? pick(host, stage >= Stages.POSSESSION ? Topic.S3 : stage == Stages.OPPRESSION ? Topic.S2 : Topic.S1, -1, random)
                : pick(host, topic, late, random);
        if (topic == Topic.QUESTION || topic == null) {
            // Built-in generator: it answers about whatever you asked, as a soul in torment would
            String aboutIt = Phrasebook.soulAboutTopic(message, host, late == 1, random);
            if (aboutIt != null && random.nextFloat() < 0.6F) {
                line = aboutIt;
            }
        }
        // With an API key, a real AI gives the soul its words (this line is used if it can't)
        if (!AiVoice.askSoul(player, host, stage, message, line, reply -> speak(player, host, reply, 0))) {
            speak(player, host, line, 20 + random.nextInt(20));
        }
        // It just spoke: give it a moment before it speaks up on its own again
        NEXT.put(player.getUuid(), Math.max(NEXT.getOrDefault(player.getUuid(), 0L), host.world.getTime() + 300));
    }

    /** What your telepathy hears when you turn it inward. */
    public static String innerThought(MobEntity host, int stage) {
        Topic topic = stage >= Stages.POSSESSION ? Topic.S3 : stage == Stages.OPPRESSION ? Topic.S2 : Topic.S1;
        return pick(host, topic, -1, host.getRandom());
    }

    public static String mood(int stage) {
        return switch (stage) {
            case Stages.INFESTATION -> "It is still fighting you, pushing at the edges of your mind.";
            case Stages.OPPRESSION -> "It is losing. It bargains, it begs, it asks you to forgive it.";
            default -> "It is almost gone. A whisper at the bottom of a well.";
        };
    }

    private static String pick(MobEntity host, Topic topic, int index, Random random) {
        String[] lines = LINES.get(voiceOf(host)).get(topic);
        if (lines == null || lines.length == 0) {
            return "...";
        }
        if (index >= 0) {
            return lines[Math.min(index, lines.length - 1)];
        }
        return lines[random.nextInt(lines.length)];
    }

    private static void speak(ServerPlayerEntity player, MobEntity host, String line, int delay) {
        String filled = Conversation.fill(line, host, host, player);
        MutableText text = Text.literal("Soul of " + PossessedGrin.the(host) + ": ").formatted(Formatting.DARK_AQUA, Formatting.ITALIC);
        if (filled.startsWith("*") && filled.endsWith("*")) {
            text.append(Text.literal(filled).formatted(Formatting.GRAY, Formatting.ITALIC));
        } else {
            text.append(Text.literal("\"" + filled + "\"").formatted(Formatting.AQUA, Formatting.ITALIC));
        }
        Conversation.say(player, text, delay);
        player.playSound(SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.HOSTILE, 1.0F, 0.6F + host.getRandom().nextFloat() * 0.3F);
    }

    private static void put(Voice voice, Topic topic, String... lines) {
        LINES.computeIfAbsent(voice, v -> new EnumMap<>(Topic.class)).put(topic, lines);
    }

    static {
        // --- people: villagers, illagers, witches, piglins ------------------------------------------------------
        put(Voice.PERSON, Topic.S1, "Who's there? Who's in here with me?", "Something cold just walked into my head. Get out.",
                "I can hear you breathing. Inside me. Why can I hear you breathing?",
                "This is my body. I don't know what you are, but this is MY body.");
        put(Voice.PERSON, Topic.S2, "Please... it hurts when you move my arms.", "I'm sorry. Whatever I did, I'm sorry. Let me go.",
                "I can still feel my hands. Don't take my hands.", "I'll give you anything. Take someone else. Please, not me.",
                "Forgive me. I don't even know what for, but forgive me.");
        put(Voice.PERSON, Topic.S3, "...still... here... I'm still here...", "Is it night? I can't see anything anymore.",
                "Don't go near my family. Please. Not them.", "I forgive you. Does that help? Will you leave if I forgive you?",
                "...please...");
        put(Voice.PERSON, Topic.SCREAM2, "No... NO! Get OUT of me!");
        put(Voice.PERSON, Topic.SCREAM3, "I can't... I can't feel anything... where did my body go?");
        put(Voice.PERSON, Topic.APOLOGY, "Sorry? Then LEAVE!", "...sorry doesn't give me back my eyes. But... I forgive you. Now go.");
        put(Voice.PERSON, Topic.FORGIVE, "Forgive ME? I didn't do anything! You're the one inside me!", "...you forgive me? ...thank you. I'm so tired.");
        put(Voice.PERSON, Topic.RELEASE, "Then do it. Please. Do it now.", "You won't. I know you won't. ...Will you?");
        put(Voice.PERSON, Topic.TAUNT, "You think that scares me? I've lived through raids!", "...yes. Laugh. I can't feel it anymore.");
        put(Voice.PERSON, Topic.GREET, "Don't talk to me like we're friends.", "...hello? Is someone there? Is that... me?");
        put(Voice.PERSON, Topic.QUESTION, "I don't know! I don't know anything! Just get out!", "...I don't remember... I don't remember my own name...");
        put(Voice.PERSON, Topic.IDENTITY, "I'm the one who LIVES here!", "...I was a {host}. I think. I think I was.");
        put(Voice.PERSON, Topic.FEELINGS, "How do you think I feel?! There's a monster in my skin!", "...nothing. I feel nothing. Is that bad?");
        put(Voice.PERSON, Topic.DRAIN, "You're eating me. I can feel you eating me from the inside.", "I'm so thin. I can feel my own bones.",
                "There won't be anything left of me. Will there?");
        put(Voice.PERSON, Topic.FINAL, "...it's quiet now... is it over...?", "...tell them... I tried...", "...so cold... and then... not...");

        // --- the walking dead ---------------------------------------------------------------------------------
        put(Voice.UNDEAD, Topic.S1, "Hhhrr... I was resting. I was finally resting.", "Two of us in here now. Not enough room.",
                "I died once. You can't scare me with that.");
        put(Voice.UNDEAD, Topic.S2, "It's worse than dying. Being pushed out of my own rot.", "Let go. I don't have much left, but it's mine.",
                "I'm sorry. I'm sorry for the ones I bit. Is that why you've come?");
        put(Voice.UNDEAD, Topic.S3, "...darker than the grave in here...", "Hhh... I'm forgetting the hunger. I'm forgetting everything.",
                "...forgive me... for whatever I was...");
        put(Voice.UNDEAD, Topic.SCREAM2, "RRRAAAH! NOT AGAIN! I already died once!");
        put(Voice.UNDEAD, Topic.SCREAM3, "...hhhhhh... so... cold...");
        put(Voice.UNDEAD, Topic.APOLOGY, "Hhhrr... sorry won't... bury me again.", "...it's alright... I'm used to... being used...");
        put(Voice.UNDEAD, Topic.FORGIVE, "Forgive...? I've bitten... so many...", "...forgiven... finally... thank you...");
        put(Voice.UNDEAD, Topic.RELEASE, "Go... let me... rot in peace.", "...don't... leave me... empty...");
        put(Voice.UNDEAD, Topic.TAUNT, "Laugh... at the dead... we were you... once.", "...hhhh...");
        put(Voice.UNDEAD, Topic.GREET, "Hhhrr... you again.", "...who... speaks...?");
        put(Voice.UNDEAD, Topic.QUESTION, "Don't... know. Rotten. All rotten.", "...gone... the words are gone...");
        put(Voice.UNDEAD, Topic.IDENTITY, "Was... somebody. Before. Now... this.", "...nobody... I'm nobody...");
        put(Voice.UNDEAD, Topic.FEELINGS, "Cold. Crowded. Angry.", "...like the grave... but lonelier...");
        put(Voice.UNDEAD, Topic.DRAIN, "Even the rot... is being eaten...", "Hhh... hollow... you've hollowed me out...");
        put(Voice.UNDEAD, Topic.FINAL, "...back to the earth... finally...", "...rest... at last...");

        // --- beasts and monsters ------------------------------------------------------------------------------
        put(Voice.BEAST, Topic.S1, "*it panics* Wrong. Wrong. Something is wrong.", "Cold. Inside. Run? Can't run. Legs won't.",
                "Who? Who? Who's there?");
        put(Voice.BEAST, Topic.S2, "Hurts. Make it stop. Please, make it stop.", "*whimpering* Sorry. Sorry. Will be good. Will be good.",
                "Let go. Let go. Want to go home.");
        put(Voice.BEAST, Topic.S3, "...dark...", "*a tiny voice* ...mama?", "...sorry...");
        put(Voice.BEAST, Topic.SCREAM2, "*a terrified shriek fills your head*");
        put(Voice.BEAST, Topic.SCREAM3, "*silence, then one small sob*");
        put(Voice.BEAST, Topic.APOLOGY, "*a confused whimper* Sorry? Then let go!", "*a soft, tired sound*");
        put(Voice.BEAST, Topic.FORGIVE, "*it doesn't understand, but it's trying to*",
                "*it presses close inside you, like a frightened animal against a stranger's leg*");
        put(Voice.BEAST, Topic.RELEASE, "Go! Go! Want to run!", "*a flicker of hope, then nothing*");
        put(Voice.BEAST, Topic.TAUNT, "*it cowers in the back of your mind*", "*it doesn't react anymore*");
        put(Voice.BEAST, Topic.GREET, "*it flinches at your voice*", "*it barely stirs*");
        put(Voice.BEAST, Topic.QUESTION, "Don't know! Scared! Scared!", "...dark... only dark...");
        put(Voice.BEAST, Topic.IDENTITY, "{host}! Just a {host}! Small! Leave!", "...was... a {host}...");
        put(Voice.BEAST, Topic.FEELINGS, "Scared! Cold! Hurts!", "...sleepy... so sleepy...");
        put(Voice.BEAST, Topic.DRAIN, "*it's too weak to whimper*", "Tired... so tired... so hungry...");
        put(Voice.BEAST, Topic.FINAL, "*one last, small breath*", "*a soft sigh, and then nothing*");

        // --- pets ---------------------------------------------------------------------------------------------
        put(Voice.PET, Topic.S1, "*whines* Where's {owner}? Something's in my head. Where's {owner}?",
                "I'm a good {host}. Why is this happening? I'm good.");
        put(Voice.PET, Topic.S2, "*whimpers* I'm sorry. Whatever I did, I'm sorry. I'll be good.",
                "Please let me go home. My bed's by the fire.", "{owner} will come. {owner} always comes.");
        put(Voice.PET, Topic.S3, "...is {owner} here? I can't see...", "...I forgive you. I always forgive.", "...good {host}... good...");
        put(Voice.PET, Topic.SCREAM2, "*a long, terrified howl*");
        put(Voice.PET, Topic.SCREAM3, "*a tiny, broken whimper*");
        put(Voice.PET, Topic.APOLOGY, "*sniffles* It's okay. Just let me go home?", "*it nuzzles the inside of your mind* ...okay. Okay.");
        put(Voice.PET, Topic.FORGIVE, "I'm sorry! I'm sorry for whatever I did!", "...good {host}? Am I a good {host}?");
        put(Voice.PET, Topic.RELEASE, "*tail thumps weakly* Really? You promise?", "...I'll wait for you. I always wait.");
        put(Voice.PET, Topic.TAUNT, "*whimpers and tucks its tail*", "*it doesn't even whimper anymore*");
        put(Voice.PET, Topic.GREET, "*a hopeful whine* Are you {owner}?", "...{owner}...?");
        put(Voice.PET, Topic.QUESTION, "I don't know! I'm just a {host}!", "...I don't know... I'm sorry...");
        put(Voice.PET, Topic.IDENTITY, "I'm {owner}'s {host}! Their good {host}!", "...somebody's... {host}...");
        put(Voice.PET, Topic.FEELINGS, "Scared! I want {owner}!", "...cold... is it bedtime...?");
        put(Voice.PET, Topic.DRAIN, "...I'm so tired... can I lie down by the fire...?", "...is {owner} coming...?");
        put(Voice.PET, Topic.FINAL, "...good {host}... I was a good {host}...", "...tell {owner}... I waited...");

        // --- golems -------------------------------------------------------------------------------------------
        put(Voice.GUARDIAN, Topic.S1, "*grind* Intrusion detected in my core. Identify yourself.",
                "I protect this village. You will not use me against it.");
        put(Voice.GUARDIAN, Topic.S2, "*creak* Control... slipping. Forgive me, village.", "Do not make me hurt them. I beg you. Do not.");
        put(Voice.GUARDIAN, Topic.S3, "...village... forgive... me...", "*a faint clank, like a heartbeat stopping*");
        put(Voice.GUARDIAN, Topic.SCREAM2, "*CLANG* CORE BREACH!");
        put(Voice.GUARDIAN, Topic.SCREAM3, "...the flowers... who will give them... the flowers...");
        put(Voice.GUARDIAN, Topic.APOLOGY, "*grind* Apology logged. Release me.", "...accepted... too late...");
        put(Voice.GUARDIAN, Topic.FORGIVE, "*clank* I require no forgiveness. You require removal.", "...forgiven... I am... forgiven...");
        put(Voice.GUARDIAN, Topic.RELEASE, "Then release me. The village needs its guardian.", "...protect them... when I'm gone...");
        put(Voice.GUARDIAN, Topic.TAUNT, "*CLANK* Your words change nothing. I will resist.", "...resistance... failing...");
        put(Voice.GUARDIAN, Topic.GREET, "*grind* You are not welcome here.", "...who... speaks...");
        put(Voice.GUARDIAN, Topic.QUESTION, "*grind* I will tell you nothing, intruder.", "...memory... corrupted...");
        put(Voice.GUARDIAN, Topic.IDENTITY, "I am the guardian of this village.", "...I was... a guardian...");
        put(Voice.GUARDIAN, Topic.FEELINGS, "*creak* Compromised. Furious.", "...rusting... from inside...");
        put(Voice.GUARDIAN, Topic.DRAIN, "*creak* Structural failure... spreading...", "...iron... turning... to rust...");
        put(Voice.GUARDIAN, Topic.FINAL, "...village... safe...?", "*a last, slow clank*");

        // --- bosses -------------------------------------------------------------------------------------------
        put(Voice.BOSS, Topic.S1, "You dare? You DARE crawl inside ME?", "Small thing. I will burn you out of my veins.",
                "I have ended kingdoms. You are nothing.");
        put(Voice.BOSS, Topic.S2, "...how? How are you this strong?", "Release me, and I may let you live.",
                "This is not possible. I cannot be taken.");
        put(Voice.BOSS, Topic.S3, "...I yield. Is that what you wanted to hear? I yield.", "...forgive my pride. Let me go back to the dark.",
                "...even I... have never felt... this cold...");
        put(Voice.BOSS, Topic.SCREAM2, "RAAAAGH! GET OUT OF MY MIND!");
        put(Voice.BOSS, Topic.SCREAM3, "...it... is... yours...");
        put(Voice.BOSS, Topic.APOLOGY, "Your sorrow is worthless. Get out.", "...I accept. I accept everything now.");
        put(Voice.BOSS, Topic.FORGIVE, "Forgive ME? Arrogant spark!", "...forgiveness... from a thing like you... how it burns...");
        put(Voice.BOSS, Topic.RELEASE, "Release me, and I will spare your world.", "...no. Stay. I have nowhere else to go.");
        put(Voice.BOSS, Topic.TAUNT, "Laugh while you can. I am patient.", "...yes. Mock me. I deserve it.");
        put(Voice.BOSS, Topic.GREET, "Do not greet me as an equal.", "...you... again...");
        put(Voice.BOSS, Topic.QUESTION, "I owe you no answers.", "...I knew so much... once...");
        put(Voice.BOSS, Topic.IDENTITY, "I am the {host}. I am the end of things.", "...I was... the {host}...");
        put(Voice.BOSS, Topic.FEELINGS, "Wrath. Pure wrath.", "...small. I feel... small.");
        put(Voice.BOSS, Topic.DRAIN, "...you are devouring me. ME.", "...ages I have lived... and you eat them like bread...");
        put(Voice.BOSS, Topic.FINAL, "...so this... is how an ending feels...", "...remember... what I was...");
    }
}
