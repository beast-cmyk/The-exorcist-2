package com.possessedgrin.haunt;

import com.possessedgrin.voice.Witness;
import com.possessedgrin.PossessedGrin;
import com.possessedgrin.voice.Conversation;
import com.possessedgrin.voice.Intent;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerProfession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The exorcism.
 * <ul>
 *   <li><b>Holy water:</b> any splash bottle of water that bursts on a possessed creature burns it (3 damage,
 *       hissing smoke). Six burns in a row and the demon is cast out: you're forced out of the body. The count
 *       slowly fades if they stop.</li>
 *   <li><b>Exorcists:</b> cleric villagers recognise what you are. If one can see you within 14 blocks, it speaks
 *       the rite at you, commanding you to leave the body, and throws holy water every few seconds, even while the
 *       other villagers run. The longer the rite goes on, the faster they throw and the harder it burns.</li>
 *   <li><b>Breaking their faith:</b> taunt an exorcist (in chat or with the taunt key) and it falters: it stumbles
 *       over the rite and stops throwing for a while, and the rite loses some of its strength.</li>
 * </ul>
 * The full rite, with the old priest who comes out of the fog, is in {@link Rite}. Every word of the rite is
 * original.
 */
public final class Exorcism {
    private static final int BURNS_TO_CAST_OUT = 6;
    private static final int BURN_FADE_TICKS = 200;   // one burn forgotten every 10 seconds
    private static final float BURN_DAMAGE = 3.0F;
    private static final double EXORCIST_RANGE = 14.0;

    private static final int RITE_FADES_AFTER = 600;     // 30 s without the rite and it starts over
    private static final int FALTER_TICKS = 200;

    private static final Map<UUID, Integer> BURNS = new HashMap<>();
    private static final Map<UUID, Long> LAST_BURN = new HashMap<>();
    private static final Map<UUID, Long> EXORCIST_READY = new HashMap<>();
    /** Per exorcist: how far through the rite it is, and when it last spoke a line of it. */
    private static final Map<UUID, Integer> RITE = new HashMap<>();
    private static final Map<UUID, Long> RITE_SPOKEN = new HashMap<>();
    /** Per possessed player: the strongest rite being spoken against them right now. */
    private static final Map<UUID, Integer> RITE_POWER = new HashMap<>();
    private static final Map<UUID, Long> RITE_POWER_AT = new HashMap<>();

    /** The rite, spoken a line at a time with each throw. It escalates, then repeats its last commands. */
    private static final String[] RITE_LINES = {
            "I see you, spirit. I know what you are. Leave that {host}.",
            "You have no claim on this body. Its soul is not yours to keep.",
            "By the morning light and the bell of this village, I command you: depart.",
            "Every word you speak is a lie. I will not listen. Go back to the dark.",
            "Release the {host}! Release it! I command you!",
            "Begone, unclean thing! There is no place for you here!",
            "Out! Out of that body! Out of this village! Out!",
            "I am not afraid of you. Leave. Leave NOW."};
    private static final String[] REBUKES = {
            "I will not bargain with you.",
            "Speak all you like. The words of the rite are louder.",
            "You may wear that {host}, but you will not keep it.",
            "I hear you. I am not afraid of you.",
            "Silence! You have no voice here.",
            "That is the {host}'s mouth, but those are not its words."};
    private static final String[] FALTERS = {
            "*its hands shake* Your words are poison... no. No! I will not listen!",
            "*stumbles over the words of the rite*",
            "Lies. All of it lies. ...Isn't it?",
            "*the holy water trembles in its grip*",
            "How do you know that? How could you know that...?"};

    private Exorcism() {
    }

    /** A splash bottle of water just burst. Called from the potion itself. */
    public static void holyWater(PotionEntity splash) {
        if (!(splash.world instanceof ServerWorld world)) {
            return;
        }
        for (LivingEntity touched : world.getEntitiesByClass(LivingEntity.class, splash.getBoundingBox().expand(4.0, 2.0, 4.0),
                PossessedGrin::isPossessed)) {
            if (splash.squaredDistanceTo(touched) < 16.0 && PossessedGrin.possessorOf(touched) instanceof ServerPlayerEntity rider) {
                burn(world, touched, rider);
            }
        }
    }

