package com.possessedgrin.mixin;

import com.possessedgrin.PossessedPunch;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives villagers (and other mobs in the punch tag) an attack-damage stat, so Requiem lets them attack. */
@Mixin(DefaultAttributeRegistry.class)
public abstract class DefaultAttributeRegistryMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private static void possessedgrin$addAttackStat(EntityType<? extends LivingEntity> type, CallbackInfoReturnable<DefaultAttributeContainer> cir) {
        DefaultAttributeContainer patched = PossessedPunch.withAttackStat(type, cir.getReturnValue());
        if (patched != null) {
            cir.setReturnValue(patched);
        }
    }
}
