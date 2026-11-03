package ladysnake.requiem.api.v1.entity.ability;

import net.minecraft.entity.LivingEntity;

import java.util.function.Function;

/** COMPILE-ONLY STAND-IN for Requiem's real interface (2.0.0-beta.16). Left out of the jar (see build.gradle). */
public interface MobAbilityConfig<E extends LivingEntity> {
    interface Builder<E extends LivingEntity> {
        default Builder<E> directAttack(Function<E, DirectAbility<? super E, ?>> factory) {
            return direct(AbilityType.ATTACK, factory);
        }

        default Builder<E> directInteract(Function<E, DirectAbility<? super E, ?>> factory) {
            return direct(AbilityType.INTERACT, factory);
        }

        default Builder<E> indirectAttack(Function<E, IndirectAbility<? super E>> factory) {
            return indirect(AbilityType.ATTACK, factory);
        }

        default Builder<E> indirectInteract(Function<E, IndirectAbility<? super E>> factory) {
            return indirect(AbilityType.INTERACT, factory);
        }

        Builder<E> direct(AbilityType type, Function<E, DirectAbility<? super E, ?>> factory);

        Builder<E> indirect(AbilityType type, Function<E, IndirectAbility<? super E>> factory);

        MobAbilityConfig<E> build();
    }
}
