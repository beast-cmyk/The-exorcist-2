package com.possessedgrin.stage;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.voice.HostSoul;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

/**
 * The demon feeds on the body it wears. From the moment you enter it you draw its strength into yourself: your
 * hunger refills every ten seconds and you never starve. But a body can only give so much. After about 20 minutes
 * (one Minecraft day) it is spent: it wastes, then fails, then dies. You slip out just before the end, so its death
 * costs you nothing: no Attrition.
 */
public final class Feeding {
    /** Percent of the body's life drawn, at which you're told how it's holding up. */
    private static final int[] MILESTONES = {1, 50, 75, 90, 97};
    private static final int FEED_INTERVAL = 200;      // 10 seconds

    /** Per possessed mob: the last milestone announced. Server thread only. */
    private static final Map<Integer, Integer> REACHED = new HashMap<>();

    private Feeding() {
    }

    static void forget(int hostId) {
        REACHED.remove(hostId);
    }

    static void clear() {
        REACHED.clear();
    }

    /** Called every tick for each possessing player. Returns false if the body just died. */
    static boolean tick(ServerPlayerEntity player, MobEntity host, long elapsed, long now) {
        // Feed: the body's strength becomes yours
        if (now % FEED_INTERVAL == 0) {
            player.getHungerManager().add(1, 0.5F);
        }

        float drain = Math.min(1.0F, elapsed / (float) Stages.LIFETIME);
        int percent = (int) (drain * 100.0F);
        int reached = REACHED.getOrDefault(host.getId(), -1);
        for (int i = reached + 1; i < MILESTONES.length; i++) {
            if (percent < MILESTONES[i]) {
                break;
            }
            REACHED.put(host.getId(), i);
            announce(player, host, i, elapsed);
        }

        // The body falters as it runs out
        if (now % 100 == 0) {
            if (drain >= 0.75F) {
                weaken(player, host, StatusEffects.SLOWNESS, drain >= 0.9F ? 1 : 0);
            }
            if (drain >= 0.9F) {
                weaken(player, host, StatusEffects.WEAKNESS, 0);
                host.world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                        SoundCategory.HOSTILE, 0.8F, 0.4F);
            }
        }

        if (drain >= 1.0F) {
            die(player, host);
            return false;
        }
        return true;
    }

    private static void weaken(ServerPlayerEntity player, MobEntity host, StatusEffect effect, int amplifier) {
        // On both, so it's felt however Requiem shares effects between you and the body
        host.addStatusEffect(new StatusEffectInstance(effect, 120, amplifier, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(effect, 120, amplifier, false, false, true));
    }

    private static void announce(ServerPlayerEntity player, MobEntity host, int milestone, long elapsed) {
        long minutesLeft = Math.max(1L, (Stages.LIFETIME - elapsed + 600L) / 1200L);
        String body = PossessedGrin.the(host);
        String message = switch (milestone) {
            case 0 -> "You feed on " + body + ". Its strength flows into you; you'll never go hungry inside it. But it won't last forever: about "
                    + minutesLeft + " minutes.";
            case 1 -> "The body is wasting, grey and sunken. You've drawn half of its life. About " + minutesLeft + " minutes left.";
            case 2 -> "The body is rotting. The skin is coming away from it in patches. About " + minutesLeft + " minutes left.";
            case 3 -> "There is more bone than body now. Its heart stumbles. About " + minutesLeft + (minutesLeft == 1 ? " minute" : " minutes") + " left.";
            default -> "Seconds left. The body is coming apart around you.";
        };
        player.sendMessage(Text.literal(message).formatted(milestone >= 3 ? Formatting.DARK_RED : Formatting.DARK_GRAY, Formatting.ITALIC), false);
        if (milestone == 1 || milestone == 2) {
            HostSoul.drained(player, host);
        }
    }

    /** The body is spent. You slip out first, so its death is not yours. */
    private static void die(ServerPlayerEntity player, MobEntity host) {
        ServerWorld world = (ServerWorld) host.world;
        HostSoul.lastWords(player, host);
        StageDirector.title(player,
                Text.literal("The body is spent").formatted(Formatting.DARK_RED),
                Text.literal("You slip out as " + PossessedGrin.the(host) + " crumbles. It costs you nothing.").formatted(Formatting.GRAY, Formatting.ITALIC));
        StageDirector.spent(player.getUuid());

        // Leave first: the body dies empty, so Requiem never counts it as your death (no Attrition)
        PossessionComponent.get(player).stopPossessing();

        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 0.5F, 1.6F);
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.HOSTILE, 1.5F, 0.5F);
        world.spawnParticles(ParticleTypes.SOUL, host.getX(), host.getBodyY(0.5), host.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, host.getX(), host.getBodyY(0.5), host.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
        world.spawnParticles(StageDirector.BLOOD, host.getX(), host.getBodyY(0.7), host.getZ(), 30, 0.4, 0.5, 0.4, 0.0);

        // What's left of it falls in on itself: a heap of bones and rags
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.ENTITY_SKELETON_DEATH, SoundCategory.HOSTILE, 1.0F, 0.6F);
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.BLOCK_BONE_BLOCK_BREAK, SoundCategory.HOSTILE, 1.2F, 0.7F);
        world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.BONE)),
                host.getX(), host.getBodyY(0.5), host.getZ(), 30, host.getWidth() * 0.4, host.getHeight() * 0.3, host.getWidth() * 0.4, 0.08);
        int bones = 2 + world.getRandom().nextInt(3) + (int) Math.min(4.0F, host.getHeight() * host.getWidth() * 2.0F);
        host.dropStack(new ItemStack(Items.BONE, bones));
        if (world.getRandom().nextBoolean()) {
            host.dropStack(new ItemStack(Items.ROTTEN_FLESH, 1 + world.getRandom().nextInt(2)));
        }

        host.damage(DamageSource.STARVE, Float.MAX_VALUE);
        if (host.isAlive()) {
            host.kill();        // bosses that shrug off starving (the Ender Dragon) still go
        }
        REACHED.remove(host.getId());
    }
}
