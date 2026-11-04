package com.possessedgrin.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Warden's sonic boom, aimed wherever the possessing player looks. Like the real one it charges up first
 * (with the matching animation and sound), passes through walls, and hits every creature along the beam.
 * Numbers match vanilla: 10 damage (ignores armor), big knockback.
 */
public class SonicBoomAbility extends PossessedBossAbility<WardenEntity> {
    private static final Map<WardenEntity, SonicBoomAbility> INSTANCES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final double RANGE = 20.0;
    private static final int CHARGE_TICKS = 34;      // when vanilla's animation releases the boom
    private static final int COOLDOWN_TICKS = 80;    // 4 seconds, charge included
    private static final float DAMAGE = 10.0F;
    private static final byte SONIC_BOOM_ANIMATION = 62;

    private int charge = -1;

    private SonicBoomAbility(WardenEntity owner) {
        super(owner, COOLDOWN_TICKS, RANGE);
    }

    public static SonicBoomAbility of(WardenEntity warden) {
        return INSTANCES.computeIfAbsent(warden, SonicBoomAbility::new);
    }

    @Override
    protected void start(PlayerEntity rider) {
        this.owner.world.sendEntityStatus(this.owner, SONIC_BOOM_ANIMATION);
        this.owner.playSound(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 3.0F, 1.0F);
        this.charge = CHARGE_TICKS;
    }

    @Override
    protected void serverTick() {
        if (this.charge > 0 && --this.charge == 0) {
            this.charge = -1;
            PlayerEntity rider = this.rider();
            if (rider != null && this.owner.isAlive()) {
                this.fire(rider);
            }
        }
    }

    private void fire(PlayerEntity rider) {
        if (!(this.owner.world instanceof ServerWorld world)) {
            return;
        }
        Vec3d direction = rider.getRotationVec(1.0F);
        Vec3d start = this.owner.getPos().add(0.0, 1.6, 0.0);
        Vec3d end = start.add(direction.multiply(RANGE));

        for (int i = 1; i <= RANGE; i++) {
            Vec3d point = start.add(direction.multiply(i));
            world.spawnParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        this.owner.playSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 3.0F, 1.0F);

        Box searchArea = new Box(start, end).expand(1.5);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, searchArea,
                e -> e != this.owner && e != rider && e.isAlive())) {
            if (target.getBoundingBox().expand(0.75).raycast(start, end).isEmpty()) {
                continue;
            }
            if (target.damage(DamageSource.sonicBoom(this.owner), DAMAGE)) {
                double resistance = target.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
                double up = 0.5 * (1.0 - resistance);
                double away = 2.5 * (1.0 - resistance);
                target.addVelocity(direction.x * away, direction.y * up, direction.z * away);
            }
        }
    }
}
