package com.possessedgrin.client;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Head-turn animations playing on this client: the head twists 180 degrees in about half a second, stares
 * backwards for over a second, then slowly twists back.
 */
public final class HeadTurns {
    private static final float TURN = 12.0F;
    private static final float HOLD_UNTIL = 36.0F;
    private static final float DURATION = 50.0F;

    /** Entity id to the client world tick when its head started turning. */
    private static final Map<Integer, Long> STARTED = new HashMap<>();

    private HeadTurns() {
    }

    static void start(int entityId, long now) {
        STARTED.put(entityId, now);
    }

    /** How far the head is turned right now, in radians (0 when not turning). */
    public static float yawOffset(Entity entity, float tickDelta) {
        Long start = STARTED.get(entity.getId());
        if (start == null) {
            return 0.0F;
        }
        float t = (entity.world.getTime() - start) + tickDelta;
        if (t < 0.0F || t >= DURATION) {
            STARTED.remove(entity.getId());
            return 0.0F;
        }
        float amount;
        if (t < TURN) {
            amount = smooth(t / TURN);
        } else if (t < HOLD_UNTIL) {
            amount = 1.0F;
        } else {
            amount = 1.0F - smooth((t - HOLD_UNTIL) / (DURATION - HOLD_UNTIL));
        }
        return amount * (float) Math.PI;
    }

    private static float smooth(float x) {
        x = MathHelper.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }
}
