package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.AngelEnderman;
import com.ronm19.enderism.entity.AngelEnderman.CombatPhase;
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

public final class AngelEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("angel_enderman");
    private static final Identifier GLOW = texture("angel_enderman_glow");
    private static final RenderType EYES = RenderTypes.eyes(texture("angel_enderman_eyes"));
    private static final RenderType RADIANCE = RenderTypes.eyes(GLOW);
    private final AngelEndermanModel.Ornaments ornaments;

    public AngelEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = .5F;
        var angel = new AngelEndermanModel(this.model.root());
        this.model = angel;
        this.ornaments = new AngelEndermanModel.Ornaments(context.bakeLayer(AngelEndermanModel.LAYER), angel);
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new OrnamentsLayer());
        this.addLayer(new GlowLayer(RADIANCE, false));
        this.addLayer(new GlowLayer(EYES, true));
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/angel_enderman/" + name + ".png");
    }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public AngelRenderState createRenderState() { return new AngelRenderState(); }
    public AngelEndermanModel.Ornaments getOrnaments() { return this.ornaments; }

    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof AngelEnderman angel) {
            var render = (AngelRenderState)state;
            render.phase = angel.getCombatPhase();
            render.phaseAge = Math.max(0, angel.level().getGameTime() - angel.getCombatPhaseStart() + partialTick);
            render.brightness = Mth.clamp(angel.getEyeBrightness(), 0, 1);
            state.isCreepy = false;
        }
    }

    public static final class AngelRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.IDLE;
        public float phaseAge;
        public float brightness = .55F;
    }

    private final class OrnamentsLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        OrnamentsLayer() { super(AngelEndermanRenderer.this); }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                                     EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible && state.isInvisibleToPlayer && !state.appearsGlowing()) return;
            RenderType material = !state.isInvisible ? RenderTypes.entityCutout(BODY)
                    : state.isInvisibleToPlayer ? RenderTypes.outline(BODY) : RenderTypes.entityTranslucent(BODY);
            int color = state.isInvisible && !state.isInvisibleToPlayer ? 654311423 : -1;
            collector.order(1).submitModel(ornaments, state, poses, material, light,
                    getOverlayCoords(state, 0), color, null, state.outlineColor, null);
            if (!state.isInvisible) {
                int channel = Math.round(255 * (.55F + .25F * ((AngelRenderState)state).brightness));
                int glow = 0xFF000000 | channel << 16 | channel << 8 | channel;
                collector.order(2).submitModel(ornaments, state, poses, RADIANCE, LightCoordsUtil.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, glow, null, state.outlineColor, null);
            }
        }
    }

    private final class GlowLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private final RenderType material;
        private final boolean eyes;
        GlowLayer(RenderType material, boolean eyes) { super(AngelEndermanRenderer.this); this.material = material; this.eyes = eyes; }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                                     EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            float brightness = ((AngelRenderState)state).brightness;
            int channel = Math.round(255 * (this.eyes ? brightness : .42F + .3F * brightness));
            int color = 0xFF000000 | channel << 16 | channel << 8 | channel;
            collector.order(this.eyes ? 4 : 3).submitModel(this.getParentModel(), state, poses, this.material,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}

