package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.HuskEnderman;
import com.ronm19.enderism.entity.HuskEnderman.CombatPhase;
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

/** World-lit desiccated skin and native amber eyes; no custom screen effects or weather. */
public final class HuskEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("husk_enderman");
    private static final RenderType EYES = RenderTypes.eyes(texture("husk_enderman_eyes"));
    public HuskEndermanRenderer(EntityRendererProvider.Context context) {
        super(context); this.shadowRadius = .5F; this.model = new HuskEndermanModel(this.model.root());
        this.layers.removeIf(layer -> layer instanceof EnderEyesLayer || layer instanceof CarriedBlockLayer);
        this.addLayer(new HuskEyes());
    }
    private static Identifier texture(String name) { return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/husk_enderman/" + name + ".png"); }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public HuskRenderState createRenderState() { return new HuskRenderState(); }
    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks); state.carriedBlock.clear(); state.isCreepy = false;
        if (entity instanceof HuskEnderman husk) {
            var render = (HuskRenderState)state; render.phase = husk.getCombatPhase();
            render.phaseAge = Math.max(0, husk.level().getGameTime() - husk.getCombatPhaseStart() + partialTicks);
            render.warmupTicks = husk.getWarmupTicksRemaining(); render.brightness = Mth.clamp(husk.getEyeBrightness(), 0, 1);
        }
    }
    public static final class HuskRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.IDLE;
        public float phaseAge, brightness = .6F;
        public int warmupTicks;
    }
    private final class HuskEyes extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        HuskEyes() { super(HuskEndermanRenderer.this); }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            int channel = Math.round(255 * Mth.clamp(((HuskRenderState)state).brightness, 0, 1));
            int tint = 0xFF000000 | channel << 16 | channel << 8 | channel;
            collector.order(2).submitModel(getParentModel(), state, poses, EYES, LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor, null);
        }
    }
}
