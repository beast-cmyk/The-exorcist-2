package com.possessedgrin.mixin;

import com.possessedgrin.PossessedPunch;
import com.possessedgrin.voice.Kinship;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the possessed-punch damage for the duration of a possessed mob's attack, and stops mobs from turning on a
 * possessed body of their own kind.
 */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @Inject(method = "tryAttack", at = @At("HEAD"))
    private void possessedgrin$beforeAttack(Entity target, CallbackInfoReturnable<Boolean> cir) {
        PossessedPunch.beforeAttack((MobEntity) (Object) this);
    }

    @Inject(method = "tryAttack", at = @At("RETURN"))
    private void possessedgrin$afterAttack(Entity target, CallbackInfoReturnable<Boolean> cir) {
        PossessedPunch.afterAttack((MobEntity) (Object) this);
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void possessedgrin$neverTargetKin(@Nullable LivingEntity target, CallbackInfo ci) {
        if (target != null && Kinship.spares((MobEntity) (Object) this, target)) {
            ci.cancel();
        }
    }
}
