package com.possessedgrin.client;

import com.possessedgrin.PossessedGrin;
import com.possessedgrin.mixin.client.AnimalModelAccessor;
import com.possessedgrin.mixin.client.ModelPartAccessor;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.ModelWithHead;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The possessed face for mobs without a hand-made face texture (cows, creepers, spiders, the Wither, the Warden,
 * modded mobs...): a small face decal with white eyes and a grin, stuck on the front of the mob's head.
 *
 * <p>The head is found from the model itself: a part called {@code head} (or ending in {@code _head}, which gives
 * the Wither all three faces), else an animal model's head part, else a part called {@code body} or {@code cube}
 * (fish, bees, slimes, ghasts). The decal covers the front of that part's biggest box, and follows it as it moves.
 */
public final class FaceDecals {
    public static final Identifier FACE = PossessedGrin.id("textures/entity/possessed/generic_face.png");
    public static final Identifier EYES = PossessedGrin.id("textures/entity/possessed/generic_eyes.png");
    public static final Identifier BLOOD = PossessedGrin.id("textures/entity/possessed/generic_blood.png");
    /** The face once the body has rotted to the bone, and its pinpoint eyes. */
    public static final Identifier SKULL = PossessedGrin.id("textures/entity/possessed/generic_skull.png");
    public static final Identifier SKULL_EYES = PossessedGrin.id("textures/entity/possessed/generic_skull_eyes.png");
    /** The demon's own face, seen for a few frames at a time. */
    public static final Identifier TRUE_FACE = PossessedGrin.id("textures/entity/possessed/generic_true_face.png");
    static final int FULL_BRIGHT = 0xF00000;
    /** Full block and sky light, for the true face: it shows even in the dark. */
    private static final int LIT = 0xF000F0;

    /** How far in front of the face the decal floats, in model pixels: clears sheep wool and similar outer layers. */
    private static final float OFFSET = 0.65F;

    /** Models are reused for every mob of a type, so the head search runs once per model. Render thread only. */
    private static final Map<EntityModel<?>, List<Target>> TARGETS = new WeakHashMap<>();

    /** {@code head} is the head part itself; {@code path} may go one step further, to the child holding its biggest box. */
    private record Target(List<ModelPart> path, ModelPart head, ModelPart.Cuboid box, boolean animalHead) {
    }

    private FaceDecals() {
    }

