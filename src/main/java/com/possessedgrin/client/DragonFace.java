package com.possessedgrin.client;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.mixin.client.ModelPartAccessor;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import org.jetbrains.annotations.Nullable;

/**
 * The Ender Dragon's possessed face. Its renderer isn't a normal mob renderer, so the head's on-screen position
 * is captured while the dragon model draws its head, then two decals are added: slanted white eyes on the front
 * of the skull, and a toothy grin across the tip of the snout.
 */
public final class DragonFace {
    private static final Identifier EYES_FACE = PossessedGrin.id("textures/entity/possessed/dragon_eyes.png");
    private static final Identifier EYES_GLOW = PossessedGrin.id("textures/entity/possessed/dragon_eyes_glow.png");
    private static final Identifier GRIN = PossessedGrin.id("textures/entity/possessed/dragon_grin.png");
    private static final Identifier BLOOD = PossessedGrin.id("textures/entity/possessed/dragon_blood.png");
    private static final float OFFSET = 0.3F;

    @Nullable private static ModelPart head;
    @Nullable private static Matrix4f position;
    @Nullable private static Matrix3f normal;

    private DragonFace() {
    }

    public static void reset() {
        head = null;
        position = null;
        normal = null;
    }

    /** Called right before the dragon model draws its head, with the matrices it is about to use. */
    public static void captureHead(ModelPart dragonHead, MatrixStack matrices) {
        matrices.push();
        dragonHead.rotate(matrices);
        head = dragonHead;
        position = matrices.peek().getPositionMatrix().copy();
        normal = matrices.peek().getNormalMatrix().copy();
        matrices.pop();
    }

    public static void draw(VertexConsumerProvider vertexConsumers, int light, PossessedLook look) {
        if (head == null || position == null || normal == null) {
            return;
        }
        ModelPart.Cuboid skull = FaceDecals.biggest(head);
        ModelPart.Cuboid snout = null;
        for (ModelPart.Cuboid cuboid : ((ModelPartAccessor) (Object) head).possessedgrin$getCuboids()) {
            if (cuboid != skull && (snout == null || cuboid.minZ < snout.minZ)) {
                snout = cuboid;
            }
        }
        if (skull == null) {
            return;
        }
        // Eyes on the upper part of the skull's front, above the snout
        float eyesBottom = skull.minY + (skull.maxY - skull.minY) * 0.45F;
        int overlay = OverlayTexture.DEFAULT_UV;

        if (look.face()) {
            VertexConsumer eyes = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(EYES_FACE));
            FaceDecals.quad(position, normal, eyes, skull.minX, skull.minY, skull.maxX, eyesBottom, skull.minZ - OFFSET, light, overlay);
            if (snout != null) {
                VertexConsumer grin = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(GRIN));
                FaceDecals.quad(position, normal, grin, snout.minX, snout.minY, snout.maxX, snout.maxY, snout.minZ - OFFSET, light, overlay);
            }
        }
        if (look.blood()) {
            VertexConsumer blood = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(BLOOD));
            FaceDecals.quad(position, normal, blood, skull.minX, skull.minY, skull.maxX, eyesBottom, skull.minZ - OFFSET, light, overlay);
        }
        if (look.eyes()) {
            VertexConsumer glow = vertexConsumers.getBuffer(RenderLayer.getEyes(EYES_GLOW));
            FaceDecals.quad(position, normal, glow, skull.minX, skull.minY, skull.maxX, eyesBottom, skull.minZ - OFFSET,
                    FaceDecals.FULL_BRIGHT, overlay, look.glow());
        }
    }
}
