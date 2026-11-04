package com.possessedgrin.mixin;

import com.possessedgrin.PossessedGrin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A possessed Warden still blinds players around it with Darkness, just not the player controlling it. */
@Mixin(WardenEntity.class)
public abstract class WardenEntityMixin {
    @Inject(method = "addDarknessToClosePlayers", at = @At("TAIL"))
    private static void possessedgrin$spareRider(ServerWorld world, Vec3d pos, Entity warden, int range, CallbackInfo ci) {
        PlayerEntity rider = PossessedGrin.possessorOf(warden);
        if (rider != null) {
            rider.removeStatusEffect(StatusEffects.DARKNESS);
        }
    }
}
