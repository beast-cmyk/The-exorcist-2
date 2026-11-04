package com.possessedgrin.power;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.PossessedGrinMod;
import com.possessedgrin.haunt.AtticSounds;
import com.possessedgrin.haunt.BedShaking;
import com.possessedgrin.haunt.DemonIdolItem;
import com.possessedgrin.haunt.Rite;
import com.possessedgrin.haunt.SpiritBoard;
import com.possessedgrin.haunt.Subliminal;
import com.possessedgrin.voice.Conversation;
import com.possessedgrin.haunt.Exorcism;
import com.possessedgrin.haunt.Haunting;
import com.possessedgrin.stage.Stages;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the key-activated powers. The client only says "I pressed power X"; everything is checked and
 * done here (are you possessing something, is the power off cooldown...), so it works the same in multiplayer.
 */
public final class PossessedPowers {
    /** Client to server: one byte, the {@link Power} used. */
    public static final Identifier USE_POWER = PossessedGrin.id("use_power");
    /** Server to clients: an entity id whose head should turn around. */
    public static final Identifier HEAD_TURN = PossessedGrin.id("head_turn");
    /** Server to clients: an entity id and how it taunts (0 laugh, 1 hiss, 2 growl), to animate it. */
    public static final Identifier TAUNT = PossessedGrin.id("taunt");

    /** Per player: the game tick at which each power is ready again. Server thread only. */
    private static final Map<UUID, long[]> READY_AT = new HashMap<>();

    private PossessedPowers() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(USE_POWER, (server, player, handler, buf, responseSender) -> {
            int ordinal = buf.readByte();
            server.execute(() -> use(player, ordinal));
        });
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            Telekinesis.tick(world);
            ColdPresence.tick(world);
            Haunting.tick(world);
            BedShaking.tick(world);
            Exorcism.tick(world);
            Rite.tick(world);
            SpiritBoard.tick(world);
            AtticSounds.tick(world);
            Subliminal.tick(world, PossessedGrinMod.DEMON_IDOL);
            DemonIdolItem.tick(world, PossessedGrinMod.DEMON_IDOL);
        });
    }

    private static void use(ServerPlayerEntity player, int ordinal) {
        Power[] powers = Power.values();
        if (ordinal < 0 || ordinal >= powers.length) {
            return;
        }
        MobEntity host = PossessionComponent.getHost(player);
        if (host == null || !host.isAlive()) {
            return;
        }
        Power power = powers[ordinal];
        int stage = Stages.of(host);
        boolean invitedJump = power == Power.BODY_JUMP && Rite.offerOpen(player.getUuid());
        if (stage < power.stage && !invitedJump) {
            long wait = Math.max(0L, (power.stage == Stages.POSSESSION ? Stages.POSSESSION_AT : Stages.OPPRESSION_AT) - Stages.elapsed(host));
            player.sendMessage(Text.literal("The " + host.getType().getName().getString().toLowerCase(Locale.ROOT)
                    + "'s soul still holds on. " + power.title + " awakens at stage " + roman(power.stage) + ", "
                    + Stages.name(power.stage) + " (about " + Math.max(1L, (wait + 19) / 20) + "s).")
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), true);
            return;
        }
        long now = player.world.getTime();
        long[] readyAt = READY_AT.computeIfAbsent(player.getUuid(), uuid -> new long[powers.length]);
        if (now < readyAt[ordinal]) {
            long seconds = (readyAt[ordinal] - now + 19) / 20;
            player.sendMessage(Text.literal("Your power returns in " + seconds + "s...").formatted(Formatting.DARK_GRAY, Formatting.ITALIC), true);
            return;
        }
        boolean used = switch (power) {
            case TELEKINESIS -> Telekinesis.use(player, host);
            case POLTERGEIST -> Poltergeist.use(player, host);
            case TELEPATHY -> Telepathy.use(player, host);
            case HEAD_TURN -> HeadTurn.use(player, host);
            case BILE -> Bile.use(player, host);
            case TAUNT -> Taunt.use(player, host);
            case BODY_JUMP -> BodyJump.use(player, host);
        };
        if (used) {
            // Powers come back twice as fast during an Ouija séance
            readyAt[ordinal] = now + (Haunting.inSeance(player) ? power.cooldown / 2 : power.cooldown);
            // Every power makes the room react: your screen shakes, armor stands wobble, frames spin...
            Haunting.shake(player, power.shake, power.shakeTicks);
            if (power != Power.TAUNT && power != Power.BODY_JUMP) {
                Haunting.rattle((ServerWorld) host.world, host, 6.0, power == Power.POLTERGEIST);
            }
            shakeTheirFaith(player, host, power);
        }
    }

    /** Priests in the middle of a rite who see this lose some of their faith. */
    private static void shakeTheirFaith(ServerPlayerEntity player, MobEntity host, Power power) {
        switch (power) {
            case HEAD_TURN -> Rite.witnessed(host, 10.0F, "Its head turned all the way around, cracking, and it stared at you backwards.");
            case BILE -> Rite.witnessed(host, 12.0F, "It vomited green bile across the room, and some of it hit you.");
            case TELEKINESIS -> Rite.witnessed(host, 8.0F, "Things rose into the air on their own and flew across the room.");
            case POLTERGEIST -> Rite.witnessed(host, 6.0F, "Every door slammed at once and the whole room shook.");
            case TELEPATHY -> {
                // Pulling a secret out of a priest's own head
                MobEntity read = Conversation.lookedAt(player, host, 24.0);
                if (read != null && Rite.isPriest(read)) {
                    Rite.shaken(read, 15.0F, "It just told you something about yourself nobody could know: a secret you've never said out loud to anyone.",
                            "...How do you know that? Nobody knows that. Nobody.");
                }
            }
            default -> {
            }
        }
    }

    public static String roman(int stage) {
        return switch (stage) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(stage);
        };
    }

    /** Plays a sound at the possessed mob for everyone nearby. */
    static void sound(MobEntity host, SoundEvent sound, float volume, float pitch) {
        host.world.playSound(null, host.getX(), host.getY(), host.getZ(), sound, SoundCategory.HOSTILE, volume, pitch);
    }
}
