package com.possessedgrin.power;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.stage.Stages;
import com.possessedgrin.voice.Conversation;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Taunting, no typing needed: the body throws back its head and laughs, bares its teeth and hisses, or lets out a
 * growl so low the ground hums. Whoever you're looking at reacts as if you'd insulted it, and everyone nearby
 * hears it.
 */
public final class Taunt {
    public static final int LAUGH = 0;
    public static final int HISS = 1;
    public static final int GROWL = 2;

    private Taunt() {
    }

    static boolean use(ServerPlayerEntity player, MobEntity host) {
        ServerWorld world = (ServerWorld) host.world;
        Random random = world.getRandom();
        int stage = Stages.of(host);
        int kind = random.nextInt(3);
        float deeper = stage >= Stages.POSSESSION ? 0.85F : 1.0F;

        switch (kind) {
            case LAUGH -> PossessedPowers.sound(host, SoundEvents.ENTITY_WITCH_CELEBRATE, 1.2F, 0.5F * deeper);
            case HISS -> {
                PossessedPowers.sound(host, SoundEvents.ENTITY_CAT_HISS, 1.2F, 0.45F * deeper);
                PossessedPowers.sound(host, SoundEvents.ENTITY_VEX_CHARGE, 0.7F, 0.5F * deeper);
            }
            default -> {
                PossessedPowers.sound(host, SoundEvents.ENTITY_WARDEN_LISTENING_ANGRY, 1.3F, 0.6F * deeper);
                PossessedPowers.sound(host, SoundEvents.ENTITY_RAVAGER_ROAR, 0.5F, 0.5F * deeper);
            }
        }

        // Everyone who can see the body watches it happen
        Set<ServerPlayerEntity> watchers = new LinkedHashSet<>(PlayerLookup.tracking(host));
        watchers.add(player);
        for (ServerPlayerEntity watcher : watchers) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(host.getId());
            buf.writeByte(kind);
            ServerPlayNetworking.send(watcher, PossessedPowers.TAUNT, buf);
        }

        MobEntity target = Conversation.lookedAt(player, host, 24.0);
        player.sendMessage(Text.literal(describe(kind, host, target, random)).formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
        if (target != null) {
            Conversation.taunted(player, host, target);
        }
        Conversation.bystanders(player, host, target, true);
        return true;
    }

    private static String describe(int kind, MobEntity host, MobEntity target, Random random) {
        String body = capitalize(PossessedGrin.the(host));
        String at = target == null ? "" : " at " + PossessedGrin.the(target);
        String[] lines = switch (kind) {
            case LAUGH -> new String[]{
                    body + " throws back its head and laughs" + at + ". It goes on far too long.",
                    "A laugh comes out of " + PossessedGrin.the(host) + " that does not belong to it.",
                    body + " giggles" + at + ", high and breathless, like a child who knows a secret."};
            case HISS -> new String[]{
                    body + " bares its teeth" + at + " and hisses.",
                    body + "'s lip curls back. It smiles" + at + " the way a cat smiles at a bird.",
                    "Something hisses through " + PossessedGrin.the(host) + "'s teeth, long and wet."};
            default -> new String[]{
                    "A growl rolls out of " + PossessedGrin.the(host) + ", so low the ground seems to hum.",
                    body + " growls" + at + ". The sound comes from somewhere much deeper than its chest.",
                    body + " lowers its head" + at + " and makes a sound no " + PossessedGrin.kind(host) + " should make."};
        };
        return lines[random.nextInt(lines.length)];
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
