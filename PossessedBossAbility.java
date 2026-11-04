package com.possessedgrin.ability;

import com.possessedgrin.PossessedGrin;
import ladysnake.requiem.api.v1.entity.ability.DirectAbility;
import ladysnake.requiem.api.v1.entity.ability.IndirectAbility;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import org.jetbrains.annotations.Nullable;

/**
 * A boss attack fired by right-clicking, whether or not the crosshair is on a mob. One instance serves both the
 * "right click a mob" and "right click the air" slots, so they share a single cooldown.
 *
 * <p>Requiem runs {@link #trigger()} on the client first (to show the swing) and then on the server; the actual
 * attack only happens server-side. {@link #update()} may be called more than once per tick (once per slot), so it
 * only counts down once per game tick.
 */
public abstract class PossessedBossAbility<E extends MobEntity> implements DirectAbility<E, LivingEntity>, IndirectAbility<E> {
    protected final E owner;
    private final int cooldownTicks;
    private final double range;
    private int cooldown;
    private int lastTickedAge = Integer.MIN_VALUE;

    protected PossessedBossAbility(E owner, int cooldownTicks, double range) {
        this.owner = owner;
        this.cooldownTicks = cooldownTicks;
        this.range = range;
    }

    /** Server side: start the attack. */
    protected abstract void start(PlayerEntity rider);

    /** Server side, every tick. */
    protected void serverTick() {
    }

    @Nullable
    protected final PlayerEntity rider() {
        return PossessedGrin.possessorOf(this.owner);
    }

    @Override
    public boolean trigger() {
        PlayerEntity rider = this.rider();
        if (this.cooldown > 0 || rider == null) {
            return false;
        }
        this.cooldown = this.cooldownTicks;
        if (!this.owner.world.isClient) {
            this.start(rider);
        }
        return true;
    }

    @Override
    public ActionResult trigger(LivingEntity target) {
        return this.trigger() ? ActionResult.SUCCESS : ActionResult.FAIL;
    }

    @Override
    public void update() {
        if (this.owner.age == this.lastTickedAge) {
            return;
        }
        this.lastTickedAge = this.owner.age;
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (!this.owner.world.isClient) {
            this.serverTick();
        }
    }

    @Override
    public float getCooldownProgress() {
        return 1.0F - (float) this.cooldown / this.cooldownTicks;
    }

    @Override
    public double getRange() {
        return this.range;
    }

    @Override
    public Class<LivingEntity> getTargetType() {
        return LivingEntity.class;
    }

    @Override
    public boolean canTarget(LivingEntity target) {
        return target != this.owner && target != this.rider();
    }
}
