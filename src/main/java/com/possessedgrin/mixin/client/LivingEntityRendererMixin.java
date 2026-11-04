package com.possessedgrin.mixin.client;

import com.possessedgrin.client.DemonPoses;
import com.possessedgrin.client.FaceDecals;
import com.possessedgrin.client.HeadTurns;
import com.possessedgrin.client.PossessedFaceFeatureRenderer;
import com.possessedgrin.client.PossessedLook;
import com.possessedgrin.client.PossessedMotion;
import com.possessedgrin.client.PossessedWeltsFeatureRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Every living-mob renderer gets:
 * <ul>
 *   <li>the possessed layer (skin, eyes, grin, blood), added before the subclass layers so it sits on the skin;</li>
 *   <li>the demon's body poses (levitating flat, spider-walk) and its possessed movement (hunch, heavy breathing,
 *       sway, convulsions) right after the mob is turned to face its way;</li>
 *   <li>after the model has posed itself for the frame: the head turn, twitches, the tilted head, the head bent
 *       back on walls, the head snapping round at whoever stares, jerking marionette limbs, limbs bent under the
 *       body in the spider-walk, the head stretched wide as a stage takes hold, and no nose on a skull. All of it
 *       is undone once the frame is drawn.</li>
 * </ul>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> implements FeatureRendererContext<T, M> {
    private static final String RENDER =
            "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";
    private static final float HEAD_BENT_BACK = -1.4F;            // radians, about 80 degrees
    private static final float LIMBS_UNDER = (float) (Math.PI / 2.0);

    @Shadow
    @Final
    protected List<FeatureRenderer<T, M>> features;

    @Shadow
    protected M model;

    /** Parts nudged this frame, with how much, so they can be put back: part, then yaw, pitch, roll. */
    @Unique
    private final List<ModelPart> possessedgrin$tweakedParts = new ArrayList<>();
    @Unique
    private final List<float[]> possessedgrin$tweaks = new ArrayList<>();
    @Unique
    private boolean possessedgrin$lateLayerAdded;
    /** Head parts stretched wide this frame, with their original x and y scale. */
    @Unique
    private final List<ModelPart> possessedgrin$scaledParts = new ArrayList<>();
    @Unique
    private final List<float[]> possessedgrin$scales = new ArrayList<>();
    /** Parts hidden this frame (a skull has no nose). */
    @Unique
    private final List<ModelPart> possessedgrin$hiddenParts = new ArrayList<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void possessedgrin$addFaceLayer(EntityRendererFactory.Context ctx, M model, float shadowRadius, CallbackInfo ci) {
        this.features.add(new PossessedFaceFeatureRenderer<>(this));
    }

    @Inject(method = "setupTransforms", at = @At("TAIL"), require = 0)
    private void possessedgrin$demonPose(T entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, CallbackInfo ci) {
        DemonPoses.applyBody(entity, this.model, matrices, tickDelta);
        PossessedMotion.applyBody(entity, matrices, tickDelta);
    }

    @Inject(
            method = RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V", shift = At.Shift.AFTER),
            require = 0
    )
    private void possessedgrin$poseParts(T entity, float yaw, float tickDelta, MatrixStack matrices,
                                         VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        float turn = HeadTurns.yawOffset(entity, tickDelta);
        float[] pose = DemonPoses.poseOf(entity);
        float[] motion = PossessedMotion.head(entity, tickDelta);
        float headYaw = turn + (motion == null ? 0.0F : motion[0]);
        float headPitch = (pose == null ? 0.0F : HEAD_BENT_BACK * pose[DemonPoses.CLIMB]) + (motion == null ? 0.0F : motion[1]);
        float headRoll = motion == null ? 0.0F : motion[2];
        float limbPitch = pose == null ? 0.0F : LIMBS_UNDER * pose[DemonPoses.SPIDER];

        if (headYaw != 0.0F || headPitch != 0.0F || headRoll != 0.0F) {
            for (ModelPart head : FaceDecals.headParts(this.model)) {
                this.possessedgrin$tweak(head, headYaw, headPitch, headRoll);
            }
        }
        // A new stage takes hold: the head and its grin stretch too wide for a moment
        float widen = PossessedMotion.widen(entity, tickDelta);
        if (widen > 0.0F) {
            for (ModelPart head : FaceDecals.headParts(this.model)) {
                this.possessedgrin$scaledParts.add(head);
                this.possessedgrin$scales.add(new float[]{head.xScale, head.yScale});
                head.xScale *= 1.0F + 0.22F * widen;
                head.yScale *= 1.0F - 0.06F * widen;
            }
        }
        // Rotted to the bone: the skull has no nose to stick out of it
        if (PossessedLook.decay(entity) >= 2) {
            for (ModelPart head : FaceDecals.headParts(this.model)) {
                ModelPart nose = ((ModelPartAccessor) (Object) head).possessedgrin$getChildren().get("nose");
                if (nose != null && nose.visible) {
                    nose.visible = false;
                    this.possessedgrin$hiddenParts.add(nose);
                }
            }
        }

        List<ModelPart> limbs = DemonPoses.limbs(this.model);
        boolean biped = this.model instanceof BipedEntityModel<?> && limbs.size() == 4;
        for (int i = 0; i < limbs.size(); i++) {
            float[] jerk = PossessedMotion.limb(entity, i, limbs.size(), biped, tickDelta);
            float[] posed = DemonPoses.limbPose(entity, limbs.get(i), i, biped, tickDelta);
            float pitch = limbPitch + (jerk == null ? 0.0F : jerk[0]) + (posed == null ? 0.0F : posed[0]);
            float roll = (jerk == null ? 0.0F : jerk[1]) + (posed == null ? 0.0F : posed[1]);
            if (pitch != 0.0F || roll != 0.0F) {
                this.possessedgrin$tweak(limbs.get(i), 0.0F, pitch, roll);
            }
        }
    }

    /** The welts go on last, after every layer the mob's own renderer added (clothing, robes...). */
    @Inject(method = RENDER, at = @At("HEAD"))
    private void possessedgrin$addLateLayer(T entity, float yaw, float tickDelta, MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!this.possessedgrin$lateLayerAdded) {
            this.possessedgrin$lateLayerAdded = true;
            this.features.add(new PossessedWeltsFeatureRenderer<>(this));
        }
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void possessedgrin$undoTweaks(T entity, float yaw, float tickDelta, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        for (int i = 0; i < this.possessedgrin$tweakedParts.size(); i++) {
            ModelPart part = this.possessedgrin$tweakedParts.get(i);
            float[] tweak = this.possessedgrin$tweaks.get(i);
            part.yaw -= tweak[0];
            part.pitch -= tweak[1];
            part.roll -= tweak[2];
        }
        this.possessedgrin$tweakedParts.clear();
        this.possessedgrin$tweaks.clear();
        for (int i = 0; i < this.possessedgrin$scaledParts.size(); i++) {
            ModelPart part = this.possessedgrin$scaledParts.get(i);
            float[] scale = this.possessedgrin$scales.get(i);
            part.xScale = scale[0];
            part.yScale = scale[1];
        }
        this.possessedgrin$scaledParts.clear();
        this.possessedgrin$scales.clear();
        for (ModelPart part : this.possessedgrin$hiddenParts) {
            part.visible = true;
        }
        this.possessedgrin$hiddenParts.clear();
    }

    @Unique
    private void possessedgrin$tweak(ModelPart part, float yaw, float pitch, float roll) {
        part.yaw += yaw;
        part.pitch += pitch;
        part.roll += roll;
        this.possessedgrin$tweakedParts.add(part);
        this.possessedgrin$tweaks.add(new float[]{yaw, pitch, roll});
    }
}
