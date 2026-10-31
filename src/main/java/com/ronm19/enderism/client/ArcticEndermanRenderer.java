package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.ArcticEnderman;
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

/** Vanilla Enderman anatomy with Arctic freezing, silent observation, and cyan warning eyes. */
public final class ArcticEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = Identifier.fromNamespaceAndPath(
            Enderism.MOD_ID, "textures/entity/arctic_enderman/arctic_enderman.png");
    private static final RenderType EYES = RenderTypes.eyes(Identifier.fromNamespaceAndPath(
            Enderism.MOD_ID, "textures/entity/arctic_enderman/arctic_enderman_eyes.png"));

    public ArcticEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new ArcticModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new ArcticEyesLayer(this));
    }

    @Override
    public Identifier getTextureLocation(EndermanRenderState state) {
        return BODY;
    }

    @Override
    public ArcticRenderState createRenderState() {
        return new ArcticRenderState();
    }

    @Override
    public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        ArcticRenderState arcticState = (ArcticRenderState) state;
        if (entity instanceof ArcticEnderman arctic) {
            arcticState.eyeBrightness = Mth.clamp(arctic.getEyeBrightness(), 0.0F, 1.0F);
            arcticState.abilityWarning = arctic.isAbilityWindingUp();
            arcticState.still = (arctic.isObserving() || arctic.isAbilityWindingUp())
                    && arctic.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4;
            // Arctic's silent execution observation replaces vanilla random anger jitter.
            state.isCreepy = false;
        }
    }

    public static final class ArcticRenderState extends EndermanRenderState {
        private float eyeBrightness = 0.45F;
        private boolean abilityWarning;
        private boolean still;
    }

    private static final class ArcticModel extends EndermanModel<EndermanRenderState> {
        private ArcticModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            ArcticRenderState arctic = (ArcticRenderState) state;
            if (arctic.still && state.attackTime <= 0.0F) {
                this.rightArm.xRot = 0.0F;
                this.leftArm.xRot = 0.0F;
                this.rightLeg.xRot = 0.0F;
                this.leftLeg.xRot = 0.0F;
            }
            if (arctic.abilityWarning) {
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

    private static final class ArcticEyesLayer extends EyesLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private ArcticEyesLayer(RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> renderer) {
            super(renderer);
        }

        @Override
        public RenderType renderType() {
            return EYES;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                EndermanRenderState state, float yRot, float xRot) {
            int brightness = Math.round(((ArcticRenderState) state).eyeBrightness * 255.0F);
            int color = 0xFF000000 | brightness << 16 | brightness << 8 | brightness;
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, EYES,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}

