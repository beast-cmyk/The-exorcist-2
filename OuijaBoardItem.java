package com.possessedgrin.haunt;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The Ouija board. Right-click to hold a one-minute séance: the haunting starts around you (knocks, whispers,
 * dying lights, cold breath), any creature at all can be possessed while it lasts, and possession powers
 * recharge twice as fast. Can be used again once the séance is over.
 */
public class OuijaBoardItem extends Item {
    public OuijaBoardItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            Haunting.startSeance(player);
        }
        user.getItemCooldownManager().set(this, Haunting.SEANCE_TICKS);
        return TypedActionResult.success(stack, world.isClient);
    }
}
