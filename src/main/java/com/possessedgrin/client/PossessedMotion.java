package com.possessedgrin.client;

import com.possessedgrin.stage.Stages;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3f;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * How a possessed body moves, so it reads as possessed rather than reskinned. Everything gets worse with each
 * stage:
 * <ul>
 *   <li><b>Twitches:</b> the head snaps to a new angle, holds it a beat too long, and snaps back. No easing: it
 *       should look wrong. Rare in stage I, constant by stage III.</li>
 *   <li><b>Convulsions:</b> the whole body seizes and shakes when a new stage takes hold, and in random fits
 *       afterwards.</li>
 *   <li><b>Stage II:</b> a hunched stoop and heavy, visible breathing; now and then the head cranes all the way
 *       back to stare straight up.</li>
 *   <li><b>Stage III:</b> the head hangs tilted to one side, the body sways, the walk turns into a stiff marionette
 *       lurch, and single limbs jerk on their own as if pulled on strings.</li>
 *   <li><b>Taunts</b> (any stage): it throws its head back and shakes with laughter, cocks its head and bares its
 *       teeth to hiss, or drops its head and hunches to growl.</li>
 * </ul>
 * Render thread only.
 */
public final class PossessedMotion {
    private static final class State {
        final Random random;
        int lastStage;
        float twitchStart = -1000.0F;
        float twitchLength;
        float twitchYaw;
        float twitchPitch;
        float twitchRoll;
        float nextTwitch;
        float lookUpStart = -1000.0F;
        float lookUpLength;
        float nextLookUp;
        float convulseStart = -1000.0F;
        float convulseLength;
        float nextFit;
        float limbStart = -1000.0F;
        float limbLength;
        int limbIndex;
        float limbPitch;
        float limbRoll;
        float nextLimb;
        float tauntStart = -1000.0F;
        int tauntKind;

        State(int id, float now) {
            this.random = new Random(id * 31L + 7L);
            this.nextTwitch = now + 10.0F;
            this.nextLookUp = now + 200.0F + this.random.nextInt(200);
            this.nextFit = now + 300.0F + this.random.nextInt(300);
            this.nextLimb = now + 20.0F;
        }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<Integer, Integer> PENDING_TAUNTS = new HashMap<>();
    private static final float TAUNT_LENGTH = 34.0F;

    private PossessedMotion() {
    }

    public static void clear() {
        STATES.clear();
        PENDING_TAUNTS.clear();
    }

    /** The server says this body just taunted (0 laugh, 1 hiss, 2 growl). Starts on its next frame. */
    public static void taunt(int entityId, int kind) {
        PENDING_TAUNTS.put(entityId, kind);
    }

    private static float taunting(State state, float now) {
        return envelope(now - state.tauntStart, TAUNT_LENGTH);
    }

    @Nullable
    private static State state(LivingEntity entity, float now) {
        int stage = Stages.of(entity);
        if (stage == Stages.NONE) {
            STATES.remove(entity.getId());
            return null;
        }
        State state = STATES.computeIfAbsent(entity.getId(), id -> new State(id, now));
        if (state.lastStage > 0 && stage > state.lastStage) {
            convulse(state, now, stage >= Stages.POSSESSION ? 55.0F : 40.0F);      // the new stage takes hold
        }
        state.lastStage = stage;
        Integer taunt = PENDING_TAUNTS.remove(entity.getId());
        if (taunt != null) {
            state.tauntStart = now;
            state.tauntKind = taunt;
        }
        return state;
    }

    private static void convulse(State state, float now, float length) {
        state.convulseStart = now;
        state.convulseLength = length;
    }

    /** 0 to 1: how hard the body is convulsing right now. */
    private static float convulsion(State state, float now) {
        if (state.lastStage >= Stages.OPPRESSION && now >= state.nextFit) {
            convulse(state, now, state.lastStage >= Stages.POSSESSION ? 20.0F + state.random.nextInt(15) : 12.0F);
            state.nextFit = now + (state.lastStage >= Stages.POSSESSION ? 160 + state.random.nextInt(240) : 320 + state.random.nextInt(320));
        }
        float into = now - state.convulseStart;
        return into >= 0.0F && into < state.convulseLength ? 1.0F - into / state.convulseLength : 0.0F;
    }

