package com.possessedgrin.mixin.client;

import com.possessedgrin.client.DragonFace;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Records where the dragon's head is drawn, so its possessed face can be stuck on afterwards. */
@Mixin(targets = "net.minecraft.client.render.entity.EnderDragonEntityRenderer$DragonEntityModel")
public abstract class DragonEntityModelMixin {
    @Shadow
    @Final
    private ModelPart head;

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelPart;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"),
            require = 0
    )
    private void possessedgrin$captureHead(ModelPart part, MatrixStack matrices, VertexConsumer vertices,
                                           int light, int overlay, float red, float green, float blue, float alpha) {
        if (part == this.head) {
            DragonFace.captureHead(part, matrices);
        }
        part.render(matrices, vertices, light, overlay, red, green, blue, alpha);
    }
}
