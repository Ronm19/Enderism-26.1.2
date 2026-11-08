package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.EndermanGolem;
import com.ronm19.enderism.entity.EndermanGolem.CombatPhase;
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
import net.minecraft.world.entity.monster.EnderMan;

public final class EndermanGolemRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("enderman_golem");
    private static final RenderType EYES = RenderTypes.eyes(texture("enderman_golem_eyes"));
    private static final RenderType GLOW = RenderTypes.eyes(texture("enderman_golem_glow"));
    private final EndermanGolemModel.Ornaments ornaments;
    public EndermanGolemRenderer(EntityRendererProvider.Context context) {
        super(context); this.shadowRadius = 1.15F;
        var golem = new EndermanGolemModel(this.model.root()); this.model = golem;
        this.ornaments = new EndermanGolemModel.Ornaments(context.bakeLayer(EndermanGolemModel.LAYER), golem);
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new ArmorLayer()); this.addLayer(new GlowLayer(GLOW, false)); this.addLayer(new GlowLayer(EYES, true));
    }
    private static Identifier texture(String name) { return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/enderman_golem/" + name + ".png"); }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public GolemRenderState createRenderState() { return new GolemRenderState(); }
    public EndermanGolemModel.Ornaments getOrnaments() { return this.ornaments; }
    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof EndermanGolem golem) {
            var render = (GolemRenderState)state;
            render.phase = golem.getCombatPhase(); render.phaseAge = Math.max(0, golem.level().getGameTime() - golem.getCombatPhaseStart() + partialTick);
            render.brightness = golem.getEyeBrightness(); state.isCreepy = false;
        }
    }
    public static final class GolemRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.IDLE;
        public float phaseAge;
        public float brightness = .65F;
    }
    private final class ArmorLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        ArmorLayer() { super(EndermanGolemRenderer.this); }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible && state.isInvisibleToPlayer && !state.appearsGlowing()) return;
            RenderType material = !state.isInvisible ? RenderTypes.entityCutout(BODY)
                    : state.isInvisibleToPlayer ? RenderTypes.outline(BODY) : RenderTypes.entityTranslucent(BODY);
            collector.order(1).submitModel(ornaments, state, poses, material, light, getOverlayCoords(state, 0),
                    state.isInvisible && !state.isInvisibleToPlayer ? 654311423 : -1, null, state.outlineColor, null);
        }
    }
    private final class GlowLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private final RenderType material; private final boolean eyes;
        GlowLayer(RenderType material, boolean eyes) { super(EndermanGolemRenderer.this); this.material = material; this.eyes = eyes; }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            int channel = Math.round(255 * (this.eyes ? ((GolemRenderState)state).brightness : .42F));
            int color = 0xff000000 | channel << 16 | channel << 8 | channel;
            collector.order(this.eyes ? 3 : 2).submitModel(this.getParentModel(), state, poses, this.material,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}
