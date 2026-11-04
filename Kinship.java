package com.possessedgrin.voice;

import com.possessedgrin.PossessedGrin;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blood is thicker than demons. Strike one of your body's own kind and it won't fight back: it backs away hurt and
 * confused, then comes back to help you (heals you, brings you something). Its kin never turn on your body either,
 * unless you taunt them into it.
 */
public final class Kinship {
    private static final int RETREAT_TICKS = 60;
    private static final int GIVE_UP_TICKS = 400;
    private static final int HELP_COOLDOWN = 600;
    private static final int PROVOKE_TICKS = 400;
    private static final double CALM_RADIUS = 24.0;

    /** Families of mobs that count as the same kind. Everything else only counts its own type. */
    private static final Map<String, String> FAMILY = new HashMap<>();

    static {
        family("zombie", "minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager", "minecraft:giant");
        family("skeleton", "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton");
        family("illager", "minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:illusioner");
        family("piglin", "minecraft:piglin", "minecraft:piglin_brute", "requiem:cured_piglin", "requiem:cured_piglin_brute");
        family("villager", "minecraft:villager", "minecraft:wandering_trader", "requiem:cured_villager");
        family("spider", "minecraft:spider", "minecraft:cave_spider");
        family("horse", "minecraft:horse", "minecraft:donkey", "minecraft:mule");
        family("llama", "minecraft:llama", "minecraft:trader_llama");
    }

    private static final class Calm {
        final UUID host;
        final ServerPlayerEntity rider;
        final long retreatUntil;
        final long giveUpAt;
        final boolean willHelp;

        Calm(UUID host, ServerPlayerEntity rider, long now, boolean willHelp) {
            this.host = host;
            this.rider = rider;
            this.retreatUntil = now + RETREAT_TICKS;
            this.giveUpAt = now + GIVE_UP_TICKS;
            this.willHelp = willHelp;
        }
    }

    private static final Map<UUID, Calm> CALMING = new HashMap<>();
    private static final Map<UUID, Long> HELP_READY = new HashMap<>();
    private static final Map<UUID, Long> PROVOKED_UNTIL = new HashMap<>();

