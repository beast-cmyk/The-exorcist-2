package com.possessedgrin;

import ladysnake.requiem.api.v1.possession.Possessable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Shared ids, tags and helpers. */
public final class PossessedGrin {
    public static final String MOD_ID = "possessedgrin";

    /** Bosses players may possess (Warden, Wither, Ender Dragon, plus anything in Requiem's boss tag). */
    public static final TagKey<EntityType<?>> POSSESSABLE_BOSSES = tag(MOD_ID, "possessable_bosses");

    /** Mobs that should never get the possessed punch. Empty by default; for datapacks. */
    public static final TagKey<EntityType<?>> NEVER_PUNCH = tag(MOD_ID, "never_punch");

    /** Requiem's own "can never be possessed" list (bosses, ghosts, morticians). */
    public static final TagKey<EntityType<?>> REQUIEM_POSSESSION_BLACKLIST = tag("requiem", "possession/possession_blacklist");

    private PossessedGrin() {
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    private static TagKey<EntityType<?>> tag(String namespace, String path) {
        return TagKey.of(Registry.ENTITY_TYPE_KEY, new Identifier(namespace, path));
    }

    /** The player possessing this entity, if any. Requiem mixes {@link Possessable} into every LivingEntity. */
    @Nullable
    public static PlayerEntity possessorOf(@Nullable Entity entity) {
        return entity instanceof Possessable possessable ? possessable.getPossessor() : null;
    }

    public static boolean isPossessed(@Nullable Entity entity) {
        return entity instanceof Possessable possessable && possessable.isBeingPossessed();
    }

    /** "the villager", or its name if it has one ("Steve the villager" becomes just "Steve"). */
    public static String the(Entity entity) {
        return entity.hasCustomName() ? entity.getName().getString() : "the " + kind(entity);
    }

    /** The mob's type in lower case: "villager", "iron golem". */
    public static String kind(Entity entity) {
        return entity.getType().getName().getString().toLowerCase(Locale.ROOT);
    }

    /** What to call it at the start of a line: "Villager", or its name. */
    public static String label(Entity entity) {
        return entity.hasCustomName() ? entity.getName().getString() : entity.getType().getName().getString();
    }
}
