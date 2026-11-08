package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.SoulSandEnderman;
import com.ronm19.enderism.entity.SoulSandEnderman.CombatPhase;
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

/** The approved body is unchanged; separate masks illuminate its white eyes and cyan details. */
public final class SoulSandEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("soul_sand_enderman");
    private static final RenderType EYES = RenderTypes.eyes(texture("soul_sand_enderman_eyes"));
    private static final RenderType GLOW = RenderTypes.eyes(texture("soul_sand_enderman_glow"));
    public SoulSandEndermanRenderer(EntityRendererProvider.Context context) {
        super(context); this.shadowRadius = .5F; this.model = new SoulSandEndermanModel(this.model.root());
        this.layers.removeIf(layer -> layer instanceof EnderEyesLayer || layer instanceof CarriedBlockLayer);
        this.addLayer(new SoulfireLight());
    }
    private static Identifier texture(String name) { return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/soul_sand_enderman/" + name + ".png"); }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public SoulSandRenderState createRenderState() { return new SoulSandRenderState(); }
    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks); state.carriedBlock.clear(); state.isCreepy = false;
        if (entity instanceof SoulSandEnderman soul) {
            var render = (SoulSandRenderState)state;
            render.phase = soul.getCombatPhase(); render.phaseAge = Math.max(0, soul.level().getGameTime() - soul.getCombatPhaseStart() + partialTicks);
            render.warmupTicks = soul.getWarmupTicksRemaining(); render.brightness = Mth.clamp(soul.getEyeBrightness(), 0, 1);
            render.dominion = soul.isDominionActive(); render.mastery = soul.isSoulSandMasteryActive(); render.hunting = soul.isActivelyHunting();
        }
    }
    public static final class SoulSandRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.IDLE;
        public float phaseAge, brightness = .65F;
        public int warmupTicks;
        public boolean dominion, mastery, hunting;
        public float glowOpacity() { return this.dominion ? .85F : this.phase.name().endsWith("WINDUP") || this.phase == CombatPhase.STRIKE || this.phase == CombatPhase.ERUPTION ? .65F : this.hunting ? .3F : .12F; }
    }
    private final class SoulfireLight extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        SoulfireLight() { super(SoulSandEndermanRenderer.this); }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            var render = (SoulSandRenderState)state;
            int eye = Math.round(255 * Mth.clamp(render.brightness, 0, 1));
            int eyeTint = 0xFF000000 | eye << 16 | eye << 8 | eye;
            collector.order(1).submitModel(getParentModel(), state, poses, EYES, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, eyeTint, null, state.outlineColor, null);
            int alpha = Math.round(255 * render.glowOpacity());
            collector.order(2).submitModel(getParentModel(), state, poses, GLOW, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, alpha << 24 | 0xFFFFFF, null, state.outlineColor, null);
        }
    }
}
