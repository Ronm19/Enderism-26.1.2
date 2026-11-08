package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.SculkEnderman;
import com.ronm19.enderism.entity.SculkEnderman.CombatPhase;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.CarriedBlockLayer;
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

/** A world-lit sculk body, gently luminous veins, and actual branching sensor tendrils. */
public final class SculkEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("sculk_enderman");
    private static final Identifier TENDRILS = texture("sculk_enderman_tendrils");
    private static final RenderType EYES = RenderTypes.eyes(texture("sculk_enderman_eyes"));
    private static final RenderType VEINS = RenderTypes.eyes(texture("sculk_enderman_glow"));
    public SculkEndermanRenderer(EntityRendererProvider.Context context) {
        super(context); this.shadowRadius = .5F; this.model = new SculkEndermanModel(this.model.root());
        this.layers.removeIf(layer -> layer instanceof EnderEyesLayer || layer instanceof CarriedBlockLayer);
        this.addLayer(new SensorTendrils(context)); this.addLayer(new ScaledGlow(VEINS, false)); this.addLayer(new ScaledGlow(EYES, true));
    }
    private static Identifier texture(String name) { return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/sculk_enderman/" + name + ".png"); }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public SculkRenderState createRenderState() { return new SculkRenderState(); }
    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks); state.carriedBlock.clear(); state.isCreepy = false;
        if (entity instanceof SculkEnderman sculk) {
            var render = (SculkRenderState)state; render.phase = sculk.getCombatPhase();
            render.phaseAge = Math.max(0, sculk.level().getGameTime() - sculk.getCombatPhaseStart() + partialTicks);
            render.brightness = Mth.clamp(sculk.getEyeBrightness(), 0, 1); render.detectionPulseTicks = sculk.getDetectionPulseTicksRemaining();
        }
    }
    public static final class SculkRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.LISTENING;
        public float phaseAge, brightness = .48F;
        public int detectionPulseTicks;
    }
    private final class SensorTendrils extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private final SculkEndermanModel.Tendrils tendrils;
        SensorTendrils(EntityRendererProvider.Context context) {
            super(SculkEndermanRenderer.this); this.tendrils = new SculkEndermanModel.Tendrils(context.bakeLayer(SculkEndermanModel.TENDRILS_LAYER), (SculkEndermanModel)getParentModel());
        }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            float brightness = ((SculkRenderState)state).brightness;
            int channel = Math.round(255 * (.65F + .35F * brightness));
            int tint = 0xFF000000 | channel << 16 | channel << 8 | channel;
            collector.order(1).submitModel(this.tendrils, state, poses, RenderTypes.entityCutout(TENDRILS), LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor, null);
        }
    }
    private final class ScaledGlow extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private final RenderType material; private final boolean eyes;
        ScaledGlow(RenderType material, boolean eyes) { super(SculkEndermanRenderer.this); this.material = material; this.eyes = eyes; }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            float brightness = ((SculkRenderState)state).brightness;
            int channel = Math.round(255 * (this.eyes ? brightness : .25F + .5F * brightness));
            int tint = 0xFF000000 | channel << 16 | channel << 8 | channel;
            collector.order(this.eyes ? 3 : 2).submitModel(getParentModel(), state, poses, this.material, LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor, null);
        }
    }
}
