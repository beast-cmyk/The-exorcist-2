package com.possessedgrin.client;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.mixin.client.ModelPartAccessor;
import com.possessedgrin.stage.Stages;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * How a possessed body holds itself:
 * <ul>
 *   <li><b>Levitation:</b> hover in place while flying and the body tips over to float flat on its back, bobbing,
 *       like the bed scene. Start moving and it rights itself.</li>
 *   <li><b>Spider-walk:</b> crouch on the ground (humanoids) and the body flips face-up, arms and legs bent
 *       under it, and scuttles head-first.</li>
 *   <li><b>Wall crawl:</b> while climbing a wall, the head bends all the way back to stare up.</li>
 * </ul>
 * Poses blend in and out smoothly. Levitation comes with stage II, the spider-walk and wall crawl with stage III.
 */
public final class DemonPoses {
    public static final int LEVITATE = 0;
    public static final int SPIDER = 1;
    public static final int CLIMB = 2;

    private static final float BLEND_STEP = 0.08F;
    private static final Set<String> LIMB_NAMES = Set.of("right_arm", "left_arm", "arms", "right_leg", "left_leg");

    /** Entity id to how far into each pose it is (0 to 1). Render thread only. */
    private static final Map<Integer, float[]> POSES = new HashMap<>();
    private static final Map<EntityModel<?>, List<ModelPart>> LIMBS = new WeakHashMap<>();

    private DemonPoses() {
    }

    /** Moves and turns the whole body. Called just after the renderer has turned the mob to face its direction. */
    public static void applyBody(LivingEntity entity, EntityModel<?> model, MatrixStack matrices, float tickDelta) {
        PlayerEntity rider = PossessedGrin.possessorOf(entity);
        if (rider == null) {
            POSES.remove(entity.getId());
            return;
        }
        float[] pose = POSES.computeIfAbsent(entity.getId(), id -> new float[3]);
        boolean flying = rider.getAbilities().flying;
        boolean grounded = rider.isOnGround() || entity.isOnGround();
        boolean hovering = flying && rider.getVelocity().horizontalLengthSquared() < 0.004;
        boolean crouching = rider.isSneaking() || entity.isSneaking();

        int stage = Stages.of(entity);
        pose[LEVITATE] = blend(pose[LEVITATE], hovering && stage >= Stages.OPPRESSION);
        pose[SPIDER] = blend(pose[SPIDER], !flying && crouching && grounded && stage >= Stages.POSSESSION && !limbs(model).isEmpty());
        pose[CLIMB] = blend(pose[CLIMB], !flying && !grounded && rider.horizontalCollision && stage >= Stages.POSSESSION);

        float half = entity.getHeight() * 0.5F;
        if (pose[LEVITATE] > 0.001F) {
            float bob = MathHelper.sin((entity.age + tickDelta) * 0.08F) * 0.05F;
            matrices.translate(0.0, half + (0.35F + bob) * pose[LEVITATE], 0.0);
            matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(90.0F * pose[LEVITATE]));
            matrices.translate(0.0, -half, 0.0);
        }
        if (pose[SPIDER] > 0.001F) {
            float lowered = MathHelper.lerp(pose[SPIDER], half, Math.min(0.9F, entity.getHeight() * 0.45F));
            matrices.translate(0.0, lowered, 0.0);
            // Face-up, head-first: roll over, then tip forward
            matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(180.0F * pose[SPIDER]));
            matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(-90.0F * pose[SPIDER]));
            matrices.translate(0.0, -half, 0.0);
        }
    }

    /** How far into each pose this mob is right now, or null if it isn't possessed. */
    public static float[] poseOf(LivingEntity entity) {
        return POSES.get(entity.getId());
    }

    /** The model's arms and legs, for bending them under the body in the spider-walk. */
    public static List<ModelPart> limbs(EntityModel<?> model) {
        return LIMBS.computeIfAbsent(model, DemonPoses::findLimbs);
    }

    private static List<ModelPart> findLimbs(EntityModel<?> model) {
        List<ModelPart> limbs = new ArrayList<>();
        if (model instanceof BipedEntityModel<?> biped) {
            limbs.add(biped.rightArm);
            limbs.add(biped.leftArm);
            limbs.add(biped.rightLeg);
            limbs.add(biped.leftLeg);
        } else if (model instanceof SinglePartEntityModel<?> single) {
            collectLimbs(single.getPart(), limbs);
        }
        return limbs;
    }

    private static void collectLimbs(ModelPart part, List<ModelPart> limbs) {
        for (Map.Entry<String, ModelPart> child : ((ModelPartAccessor) (Object) part).possessedgrin$getChildren().entrySet()) {
            if (LIMB_NAMES.contains(child.getKey())) {
                limbs.add(child.getValue());
            } else {
                collectLimbs(child.getValue(), limbs);
            }
        }
    }

    private static float blend(float current, boolean active) {
        return active ? Math.min(1.0F, current + BLEND_STEP) : Math.max(0.0F, current - BLEND_STEP);
    }
}
