package com.possessedgrin.haunt;

import com.possessedgrin.PossessedGrin;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The haunting that follows a possessed player (and anyone holding a séance with the Ouija board):
 * <ul>
 *   <li>knocking and banging from inside the walls,</li>
 *   <li>torches and lanterns flickering (they briefly burn blue and dim) and sometimes going out,</li>
 *   <li>whispers during a séance,</li>
 *   <li>and, whenever a power is used, armor stands wobble, item frames spin, paintings fall, and the screen shakes.</li>
 * </ul>
 */
public final class Haunting {
    /** Server to client: screen shake (float strength, varint ticks). */
    public static final Identifier SHAKE = PossessedGrin.id("shake");
    public static final int SEANCE_TICKS = 1200;   // one minute

    private static final int LIGHT_RADIUS = 7;
    private static final String[] SEANCE_WHISPERS = {
            "Someone is here with you.",
            "It knows your name.",
            "Ask it something. It's listening.",
            "The planchette slides toward a letter... then stops.",
            "Something inside the walls is counting.",
            "Don't let it in.",
            "It says it has been waiting for you."
    };

    private static final Map<UUID, Long> SEANCE_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> NEXT_KNOCK = new HashMap<>();
    private static final Map<UUID, Long> NEXT_WHISPER = new HashMap<>();
    private static final List<Knock> KNOCKS = new ArrayList<>();
    private static final List<Flicker> FLICKERS = new ArrayList<>();

    private record Knock(World world, BlockPos pos, long at, boolean bang) {
    }

    private record Flicker(World world, BlockPos pos, BlockState original, BlockState flicker, long revertAt) {
    }

    private Haunting() {
    }

    // --- séance ---------------------------------------------------------------------------------------------

