package com.possessedgrin.power;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.haunt.Haunting;
import com.possessedgrin.haunt.Rite;
import com.possessedgrin.stage.Stages;
import com.possessedgrin.voice.Conversation;
import com.possessedgrin.voice.Witness;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The body jump (stage III): look at a creature within eight blocks and leap out of this body into it. The old body
 * drops, empty and dazed; the new one is taken at stage II, already half yours. Into a priest who offered himself,
 * it's stage III at once.
 */
public final class BodyJump {
    private static final double RANGE = 8.0;

    /** Players in the middle of a jump: Requiem lets them into anything (see BossPossession). */
    private static final Set<UUID> JUMPING = ConcurrentHashMap.newKeySet();
    /** Where the next possession starts, in ticks into it. Consumed by the stage director. */
    private static final Map<UUID, Long> HEAD_START = new HashMap<>();

    private BodyJump() {
    }

    static boolean use(ServerPlayerEntity player, MobEntity host) {
        MobEntity target = Conversation.lookedAt(player, host, RANGE);
        if (target == null || !target.isAlive() || PossessedGrin.isPossessed(target)) {
            player.sendMessage(Text.literal("Look at the creature you want to leap into.").formatted(Formatting.DARK_GRAY, Formatting.ITALIC), true);
            return false;
        }
        boolean invitedPriest = Rite.offeredBy(player.getUuid(), target);
        ServerWorld world = (ServerWorld) host.world;
        PossessionComponent possession = PossessionComponent.get(player);

        // A dark streak from one body to the other
        Vec3d from = host.getPos().add(0.0, host.getHeight() * 0.6, 0.0);
        Vec3d to = target.getPos().add(0.0, target.getHeight() * 0.6, 0.0);
        for (int i = 0; i <= 12; i++) {
            Vec3d at = from.lerp(to, i / 12.0);
            world.spawnParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.0);
            world.spawnParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.01);
        }

        JUMPING.add(player.getUuid());
        boolean jumped;
        try {
            possession.stopPossessing();
            jumped = possession.startPossessing(target);
            if (!jumped) {
                possession.startPossessing(host);       // couldn't get in: snap back into the old body
            }
        } finally {
            JUMPING.remove(player.getUuid());
        }
        if (!jumped) {
            player.sendMessage(Text.literal(PossessedGrin.label(target) + "'s soul holds the door shut. You fall back into "
                    + PossessedGrin.the(host) + ".").formatted(Formatting.DARK_GRAY, Formatting.ITALIC), true);
            return true;
        }

        HEAD_START.put(player.getUuid(), invitedPriest ? (long) Stages.POSSESSION_AT : (long) Stages.OPPRESSION_AT);
        world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 1.0F, 0.5F);
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.HOSTILE, 1.2F, 0.6F);
        Haunting.shake(player, 1.6F, 25);
        Witness.seen(target, host, "You saw something leave " + PossessedGrin.the(host) + " and go into " + PossessedGrin.the(target) + ".");
        Witness.record(target, "Something forced its way inside you. It is still there.");
        player.sendMessage(Text.literal("You leap from " + PossessedGrin.the(host) + " into " + PossessedGrin.the(target) + ".")
                .formatted(Formatting.DARK_RED, Formatting.ITALIC), true);
        return true;
    }

    public static boolean isJumping(UUID player) {
        return JUMPING.contains(player);
    }

    /** How far into the new possession it should start, once; -1 if this possession didn't come from a jump. */
    public static long takeHeadStart(UUID player) {
        Long start = HEAD_START.remove(player);
        return start == null ? -1L : start;
    }

    public static void clear() {
        HEAD_START.clear();
        JUMPING.clear();
    }
}
