package com.possessedgrin.mixin;

import com.possessedgrin.voice.Witness;
import com.possessedgrin.stage.Stages;
import com.possessedgrin.voice.Kinship;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The demon's voice: from stage II every sound a possessed mob makes (growls, hurt cries...) comes out deep and
 * wrong. Also: when your possessed body strikes one of its own kind, the victim doesn't fight back.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    private static final float DEMON_PITCH = 0.6F;

    @Inject(method = "getSoundPitch", at = @At("RETURN"), cancellable = true)
    private void possessedgrin$demonVoice(CallbackInfoReturnable<Float> cir) {
        if (Stages.of((LivingEntity) (Object) this) >= Stages.OPPRESSION) {
            cir.setReturnValue(cir.getReturnValueF() * DEMON_PITCH);
        }
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void possessedgrin$spareKin(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            Kinship.onHurt((LivingEntity) (Object) this, source);
            Witness.onHurt((LivingEntity) (Object) this, source.getAttacker());
        }
    }
}
