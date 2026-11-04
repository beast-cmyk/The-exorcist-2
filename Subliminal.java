package com.possessedgrin.haunt;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.stage.Stages;
import com.possessedgrin.voice.Conversation;
import com.possessedgrin.voice.Witness;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The thing inside shows its own face, for a few frames, then it's gone. From stage III it happens on its own every
 * so often (more often with the idol). Every creature that was looking at the body in that instant panics, runs,
 * and never forgets it.
 */
public final class Subliminal {
    private static final int FRAMES = 4;
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private Subliminal() {
    }

    public static void tick(ServerWorld world, Item idol) {
        long now = world.getTime();
        for (ServerPlayerEntity player : world.getPlayers()) {
            MobEntity host = PossessionComponent.getHost(player);
            if (host == null || Stages.of(host) < Stages.POSSESSION) {
                NEXT.remove(player.getUuid());
                continue;
            }
            boolean idolCarried = DemonIdolItem.carries(player, idol);
            long next = NEXT.computeIfAbsent(player.getUuid(), id -> now + 300 + world.getRandom().nextInt(600));
            if (now >= next) {
                flash(world, host, player, false);
                NEXT.put(player.getUuid(), now + (idolCarried ? 300 : 700) + world.getRandom().nextInt(idolCarried ? 400 : 900));
            }
        }
    }

    /** Shows the true face now. {@code called}: you made it happen (the idol), so you're told. */
    public static void flash(ServerWorld world, MobEntity host, ServerPlayerEntity player, boolean called) {
        Fx.send(host, Fx.TRUE_FACE, FRAMES + world.getRandom().nextInt(3));
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 0.35F, 0.45F);
        world.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.ENTITY_PHANTOM_BITE, SoundCategory.HOSTILE, 0.6F, 0.4F);
        int saw = 0;
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, host.getBoundingBox().expand(14.0),
                m -> m != host && m.isAlive() && !PossessedGrin.isPossessed(m) && m.canSee(host) && facing(m, host))) {
            saw++;
            Witness.record(mob, "For an instant " + PossessedGrin.the(host) + " had another face. White, split, grinning. Its real face.");
            if (!(mob instanceof Monster)) {
                Conversation.flee(mob, host, 10.0, 1.0);
            }
            Rite.shaken(mob, 12, "For an instant the body wore another face, its real one: ash-white, split with black cracks, grinning.",
                    "*stares* Its face... did you see its face?");
        }
        if (called || saw > 0) {
            player.sendMessage(Text.literal(saw == 0 ? "You let your true face show, for an instant."
                    : "Your true face shows for an instant. " + (saw == 1 ? "One of them" : saw + " of them") + " saw it.")
                    .formatted(Formatting.DARK_RED, Formatting.ITALIC), true);
        }
    }

    /** Is the mob looking roughly at the body? */
    private static boolean facing(MobEntity mob, MobEntity host) {
        var look = mob.getRotationVec(1.0F);
        var to = host.getEyePos().subtract(mob.getEyePos()).normalize();
        return look.dotProduct(to) > 0.5;
    }
}
