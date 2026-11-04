package com.possessedgrin.client;

import com.possessedgrin.mixin.client.ModelPartAccessor;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.IllagerEntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

/**
 * Where the front of a humanoid's body (its belly) is on its texture, read from the model itself, so the welts land
 * in the right place on every humanoid, including modded and resource-packed ones.
 */
public final class BodyFront {
    private BodyFront() {
    }

    /** Is this a model with a person-shaped body (zombies, skeletons, piglins, villagers, illagers, witches)? */
    public static boolean humanoid(EntityModel<?> model) {
        return model instanceof BipedEntityModel<?> || model instanceof VillagerResemblingModel<?> || model instanceof IllagerEntityModel<?>;
    }

    /**
     * The body's front face on the texture, {x, y, width, height} in texture pixels, choosing the box that has the
     * most actual skin painted on it; null if there isn't one.
     *
     * @param opaque given a pixel (x, y), 1 if the texture has colour there, else 0
     */
    @Nullable
    public static int[] find(EntityModel<?> model, int textureWidth, int textureHeight, IntBinaryOperator opaque) {
        ModelPart body = body(model);
        if (body == null) {
            return null;
        }
        // Draw the body once into a recorder: every face comes out with its texture corners and which way it faces
        Recorder recorder = new Recorder();
        body.render(new MatrixStack(), recorder, 0xF000F0, OverlayTexture.DEFAULT_UV);
        int[] best = null;
        int bestCount = 0;
        for (float[] face : recorder.faces) {
            if (face[4] > -0.5F) {
                continue;   // the front of a model faces -Z
            }
            int x = Math.round(face[0] * textureWidth);
            int y = Math.round(face[1] * textureHeight);
            int w = Math.round((face[2] - face[0]) * textureWidth);
            int h = Math.round((face[3] - face[1]) * textureHeight);
            if (w < 4 || h < 4) {
                continue;
            }
            int count = 0;
            for (int py = Math.max(0, y); py < y + h && py < textureHeight; py++) {
                for (int px = Math.max(0, x); px < x + w && px < textureWidth; px++) {
                    count += opaque.applyAsInt(px, py);
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = new int[]{x, y, w, h};
            }
        }
        return best;
    }

    /** Collects faces (four vertices each) as {minU, minV, maxU, maxV, normalZ}. Draws nothing. */
    private static final class Recorder implements VertexConsumer {
        final List<float[]> faces = new ArrayList<>();
        private float minU;
        private float minV;
        private float maxU;
        private float maxV;
        private float normalZ;
        private int corners;

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v,
                           int overlay, int light, float normalX, float normalY, float normalZ) {
            if (this.corners == 0) {
                this.minU = u;
                this.minV = v;
                this.maxU = u;
                this.maxV = v;
                this.normalZ = normalZ;
            } else {
                this.minU = Math.min(this.minU, u);
                this.minV = Math.min(this.minV, v);
                this.maxU = Math.max(this.maxU, u);
                this.maxV = Math.max(this.maxV, v);
            }
            if (++this.corners == 4) {
                this.faces.add(new float[]{this.minU, this.minV, this.maxU, this.maxV, this.normalZ});
                this.corners = 0;
            }
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void next() {
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unfixColor() {
        }
    }

    @Nullable
    private static ModelPart body(EntityModel<?> model) {
        if (model instanceof BipedEntityModel<?> biped) {
            return biped.body;
        }
        if (model instanceof SinglePartEntityModel<?> single) {
            List<ModelPart> found = new ArrayList<>();
            find(single.getPart(), found);
            return found.isEmpty() ? null : found.get(0);
        }
        return null;
    }

    private static void find(ModelPart part, List<ModelPart> found) {
        for (Map.Entry<String, ModelPart> child : ((ModelPartAccessor) (Object) part).possessedgrin$getChildren().entrySet()) {
            if (child.getKey().equals("body")) {
                found.add(child.getValue());
                return;
            }
            find(child.getValue(), found);
        }
    }
}
