package com.ronm19.enderism.client;

import com.ronm19.enderism.Enderism;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Native Enderman atlas and grounded limbs, with a small separate sensor-tendril mesh. */
public final class SculkEndermanModel extends EndermanModel<EndermanRenderState> {
    public static final float BODY_SCALE = 1.10F;
    public static final ModelLayerLocation TENDRILS_LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "sculk_enderman"), "tendrils");

    public SculkEndermanModel(ModelPart root) { super(root); }

    public static LayerDefinition createTendrilsLayer() {
        var mesh = new MeshDefinition();
        var head = mesh.getRoot().addOrReplaceChild("head_growths", CubeListBuilder.create(), PartPose.ZERO);
        for (int side : new int[] {-1, 1}) {
            var sensor = head.addOrReplaceChild(side < 0 ? "right_sensor" : "left_sensor", CubeListBuilder.create(),
                    PartPose.offsetAndRotation(side * 3.8F, -7.3F, .35F, .05F, 0, side * .12F));
            segment(sensor, "base", 0, side * .35F, -.2F, 0, 1.3F, 1.35F, 1.2F);
            segment(sensor, "elbow", 8, side * 1.2F, -1.1F, 0, 1.2F, 1.25F, 1.05F);
            segment(sensor, "stem", 8, side * 1.75F, -2.35F, 0, 1.0F, 1.85F, 1.0F);
            segment(sensor, "tip", 16, side * 1.55F, -3.75F, 0, 1.15F, 1.05F, 1.15F);
            segment(sensor, "fork", 8, side * .65F, -2.15F, 0, .85F, 1.0F, .85F);
            var tine = head.addOrReplaceChild(side < 0 ? "right_tine" : "left_tine", CubeListBuilder.create(),
                    PartPose.offsetAndRotation(side * 1.8F, -7.85F, -2.65F, -.12F, 0, side * .1F));
            segment(tine, "stem", 8, 0, -.8F, 0, .75F, 1.65F, .75F);
            segment(tine, "tip", 16, side * .2F, -1.85F, 0, .8F, .7F, .8F);
        }
        var rear = head.addOrReplaceChild("rear_tine", CubeListBuilder.create(), PartPose.offset(0, -7.8F, 3.1F));
        segment(rear, "stem", 0, 0, -.8F, 0, .85F, 1.8F, .85F);
        segment(rear, "tip", 16, -.25F, -1.9F, 0, .85F, .75F, .85F);
        return LayerDefinition.create(mesh, 32, 16);
    }
    private static void segment(PartDefinition parent, String name, int u, float x, float y, float z, float sx, float sy, float sz) {
        parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, 0).addBox(-.5F, -.5F, -.5F, 1, 1, 1),
                new PartPose(x, y, z, 0, 0, 0, sx, sy, sz));
    }
    @Override public void setupAnim(EndermanRenderState state) {
        state.rightArmPose = state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        state.isUsingItem = false; state.carriedBlock.clear();
        super.setupAnim(state);
        this.root().xScale = this.root().yScale = this.root().zScale = BODY_SCALE;
        this.root().y = 24 - 25 * BODY_SCALE;
        if (state.deathTime > 0) return;
        var render = (SculkEndermanRenderer.SculkRenderState)state;
        float breath = Mth.sin(state.ageInTicks * .055F), rise = Mth.clamp(render.phaseAge / 10, 0, 1);
        switch (render.phase) {
            case LISTENING -> { this.head.xRot -= .035F; this.head.zRot += breath * .018F; this.rightArm.zRot += .012F * breath; this.leftArm.zRot -= .012F * breath; }
            case DETECTING -> { this.head.xRot -= .11F * rise; this.head.zRot += .08F * rise; this.rightArm.xRot *= .35F; this.leftArm.xRot *= .35F; }
            case INVESTIGATING, SEARCHING -> { this.head.yRot += .12F * breath; this.head.xRot -= .05F; this.rightArm.xRot *= .8F; this.leftArm.xRot *= .8F; }
            case PURSUING -> { lean(.055F); this.rightArm.xRot *= 1.12F; this.leftArm.xRot *= 1.12F; }
            case CRUSHER_WINDUP -> {
                this.rightArm.xRot = -1.95F * rise; this.leftArm.xRot = -1.35F * rise;
                this.rightArm.yRot = -.16F * rise; this.leftArm.yRot = .12F * rise;
                this.rightArm.zRot = -.1F * rise; this.leftArm.zRot = .12F * rise; lean(-.04F * rise);
            }
            case CRUSHER -> {
                float impact = 1 - Mth.clamp(render.phaseAge / 8, 0, 1);
                this.rightArm.xRot = -.25F - .95F * impact; this.leftArm.xRot = -.15F - .6F * impact;
                this.rightArm.yRot = -.15F * impact; this.leftArm.yRot = .12F * impact; lean(.13F * impact);
            }
            case RECOVERY -> { float amount = 1 - Mth.clamp(render.phaseAge / 10, 0, 1); lean(.08F * amount); this.rightArm.xRot = -.15F * amount; this.leftArm.xRot = -.12F * amount; }
        }
        // A short sensory pulse never replaces a committed physical strike.
        if (render.detectionPulseTicks > 0 && render.phase != com.ronm19.enderism.entity.SculkEnderman.CombatPhase.CRUSHER
                && render.phase != com.ronm19.enderism.entity.SculkEnderman.CombatPhase.CRUSHER_WINDUP
                && render.phase != com.ronm19.enderism.entity.SculkEnderman.CombatPhase.RECOVERY)
            this.head.zRot += .035F * Mth.clamp(render.detectionPulseTicks / 12F, 0, 1);
    }
    private void lean(float angle) {
        float sin = Mth.sin(angle), cos = Mth.cos(angle);
        for (var part : new ModelPart[] {this.body, this.head, this.rightArm, this.leftArm}) {
            float y = part.y + 5;
            part.y = y * cos - part.z * sin - 5; part.z = y * sin + part.z * cos; part.xRot += angle;
        }
    }
    public static final class Tendrils extends Model<EndermanRenderState> {
        private final SculkEndermanModel body;
        public Tendrils(ModelPart root, SculkEndermanModel body) { super(root, RenderTypes::entityCutout); this.body = body; }
        @Override public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state); this.body.setupAnim(state); follow(this.root, this.body.root());
            var growths = this.root.getChild("head_growths"); follow(growths, this.body.head);
            var render = (SculkEndermanRenderer.SculkRenderState)state;
            float pulse = Mth.clamp(render.detectionPulseTicks / 12F, 0, 1);
            float sway = Mth.sin(state.ageInTicks * .07F) * .035F;
            growths.getChild("right_sensor").zRot -= sway + .09F * pulse;
            growths.getChild("left_sensor").zRot += sway + .09F * pulse;
            growths.getChild("right_tine").xRot -= .04F * pulse;
            growths.getChild("left_tine").xRot -= .04F * pulse;
            growths.getChild("rear_tine").xRot += sway;
        }
        private static void follow(ModelPart target, ModelPart source) {
            target.loadPose(source.storePose()); target.xScale = source.xScale; target.yScale = source.yScale; target.zScale = source.zScale;
        }
    }
}
