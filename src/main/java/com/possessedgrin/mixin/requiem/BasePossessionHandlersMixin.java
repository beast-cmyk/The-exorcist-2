package com.possessedgrin.mixin.requiem;

import com.possessedgrin.BossPossession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-installs the boss rules right after Requiem registers its own possession rules, so ours always win
 * regardless of which mod's plugin loads first.
 */
@Pseudo
@Mixin(targets = "ladysnake.requiem.common.remnant.BasePossessionHandlers", remap = false)
public abstract class BasePossessionHandlersMixin {
    @Inject(method = "register", at = @At("TAIL"), require = 0)
    private static void possessedgrin$allowBosses(CallbackInfo ci) {
        BossPossession.install();
    }
}
