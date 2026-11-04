package com.possessedgrin;

import com.possessedgrin.mixin.DefaultAttributeContainerAccessor;
import com.possessedgrin.stage.Stages;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets every possessed mob attack, including passive ones like villagers, cows or the Ender Dragon.
 *
 * <p>Requiem's possessed melee attack refuses to run for mobs without an attack-damage stat, and passive mobs
 * don't have one. So:
 * <ol>
 *   <li>Every mob without that stat gets it, with a base value of <b>0</b>. Zero on purpose: Requiem uses the base
 *       value to decide how hard a soul is to capture, and 0 counts exactly the same as "no stat", so soul capture
 *       is unchanged. Their AI never attacks, so nothing else about them changes either.</li>
 *   <li>While a possessed one swings, it temporarily gets extra damage scaled to its size: a tenth of its max
 *       health, from 1 (chicken, cow) through 2 (villager) up to 10 (Ender Dragon). Held weapons add their damage
 *       on top, and Requiem's attack-cooldown scaling still applies.</li>
 * </ol>
 */
public final class PossessedPunch {
    private static final UUID PUNCH_MODIFIER_ID = UUID.fromString("5d1f0e7a-2a4f-4c7e-9a52-7c4f0c3b9e11");
    private static final UUID STRENGTH_MODIFIER_ID = UUID.fromString("9b0c55a2-6c1e-4b8f-a0e4-0f3d2a7c61d4");
    private static final UUID KNOCKBACK_MODIFIER_ID = UUID.fromString("c4e7a3f1-8d2b-4e6a-b1f9-3a5d7e9c2b80");
    private static final double UNHOLY_STRENGTH = 0.5;   // +50% damage
    private static final double UNHOLY_KNOCKBACK = 1.0;

    /** Mob types that were given the stat by this addon, with their patched default stats. */
    private static final Map<EntityType<?>, DefaultAttributeContainer> WITH_ATTACK_STAT = new ConcurrentHashMap<>();

    private PossessedPunch() {
    }

    /**
     * Returns the mob's default stats plus a zero attack-damage stat, or {@code null} to leave them unchanged.
     * The tag check isn't cached, because tags may not be loaded yet the first time this is asked.
     */
    @Nullable
    public static DefaultAttributeContainer withAttackStat(EntityType<?> type, @Nullable DefaultAttributeContainer original) {
        if (original == null || original.has(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                || type == EntityType.PLAYER || type.isIn(PossessedGrin.NEVER_PUNCH)) {
            return null;
        }
        return WITH_ATTACK_STAT.computeIfAbsent(type, t -> {
            DefaultAttributeContainer.Builder builder = DefaultAttributeContainer.builder();
            ((DefaultAttributeContainerAccessor) original).possessedgrin$getInstances()
                    .forEach((attribute, instance) -> builder.add(attribute, instance.getBaseValue()));
            builder.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0);
            // Minecraft's mob attack also reads knockback; almost every mob has it, but make sure
            if (!original.has(EntityAttributes.GENERIC_ATTACK_KNOCKBACK)) {
                builder.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 0.0);
            }
            return builder.build();
        });
    }

    public static float punchDamage(MobEntity mob) {
        return MathHelper.clamp(mob.getMaxHealth() / 10.0F, 1.0F, 10.0F);
    }

    /**
     * Called right before a mob attacks. Possessed mobs that had no attack of their own get the base punch; from
     * stage III every possessed mob hits with unholy strength: +50% damage and a hard shove.
     */
    public static void beforeAttack(MobEntity mob) {
        if (!PossessedGrin.isPossessed(mob)) {
            return;
        }
        EntityAttributeInstance attack = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attack == null) {
            return;
        }
        if (WITH_ATTACK_STAT.containsKey(mob.getType())) {
            addOnce(attack, PUNCH_MODIFIER_ID, "Possessed punch", punchDamage(mob), EntityAttributeModifier.Operation.ADDITION);
        }
        if (Stages.of(mob) < Stages.POSSESSION) {
            return;     // unholy strength awakens at stage III
        }
        addOnce(attack, STRENGTH_MODIFIER_ID, "Unholy strength", UNHOLY_STRENGTH, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
        EntityAttributeInstance knockback = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_KNOCKBACK);
        if (knockback != null) {
            addOnce(knockback, KNOCKBACK_MODIFIER_ID, "Unholy strength", UNHOLY_KNOCKBACK, EntityAttributeModifier.Operation.ADDITION);
        }
    }

    /** Called right after a mob attacks. */
    public static void afterAttack(MobEntity mob) {
        EntityAttributeInstance attack = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attack != null) {
            removeIfPresent(attack, PUNCH_MODIFIER_ID);
            removeIfPresent(attack, STRENGTH_MODIFIER_ID);
        }
        EntityAttributeInstance knockback = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_KNOCKBACK);
        if (knockback != null) {
            removeIfPresent(knockback, KNOCKBACK_MODIFIER_ID);
        }
    }

    private static void addOnce(EntityAttributeInstance instance, UUID id, String name, double amount, EntityAttributeModifier.Operation operation) {
        if (instance.getModifier(id) == null) {
            instance.addTemporaryModifier(new EntityAttributeModifier(id, name, amount, operation));
        }
    }

    private static void removeIfPresent(EntityAttributeInstance instance, UUID id) {
        if (instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
    }
}
