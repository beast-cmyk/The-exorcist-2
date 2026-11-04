package com.possessedgrin.client;

import net.minecraft.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of the subliminal flash: for a few frames the possessed body wears the demon's own face, then it's
 * gone, and you're left unsure you saw anything. Render thread only.
 */
public final class TrueFace {
    /** Entity id to the world time the flash ends. */
    private static final Map<Integer, Long> UNTIL = new HashMap<>();

    private TrueFace() {
    }

    public static void flash(int entityId, long now, int ticks) {
        UNTIL.put(entityId, now + ticks);
    }

    public static boolean showing(Entity entity) {
        Long until = UNTIL.get(entity.getId());
        if (until == null) {
            return false;
        }
        if (entity.world.getTime() >= until) {
            UNTIL.remove(entity.getId());
            return false;
        }
        return true;
    }

    public static void clear() {
        UNTIL.clear();
    }
}
