package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.NightmareEnderman;
import com.ronm19.enderism.entity.NightmareEnderman.CombatPhase;
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

/** One vanilla skeleton renders the physical hunter and cosmetic pre-hunt silhouettes. */
public final class NightmareEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("nightmare_enderman");
    private static final RenderType EYES = RenderTypes.eyes(texture("nightmare_enderman_eyes"));

    public NightmareEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new NightmareModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new RedEyesLayer());
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/nightmare_enderman/" + name + ".png");
    }

    @Override
    public Identifier getTextureLocation(EndermanRenderState state) {
        return BODY;
    }

    @Override
    public NightmareRenderState createRenderState() {
        return new NightmareRenderState();
    }

    @Override
    public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof NightmareEnderman nightmare) {
            var render = (NightmareRenderState)state;
            render.phase = nightmare.getCombatPhase();
            render.phaseAge = Math.max(0, nightmare.level().getGameTime() - nightmare.getCombatPhaseStart() + partialTick);
            render.brightness = Mth.clamp(nightmare.getEyeBrightness(), 0, 1);
            // The reveal has its own pose; the vanilla head jitter would conceal it.
            state.isCreepy = false;
        }
    }

    @Override
    protected @Nullable RenderType getRenderType(EndermanRenderState state, boolean visible,
                                                 boolean transparent, boolean glowing) {
        if (((NightmareRenderState)state).apparition) return RenderTypes.entityTranslucent(BODY);
        return super.getRenderType(state, visible, transparent, glowing);
    }

    @Override
    protected int getModelTint(EndermanRenderState state) {
        var nightmare = (NightmareRenderState)state;
        return nightmare.apparition ? Math.round(Mth.clamp(nightmare.opacity, 0, 1) * 255) << 24 | 0xFFFFFF : -1;
    }

    public static final class NightmareRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.IDLE;
        public float phaseAge;
        public float brightness = .75F;
        public boolean apparition;
        public float opacity = .85F;
    }

    private static final class NightmareModel extends EndermanModel<EndermanRenderState> {
        private NightmareModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            if (!state.carriedBlock.isEmpty()) return;
            var nightmare = (NightmareRenderState)state;
            float breath = Mth.sin(state.ageInTicks * .105F);
            switch (nightmare.phase) {
                case IDLE -> {
                    this.rightArm.zRot += .025F * breath;
                    this.leftArm.zRot -= .025F * breath;
                }
                case OBSERVING, ILLUSIONS -> {
                    this.head.zRot = .055F * breath;
                    this.rightArm.xRot = -.025F;
                    this.leftArm.xRot = -.025F;
                }
                case STALKING -> {
                    this.head.xRot -= .13F;
                    this.rightArm.xRot -= .1F;
                    this.leftArm.xRot -= .1F;
                }
                case REVEAL -> {
                    float tension = Mth.sin(Mth.clamp(nightmare.phaseAge / 12, 0, 1) * (float)Math.PI);
                    this.head.xRot -= .18F * tension;
                    this.rightArm.zRot = -.19F * tension;
                    this.leftArm.zRot = .19F * tension;
                    this.rightArm.xRot = -.3F * tension;
                    this.leftArm.xRot = -.3F * tension;
                }
                case PURSUIT -> {
                    this.rightArm.xRot *= 1.28F;
                    this.leftArm.xRot *= 1.28F;
                    this.head.xRot -= .08F;
                }
                case GRASP -> {
                    float swing = Mth.sin(Mth.clamp(nightmare.phaseAge / 9, 0, 1) * (float)Math.PI);
                    this.rightArm.xRot = -.24F - 1.45F * swing;
                    this.rightArm.yRot = -.34F * swing;
                    this.leftArm.xRot = -.38F * swing;
                }
                case SEARCHING -> this.head.yRot += .12F * breath;
            }
        }
    }

    private final class RedEyesLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private RedEyesLayer() {
            super(NightmareEndermanRenderer.this);
        }

        @Override
        public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state,
                           float yRot, float xRot) {
            if (state.isInvisible) return;
            var nightmare = (NightmareRenderState)state;
            float opacity = nightmare.apparition ? nightmare.opacity : 1;
            int value = Math.round(255 * Mth.clamp(nightmare.brightness * opacity, 0, 1));
            int color = 0xFF000000 | value << 16 | value << 8 | value;
            collector.order(1).submitModel(this.getParentModel(), state, poses, EYES,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, 0, null);
        }
    }
}
