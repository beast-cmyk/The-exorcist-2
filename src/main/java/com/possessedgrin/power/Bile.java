package com.possessedgrin.power;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.Vec3d;

/**
 * The green bile: a gush of green muck sprayed where you look, 8 blocks long. Anything caught in it gets
 * sick (nausea and a little poison) and takes a bit of damage.
 */
public final class Bile {
    private static final double RANGE = 8.0;
    private static final double CONE = Math.cos(Math.toRadians(22.0));

    private Bile() {
    }

    static boolean use(ServerPlayerEntity player, MobEntity host) {
        ServerWorld world = (ServerWorld) host.world;
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d mouth = player.getEyePos().add(look.multiply(host.getWidth() * 0.6 + 0.3)).add(0.0, -0.25, 0.0);
        Random random = world.getRandom();

        for (int i = 0; i < 40; i++) {
            Vec3d spray = look.add(random.nextGaussian() * 0.12, random.nextGaussian() * 0.12, random.nextGaussian() * 0.12).normalize();
            double speed = 0.6 + random.nextDouble() * 0.5;
            // Particle count 0 means "fire one particle with this velocity"
            world.spawnParticles(i % 2 == 0 ? ParticleTypes.ITEM_SLIME : ParticleTypes.SNEEZE,
                    mouth.x, mouth.y, mouth.z, 0, spray.x, spray.y, spray.z, speed);
        }
        PossessedPowers.sound(host, SoundEvents.ENTITY_LLAMA_SPIT, 1.5F, 0.5F);
        PossessedPowers.sound(host, SoundEvents.ENTITY_ZOGLIN_ANGRY, 0.8F, 0.6F);

        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, host.getBoundingBox().expand(RANGE),
                e -> e != host && e != player && e.isAlive() && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())))) {
            Vec3d toVictim = victim.getPos().add(0.0, victim.getHeight() / 2.0, 0.0).subtract(mouth);
            if (toVictim.lengthSquared() > RANGE * RANGE || toVictim.normalize().dotProduct(look) < CONE) {
                continue;
            }
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 160, 0), host);
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0), host);
            victim.damage(DamageSource.mob(host), 2.0F);
        }
        return true;
    }
}
