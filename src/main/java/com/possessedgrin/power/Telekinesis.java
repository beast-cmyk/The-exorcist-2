package com.possessedgrin.power;

import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Telekinesis. Press once: whatever you're looking at (up to 24 blocks away) rises and floats in front of you:
 * a mob, a player, a dropped item, or a block torn right out of the ground (furniture flying across the room).
 * Press again: it's hurled where you look, and anything thrown into a wall gets slammed against it.
 * Lifting blocks follows the mobGriefing gamerule, and never takes unbreakable blocks or chests/containers.
 */
public final class Telekinesis {
    private static final double REACH = 24.0;
    private static final int MAX_HOLD_TICKS = 400;   // drops after 20 seconds
    private static final double THROW_SPEED = 2.2;
    private static final float WALL_SLAM_DAMAGE = 6.0F;

    private static final Map<UUID, Hold> HELD = new HashMap<>();
    private static final Map<Entity, Thrown> THROWN = new HashMap<>();

    private static final class Hold {
        final Entity target;
        final boolean hadNoGravity;
        int ticks;

        Hold(Entity target) {
            this.target = target;
            this.hadNoGravity = target.hasNoGravity();
        }
    }

    private record Thrown(MobEntity thrower, int untilTick) {
    }

    private Telekinesis() {
    }

    static boolean use(ServerPlayerEntity player, MobEntity host) {
        Hold hold = HELD.remove(player.getUuid());
        if (hold != null) {
            hurl(player, host, hold);
            return true;
        }
        Entity target = findEntity(player, host);
        if (target == null) {
            target = tearOutBlock(player, host);
        }
        if (target == null) {
            return false;
        }
        HELD.put(player.getUuid(), new Hold(target));
        target.setNoGravity(true);
        PossessedPowers.sound(host, SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 1.0F, 0.6F);
        return true;
    }

    @Nullable
    private static Entity findEntity(ServerPlayerEntity player, MobEntity host) {
        Vec3d eye = player.getCameraPosVec(1.0F);
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d end = eye.add(look.multiply(REACH));
        Box searchArea = player.getBoundingBox().stretch(look.multiply(REACH)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, searchArea, e -> canLift(e, player, host), REACH * REACH);
        if (hit == null) {
            return null;
        }
        Entity entity = hit.getEntity();
        // Grabbing any part of the Ender Dragon grabs the whole dragon
        return entity instanceof EnderDragonPart part ? part.owner : entity;
    }

    private static boolean canLift(Entity entity, ServerPlayerEntity player, MobEntity host) {
        if (entity == player || entity == host || !entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof EnderDragonPart part && part.owner == host) {
            return false;
        }
        return !(entity instanceof PlayerEntity other && other.isCreative());
    }

    @Nullable
    private static Entity tearOutBlock(ServerPlayerEntity player, MobEntity host) {
        ServerWorld world = (ServerWorld) host.world;
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            return null;
        }
        HitResult hit = player.raycast(REACH, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        float hardness = state.getHardness(world, pos);
        if (state.isAir() || state.hasBlockEntity() || hardness < 0.0F || hardness > 50.0F || !state.getFluidState().isEmpty()) {
            return null;
        }
        FallingBlockEntity block = FallingBlockEntity.spawnFromBlock(world, pos, state);
        block.dropItem = true;
        return block;
    }

    private static void hurl(ServerPlayerEntity player, MobEntity host, Hold hold) {
        Entity target = hold.target;
        target.setNoGravity(hold.hadNoGravity);
        if (!target.isAlive()) {
            return;
        }
        Vec3d look = player.getRotationVec(1.0F);
        target.setVelocity(look.multiply(THROW_SPEED).add(0.0, 0.15, 0.0));
        target.velocityModified = true;
        THROWN.put(target, new Thrown(host, (int) host.world.getTime() + 40));
        PossessedPowers.sound(host, SoundEvents.ITEM_TRIDENT_THROW, 1.2F, 0.5F);
    }

    private static void drop(Hold hold) {
        hold.target.setNoGravity(hold.hadNoGravity);
    }

    static void tick(ServerWorld world) {
        // Carry everything currently held
        Iterator<Map.Entry<UUID, Hold>> held = HELD.entrySet().iterator();
        while (held.hasNext()) {
            Map.Entry<UUID, Hold> entry = held.next();
            Hold hold = entry.getValue();
            if (hold.target.world != world) {
                continue;
            }
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            MobEntity host = player == null ? null : PossessionComponent.getHost(player);
            if (host == null || host.world != world || !hold.target.isAlive() || ++hold.ticks > MAX_HOLD_TICKS) {
                drop(hold);
                held.remove();
                continue;
            }
            double distance = 2.5 + host.getWidth() + hold.target.getWidth() / 2.0;
            Vec3d holdPoint = player.getCameraPosVec(1.0F).add(player.getRotationVec(1.0F).multiply(distance));
            Vec3d center = hold.target.getPos().add(0.0, hold.target.getHeight() / 2.0, 0.0);
            hold.target.setVelocity(holdPoint.subtract(center).multiply(0.35));
            hold.target.velocityModified = true;
            hold.target.fallDistance = 0.0F;
            if (hold.target instanceof FallingBlockEntity block) {
                block.timeFalling = 1;   // keeps the floating block from expiring
            }
            if (world.getTime() % 3 == 0) {
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 2,
                        hold.target.getWidth() / 2.0, hold.target.getHeight() / 2.0, hold.target.getWidth() / 2.0, 0.02);
            }
        }

        // Slam anything thrown into a wall
        long now = world.getTime();
        for (Map.Entry<Entity, Thrown> entry : new ArrayList<>(THROWN.entrySet())) {
            Entity thrown = entry.getKey();
            if (thrown.world != world) {
                continue;
            }
            if (!thrown.isAlive() || now > entry.getValue().untilTick()) {
                THROWN.remove(thrown);
            } else if (thrown.horizontalCollision) {
                if (thrown instanceof LivingEntity living) {
                    living.damage(DamageSource.mob(entry.getValue().thrower()), WALL_SLAM_DAMAGE);
                }
                world.spawnParticles(ParticleTypes.CRIT, thrown.getX(), thrown.getBodyY(0.5), thrown.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
                PossessedPowers.sound(entry.getValue().thrower(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
                THROWN.remove(thrown);
            }
        }
    }
}
