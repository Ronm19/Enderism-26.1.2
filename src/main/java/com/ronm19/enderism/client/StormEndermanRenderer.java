package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.StormEnderman;
import net.minecraft.client.model.geom.ModelPart;
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

/** World-lit vanilla anatomy with separate eye and narrow lightning emission. */
public final class StormEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = id("textures/entity/storm_enderman/storm_enderman.png");
    private static final Identifier EYES = id("textures/entity/storm_enderman/storm_enderman_eyes.png");
    private static final Identifier LIGHTNING = id("textures/entity/storm_enderman/storm_enderman_glow.png");

    public StormEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new StormModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new StormGlowLayer(this, EYES, true));
        if (context.getResourceManager().getResource(LIGHTNING).isPresent())
            this.addLayer(new StormGlowLayer(this, LIGHTNING, false));
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, path); }

    @Override
    public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }

    @Override
    public StormRenderState createRenderState() { return new StormRenderState(); }

    @Override
    public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (entity instanceof StormEnderman storm) {
            StormRenderState render = (StormRenderState) state;
            render.eyeBrightness = Mth.clamp(storm.getEyeBrightness(), 0.0F, 1.0F);
            render.windingUp = storm.getWarmupTicksRemaining() > 0;
        }
    }

    public static final class StormRenderState extends EndermanRenderState {
        private float eyeBrightness = 0.8F;
        private boolean windingUp;
    }

    private static final class StormModel extends EndermanModel<EndermanRenderState> {
        private StormModel(ModelPart root) { super(root); }

        @Override
        public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            if (((StormRenderState) state).windingUp) {
                this.rightArm.xRot = -0.50F;
                this.leftArm.xRot = -0.50F;
                this.rightArm.zRot = 0.10F;
                this.leftArm.zRot = -0.10F;
            }
        }
    }

    private static final class StormGlowLayer extends EyesLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private final RenderType type;
        private final boolean eyes;
        private StormGlowLayer(RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> renderer,
                               Identifier texture, boolean eyes) {
            super(renderer);
            this.type = RenderTypes.eyes(texture);
            this.eyes = eyes;
        }

        @Override
        public RenderType renderType() { return this.type; }

        @Override
        public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                           EndermanRenderState state, float yRot, float xRot) {
            float brightness = this.eyes ? ((StormRenderState) state).eyeBrightness : 1.0F;
            int channel = Math.round(brightness * 255.0F);
            int color = 0xFF000000 | channel << 16 | channel << 8 | channel;
            collector.order(this.eyes ? 2 : 1).submitModel(this.getParentModel(), state, poses, this.type,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}
