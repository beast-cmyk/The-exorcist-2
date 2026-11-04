package com.possessedgrin.client;

import com.possessedgrin.stage.Stages;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/**
 * From stage II, two words rise in welts across a possessed humanoid's belly; and once the body has been fed on
 * long enough, it rots, then turns to bone. Added as the last layer of the renderer, so it all shows through
 * clothing and robes.
 */
public class PossessedWeltsFeatureRenderer<T extends LivingEntity, M extends EntityModel<T>> extends FeatureRenderer<T, M> {
    public PossessedWeltsFeatureRenderer(FeatureRendererContext<T, M> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T entity,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        if (entity.isInvisible() || Stages.of(entity) < Stages.OPPRESSION) {
            return;
        }
        int overlay = LivingEntityRenderer.getOverlay(entity, 0.0F);
        int decay = PossessedLook.decay(entity);
        if (decay > 0) {
            // The body rotting through its clothes
            Identifier rot = PossessedSkins.decayFor(this.getTexture(entity), decay, this.getContextModel());
            if (rot != null) {
                VertexConsumer layer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(rot));
                this.getContextModel().render(matrices, layer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
        if (decay >= 2) {
            return;     // the skin with the words on it has rotted away
        }
        Identifier welts = PossessedSkins.weltsFor(this.getTexture(entity), this.getContextModel());
        if (welts != null) {
            VertexConsumer layer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(welts));
            this.getContextModel().render(matrices, layer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
