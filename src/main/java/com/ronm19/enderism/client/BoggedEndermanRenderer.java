package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.BoggedEnderman;
import com.ronm19.enderism.entity.BoggedEnderman.CombatPhase;
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

/** World-lit decayed anatomy with green eye brightness extracted from synchronized server state. */
public final class BoggedEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("bogged_enderman");
    private static final Identifier DECORATIONS = texture("bogged_enderman_ornaments");
    private static final RenderType EYES = RenderTypes.eyes(texture("bogged_enderman_eyes"));
    private final BoggedEndermanModel.Ornaments ornaments;
    public BoggedEndermanRenderer(EntityRendererProvider.Context context) {
        super(context); this.shadowRadius = .52F;
        var bogged = new BoggedEndermanModel(this.model.root()); this.model = bogged;
        this.ornaments = new BoggedEndermanModel.Ornaments(context.bakeLayer(BoggedEndermanModel.LAYER), bogged);
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new VegetationLayer()); this.addLayer(new GreenEyesLayer());
    }
    private static Identifier texture(String name) { return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/bogged_enderman/" + name + ".png"); }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public BoggedRenderState createRenderState() { return new BoggedRenderState(); }
    public BoggedEndermanModel.Ornaments getOrnaments() { return this.ornaments; }
    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof BoggedEnderman body) {
            var render = (BoggedRenderState)state;
            render.phase = body.getCombatPhase();
            render.phaseAge = Math.max(0, body.level().getGameTime() - body.getCombatPhaseStart() + partialTick);
            render.brightness = Mth.clamp(body.getEyeBrightness(), 0, 1);
            state.isCreepy = false;
        }
    }
    public static final class BoggedRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.IDLE;
        public float phaseAge;
        public float brightness = .35F;
    }
    private final class VegetationLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        VegetationLayer() { super(BoggedEndermanRenderer.this); }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible && state.isInvisibleToPlayer && !state.appearsGlowing()) return;
            RenderType material = !state.isInvisible ? RenderTypes.entityCutout(DECORATIONS)
                    : state.isInvisibleToPlayer ? RenderTypes.outline(DECORATIONS) : RenderTypes.entityTranslucent(DECORATIONS);
            int color = state.isInvisible && !state.isInvisibleToPlayer ? 654311423 : -1;
            collector.order(1).submitModel(ornaments, state, poses, material, light, getOverlayCoords(state, 0), color, null, state.outlineColor, null);
        }
    }
    private final class GreenEyesLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        GreenEyesLayer() { super(BoggedEndermanRenderer.this); }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            int channel = Math.round(255 * Mth.clamp(((BoggedRenderState)state).brightness, 0, 1));
            int tint = 0xFF000000 | channel << 16 | channel << 8 | channel;
            collector.order(2).submitModel(getParentModel(), state, poses, EYES, LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor, null);
        }
    }
}
