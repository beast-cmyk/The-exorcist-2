package com.possessedgrin.haunt;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.voice.AiVoice;
import com.possessedgrin.voice.Conversation;
import com.possessedgrin.voice.Feelings;
import com.possessedgrin.voice.Witness;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The spirit board, from the other side. During a séance, a villager nearby (a child, if there is one) comes to the
 * board and asks it questions, and you are the one who answers: type your answer in chat and the planchette spells
 * it out, letter by letter, knocking. After a few answers the villager trusts you enough to invite you in. An
 * invited creature can be possessed whatever it is, and gives itself up far faster (see {@link Invitations}).
 */
public final class SpiritBoard {
    private static final int ANSWERS_TO_INVITE = 3;
    private static final int WAIT_FOR_ANSWER = 600;
    private static final int LETTER_TICKS = 7;
    private static final double RANGE = 14.0;

    private static final String[][] QUESTIONS = {
            {"*rests two fingers on the planchette* Is someone there?", "Hello? Is anybody here with us?", "Are you there? You can talk to me."},
            {"What's your name?", "Do you have a name? I'll tell you mine.", "Who are you?"},
            {"Are you nice?", "Where do you live?", "Are you lonely too?", "Can you see me right now?"},
            {"Do you want to be my friend?", "Will you stay with me when everyone's asleep?", "Can I tell you a secret?"}};
    private static final String[] PLEASED = {
            "It moved! It really moved!", "*giggles* It answered me.", "I knew you were real.", "*leans closer to the board* Tell me more."};
    private static final String[] UNEASY = {
            "...That's not very nice.", "*pulls its fingers back a little* Why would you say that?", "*glances behind itself* ...Okay."};
    private static final String[] INVITE = {
            "*whispers to the board* You can come in, if you want. I don't mind.",
            "*very quietly* If you want somewhere to stay, you can stay with me.",
            "*smiles at nothing* Come on, then. Come inside. I'll keep you."};
    private static final Set<String> DARK_WORDS = Set.of("kill", "die", "dead", "death", "hate", "blood", "hell", "demon", "devil",
            "pain", "hurt", "burn", "soul", "mine", "never", "scream", "rot", "dark", "eat");

    private record Question(UUID asker, long expires) {
    }

    private record Letter(UUID player, UUID asker, String shown, long at, boolean last, String answer) {
    }

    /** Players the board is waiting on. Read from the chat thread, so concurrent. */
    private static final Map<UUID, Question> WAITING = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ANSWERED = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final List<Letter> SPELLING = new ArrayList<>();

    private SpiritBoard() {
    }

