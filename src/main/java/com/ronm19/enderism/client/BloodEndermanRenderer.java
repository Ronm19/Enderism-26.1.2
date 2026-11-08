package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.BloodEnderman;
import com.ronm19.enderism.entity.BloodEnderman.CombatPhase;
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

/** Native Enderman anatomy with separate melee, lance, and brief healing presentation. */
public final class BloodEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("blood_enderman");
    private static final RenderType EYES = RenderTypes.eyes(texture("blood_enderman_eyes"));
    private static final RenderType VEINS = RenderTypes.eyes(texture("blood_enderman_glow"));

    public BloodEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = .55F;
        this.model = new BloodModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new GlowLayer(VEINS, false));
        this.addLayer(new GlowLayer(EYES, true));
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/blood_enderman/" + name + ".png");
    }

    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public BloodRenderState createRenderState() { return new BloodRenderState(); }

    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof BloodEnderman blood) {
            var render = (BloodRenderState)state;
            render.phase = blood.getCombatPhase();
            render.phaseAge = Math.max(0, blood.level().getGameTime() - blood.getCombatPhaseStart() + partialTick);
            render.brightness = Mth.clamp(blood.getEyeBrightness(), 0, 1);
            render.bloodlust = blood.isBloodlustActive();
            render.killAura = blood.getBloodlustAuraEnd() > blood.level().getGameTime();
            state.isCreepy = false;
        }
    }

    public static final class BloodRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.IDLE;
        public float phaseAge;
        public float brightness = .82F;
        public boolean bloodlust;
        public boolean killAura;
    }

    private static final class BloodModel extends EndermanModel<EndermanRenderState> {
        private BloodModel(ModelPart root) { super(root); }

        @Override public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            if (!state.carriedBlock.isEmpty()) return;
            var blood = (BloodRenderState)state;
            float breath = Mth.sin(state.ageInTicks * .14F);
            float rise = Mth.clamp(blood.phaseAge / 10, 0, 1);
            switch (blood.phase) {
                case IDLE -> {
                    this.rightArm.zRot += .025F * breath;
                    this.leftArm.zRot -= .025F * breath;
                }
                case PURSUIT -> {
                    this.rightArm.xRot *= 1.18F;
                    this.leftArm.xRot *= 1.18F;
                    this.head.xRot -= .07F;
                }
                case BUTCHERY -> {
                    float strike = Mth.sin(Mth.clamp(blood.phaseAge / 9, 0, 1) * Mth.PI);
                    this.rightArm.xRot = -.25F - 1.9F * strike;
                    this.rightArm.yRot = -.45F * strike;
                    this.rightArm.zRot = -.2F * strike;
                    this.leftArm.xRot = -.1F - .4F * strike;
                    this.head.xRot -= .12F * strike;
                }
                case LANCE_WINDUP -> {
                    this.rightArm.xRot = -1.5F * rise;
                    this.rightArm.yRot = -.42F * rise;
                    this.leftArm.xRot = -.55F * rise;
                    this.leftArm.yRot = .22F * rise;
                    this.head.xRot -= .1F * rise;
                }
                case LANCE_RELEASE -> {
                    float reach = 1 - Mth.clamp(blood.phaseAge / 8, 0, 1);
                    this.rightArm.xRot = -1.55F * reach;
                    this.rightArm.yRot = .16F * reach;
                    this.leftArm.xRot = -.35F * reach;
                }
                case RECOVERY -> {
                    this.rightArm.xRot = -.3F * (1 - rise);
                    this.leftArm.xRot = -.15F * (1 - rise);
                }
                case SEARCHING -> this.head.yRot += .15F * breath;
            }
            if (blood.killAura) this.body.zRot += .012F * breath;
        }
    }

    private final class GlowLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        private final RenderType material;
        private final boolean eyes;

        private GlowLayer(RenderType material, boolean eyes) {
            super(BloodEndermanRenderer.this);
            this.material = material;
            this.eyes = eyes;
        }

        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                                     EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            var blood = (BloodRenderState)state;
            float intensity = this.eyes ? blood.brightness
                    : blood.killAura ? .95F : blood.bloodlust ? .78F : .58F;
            int channel = Math.round(255 * Mth.clamp(intensity, 0, 1));
            int color = 0xFF000000 | channel << 16 | channel << 8 | channel;
            collector.order(this.eyes ? 2 : 1).submitModel(this.getParentModel(), state, poses, this.material,
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null);
        }
    }
}
