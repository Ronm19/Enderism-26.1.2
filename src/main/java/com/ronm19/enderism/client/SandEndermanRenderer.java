package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.SandEnderman;
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
import org.jspecify.annotations.Nullable;

/** The vanilla body fades into its own dust, while separate depth-tested eyes keep full opacity. */
public final class SandEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = Identifier.fromNamespaceAndPath(
            Enderism.MOD_ID, "textures/entity/sand_enderman/sand_enderman.png");
    private static final RenderType EYES = RenderTypes.eyes(Identifier.fromNamespaceAndPath(
            Enderism.MOD_ID, "textures/entity/sand_enderman/sand_enderman_eyes.png"));

    public SandEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SandModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new SandEyesLayer(this));
    }

    @Override
    public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }

    @Override
    public SandRenderState createRenderState() { return new SandRenderState(); }

    @Override
    public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        SandRenderState render = (SandRenderState) state;
        if (entity instanceof SandEnderman sand) {
            float veil = SandstormClient.fieldStrength(sand, sand.position());
            render.bodyAlpha = 1.0F - veil * 0.65F;
            render.eyeBrightness = veil > 0.0F ? 1.0F : Mth.clamp(sand.getEyeBrightness(), 0.0F, 1.0F);
            render.preparingVeil = sand.isVeilWindingUp();
            // Native anger jitter would advertise a hunter that is hiding his body in moving dust.
            if (veil > 0.0F || render.preparingVeil) state.isCreepy = false;
        }
    }

    @Override
    protected int getModelTint(EndermanRenderState state) {
        return Math.round(((SandRenderState) state).bodyAlpha * 255.0F) << 24 | 0xFFFFFF;
    }

    @Override
    protected @Nullable RenderType getRenderType(EndermanRenderState state, boolean visible, boolean transparent, boolean glowing) {
        if (visible && ((SandRenderState) state).bodyAlpha < 1.0F) return RenderTypes.entityTranslucentCullItemTarget(BODY);
        return super.getRenderType(state, visible, transparent, glowing);
    }

    public static final class SandRenderState extends EndermanRenderState {
        private float bodyAlpha = 1.0F;
        private float eyeBrightness = 0.9F;
        private boolean preparingVeil;
    }

    private static final class SandModel extends EndermanModel<EndermanRenderState> {
        private SandModel(ModelPart root) { super(root); }

        @Override
        public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            if (((SandRenderState) state).preparingVeil) {
                this.rightArm.xRot = -0.45F;
                this.leftArm.xRot = -0.45F;
                this.rightArm.zRot = 0.18F;
                this.leftArm.zRot = -0.18F;
            }
            if (state.attackTime > 0.0F) {
                float slash = Mth.sin(state.attackTime * (float) Math.PI);
                ModelPart arm = this.getArm(state.attackArm);
                float side = state.attackArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
                arm.xRot -= slash * 1.05F;
                arm.yRot += slash * side * 0.28F;
                arm.zRot -= slash * side * 0.55F;
                this.body.yRot += slash * side * 0.08F;
            }
        }
    }

    private static final class SandEyesLayer extends EyesLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private SandEyesLayer(RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> renderer) { super(renderer); }
        @Override
        public RenderType renderType() { return EYES; }
        @Override
        public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                           EndermanRenderState state, float yRot, float xRot) {
            int brightness = Math.round(((SandRenderState) state).eyeBrightness * 255.0F);
            int color = 0xFF000000 | brightness << 16 | brightness << 8 | brightness;
            collector.order(1).submitModel(this.getParentModel(), state, poses, EYES,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}
