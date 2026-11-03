package com.possessedgrin.ability;

import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Ender Dragon's breath attack: a dragon fireball launched from its mouth wherever the possessing player
 * looks. It bursts into a lingering cloud of dragon's breath, exactly like the real fight (you can even bottle it).
 */
public class DragonBreathAbility extends PossessedBossAbility<EnderDragonEntity> {
    private static final Map<EnderDragonEntity, DragonBreathAbility> INSTANCES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final double RANGE = 32.0;
    private static final int COOLDOWN_TICKS = 40;    // 2 seconds
    private static final int DRAGON_SHOOTS_EVENT = 1017;

    private DragonBreathAbility(EnderDragonEntity owner) {
        super(owner, COOLDOWN_TICKS, RANGE);
    }

    public static DragonBreathAbility of(EnderDragonEntity dragon) {
        return INSTANCES.computeIfAbsent(dragon, DragonBreathAbility::new);
    }

    @Override
    protected void start(PlayerEntity rider) {
        Vec3d direction = rider.getRotationVec(1.0F);
        EnderDragonPart head = this.owner.getBodyParts()[0];

        DragonFireballEntity fireball = new DragonFireballEntity(this.owner.world, this.owner, direction.x, direction.y, direction.z);
        fireball.refreshPositionAndAngles(
                head.getX() + direction.x,
                head.getBodyY(0.5) + 0.5,
                head.getZ() + direction.z,
                0.0F, 0.0F);
        this.owner.world.spawnEntity(fireball);
        if (!this.owner.isSilent()) {
            this.owner.world.syncWorldEvent(null, DRAGON_SHOOTS_EVENT, this.owner.getBlockPos(), 0);
        }
    }
}
