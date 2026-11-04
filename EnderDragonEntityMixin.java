package com.possessedgrin.mixin;

import com.possessedgrin.PossessedGrin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.phase.Phase;
import net.minecraft.entity.boss.dragon.phase.PhaseManager;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * The Ender Dragon flies itself with a "phase" autopilot that Requiem's possession doesn't switch off. While a
 * player possesses it: hold it in its hover phase (wings keep flapping), switch off the autopilot's steering so
 * the player flies it, face it where the player looks, and stop its wings and head from hitting its own rider.
 * Its head still bites and its wings still fling anything else it flies into.
 */
@Mixin(EnderDragonEntity.class)
public abstract class EnderDragonEntityMixin extends MobEntity {
    @Shadow
    public abstract PhaseManager getPhaseManager();

    protected EnderDragonEntityMixin(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void possessedgrin$followRider(CallbackInfo ci) {
        PlayerEntity rider = PossessedGrin.possessorOf(this);
        if (rider == null) {
            return;
        }
        if (!this.world.isClient && this.getPhaseManager().getCurrent().getType() != PhaseType.HOVER) {
            this.getPhaseManager().setPhase(PhaseType.HOVER);
        }
        // The dragon model faces the opposite way to its yaw value, hence the 180.
        float yaw = rider.getYaw() + 180.0F;
        this.setYaw(yaw);
        this.bodyYaw = yaw;
        this.headYaw = yaw;
    }

    @Redirect(
            method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/boss/dragon/phase/Phase;getPathTarget()Lnet/minecraft/util/math/Vec3d;"),
            require = 0
    )
    private Vec3d possessedgrin$noAutopilot(Phase phase) {
        return PossessedGrin.isPossessed(this) ? null : phase.getPathTarget();
    }

    @ModifyVariable(method = "launchLivingEntities", at = @At("HEAD"), argsOnly = true)
    private List<Entity> possessedgrin$dontFlingRider(List<Entity> entities) {
        return this.possessedgrin$withoutRider(entities);
    }

    @ModifyVariable(method = "damageLivingEntities", at = @At("HEAD"), argsOnly = true)
    private List<Entity> possessedgrin$dontBiteRider(List<Entity> entities) {
        return this.possessedgrin$withoutRider(entities);
    }

    private List<Entity> possessedgrin$withoutRider(List<Entity> entities) {
        PlayerEntity rider = PossessedGrin.possessorOf(this);
        if (rider == null || !entities.contains(rider)) {
            return entities;
        }
        List<Entity> others = new ArrayList<>(entities);
        others.remove(rider);
        return others;
    }
}
