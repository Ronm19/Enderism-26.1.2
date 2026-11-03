package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.SilverEnderman;
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

/** The huntress keeps vanilla anatomy; her unarmed slash poses and eye intensity carry the combat presentation. */
public final class SilverEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = Identifier.fromNamespaceAndPath(
            Enderism.MOD_ID, "textures/entity/silver_enderman/silver_enderman.png");
    private static final RenderType EYES = RenderTypes.eyes(Identifier.fromNamespaceAndPath(
            Enderism.MOD_ID, "textures/entity/silver_enderman/silver_enderman_eyes.png"));

    public SilverEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SilverModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new SilverEyesLayer(this));
    }

    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public SilverRenderState createRenderState() { return new SilverRenderState(); }
    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (entity instanceof SilverEnderman silver) {
            SilverRenderState render = (SilverRenderState) state;
            render.slashPattern = silver.getSlashPattern();
            render.eyeBrightness = Mth.clamp(silver.getEyeBrightness(), 0.0F, 1.0F);
            render.stepping = silver.getWarmupTicksRemaining() > 0;
            if (render.stepping) state.isCreepy = false;
        }
    }

    public static final class SilverRenderState extends EndermanRenderState {
        private int slashPattern;
        private float eyeBrightness = 0.75F;
        private boolean stepping;
    }

    private static final class SilverModel extends EndermanModel<EndermanRenderState> {
        private SilverModel(ModelPart root) { super(root); }
        @Override public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            SilverRenderState silver = (SilverRenderState) state;
            if (silver.stepping) {
                this.rightArm.xRot = -0.35F; this.leftArm.xRot = -0.35F;
                this.rightArm.zRot = 0.10F; this.leftArm.zRot = -0.10F;
            }
            if (state.attackTime <= 0.0F) return;
            float slash = Mth.sin(Mth.sqrt(state.attackTime) * (float) Math.PI);
            switch (Math.floorMod(silver.slashPattern, 3)) {
                case 0 -> {
                    this.rightArm.xRot = -0.8F - slash * 0.4F;
                    this.rightArm.yRot = -1.1F + slash * 1.9F;
                    this.rightArm.zRot = -0.8F + slash * 1.25F;
                    this.body.yRot = slash * 0.16F;
                }
                case 1 -> {
                    this.rightArm.xRot = -1.5F + slash * 1.55F;
                    this.rightArm.yRot = -0.45F + slash * 0.8F;
                    this.rightArm.zRot = -0.55F + slash * 0.95F;
                    this.body.yRot = -slash * 0.12F;
                }
                default -> {
                    this.leftArm.xRot = -0.9F - slash * 0.35F;
                    this.leftArm.yRot = 1.0F - slash * 1.9F;
                    this.leftArm.zRot = 0.75F - slash * 1.2F;
                    this.body.yRot = -slash * 0.16F;
                }
            }
        }
    }

    private static final class SilverEyesLayer extends EyesLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private SilverEyesLayer(RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> renderer) { super(renderer); }
        @Override public RenderType renderType() { return EYES; }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                                     EndermanRenderState state, float yRot, float xRot) {
            int brightness = Math.round(((SilverRenderState) state).eyeBrightness * 255.0F);
            int color = 0xFF000000 | brightness << 16 | brightness << 8 | brightness;
            collector.order(1).submitModel(this.getParentModel(), state, poses, EYES,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}
