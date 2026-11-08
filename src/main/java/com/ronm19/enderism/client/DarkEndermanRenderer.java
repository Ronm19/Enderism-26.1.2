package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.DarkEnderman;
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

/** Shared vanilla anatomy with separate melee, barrage, and Wither preparation poses. */
public final class DarkEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = Identifier.fromNamespaceAndPath(Enderism.MOD_ID,
            "textures/entity/dark_enderman/dark_enderman.png");
    private static final RenderType EYES = RenderTypes.eyes(Identifier.fromNamespaceAndPath(Enderism.MOD_ID,
            "textures/entity/dark_enderman/dark_enderman_eyes.png"));
    public DarkEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new DarkModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new DarkEyesLayer(this));
    }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public DarkRenderState createRenderState() { return new DarkRenderState(); }
    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTick) {
        super.extractRenderState(entity,state,partialTick);
        if (entity instanceof DarkEnderman dark) {
            DarkRenderState render = (DarkRenderState)state;
            render.castKind = dark.getCastTicksRemaining() > 0 ? dark.getCastKind() : 0;
            render.brightness = Mth.clamp(dark.getEyeBrightness(),0.0F,1.0F);
            if (render.castKind != 0) state.isCreepy = false;
        }
    }
    public static final class DarkRenderState extends EndermanRenderState {
        private int castKind;
        private float brightness = .7F;
    }
    private static final class DarkModel extends EndermanModel<EndermanRenderState> {
        private DarkModel(ModelPart root) { super(root); }
        @Override public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            int cast = ((DarkRenderState)state).castKind;
            if (cast == 1) {
                this.rightArm.xRot = -1.35F; this.rightArm.yRot = -.15F;
                this.leftArm.xRot = -.4F;
            } else if (cast == 2) {
                this.rightArm.xRot = -.9F; this.leftArm.xRot = -.9F;
                this.rightArm.yRot = -.2F; this.leftArm.yRot = .2F;
                this.head.xRot += .06F;
            } else if (state.attackTime > 0) {
                float strike = Mth.sin(Mth.sqrt(state.attackTime)*(float)Math.PI);
                this.rightArm.xRot = -.6F - strike;
                this.body.yRot = -.08F*strike;
            }
        }
    }
    private static final class DarkEyesLayer extends EyesLayer<EndermanRenderState,EndermanModel<EndermanRenderState>> {
        private DarkEyesLayer(RenderLayerParent<EndermanRenderState,EndermanModel<EndermanRenderState>> renderer) { super(renderer); }
        @Override public RenderType renderType() { return EYES; }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                                     EndermanRenderState state, float yRot, float xRot) {
            int value = Math.round(((DarkRenderState)state).brightness*255);
            int color = 0xFF000000 | value << 16 | value << 8 | value;
            collector.order(1).submitModel(this.getParentModel(),state,poses,EYES,
                    LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,color,null,state.outlineColor,null);
        }
    }
}
