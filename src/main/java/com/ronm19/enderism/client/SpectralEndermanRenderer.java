package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.SpectralEnderman;
import com.ronm19.enderism.entity.SpectralEnderman.CombatPhase;
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
import org.jspecify.annotations.Nullable;

/** A brief visual fade on the real native skeleton, with no visibility or collision changes. */
public final class SpectralEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("spectral_enderman");
    private static final RenderType EYES = RenderTypes.eyes(texture("spectral_enderman_eyes"));
    private static final RenderType WISPS = RenderTypes.eyes(texture("spectral_enderman_glow"));

    public SpectralEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = .5F;
        this.model = new SpectralModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new GlowLayer(WISPS, false));
        this.addLayer(new GlowLayer(EYES, true));
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(Enderism.MOD_ID,
                "textures/entity/spectral_enderman/" + name + ".png");
    }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public SpectralRenderState createRenderState() { return new SpectralRenderState(); }

    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof SpectralEnderman spectral) {
            var render = (SpectralRenderState)state;
            render.phase = spectral.getCombatPhase();
            render.phaseAge = Math.max(0, spectral.level().getGameTime() - spectral.getCombatPhaseStart() + partialTick);
            render.brightness = Mth.clamp(spectral.getEyeBrightness(), 0, 1);
            render.opacity = Mth.clamp(spectral.getVisualOpacity(partialTick), .24F, 1);
            // The ordinary vanilla jaw jitter should not replace a deliberate materialization pose.
            state.isCreepy = false;
        }
    }

    @Override protected int getModelTint(EndermanRenderState state) {
        int alpha = Math.round(255 * Mth.clamp(((SpectralRenderState)state).opacity, .24F, 1));
        return alpha << 24 | 0xFFFFFF;
    }

    @Override protected @Nullable RenderType getRenderType(EndermanRenderState state, boolean isBodyVisible,
                                                         boolean forceTransparent, boolean appearGlowing) {
        // Native invisibility and outline handling retain ownership when supplied by another system.
        if (isBodyVisible && ((SpectralRenderState)state).opacity < .999F) return RenderTypes.entityTranslucent(BODY);
        return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
    }

    public static final class SpectralRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.IDLE;
        public float phaseAge;
        public float brightness = .8F;
        public float opacity = 1;
    }

    private static final class SpectralModel extends EndermanModel<EndermanRenderState> {
        private SpectralModel(ModelPart root) { super(root); }
        @Override public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            if (!state.carriedBlock.isEmpty()) return;
            var spectral = (SpectralRenderState)state;
            float breath = Mth.sin(state.ageInTicks * .105F);
            float rise = Mth.clamp(spectral.phaseAge / 10, 0, 1);
            switch (spectral.phase) {
                case IDLE -> {
                    this.rightArm.zRot += .022F * breath;
                    this.leftArm.zRot -= .022F * breath;
                }
                case OBSERVING -> {
                    this.head.zRot += .055F * breath;
                    this.rightArm.xRot = -.06F;
                    this.leftArm.xRot = -.06F;
                }
                case STALKING -> {
                    this.rightArm.xRot *= .55F; this.leftArm.xRot *= .55F;
                    this.head.xRot -= .08F;
                }
                case MANIFESTATION_PREP -> {
                    this.rightArm.xRot = -.5F * rise;
                    this.leftArm.xRot = -.5F * rise;
                    this.rightArm.zRot = -.18F * rise;
                    this.leftArm.zRot = .18F * rise;
                }
                case APPARITION_PREP -> {
                    this.rightArm.xRot = -1.05F * rise;
                    this.leftArm.xRot = -.7F * rise;
                    this.rightArm.yRot = -.4F * rise;
                    this.leftArm.yRot = .3F * rise;
                    this.head.xRot -= .12F * rise;
                }
                case TRANSITION -> {
                    this.rightArm.zRot = -.19F;
                    this.leftArm.zRot = .19F;
                    this.head.xRot -= .12F;
                }
                case MATERIALIZING -> {
                    float emerge = Mth.clamp(spectral.phaseAge / 8, 0, 1);
                    this.rightArm.xRot = -.65F * emerge;
                    this.leftArm.xRot = -.4F * emerge;
                    this.rightArm.yRot = -.25F * emerge;
                    this.leftArm.yRot = .25F * emerge;
                }
                case AMBUSH_READY -> {
                    this.rightArm.xRot = -1.15F;
                    this.rightArm.yRot = -.55F;
                    this.leftArm.xRot = -.55F;
                    this.head.xRot -= .1F;
                }
                case REND, EMPOWERED_REND -> {
                    float strike = Mth.sin(Mth.clamp(spectral.phaseAge / 9, 0, 1) * Mth.PI);
                    float power = spectral.phase == CombatPhase.EMPOWERED_REND ? 1.0F : .76F;
                    this.rightArm.xRot = -.2F - 1.8F * strike;
                    this.rightArm.yRot = -.65F * strike;
                    this.rightArm.zRot = -.25F * strike;
                    this.leftArm.xRot = -.25F - .9F * strike * power;
                    this.leftArm.yRot = .3F * strike * power;
                    this.head.xRot -= .16F * strike;
                }
                case RECOVERY -> {
                    this.rightArm.xRot = -.4F * (1 - rise);
                    this.leftArm.xRot = -.22F * (1 - rise);
                }
                case SEARCHING -> this.head.yRot += .16F * breath;
            }
        }
    }

    private final class GlowLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private final RenderType material;
        private final boolean eyes;
        private GlowLayer(RenderType material, boolean eyes) {
            super(SpectralEndermanRenderer.this); this.material = material; this.eyes = eyes;
        }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                                     EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            var spectral = (SpectralRenderState)state;
            float opacity = Mth.clamp(spectral.opacity, .24F, 1);
            float intensity = this.eyes ? spectral.brightness : .42F + .3F * spectral.brightness;
            int channel = Math.round(255 * Mth.clamp(intensity, 0, 1));
            // Eyes use translucent blending in this version: dimming RGB alone would leave
            // opaque dark markings floating over the faint body. Fade their alpha as well.
            int alpha = Math.round(255 * (this.eyes ? .45F + .55F * opacity : opacity));
            int color = alpha << 24 | channel << 16 | channel << 8 | channel;
            collector.order(this.eyes ? 2 : 1).submitModel(this.getParentModel(), state, poses, this.material,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}