    public static void startSeance(ServerPlayerEntity player) {
        long now = player.world.getTime();
        SEANCE_UNTIL.put(player.getUuid(), now + SEANCE_TICKS);
        NEXT_WHISPER.put(player.getUuid(), now + 100);
        NEXT_KNOCK.put(player.getUuid(), now + 40);
        player.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.AMBIENT, 0.7F, 0.6F);
        player.world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.AMBIENT, 1.0F, 0.5F);
        player.sendMessage(Text.literal("The planchette begins to move on its own...").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), false);
        player.sendMessage(Text.literal("For one minute, any creature will accept you, and your powers return twice as fast.").formatted(Formatting.GRAY), false);
        ((ServerWorld) player.world).spawnParticles(ParticleTypes.SOUL, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 1.5, 1.0, 1.5, 0.02);
        shake(player, 0.8F, 30);
    }

    /** Keeps a séance going while someone is still talking to the board. */
    public static void extendSeance(PlayerEntity player, int ticks) {
        Long until = SEANCE_UNTIL.get(player.getUuid());
        long now = player.world.getTime();
        if (until != null && until > now) {
            SEANCE_UNTIL.put(player.getUuid(), Math.max(until, now + ticks));
        }
    }

    public static boolean inSeance(PlayerEntity player) {
        Long until = SEANCE_UNTIL.get(player.getUuid());
        return until != null && player.world.getTime() < until;
    }

    // --- every tick ------------------------------------------------------------------------------------------

    public static void tick(ServerWorld world) {
        long now = world.getTime();
        playDueKnocks(world, now);
        revertDueFlickers(world, now);

        for (ServerPlayerEntity player : world.getPlayers()) {
            MobEntity host = PossessionComponent.getHost(player);
            boolean seance = inSeance(player);
            if (host == null && !seance) {
                continue;
            }
            Entity center = host != null ? host : player;
            Random random = world.getRandom();

            long nextKnock = NEXT_KNOCK.getOrDefault(player.getUuid(), now + 100);
            if (now >= nextKnock) {
                scheduleKnocks(world, center, random, now);
                NEXT_KNOCK.put(player.getUuid(), now + 160 + random.nextInt(seance ? 120 : 280));
            } else if (!NEXT_KNOCK.containsKey(player.getUuid())) {
                NEXT_KNOCK.put(player.getUuid(), nextKnock);
            }

            if (now % 15 == 0) {
                flickerLights(world, center.getBlockPos(), random, now);
            }
            if (now % 100 == 0 && random.nextFloat() < 0.35F) {
                snuffALight(world, center.getBlockPos(), random);
            }

            if (seance) {
                long nextWhisper = NEXT_WHISPER.getOrDefault(player.getUuid(), now);
                if (now >= nextWhisper) {
                    String whisper = SEANCE_WHISPERS[random.nextInt(SEANCE_WHISPERS.length)];
                    player.sendMessage(Text.literal("\"" + whisper + "\"").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), false);
                    player.playSound(SoundEvents.ENTITY_VEX_AMBIENT, SoundCategory.AMBIENT, 0.8F, 0.4F);
                    NEXT_WHISPER.put(player.getUuid(), now + 240 + random.nextInt(160));
                }
                if (host == null && now % 20 == 0) {
                    // The room is cold during a séance too: you can see your breath
                    var breath = player.getEyePos().add(player.getRotationVec(1.0F).multiply(0.4)).add(0.0, -0.15, 0.0);
                    world.spawnParticles(ParticleTypes.CLOUD, breath.x, breath.y, breath.z, 1, 0.02, 0.02, 0.02, 0.005);
                }
            }
        }

        if (now % 1200 == 0) {
            SEANCE_UNTIL.values().removeIf(until -> until < now - 1200);
        }
    }

    // --- knocking ---------------------------------------------------------------------------------------------

    private static void scheduleKnocks(ServerWorld world, Entity center, Random random, long now) {
        BlockPos origin = center.getBlockPos();
        BlockPos spot = origin;
        // Prefer a spot inside a wall
        for (int tries = 0; tries < 12; tries++) {
            BlockPos candidate = origin.add(random.nextInt(11) - 5, random.nextInt(3), random.nextInt(11) - 5);
            if (world.getBlockState(candidate).isSolidBlock(world, candidate)) {
                spot = candidate;
                break;
            }
        }
        boolean bang = random.nextFloat() < 0.2F;
        int count = bang ? 2 : 3 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            KNOCKS.add(new Knock(world, spot.toImmutable(), now + 1 + i * (bang ? 10L : 6L), bang));
        }
    }

    private static void playDueKnocks(ServerWorld world, long now) {
        Iterator<Knock> it = KNOCKS.iterator();
        while (it.hasNext()) {
            Knock knock = it.next();
            if (knock.world() != world || knock.at() > now) {
                continue;
            }
            it.remove();
            if (knock.bang()) {
                world.playSound(null, knock.pos(), SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.AMBIENT, 0.7F, 0.55F);
            } else {
                world.playSound(null, knock.pos(), SoundEvents.BLOCK_WOOD_HIT, SoundCategory.AMBIENT, 2.0F, 0.6F + world.getRandom().nextFloat() * 0.15F);
            }
        }
    }

    // --- dying lights -------------------------------------------------------------------------------------------

    private static void flickerLights(ServerWorld world, BlockPos origin, Random random, long now) {
        List<BlockPos> lights = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(origin.add(-LIGHT_RADIUS, -3, -LIGHT_RADIUS), origin.add(LIGHT_RADIUS, 4, LIGHT_RADIUS))) {
            BlockState state = world.getBlockState(pos);
            if (state.isOf(Blocks.TORCH) || state.isOf(Blocks.WALL_TORCH) || state.isOf(Blocks.LANTERN)) {
                lights.add(pos.toImmutable());
            }
        }
        for (int i = 0; i < 2 && !lights.isEmpty(); i++) {
            if (random.nextFloat() > 0.6F) {
                continue;
            }
            BlockPos pos = lights.remove(random.nextInt(lights.size()));
            BlockState original = world.getBlockState(pos);
            Block soulVersion = original.isOf(Blocks.TORCH) ? Blocks.SOUL_TORCH
                    : original.isOf(Blocks.WALL_TORCH) ? Blocks.SOUL_WALL_TORCH
                    : Blocks.SOUL_LANTERN;
            BlockState flicker = copyProperties(original, soulVersion.getDefaultState());
            world.setBlockState(pos, flicker);
            FLICKERS.add(new Flicker(world, pos, original, flicker, now + 4 + random.nextInt(8)));
        }
    }

    private static void revertDueFlickers(ServerWorld world, long now) {
        Iterator<Flicker> it = FLICKERS.iterator();
        while (it.hasNext()) {
            Flicker flicker = it.next();
            if (flicker.world() != world || flicker.revertAt() > now) {
                continue;
            }
            it.remove();
            if (world.isChunkLoaded(flicker.pos()) && world.getBlockState(flicker.pos()) == flicker.flicker()) {
                world.setBlockState(flicker.pos(), flicker.original());
            }
        }
    }

    /** Blows out one torch, candle or campfire nearby. Torches drop as items; candles and campfires can be relit. */
    private static void snuffALight(ServerWorld world, BlockPos origin, Random random) {
        List<BlockPos> lights = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(origin.add(-LIGHT_RADIUS, -3, -LIGHT_RADIUS), origin.add(LIGHT_RADIUS, 4, LIGHT_RADIUS))) {
            BlockState state = world.getBlockState(pos);
            boolean torch = state.isOf(Blocks.TORCH) || state.isOf(Blocks.WALL_TORCH);
            boolean lit = state.contains(Properties.LIT) && state.get(Properties.LIT);
            if (torch || lit) {
                lights.add(pos.toImmutable());
            }
        }
        if (lights.isEmpty()) {
            return;
        }
        BlockPos pos = lights.get(random.nextInt(lights.size()));
        BlockState state = world.getBlockState(pos);
        if (state.contains(Properties.LIT)) {
            world.setBlockState(pos, state.with(Properties.LIT, false));
            world.playSound(null, pos, SoundEvents.BLOCK_CANDLE_EXTINGUISH, SoundCategory.BLOCKS, 1.0F, 0.8F);
        } else if (world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            world.breakBlock(pos, true);
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.6F, 1.2F);
        } else {
            return;
        }
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.1, 0.1, 0.1, 0.01);
    }

    private static BlockState copyProperties(BlockState from, BlockState to) {
        for (Property<?> property : from.getProperties()) {
            if (to.contains(property)) {
                to = copy(from, to, property);
            }
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.with(property, from.get(property));
    }

    // --- rattling and shaking ------------------------------------------------------------------------------------

    /** Armor stands wobble and item frames spin; when {@code violent}, paintings may crash to the floor too. */
    public static void rattle(ServerWorld world, LivingEntity center, double radius, boolean violent) {
        Random random = world.getRandom();
        for (Entity entity : world.getOtherEntities(center, center.getBoundingBox().expand(radius))) {
            if (entity instanceof ArmorStandEntity) {
                world.sendEntityStatus(entity, (byte) 32);   // the wobble you see when you punch one
            } else if (entity instanceof ItemFrameEntity frame && !frame.getHeldItemStack().isEmpty()) {
                frame.setRotation(frame.getRotation() + 1);
                world.playSound(null, frame.getBlockPos(), SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, SoundCategory.BLOCKS, 1.0F, 0.7F);
            } else if (violent && entity instanceof PaintingEntity painting && random.nextFloat() < 0.25F
                    && world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                painting.damage(DamageSource.mob(center), 1.0F);   // falls off the wall and drops as an item
            }
        }
    }

    public static void shake(ServerPlayerEntity player, float strength, int ticks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(strength);
        buf.writeVarInt(ticks);
        ServerPlayNetworking.send(player, SHAKE, buf);
    }
}
