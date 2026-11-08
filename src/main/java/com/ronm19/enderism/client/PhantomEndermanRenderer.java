package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.PhantomEnderman;
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

/** Vanilla ground-model anatomy, with restrained stalking, strike, and curse poses. */
public final class PhantomEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = Identifier.fromNamespaceAndPath(Enderism.MOD_ID,
            "textures/entity/phantom_enderman/phantom_enderman.png");
    private static final RenderType EYES = RenderTypes.eyes(Identifier.fromNamespaceAndPath(Enderism.MOD_ID,
            "textures/entity/phantom_enderman/phantom_enderman_eyes.png"));

    public PhantomEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new PhantomModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new PhantomEyesLayer(this));
    }

    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public PhantomRenderState createRenderState() { return new PhantomRenderState(); }
    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTick) {
        super.extractRenderState(entity,state,partialTick);
        if (entity instanceof PhantomEnderman phantom) {
            PhantomRenderState render = (PhantomRenderState) state;
            render.stalking = phantom.isStalking();
            render.stepping = phantom.getWarmupTicksRemaining() > 0;
            render.cursing = phantom.getCursePendingTicks() > 0;
            render.rearAttack = phantom.isRearAttackAnimation();
            render.brightness = Mth.clamp(phantom.getEyeBrightness(),0.0F,1.0F);
            if (render.stalking || render.stepping) state.isCreepy = false;
        }
    }

    public static final class PhantomRenderState extends EndermanRenderState {
        private boolean stalking, stepping, cursing, rearAttack;
        private float brightness = .7F;
    }

    private static final class PhantomModel extends EndermanModel<EndermanRenderState> {
        private PhantomModel(ModelPart root) { super(root); }
        @Override public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            PhantomRenderState phantom = (PhantomRenderState) state;
            if (phantom.stalking) {
                this.rightArm.xRot *= .5F; this.leftArm.xRot *= .5F;
                this.head.xRot += .04F;
            }
            if (phantom.stepping) {
                this.rightArm.xRot = -.28F; this.leftArm.xRot = -.28F;
                this.rightArm.zRot = .08F; this.leftArm.zRot = -.08F;
            } else if (phantom.cursing) {
                this.leftArm.xRot = -.65F; this.leftArm.zRot = -.12F;
            }
            if (state.attackTime > 0.0F) {
                float strike = Mth.sin(Mth.sqrt(state.attackTime) * (float)Math.PI);
                this.rightArm.xRot = -.7F - strike * (phantom.rearAttack ? 1.1F : .7F);
                this.rightArm.yRot = -.22F + strike * .4F;
                this.body.yRot = strike * (phantom.rearAttack ? -.15F : .08F);
            }
        }
    }

    private static final class PhantomEyesLayer extends EyesLayer<EndermanRenderState,EndermanModel<EndermanRenderState>> {
        private PhantomEyesLayer(RenderLayerParent<EndermanRenderState,EndermanModel<EndermanRenderState>> renderer) { super(renderer); }
        @Override public RenderType renderType() { return EYES; }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                                     EndermanRenderState state, float yRot, float xRot) {
            int value = Math.round(((PhantomRenderState)state).brightness * 255.0F);
            int color = 0xFF000000 | value << 16 | value << 8 | value;
            collector.order(1).submitModel(this.getParentModel(),state,poses,EYES,
                    LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,color,null,state.outlineColor,null);
        }
    }
}
