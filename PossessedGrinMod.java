package com.possessedgrin;

import com.possessedgrin.haunt.DemonIdolItem;
import com.possessedgrin.haunt.OuijaBoardItem;
import com.possessedgrin.power.PossessedPowers;
import com.possessedgrin.stage.StageDirector;
import com.possessedgrin.voice.AiVoice;
import com.possessedgrin.voice.Conversation;
import com.possessedgrin.voice.Kinship;
import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Rarity;
import net.minecraft.util.registry.Registry;

/**
 * Common (client + server) start-up: the Ouija board, the idol, the possession powers, the stages of a possession,
 * the voices of the creatures, and the bond between a body and its kin.
 */
public final class PossessedGrinMod implements ModInitializer {
    public static final Item OUIJA_BOARD = new OuijaBoardItem(new Item.Settings().maxCount(1).group(ItemGroup.MISC));
    public static final Item DEMON_IDOL = new DemonIdolItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON).group(ItemGroup.MISC));

    @Override
    public void onInitialize() {
        Registry.register(Registry.ITEM, PossessedGrin.id("ouija_board"), OUIJA_BOARD);
        Registry.register(Registry.ITEM, PossessedGrin.id("demon_idol"), DEMON_IDOL);
        DemonIdolItem.init(DEMON_IDOL);
        PossessedPowers.init();
        StageDirector.init();
        Kinship.init();
        Conversation.init();
        AiVoice.init();
    }
}
