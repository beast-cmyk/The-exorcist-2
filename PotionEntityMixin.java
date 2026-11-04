package com.possessedgrin.mixin;

import com.possessedgrin.haunt.Exorcism;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Splash bottles of plain water become holy water against the possessed. */
@Mixin(PotionEntity.class)
public abstract class PotionEntityMixin {
    @Inject(method = "applyWater", at = @At("TAIL"))
    private void possessedgrin$holyWater(CallbackInfo ci) {
        Exorcism.holyWater((PotionEntity) (Object) this);
    }
}
