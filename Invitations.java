package com.possessedgrin.haunt;

import net.minecraft.entity.mob.MobEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creatures that asked you in: the child at the spirit board who said you could come in, the young priest who
 * offered himself. Requiem lets you possess them whatever they are, and the possession starts at stage II, since
 * they opened the door themselves. An invitation is used up once you go in.
 */
public final class Invitations {
    private static final Map<UUID, Boolean> INVITED = new ConcurrentHashMap<>();

    private Invitations() {
    }

    public static void invite(MobEntity mob) {
        INVITED.put(mob.getUuid(), Boolean.TRUE);
    }

    public static void revoke(UUID mob) {
        INVITED.remove(mob);
    }

    public static boolean isInvited(MobEntity mob) {
        return INVITED.containsKey(mob.getUuid());
    }

    /** True once: the possession of this creature should start at stage II. */
    public static boolean consume(MobEntity mob) {
        return INVITED.remove(mob.getUuid()) != null;
    }

    public static void clear() {
        INVITED.clear();
    }
}
