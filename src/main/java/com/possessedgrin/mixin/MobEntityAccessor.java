package com.possessedgrin.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Telepathy reads what a mob is doing straight from its running goals. */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {
    @Accessor("goalSelector")
    GoalSelector possessedgrin$getGoalSelector();

    @Accessor("targetSelector")
    GoalSelector possessedgrin$getTargetSelector();
}
