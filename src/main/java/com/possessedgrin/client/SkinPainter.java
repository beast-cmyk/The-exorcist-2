package com.possessedgrin.client;

import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Paints the possessed skin at four times the resolution of the mob's own texture (the model's UVs don't care about
 * texture size, so it lands exactly on the mob, with finer detail than the mob itself has).
 *
 * <p>Plain Java on ARGB pixel arrays, so it can be previewed outside the game.
 * <ul>
 *   <li><b>Level 1 (stage II):</b> a mottled, sickly pallor; thin dark veins that branch as they spread; and, on
 *       humanoids, two words rising in raised welts across the belly.</li>
 *   <li><b>Level 2 (stage III):</b> a corpse's grey pallor, twice the veins, jagged cracks, livid bruises, and cuts
 *       that bleed in thin runs ending in drops.</li>
 *   <li><b>Level 3 (wasting):</b> grey and sunken, cracked all over.</li>
 * </ul>
 */
public final class SkinPainter {
    public static final int SCALE = 4;

    private static final int[] WASH = {214, 224, 196};
    private static final int[] DEATH_WASH = {198, 204, 188};
    private static final int[] WASTED = {150, 146, 128};
    private static final int[] VEIN = {92, 18, 46};
    private static final int[] VEIN_CORE = {48, 6, 22};
    private static final int[] CRACK = {34, 8, 14};
    private static final int[] CRACK_EDGE = {236, 214, 204};
    private static final int[] BRUISE = {66, 28, 76};
    private static final int[] BLOOD = {156, 8, 14};
    private static final int[] BLOOD_DARK = {92, 4, 8};
    private static final int[] BLOOD_SHINE = {226, 70, 70};
    private static final int[] WELT = {168, 44, 54};
    private static final int[] WELT_TOP = {236, 150, 150};
    private static final int[] WELT_SHADOW = {74, 10, 20};
    private static final int[] FLESH = {150, 34, 38};
    private static final int[] FLESH_DEEP = {82, 10, 16};
    private static final int[] FLESH_WET = {222, 118, 112};
    private static final int[] FLESH_SHADOW = {40, 4, 8};
    private static final int[] SKIN_LIP = {206, 196, 166};
    private static final int[] MOULD = {44, 50, 26};
    private static final int[] MOULD_PALE = {118, 122, 84};
    private static final int[] BONE = {226, 218, 192};
    private static final int[] BONE_LIGHT = {246, 240, 220};
    private static final int[] BONE_SHADE = {184, 172, 140};
    private static final int[] BONE_DARK = {120, 104, 78};
    private static final int[] BONE_PIT = {96, 78, 58};
    private static final int[] CAVITY = {26, 6, 8};

    /** 3x5 and 5x5 letters for the welts. */
    private static final String[][] LETTERS = {
            {"H", "X.X", "X.X", "XXX", "X.X", "X.X"},
            {"E", "XXX", "X..", "XX.", "X..", "XXX"},
            {"L", "X..", "X..", "X..", "X..", "XXX"},
            {"P", "XX.", "X.X", "XX.", "X..", "X.."},
            {"M", "X...X", "XX.XX", "X.X.X", "X...X", "X...X"},
    };

    private final int width;
    private final int height;
    private final int baseWidth;
    private final int[] base;
    private final int[] out;
    private final Random random;

    private SkinPainter(int[] base, int baseWidth, int baseHeight, long seed) {
        this.base = base;
        this.baseWidth = baseWidth;
        this.width = baseWidth * SCALE;
        this.height = baseHeight * SCALE;
        this.out = new int[this.width * this.height];
        this.random = new Random(seed);
    }

    /**
     * @param base       the mob's own texture, ARGB, row by row
     * @param level      1 to 3, see the class description
     * @param helpRect   the front of the body in texture pixels {x, y, width, height}, for the welts; or null
     * @return the overlay, ARGB, {@code baseWidth * SCALE} by {@code baseHeight * SCALE}
     */
    public static int[] paint(int[] base, int baseWidth, int baseHeight, int level, long seed, @Nullable int[] helpRect) {
        SkinPainter painter = new SkinPainter(base, baseWidth, baseHeight, seed);
        painter.run(level, helpRect);
        return painter.out;
    }

