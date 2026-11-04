package ladysnake.requiem.api.v1.possession;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/** COMPILE-ONLY STAND-IN for Requiem's real interface (2.0.0-beta.16). Left out of the jar (see build.gradle). */
public interface PossessionComponent {
    static PossessionComponent get(PlayerEntity player) {
        throw new AssertionError("compile-time stub");
    }

    /** The mob this player is currently possessing, or null. */
    @Nullable
    static MobEntity getHost(Entity possessor) {
        throw new AssertionError("compile-time stub");
    }

    void stopPossessing();

    /** Same as {@code startPossessing(mob, false)}: try to possess this mob now. */
    default boolean startPossessing(MobEntity mob) {
        return startPossessing(mob, false);
    }

    boolean startPossessing(MobEntity mob, boolean simulate);
}
