package com.possessedgrin.client;

import com.possessedgrin.PossessedGrin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.ModelWithHead;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Draws the possession on any mob that a Requiem player is currently possessing, stage by stage (see
 * {@link PossessedLook}): flickering white eyes, then the sickly skin, glowing eyes, the malicious grin and fangs,
 * then blood, bruises and a corpse's pallor.
 *
 * <p>Humanoids have hand-made faces that match their own textures exactly; every other mob gets the generic
 * face decal from {@link FaceDecals}.
 *
 * <p>Textures are looked up by entity id:
 * {@code assets/possessedgrin/textures/entity/possessed/<namespace>/<path>_face.png}, {@code ..._eyes.png} and
 * {@code ..._blood.png}.
 * They use the same size and UV layout as the mob's own texture. Mobs without a face texture are left alone,
 * so a resource pack can add support for more mobs just by adding PNGs.
 */
public class PossessedFaceFeatureRenderer<T extends LivingEntity, M extends EntityModel<T>> extends FeatureRenderer<T, M> {
    /** Same packed light value vanilla uses for spider / enderman eyes. */
    private static final int FULL_BRIGHT = 0xF00000;

    /**
     * Some mobs draw an inflated outer layer over their face (drowned seaweed, stray rags, villager clothing
     * and hoods, the illusioner's hood). For those, the head is stretched slightly forward (along its own depth
     * axis only) while the grin is drawn, so the grin sits just in front of that layer instead of hiding behind it.
     */
    private static final Map<Identifier, Float> FACE_DEPTH = Map.of(
            new Identifier("minecraft", "drowned"), 1.10F,
            new Identifier("minecraft", "stray"), 1.10F,
            new Identifier("minecraft", "zombie_villager"), 1.16F,
            new Identifier("minecraft", "villager"), 1.16F,
            new Identifier("minecraft", "wandering_trader"), 1.16F,
            new Identifier("minecraft", "illusioner"), 1.16F,
            new Identifier("requiem", "cured_villager"), 1.16F
    );

    /** Renderers (and this feature) are rebuilt on every resource reload, so this cache never goes stale. */
    private final Map<EntityType<?>, Optional<FaceTextures>> cache = new IdentityHashMap<>();

    public PossessedFaceFeatureRenderer(FeatureRendererContext<T, M> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T entity,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        PossessedLook look = PossessedLook.of(entity, tickDelta);
        if (look == null) {
            return;
        }
        M model = this.getContextModel();
        int overlay = LivingEntityRenderer.getOverlay(entity, 0.0F);

        // The possessed skin: pale, veined and cracked (stage II), then bloodied and bruised (stage III)
        if (look.skinLevel() > 0) {
            Identifier skin = PossessedSkins.skinFor(this.getTexture(entity), look.skinLevel());
            if (skin != null) {
                VertexConsumer skinLayer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(skin));
                model.render(matrices, skinLayer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        // For a few frames, the thing inside shows its own face
        if (TrueFace.showing(entity)) {
            FaceDecals.renderTrueFace(model, matrices, vertexConsumers, overlay);
            return;
        }
        // Rotted to the bone: a skull for a face, whatever the mob
        if (look.decay() >= 2) {
            FaceDecals.renderSkull(model, matrices, vertexConsumers, light, overlay, look.glow());
            return;
        }

        FaceTextures textures = this.cache.computeIfAbsent(entity.getType(), PossessedFaceFeatureRenderer::resolve).orElse(null);
        if (textures == null) {
            // No hand-made face for this mob: use the generic face decal on its head
            FaceDecals.render(model, matrices, vertexConsumers, light, overlay, look);
            return;
        }

        ModelPart head = model instanceof ModelWithHead withHead ? withHead.getHead() : null;
        float originalZScale = head != null ? head.zScale : 1.0F;
        if (head != null) {
            head.zScale = originalZScale * textures.depth();
        }

        try {
            if (look.face() && textures.face() != null) {
                // Translucent: the high-detail faces have soft, see-through shadows around the eyes
                VertexConsumer face = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(textures.face()));
                model.render(matrices, face, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
            }
            if (look.blood() && textures.blood() != null) {
                VertexConsumer blood = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(textures.blood()));
                model.render(matrices, blood, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
            }
            if (look.eyes() && textures.eyes() != null) {
                float glow = look.glow();
                VertexConsumer eyes = vertexConsumers.getBuffer(RenderLayer.getEyes(textures.eyes()));
                model.render(matrices, eyes, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, glow, glow, glow, 1.0F);
            }
        } finally {
            if (head != null) {
                head.zScale = originalZScale;
            }
        }
    }

    private static Optional<FaceTextures> resolve(EntityType<?> type) {
        Identifier id = EntityType.getId(type);
        String base = "textures/entity/possessed/" + id.getNamespace() + "/" + id.getPath();
        Identifier face = new Identifier(PossessedGrin.MOD_ID, base + "_face.png");
        Identifier eyes = new Identifier(PossessedGrin.MOD_ID, base + "_eyes.png");
        Identifier blood = new Identifier(PossessedGrin.MOD_ID, base + "_blood.png");

        ResourceManager resources = MinecraftClient.getInstance().getResourceManager();
        boolean hasFace = resources.getResource(face).isPresent();
        boolean hasEyes = resources.getResource(eyes).isPresent();
        if (!hasFace && !hasEyes) {
            return Optional.empty();
        }
        boolean hasBlood = resources.getResource(blood).isPresent();
        return Optional.of(new FaceTextures(hasFace ? face : null, hasEyes ? eyes : null, hasBlood ? blood : null,
                FACE_DEPTH.getOrDefault(id, 1.0F)));
    }

    private record FaceTextures(@Nullable Identifier face, @Nullable Identifier eyes, @Nullable Identifier blood, float depth) {
    }
}