    private Kinship() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(Kinship::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CALMING.clear();
            HELP_READY.clear();
            PROVOKED_UNTIL.clear();
        });
    }

    private static void family(String name, String... ids) {
        for (String id : ids) {
            FAMILY.put(id, name);
        }
    }

    public static boolean sameKind(Entity a, Entity b) {
        String ka = familyOf(a.getType());
        return ka.equals(familyOf(b.getType()));
    }

    private static String familyOf(EntityType<?> type) {
        String id = EntityType.getId(type).toString();
        return FAMILY.getOrDefault(id, id);
    }

    /** The possessed body behind this entity: the entity itself, or the body a player is wearing. */
    @Nullable
    private static MobEntity bodyOf(@Nullable Entity entity) {
        if (entity instanceof MobEntity mob && PossessedGrin.isPossessed(mob)) {
            return mob;
        }
        if (entity instanceof PlayerEntity player) {
            return PossessionComponent.getHost(player);
        }
        return null;
    }

    /** Taunting your kin breaks the bond for a while: they're allowed to fight you. */
    public static void provoke(MobEntity mob) {
        PROVOKED_UNTIL.put(mob.getUuid(), mob.world.getTime() + PROVOKE_TICKS);
    }

    private static boolean provoked(MobEntity mob) {
        Long until = PROVOKED_UNTIL.get(mob.getUuid());
        return until != null && mob.world.getTime() < until;
    }

    /** Called before a mob picks a target. True: it won't target this possessed body of its own kind. */
    public static boolean spares(MobEntity mob, LivingEntity target) {
        if (mob.world.isClient || PossessedGrin.isPossessed(mob)) {
            return false;
        }
        MobEntity body = bodyOf(target);
        return body != null && body != mob && sameKind(mob, body) && !provoked(mob);
    }

    /** Called after any mob is hurt. */
    public static void onHurt(LivingEntity victim, DamageSource source) {
        if (victim.world.isClient || !(victim instanceof MobEntity mob) || PossessedGrin.isPossessed(mob) || !mob.isAlive()) {
            return;
        }
        MobEntity body = bodyOf(source.getAttacker());
        if (body == null || body == mob || !sameKind(mob, body) || provoked(mob)) {
            return;
        }
        PlayerEntity possessor = PossessedGrin.possessorOf(body);
        if (!(possessor instanceof ServerPlayerEntity rider)) {
            return;
        }
        pacify(mob);
        long now = mob.world.getTime();
        boolean fresh = !CALMING.containsKey(mob.getUuid());
        boolean willHelp = now >= HELP_READY.getOrDefault(mob.getUuid(), 0L);
        CALMING.put(mob.getUuid(), new Calm(body.getUuid(), rider, now, willHelp));
        if (fresh) {
            Conversation.speak(rider, body, mob, Persona.of(mob).line(Persona.Topic.KIN_HURT, mob.getRandom()), 4);
        }
    }

    /** Stop fighting: forget who hurt you, who you were angry at, who you were after. */
    public static void pacify(MobEntity mob) {
        mob.setTarget(null);
        mob.setAttacker(null);
        Brain<?> brain = mob.getBrain();
        brain.forget(MemoryModuleType.ATTACK_TARGET);
        brain.forget(MemoryModuleType.ANGRY_AT);
        brain.forget(MemoryModuleType.HURT_BY);
        brain.forget(MemoryModuleType.HURT_BY_ENTITY);
        if (mob instanceof Angerable angerable) {
            angerable.stopAnger();
        }
    }

    private static void tick(ServerWorld world) {
        long now = world.getTime();
        Iterator<Map.Entry<UUID, Calm>> it = CALMING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Calm> entry = it.next();
            Calm calm = entry.getValue();
            if (calm.rider.world != world) {
                continue;
            }
            Entity found = world.getEntity(entry.getKey());
            Entity hostEntity = world.getEntity(calm.host);
            if (!(found instanceof MobEntity mob) || !mob.isAlive() || !(hostEntity instanceof MobEntity host)
                    || !host.isAlive() || now > calm.giveUpAt) {
                it.remove();
                continue;
            }
            if (now % 10 == 0) {
                pacify(mob);
            }
            if (now < calm.retreatUntil) {
                if (now % 5 == 0) {
                    retreat(mob, host);
                }
            } else if (!calm.willHelp) {
                it.remove();        // helped you not long ago; it keeps its distance this time
            } else if (mob.squaredDistanceTo(host) > 6.25) {
                if (now % 10 == 0) {
                    walkTo(mob, host.getPos(), host, 1.0);
                }
            } else {
                help(world, mob, host, calm.rider);
                HELP_READY.put(mob.getUuid(), now + HELP_COOLDOWN);
                it.remove();
            }
        }
        if (now % 10 == 0) {
            calmKin(world);
        }
        if (now % 1200 == 0) {
            PROVOKED_UNTIL.values().removeIf(until -> until < now);
            HELP_READY.values().removeIf(until -> until < now);
        }
    }

    /** Mobs of your body's kind that somehow set their sights on it (pack anger, piglin alarms...) think again. */
    private static void calmKin(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            MobEntity host = PossessionComponent.getHost(player);
            if (host == null) {
                continue;
            }
            List<MobEntity> kin = world.getEntitiesByClass(MobEntity.class, host.getBoundingBox().expand(CALM_RADIUS),
                    m -> m != host && m.isAlive() && sameKind(m, host) && !PossessedGrin.isPossessed(m));
            for (MobEntity mob : kin) {
                LivingEntity target = mob.getTarget();
                if ((target == host || target == player) && !provoked(mob)) {
                    pacify(mob);
                }
            }
        }
    }

    private static void retreat(MobEntity mob, MobEntity host) {
        Vec3d away = mob.getPos().subtract(host.getPos());
        away = new Vec3d(away.x, 0.0, away.z);
        if (away.lengthSquared() < 1.0E-4) {
            away = new Vec3d(mob.getRandom().nextDouble() - 0.5, 0.0, mob.getRandom().nextDouble() - 0.5);
        }
        Vec3d to = mob.getPos().add(away.normalize().multiply(8.0));
        walkTo(mob, to, null, 1.3);
    }

    private static void walkTo(MobEntity mob, Vec3d to, @Nullable Entity entity, double speed) {
        mob.getNavigation().startMovingTo(to.x, to.y, to.z, speed);
        // Brain-driven mobs (villagers, piglins) walk where their brain says
        Brain<?> brain = mob.getBrain();
        if (entity != null) {
            brain.remember(MemoryModuleType.WALK_TARGET, new WalkTarget(entity, (float) (speed * 0.6), 2));
        } else {
            brain.remember(MemoryModuleType.WALK_TARGET, new WalkTarget(to, (float) (speed * 0.6), 1));
        }
    }

    private static void help(ServerWorld world, MobEntity mob, MobEntity host, ServerPlayerEntity rider) {
        mob.getLookControl().lookAt(host, 30.0F, 30.0F);
        mob.getNavigation().stop();
        host.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
        world.spawnParticles(ParticleTypes.HEART, host.getX(), host.getEyeY() + 0.4, host.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
        ItemStack gift = gift(mob);
        if (!gift.isEmpty()) {
            mob.dropStack(gift);
        }
        Conversation.speak(rider, host, mob, Persona.of(mob).line(Persona.Topic.KIN_HELP, mob.getRandom()), 0);
        Witness.record(mob, "You helped the possessed body, because it's one of your own.");
    }

    private static ItemStack gift(MobEntity mob) {
        Identifier id = EntityType.getId(mob.getType());
        String family = familyOf(mob.getType());
        if (mob instanceof EndermanEntity enderman && enderman.getCarriedBlock() != null) {
            ItemStack block = new ItemStack(enderman.getCarriedBlock().getBlock().asItem());
            enderman.setCarriedBlock(null);
            return block;
        }
        if (mob instanceof CatEntity) {
            return new ItemStack(Items.FEATHER);
        }
        return switch (family) {
            case "villager" -> new ItemStack(Items.BREAD, 2);
            case "zombie" -> new ItemStack(Items.ROTTEN_FLESH);
            case "skeleton" -> new ItemStack(Items.BONE, 2);
            case "piglin" -> new ItemStack(Items.GOLD_NUGGET, 3);
            case "illager" -> new ItemStack(Items.ARROW, 4);
            case "spider" -> new ItemStack(Items.STRING, 2);
            default -> switch (id.getPath()) {
                case "witch" -> PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.HEALING);
                case "iron_golem" -> new ItemStack(Items.POPPY);
                case "creeper" -> new ItemStack(Items.GUNPOWDER);
                case "chicken" -> new ItemStack(Items.EGG);
                case "sheep" -> new ItemStack(Items.WHITE_WOOL);
                case "pig" -> new ItemStack(Items.CARROT);
                case "wolf" -> new ItemStack(Items.BONE);
                case "bee" -> new ItemStack(Items.HONEYCOMB);
                case "snow_golem" -> new ItemStack(Items.SNOWBALL, 4);
                case "blaze" -> new ItemStack(Items.BLAZE_ROD);
                case "slime" -> new ItemStack(Items.SLIME_BALL);
                default -> ItemStack.EMPTY;
            };
        };
    }

    /** For mob chat: is this mob family to the body you're wearing? */
    public static boolean isKin(MobEntity mob, @Nullable MobEntity host) {
        return host != null && host != mob && sameKind(mob, host);
    }

    public static void unprovoke(MobEntity mob) {
        PROVOKED_UNTIL.remove(mob.getUuid());
    }

    /** You asked your kin for help: if it hasn't helped you lately, it comes straight over. */
    public static boolean comeHelp(MobEntity mob, MobEntity host, ServerPlayerEntity rider) {
        long now = mob.world.getTime();
        if (now < HELP_READY.getOrDefault(mob.getUuid(), 0L)) {
            return false;
        }
        CALMING.put(mob.getUuid(), new Calm(host.getUuid(), rider, now - RETREAT_TICKS, true));   // no backing away first
        return true;
    }
}
