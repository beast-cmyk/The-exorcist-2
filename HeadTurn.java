package com.possessedgrin.power;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.voice.Witness;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

/**
 * The head turn: the possessed mob's head twists all the way around to face behind it, with a sickening crack,
 * holds there, then twists back. The animation plays on every nearby player's screen (see the client side).
 *
 * <p>Anyone who sees it is struck with terror: everything within 10 blocks is slowed and weakened for 5 seconds,
 * mobs forget whoever they were fighting and run from you, and villagers panic.
 */
public final class HeadTurn {
    private static final double TERROR_RADIUS = 10.0;
    private static final int TERROR_TICKS = 100;

    private HeadTurn() {
    }

    static boolean use(ServerPlayerEntity player, MobEntity host) {
        Set<ServerPlayerEntity> viewers = new HashSet<>(PlayerLookup.tracking(host));
        viewers.add(player);
        for (ServerPlayerEntity viewer : viewers) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(host.getId());
            ServerPlayNetworking.send(viewer, PossessedPowers.HEAD_TURN, buf);
        }
        PossessedPowers.sound(host, SoundEvents.BLOCK_BONE_BLOCK_BREAK, 1.5F, 0.5F);
        PossessedPowers.sound(host, SoundEvents.ENTITY_SKELETON_HURT, 0.8F, 0.4F);
        terrify((ServerWorld) host.world, player, host);
        Witness.seen(host, null, "You saw " + PossessedGrin.the(host) + "'s head twist all the way around, with a crack.");
        return true;
    }

    private static void terrify(ServerWorld world, ServerPlayerEntity player, MobEntity host) {
        for (LivingEntity witness : world.getEntitiesByClass(LivingEntity.class, host.getBoundingBox().expand(TERROR_RADIUS),
                e -> e != host && e != player && e.isAlive() && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())))) {
            witness.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, TERROR_TICKS, 1), host);
            witness.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, TERROR_TICKS, 0), host);
            world.spawnParticles(ParticleTypes.SMOKE, witness.getX(), witness.getEyeY() + 0.3, witness.getZ(), 4, 0.2, 0.1, 0.2, 0.01);

            if (witness instanceof MobEntity mob) {
                mob.setTarget(null);
                if (mob instanceof VillagerEntity villager) {
                    // Villagers panic when they remember being attacked
                    villager.getBrain().remember(MemoryModuleType.HURT_BY, DamageSource.mob(host));
                    villager.getBrain().remember(MemoryModuleType.HURT_BY_ENTITY, host);
                }
                if (mob instanceof PathAwareEntity walker) {
                    Vec3d away = walker.getPos().subtract(host.getPos()).multiply(1.0, 0.0, 1.0);
                    away = away.lengthSquared() < 1.0E-4 ? new Vec3d(1.0, 0.0, 0.0) : away.normalize();
                    Vec3d refuge = walker.getPos().add(away.multiply(12.0));
                    walker.getNavigation().startMovingTo(refuge.x, refuge.y, refuge.z, 1.4);
                }
            }
        }
    }
}
