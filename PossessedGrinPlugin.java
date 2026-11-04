package com.possessedgrin;

import com.possessedgrin.ability.DragonBreathAbility;
import com.possessedgrin.ability.SonicBoomAbility;
import ladysnake.requiem.api.v1.RequiemPlugin;
import ladysnake.requiem.api.v1.entity.ability.MobAbilityRegistry;
import net.minecraft.entity.EntityType;

/**
 * Hooked into Requiem through the {@code requiem:plugin} entrypoint.
 *
 * <p>Controls while possessing (left click on a mob is the normal melee attack for all of them):
 * <ul>
 *   <li><b>Warden</b>: right click = sonic boom (charges for under 2 seconds, then fires where you look).</li>
 *   <li><b>Ender Dragon</b>: right click = dragon's breath fireball. Left click on a mob = bite.</li>
 *   <li><b>Wither</b>: Requiem already provides it: left click a mob = black skull, left click air = blue skull.</li>
 * </ul>
 */
public final class PossessedGrinPlugin implements RequiemPlugin {
    @Override
    public void onRequiemInitialize() {
        // Also done from a mixin right after Requiem registers its own rules; doing it here too is harmless.
        BossPossession.install();
    }

    @Override
    public void registerMobAbilities(MobAbilityRegistry registry) {
        registry.beginRegistration(EntityType.WARDEN)
                .directInteract(SonicBoomAbility::of)
                .indirectInteract(SonicBoomAbility::of)
                .build();
        registry.beginRegistration(EntityType.ENDER_DRAGON)
                .directInteract(DragonBreathAbility::of)
                .indirectInteract(DragonBreathAbility::of)
                .build();
    }
}