    public static void tick(ServerWorld world) {
        long now = world.getTime();
        spell(world, now);
        if (now % 20 != 11) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            UUID id = player.getUuid();
            if (!Haunting.inSeance(player)) {
                Question stale = WAITING.remove(id);
                if (stale != null) {
                    NEXT.remove(id);
                }
                continue;
            }
            MobEntity host = PossessionComponent.getHost(player);
            Entity board = host != null ? host : player;
            Question waiting = WAITING.get(id);
            if (waiting != null) {
                if (now > waiting.expires()) {
                    WAITING.remove(id);
                    NEXT.put(id, now + 300);
                    MobEntity asker = mob(world, waiting.asker());
                    if (asker != null) {
                        AiVoice.say(player, host, asker, role(asker), scene(ANSWERED.getOrDefault(asker.getUuid(), 0)),
                                "(You asked the board something a long while ago, and the planchette hasn't moved since.)", "...It's gone quiet.", 0);
                    }
                }
                continue;
            }
            if (now < NEXT.getOrDefault(id, now + 60)) {
                NEXT.putIfAbsent(id, now + 60);
                continue;
            }
            VillagerEntity asker = pickAsker(world, board, host);
            NEXT.put(id, now + 200 + world.getRandom().nextInt(200));
            if (asker == null) {
                continue;
            }
            ask(world, player, host, board, asker, now);
        }
    }

    @Nullable
    private static VillagerEntity pickAsker(ServerWorld world, Entity board, @Nullable MobEntity host) {
        VillagerEntity best = null;
        for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class, board.getBoundingBox().expand(RANGE),
                v -> v != host && v.isAlive() && !v.isSleeping() && !PossessedGrin.isPossessed(v) && !Exorcism.isExorcist(v)
                        && !Invitations.isInvited(v))) {
            // The children come first: they always want to play with the board
            if (best == null || villager.isBaby() && !best.isBaby()
                    || villager.isBaby() == best.isBaby() && villager.squaredDistanceTo(board) < best.squaredDistanceTo(board)) {
                best = villager;
            }
        }
        return best;
    }

    private static void ask(ServerWorld world, ServerPlayerEntity player, @Nullable MobEntity host, Entity board, VillagerEntity asker, long now) {
        int asked = ANSWERED.getOrDefault(asker.getUuid(), 0);
        int step = Math.min(QUESTIONS.length - 1, asked);
        String[] options = QUESTIONS[step];
        String question = options[world.getRandom().nextInt(options.length)];
        asker.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(board, 0.5F, 2));
        asker.getLookControl().lookAt(board, 30.0F, 30.0F);
        AiVoice.say(player, host, asker, role(asker), scene(asked), asked == 0
                ? "(Ask the board your first question: one short question, the way you'd really ask it.)"
                : "(Ask the board your next question: one short question that follows from what it has answered so far.)", question, 0);
        WAITING.put(player.getUuid(), new Question(asker.getUuid(), now + WAIT_FOR_ANSWER));
        Haunting.extendSeance(player, WAIT_FOR_ANSWER + 400);
        if (ANSWERED.getOrDefault(asker.getUuid(), 0) == 0) {
            player.sendMessage(Text.literal("They're asking the board. You are what answers it: type your answer in chat.")
                    .formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), false);
        }
        world.playSound(null, asker.getX(), asker.getY(), asker.getZ(), SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, SoundCategory.AMBIENT, 0.6F, 0.6F);
    }

    /** Is the board waiting on this player's answer? Safe to call from the chat thread. */
    public static boolean awaiting(UUID player) {
        return WAITING.containsKey(player);
    }

    /** The player answered: spell it out on the board. Returns false if the board wasn't waiting on them. */
    public static boolean answer(ServerPlayerEntity player, String message) {
        Question question = WAITING.remove(player.getUuid());
        if (question == null) {
            return false;
        }
        String clean = message.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", "").trim().replaceAll(" +", " ");
        if (clean.length() > 28) {
            clean = clean.substring(0, 28).trim();
        }
        if (clean.isEmpty()) {
            clean = "...";
        }
        long now = player.world.getTime();
        StringBuilder shown = new StringBuilder();
        int index = 0;
        for (char c : clean.toCharArray()) {
            shown.append(c == ' ' ? "  " : c + " ");
            boolean last = index == clean.length() - 1;
            SPELLING.add(new Letter(player.getUuid(), question.asker(), shown.toString().trim(), now + 10 + (long) index * LETTER_TICKS, last, clean));
            index++;
        }
        return true;
    }

    private static void spell(ServerWorld world, long now) {
        Iterator<Letter> it = SPELLING.iterator();
        while (it.hasNext()) {
            Letter letter = it.next();
            if (letter.at() > now) {
                continue;
            }
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(letter.player());
            MobEntity asker = mob(world, letter.asker());
            if (player == null || player.world != world) {
                it.remove();
                continue;
            }
            it.remove();
            player.sendMessage(Text.literal("The planchette spells: ").formatted(Formatting.DARK_PURPLE)
                    .append(Text.literal(letter.shown()).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)), true);
            Entity at = asker != null ? asker : player;
            world.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.BLOCK_WOOD_HIT, SoundCategory.AMBIENT, 0.5F, 1.3F + world.getRandom().nextFloat() * 0.2F);
            if (letter.last() && asker != null) {
                answered(world, player, asker, letter.answer());
            }
        }
    }

    private static void answered(ServerWorld world, ServerPlayerEntity player, MobEntity asker, String answer) {
        MobEntity host = PossessionComponent.getHost(player);
        Random random = world.getRandom();
        Witness.record(asker, "You asked the spirit board a question, and the planchette moved by itself and spelled: " + answer + ".");
        player.sendMessage(Text.literal("The board spelled: " + answer).formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), false);
        boolean dark = false;
        for (String word : answer.toLowerCase(Locale.ROOT).split(" ")) {
            dark |= DARK_WORDS.contains(word);
        }
        int count = ANSWERED.getOrDefault(asker.getUuid(), 0) + 1;
        ANSWERED.put(asker.getUuid(), count);
        if (dark) {
            Feelings.feel(asker, Feelings.Emotion.FEAR, asker.isBaby() ? 0.15F : 0.35F);
        }
        if (dark && !asker.isBaby() && random.nextBoolean()) {
            // A grown-up knows when to stop
            AiVoice.say(player, host, asker, role(asker), scene(count),
                    "(The planchette just spelled: " + answer + ". That's enough. You snatch your hand off it and refuse to go on.)",
                    "*snatches its hand off the planchette* No. No more of this.", 15);
            Conversation.flee(asker, player, 8.0, 0.6);
            ANSWERED.put(asker.getUuid(), Math.max(0, count - 2));
            return;
        }
        if (count >= ANSWERS_TO_INVITE) {
            Invitations.invite(asker);
            ANSWERED.remove(asker.getUuid());
            AiVoice.say(player, host, asker, role(asker), scene(count),
                    "(The planchette just spelled: " + answer + ". You've decided: you tell the spirit it can come and stay with you, "
                            + "inside you if it wants. Say so, softly, in your own words.)", INVITE[random.nextInt(INVITE.length)], 25);
            player.sendMessage(Text.literal(PossessedGrin.label(asker) + " has invited you in. Possess it, and it will give itself up far faster.")
                    .formatted(Formatting.GOLD, Formatting.ITALIC), false);
            world.playSound(null, asker.getX(), asker.getY(), asker.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 0.8F, 0.6F);
            Witness.record(asker, "You told the voice in the board it could come inside you.");
            return;
        }
        String[] lines = dark ? UNEASY : PLEASED;
        AiVoice.say(player, host, asker, role(asker), scene(count),
                "(The planchette just moved by itself and spelled: " + answer + ". React to it in a sentence or two, the way you really would.)",
                lines[random.nextInt(lines.length)], 20);
        NEXT.put(player.getUuid(), world.getTime() + 80 + random.nextInt(80));
    }

    /** Who is asking, for the AI. */
    private static String role(MobEntity asker) {
        return asker.isBaby()
                ? "a village child of about seven, up past bedtime, kneeling at a spirit board in the dark with your fingers on the "
                        + "planchette. You're lonely, bright, and brave the way children are, and you half-believe the spirit could be your friend."
                : "a grown-up villager who sat down at a spirit board half as a joke, and is no longer sure it's a joke.";
    }

    private static String scene(int asked) {
        return "You're at a spirit board in the dark. When you ask it something, the planchette moves by itself and spells out "
                + "the answer, letter by letter, with a little knock for each one. You've asked it " + asked + (asked == 1 ? " question" : " questions")
                + " so far, and its answers are in this conversation.";
    }

    @Nullable
    private static MobEntity mob(ServerWorld world, UUID id) {
        return world.getEntity(id) instanceof MobEntity mob ? mob : null;
    }

    public static void clear() {
        WAITING.clear();
        ANSWERED.clear();
        NEXT.clear();
        SPELLING.clear();
    }
}
