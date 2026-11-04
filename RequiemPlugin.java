package ladysnake.requiem.api.v1;

import ladysnake.requiem.api.v1.entity.ability.MobAbilityRegistry;

/** COMPILE-ONLY STAND-IN for Requiem's real interface (2.0.0-beta.16). Left out of the jar (see build.gradle). */
public interface RequiemPlugin {
    default void onRequiemInitialize() {
    }

    default void registerMobAbilities(MobAbilityRegistry registry) {
    }
}
