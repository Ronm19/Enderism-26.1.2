package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.WhiteEnderman;
import com.ronm19.enderism.entity.WhiteEnderman.CombatPhase;
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

/** White skin follows ordinary lighting; only the native violet eye pixels are emissive. */
public final class WhiteEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("white_enderman");
    private static final RenderType EYES = RenderTypes.eyes(texture("white_enderman_eyes"));
    public WhiteEndermanRenderer(EntityRendererProvider.Context context) {
        super(context); this.shadowRadius = .5F; this.model = new WhiteEndermanModel(this.model.root());
        this.layers.removeIf(layer -> layer instanceof EnderEyesLayer || layer instanceof CarriedBlockLayer);
        this.addLayer(new WhiteEyes());
    }
    private static Identifier texture(String name) { return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/white_enderman/" + name + ".png"); }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public WhiteRenderState createRenderState() { return new WhiteRenderState(); }
    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks); state.carriedBlock.clear(); state.isCreepy = false;
        if (entity instanceof WhiteEnderman white) {
            var render = (WhiteRenderState)state;
            render.phase = white.getCombatPhase(); render.phaseAge = Math.max(0, white.level().getGameTime() - white.getCombatPhaseStart() + partialTicks);
            render.warmupTicks = white.getWarmupTicksRemaining(); render.brightness = Mth.clamp(white.getEyeBrightness(), 0, 1); render.apex = white.isApexActive();
        }
    }
    public static final class WhiteRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.IDLE;
        public float phaseAge, brightness = .55F;
        public int warmupTicks;
        public boolean apex;
    }
    private final class WhiteEyes extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        WhiteEyes() { super(WhiteEndermanRenderer.this); }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            int channel = Math.round(255 * Mth.clamp(((WhiteRenderState)state).brightness, 0, 1));
            int tint = 0xFF000000 | channel << 16 | channel << 8 | channel;
            collector.order(1).submitModel(getParentModel(), state, poses, EYES, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor, null);
        }
    }
}
