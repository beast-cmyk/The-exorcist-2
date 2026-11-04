package ladysnake.requiem.api.v1.possession;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * COMPILE-ONLY STAND-IN for Requiem's real interface (Requiem 2.0.0-beta.16).
 * Left out of the jar (see build.gradle): in game, Requiem's own class is used. Requiem mixes it into every LivingEntity.
 */
public interface Possessable {
    boolean isBeingPossessed();

    @Nullable
    PlayerEntity getPossessor();
}
