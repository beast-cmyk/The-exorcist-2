package ladysnake.requiem.api.v1.entity;

import net.minecraft.entity.player.PlayerEntity;

/** COMPILE-ONLY STAND-IN for Requiem's real interface (2.0.0-beta.16). Left out of the jar (see build.gradle). */
public interface MovementAlterer {
    static MovementAlterer get(PlayerEntity player) {
        throw new AssertionError("stand-in: Requiem provides the real MovementAlterer in game");
    }

    void applyConfig();
}