    /**
     * The body rotting away, drawn on top of everything (clothing included).
     * <ul>
     *   <li><b>Decay 1 (rotting):</b> the skin splits and peels back in ragged patches, showing wet raw flesh, with
     *       blackened, mouldering spots around them.</li>
     *   <li><b>Decay 2 (skeletal):</b> the patches spread and the flesh in them is gone to the bone: ivory bone,
     *       shaded and pitted, rimmed with the last raw flesh; on humanoids, the ribcage shows through the chest.</li>
     * </ul>
     * The same seed grows the same patches, so the skeleton opens up exactly where the rot started.
     *
     * @param chestRect the front of the body in texture pixels {x, y, width, height}, for the ribs; or null
     */
    public static int[] decay(int[] base, int baseWidth, int baseHeight, int decay, long seed, @Nullable int[] chestRect) {
        SkinPainter painter = new SkinPainter(base, baseWidth, baseHeight, seed);
        painter.rot(decay, chestRect);
        return painter.out;
    }

    /** Just the welts, on their own (drawn on top of clothing, so robes don't hide them). */
    public static int[] welts(int[] base, int baseWidth, int baseHeight, int[] helpRect) {
        SkinPainter painter = new SkinPainter(base, baseWidth, baseHeight, 0L);
        painter.welts(helpRect);
        return painter.out;
    }

