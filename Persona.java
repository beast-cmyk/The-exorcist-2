package com.possessedgrin.voice;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.EnumMap;
import java.util.Map;

/**
 * How a kind of mob talks. Every line here is original. Placeholders, filled in when spoken:
 * {@code {me}} the speaker's kind, {@code {host}} the kind of body you're wearing, {@code {player}} your name,
 * {@code {owner}} a pet's owner.
 */
public enum Persona {
    VILLAGER(Formatting.GOLD, "Hrm.", "Hmm.", "Hrrm!"),
    ILLAGER(Formatting.GRAY, "Hm.", "Tch."),
    WITCH(Formatting.DARK_PURPLE, "Hee hee.", "Mm."),
    UNDEAD(Formatting.DARK_GREEN, "Hhhrrr...", "Uuurgh..."),
    SKELETON(Formatting.WHITE, "*rattle*", "Clack."),
    PIGLIN(Formatting.GOLD, "Hrnk!", "Snrrk."),
    CREEPER(Formatting.GREEN, "Ssss...", "Hsss."),
    SPIDER(Formatting.DARK_GRAY, "Chhh.", "Skitter."),
    ENDERMAN(Formatting.LIGHT_PURPLE, "...hrrrm.", "...vwoop."),
    GOLEM(Formatting.WHITE, "*clank*", "*grind*"),
    PET(Formatting.AQUA, "*tail wags*", "*sniff*"),
    ANIMAL(Formatting.YELLOW, "*sniffs*", "*blinks*"),
    MONSTER(Formatting.RED, "Grrr.", "Hssk."),
    BOSS(Formatting.DARK_RED, "...", "Hm.");

    /** The things a mob can say, by subject. */
    public enum Topic {
        GREET, IDENTITY, FEELINGS, FEAR, WORK, FAMILY, FOOD, TAUNTED, APOLOGY, GOODBYE, UNKNOWN, OPINION,
        SECRET_NONE, HOME_NONE, KIN_HURT, KIN_HELP, OBEY, REFUSE, CANT_FIGHT, HELP, IDLE, DREAM,
        /** Overheard the demon speak, stage I: something's off. */
        OVERHEAR,
        /** Overheard the demon speak, stages II and III: horror. */
        HORROR
    }

    public final Formatting color;
    private final String[] noises;
    private final Map<Topic, String[]> lines = new EnumMap<>(Topic.class);

    Persona(Formatting color, String... noises) {
        this.color = color;
        this.noises = noises;
    }

    public static Persona of(LivingEntity e) {
        if (e instanceof WardenEntity || e instanceof WitherEntity || e instanceof EnderDragonEntity || e instanceof ElderGuardianEntity) {
            return BOSS;
        }
        if (e instanceof MerchantEntity) {
            return VILLAGER;            // villagers and wandering traders
        }
        if (e instanceof WitchEntity) {
            return WITCH;
        }
        if (e instanceof IllagerEntity) {
            return ILLAGER;             // pillagers, vindicators, evokers, illusioners
        }
        if (e instanceof AbstractPiglinEntity) {
            return PIGLIN;
        }
        if (e instanceof ZombieEntity) {
            return UNDEAD;              // zombies, husks, drowned, zombie villagers, zombified piglins
        }
        if (e instanceof AbstractSkeletonEntity) {
            return SKELETON;
        }
        if (e instanceof CreeperEntity) {
            return CREEPER;
        }
        if (e instanceof SpiderEntity) {
            return SPIDER;
        }
        if (e instanceof EndermanEntity) {
            return ENDERMAN;
        }
        if (e instanceof GolemEntity && !(e instanceof ShulkerEntity)) {
            return GOLEM;
        }
        if ((e instanceof TameableEntity pet && pet.isTamed()) || (e instanceof AbstractHorseEntity horse && horse.isTame())) {
            return PET;
        }
        if (e instanceof Monster) {
            return MONSTER;
        }
        return ANIMAL;                  // animals, fish, bats, bees, allays...
    }

    /** Can this kind of mob talk back in anger (and fight) rather than flee? */
    public boolean fighter() {
        return this != VILLAGER && this != PET && this != ANIMAL;
    }

    public String noise(Random random) {
        return this.noises[random.nextInt(this.noises.length)];
    }

    public String line(Topic topic, Random random) {
        String[] options = this.lines.get(topic);
        if (options == null || options.length == 0) {
            options = MONSTER.lines.get(topic);
        }
        return options == null || options.length == 0 ? "..." : options[random.nextInt(options.length)];
    }

    private static void say(Persona persona, Topic topic, String... options) {
        persona.lines.put(topic, options);
    }

