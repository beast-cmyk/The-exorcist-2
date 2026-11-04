package com.possessedgrin.haunt;

import com.possessedgrin.stage.Stages;
import com.possessedgrin.voice.Witness;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The house is never quiet around a possessed body (or during a séance): something scratches inside the walls,
 * and slow footsteps cross the floor above, where nobody is. Villagers who hear it remember it.
 */
public final class AtticSounds {
    private record Sound(ServerWorld world, BlockPos pos, SoundEvent event, float volume, float pitch, long at) {
    }

    private static final List<Sound> QUEUE = new ArrayList<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private AtticSounds() {
    }

    public static void tick(ServerWorld world) {
        long now = world.getTime();
        play(world, now);
        if (now % 20 != 5) {
            return;
        }
        Random random = world.getRandom();
        for (ServerPlayerEntity player : world.getPlayers()) {
            MobEntity host = PossessionComponent.getHost(player);
            boolean seance = Haunting.inSeance(player);
            if (host == null && !seance) {
                NEXT.remove(player.getUuid());
                continue;
            }
            Entity center = host != null ? host : player;
            int stage = host == null ? Stages.INFESTATION : Stages.of(host);
            long next = NEXT.computeIfAbsent(player.getUuid(), id -> now + 200 + random.nextInt(300));
            if (now < next) {
                continue;
            }
            NEXT.put(player.getUuid(), now + (stage >= Stages.POSSESSION || seance ? 300 : 500) + random.nextInt(500));
            if (random.nextBoolean()) {
                scratching(world, center, random, now);
            } else {
                footsteps(world, center, random, now);
            }
        }
    }

    /** Claws in the wall: a burst of scratching, a pause, another burst, from inside a solid block nearby. */
    private static void scratching(ServerWorld world, Entity center, Random random, long now) {
        BlockPos wall = solidNear(world, center.getBlockPos(), random);
        if (wall == null) {
            return;
        }
        long at = now;
        for (int burst = 0; burst < 2 + random.nextInt(2); burst++) {
            for (int i = 0; i < 5 + random.nextInt(4); i++) {
                QUEUE.add(new Sound(world, wall, random.nextBoolean() ? SoundEvents.ENTITY_SILVERFISH_STEP : SoundEvents.BLOCK_ROOTED_DIRT_HIT,
                        0.55F, 0.6F + random.nextFloat() * 0.5F, at));
                at += 2 + random.nextInt(2);
            }
            at += 15 + random.nextInt(20);
        }
        heard(world, wall, "You heard something scratching inside the walls, near where the possessed one lives.");
    }

    /** Heavy, slow steps crossing the floor above, a few blocks over your head. */
    private static void footsteps(ServerWorld world, Entity center, Random random, long now) {
        BlockPos start = null;
        for (int up = 2; up <= 6 && start == null; up++) {
            BlockPos above = center.getBlockPos().up(up);
            if (world.getBlockState(above).isSolidBlock(world, above)) {
                start = above;
            }
        }
        if (start == null) {
            return;     // open sky: nothing walking up there
        }
        Direction direction = Direction.Type.HORIZONTAL.random(random);
        BlockPos at = start.offset(direction.getOpposite(), 3);
        long time = now;
        for (int step = 0; step < 6 + random.nextInt(4); step++) {
            BlockState floor = world.getBlockState(at);
            SoundEvent sound = floor.isAir() ? SoundEvents.BLOCK_WOOD_STEP : floor.getSoundGroup().getStepSound();
            QUEUE.add(new Sound(world, at, sound, 0.9F, 0.55F + random.nextFloat() * 0.1F, time));
            at = at.offset(direction);
            time += 12 + random.nextInt(6);
        }
        if (random.nextInt(3) == 0) {
            QUEUE.add(new Sound(world, at, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, 0.5F, 0.5F, time + 20));
        }
        heard(world, start, "You heard footsteps in the attic, slow and heavy, when there was nobody up there.");
    }

    @Nullable
    private static BlockPos solidNear(ServerWorld world, BlockPos origin, Random random) {
        for (int tries = 0; tries < 16; tries++) {
            BlockPos candidate = origin.add(random.nextInt(9) - 4, random.nextInt(3), random.nextInt(9) - 4);
            if (world.getBlockState(candidate).isSolidBlock(world, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static void heard(ServerWorld world, BlockPos where, String memory) {
        for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class, new Box(where).expand(10.0),
                VillagerEntity::isAlive)) {
            Witness.record(villager, memory);
        }
    }

    private static void play(ServerWorld world, long now) {
        Iterator<Sound> it = QUEUE.iterator();
        while (it.hasNext()) {
            Sound sound = it.next();
            if (sound.world() != world || sound.at() > now) {
                continue;
            }
            it.remove();
            world.playSound(null, sound.pos(), sound.event(), SoundCategory.AMBIENT, sound.volume(), sound.pitch());
        }
    }

    public static void clear() {
        QUEUE.clear();
        NEXT.clear();
    }
}
