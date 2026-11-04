package com.possessedgrin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The possessed skin, made on the fly from each mob's own texture by {@link SkinPainter}, at four times its
 * resolution, so it fits every mob (modded ones and resource packs included) with finer detail than the mob itself.
 * Drawn see-through on top of the normal skin.
 * <ul>
 *   <li>Level 1 (stage II): pallor and branching veins.</li>
 *   <li>Level 2 (stage III): grey pallor, more veins, cracks, bruises, bleeding cuts.</li>
 *   <li>Level 3: a body wasting away, grey and sunken.</li>
 *   <li>Decay 1 and 2: the skin rotting off in patches, then bone showing through (ribs on humanoids).</li>
 * </ul>
 * Humanoids also get the welts: two words rising across the belly, drawn as their own layer on top of clothing.
 */
public final class PossessedSkins {
    /** Render thread only. Empty when a texture can't be read. */
    private static final Map<String, Optional<Identifier>> SKINS = new HashMap<>();

    private PossessedSkins() {
    }

    @Nullable
    public static Identifier skinFor(Identifier baseTexture, int level) {
        return SKINS.computeIfAbsent(baseTexture + "#" + level, key -> create(baseTexture, level, null)).orElse(null);
    }

    /** The welts layer for a humanoid model wearing this texture, or null. */
    @Nullable
    public static Identifier weltsFor(Identifier baseTexture, EntityModel<?> model) {
        if (!BodyFront.humanoid(model)) {
            return null;
        }
        return SKINS.computeIfAbsent(baseTexture + "#welts#" + model.getClass().getName(), key -> create(baseTexture, -1, model)).orElse(null);
    }

    /** The rot (1) or the bare bones (2) for this texture, drawn over clothing; humanoids get a ribcage. */
    @Nullable
    public static Identifier decayFor(Identifier baseTexture, int decay, EntityModel<?> model) {
        return SKINS.computeIfAbsent(baseTexture + "#decay" + decay + "#" + model.getClass().getName(),
                key -> create(baseTexture, 10 + decay, model)).orElse(null);
    }

    /** level 1-3: possessed skin; -1: welts; 11-12: decay 1-2. */
    private static Optional<Identifier> create(Identifier baseTexture, int level, @Nullable EntityModel<?> model) {
        MinecraftClient client = MinecraftClient.getInstance();
        Optional<Resource> resource = client.getResourceManager().getResource(baseTexture);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream in = resource.get().getInputStream(); NativeImage source = NativeImage.read(in)) {
            int width = source.getWidth();
            int height = source.getHeight();
            int[] base = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    base[y * width + x] = abgrToArgb(source.getColor(x, y));
                }
            }

            int[] painted;
            if (level < 0) {
                int[] belly = model == null ? null : BodyFront.find(model, width, height,
                        (x, y) -> ((base[y * width + x] >>> 24) & 0xFF) >= 16 ? 1 : 0);
                if (belly == null) {
                    return Optional.empty();
                }
                painted = SkinPainter.welts(base, width, height, belly);
            } else if (level > 10) {
                int[] chest = model == null || !BodyFront.humanoid(model) ? null : BodyFront.find(model, width, height,
                        (x, y) -> ((base[y * width + x] >>> 24) & 0xFF) >= 16 ? 1 : 0);
                painted = SkinPainter.decay(base, width, height, level - 10, baseTexture.toString().hashCode(), chest);
            } else {
                painted = SkinPainter.paint(base, width, height, level, baseTexture.toString().hashCode(), null);
            }

            int outWidth = width * SkinPainter.SCALE;
            int outHeight = height * SkinPainter.SCALE;
            NativeImage skin = new NativeImage(outWidth, outHeight, true);
            for (int y = 0; y < outHeight; y++) {
                for (int x = 0; x < outWidth; x++) {
                    skin.setColor(x, y, argbToAbgr(painted[y * outWidth + x]));
                }
            }
            String name = level < 0 ? "possessedgrin_welts" : level > 10 ? "possessedgrin_decay" + (level - 10) : "possessedgrin_skin" + level;
            return Optional.of(client.getTextureManager().registerDynamicTexture(name, new NativeImageBackedTexture(skin)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** NativeImage stores colours as ABGR; the painter works in ARGB. Swapping red and blue converts either way. */
    private static int abgrToArgb(int color) {
        return (color & 0xFF00FF00) | ((color & 0xFF) << 16) | ((color >> 16) & 0xFF);
    }

    private static int argbToAbgr(int color) {
        return abgrToArgb(color);
    }
}
