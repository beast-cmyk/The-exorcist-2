package ladysnake.requiem.api.v1.entity.ability;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;

/** COMPILE-ONLY STAND-IN for Requiem's real interface (2.0.0-beta.16). Left out of the jar (see build.gradle). */
public interface MobAbilityRegistry {
    <E extends MobEntity> MobAbilityConfig.Builder<E> beginRegistration(EntityType<E> entityType);
}
