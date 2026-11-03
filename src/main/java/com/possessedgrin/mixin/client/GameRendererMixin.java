package com.possessedgrin.mixin.client;

import com.possessedgrin.client.CameraShake;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the screen shake, right where the game tilts your view when you get hurt. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "bobViewWhenHurt", at = @At("TAIL"))
    private void possessedgrin$shake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        CameraShake.apply(matrices, tickDelta);
    }
}