    private static void burn(ServerWorld world, LivingEntity host, ServerPlayerEntity rider) {
        // The longer the rite has gone on, the more it burns
        host.damage(DamageSource.MAGIC, BURN_DAMAGE + Math.min(3, ritePower(rider, world.getTime()) / 2));
        Witness.seen(host, null, "You saw holy water burn " + PossessedGrin.the(host) + " like acid.");
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 1.2F, 0.6F);
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.ENTITY_GHAST_HURT, SoundCategory.HOSTILE, 0.8F, 0.5F);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, host.getX(), host.getBodyY(0.6), host.getZ(), 15, host.getWidth() / 2.0, host.getHeight() / 3.0, host.getWidth() / 2.0, 0.02);

        Rite.burned(rider);
        int burns = BURNS.getOrDefault(rider.getUuid(), 0) + 1;
        if (burns >= BURNS_TO_CAST_OUT) {
            castOut(world, host, rider);
            return;
        }
        BURNS.put(rider.getUuid(), burns);
        LAST_BURN.put(rider.getUuid(), world.getTime());
        rider.sendMessage(Text.literal("The holy water burns! ").formatted(Formatting.RED)
                .append(Text.literal("■".repeat(burns)).formatted(Formatting.GOLD))
                .append(Text.literal("■".repeat(BURNS_TO_CAST_OUT - burns)).formatted(Formatting.DARK_GRAY)), true);
        Haunting.shake(rider, 0.9F, 15);
    }

    static void castOut(ServerWorld world, LivingEntity host, ServerPlayerEntity rider) {
        BURNS.remove(rider.getUuid());
        LAST_BURN.remove(rider.getUuid());
        PossessionComponent.get(rider).stopPossessing();

        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 1.5F, 0.6F);
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 0.6F, 1.4F);
        world.spawnParticles(ParticleTypes.SOUL, host.getX(), host.getBodyY(0.5), host.getZ(), 60, 0.6, 1.0, 0.6, 0.08);
        world.spawnParticles(ParticleTypes.END_ROD, host.getX(), host.getBodyY(0.5), host.getZ(), 40, 0.5, 1.0, 0.5, 0.15);
        host.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1));
        rider.sendMessage(Text.literal("You have been cast out!").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
        Haunting.shake(rider, 2.0F, 40);
    }

    public static void tick(ServerWorld world) {
        long now = world.getTime();
        if (now % 20 == 0) {
            LAST_BURN.entrySet().removeIf(entry -> {
                if (now - entry.getValue() < BURN_FADE_TICKS) {
                    return false;
                }
                int left = BURNS.getOrDefault(entry.getKey(), 0) - 1;
                if (left <= 0) {
                    BURNS.remove(entry.getKey());
                    return true;
                }
                BURNS.put(entry.getKey(), left);
                entry.setValue(now);
                return false;
            });
        }
        if (now % 10 != 0) {
            return;
        }
        for (ServerPlayerEntity rider : world.getPlayers()) {
            MobEntity host = PossessionComponent.getHost(rider);
            if (host == null) {
                continue;
            }
            for (VillagerEntity exorcist : world.getEntitiesByClass(VillagerEntity.class, host.getBoundingBox().expand(EXORCIST_RANGE),
                    v -> v != host && v.isAlive() && !v.isSleeping() && isExorcist(v))) {
                if (now < EXORCIST_READY.getOrDefault(exorcist.getUuid(), 0L)) {
                    continue;
                }
                double distance = exorcist.distanceTo(host);
                if (distance < 2.5 || distance > EXORCIST_RANGE || !exorcist.canSee(host)) {
                    continue;
                }
                throwHolyWater(world, exorcist, host);
                int step = speakRite(world, exorcist, host, rider, now);
                // The rite builds: it throws faster the further it gets
                int wait = Math.max(20, 50 - step * 5) + world.getRandom().nextInt(Math.max(10, 30 - step * 3));
                EXORCIST_READY.put(exorcist.getUuid(), now + wait);
            }
        }
        if (now % 1200 == 0) {
            EXORCIST_READY.values().removeIf(ready -> ready < now - 1200);
            RITE_SPOKEN.entrySet().removeIf(entry -> {
                boolean stale = now - entry.getValue() > RITE_FADES_AFTER;
                if (stale) {
                    RITE.remove(entry.getKey());
                }
                return stale;
            });
        }
    }

    // --- the rite --------------------------------------------------------------------------------------------

    public static boolean isExorcist(LivingEntity entity) {
        return entity instanceof VillagerEntity villager && !villager.isBaby() && !PossessedGrin.isPossessed(villager)
                && villager.getVillagerData().getProfession() == VillagerProfession.CLERIC
                && !villager.getScoreboardTags().contains(Rite.SHATTERED_TAG);
    }

    /** The exorcist speaks the next line of the rite. Returns how far into the rite it is. */
    private static int speakRite(ServerWorld world, VillagerEntity exorcist, MobEntity host, ServerPlayerEntity rider, long now) {
        UUID id = exorcist.getUuid();
        Long spoken = RITE_SPOKEN.get(id);
        int step = spoken == null || now - spoken > RITE_FADES_AFTER ? 1 : RITE.getOrDefault(id, 0) + 1;
        RITE.put(id, step);
        RITE_SPOKEN.put(id, now);
        int index = step <= RITE_LINES.length ? step - 1 : RITE_LINES.length - 3 + world.getRandom().nextInt(3);
        Conversation.speak(rider, host, exorcist, RITE_LINES[index], 0);

        int before = ritePower(rider, now);
        RITE_POWER.put(rider.getUuid(), Math.max(before, step));
        RITE_POWER_AT.put(rider.getUuid(), now);
        if (before < 3 && step >= 3) {
            rider.sendMessage(Text.literal("The rite grows stronger. The holy water burns hotter.").formatted(Formatting.GOLD, Formatting.ITALIC), true);
        } else if (before < 6 && step >= 6) {
            rider.sendMessage(Text.literal("The rite is at full strength. You can feel it pulling you out.").formatted(Formatting.GOLD, Formatting.BOLD), true);
        }
        return step;
    }

    private static int ritePower(PlayerEntity rider, long now) {
        Long at = RITE_POWER_AT.get(rider.getUuid());
        if (at == null || now - at > RITE_FADES_AFTER) {
            RITE_POWER.remove(rider.getUuid());
            RITE_POWER_AT.remove(rider.getUuid());
            return 0;
        }
        return RITE_POWER.getOrDefault(rider.getUuid(), 0);
    }

    /** An exorcist overheard the demon speak: it answers, and readies the holy water. */
    public static String rebuke(MobEntity exorcist, MobEntity host) {
        EXORCIST_READY.remove(exorcist.getUuid());
        return REBUKES[exorcist.getRandom().nextInt(REBUKES.length)];
    }

    /** You spoke to an exorcist directly. Taunts shake its faith; nothing else moves it. */
    public static String answer(MobEntity exorcist, MobEntity host, Intent intent, Random random) {
        if (intent == Intent.TAUNT) {
            falter(exorcist, host);
            return FALTERS[random.nextInt(FALTERS.length)];
        }
        if (intent.isCommand()) {
            return "I serve a higher power than you.";
        }
        if (intent == Intent.APOLOGY) {
            return "If you are sorry, then leave. Leave the {host} now.";
        }
        return rebuke(exorcist, host);
    }

    /** Its faith wavers: it stops throwing for a while, and the rite loses ground. */
    private static void falter(MobEntity exorcist, MobEntity host) {
        Rite.shaken(exorcist, 10.0F, "It mocked you to your face, and it knew exactly where to cut.", null);
        long now = exorcist.world.getTime();
        EXORCIST_READY.put(exorcist.getUuid(), now + FALTER_TICKS);
        RITE.computeIfPresent(exorcist.getUuid(), (id, step) -> Math.max(0, step - 2));
        PlayerEntity rider = PossessedGrin.possessorOf(host);
        if (rider != null) {
            RITE_POWER.computeIfPresent(rider.getUuid(), (id, power) -> Math.max(0, power - 2));
        }
        if (exorcist.world instanceof ServerWorld world) {
            world.sendEntityStatus(exorcist, (byte) 42);     // a frightened sweat
        }
    }

    /** Thrown the same way a witch throws her potions, aimed slightly ahead of a moving target. */
    private static void throwHolyWater(ServerWorld world, VillagerEntity exorcist, LivingEntity target) {
        exorcist.getLookControl().lookAt(target, 30.0F, 30.0F);
        Vec3d motion = target.getVelocity();
        double dx = target.getX() + motion.x - exorcist.getX();
        double dy = target.getEyeY() - 1.1 - exorcist.getY();
        double dz = target.getZ() + motion.z - exorcist.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);

        PotionEntity holyWater = new PotionEntity(world, exorcist);
        holyWater.setItem(PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.WATER));
        holyWater.setPitch(holyWater.getPitch() + 20.0F);
        holyWater.setVelocity(dx, dy + flat * 0.2, dz, 0.75F, 8.0F);
        world.spawnEntity(holyWater);

        world.playSound(null, exorcist.getX(), exorcist.getY(), exorcist.getZ(), SoundEvents.ENTITY_WITCH_THROW, SoundCategory.NEUTRAL, 1.0F, 0.8F);
        world.playSound(null, exorcist.getX(), exorcist.getY(), exorcist.getZ(), SoundEvents.ENTITY_VILLAGER_WORK_CLERIC, SoundCategory.NEUTRAL, 1.0F, 0.7F);
        world.spawnParticles(ParticleTypes.END_ROD, exorcist.getX(), exorcist.getBodyY(0.8), exorcist.getZ(), 5, 0.3, 0.3, 0.3, 0.02);
    }

    /** Lets other code know if someone is close to being cast out (used for nothing else yet). */
    public static int burnsOn(PlayerEntity rider) {
        return BURNS.getOrDefault(rider.getUuid(), 0);
    }
}
