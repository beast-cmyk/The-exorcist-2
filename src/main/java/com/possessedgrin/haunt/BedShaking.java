package com.possessedgrin.haunt;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.stage.Stages;
import com.possessedgrin.voice.Witness;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The beds shake. From stage II, every so often every bed near the possessed body starts banging against the floor,
 * hard and fast, for a few seconds, and anyone asleep in one wakes up in terror. Float over a bed and it shakes
 * far more often. If the body is on or beside the bed, it thrashes with it; when the shaking stops, it rises off
 * the bed, rigid, and hangs flat in the air for a few seconds.
 */
public final class BedShaking {
    private static final int RADIUS = 8;
    private static final int EPISODE = 60;      // three seconds of banging
    private static final int RISE_TICKS = 28;   // about a block and a half up
    private static final int HANG_TICKS = 90;

    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, Long> UNTIL = new HashMap<>();
    private static final Map<UUID, List<BlockPos>> BEDS = new HashMap<>();

    private BedShaking() {
    }

    public static void tick(ServerWorld world) {
        long now = world.getTime();
        Random random = world.getRandom();
        for (ServerPlayerEntity player : world.getPlayers()) {
            MobEntity host = PossessionComponent.getHost(player);
            UUID id = player.getUuid();
            if (host == null || Stages.of(host) < Stages.OPPRESSION) {
                NEXT.remove(id);
                UNTIL.remove(id);
                BEDS.remove(id);
                continue;
            }
            long until = UNTIL.getOrDefault(id, 0L);
            if (now < until) {
                if (now % 3 == 0) {
                    bang(world, BEDS.getOrDefault(id, List.of()), random);
                }
                continue;
            }
            if (until != 0L) {
                UNTIL.remove(id);
                rise(world, player, host, BEDS.getOrDefault(id, List.of()));
            }
            long next = NEXT.computeIfAbsent(id, uuid -> now + 200 + random.nextInt(200));
            if (now < next) {
                continue;
            }
            List<BlockPos> beds = findBeds(world, host.getBlockPos());
            boolean overABed = player.getAbilities().flying && !findBeds(world, host.getBlockPos().down(2)).isEmpty();
            NEXT.put(id, now + (overABed ? 120 + random.nextInt(80) : 400 + random.nextInt(400)));
            if (beds.isEmpty()) {
                continue;
            }
            UNTIL.put(id, now + EPISODE);
            BEDS.put(id, beds);
            for (BlockPos bed : beds) {
                wakeSleepers(world, bed);
            }
            if (nearBed(host, beds)) {
                // The body on the bed thrashes with it, as if something is shaking it from inside
                Fx.send(host, Fx.THRASH, EPISODE);
                Witness.seen(host, null, "You saw " + PossessedGrin.the(host) + " thrash on the bed while the whole bed shook.");
            }
        }
    }

    /** When the shaking stops, the body on the bed rises off it, stiff as a board, and hangs there. */
    private static void rise(ServerWorld world, ServerPlayerEntity player, MobEntity host, List<BlockPos> beds) {
        if (!nearBed(host, beds) || !host.isOnGround()) {
            return;
        }
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, RISE_TICKS, 0, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, RISE_TICKS + HANG_TICKS, 0, false, false));
        Fx.send(host, Fx.RIGID, RISE_TICKS + HANG_TICKS);
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.HOSTILE, 0.6F, 0.5F);
        Witness.seen(host, null, "You saw " + PossessedGrin.the(host) + " rise off the bed, rigid as a board, and hang in the air above it.");
    }

    private static boolean nearBed(MobEntity host, List<BlockPos> beds) {
        for (BlockPos bed : beds) {
            if (host.getBlockPos().getSquaredDistance(bed) <= 6.0) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> findBeds(ServerWorld world, BlockPos center) {
        List<BlockPos> beds = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(center.add(-RADIUS, -3, -RADIUS), center.add(RADIUS, 3, RADIUS))) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock && state.get(BedBlock.PART) == BedPart.HEAD) {
                beds.add(pos.toImmutable());
            }
        }
        return beds;
    }

    /** One violent jolt: the frame thumps the floor, splinters fly. */
    private static void bang(ServerWorld world, List<BlockPos> beds, Random random) {
        for (BlockPos bed : beds) {
            BlockState state = world.getBlockState(bed);
            if (!(state.getBlock() instanceof BedBlock)) {
                continue;
            }
            if (random.nextFloat() < 0.7F) {
                world.playSound(null, bed, random.nextBoolean() ? SoundEvents.BLOCK_WOOD_HIT : SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
                        SoundCategory.BLOCKS, 0.5F + random.nextFloat() * 0.3F, 0.6F + random.nextFloat() * 0.3F);
            }
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                    bed.getX() + 0.5, bed.getY() + 0.55, bed.getZ() + 0.5, 4, 0.4, 0.05, 0.4, 0.0);
        }
    }

    private static void wakeSleepers(ServerWorld world, BlockPos bed) {
        for (LivingEntity sleeper : world.getEntitiesByClass(LivingEntity.class, new Box(bed).expand(1.5), LivingEntity::isSleeping)) {
            sleeper.wakeUp();
            Witness.record(sleeper, "Your bed shook so violently it threw you awake, in the dark.");
        }
    }
}
