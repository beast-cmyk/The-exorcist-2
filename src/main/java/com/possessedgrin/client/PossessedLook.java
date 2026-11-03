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
 * </ul>
 */
public record PossessedLook(int stage, boolean face, boolean blood, boolean eyes, float glow, int skinLevel) {
    @Nullable
    public static PossessedLook of(Entity entity, float tickDelta) {
        int stage = Stages.of(entity);
        if (stage == Stages.NONE) {
            return null;
        }
        float time = entity.age + tickDelta;
        if (stage == Stages.INFESTATION) {
            float progress = Stages.progress(entity);
            int period = Math.max(14, (int) (70 - 50 * progress));
            int phase = Math.floorMod(entity.age + entity.getId() * 37, period);
            boolean lit = phase < 4 || (progress > 0.6F && Math.floorMod(entity.age * 7 + entity.getId(), 41) < 2);
            return new PossessedLook(stage, false, false, lit, 0.85F, 0);
        }
        if (stage == Stages.OPPRESSION) {
            return new PossessedLook(stage, true, false, true, 0.95F, 1);
        }
        float pulse = 0.8F + 0.2F * MathHelper.sin(time * 0.18F);
        return new PossessedLook(stage, true, true, true, pulse, 2);
    }
}
