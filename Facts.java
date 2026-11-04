package com.possessedgrin.voice;

import com.possessedgrin.PossessedGrin;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Things a mob actually knows about the world, read from the game, for its answers and its thoughts. */
public final class Facts {
    private Facts() {
    }

    // --- time and weather ------------------------------------------------------------------------------------

    /** The Nether and the End have no day, night or weather; everywhere else does. */
    public static boolean hasSky(World world) {
        return world.getRegistryKey() != World.NETHER && world.getRegistryKey() != World.END;
    }

    public static String timeOfDay(World world) {
        if (!hasSky(world)) {
            return world.getRegistryKey() == World.NETHER
                    ? "There's no day or night down here. Only heat."
                    : "Time stopped here long ago.";
        }
        long time = world.getTimeOfDay() % 24000L;
        long day = world.getTimeOfDay() / 24000L + 1;
        String part;
        if (time < 1000) {
            part = "just after dawn";
        } else if (time < 5000) {
            part = "morning";
        } else if (time < 7000) {
            part = "about noon";
        } else if (time < 11000) {
            part = "afternoon";
        } else if (time < 13000) {
            part = "dusk. The dark is coming";
        } else if (time < 17000) {
            part = "night";
        } else if (time < 19000) {
            part = "midnight";
        } else if (time < 23000) {
            part = "the dead of night";
        } else {
            part = "nearly dawn";
        }
        String moon = "";
        if (time >= 13000 && time < 23000 && world.getMoonPhase() == 0) {
            moon = " And the moon is full.";
        }
        return "It's " + part + ", on day " + day + "." + moon;
    }

    public static String weather(World world) {
        if (!hasSky(world)) {
            return world.getRegistryKey() == World.NETHER ? "Weather? It's always burning here." : "Nothing ever changes here. No wind. No rain.";
        }
        if (world.isThundering()) {
            return "A storm. Thunder. A bad night to be outside.";
        }
        if (world.isRaining()) {
            return "Raining. Grey and wet.";
        }
        return world.isDay() ? "Clear skies." : "Clear. You can see every star.";
    }

    // --- places ----------------------------------------------------------------------------------------------

    public static String biome(World world, BlockPos pos) {
        return world.getBiome(pos).getKey()
                .map(key -> key.getValue().getPath().replace('_', ' '))
                .orElse("somewhere strange");
    }

    public static String dimension(World world) {
        if (world.getRegistryKey() == World.NETHER) {
            return "the Nether";
        }
        if (world.getRegistryKey() == World.END) {
            return "the End";
        }
        return world.getRegistryKey() == World.OVERWORLD ? "the Overworld" : world.getRegistryKey().getValue().getPath();
    }

    public static String where(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /** "12 blocks north-east, 3 down" from one place to another. */
    public static String direction(Vec3d from, BlockPos to) {
        double dx = to.getX() + 0.5 - from.x;
        double dz = to.getZ() + 0.5 - from.z;
        int dy = MathHelper.floor(to.getY() - from.y);
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String vertical = dy > 2 ? ", " + dy + " up" : dy < -2 ? ", " + (-dy) + " down" : "";
        if (distance < 2) {
            return dy < -2 ? (-dy) + " blocks straight down" : dy > 2 ? dy + " blocks straight up" : "right here";
        }
        // Minecraft: +z is south, +x is east
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        String compass = names[Math.floorMod(Math.round(angle / 45.0), 8)];
        return distance + " blocks " + compass + vertical;
    }

    // --- brain memories --------------------------------------------------------------------------------------

    /** A remembered place (home, job site, meeting point) in this dimension, if the mob's brain has one. */
    @Nullable
    public static BlockPos remembered(LivingEntity mob, MemoryModuleType<GlobalPos> type) {
        Brain<?> brain = mob.getBrain();
        if (!brain.hasMemoryModule(type)) {
            return null;
        }
        Optional<GlobalPos> pos = brain.getOptionalMemory(type);
        return pos.filter(p -> p.getDimension() == mob.world.getRegistryKey()).map(GlobalPos::getPos).orElse(null);
    }

    /** Who this mob is angry at, from its brain (piglins, hoglins...) or its anger (wolves, golems, endermen...). */
    @Nullable
    public static Entity angryAt(LivingEntity mob) {
        if (!(mob.world instanceof ServerWorld world)) {
            return null;
        }
        Brain<?> brain = mob.getBrain();
        if (brain.hasMemoryModule(MemoryModuleType.ANGRY_AT)) {
            Optional<UUID> uuid = brain.getOptionalMemory(MemoryModuleType.ANGRY_AT);
            if (uuid.isPresent()) {
                return world.getEntity(uuid.get());
            }
        }
        return null;
    }

    // --- what it can see -------------------------------------------------------------------------------------

    public static String sees(LivingEntity mob, @Nullable LivingEntity host, @Nullable PlayerEntity rider) {
        List<LivingEntity> seen = mob.world.getEntitiesByClass(LivingEntity.class, mob.getBoundingBox().expand(20.0),
                e -> e != mob && e != rider && e.isAlive() && !e.isInvisible() && !e.isSpectator() && mob.canSee(e));
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> players = new ArrayList<>();
        boolean seesYou = false;
        seen.sort((a, b) -> Double.compare(a.squaredDistanceTo(mob), b.squaredDistanceTo(mob)));
        for (LivingEntity e : seen) {
            if (e == host) {
                seesYou = true;
            } else if (e instanceof PlayerEntity player) {
                players.add(player.getName().getString());
            } else {
                counts.merge(PossessedGrin.kind(e), 1, Integer::sum);
            }
        }
        List<String> parts = new ArrayList<>();
        if (seesYou) {
            parts.add("you");
        }
        for (String name : players) {
            parts.add("a person called " + name);
        }
        int shown = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (shown++ >= 4) {
                break;
            }
            parts.add(entry.getValue() == 1 ? article(entry.getKey()) + " " + entry.getKey() : entry.getValue() + " " + plural(entry.getKey()));
        }
        if (parts.isEmpty()) {
            return "Nothing. Nobody. Just the dark.";
        }
        if (parts.size() == 1 && seesYou) {
            return "Only you. Only ever you.";
        }
        return "I see " + join(parts) + ".";
    }