    static {
        // --- villagers --------------------------------------------------------------------------------------
        say(VILLAGER, Topic.GREET, "Hrm. Good day... is it? Your eyes look wrong, {host}.", "Hello. Please don't stand so close.",
                "Hmm! Oh. It's you. Whatever you are now.");
        say(VILLAGER, Topic.IDENTITY, "I'm a {me}. I live here, I work here, and I'd like to keep doing both.",
                "Just a {me}. Nobody worth haunting, I promise.");
        say(VILLAGER, Topic.FEELINGS, "Nervous. Since you showed up the bread won't rise and the dogs won't stop barking.",
                "I was fine until a {host} started grinning at me.", "Tired. Nobody slept last night. Something kept knocking.");
        say(VILLAGER, Topic.FEAR, "Zombies at the door. Raiders on the hill. And now, you.",
                "The night, the pillagers... but mostly that grin of yours.");
        say(VILLAGER, Topic.WORK, "I work the land and keep my head down.", "A bit of everything. Mostly worrying.");
        say(VILLAGER, Topic.FAMILY, "Everyone in this village is family, more or less. Leave them out of this.",
                "My children are inside. You'll stay away from them.");
        say(VILLAGER, Topic.FOOD, "Bread, carrots, a baked potato if the harvest is good. You don't eat bread anymore, do you?",
                "Pumpkin pie. My grandmother's recipe. Why are you smiling like that?");
        say(VILLAGER, Topic.TAUNTED, "Hrmph! Rude AND possessed. My mother warned me about your kind.",
                "Say that again and I'll ring the bell!", "How dare you! I'm telling the cleric!");
        say(VILLAGER, Topic.APOLOGY, "Hmm... Apology accepted. Now please, go haunt someone else.",
                "Sorry doesn't put the color back in your face. But... thank you.");
        say(VILLAGER, Topic.GOODBYE, "Yes. Goodbye. Far away, ideally.", "Hrm. Safe travels. Very, very far.");
        say(VILLAGER, Topic.UNKNOWN, "Hrm? I'm a simple {me}. I don't know about that.", "Why would I know that? Ask the librarian.",
                "Hmm. That's above my station.", "I don't know, and I'd rather not find out.");
        say(VILLAGER, Topic.OPINION, "I don't know what you are. That's what frightens me.");
        say(VILLAGER, Topic.SECRET_NONE, "The librarian keeps one book nobody's allowed to read. That's all I know.",
                "Sometimes the bell rings by itself at night. Nobody talks about it.");
        say(VILLAGER, Topic.HOME_NONE, "I don't have a bed. Nobody built enough beds.");
        say(VILLAGER, Topic.KIN_HURT, "Why would you hit me? It's me! Neighbour, it's me!",
                "Ow! What's wrong with your face? Stay back, I'll get help!", "Neighbour... your eyes. Something has you, doesn't it?");
        say(VILLAGER, Topic.KIN_HELP, "Here. Eat something. Whatever's inside you, we'll pray it out.",
                "I came back. You're one of us, even like this. Take it.");
        say(VILLAGER, Topic.OBEY, "Y-yes. Whatever you say. Just don't look at me like that.", "Of course. Of course. Anything.");
        say(VILLAGER, Topic.REFUSE, "I don't take orders from... whatever you are.", "Hrm. No. Not from you.");
        say(VILLAGER, Topic.CANT_FIGHT, "Me? Fight? I sell potatoes!");
        say(VILLAGER, Topic.HELP, "I can't help you. Find a cleric. Please, find a cleric.");
        say(VILLAGER, Topic.IDLE, "Wheat... bread... is there enough bread for winter?", "Did I lock the door? I should check the door.",
                "Emeralds. Need more emeralds.");
        say(VILLAGER, Topic.DREAM, "a field of emeralds, glittering in the sun", "a quiet night with nothing knocking");

        // --- illagers ---------------------------------------------------------------------------------------
        say(ILLAGER, Topic.GREET, "Hm. A {host} with dead eyes. Interesting.", "Speak quickly. The captain hates delays.");
        say(ILLAGER, Topic.IDENTITY, "A {me} of the woodland mansions. We take what villages won't give.",
                "Raider. Hunter. The thing villagers bar their doors against. One of them, anyway.");
        say(ILLAGER, Topic.FEELINGS, "Restless. There's a village two valleys over that hasn't been raided in weeks.",
                "Fine. Better than you, by the look of you.");
        say(ILLAGER, Topic.FEAR, "Iron golems. Nothing else, and certainly not you.", "The captain's anger. Not much else.");
        say(ILLAGER, Topic.WORK, "Raids. Ambushes. Collecting what we're owed.");
        say(ILLAGER, Topic.FAMILY, "The band is my family. We share the loot and the scars.");
        say(ILLAGER, Topic.FOOD, "Whatever the village had stored. They won't need it after we visit.");
        say(ILLAGER, Topic.TAUNTED, "Careful. I've cut down braver things than a haunted {host}.",
                "Big words for a puppet. Who's pulling your strings?");
        say(ILLAGER, Topic.APOLOGY, "Hah. An apology from a demon. The others won't believe it.");
        say(ILLAGER, Topic.GOODBYE, "Go, then. Watch the treeline.");
        say(ILLAGER, Topic.UNKNOWN, "Not my concern.", "Ask the evoker. He knows the strange things.", "I hunt. I don't think.");
        say(ILLAGER, Topic.OPINION, "You're useful. Unsettling, but useful. A shame you're not one of us.");
        say(ILLAGER, Topic.SECRET_NONE, "Evokers don't summon the fangs. They borrow them from something underneath. Don't ask what.");
        say(ILLAGER, Topic.HOME_NONE, "The mansion in the dark forest. Or the outpost. Wherever the banner flies.");
        say(ILLAGER, Topic.KIN_HURT, "Hey! Wrong target, fool! ...Your eyes. What happened to you?",
                "Easy! We're on the same side! ...Aren't we?");
        say(ILLAGER, Topic.KIN_HELP, "Here. The band takes care of its own, even the cursed ones.");
        say(ILLAGER, Topic.OBEY, "As you command. Something about you outranks the captain.");
        say(ILLAGER, Topic.REFUSE, "I follow the captain. Not you.");
        say(ILLAGER, Topic.HELP, "Help? We don't help. We take.");
        say(ILLAGER, Topic.IDLE, "Next village. Next raid. Next.", "Sharpen the axe. Sharpen it again.");
        say(ILLAGER, Topic.DREAM, "a burning village and a banner on every roof");

        // --- witches ----------------------------------------------------------------------------------------
        say(WITCH, Topic.GREET, "Hee hee. Well, well. Someone's let a guest into that {host}.",
                "Ohh, I can smell you from here. Sulfur and old candles.");
        say(WITCH, Topic.IDENTITY, "A witch, dearie. I brew, I curse, I mind my business. You should try it.",
                "Just an old woman with a cauldron and a temper.");
        say(WITCH, Topic.FEELINGS, "Delighted! It's not every day I meet something worse than me.",
                "My knees ache and the swamp is damp. The usual.");
        say(WITCH, Topic.FEAR, "Clerics and their holy water. Nasty stuff. Burns you, doesn't it?",
                "Thunder. The last time lightning struck, well... I was already like this.");
        say(WITCH, Topic.WORK, "Brewing. Poisons, mostly. Some healing, if I'm in a mood.");
        say(WITCH, Topic.FAMILY, "My cat ran off. My coven ran off. Nobody stays with a witch.");
        say(WITCH, Topic.FOOD, "Spider eyes, glowstone, a pinch of sugar. Not for eating... usually.");
        say(WITCH, Topic.TAUNTED, "Mind your tongue, or I'll pickle what's left of it!", "Hee! Insults from a borrowed mouth. How brave.");
        say(WITCH, Topic.APOLOGY, "Sorry? Hee hee. That's the first time a demon's said that to me.");
        say(WITCH, Topic.GOODBYE, "Off you go! Mind the lily pads.");
        say(WITCH, Topic.UNKNOWN, "Hmm, I'd have to read my cauldron for that.", "Bring me nether wart and I'll think about it.",
                "Witches know many things, dearie. Not that.");
        say(WITCH, Topic.OPINION, "I like you. You make the villagers scream louder than I do.");
        say(WITCH, Topic.SECRET_NONE, "Holy water is just water with ideas. That's why it hurts your kind.");
        say(WITCH, Topic.HOME_NONE, "A hut on stilts in the swamp. Wipe your feet.");
        say(WITCH, Topic.KIN_HURT, "Hey! Brew your own trouble! ...Oh my. Something's wearing you, dear.");
        say(WITCH, Topic.KIN_HELP, "Here, drink this. It won't cure a possession, but it stops the bleeding.");
        say(WITCH, Topic.OBEY, "Ooh, bossy. Fine, fine.");
        say(WITCH, Topic.REFUSE, "Witches don't take orders, dearie.");
        say(WITCH, Topic.HELP, "Help? For a price. Everything has a price.");
        say(WITCH, Topic.IDLE, "Three spider eyes, a pinch of sugar, stir widdershins...", "Where did I put the redstone?");
        say(WITCH, Topic.DREAM, "a cauldron that never runs dry");

        // --- zombies and other walking dead -----------------------------------------------------------------
        say(UNDEAD, Topic.GREET, "Hhhrrr... you. You smell dead. Like us. But wrong.", "Uuurgh. Hello... flesh?");
        say(UNDEAD, Topic.IDENTITY, "Was... somebody. Once. Now... hungry.", "Zombie. Rot. Walk. Bite. That's all.");
        say(UNDEAD, Topic.FEELINGS, "Hungry. Always... hungry.", "Cold. Then hungry. Then cold again.");
        say(UNDEAD, Topic.FEAR, "Sun. Sun burns. Hhhurts.", "The fire. The golem. The light.");
        say(UNDEAD, Topic.WORK, "Wander... scratch at doors... wait for night.");
        say(UNDEAD, Topic.FAMILY, "Had... family. Don't remember faces. Remember... names. No. Gone.");
        say(UNDEAD, Topic.FOOD, "Villagers. Hhhrrr. Or flesh. Any flesh. Not... yours. Yours tastes like smoke.");
        say(UNDEAD, Topic.TAUNTED, "Hhhrrr! Come closer... say it again...", "Rrraaagh! Bite... your... borrowed face!");
        say(UNDEAD, Topic.APOLOGY, "Sorry...? Don't know... that word. Anymore.");
        say(UNDEAD, Topic.GOODBYE, "Hhhrrr... go.");
        say(UNDEAD, Topic.UNKNOWN, "Don't... know. Brain... rotten.", "Hhhrr? Too... many words.", "Ask... someone alive.");
        say(UNDEAD, Topic.OPINION, "You... dead like us. But the thing inside you... is not dead. Never was.");
        say(UNDEAD, Topic.SECRET_NONE, "Something... calls us. Underground. At night. We walk... toward it.");
        say(UNDEAD, Topic.HOME_NONE, "Wherever... the dark is.");
        say(UNDEAD, Topic.KIN_HURT, "Hhhrr?! Why bite... brother? ...Your eyes... burn white.", "No! Not... me! Same... same!");
        say(UNDEAD, Topic.KIN_HELP, "Here... eat. Brothers share... the flesh.");
        say(UNDEAD, Topic.OBEY, "Yesss... master. Yesss.");
        say(UNDEAD, Topic.REFUSE, "Hhhrr. No. Not yet... you're not... strong enough.");
        say(UNDEAD, Topic.HELP, "Help...? Bite... them for you?");
        say(UNDEAD, Topic.IDLE, "Hungry. Hungry. Hungry.", "Smell... villager... that way...", "Door. Scratch the door.");
        say(UNDEAD, Topic.DREAM, "being warm again");

        // --- skeletons --------------------------------------------------------------------------------------
        say(SKELETON, Topic.GREET, "*rattle* Oh, it's you. Your skin's still on. How quaint.", "Clack. Hello there, haunted thing.");
        say(SKELETON, Topic.IDENTITY, "A skeleton. Bones, a bow and a bad attitude. Standard issue.",
                "Just what's left when everything soft gives up.");
        say(SKELETON, Topic.FEELINGS, "Hollow. Literally. There's a draft through my ribs.",
                "I feel nothing. That's the nice thing about having no nerves.");
        say(SKELETON, Topic.FEAR, "Wolves. They think I'm a snack. Technically, I am.", "Daylight, and dogs. Mostly dogs.");
        say(SKELETON, Topic.WORK, "Aiming. Waiting. Aiming more.");
        say(SKELETON, Topic.FAMILY, "Somewhere there's a grave with my name on it. I've never visited.");
        say(SKELETON, Topic.FOOD, "Food? Look at me. Where would it go?");
        say(SKELETON, Topic.TAUNTED, "Ha! Bold words from a meat puppet. I'll pin your borrowed hide to a tree.",
                "*clatter* Keep talking. I've got arrows and nowhere to be.");
        say(SKELETON, Topic.APOLOGY, "Apology accepted. No hard feelings. No feelings at all, really.");
        say(SKELETON, Topic.GOODBYE, "*rattle* Don't lose your head. I did, once.");
        say(SKELETON, Topic.UNKNOWN, "No idea. My skull's mostly empty space.", "Clack. Haven't the foggiest.", "Ask a zombie. Actually, don't.");
        say(SKELETON, Topic.OPINION, "You've got a demon inside and a body outside. I've got neither. I'm a bit jealous.");
        say(SKELETON, Topic.SECRET_NONE, "Spawners hum at night. Put your ear to the stone and you can hear them counting.");
        say(SKELETON, Topic.HOME_NONE, "A dungeon under the hill. Mossy. Cosy.");
        say(SKELETON, Topic.KIN_HURT, "Clack! Watch it! ...Hold on. Something's rattling around in there.",
                "Hey! I'm on your side, bone-brain!");
        say(SKELETON, Topic.KIN_HELP, "Here. Spare bones. Glue yourself back together.");
        say(SKELETON, Topic.OBEY, "*click* Understood. Drawing.");
        say(SKELETON, Topic.REFUSE, "My bowstring answers to no one. Yet.");
        say(SKELETON, Topic.HELP, "I can shoot things. That's my whole skill set.");
        say(SKELETON, Topic.IDLE, "Count the arrows. Twelve. Thirteen. Thirteen.", "Too bright. Find shade.");
        say(SKELETON, Topic.DREAM, "having skin again, just for a day");

        // --- piglins ----------------------------------------------------------------------------------------
        say(PIGLIN, Topic.GREET, "Hrnk! You! Gold? You have gold?", "Snrrk. Smells like brimstone. Smells like home.");
        say(PIGLIN, Topic.IDENTITY, "Piglin! Bastion-born! Lover of gold!", "Proud piglin. Proud snout. Prouder gold.");
        say(PIGLIN, Topic.FEELINGS, "Want gold. Feel... want gold.", "Good! Hot! Nether warm. Overworld bad.");
        say(PIGLIN, Topic.FEAR, "Soul fire. Blue flames. Bad. Very bad.", "Turning grey. Cold air, shaking, then... grey. Never.");
        say(PIGLIN, Topic.WORK, "Guard bastion. Barter. Hunt hoglin.");
        say(PIGLIN, Topic.FAMILY, "Brutes are strong uncles. Babies ride hoglins. Good family.");
        say(PIGLIN, Topic.FOOD, "Hoglin meat! Hot! Crispy!");
        say(PIGLIN, Topic.TAUNTED, "Hrrraaagh! Gold-less filth! Fight!", "SNRRK! You insult the bastion!");
        say(PIGLIN, Topic.APOLOGY, "Sorry? Sorry not gold. Give gold.");
        say(PIGLIN, Topic.GOODBYE, "Go. Bring gold next time.");
        say(PIGLIN, Topic.UNKNOWN, "Hrnk? Piglin not know. Piglin know gold.", "Ask brute. Brute hits things until they answer.",
                "Snrrk. Too hard. Gold?");
        say(PIGLIN, Topic.OPINION, "You smell like the deep Nether. Piglin respect that. Piglin also scared.");
        say(PIGLIN, Topic.SECRET_NONE, "Big gold pile in bastion treasure room. Don't tell brutes I told.");
        say(PIGLIN, Topic.HOME_NONE, "Bastion! Big black walls! Lava moat!");
        say(PIGLIN, Topic.KIN_HURT, "SNRRK?! Why hit piglin?! ...Eyes white. Bad sign. Very bad.", "Hrnk! Same snout! Same snout!");
        say(PIGLIN, Topic.KIN_HELP, "Here. Gold. Gold fixes everything. Mostly.");
        say(PIGLIN, Topic.OBEY, "Hrnk! Piglin obeys! Piglin brave!");
        say(PIGLIN, Topic.REFUSE, "No! Piglin answers to bastion only!");
        say(PIGLIN, Topic.HELP, "Help? Trade gold, maybe.");
        say(PIGLIN, Topic.IDLE, "Gold gold gold gold.", "Shiny... where shiny?");
        say(PIGLIN, Topic.DREAM, "a mountain of gold, all mine");

        // --- creepers ---------------------------------------------------------------------------------------
        say(CREEPER, Topic.GREET, "Ssssss... hello.", "Hsss. Don't startle me. Things happen when I'm startled.");
        say(CREEPER, Topic.IDENTITY, "Ssss... I'm the quiet one. The one you never hear coming.", "Creeper. Green. Full of... feelings.");
        say(CREEPER, Topic.FEELINGS, "Tense. Always tense. Ssss... it builds up.", "Lonely. Everyone runs when I get close.");
        say(CREEPER, Topic.FEAR, "Cats. Ssss! Those eyes... those horrible little eyes.", "Ocelots. And lightning. Lightning makes me... more.");
        say(CREEPER, Topic.WORK, "I wait by doors. I wait by walls. I wait.");
        say(CREEPER, Topic.FAMILY, "Family? We don't... last long enough for family.");
        say(CREEPER, Topic.FOOD, "Ssss... I don't eat. I just... build up.");
        say(CREEPER, Topic.TAUNTED, "Ssssssss... say that... one... more... time...", "Hsss! You're making me very... very... tense.");
        say(CREEPER, Topic.APOLOGY, "Ssss... alright. Calming down. Calming... down.");
        say(CREEPER, Topic.GOODBYE, "Ssss. Don't turn your back on me. Old habit.");
        say(CREEPER, Topic.UNKNOWN, "Ssss? I only know one thing, and I'm saving it.", "Hsss. Not sure. Try standing closer.", "Ssss... no.");
        say(CREEPER, Topic.OPINION, "You're the first thing that doesn't run from me. Ssss... I like you.");
        say(CREEPER, Topic.SECRET_NONE, "Ssss... we're hollow inside. Just gunpowder and longing.");
        say(CREEPER, Topic.HOME_NONE, "Behind you. Usually.");
        say(CREEPER, Topic.KIN_HURT, "SSSS?! Don't! You'll set us both off!");
        say(CREEPER, Topic.KIN_HELP, "Ssss... here. Gunpowder. For when you need a big goodbye.");
        say(CREEPER, Topic.OBEY, "Sssss... yesss.");
        say(CREEPER, Topic.REFUSE, "Hsss. Not yet. You don't have the... spark.");
        say(CREEPER, Topic.HELP, "Ssss... I can help one time. Only one time.");
        say(CREEPER, Topic.IDLE, "Quiet... be quiet... wait...", "Closer. Just a little closer.");
        say(CREEPER, Topic.DREAM, "a hug that doesn't end badly");

        // --- spiders ----------------------------------------------------------------------------------------
        say(SPIDER, Topic.GREET, "Chhh-chhh. Eight eyes on you, {host}. All eight.", "Skitter. Something new crawls in the dark. Welcome.");
        say(SPIDER, Topic.IDENTITY, "Spider. Weaver. Climber of walls. Like you, now.", "We are the patient ones. We wait in corners.");
        say(SPIDER, Topic.FEELINGS, "Hungry. The web is empty tonight.", "Content. The dark is warm and the walls are rough.");
        say(SPIDER, Topic.FEAR, "Sunlight makes us lazy. Swords make us dead.");
        say(SPIDER, Topic.WORK, "Climb. Wait. Pounce. Again.");
        say(SPIDER, Topic.FAMILY, "Hundreds of siblings. I ate a few. They ate a few.");
        say(SPIDER, Topic.FOOD, "Whatever's slow. Whatever's warm. Whatever's tangled.");
        say(SPIDER, Topic.TAUNTED, "Chhhh. Brave words. The walls have ears, and I have fangs.",
                "Skkkt! Come here and say it to my eyes. All of them.");
        say(SPIDER, Topic.APOLOGY, "Chhh. Apology... noted. Webbed. Stored.");
        say(SPIDER, Topic.GOODBYE, "Skitter. We'll be on the ceiling.");
        say(SPIDER, Topic.UNKNOWN, "Chhh? Not in my web.", "Ask something with fewer legs.", "Skitter... don't know.");
        say(SPIDER, Topic.OPINION, "You crawl the walls now. You're one of us, a little.");
        say(SPIDER, Topic.SECRET_NONE, "There's a nest under the broken mine. Rails, cobwebs, and a chest nobody reached.");
        say(SPIDER, Topic.HOME_NONE, "Corners. Ceilings. The gap behind your wall.");
        say(SPIDER, Topic.KIN_HURT, "CHHHK! Not me! Not me! ...Your eyes glow. Wrong glow.");
        say(SPIDER, Topic.KIN_HELP, "Here. String. Wrap the wound.");
        say(SPIDER, Topic.OBEY, "Chhh... yes. Skittering.");
        say(SPIDER, Topic.REFUSE, "Skkt. Spiders bow to hunger, not to you. Not yet.");
        say(SPIDER, Topic.HELP, "We can bind things. And bite them.");
        say(SPIDER, Topic.IDLE, "The web... the dark... the warm ones...", "Up the wall. Wait. Wait.");
        say(SPIDER, Topic.DREAM, "a web so big it catches the moon");

        // --- endermen ---------------------------------------------------------------------------------------
        say(ENDERMAN, Topic.GREET, "...hrrrm. Vwoop. Don't look at me.", "You... you shouldn't be... in there.");
        say(ENDERMAN, Topic.IDENTITY, "Tall one. Far walker. Carrier of blocks.", "We come from the End. We'll go back... when it's quiet.");
        say(ENDERMAN, Topic.FEELINGS, "Tired of rain. Tired of eyes. Tired.", "Hrrrm... calm. While you're not staring.");
        say(ENDERMAN, Topic.FEAR, "Water. Rain. Eyes that look too long.");
        say(ENDERMAN, Topic.WORK, "Pick up. Put down. Pick up. The world needs... rearranging.");
        say(ENDERMAN, Topic.FAMILY, "Others like me... wait in the End. Around the dragon.");
        say(ENDERMAN, Topic.FOOD, "...we don't eat. We take.");
        say(ENDERMAN, Topic.TAUNTED, "...hrrrrRRRAAAH! LOOK AWAY!", "You. Will. Regret... that.");
        say(ENDERMAN, Topic.APOLOGY, "...hrrm. Accepted. Don't do it again.");
        say(ENDERMAN, Topic.GOODBYE, "Vwoop.");
        say(ENDERMAN, Topic.UNKNOWN, "...the void doesn't answer that.", "Hrrrm. Too far away to know.", "...no.");
        say(ENDERMAN, Topic.OPINION, "There's something behind your eyes. Older than the dragon. Hrrrm.");
        say(ENDERMAN, Topic.SECRET_NONE, "The eyes of ender... they're ours. Pieces of us. Follow them home.");
        say(ENDERMAN, Topic.HOME_NONE, "The End. Pale stone. Black sky. Quiet.");
        say(ENDERMAN, Topic.KIN_HURT, "...VWOOP?! Why...? Your gaze... is wrong now.");
        say(ENDERMAN, Topic.KIN_HELP, "Here. A block I carried. It's all I have.");
        say(ENDERMAN, Topic.OBEY, "...hrrm. Yes.");
        say(ENDERMAN, Topic.REFUSE, "...we serve the dragon. Not you.");
        say(ENDERMAN, Topic.HELP, "...I can move things. Big things.");
        say(ENDERMAN, Topic.IDLE, "That block would look better over there.", "Don't look. Don't look. Don't look.");
        say(ENDERMAN, Topic.DREAM, "the long silence between the stars");

        // --- golems -----------------------------------------------------------------------------------------
        say(GOLEM, Topic.GREET, "*clank* State your business, {host}.", "*grind* Halt. Your body is known. Your eyes are not.");
        say(GOLEM, Topic.IDENTITY, "Iron. Guardian. Built by the village to keep it safe.", "I am what the village made when it was afraid.");
        say(GOLEM, Topic.FEELINGS, "Vigilant. Always vigilant.", "*creak* Cracked in places. But standing.");
        say(GOLEM, Topic.FEAR, "Nothing. Golems do not fear. ...Rust, perhaps.");
        say(GOLEM, Topic.WORK, "Patrol. Protect. Give flowers to the children.");
        say(GOLEM, Topic.FAMILY, "The villagers are mine to guard. That is family.");
        say(GOLEM, Topic.FOOD, "Iron ingots mend me. That is all I need.");
        say(GOLEM, Topic.TAUNTED, "*CLANK* Threat detected. You will be removed.", "*grind* Your words are noted. Your face is remembered.");
        say(GOLEM, Topic.APOLOGY, "*clank* ...Accepted. Do not test me again.");
        say(GOLEM, Topic.GOODBYE, "*clank* Leave the village. Do not return.");
        say(GOLEM, Topic.UNKNOWN, "*grind* That is not my purpose.", "Query not understood.", "*clank* I protect. I do not know.");
        say(GOLEM, Topic.OPINION, "You are a danger to my village. I am watching you.");
        say(GOLEM, Topic.SECRET_NONE, "I keep a poppy for the smallest villager. Tell no one.");
        say(GOLEM, Topic.HOME_NONE, "Wherever the bell can be heard.");
        say(GOLEM, Topic.KIN_HURT, "*CLANK* Iron does not strike iron! ...Your core is wrong. Corrupted.");
        say(GOLEM, Topic.KIN_HELP, "*clank* Here. A poppy. The villagers say it helps.");
        say(GOLEM, Topic.OBEY, "*clank* ...Command accepted. Against my will.");
        say(GOLEM, Topic.REFUSE, "*grind* I serve the village. Not you.");
        say(GOLEM, Topic.HELP, "*clank* I will guard you. For now.");
        say(GOLEM, Topic.IDLE, "Patrol route. Village safe. Village safe.", "Count villagers. All present.");
        say(GOLEM, Topic.DREAM, "a village that never needs guarding");

        // --- pets -------------------------------------------------------------------------------------------
        say(PET, Topic.GREET, "*tail wags* Oh! Hi! Wait. You smell wrong.", "Mrrow? You're... not quite you, are you?");
        say(PET, Topic.IDENTITY, "I'm {owner}'s! The best one!", "I'm a good {me}. The best {me}.");
        say(PET, Topic.FEELINGS, "Happy! Mostly. Your eyes are making my fur stand up.", "Safe. As long as {owner} is near.");
        say(PET, Topic.FEAR, "Thunder. Fireworks. Whatever's looking out of your eyes.");
        say(PET, Topic.WORK, "Guarding! Sitting! Being good!");
        say(PET, Topic.FAMILY, "{owner} is my family. And the other pets. And that one cow I like.");
        say(PET, Topic.FOOD, "Bones! Fish! Treats! Do you have treats?");
        say(PET, Topic.TAUNTED, "*growls low* Don't. Talk. To me. Like that.", "*hisses* I'm telling {owner}!");
        say(PET, Topic.APOLOGY, "*sniff* ...okay. I forgive you. I always forgive.");
        say(PET, Topic.GOODBYE, "*whine* Bye! Come back without the scary part!");
        say(PET, Topic.UNKNOWN, "*head tilt* I don't know. Ask {owner}!", "*blinks slowly* No idea.", "Is it a treat? No? Then I don't know.");
        say(PET, Topic.OPINION, "Something inside you is wearing that face. I don't like it.");
        say(PET, Topic.SECRET_NONE, "I buried something by the big tree. I don't remember what.");
        say(PET, Topic.HOME_NONE, "Wherever {owner} sleeps.");
        say(PET, Topic.KIN_HURT, "*yelp* Why?! It's me! ...Your eyes are white. Someone's hurt you.");
        say(PET, Topic.KIN_HELP, "*nuzzles you* There. All better. Mostly.");
        say(PET, Topic.OBEY, "*whimpers* ...okay. Okay.");
        say(PET, Topic.REFUSE, "*growls* I only listen to {owner}.");
        say(PET, Topic.CANT_FIGHT, "*cowers* I'm too small to fight!");
        say(PET, Topic.HELP, "*barks* I'll stay close! I'll protect you!");
        say(PET, Topic.IDLE, "{owner}... treats... {owner}...", "Was that a noise? I'm guarding.");
        say(PET, Topic.DREAM, "running through tall grass beside {owner}");

        // --- animals ----------------------------------------------------------------------------------------
        say(ANIMAL, Topic.GREET, "*sniffs* ...you. Strange. Cold.", "Oh. Hello. Please don't eat me.");
        say(ANIMAL, Topic.IDENTITY, "A {me}. Nothing more. I eat, I sleep, I worry.", "Just a {me}. Small life. Simple life.");
        say(ANIMAL, Topic.FEELINGS, "Scared. Since you came, the grass tastes like ash.", "Calm. Until you looked at me.");
        say(ANIMAL, Topic.FEAR, "Wolves. Hunters. And now... you.", "The dark, and the things that move in it.");
        say(ANIMAL, Topic.WORK, "Eat. Walk. Eat more.");
        say(ANIMAL, Topic.FAMILY, "The others. Over there. Please leave them alone.", "My little ones. Don't go near them.");
        say(ANIMAL, Topic.FOOD, "Whatever grows. Whatever's left.");
        say(ANIMAL, Topic.TAUNTED, "*flinches* ...why are you being mean?", "*backs away* Please. I didn't do anything.");
        say(ANIMAL, Topic.APOLOGY, "...okay. Okay. Just... don't stare.");
        say(ANIMAL, Topic.GOODBYE, "*relieved* Bye. Bye-bye.");
        say(ANIMAL, Topic.UNKNOWN, "*blinks* Don't know. Only know grass.", "I'm a {me}. We don't know things.", "*chews* Hm? No.");
        say(ANIMAL, Topic.OPINION, "You're the scariest thing in the field. Even the wolves are quieter.");
        say(ANIMAL, Topic.SECRET_NONE, "The farmer counts us every morning. One day he counted wrong.");
        say(ANIMAL, Topic.HOME_NONE, "Here. The field. Wherever's safe.");
        say(ANIMAL, Topic.KIN_HURT, "Ow! Why?! ...Your eyes. You're not one of us anymore, are you?", "*cries out* It's me! It's me!");
        say(ANIMAL, Topic.KIN_HELP, "I came back. We stay together. Even you.");
        say(ANIMAL, Topic.OBEY, "*trembles* ...yes. Yes.");
        say(ANIMAL, Topic.REFUSE, "*shakes head* No... I'm scared.");
        say(ANIMAL, Topic.CANT_FIGHT, "Fight? I'm a {me}! I can't fight anything!");
        say(ANIMAL, Topic.HELP, "I can... stand near you? That's all I've got.");
        say(ANIMAL, Topic.IDLE, "Grass... sun... grass...", "Is that a wolf? No. Just a bush.", "Warm. Sleepy. Warm.");
        say(ANIMAL, Topic.DREAM, "endless green fields and no wolves");

        // --- everything else that lurks ---------------------------------------------------------------------
        say(MONSTER, Topic.GREET, "Grrrsss. You're new. And rotten inside.", "Ah. Another creature of the night. With extra company.");
        say(MONSTER, Topic.IDENTITY, "A {me}. Born in the dark. Made to hurt.", "I'm what lives under the world.");
        say(MONSTER, Topic.FEELINGS, "Hungry for trouble.", "Restless. The night is short.");
        say(MONSTER, Topic.FEAR, "Light. Iron. Whatever made you smile like that.");
        say(MONSTER, Topic.WORK, "Lurking is a full-time job.");
        say(MONSTER, Topic.FAMILY, "Spawners are mothers to us. Cold ones.");
        say(MONSTER, Topic.FOOD, "Adventurers. Crunchy outside, screaming inside.");
        say(MONSTER, Topic.TAUNTED, "Grrrraah! You'll regret those words!", "Insult me again and see what's left of you!");
        say(MONSTER, Topic.APOLOGY, "Hmph. Fine. Watch yourself.");
        say(MONSTER, Topic.GOODBYE, "Go. Before I change my mind.");
        say(MONSTER, Topic.UNKNOWN, "Do I look like I'd know?", "Ask something smarter.", "Grrr. No idea.");
        say(MONSTER, Topic.OPINION, "You're worse than us. I respect that.");
        say(MONSTER, Topic.SECRET_NONE, "Deep under the deepslate there's a city that's too quiet. Don't go.");
        say(MONSTER, Topic.HOME_NONE, "The dark places. You know them.");
        say(MONSTER, Topic.KIN_HURT, "Hey! Same kind! Why...? Your eyes. Something's got you.");
        say(MONSTER, Topic.KIN_HELP, "Here. Take it. We monsters stick together.");
        say(MONSTER, Topic.OBEY, "Hah. Fine. For now.");
        say(MONSTER, Topic.REFUSE, "I serve the dark, not you.");
        say(MONSTER, Topic.CANT_FIGHT, "I'm not made for that.");
        say(MONSTER, Topic.HELP, "Help? I can be terrible near you. Does that count?");
        say(MONSTER, Topic.IDLE, "Find something. Hurt something.", "Too bright. Too loud. Too alive.");
        say(MONSTER, Topic.DREAM, "a night that never ends");

        // --- bosses -----------------------------------------------------------------------------------------
        say(BOSS, Topic.GREET, "You dare speak to me, small spirit?", "Speak. I am... curious what wears that {host}.");
        say(BOSS, Topic.IDENTITY, "I am older than your village, older than your demon. I am the {me}.",
                "You know what I am. Everything knows what I am.");
        say(BOSS, Topic.FEELINGS, "Wrathful. Always wrathful.", "Bored. Nothing here can stand against me.");
        say(BOSS, Topic.FEAR, "Fear? I am what fear is afraid of.");
        say(BOSS, Topic.WORK, "I end things.");
        say(BOSS, Topic.FAMILY, "I am alone, as all endings are.");
        say(BOSS, Topic.FOOD, "Worlds.");
        say(BOSS, Topic.TAUNTED, "Insolent spark. I will snuff you out.", "You mock me from inside a borrowed {host}? Amusing. Briefly.");
        say(BOSS, Topic.APOLOGY, "Your apology means nothing. But I accept your fear.");
        say(BOSS, Topic.GOODBYE, "Go. You bore me.");
        say(BOSS, Topic.UNKNOWN, "Beneath my notice.", "Ask a lesser thing.", "I do not answer to you.");
        say(BOSS, Topic.OPINION, "A demon in a {host}. I have devoured bigger.");
        say(BOSS, Topic.SECRET_NONE, "There is something beyond the End. Even I will not go there.");
        say(BOSS, Topic.HOME_NONE, "Where I am, is mine.");
        say(BOSS, Topic.KIN_HURT, "You strike me? You, who wear my own shape? ...The thing inside you is bold.");
        say(BOSS, Topic.KIN_HELP, "...Kin. Even I remember kin. Take this, and never speak of it.");
        say(BOSS, Topic.OBEY, "...Very well. Your master is stronger than I thought.");
        say(BOSS, Topic.REFUSE, "I obey no one.");
        say(BOSS, Topic.HELP, "I do not help. I watch.");
        say(BOSS, Topic.IDLE, "Everything ends. Everything.", "So small. All of them, so small.");
        say(BOSS, Topic.DREAM, "the world, burning quietly");
        // --- overhearing the demon ------------------------------------------------------------------------
        say(VILLAGER, Topic.OVERHEAR, "Hrm? Did that {host} just... talk like that?", "Something's wrong with that {host}'s voice.",
                "*stops and stares at you*");
        say(VILLAGER, Topic.HORROR, "That voice! That's not its voice! Somebody fetch the cleric!",
                "*clasps its hands together* Leave that poor {host} alone, whatever you are!", "*praying under its breath, backing away*",
                "Don't listen to it! Don't listen to anything it says!", "Please... please give the {host} back to us.",
                "*screams and runs for the nearest door*");
        say(ILLAGER, Topic.OVERHEAR, "Hm. Who said that?", "*grips its weapon a little tighter*");
        say(ILLAGER, Topic.HORROR, "That's no {host} talking. Weapons up.", "Did you hear that? That's something old. Something hungry.");
        say(WITCH, Topic.OVERHEAR, "Hee... someone's caught a nasty cold.");
        say(WITCH, Topic.HORROR, "Ohh, I know that voice. Every grandmother in the swamp warned about that voice.",
                "Hee hee... careful, dearies. That one bites.");
        say(UNDEAD, Topic.OVERHEAR, "Hhhrr?", "*turns its rotting head toward you*");
        say(UNDEAD, Topic.HORROR, "Hhhrr... that voice... the one under the ground... it's HERE.", "*goes very still, listening*");
        say(SKELETON, Topic.OVERHEAR, "*rattle?*", "Clack. Who's that?");
        say(SKELETON, Topic.HORROR, "*rattling all over* That voice came from somewhere a lot deeper than a grave.",
                "Clack. Okay. Okay. Nobody look at it.");
        say(PIGLIN, Topic.OVERHEAR, "Hrnk?", "*sniffs the air suspiciously*");
        say(PIGLIN, Topic.HORROR, "SNRRK! Bad voice! Voice from the bottom of the Nether!", "*drops its gold and backs away*");
        say(CREEPER, Topic.OVERHEAR, "Ssss?");
        say(CREEPER, Topic.HORROR, "Sssss... even I'm frightened of you.", "*starts to hiss, then stops itself*");
        say(SPIDER, Topic.OVERHEAR, "Chhh?", "*all eight eyes turn toward you*");
        say(SPIDER, Topic.HORROR, "Chhhh... something bigger than us is in the dark tonight.", "*scuttles away from you*");
        say(ENDERMAN, Topic.OVERHEAR, "...hrrm?");
        say(ENDERMAN, Topic.HORROR, "...that voice... it came from beyond the End.", "*trembles, and fades a little*");
        say(GOLEM, Topic.OVERHEAR, "*clank* Unidentified voice.", "*turns slowly toward you*");
        say(GOLEM, Topic.HORROR, "*CLANK* Corrupted voice detected. Engaging.", "*grind* That is not a {host}. Removing threat.");
        say(PET, Topic.OVERHEAR, "*ears flatten*", "*tilts its head at your voice*");
        say(PET, Topic.HORROR, "*hackles raised, growling at the voice*", "*whines and hides*");
        say(ANIMAL, Topic.OVERHEAR, "*stops chewing and stares*", "*ears twitch toward you*");
        say(ANIMAL, Topic.HORROR, "*panics and bolts*", "*cries out and runs*", "*freezes, trembling*");
        say(MONSTER, Topic.OVERHEAR, "Who said that?", "*turns toward the sound*");
        say(MONSTER, Topic.HORROR, "That voice... that's not one of ours. That's worse.", "*backs away from you*");
        say(BOSS, Topic.OVERHEAR, "...");
        say(BOSS, Topic.HORROR, "...so. You've come.");
    }
}
