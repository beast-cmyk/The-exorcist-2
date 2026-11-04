package com.possessedgrin.client;

import com.possessedgrin.stage.Stages;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * How much of the demon shows on a possessed body this frame:
 * <ul>
 *   <li><b>Stage I:</b> only the eyes, and only in flickers, like something peering out. They flicker more often
 *       as the stage goes on.</li>
 *   <li><b>Stage II:</b> the eyes stay lit; the grin, the fangs and the sickly skin come through.</li>
 *   <li><b>Stage III:</b> everything: blood, bruises, a corpse's pallor, and eyes that pulse.</li>
 *   <li><b>Wasting:</b> half its life drawn, the body turns grey and sunken; at three quarters the skin rots off
 *       in patches; in the last minutes it is mostly bone, ribs showing, with a skull for a face.</li>
 * </ul>
 */
public record PossessedLook(int stage, boolean face, boolean blood, boolean eyes, float glow, int skinLevel, int decay) {
    /** Half its life drawn: grey and sunken. */
    public static final float WASTING = 0.5F;
    /** Three quarters: the skin rots off in patches. */
    public static final float ROTTING = 0.75F;
    /** The last minutes: bone shows everywhere, the face is a skull. */
    public static final float SKELETAL = 0.9F;

    @Nullable
    public static PossessedLook of(Entity entity, float tickDelta) {
        int stage = Stages.of(entity);
        if (stage == Stages.NONE) {
            return null;
        }
        float time = entity.age + tickDelta;
        int decay = decay(entity);
        if (stage == Stages.INFESTATION) {
            float progress = Stages.progress(entity);
            int period = Math.max(14, (int) (70 - 50 * progress));
            int phase = Math.floorMod(entity.age + entity.getId() * 37, period);
            boolean lit = phase < 4 || (progress > 0.6F && Math.floorMod(entity.age * 7 + entity.getId(), 41) < 2);
            return new PossessedLook(stage, false, false, lit, 0.85F, 0, 0);
        }
        if (stage == Stages.OPPRESSION) {
            return new PossessedLook(stage, true, false, true, 0.95F, 1, 0);
        }
        float pulse = 0.8F + 0.2F * MathHelper.sin(time * 0.18F);
        if (decay >= 2) {
            // Pinpoint eyes deep in the sockets, burning steadily
            pulse = 0.9F + 0.1F * MathHelper.sin(time * 0.5F);
        }
        return new PossessedLook(stage, true, true, true, pulse, Stages.drain(entity) >= WASTING ? 3 : 2, decay);
    }

    /** 0 fresh, 1 rotting, 2 skeletal. Only a body possessed long enough to reach stage III decays. */
    public static int decay(Entity entity) {
        if (Stages.of(entity) < Stages.POSSESSION) {
            return 0;
        }
        float drain = Stages.drain(entity);
        return drain >= SKELETAL ? 2 : drain >= ROTTING ? 1 : 0;
    }
}
