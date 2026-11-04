package com.possessedgrin.mixin.requiem;

import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The Ender Dragon is made of separate hitbox parts, so your crosshair lands on a head/body/wing part rather than
 * the dragon itself, and Requiem can't possess a part. Point Requiem at the dragon instead.
 */
@Pseudo
@Mixin(targets = "ladysnake.requiem.core.ability.PlayerAbilityController", remap = false)
public abstract class PlayerAbilityControllerMixin {
    @ModifyVariable(method = "tryTarget", at = @At("HEAD"), argsOnly = true, require = 0)
    private Entity possessedgrin$targetWholeDragon(Entity target) {
        return target instanceof EnderDragonPart part ? part.owner : target;
    }
}
