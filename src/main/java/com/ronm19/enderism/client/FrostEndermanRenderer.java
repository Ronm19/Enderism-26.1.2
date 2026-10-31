package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.FrostEnderman;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EnderEyesLayer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.EnderMan;

/** Environment-lit vanilla anatomy, with separately animated eyes and optional frost emission. */
public final class FrostEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = id("textures/entity/frost_enderman/frost_enderman.png");
    private static final Identifier EYES = id("textures/entity/frost_enderman/frost_enderman_eyes.png");
    private static final Identifier FROST = id("textures/entity/frost_enderman/frost_enderman_glow.png");
    public FrostEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new FrostGlowLayer(this, EYES, true));
        // An optional glow map affects only its opaque frost texels, never the complete body.
        if (context.getResourceManager().getResource(FROST).isPresent()) {
            this.addLayer(new FrostGlowLayer(this, FROST, false));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, path);
    }

    @Override
    public Identifier getTextureLocation(EndermanRenderState state) {
        return BODY;
    }

    @Override
    public FrostRenderState createRenderState() {
        return new FrostRenderState();
    }

    @Override
    public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (entity instanceof FrostEnderman frost) {
            ((FrostRenderState) state).eyeBrightness = Mth.clamp(frost.getEyeBrightness(), 0.0F, 1.0F);
        }
    }

    public static final class FrostRenderState extends EndermanRenderState {
        private float eyeBrightness = 0.8F;
    }

    private static final class FrostGlowLayer extends EyesLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private final RenderType renderType;
        private final boolean eyes;
        private FrostGlowLayer(RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> renderer,
                              Identifier texture, boolean eyes) {
            super(renderer);
            this.renderType = RenderTypes.eyes(texture);
            this.eyes = eyes;
        }

        @Override
        public RenderType renderType() {
            return this.renderType;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                           EndermanRenderState state, float yRot, float xRot) {
            float brightness = this.eyes ? ((FrostRenderState) state).eyeBrightness : 1.0F;
            int red = Math.round(brightness * 255.0F);
            int green = Math.round(brightness * 255.0F);
            int blue = Math.round(brightness * 255.0F);
            int color = 0xFF000000 | red << 16 | green << 8 | blue;
            collector.order(this.eyes ? 2 : 1).submitModel(this.getParentModel(), state, poseStack, this.renderType,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}

