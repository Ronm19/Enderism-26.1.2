package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.SkeletonEnderman;
import com.ronm19.enderism.entity.SkeletonEnderman.CombatPhase;
import com.ronm19.enderism.entity.projectile.SkeletonArcaneArrow.Element;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.model.object.projectile.ArrowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.CarriedBlockLayer;
import net.minecraft.client.renderer.entity.layers.EnderEyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.AABB;

/** Supplied skeletal atlas and native limbs; conjured formation is presentation, never extra projectiles. */
public final class SkeletonEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("skeleton_enderman");
    private static final RenderType EYES = RenderTypes.eyes(texture("skeleton_enderman_eyes"));
    private static final Element[] ELEMENTS = Element.values();
    private final ArrowModel formationArrow;
    public SkeletonEndermanRenderer(EntityRendererProvider.Context context) {
        super(context); this.model = new SkeletonEndermanModel(this.model.root());
        this.formationArrow = new ArrowModel(context.bakeLayer(ModelLayers.ARROW));
        this.layers.removeIf(layer -> layer instanceof EnderEyesLayer || layer instanceof CarriedBlockLayer);
        this.addLayer(new SpectralEyes()); this.addLayer(new ArrowFormation());
    }
    private static Identifier texture(String name) { return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/skeleton_enderman/" + name + ".png"); }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public SkeletonRenderState createRenderState() { return new SkeletonRenderState(); }
    @Override protected AABB getBoundingBoxForCulling(EnderMan entity) {
        return entity instanceof SkeletonEnderman skeleton && skeleton.getVolleyBudget() > 0
                ? super.getBoundingBoxForCulling(entity).inflate(1.25, .25, 1.25) : super.getBoundingBoxForCulling(entity);
    }
    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks); state.carriedBlock.clear(); state.isCreepy = false;
        if (entity instanceof SkeletonEnderman skeleton) {
            var render = (SkeletonRenderState)state;
            render.phase = skeleton.getCombatPhase();
            render.phaseAge = Math.max(0, skeleton.level().getGameTime() - skeleton.getCombatPhaseStart() + partialTicks);
            render.brightness = Mth.clamp(skeleton.getEyeBrightness(), 0, 1);
            render.volleyBudget = Math.clamp(skeleton.getVolleyBudget(), 0, 20);
            render.element = skeleton.getSelectedElement();
        }
    }
    public static int formationArrowCount(SkeletonRenderState state) {
        return switch (state.phase) {
            case CURSED_WINDUP -> Math.clamp(state.volleyBudget, 0, 1);
            case ARCANE_WINDUP, DEATHS_WINDUP -> Math.clamp(state.volleyBudget, 0, 20);
            default -> 0;
        };
    }
    public static final class SkeletonRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.IDLE;
        public float phaseAge, brightness = .45F;
        public int volleyBudget;
        public Element element = Element.FREEZING;
    }
    private final class SpectralEyes extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        SpectralEyes() { super(SkeletonEndermanRenderer.this); }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            int channel = Math.round(255 * ((SkeletonRenderState)state).brightness);
            int tint = 0xFF000000 | channel << 16 | channel << 8 | channel;
            collector.order(2).submitModel(getParentModel(), state, poses, EYES, LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor, null);
        }
    }
    private final class ArrowFormation extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        ArrowFormation() { super(SkeletonEndermanRenderer.this); }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            var render = (SkeletonRenderState)state; int count = formationArrowCount(render);
            if (count == 0) return;
            float growth = Mth.clamp(render.phaseAge / 8, 0, 1);
            for (int i = 0; i < count; i++) {
                double angle = (i + .25) * Math.PI * 2 / Math.max(1, count);
                double x = count == 1 ? -.32 : Math.cos(angle) * .85;
                double y = count == 1 ? -.52 : -.35 + Math.sin(angle) * .7;
                poses.pushPose(); poses.translate(x, y, -.65);
                poses.mulPose(Axis.YP.rotationDegrees(90)); poses.mulPose(Axis.ZP.rotationDegrees(-8));
                float scale = .22F + .16F * growth; poses.scale(scale, scale, scale);
                var arrowState = new ArrowRenderState();
                Element element = render.phase == CombatPhase.CURSED_WINDUP ? render.element
                        : ELEMENTS[(render.element.ordinal() + i) % ELEMENTS.length];
                collector.order(1).submitModel(formationArrow, arrowState, poses,
                        RenderTypes.entityCutout(SkeletonArcaneArrowRenderer.texture(element)), LightCoordsUtil.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);
                poses.popPose();
            }
        }
    }
}
