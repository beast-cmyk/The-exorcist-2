package com.possessedgrin.client;

import com.possessedgrin.haunt.Fx;
import com.possessedgrin.haunt.Haunting;
import com.possessedgrin.power.PossessedPowers;
import com.possessedgrin.power.Power;
import com.possessedgrin.stage.Stages;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;

/** Client start-up: the power keys, and listening for head turns, screen shakes, possession stages and body effects. */
public final class PossessedGrinClient implements ClientModInitializer {
    private static final String KEY_CATEGORY = "category.possessedgrin.powers";
    private static final KeyBinding[] KEYS = new KeyBinding[Power.values().length];

    @Override
    public void onInitializeClient() {
        for (Power power : Power.values()) {
            KEYS[power.ordinal()] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.possessedgrin." + power.id, InputUtil.Type.KEYSYM, power.defaultKey, KEY_CATEGORY));
        }
        ClientTickEvents.END_CLIENT_TICK.register(PossessedGrinClient::onTick);
        ClientPlayNetworking.registerGlobalReceiver(PossessedPowers.TAUNT, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            int kind = buf.readByte();
            client.execute(() -> PossessedMotion.taunt(entityId, kind));
        });
        ClientPlayNetworking.registerGlobalReceiver(Stages.SYNC, (client, handler, buf, responseSender) -> {
            int hostId = buf.readVarInt();
            long elapsed = buf.readVarLong();
            client.execute(() -> {
                if (client.world != null) {
                    Stages.clientSync(hostId, elapsed, client.world.getTime());
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            Stages.clientClear();
            PossessedMotion.clear();
            DemonPoses.clear();
            TrueFace.clear();
        }));
        ClientPlayNetworking.registerGlobalReceiver(Fx.ID, (client, handler, buf, responseSender) -> {
            int kind = buf.readByte();
            int entityId = buf.readVarInt();
            int ticks = buf.readVarInt();
            client.execute(() -> {
                if (client.world == null) {
                    return;
                }
                long now = client.world.getTime();
                switch (kind) {
                    case Fx.THRASH -> PossessedMotion.thrash(entityId, ticks);
                    case Fx.RIGID -> DemonPoses.rigid(entityId, now + ticks);
                    case Fx.TRUE_FACE -> TrueFace.flash(entityId, now, ticks);
                    default -> {
                    }
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(PossessedPowers.HEAD_TURN, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            client.execute(() -> {
                if (client.world != null) {
                    HeadTurns.start(entityId, client.world.getTime());
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Haunting.SHAKE, (client, handler, buf, responseSender) -> {
            float strength = buf.readFloat();
            int ticks = buf.readVarInt();
            client.execute(() -> {
                if (client.world != null) {
                    CameraShake.start(strength, ticks, client.world.getTime());
                }
            });
        });
    }

    private static void onTick(MinecraftClient client) {
        for (Power power : Power.values()) {
            while (KEYS[power.ordinal()].wasPressed()) {
                if (client.player == null || client.world == null || PossessionComponent.getHost(client.player) == null) {
                    continue;   // powers only work while possessing a mob
                }
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeByte(power.ordinal());
                ClientPlayNetworking.send(PossessedPowers.USE_POWER, buf);
                if (power == Power.TELEPATHY) {
                    SecondSight.open(client.world.getTime());
                }
            }
        }
    }
}
