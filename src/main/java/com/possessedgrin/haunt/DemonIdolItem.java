package com.possessedgrin.haunt;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.stage.Stages;
import com.possessedgrin.voice.Conversation;
import com.possessedgrin.voice.Witness;
import ladysnake.requiem.api.v1.possession.PossessionComponent;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The idol: a small, very old statuette of something winged, dug out of the sand. It belongs to you.
 * <ul>
 *   <li>Found in desert temple chests (about one chest in three).</li>
 *   <li>Carry it and every possession takes hold half again as fast.</li>
 *   <li>Clerics can feel it. They keep their distance from whoever carries it, and during a rite it eats at the
 *       priests' faith.</li>
 *   <li>Use it as a soul to hear it call you (a short séance). Use it inside a body to let the thing inside show
 *       its face for an instant; everyone who sees it runs.</li>
 * </ul>
 */
public class DemonIdolItem extends Item {
    private static final Identifier DESERT_TEMPLE = new Identifier("minecraft", "chests/desert_pyramid");
    private static final int USE_COOLDOWN = 600;

    public DemonIdolItem(Settings settings) {
        super(settings);
    }

    public static void init(Item idol) {
        LootTableEvents.MODIFY.register((resources, loot, id, table, source) -> {
            if (source.isBuiltin() && DESERT_TEMPLE.equals(id)) {
                table.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1.0F))
                        .conditionally(RandomChanceLootCondition.builder(0.33F))
                        .with(ItemEntry.builder(idol)));
            }
        });
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            MobEntity host = PossessionComponent.getHost(player);
            ServerWorld serverWorld = (ServerWorld) world;
            serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.AMBIENT, 1.2F, 0.5F);
            serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT, 0.4F, 0.5F);
            if (host == null) {
                player.sendMessage(Text.literal("The idol is warm in your hand. Far away, dogs begin to howl. It knows you.")
                        .formatted(Formatting.GOLD, Formatting.ITALIC), false);
                Haunting.startSeance(player);
            } else {
                Subliminal.flash(serverWorld, host, player, true);
            }
            serverWorld.spawnParticles(ParticleTypes.ASH, player.getX(), player.getBodyY(0.6), player.getZ(), 40, 1.5, 1.0, 1.5, 0.01);
        }
        user.getItemCooldownManager().set(this, USE_COOLDOWN);
        return TypedActionResult.success(stack, world.isClient);
    }

    /** Does this player have the idol on them? */
    public static boolean carries(PlayerEntity player, Item idol) {
        if (player.getMainHandStack().isOf(idol) || player.getOffHandStack().isOf(idol)) {
            return true;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isOf(idol)) {
                return true;
            }
        }
        return false;
    }

    /** Every second: clerics near the idol back away from it, unless they are in the middle of a rite. */
    public static void tick(ServerWorld world, Item idol) {
        if (world.getTime() % 20 != 7) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!carries(player, idol)) {
                continue;
            }
            MobEntity host = PossessionComponent.getHost(player);
            Entity center = host != null ? host : player;
            for (VillagerEntity cleric : world.getEntitiesByClass(VillagerEntity.class, center.getBoundingBox().expand(10.0),
                    v -> Exorcism.isExorcist(v) && !Rite.isPriest(v))) {
                Conversation.flee(cleric, center, 7.0, 0.7);
                if (world.getRandom().nextInt(4) == 0) {
                    Witness.record(cleric, "You felt something old and cold near " + (host != null ? PossessedGrin.the(host) : "a stranger")
                            + ", like an idol that had been buried for a reason.");
                }
            }
            if (host != null && Stages.of(host) >= Stages.OPPRESSION && world.getRandom().nextInt(6) == 0) {
                world.spawnParticles(ParticleTypes.ASH, host.getX(), host.getBodyY(0.5), host.getZ(), 4, 0.4, 0.5, 0.4, 0.0);
            }
        }
    }
}
