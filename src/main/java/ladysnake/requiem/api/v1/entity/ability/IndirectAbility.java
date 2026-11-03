package ladysnake.requiem.api.v1.entity.ability;

import net.minecraft.entity.LivingEntity;

/** COMPILE-ONLY STAND-IN for Requiem's real interface (2.0.0-beta.16). Left out of the jar (see build.gradle). */
@FunctionalInterface
public interface IndirectAbility<E extends LivingEntity> extends MobAbility<E> {
    boolean trigger();
}
