package com.possessedgrin.client;

import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;

/** Telepathy's sight: for 10 seconds, every living creature within 64 blocks glows through walls (for you only). */
public final class SecondSight {
    private static final int DURATION_TICKS = 200;
    private static final double RANGE = 64.0;

    private static long openUntil = Long.MIN_VALUE;

    private SecondSight() {
    }

    static void open(long now) {
        openUntil = now + DURATION_TICKS;
    }

    public static boolean reveals(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.world.getTime() > openUntil) {
            return false;
        }
        if (!(entity instanceof LivingEntity) || entity == client.player || entity.squaredDistanceTo(client.player) > RANGE * RANGE) {
            return false;
        }
        MobEntity host = PossessionComponent.getHost(client.player);
        return host != null && entity != host;
    }
}
