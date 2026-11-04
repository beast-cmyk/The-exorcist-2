package com.possessedgrin.stage;

import com.possessedgrin.PossessedGrin;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The three stages of a possession, like the book and the film: first something is in the house, then it is in
 * the body and fighting for it, then it has won.
 *
 * <ol>
 *   <li><b>Infestation</b> (0 to 12 s): knocking, cold, dying lights. The mob's eyes flicker white. You can read
 *       minds and talk.</li>
 *   <li><b>Oppression</b> (12 to 30 s): the body convulses and sickens, the voice drops, the head turns,
 *       things fly about, and the body can float.</li>
 *   <li><b>Possession</b> (30 s on): the full horror. Blood, the grin, the spider-walk, telekinesis, bile and
 *       unholy strength.</li>
 * </ol>
 *
 * <p>Both sides keep a map of possessed mob (entity id) to the world time its possession began. The server owns it
 * and sends updates to clients, which only use theirs for drawing. In singleplayer both live in the same game, so
 * they are kept apart.
 */
public final class Stages {
    public static final int NONE = 0;
    public static final int INFESTATION = 1;
    public static final int OPPRESSION = 2;
    public static final int POSSESSION = 3;

    public static final int OPPRESSION_AT = 240;    // 12 seconds
    public static final int POSSESSION_AT = 600;    // 30 seconds
    /** How long a body lasts while you feed on it: one Minecraft day, 20 minutes. */
    public static final int LIFETIME = 24000;

    /** Server to clients: varint host entity id, varlong ticks since the possession began (-1: it ended). */
    public static final Identifier SYNC = PossessedGrin.id("stage");

    private static final Map<Integer, Long> SERVER = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> CLIENT = new ConcurrentHashMap<>();

    private Stages() {
    }

    public static int stageAt(long elapsed) {
        return elapsed >= POSSESSION_AT ? POSSESSION : elapsed >= OPPRESSION_AT ? OPPRESSION : INFESTATION;
    }

    public static String name(int stage) {
        return switch (stage) {
            case INFESTATION -> "Infestation";
            case OPPRESSION -> "Oppression";
            case POSSESSION -> "Possession";
            default -> "None";
        };
    }

    /** Ticks since this mob's possession began, or -1 if it isn't known. */
    public static long elapsed(@Nullable Entity host) {
        if (host == null) {
            return -1;
        }
        Long start = (host.world.isClient ? CLIENT : SERVER).get(host.getId());
        return start == null ? -1 : Math.max(0L, host.world.getTime() - start);
    }

    /** The stage of a possessed mob, or {@link #NONE} if nothing is possessing it. */
    public static int of(@Nullable Entity host) {
        if (host == null || !PossessedGrin.isPossessed(host)) {
            return NONE;
        }
        long elapsed = elapsed(host);
        return elapsed < 0 ? INFESTATION : stageAt(elapsed);
    }

    /** The stage of the mob this player is possessing, or {@link #NONE}. */
    public static int ofPlayer(@Nullable PlayerEntity player) {
        return player == null ? NONE : of(PossessionComponent.getHost(player));
    }

    /** For a possessed mob or its possessor: the stage of the possession. */
    public static int ofEither(@Nullable Entity entity) {
        if (entity instanceof PlayerEntity player) {
            return ofPlayer(player);
        }
        return of(entity);
    }

    /** 0 to 1: how far through the current stage, for effects that build up. */
    public static float progress(@Nullable Entity host) {
        long elapsed = elapsed(host);
        if (elapsed < 0) {
            return 0.0F;
        }
        if (elapsed < OPPRESSION_AT) {
            return elapsed / (float) OPPRESSION_AT;
        }
        if (elapsed < POSSESSION_AT) {
            return (elapsed - OPPRESSION_AT) / (float) (POSSESSION_AT - OPPRESSION_AT);
        }
        return 1.0F;
    }

    /** 0 to 1: how much of the body's life you have drawn out of it. At 1 it dies. */
    public static float drain(@Nullable Entity host) {
        long elapsed = elapsed(host);
        return elapsed < 0 ? 0.0F : Math.min(1.0F, elapsed / (float) LIFETIME);
    }

    // --- server bookkeeping (used by StageDirector) ---

    static Map<Integer, Long> server() {
        return SERVER;
    }

    // --- client side ---

    /** Applies an update from the server. Returns the stage the mob was in before (0 if unknown). */
    public static int clientSync(int hostId, long elapsed, long now) {
        Long before = CLIENT.get(hostId);
        int previous = before == null ? NONE : stageAt(Math.max(0L, now - before));
        if (elapsed < 0) {
            CLIENT.remove(hostId);
        } else {
            CLIENT.put(hostId, now - elapsed);
        }
        return previous;
    }

    public static void clientClear() {
        CLIENT.clear();
    }
}
