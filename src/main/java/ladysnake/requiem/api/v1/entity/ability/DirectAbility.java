package ladysnake.requiem.api.v1.entity.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;

/** COMPILE-ONLY STAND-IN for Requiem's real interface (2.0.0-beta.16). Left out of the jar (see build.gradle). */
public interface DirectAbility<E extends LivingEntity, T extends Entity> extends MobAbility<E> {
    double getRange();

    Class<T> getTargetType();

    boolean canTarget(T target);

    ActionResult trigger(T target);
}
