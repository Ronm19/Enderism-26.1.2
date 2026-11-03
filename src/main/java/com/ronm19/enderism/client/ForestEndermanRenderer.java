package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.ForestEnderman;
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
import org.jspecify.annotations.Nullable;

/** Vanilla anatomy and world-lit bark, with partial woodland camouflage and separate green eyes. */
public final class ForestEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = Identifier.fromNamespaceAndPath(
            Enderism.MOD_ID, "textures/entity/forest_enderman/forest_enderman.png");
    private static final RenderType EYES = RenderTypes.eyes(Identifier.fromNamespaceAndPath(
            Enderism.MOD_ID, "textures/entity/forest_enderman/forest_enderman_eyes.png"));

    public ForestEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new ForestModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new ForestEyesLayer(this));
    }

    @Override
    public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }

    @Override
    public ForestRenderState createRenderState() { return new ForestRenderState(); }

    @Override
    public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (entity instanceof ForestEnderman forest) {
            ForestRenderState render = (ForestRenderState) state;
            render.camouflageAlpha = Mth.clamp(forest.getCamouflageAlpha(), 0.0F, 1.0F);
            render.eyeBrightness = Mth.clamp(forest.getEyeBrightness(), 0.0F, 1.0F);
            render.casting = forest.getWarmupTicksRemaining() > 0;
        }
    }

    @Override
    protected int getModelTint(EndermanRenderState state) {
        int alpha = Math.round(((ForestRenderState) state).camouflageAlpha * 255.0F);
        return alpha << 24 | 0xFFFFFF;
    }

    @Override
    protected @Nullable RenderType getRenderType(EndermanRenderState state, boolean visible, boolean transparent, boolean glowing) {
        if (visible && ((ForestRenderState) state).camouflageAlpha < 1.0F) {
            return RenderTypes.entityTranslucentCullItemTarget(BODY);
        }
        return super.getRenderType(state, visible, transparent, glowing);
    }

    public static final class ForestRenderState extends EndermanRenderState {
        private float camouflageAlpha = 1.0F;
        private float eyeBrightness = 0.8F;
        private boolean casting;
    }

    private static final class ForestModel extends EndermanModel<EndermanRenderState> {
        private ForestModel(ModelPart root) { super(root); }

        @Override
        public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            if (((ForestRenderState) state).casting) {
                this.rightArm.xRot = -0.65F;
                this.leftArm.xRot = -0.65F;
                this.rightArm.zRot = 0.12F;
                this.leftArm.zRot = -0.12F;
            }
        }
    }

    private static final class ForestEyesLayer extends EyesLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private ForestEyesLayer(RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> renderer) { super(renderer); }
        @Override
        public RenderType renderType() { return EYES; }
        @Override
        public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                           EndermanRenderState state, float yRot, float xRot) {
            int brightness = Math.round(((ForestRenderState) state).eyeBrightness * 255.0F);
            int color = 0xFF000000 | brightness << 16 | brightness << 8 | brightness;
            collector.order(1).submitModel(this.getParentModel(), state, poses, EYES,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}