    public static void render(EntityModel<?> model, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay,
                              PossessedLook look) {
        List<Target> targets = TARGETS.computeIfAbsent(model, FaceDecals::findTargets);
        if (targets.isEmpty()) {
            return;
        }
        if (look.face()) {
            VertexConsumer face = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(FACE));
            for (Target target : targets) {
                draw(model, target, matrices, face, light, overlay, 1.0F);
            }
        }
        if (look.blood()) {
            VertexConsumer blood = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(BLOOD));
            for (Target target : targets) {
                draw(model, target, matrices, blood, light, overlay, 1.0F);
            }
        }
        if (look.eyes()) {
            VertexConsumer eyes = vertexConsumers.getBuffer(RenderLayer.getEyes(EYES));
            for (Target target : targets) {
                draw(model, target, matrices, eyes, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, look.glow());
            }
        }
    }

    /** A skull over the front of the head, with two burning points of light deep in its sockets. */
    public static void renderSkull(EntityModel<?> model, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay,
                                   float glow) {
        List<Target> targets = TARGETS.computeIfAbsent(model, FaceDecals::findTargets);
        VertexConsumer skull = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(SKULL));
        for (Target target : targets) {
            draw(model, target, matrices, skull, light, overlay, 1.0F);
        }
        VertexConsumer eyes = vertexConsumers.getBuffer(RenderLayer.getEyes(SKULL_EYES));
        for (Target target : targets) {
            draw(model, target, matrices, eyes, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, glow);
        }
    }

    /** The demon's true face over the head, fully lit. */
    public static void renderTrueFace(EntityModel<?> model, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int overlay) {
        VertexConsumer face = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TRUE_FACE));
        for (Target target : TARGETS.computeIfAbsent(model, FaceDecals::findTargets)) {
            draw(model, target, matrices, face, LIT, overlay, 1.0F);
        }
    }

    private static void draw(EntityModel<?> model, Target target, MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
                             float brightness) {
        for (ModelPart part : target.path()) {
            if (!part.visible) {
                return;
            }
        }
        matrices.push();
        if (target.animalHead() && model.child) {
            // Same transform AnimalModel applies to baby heads
            AnimalModelAccessor animal = (AnimalModelAccessor) model;
            if (animal.possessedgrin$isHeadScaled()) {
                float scale = 1.5F / animal.possessedgrin$getInvertedChildHeadScale();
                matrices.scale(scale, scale, scale);
            }
            matrices.translate(0.0, animal.possessedgrin$getChildHeadYOffset() / 16.0F, animal.possessedgrin$getChildHeadZOffset() / 16.0F);
        }
        for (ModelPart part : target.path()) {
            part.rotate(matrices);
        }
        ModelPart.Cuboid box = target.box();
        float width = box.maxX - box.minX;
        // Long faces (horses, llamas): keep the decal on the top of the face where the eyes are
        float height = Math.min(box.maxY - box.minY, width * 1.25F);
        MatrixStack.Entry entry = matrices.peek();
        quad(entry.getPositionMatrix(), entry.getNormalMatrix(), vertices,
                box.minX, box.minY, box.maxX, box.minY + height, box.minZ - OFFSET, light, overlay, brightness);
        matrices.pop();
    }

    /**
     * A flat, front-facing quad in model pixels. The front of a head faces -Z; the texture's left edge goes on
     * the model's -X side (your left when the mob faces you) and its top edge on -Y (up).
     */
    static void quad(Matrix4f position, Matrix3f normal, VertexConsumer vertices,
                     float x1, float y1, float x2, float y2, float z, int light, int overlay) {
        quad(position, normal, vertices, x1, y1, x2, y2, z, light, overlay, 1.0F);
    }

    static void quad(Matrix4f position, Matrix3f normal, VertexConsumer vertices,
                     float x1, float y1, float x2, float y2, float z, int light, int overlay, float brightness) {
        vertex(position, normal, vertices, x2, y1, z, 1.0F, 0.0F, light, overlay, brightness);
        vertex(position, normal, vertices, x1, y1, z, 0.0F, 0.0F, light, overlay, brightness);
        vertex(position, normal, vertices, x1, y2, z, 0.0F, 1.0F, light, overlay, brightness);
        vertex(position, normal, vertices, x2, y2, z, 1.0F, 1.0F, light, overlay, brightness);
    }

    private static void vertex(Matrix4f position, Matrix3f normal, VertexConsumer vertices,
                               float x, float y, float z, float u, float v, int light, int overlay, float brightness) {
        vertices.vertex(position, x / 16.0F, y / 16.0F, z / 16.0F)
                .color(brightness, brightness, brightness, 1.0F)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(normal, 0.0F, 0.0F, -1.0F)
                .next();
    }

    /** The model's head part(s), for the head-turn animation. Bipeds also turn their hat layer with the head. */
    public static List<ModelPart> headParts(EntityModel<?> model) {
        List<ModelPart> heads = new ArrayList<>();
        for (Target target : TARGETS.computeIfAbsent(model, FaceDecals::findTargets)) {
            heads.add(target.head());
        }
        if (heads.isEmpty() && model instanceof ModelWithHead withHead) {
            heads.add(withHead.getHead());
        }
        if (model instanceof BipedEntityModel<?> biped && !heads.contains(biped.hat)) {
            heads.add(biped.hat);
        }
        return heads;
    }

    // --- finding the head ------------------------------------------------------------------------------------

    private static List<Target> findTargets(EntityModel<?> model) {
        List<Target> found = new ArrayList<>();
        if (model instanceof SinglePartEntityModel<?> single) {
            ModelPart root = single.getPart();
            collectHeads(root, List.of(root), found, false);
            if (found.isEmpty()) {
                collectFirstNamed(root, List.of(root), found, "body", "cube");
            }
        } else if (model instanceof AnimalModel<?>) {
            Iterator<ModelPart> heads = ((AnimalModelAccessor) model).possessedgrin$getHeadParts().iterator();
            if (heads.hasNext()) {
                ModelPart head = heads.next();
                // Horses keep the real head inside a "head_parts" group, so look for a "head" inside first
                collectHeads(head, List.of(head), found, true);
                if (found.isEmpty()) {
                    addTarget(List.of(head), found, true);
                }
            }
        } else if (model instanceof ModelWithHead withHead) {
            addTarget(List.of(withHead.getHead()), found, false);
        }
        return found;
    }

    private static void collectHeads(ModelPart part, List<ModelPart> path, List<Target> found, boolean animalHead) {
        for (Map.Entry<String, ModelPart> child : children(part).entrySet()) {
            List<ModelPart> childPath = append(path, child.getValue());
            String name = child.getKey();
            if (name.equals("head") || name.endsWith("_head")) {
                addTarget(childPath, found, animalHead);
            } else {
                collectHeads(child.getValue(), childPath, found, animalHead);
            }
        }
    }

    private static boolean collectFirstNamed(ModelPart part, List<ModelPart> path, List<Target> found, String... names) {
        for (Map.Entry<String, ModelPart> child : children(part).entrySet()) {
            List<ModelPart> childPath = append(path, child.getValue());
            for (String name : names) {
                if (child.getKey().equals(name) && addTarget(childPath, found, false)) {
                    return true;
                }
            }
            if (collectFirstNamed(child.getValue(), childPath, found, names)) {
                return true;
            }
        }
        return false;
    }

    /** Uses the part's biggest box; if the part is just a group, the biggest box of its direct children. */
    private static boolean addTarget(List<ModelPart> path, List<Target> found, boolean animalHead) {
        ModelPart part = path.get(path.size() - 1);
        ModelPart head = part;
        ModelPart.Cuboid box = biggest(part);
        if (box == null) {
            ModelPart bestChild = null;
            for (ModelPart child : children(part).values()) {
                ModelPart.Cuboid candidate = biggest(child);
                if (candidate != null && (box == null || volume(candidate) > volume(box))) {
                    box = candidate;
                    bestChild = child;
                }
            }
            if (bestChild == null) {
                return false;
            }
            path = append(path, bestChild);
        }
        found.add(new Target(List.copyOf(path), head, box, animalHead));
        return true;
    }

    @Nullable
    static ModelPart.Cuboid biggest(ModelPart part) {
        ModelPart.Cuboid best = null;
        for (ModelPart.Cuboid cuboid : ((ModelPartAccessor) (Object) part).possessedgrin$getCuboids()) {
            if (best == null || volume(cuboid) > volume(best)) {
                best = cuboid;
            }
        }
        return best;
    }

    static float volume(ModelPart.Cuboid c) {
        return (c.maxX - c.minX) * (c.maxY - c.minY) * (c.maxZ - c.minZ);
    }

    private static Map<String, ModelPart> children(ModelPart part) {
        return ((ModelPartAccessor) (Object) part).possessedgrin$getChildren();
    }

    private static List<ModelPart> append(List<ModelPart> path, ModelPart part) {
        List<ModelPart> longer = new ArrayList<>(path);
        longer.add(part);
        return longer;
    }
}