    private void run(int level, @Nullable int[] helpRect) {
        boolean dead = level >= 2;
        boolean wasted = level >= 3;
        int area = this.baseWidth * (this.height / SCALE);

        // Pallor, mottled with soft blotches so it doesn't look painted on
        int[] wash = wasted ? WASTED : dead ? DEATH_WASH : WASH;
        int alpha = wasted ? 0x84 : dead ? 0x5C : 0x42;
        float[] blotches = noise(6);
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                if (skin(x, y)) {
                    int a = alpha + (int) ((blotches[y * this.width + x] - 0.5F) * 40.0F);
                    this.out[y * this.width + x] = argb(clamp(a), wash);
                }
            }
        }

        // Stage III builds on stage II's veins: same seed, same first veins
        veins(Math.max(6, area / 90));
        if (dead) {
            veins(Math.max(6, area / 90));
            bruises(Math.max(3, area / 260));
            cracks(Math.max(3, area / 380));
            bleeding(Math.max(6, area / 120));
        }
        if (wasted) {
            cracks(Math.max(4, area / 200));
            veins(Math.max(6, area / 110));
        }
        if (helpRect != null) {
            welts(helpRect);
        }
    }

    // --- rot and bone ----------------------------------------------------------------------------------------

    private void rot(int decay, @Nullable int[] chestRect) {
        boolean skeletal = decay >= 2;
        // Two layers of noise: big patches with ragged, gnawed edges
        float[] big = noise(18);
        float[] fine = noise(5);
        float[] grain = noise(3);
        float fleshAt = skeletal ? 0.47F : 0.6F;      // where the skin has peeled off
        float boneAt = 0.57F;                         // where the flesh is gone too (skeletal only)
        float mouldAt = fleshAt - 0.08F;              // blackened skin around the wounds
        float[] field = new float[this.width * this.height];
        for (int i = 0; i < field.length; i++) {
            field[i] = big[i] * 0.72F + fine[i] * 0.28F;
        }

        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                if (!skin(x, y)) {
                    continue;
                }
                int i = y * this.width + x;
                float n = field[i];
                float g = grain[i];
                if (skeletal && n >= boneAt) {
                    bone(x, y, n, g, field, boneAt);
                } else if (n >= fleshAt) {
                    // Raw flesh: dark at the torn edge, redder deeper in, with stringy fibres and glistening specks
                    float depth = Math.min(1.0F, (n - fleshAt) * 7.0F);
                    int[] flesh = mix(FLESH_DEEP, FLESH, depth * 0.75F + g * 0.35F);
                    if (Math.sin((y + g * 7.0F) * 1.25F) > 0.72) {
                        flesh = mix(flesh, FLESH_DEEP, 0.55F);
                    }
                    this.out[i] = argb(246, flesh);
                    if (g > 0.82F && this.random.nextInt(3) == 0) {
                        this.out[i] = argb(250, FLESH_WET);
                    }
                    // The peeled-back lip of skin, curling over the edge, with a shadow under it
                    if (edge(field, x, y, fleshAt)) {
                        this.out[i] = argb(240, SKIN_LIP);
                        if (y + 1 < this.height && skin(x, y + 1)) {
                            over(x, y + 1, 200, FLESH_SHADOW);
                        }
                    }
                } else if (n >= mouldAt) {
                    // Rotting skin around the wounds: greenish-black, mottled
                    float t = (n - mouldAt) / (fleshAt - mouldAt);
                    int[] mould = mix(MOULD_PALE, MOULD, t);
                    over(x, y, (int) (60 + 150 * t * (0.7F + g * 0.3F)), mould);
                }
            }
        }
        if (skeletal && chestRect != null) {
            ribs(chestRect);
        }
    }

    /** One subpixel of exposed bone: ivory, lit from above, darker underneath, pitted. */
    private void bone(int x, int y, float n, float grain, float[] field, float boneAt) {
        // Rounded: shaded where it sinks back into the flesh, brightest along the ridge
        float ridge = Math.min(1.0F, (n - boneAt) * 9.0F);
        int[] color = mix(BONE_DARK, BONE_SHADE, ridge * 1.6F);
        color = mix(color, BONE, (ridge - 0.35F) * 1.6F + (grain - 0.5F) * 0.6F);
        boolean topEdge = y == 0 || field[(y - 1) * this.width + x] < boneAt;
        boolean bottomEdge = y + 1 >= this.height || field[(y + 1) * this.width + x] < boneAt;
        if (topEdge) {
            color = BONE_LIGHT;
        } else if (bottomEdge) {
            color = BONE_DARK;
        }
        this.out[y * this.width + x] = argb(250, color);
        if (!topEdge && !bottomEdge && this.random.nextInt(37) == 0) {
            this.out[y * this.width + x] = argb(250, BONE_PIT);
        }
        // Hairline cracks across the bone
        if (n > boneAt + 0.14F && grain > 0.47F && grain < 0.5F) {
            this.out[y * this.width + x] = argb(250, BONE_DARK);
        }
    }

    /** The ribcage through the chest: a breastbone, and curved ribs over a dark hollow. */
    private void ribs(int[] rect) {
        int x0 = rect[0] * SCALE;
        int y0 = rect[1] * SCALE;
        int w = rect[2] * SCALE;
        int h = (int) (rect[3] * SCALE * 0.66F);
        int middle = x0 + w / 2;
        // The hollow behind the ribs
        for (int y = y0 + 1; y < y0 + h; y++) {
            for (int x = x0 + 1; x < x0 + w - 1; x++) {
                if (skin(x, y)) {
                    this.out[y * this.width + x] = argb(248, CAVITY);
                }
            }
        }
        // Ribs: thick at the breastbone, curving down toward the sides
        int ribs = Math.max(4, h / 6);
        int spacing = Math.max(4, h / (ribs + 1));
        int thick = Math.max(2, spacing / 2);
        for (int r = 0; r < ribs; r++) {
            int top = y0 + 2 + r * spacing;
            for (int x = x0 + 1; x < x0 + w - 1; x++) {
                float side = Math.abs(x - middle) / (w / 2.0F);
                int droop = (int) (side * side * spacing * 0.9F);
                int ribThick = Math.max(1, (int) (thick * (1.0F - side * 0.35F)));
                for (int t = 0; t < ribThick; t++) {
                    int y = top + droop + t;
                    if (y < y0 + h && skin(x, y)) {
                        int[] color = t == 0 ? BONE_LIGHT : t == ribThick - 1 ? BONE_DARK : BONE;
                        this.out[y * this.width + x] = argb(250, color);
                    }
                }
            }
        }
        // The breastbone down the middle
        int sternum = Math.max(2, w / 9);
        for (int y = y0 + 1; y < y0 + h - 1; y++) {
            for (int x = middle - sternum / 2; x < middle - sternum / 2 + sternum; x++) {
                if (skin(x, y)) {
                    boolean edgeX = x == middle - sternum / 2 || x == middle - sternum / 2 + sternum - 1;
                    this.out[y * this.width + x] = argb(250, edgeX ? BONE_SHADE : BONE_LIGHT);
                }
            }
        }
    }

    /** Is this subpixel on the border of the patch (a neighbour is below the threshold)? */
    private boolean edge(float[] field, int x, int y, float threshold) {
        for (int d = 0; d < 4; d++) {
            int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
            int ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
            if (nx >= 0 && ny >= 0 && nx < this.width && ny < this.height && skin(nx, ny)
                    && field[ny * this.width + nx] < threshold) {
                return true;
            }
        }
        return false;
    }

    private static int[] mix(int[] a, int[] b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return new int[]{(int) (a[0] + (b[0] - a[0]) * t), (int) (a[1] + (b[1] - a[1]) * t), (int) (a[2] + (b[2] - a[2]) * t)};
    }

    // --- pieces ----------------------------------------------------------------------------------------------

    /** Veins: wandering lines one subpixel wide with a faint halo, branching as they go. */
    private void veins(int count) {
        for (int i = 0; i < count; i++) {
            int[] start = randomSkin();
            if (start != null) {
                vein(start[0], start[1], this.random.nextDouble() * Math.PI * 2.0, 12 + this.random.nextInt(30), 0);
            }
        }
    }

    private void vein(double x, double y, double angle, int length, int depth) {
        for (int step = 0; step < length; step++) {
            int px = (int) Math.round(x);
            int py = (int) Math.round(y);
            if (!skin(px, py)) {
                return;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if ((dx != 0 || dy != 0) && skin(px + dx, py + dy)) {
                        over(px + dx, py + dy, 60, VEIN);
                    }
                }
            }
            over(px, py, step < length / 3 ? 215 : 175, step < length / 3 ? VEIN_CORE : VEIN);
            angle += (this.random.nextDouble() - 0.5) * 0.7;
            x += Math.cos(angle);
            y += Math.sin(angle);
            if (depth < 2 && step > 3 && this.random.nextFloat() < 0.06F) {
                vein(x, y, angle + (this.random.nextBoolean() ? 0.9 : -0.9), length / 2, depth + 1);
            }
        }
    }

    /** Cracks: jagged dark lines with a pale lip along one side, like split skin. */
    private void cracks(int count) {
        for (int i = 0; i < count; i++) {
            int[] start = randomSkin();
            if (start == null) {
                continue;
            }
            int x = start[0];
            int y = start[1];
            int dx = this.random.nextBoolean() ? 1 : -1;
            int length = 6 + this.random.nextInt(14);
            for (int step = 0; step < length && skin(x, y); step++) {
                over(x, y, 230, CRACK);
                if (skin(x, y - 1)) {
                    over(x, y - 1, 90, CRACK_EDGE);
                }
                x += dx;
                if (this.random.nextInt(3) == 0) {
                    y += this.random.nextBoolean() ? 1 : -1;
                }
            }
        }
    }

    /** Bruises: soft, dark, uneven blotches. */
    private void bruises(int count) {
        for (int i = 0; i < count; i++) {
            int[] center = randomSkin();
            if (center == null) {
                continue;
            }
            int radius = 3 + this.random.nextInt(5);
            for (int y = -radius; y <= radius; y++) {
                for (int x = -radius; x <= radius; x++) {
                    double d = Math.sqrt(x * x + y * y) / radius;
                    if (d <= 1.0 && skin(center[0] + x, center[1] + y)) {
                        int a = (int) (130 * (1.0 - d) * (0.75 + this.random.nextFloat() * 0.25));
                        over(center[0] + x, center[1] + y, a, BRUISE);
                    }
                }
            }
        }
    }

    /** Cuts that bleed: a short gash, and thin runs of blood below it that end in drops. */
    private void bleeding(int count) {
        for (int i = 0; i < count; i++) {
            int[] start = randomSkin();
            if (start == null) {
                continue;
            }
            int cut = 2 + this.random.nextInt(4);
            for (int c = 0; c < cut; c++) {
                if (skin(start[0] + c, start[1])) {
                    over(start[0] + c, start[1], 240, BLOOD_DARK);
                }
            }
            int runs = 1 + this.random.nextInt(2);
            for (int r = 0; r < runs; r++) {
                int x = start[0] + this.random.nextInt(cut);
                int y = start[1] + 1;
                int length = 4 + this.random.nextInt(16);
                for (int step = 0; step < length && skin(x, y); step++, y++) {
                    over(x, y, 235, step < length / 2 ? BLOOD : BLOOD_DARK);
                    if (step < length / 3 && skin(x + 1, y)) {
                        over(x + 1, y, 200, BLOOD_DARK);       // wider near the cut, thinning as it runs
                    }
                    if (this.random.nextInt(7) == 0) {
                        x += this.random.nextBoolean() ? 1 : -1;
                    }
                }
                if (skin(x, y) && skin(x + 1, y)) {
                    over(x, y, 240, BLOOD_DARK);                // the drop at the end
                    over(x + 1, y, 240, BLOOD_DARK);
                    over(x, y - 1, 200, BLOOD_SHINE);
                }
            }
        }
    }

    /** Two words, rising in raised welts across the belly. */
    private void welts(int[] rect) {
        String[] word = {"H", "E", "L", "P", " ", "M", "E"};
        int total = 0;
        for (String letter : word) {
            total += letter.equals(" ") ? 3 : glyph(letter)[0].length() + 1;
        }
        total -= 1;
        int areaX = rect[0] * SCALE;
        int areaWidth = rect[2] * SCALE;
        int x = areaX + Math.max(0, (areaWidth - total) / 2);
        int y = rect[1] * SCALE + (int) (rect[3] * SCALE * 0.55F);
        for (String letter : word) {
            if (letter.equals(" ")) {
                x += 3;
                continue;
            }
            String[] rows = glyph(letter);
            for (int row = 0; row < rows.length; row++) {
                for (int col = 0; col < rows[row].length(); col++) {
                    if (rows[row].charAt(col) == 'X' && skin(x + col, y + row + 1)) {
                        over(x + col, y + row + 1, 150, WELT_SHADOW);     // the welt casts a little shadow
                    }
                }
            }
            for (int row = 0; row < rows.length; row++) {
                for (int col = 0; col < rows[row].length(); col++) {
                    if (rows[row].charAt(col) == 'X' && skin(x + col, y + row)) {
                        boolean top = row == 0 || rows[row - 1].charAt(col) != 'X';
                        over(x + col, y + row, 235, top ? WELT_TOP : WELT);
                    }
                }
            }
            x += rows[0].length() + 1;
        }
    }

    private static String[] glyph(String letter) {
        for (String[] glyph : LETTERS) {
            if (glyph[0].equals(letter)) {
                String[] rows = new String[glyph.length - 1];
                System.arraycopy(glyph, 1, rows, 0, rows.length);
                return rows;
            }
        }
        return new String[]{"..."};
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    /** Is there skin under this subpixel (the mob's own texture isn't transparent there)? */
    private boolean skin(int x, int y) {
        if (x < 0 || y < 0 || x >= this.width || y >= this.height) {
            return false;
        }
        return ((this.base[(y / SCALE) * this.baseWidth + x / SCALE] >>> 24) & 0xFF) >= 16;
    }

    @Nullable
    private int[] randomSkin() {
        for (int tries = 0; tries < 30; tries++) {
            int x = this.random.nextInt(this.width);
            int y = this.random.nextInt(this.height);
            if (skin(x, y)) {
                return new int[]{x, y};
            }
        }
        return null;
    }

    /** Paints a colour over what's already there, at the given strength. */
    private void over(int x, int y, int alpha, int[] rgb) {
        if (x < 0 || y < 0 || x >= this.width || y >= this.height) {
            return;
        }
        int index = y * this.width + x;
        int under = this.out[index];
        int ua = (under >>> 24) & 0xFF;
        if (ua == 0) {
            this.out[index] = argb(alpha, rgb);
            return;
        }
        float a = alpha / 255.0F;
        int r = (int) (((under >> 16) & 0xFF) * (1 - a) + rgb[0] * a);
        int g = (int) (((under >> 8) & 0xFF) * (1 - a) + rgb[1] * a);
        int b = (int) ((under & 0xFF) * (1 - a) + rgb[2] * a);
        int outA = Math.max(ua, alpha);
        this.out[index] = (outA << 24) | (r << 16) | (g << 8) | b;
    }

    /** Smooth random values 0..1 over the whole image, varying every {@code cell} subpixels. */
    private float[] noise(int cell) {
        int gw = this.width / cell + 2;
        int gh = this.height / cell + 2;
        float[] grid = new float[gw * gh];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = this.random.nextFloat();
        }
        float[] values = new float[this.width * this.height];
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                float fx = x / (float) cell;
                float fy = y / (float) cell;
                int ix = (int) fx;
                int iy = (int) fy;
                float tx = fx - ix;
                float ty = fy - iy;
                float a = grid[iy * gw + ix] * (1 - tx) + grid[iy * gw + ix + 1] * tx;
                float b = grid[(iy + 1) * gw + ix] * (1 - tx) + grid[(iy + 1) * gw + ix + 1] * tx;
                values[y * this.width + x] = a * (1 - ty) + b * ty;
            }
        }
        return values;
    }

    private static int clamp(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }

    private static int argb(int alpha, int[] rgb) {
        return (alpha << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }
}