    /** Eases in and out over a few ticks, for slow, deliberate movements. */
    private static float envelope(float into, float length) {
        if (into < 0.0F || into > length) {
            return 0.0F;
        }
        float k = MathHelper.clamp(Math.min(into, length - into) / 8.0F, 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    private static float jitter(Random random, float amount) {
        return (random.nextFloat() - 0.5F) * 2.0F * amount;
    }

    // --- the whole body (called from the renderer's setupTransforms) ------------------------------------------

    public static void applyBody(LivingEntity entity, MatrixStack matrices, float tickDelta) {
        float now = entity.age + tickDelta;
        State state = state(entity, now);
        if (state == null) {
            return;
        }
        float pitch = 0.0F;      // degrees; negative leans forward
        float roll = 0.0F;
        float lift = 0.0F;
        if (state.lastStage >= Stages.OPPRESSION) {
            boolean full = state.lastStage >= Stages.POSSESSION;
            float hunch = full ? 12.0F : 6.0F;
            float breath = MathHelper.sin(now * (full ? 0.16F : 0.12F)) * (full ? 2.6F : 1.4F);
            pitch -= hunch + breath;
            roll += full ? MathHelper.sin(now * 0.05F) * 4.0F : 0.0F;
        }
        float taunt = taunting(state, now);
        if (taunt > 0.0F) {
            if (state.tauntKind == 0) {
                // laughing: rocks back, shoulders heaving
                pitch += 8.0F * taunt;
                lift += Math.abs(MathHelper.sin(now * 1.7F)) * 0.035F * taunt;
            } else if (state.tauntKind == 2) {
                // growling: hunches down, trembling
                pitch -= 9.0F * taunt;
                roll += jitter(state.random, 1.5F) * taunt;
            }
        }
        if (pitch != 0.0F || roll != 0.0F || lift != 0.0F) {
            float pivot = entity.getHeight() * 0.25F;
            matrices.translate(0.0, pivot + lift, 0.0);
            matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(pitch));
            matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(roll));
            matrices.translate(0.0, -pivot, 0.0);
        }
        float c = convulsion(state, now);
        if (c > 0.0F) {
            shake(entity, matrices, state, c);
        }
    }

