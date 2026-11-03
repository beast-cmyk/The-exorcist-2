package com.possessedgrin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * The possessed skin, made on the fly from each mob's own texture (only where that texture has skin), so it fits
 * every mob, including modded ones and resource packs, and is drawn see-through on top of the normal skin.
 * <ul>
 *   <li><b>Stage II:</b> a sickly, pale wash crossed by dark veins and cracks.</li>
 *   <li><b>Stage III:</b> corpse-pale, with twice the veins, deep cracks, livid bruises, and blood running down
 *       from cuts all over the body.</li>
 * </ul>
 */
public final class PossessedSkins {
    private static final int[] WASH = {214, 224, 196};          // pale, faintly green
    private static final int[] DEATH_WASH = {200, 206, 190};    // greyer, a corpse's pallor
    private static final int[] VEIN = {92, 18, 46};             // dark purple-red
    private static final int[] VEIN_CORE = {54, 6, 18};
    private static final int[] BRUISE = {62, 30, 70};           // livid purple
    private static final int[] BLOOD = {150, 8, 14};
    private static final int[] OLD_BLOOD = {84, 4, 8};

    /** Render thread only. Key: texture + "#" + level. Empty when a texture can't be read. */
    private static final Map<String, Optional<Identifier>> SKINS = new HashMap<>();

    private PossessedSkins() {
    }

    /** {@code level} 1 for stage II, 2 for stage III. */
    @Nullable
    public static Identifier skinFor(Identifier baseTexture, int level) {
        return SKINS.computeIfAbsent(baseTexture + "#" + level, key -> create(baseTexture, level)).orElse(null);
    }

    private static Optional<Identifier> create(Identifier baseTexture, int level) {
        MinecraftClient client = MinecraftClient.getInstance();
        Optional<Resource> resource = client.getResourceManager().getResource(baseTexture);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream in = resource.get().getInputStream(); NativeImage source = NativeImage.read(in)) {
            int width = source.getWidth();
            int height = source.getHeight();
            NativeImage skin = new NativeImage(width, height, true);
            boolean dead = level >= 2;

            int washAlpha = dead ? 0x60 : 0x46;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    skin.setColor(x, y, hasSkin(source, x, y) ? abgr(washAlpha, dead ? DEATH_WASH : WASH) : 0);
                }
            }

            // The same pattern every time for a given mob, and stage III builds on stage II's veins
            Random random = new Random(baseTexture.toString().hashCode());
            int area = width * height;
            veins(source, skin, random, Math.max(8, area / 110), 3, 7);
            if (dead) {
                veins(source, skin, random, Math.max(8, area / 110), 4, 9);
                bruises(source, skin, random, Math.max(3, area / 700));
                blood(source, skin, random, Math.max(8, area / 100));
            }

            Identifier id = client.getTextureManager().registerDynamicTexture(dead ? "possessedgrin_dead" : "possessedgrin_skin",
                    new NativeImageBackedTexture(skin));
            return Optional.of(id);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Short wandering lines: veins and cracks. */
    private static void veins(NativeImage source, NativeImage skin, Random random, int count, int minLength, int extraLength) {
        int width = source.getWidth();
        int height = source.getHeight();
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            int dx = random.nextInt(3) - 1;
            int dy = dx == 0 ? (random.nextBoolean() ? 1 : -1) : random.nextInt(3) - 1;
            int length = minLength + random.nextInt(extraLength);
            for (int step = 0; step < length && inside(x, y, width, height); step++) {
                if (hasSkin(source, x, y)) {
                    skin.setColor(x, y, step == 0 ? abgr(215, VEIN_CORE) : abgr(170, VEIN));
                }
                if (random.nextInt(3) == 0) {
                    dx = Math.max(-1, Math.min(1, dx + random.nextInt(3) - 1));
                    if (dx == 0 && dy == 0) {
                        dy = 1;
                    }
                }
                x += dx;
                y += dy;
            }
        }
    }

    /** Soft dark blotches. */
    private static void bruises(NativeImage source, NativeImage skin, Random random, int count) {
        int width = source.getWidth();
        int height = source.getHeight();
        for (int i = 0; i < count; i++) {
            int cx = random.nextInt(width);
            int cy = random.nextInt(height);
            int radius = 1 + random.nextInt(2);
            for (int y = cy - radius; y <= cy + radius; y++) {
                for (int x = cx - radius; x <= cx + radius; x++) {
                    if (!inside(x, y, width, height) || !hasSkin(source, x, y)) {
                        continue;
                    }
                    int d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                    if (d2 <= radius * radius) {
                        skin.setColor(x, y, abgr(d2 == 0 ? 150 : 105, BRUISE));
                    }
                }
            }
        }
    }

    /** A cut, and blood running down from it, darkening as it dries. */
    private static void blood(NativeImage source, NativeImage skin, Random random, int count) {
        int width = source.getWidth();
        int height = source.getHeight();
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            if (!hasSkin(source, x, y)) {
                continue;
            }
            int length = 2 + random.nextInt(6);
            for (int step = 0; step < length && inside(x, y, width, height) && hasSkin(source, x, y); step++) {
                boolean end = step == length - 1;
                skin.setColor(x, y, end ? abgr(235, OLD_BLOOD) : abgr(step == 0 ? 240 : 215, BLOOD));
                y++;
                if (random.nextInt(5) == 0) {
                    x += random.nextBoolean() ? 1 : -1;
                }
            }
        }
    }

    private static boolean inside(int x, int y, int width, int height) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private static boolean hasSkin(NativeImage image, int x, int y) {
        return ((image.getColor(x, y) >>> 24) & 0xFF) >= 16;
    }

    /** NativeImage stores colors as ABGR. */
    private static int abgr(int alpha, int[] rgb) {
        return (alpha << 24) | (rgb[2] << 16) | (rgb[1] << 8) | rgb[0];
    }
}
