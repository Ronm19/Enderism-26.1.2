package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.GoldenEnderman;
import com.ronm19.enderism.entity.GoldenEnderman.CombatPhase;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EnderEyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.EnderMan;

/** Vanilla Enderman geometry with Golden's own cast and melee poses. */
public final class GoldenEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("golden_enderman");
    private static final RenderType EYES = RenderTypes.eyes(texture("golden_enderman_eyes"));
    private static final RenderType GLINT = RenderTypes.eyes(texture("golden_enderman_glow"));

    public GoldenEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new GoldenModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new GlowLayer(GLINT, false));
        this.addLayer(new GlowLayer(EYES, true));
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/golden_enderman/" + name + ".png");
    }

    @Override
    public Identifier getTextureLocation(EndermanRenderState state) {
        return BODY;
    }

    @Override
    public GoldenRenderState createRenderState() {
        return new GoldenRenderState();
    }

    @Override
    public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof GoldenEnderman golden) {
            GoldenRenderState render = (GoldenRenderState)state;
            render.phase = golden.getCombatPhase();
            render.phaseAge = Math.max(0.0F, golden.level().getGameTime() - golden.getCombatPhaseStart() + partialTick);
            render.brightness = Mth.clamp(golden.getEyeBrightness(), 0.0F, 1.0F);
            if (render.phase == CombatPhase.SHACKLES_WINDUP || render.phase == CombatPhase.JUDGMENT_WINDUP) {
                state.isCreepy = false;
            }
        }
    }

    public static final class GoldenRenderState extends EndermanRenderState {
        private CombatPhase phase = CombatPhase.IDLE;
        private float phaseAge;
        private float brightness = .75F;
    }

    private static final class GoldenModel extends EndermanModel<EndermanRenderState> {
        private GoldenModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            if (!state.carriedBlock.isEmpty()) {
                return;
            }
            GoldenRenderState golden = (GoldenRenderState)state;
            float breath = Mth.sin(state.ageInTicks * .13F);
            float raise = Mth.clamp(golden.phaseAge / 8.0F, 0.0F, 1.0F);
            switch (golden.phase) {
                case IDLE -> {
                    this.rightArm.zRot += .035F * breath;
                    this.leftArm.zRot -= .035F * breath;
                }
                case PURSUIT -> {
                    this.rightArm.xRot -= .17F;
                    this.leftArm.xRot -= .17F;
                }
                case MIDAS_TOUCH -> {
                    float swing = Mth.sin((float)Math.PI * Mth.clamp(golden.phaseAge / 10.0F, 0.0F, 1.0F));
                    this.rightArm.xRot = -.26F - 1.55F * swing;
                    this.rightArm.yRot = -.52F * swing;
                    this.leftArm.xRot = -.42F * swing;
                }
                case SHACKLES_WINDUP -> {
                    this.rightArm.xRot = -1.3F * raise;
                    this.leftArm.xRot = -1.3F * raise;
                    this.rightArm.yRot = -.52F * raise;
                    this.leftArm.yRot = .52F * raise;
                    this.head.xRot += .15F * raise;
                }
                case SHACKLES_RELEASE -> {
                    this.rightArm.xRot = -1.55F + .48F * raise;
                    this.rightArm.yRot = -.16F;
                    this.leftArm.xRot = -.62F;
                }
                case JUDGMENT_WINDUP -> {
                    this.rightArm.xRot = -2.5F * raise;
                    this.leftArm.xRot = -2.5F * raise;
                    this.rightArm.zRot = -.38F * raise;
                    this.leftArm.zRot = .38F * raise;
                    this.head.xRot -= .24F * raise;
                }
                case JUDGMENT_IMPACT -> {
                    this.rightArm.xRot = -1.48F + .76F * raise;
                    this.leftArm.xRot = -1.48F + .76F * raise;
                    this.rightArm.zRot = -.22F * (1.0F - raise);
                    this.leftArm.zRot = .22F * (1.0F - raise);
                    this.head.xRot += .13F * (1.0F - raise);
                }
                case SEARCHING -> this.head.yRot += .12F * breath;
                case RECOVERY -> {
                    this.rightArm.xRot -= .35F * (1.0F - raise);
                    this.leftArm.xRot -= .35F * (1.0F - raise);
                }
            }
        }
    }

    private final class GlowLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private final RenderType material;
        private final boolean eyes;

        private GlowLayer(RenderType material, boolean eyes) {
            super(GoldenEndermanRenderer.this);
            this.material = material;
            this.eyes = eyes;
        }

        @Override
        public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state,
                           float yRot, float xRot) {
            if (state.isInvisible) {
                return;
            }
            float brightness = ((GoldenRenderState)state).brightness;
            int value = Math.round(255.0F * (this.eyes ? brightness : .48F + .52F * brightness));
            int color = 0xFF000000 | value << 16 | value << 8 | value;
            collector.order(this.eyes ? 2 : 1).submitModel(this.getParentModel(), state, poses, this.material,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}
