package ladysnake.requiem.api.v1.event.requiem;

import ladysnake.requiem.api.v1.event.IdentifyingEvent;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

/** COMPILE-ONLY STAND-IN for Requiem's real interface (2.0.0-beta.16). Left out of the jar (see build.gradle). */
public interface PossessionStartCallback {
    IdentifyingEvent<PossessionStartCallback> EVENT = null;

    Result onPossessionAttempted(MobEntity target, PlayerEntity possessor, boolean simulate);

    enum Result {
        DENY,
        PASS,
        ALLOW,
        HANDLED
    }
}