    // --- secrets ---------------------------------------------------------------------------------------------

    private record Find(String name, int rank) {
    }

    /** The most interesting hidden thing near the mob: a spawner, ancient debris, diamonds, a chest, emeralds, gold. */
    @Nullable
    public static String secret(LivingEntity mob) {
        World world = mob.world;
        BlockPos center = mob.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos best = null;
        int bestRank = 0;
        double bestDistance = Double.MAX_VALUE;
        String bestName = null;
        int r = 12;
        for (int dy = -16; dy <= 6; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!world.isChunkLoaded(pos)) {
                        continue;
                    }
                    Find find = rank(world.getBlockState(pos));
                    if (find == null) {
                        continue;
                    }
                    double distance = pos.getSquaredDistance(center);
                    if (find.rank() > bestRank || (find.rank() == bestRank && distance < bestDistance)) {
                        best = pos.toImmutable();
                        bestRank = find.rank();
                        bestDistance = distance;
                        bestName = find.name();
                    }
                }
            }
        }
        if (best == null) {
            return null;
        }
        boolean hidden = !world.isAir(best.up()) && best.getY() < center.getY();
        return "There's " + bestName + " " + direction(mob.getPos(), best) + " from here" + (hidden ? ", buried where you'd never look." : ".");
    }

    @Nullable
    private static Find rank(BlockState state) {
        if (state.isOf(Blocks.SPAWNER)) {
            return new Find("a monster spawner", 7);
        }
        if (state.isOf(Blocks.ANCIENT_DEBRIS)) {
            return new Find("ancient debris", 6);
        }
        if (state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            return new Find("diamond ore", 5);
        }
        if (state.isOf(Blocks.EMERALD_ORE) || state.isOf(Blocks.DEEPSLATE_EMERALD_ORE)) {
            return new Find("emerald ore", 4);
        }
        if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.BARREL) || state.isOf(Blocks.TRAPPED_CHEST)) {
            return new Find("a chest", 3);
        }
        if (state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DEEPSLATE_GOLD_ORE) || state.isOf(Blocks.NETHER_GOLD_ORE)) {
            return new Find("gold ore", 2);
        }
        return null;
    }

    // --- words -----------------------------------------------------------------------------------------------

    public static String itemName(ItemStack stack) {
        if (stack.isEmpty()) {
            return "nothing";
        }
        String name = stack.getName().getString();
        return stack.getCount() > 1 ? stack.getCount() + " " + name : article(name.toLowerCase(Locale.ROOT)) + " " + name;
    }

    public static String article(String word) {
        return !word.isEmpty() && "aeiou".indexOf(Character.toLowerCase(word.charAt(0))) >= 0 ? "an" : "a";
    }

    public static String plural(String word) {
        if (word.endsWith("sheep") || word.endsWith("fish") || word.endsWith("cod") || word.endsWith("salmon")) {
            return word;
        }
        if (word.endsWith("s") || word.endsWith("sh") || word.endsWith("ch")) {
            return word + "es";
        }
        if (word.endsWith("man")) {
            return word.substring(0, word.length() - 3) + "men";
        }
        if (word.endsWith("y") && !word.endsWith("ey") && !word.endsWith("ay")) {
            return word.substring(0, word.length() - 1) + "ies";
        }
        return word + "s";
    }

    public static String join(List<String> parts) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    public static String hearts(LivingEntity entity) {
        return Math.round(entity.getHealth() / 2.0F) + "/" + Math.round(entity.getMaxHealth() / 2.0F);
    }

    @Nullable
    public static String nameOf(@Nullable Entity entity, @Nullable MobEntity host, @Nullable PlayerEntity rider) {
        if (entity == null) {
            return null;
        }
        if (entity == host || entity == rider) {
            return "you";
        }
        if (entity instanceof PlayerEntity player) {
            return player.getName().getString();
        }
        return PossessedGrin.the(entity);
    }
}
