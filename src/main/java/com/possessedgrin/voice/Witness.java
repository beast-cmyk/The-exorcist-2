package com.possessedgrin.voice;

import com.possessedgrin.PossessedGrin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What creatures have seen the demon do. Each creature keeps a short list of things that happened between it and
 * you: you struck it, it watched you kill something, it saw the body convulse, you mocked it, it helped you.
 * The real AI is told these, so a villager you hit yesterday still brings it up today. Saved with the world.
 */
public final class Witness {
    private static final int MAX_EVENTS = 6;
    private static final double SIGHT = 16.0;

    record Event(String text, long time) {
    }

    /** Per creature. Server thread only. */
    private static final Map<UUID, Deque<Event>> EVENTS = new HashMap<>();

    private Witness() {
    }

    /** Something happened to this creature. */
    public static void record(LivingEntity mob, String text) {
        Deque<Event> events = EVENTS.computeIfAbsent(mob.getUuid(), id -> new ArrayDeque<>());
        Event last = events.peekLast();
        if (last != null && last.text().equals(text) && mob.world.getTime() - last.time() < 200) {
            return;     // don't fill its memory with the same thing over and over
        }
        Feelings.appraise(mob, text);       // before it's stored, so a first meeting doesn't count it twice
        events.addLast(new Event(text, mob.world.getTime()));
        while (events.size() > MAX_EVENTS) {
            events.pollFirst();
        }
        if (EVENTS.size() > 2000) {
            EVENTS.clear();
        }
    }

    /** Every creature that can see {@code where} remembers seeing this (except {@code except}). */
    public static void seen(LivingEntity where, @Nullable Entity except, String text) {
        if (!(where.world instanceof ServerWorld world)) {
            return;
        }
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, where.getBoundingBox().expand(SIGHT),
                m -> m != where && m != except && m.isAlive() && !PossessedGrin.isPossessed(m) && m.canSee(where))) {
            record(mob, text);
        }
    }

    /** Called after any creature is hurt. Remembers it when a possessed body did it. */
    public static void onHurt(LivingEntity victim, @Nullable Entity attacker) {
        if (victim.world.isClient || attacker == null) {
            return;
        }
        MobEntity body = attacker instanceof MobEntity mob && PossessedGrin.isPossessed(mob) ? mob
                : attacker instanceof PlayerEntity player ? PossessionComponent.getHost(player) : null;
        if (body == null || body == victim) {
            return;
        }
        String demon = "the demon wearing " + PossessedGrin.the(body);
        if (victim.isDead()) {
            seen(victim, body, "You saw " + demon + " kill " + PossessedGrin.the(victim) + ".");
        } else {
            if (victim instanceof MobEntity) {
                record(victim, capitalize(demon) + " hit you.");
            }
            seen(victim, body, "You saw " + demon + " attack " + PossessedGrin.the(victim) + ".");
        }
    }

    /** Wipes this creature's memory (a body you've left remembers nothing). */
    public static void forget(UUID mob) {
        EVENTS.remove(mob);
        Feelings.forget(mob);
    }

    /** What this creature remembers, oldest first, with roughly how long ago. */
    public static List<String> memories(LivingEntity mob) {
        List<String> out = new ArrayList<>();
        Deque<Event> events = EVENTS.get(mob.getUuid());
        if (events == null) {
            return out;
        }
        long now = mob.world.getTime();
        for (Event event : events) {
            out.add(event.text() + " (" + ago(now - event.time()) + ")");
        }
        return out;
    }

    private static String ago(long ticks) {
        if (ticks < 1200) {
            return "just now";
        }
        if (ticks < 12000) {
            return "a little while ago";
        }
        if (ticks < 24000) {
            return "earlier today";
        }
        long days = ticks / 24000;
        return days == 1 ? "yesterday" : days + " days ago";
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // --- saving (used by AiVoice's memory file) ---

    static Map<UUID, Deque<Event>> all() {
        return EVENTS;
    }

    static void clear() {
        EVENTS.clear();
    }
}
