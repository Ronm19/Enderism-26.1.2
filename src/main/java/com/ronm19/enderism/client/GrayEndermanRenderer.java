package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.GrayEnderman;
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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.EnderMan;

/** Vanilla Enderman anatomy with Gray's stillness, quick strikes, and amber warning eyes. */
public final class GrayEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = Identifier.fromNamespaceAndPath(
            Enderism.MOD_ID, "textures/entity/gray_enderman/gray_enderman.png");
    private static final RenderType EYES = RenderTypes.eyes(Identifier.fromNamespaceAndPath(
            Enderism.MOD_ID, "textures/entity/gray_enderman/gray_enderman_eyes.png"));

    public GrayEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new GrayModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new GrayEyesLayer(this));
    }

    @Override
    public Identifier getTextureLocation(EndermanRenderState state) {
        return BODY;
    }

    @Override
    public GrayRenderState createRenderState() {
        return new GrayRenderState();
    }

    @Override
    public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        GrayRenderState grayState = (GrayRenderState) state;
        if (entity instanceof GrayEnderman gray) {
            grayState.eyeBrightness = Mth.clamp(gray.getEyeBrightness(), 0.0F, 1.0F);
            grayState.eclipseWarning = gray.isEclipseWindingUp();
            grayState.still = (gray.getHuntState() == GrayEnderman.HuntState.OBSERVING
                    || gray.getHuntState() == GrayEnderman.HuntState.SENSING)
                    && gray.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4;
            // Gray's readable statue-like warning replaces vanilla random anger jitter.
            state.isCreepy = false;
        }
    }

    public static final class GrayRenderState extends EndermanRenderState {
        private float eyeBrightness = 0.45F;
        private boolean eclipseWarning;
        private boolean still;
    }

    private static final class GrayModel extends EndermanModel<EndermanRenderState> {
        private GrayModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            GrayRenderState gray = (GrayRenderState) state;
            if (gray.still && state.attackTime <= 0.0F) {
                this.rightArm.xRot = 0.0F;
                this.leftArm.xRot = 0.0F;
                this.rightLeg.xRot = 0.0F;
                this.leftLeg.xRot = 0.0F;
            }
            if (gray.eclipseWarning) {
                this.rightArm.xRot = -0.28F;
                this.leftArm.xRot = -0.28F;
                this.head.zRot = Mth.sin(state.ageInTicks * 0.35F) * 0.025F;
            }
            if (state.attackTime > 0.0F) {
                float strike = Mth.sin(state.attackTime * (float) Math.PI);
                ModelPart arm = this.getArm(state.attackArm);
                arm.xRot -= strike * 0.8F;
                arm.zRot += strike * (state.attackArm == HumanoidArm.RIGHT ? -0.4F : 0.4F);
            }
        }
    }

    private static final class GrayEyesLayer extends EyesLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private GrayEyesLayer(RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> renderer) {
            super(renderer);
        }

        @Override
        public RenderType renderType() {
            return EYES;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                EndermanRenderState state, float yRot, float xRot) {
            int brightness = Math.round(((GrayRenderState) state).eyeBrightness * 255.0F);
            int color = 0xFF000000 | brightness << 16 | brightness << 8 | brightness;
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, EYES,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}
