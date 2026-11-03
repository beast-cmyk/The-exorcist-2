package com.possessedgrin.power;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Poltergeist fury: every door, trapdoor and fence gate within 8 blocks bangs open or slams shut, and a shockwave
 * throws every creature nearby away from you.
 */
public final class Poltergeist {
    private static final int RADIUS = 8;

    private Poltergeist() {
    }

    static boolean use(ServerPlayerEntity player, MobEntity host) {
        ServerWorld world = (ServerWorld) host.world;
        BlockPos center = host.getBlockPos();

        for (BlockPos pos : BlockPos.iterate(center.add(-RADIUS, -4, -RADIUS), center.add(RADIUS, 4, RADIUS))) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock door) {
                if (state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                    door.setOpen(host, world, state, pos, !state.get(DoorBlock.OPEN));
                }
            } else if ((state.getBlock() instanceof TrapdoorBlock || state.getBlock() instanceof FenceGateBlock)
                    && state.contains(Properties.OPEN)) {
                boolean open = !state.get(Properties.OPEN);
                world.setBlockState(pos.toImmutable(), state.with(Properties.OPEN, open));
                world.playSound(null, pos, open ? SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN : SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE,
                        SoundCategory.BLOCKS, 1.0F, 0.8F);
            }
        }

        for (Entity entity : world.getOtherEntities(host, host.getBoundingBox().expand(RADIUS),
                e -> e != player && !e.isSpectator() && !(e instanceof PlayerEntity p && p.isCreative()))) {
            Vec3d away = entity.getPos().subtract(host.getPos()).multiply(1.0, 0.0, 1.0);
            away = away.lengthSquared() < 1.0E-4 ? new Vec3d(0.0, 0.0, 1.0) : away.normalize();
            entity.addVelocity(away.x * 1.4, 0.45, away.z * 1.4);
            entity.velocityModified = true;
        }

        world.spawnParticles(ParticleTypes.SOUL, host.getX(), host.getBodyY(0.5), host.getZ(), 40, RADIUS / 3.0, 1.0, RADIUS / 3.0, 0.05);
        PossessedPowers.sound(host, SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1.5F, 0.5F);
        PossessedPowers.sound(host, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.6F, 0.6F);
        return true;
    }
}
