package com.possessedgrin.mixin.client;

import com.possessedgrin.client.DemonPoses;
import com.possessedgrin.client.FaceDecals;
import com.possessedgrin.client.HeadTurns;
import com.possessedgrin.client.PossessedFaceFeatureRenderer;
import com.possessedgrin.client.PossessedMotion;
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
 *       back on walls, jerking marionette limbs, and limbs bent under the body in the spider-walk. All of it is
 *       undone once the frame is drawn.</li>
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
        List<ModelPart> limbs = DemonPoses.limbs(this.model);
        boolean biped = this.model instanceof BipedEntityModel<?> && limbs.size() == 4;
        for (int i = 0; i < limbs.size(); i++) {
            float[] jerk = PossessedMotion.limb(entity, i, limbs.size(), biped, tickDelta);
            float pitch = limbPitch + (jerk == null ? 0.0F : jerk[0]);
            float roll = jerk == null ? 0.0F : jerk[1];
            if (pitch != 0.0F || roll != 0.0F) {
                this.possessedgrin$tweak(limbs.get(i), 0.0F, pitch, roll);
            }
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
