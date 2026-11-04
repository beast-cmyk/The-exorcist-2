package com.possessedgrin.haunt;

import com.possessedgrin.PossessedGrin;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Server to clients: something visible happens to a possessed body for a few ticks, for everyone who can see it
 * (and its possessor). One byte for what, the entity id, and how long.
 */
public final class Fx {
    public static final Identifier ID = PossessedGrin.id("fx");

    /** The body thrashes violently (the bed scene). */
    public static final int THRASH = 0;
    /** The body goes rigid as a board and floats flat. */
    public static final int RIGID = 1;
    /** The demon's true face shows for a few frames. */
    public static final int TRUE_FACE = 2;

    private Fx() {
    }

    public static void send(Entity body, int kind, int ticks) {
        Set<ServerPlayerEntity> watchers = new LinkedHashSet<>(PlayerLookup.tracking(body));
        PlayerEntity rider = PossessedGrin.possessorOf(body);
        if (rider instanceof ServerPlayerEntity serverRider) {
            watchers.add(serverRider);
        }
        for (ServerPlayerEntity watcher : watchers) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeByte(kind);
            buf.writeVarInt(body.getId());
            buf.writeVarInt(ticks);
            ServerPlayNetworking.send(watcher, ID, buf);
        }
    }
}
