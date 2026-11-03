package com.possessedgrin.mixin.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AnimalModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the face decal find an animal's head, and size it correctly on babies. */
@Mixin(AnimalModel.class)
public interface AnimalModelAccessor {
    @Invoker("getHeadParts")
    Iterable<ModelPart> possessedgrin$getHeadParts();

    @Accessor("headScaled")
    boolean possessedgrin$isHeadScaled();

    @Accessor("childHeadYOffset")
    float possessedgrin$getChildHeadYOffset();

    @Accessor("childHeadZOffset")
    float possessedgrin$getChildHeadZOffset();

    @Accessor("invertedChildHeadScale")
    float possessedgrin$getInvertedChildHeadScale();
}
