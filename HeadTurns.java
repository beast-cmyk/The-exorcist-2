package com.possessedgrin.client;

import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Head-turn animations playing on this client: the head cranks round 180 degrees in six jerky clicks, each with a
 * crack of bone, stares backwards for over a second with a faint tremor, then slowly twists back.
 */
public final class HeadTurns {
    private static final float TURN = 16.0F;
    private static final int CLICKS = 6;
    private static final float HOLD_UNTIL = 40.0F;
    private static final float DURATION = 56.0F;

    /** Entity id to the client world tick when its head started turning. */
    private static final Map<Integer, Long> STARTED = new HashMap<>();
    /** Entity id to the last click it made a sound for. */
    private static final Map<Integer, Integer> CLICKED = new HashMap<>();

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
            CLICKED.remove(entity.getId());
            return 0.0F;
        }
        float amount;
        if (t < TURN) {
            // Six clicks: each one snaps quickly, then holds, like something ratcheting the head round
            float progress = t / TURN * CLICKS;
            int click = (int) progress;
            float within = progress - click;
            amount = (click + smooth(Math.min(1.0F, within / 0.35F))) / CLICKS;
            if (CLICKED.getOrDefault(entity.getId(), -1) < click) {
                CLICKED.put(entity.getId(), click);
                entity.world.playSound(entity.getX(), entity.getEyeY(), entity.getZ(), SoundEvents.BLOCK_BONE_BLOCK_BREAK,
                        SoundCategory.HOSTILE, 0.5F, 0.5F + click * 0.06F, false);
            }
        } else if (t < HOLD_UNTIL) {
            amount = 1.0F + MathHelper.sin(t * 2.7F) * 0.012F;      // holding, trembling
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
