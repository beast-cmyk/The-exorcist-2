package com.possessedgrin.mixin.client;

import com.possessedgrin.client.DragonFace;
import com.possessedgrin.client.PossessedLook;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the possessed face on the Ender Dragon (its renderer isn't a regular mob renderer). */
@Mixin(EnderDragonEntityRenderer.class)
public abstract class EnderDragonEntityRendererMixin {
    private static final String RENDER =
            "render(Lnet/minecraft/entity/boss/dragon/EnderDragonEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void possessedgrin$forgetLastHead(EnderDragonEntity dragon, float yaw, float tickDelta, MatrixStack matrices,
                                              VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        DragonFace.reset();
    }

    @Inject(method = RENDER, at = @At("TAIL"))
    private void possessedgrin$drawFace(EnderDragonEntity dragon, float yaw, float tickDelta, MatrixStack matrices,
                                        VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        PossessedLook look = PossessedLook.of(dragon, tickDelta);
        if (look != null && !dragon.isInvisible()) {
            DragonFace.draw(vertexConsumers, light, look);
        }
    }
}
