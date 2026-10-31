package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.BrownEnderman;
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
import net.minecraft.world.entity.HumanoidArm;

public final class BrownEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = Identifier.fromNamespaceAndPath(
            Enderism.MOD_ID, "textures/entity/brown_enderman/brown_enderman.png");
    private static final RenderType EYES = RenderTypes.eyes(Identifier.fromNamespaceAndPath(
            Enderism.MOD_ID, "textures/entity/brown_enderman/brown_enderman_eyes.png"));

    public BrownEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BrownModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new BrownEyesLayer(this));
    }

    @Override
    public Identifier getTextureLocation(EndermanRenderState state) {
        return BODY;
    }

    @Override
    public BrownRenderState createRenderState() {
        return new BrownRenderState();
    }

    @Override
    public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        BrownRenderState brownState = (BrownRenderState) state;
        if (entity instanceof BrownEnderman brown) {
            brownState.eyeBrightness = Mth.clamp(brown.getEyeBrightness(), 0.0F, 1.0F);
            // The intimidation stare stays still rather than using vanilla anger jitter.
            if (brown.isIntimidating()) {
                state.isCreepy = false;
            }
        }
    }

    public static final class BrownRenderState extends EndermanRenderState {
        private float eyeBrightness = 0.55F;
    }

    private static final class BrownModel extends EndermanModel<EndermanRenderState> {
        private BrownModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            if (state.attackTime > 0.0F) {
                // Keep vanilla anatomy and locomotion, with a readable heavy strike.
                float strike = Mth.sin(state.attackTime * (float) Math.PI);
                ModelPart arm = this.getArm(state.attackArm);
                arm.xRot -= strike * 1.1F;
                arm.zRot += strike * (state.attackArm == HumanoidArm.RIGHT ? -0.15F : 0.15F);
            }
        }
    }

    private static final class BrownEyesLayer extends EyesLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private BrownEyesLayer(RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> renderer) {
            super(renderer);
        }

        @Override
        public RenderType renderType() {
            return EYES;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                EndermanRenderState state, float yRot, float xRot) {
            int brightness = Math.round(((BrownRenderState) state).eyeBrightness * 255.0F);
            int color = 0xFF000000 | brightness << 16 | brightness << 8 | brightness;
            collector.order(1).submitModel(this.getParentModel(), state, poseStack, EYES,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}
