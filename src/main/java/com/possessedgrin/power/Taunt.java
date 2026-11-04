package com.possessedgrin.power;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.haunt.Exorcism;
import com.possessedgrin.haunt.Rite;
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
 * growl so low the ground hums, and from stage II it says something cruel, aimed at whoever you're looking at.
 * They react as if you'd insulted them, everyone nearby hears it, and priests in the middle of a rite lose faith.
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
        String said = null;
        if (stage >= Stages.OPPRESSION && random.nextFloat() < 0.75F) {
            // And then it speaks
            said = cruelty(target, random);
            Conversation.demonSays(player, host, said);
        }
        String sound = kind == LAUGH ? "laughed, high and wrong" : kind == HISS ? "hissed through its teeth" : "growled, so low the floor hummed";
        Rite.witnessed(host, 6.0F, "It " + sound + (said == null ? "." : ", and said: \"" + said + "\""));
        if (target != null) {
            Conversation.taunted(player, host, target, said);
        }
        Conversation.bystanders(player, host, target, true);
        return true;
    }

    /** Something cruel for the body to say, aimed at whoever it's looking at. Every line is original. */
    private static String cruelty(MobEntity target, Random random) {
        String you = target == null ? "you" : PossessedGrin.the(target);
        if (target != null && (Exorcism.isExorcist(target) || Rite.isPriest(target))) {
            String[] priest = {
                    "Your faith is a candle, priest. I can see it shaking from here.",
                    "Say your words. I'll wait. I have all the time there ever was.",
                    "She asked for you at the end, Father. You weren't there.",
                    "You don't believe a word of that book. I can taste it on you.",
                    "Come closer, holy man. Put your hand on its forehead. I dare you."};
            return priest[random.nextInt(priest.length)];
        }
        String[] lines = {
                "Come closer, " + you + ". I want to see what you look like when you're afraid.",
                "I know what you did. I was there. I'm always there.",
                "Nobody is coming to help you. Nobody came for this one either.",
                "Pray if you like. I enjoy the sound.",
                "This body is mine now. When it's worn out, I'll try on yours.",
                "You smell of fear, " + you + ". It suits you.",
                "Look at its hands. Look at what I've done to its hands.",
                "Run along. I'll still be here. I'm always here.",
                "I'm going to keep this one until there's nothing left of it.",
                "Do you want to hear it scream? It's still in here, you know.",
                "Sleep well tonight, " + you + ". I'll be under the bed.",
                "Everyone you love will look like this, in the end."};
        return lines[random.nextInt(lines.length)];
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