    private static void shake(LivingEntity entity, MatrixStack matrices, State state, float c) {
        float half = entity.getHeight() * 0.5F;
        matrices.translate(jitter(state.random, 0.04F * c), half + Math.abs(jitter(state.random, 0.08F * c)), jitter(state.random, 0.04F * c));
        matrices.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(jitter(state.random, 14.0F * c)));
        matrices.multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(jitter(state.random, 16.0F * c)));
        matrices.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(jitter(state.random, 10.0F * c)));
        matrices.translate(0.0, -half, 0.0);
    }

    // --- the head (called after the model has posed itself) ---------------------------------------------------

    /** Extra yaw, pitch and roll for the head this frame, or null when there is none. */
    @Nullable
    public static float[] head(LivingEntity entity, float tickDelta) {
        float now = entity.age + tickDelta;
        State state = state(entity, now);
        if (state == null) {
            return null;
        }
        int stage = state.lastStage;
        float yaw = 0.0F;
        float pitch = 0.0F;
        float roll = 0.0F;

        // Twitches: snap, hold, snap back
        if (now >= state.nextTwitch) {
            float amount = stage >= Stages.POSSESSION ? 0.9F : stage == Stages.OPPRESSION ? 0.6F : 0.35F;
            state.twitchStart = now;
            state.twitchLength = 2 + state.random.nextInt(stage >= Stages.POSSESSION ? 6 : 4);
            state.twitchYaw = jitter(state.random, amount);
            state.twitchPitch = jitter(state.random, amount * 0.5F);
            state.twitchRoll = jitter(state.random, amount * 0.8F);
            state.nextTwitch = now + (stage >= Stages.POSSESSION ? 10 + state.random.nextInt(40)
                    : stage == Stages.OPPRESSION ? 20 + state.random.nextInt(60) : 50 + state.random.nextInt(110));
        }
        if (now - state.twitchStart < state.twitchLength) {
            yaw += state.twitchYaw;
            pitch += state.twitchPitch;
            roll += state.twitchRoll;
        }

        // Stage III: the head hangs to one side and glares from under the brow
        if (stage >= Stages.POSSESSION) {
            roll += 0.38F + MathHelper.sin(now * 0.045F) * 0.1F;
            pitch += 0.12F;
        }

        // From stage II: now and then the head cranes all the way back to stare at the sky
        if (stage >= Stages.OPPRESSION) {
            if (now >= state.nextLookUp) {
                state.lookUpStart = now;
                state.lookUpLength = 30 + state.random.nextInt(30);
                state.nextLookUp = now + 240 + state.random.nextInt(360);
            }
            float k = envelope(now - state.lookUpStart, state.lookUpLength);
            if (k > 0.0F) {
                pitch -= 1.25F * k;
                roll *= 1.0F - k;
            }
        }

        float taunt = taunting(state, now);
        if (taunt > 0.0F) {
            switch (state.tauntKind) {
                case 0 -> {        // head thrown back, cackling
                    pitch += (-0.6F + MathHelper.sin(now * 1.7F) * 0.14F) * taunt;
                    roll += MathHelper.sin(now * 0.9F) * 0.12F * taunt;
                }
                case 1 -> {        // head cocked, teeth bared
                    pitch += 0.2F * taunt;
                    roll += 0.45F * taunt;
                    yaw += MathHelper.sin(now * 0.5F) * 0.1F * taunt;
                }
                default -> {       // head down, glaring, trembling
                    pitch += 0.4F * taunt + jitter(state.random, 0.05F) * taunt;
                }
            }
        }

        float c = convulsion(state, now);
        if (c > 0.0F) {
            yaw += jitter(state.random, 0.7F * c);
            pitch += jitter(state.random, 0.5F * c);
            roll += jitter(state.random, 0.6F * c);
        }
        return yaw == 0.0F && pitch == 0.0F && roll == 0.0F ? null : new float[]{yaw, pitch, roll};
    }

    // --- arms and legs ----------------------------------------------------------------------------------------

    /**
     * Extra pitch and roll for one limb this frame, or null. {@code biped}: limbs are right arm, left arm, right
     * leg, left leg, so the walk can be exaggerated in step.
     */
    @Nullable
    public static float[] limb(LivingEntity entity, int index, int count, boolean biped, float tickDelta) {
        State state = STATES.get(entity.getId());
        if (state == null || state.lastStage < Stages.OPPRESSION) {
            return null;
        }
        float now = entity.age + tickDelta;
        float pitch = 0.0F;
        float roll = 0.0F;
        boolean full = state.lastStage >= Stages.POSSESSION;

        if (full) {
            // Marionette strings: one limb at a time jerks out of place
            if (index == 0 && now >= state.nextLimb && count > 0) {
                state.limbStart = now;
                state.limbLength = 3 + state.random.nextInt(4);
                state.limbIndex = state.random.nextInt(count);
                state.limbPitch = jitter(state.random, 1.0F);
                state.limbRoll = jitter(state.random, 0.5F);
                state.nextLimb = now + 15 + state.random.nextInt(35);
            }
            if (index == state.limbIndex && now - state.limbStart < state.limbLength) {
                pitch += state.limbPitch;
                roll += state.limbRoll;
            }
            // A stiff, lurching walk
            if (biped) {
                float limbAngle = entity.limbAngle - entity.limbDistance * (1.0F - tickDelta);
                float limbDistance = Math.min(1.0F, MathHelper.lerp(tickDelta, entity.lastLimbDistance, entity.limbDistance));
                float phase = index == 0 || index == 3 ? (float) Math.PI : 0.0F;
                pitch += MathHelper.cos(limbAngle * 0.6662F + phase) * 0.45F * limbDistance;
            }
        }

        float c = convulsion(state, now);
        if (c > 0.0F) {
            pitch += jitter(state.random, 0.9F * c);
            roll += jitter(state.random, 0.45F * c);
        }
        return pitch == 0.0F && roll == 0.0F ? null : new float[]{pitch, roll};
    }
}
