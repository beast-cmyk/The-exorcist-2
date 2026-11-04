package com.possessedgrin.power;

import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The room goes cold around the possessed:
 * <ul>
 *   <li>frost drifts off the body;</li>
 *   <li>creatures nearby shiver with frost on them and slow down; players see their breath and frost creeping
 *       over their screen (it never freezes anyone to death);</li>
 *   <li>still water right around you freezes over, like Frost Walker ice that melts back later.</li>
 * </ul>
 */
public final class ColdPresence {
    private static final double RADIUS = 8.0;
    private static final int FREEZE_RADIUS = 3;
    private static final int CHILL_PER_PULSE = 25;

    private ColdPresence() {
    }

    public static void tick(ServerWorld world) {
        if (world.getTime() % 10 != 0) {
            return;
        }
        for (ServerPlayerEntity possessor : world.getPlayers()) {
            MobEntity host = PossessionComponent.getHost(possessor);
            if (host == null || host.isInvisible()) {
                continue;
            }
            world.spawnParticles(ParticleTypes.SNOWFLAKE, host.getX(), host.getBodyY(0.6), host.getZ(),
                    3, host.getWidth() * 0.6, host.getHeight() * 0.4, host.getWidth() * 0.6, 0.01);

            for (LivingEntity other : world.getEntitiesByClass(LivingEntity.class, host.getBoundingBox().expand(RADIUS),
                    e -> e != host && e != possessor && e.isAlive())) {
                if (other instanceof PlayerEntity player && (player.isSpectator() || player.isCreative())) {
                    continue;
                }
                if (other.squaredDistanceTo(host) > RADIUS * RADIUS) {
                    continue;
                }
                // Stop one tick short of the point where freezing starts to hurt
                int maxChill = other.getMinFreezeDamageTicks() - 1;
                other.setFrozenTicks(Math.min(maxChill, other.getFrozenTicks() + CHILL_PER_PULSE));
                if (other instanceof PlayerEntity) {
                    Vec3d breath = other.getEyePos().add(other.getRotationVec(1.0F).multiply(0.4)).add(0.0, -0.15, 0.0);
                    world.spawnParticles(ParticleTypes.CLOUD, breath.x, breath.y, breath.z, 1, 0.02, 0.02, 0.02, 0.005);
                } else {
                    other.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0), host);
                }
            }

            if (!host.isTouchingWater()) {
                freezeWater(world, host.getBlockPos());
            }
        }
    }

    /** Turns still water around you into frosted ice (the kind Frost Walker makes, which melts back on its own). */
    private static void freezeWater(ServerWorld world, BlockPos center) {
        BlockState ice = Blocks.FROSTED_ICE.getDefaultState();
        for (BlockPos pos : BlockPos.iterate(center.add(-FREEZE_RADIUS, -1, -FREEZE_RADIUS), center.add(FREEZE_RADIUS, 0, FREEZE_RADIUS))) {
            if (pos.getSquaredDistance(center) > FREEZE_RADIUS * FREEZE_RADIUS) {
                continue;
            }
            BlockState state = world.getBlockState(pos);
            if (state.isOf(Blocks.WATER) && state.get(FluidBlock.LEVEL) == 0 && world.getBlockState(pos.up()).isAir()) {
                BlockPos frozen = pos.toImmutable();
                world.setBlockState(frozen, ice);
                world.createAndScheduleBlockTick(frozen, Blocks.FROSTED_ICE, MathHelper.nextInt(world.getRandom(), 60, 120));
            }
        }
    }
}
