package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.DemonEnderman;
import com.ronm19.enderism.entity.DemonEnderman.CombatPhase;
import net.minecraft.client.model.Model;
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
import net.minecraft.util.Unit;
import net.minecraft.world.entity.monster.EnderMan;

public final class DemonEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("demon_enderman");
    private static final Identifier HORNS = texture("demon_enderman_horns");
    private static final RenderType EYES = RenderTypes.eyes(texture("demon_enderman_eyes"));
    private static final RenderType CRIMSON = RenderTypes.eyes(texture("demon_enderman_glow"));

    public DemonEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = .55F;
        this.model = new DemonEndermanModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new HornsLayer(context));
        this.addLayer(new GlowLayer(CRIMSON, false));
        this.addLayer(new GlowLayer(EYES, true));
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/demon_enderman/" + name + ".png");
    }

    @Override
    public Identifier getTextureLocation(EndermanRenderState state) {
        return BODY;
    }

    @Override
    public DemonRenderState createRenderState() {
        return new DemonRenderState();
    }

    @Override
    public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof DemonEnderman demon) {
            var render = (DemonRenderState)state;
            render.phase = demon.getCombatPhase();
            render.phaseAge = Math.max(0.0F, demon.level().getGameTime() - demon.getCombatPhaseStart() + partialTick);
            render.brightness = Mth.clamp(demon.getEyeBrightness(), 0, 1);
            render.frenzied = demon.isFrenzied();
            // Keep the horned head inside its normal height even while attacking.
            state.isCreepy = false;
        }
    }

    public static final class DemonRenderState extends EndermanRenderState {
        CombatPhase phase = CombatPhase.IDLE;
        float phaseAge;
        float brightness = .8F;
        boolean frenzied;
    }

    private final class HornsLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private final Model.Simple horns;

        private HornsLayer(EntityRendererProvider.Context context) {
            super(DemonEndermanRenderer.this);
            this.horns = new Model.Simple(context.bakeLayer(DemonEndermanModel.LAYER), RenderTypes::entityCutout);
        }

        @Override
        public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                           EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible && state.isInvisibleToPlayer && !state.appearsGlowing()) {
                return;
            }
            RenderType material = !state.isInvisible ? this.horns.renderType(HORNS)
                    : state.isInvisibleToPlayer ? RenderTypes.outline(HORNS)
                    : RenderTypes.entityTranslucentCullItemTarget(HORNS);
            int color = state.isInvisible && !state.isInvisibleToPlayer ? 654311423 : -1;
            poses.pushPose();
            ((DemonEndermanModel)this.getParentModel()).applyHeadTransform(poses);
            collector.order(1).submitModel(this.horns, Unit.INSTANCE, poses, material,
                    light, getOverlayCoords(state, 0), color, null, state.outlineColor, null);
            poses.popPose();
        }
    }

    private final class GlowLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private final RenderType material;
        private final boolean eyes;

        private GlowLayer(RenderType material, boolean eyes) {
            super(DemonEndermanRenderer.this);
            this.material = material;
            this.eyes = eyes;
        }

        @Override
        public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                           EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) {
                return;
            }
            float brightness = ((DemonRenderState)state).brightness;
            int channel = Math.round(255.0F * (this.eyes ? brightness : .6F + .4F * brightness));
            int color = 0xFF000000 | channel << 16 | channel << 8 | channel;
            collector.order(this.eyes ? 3 : 2).submitModel(this.getParentModel(), state, poses, this.material,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}

