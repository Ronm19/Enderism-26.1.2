package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.FireEnderman;
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

/** Environment-lit vanilla anatomy, with separately animated eyes and optional magma emission. */
public final class FireEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = id("textures/entity/fire_enderman/fire_enderman.png");
    private static final Identifier EYES = id("textures/entity/fire_enderman/fire_enderman_eyes.png");
    private static final Identifier MAGMA = id("textures/entity/fire_enderman/fire_enderman_glow.png");
    public FireEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new FireGlowLayer(this, EYES, true));
        // An optional glow map affects only its opaque magma texels, never the complete body.
        if (context.getResourceManager().getResource(MAGMA).isPresent()) {
            this.addLayer(new FireGlowLayer(this, MAGMA, false));
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
    public FireRenderState createRenderState() {
        return new FireRenderState();
    }

    @Override
    public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (entity instanceof FireEnderman fire) {
            ((FireRenderState) state).eyeBrightness = Mth.clamp(fire.getEyeBrightness(), 0.0F, 1.0F);
        }
    }

    public static final class FireRenderState extends EndermanRenderState {
        private float eyeBrightness = 0.8F;
    }

    private static final class FireGlowLayer extends EyesLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private final RenderType renderType;
        private final boolean eyes;
        private FireGlowLayer(RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> renderer,
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
            float brightness = this.eyes ? ((FireRenderState) state).eyeBrightness : 1.0F;
            int red = Math.round(brightness * 255.0F);
            int green = Math.round(brightness * 255.0F);
            int blue = Math.round(brightness * 255.0F);
            int color = 0xFF000000 | red << 16 | green << 8 | blue;
            collector.order(this.eyes ? 2 : 1).submitModel(this.getParentModel(), state, poseStack, this.renderType,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}
