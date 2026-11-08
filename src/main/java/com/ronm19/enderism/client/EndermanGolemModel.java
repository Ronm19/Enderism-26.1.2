package com.ronm19.enderism.client;

import com.ronm19.enderism.Enderism;
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

/** Two weight-bearing legs and two enormous fists, using the original Enderman UVs. */
public final class EndermanGolemModel extends EndermanModel<EndermanRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "enderman_golem"), "guardian_stone");
    private static final float BODY_SCALE = 2.1F;
    EndermanGolemModel(ModelPart root) { super(root); }

    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition();
        var head = mesh.getRoot().addOrReplaceChild("head_armor", CubeListBuilder.create(), PartPose.ZERO);
        unit(head, "right_brow", 56, 5, -2.1F, -5.0F, -4.05F, 3.6F, .65F, .65F);
        unit(head, "left_brow", 56, 5, 2.1F, -5.0F, -4.05F, 3.6F, .65F, .65F);
        unit(head, "stone_jaw", 56, 14, 0, -1.6F, -4.08F, 5.1F, 1.6F, .45F);
        var torso = mesh.getRoot().addOrReplaceChild("torso_armor", CubeListBuilder.create(), PartPose.ZERO);
        for (int side : new int[]{-1, 1}) {
            unit(torso, side < 0 ? "right_pauldron" : "left_pauldron", 56, 24,
                    side * 4.8F, .65F, 0, 3.0F, 2.25F, 5.8F);
            unit(torso, side < 0 ? "right_rim" : "left_rim", 60, 24,
                    side * 4.8F, 1.6F, 0, 3.1F, .45F, 6.0F);
            unit(torso, side < 0 ? "right_hip" : "left_hip", 56, 24,
                    side * 3.3F, 10.2F, 0, 1.2F, 2.4F, 4.5F);
        }
        unit(torso, "chest_core", 56, 24, 0, 4.1F, -2.45F, 6.6F, 6.2F, .85F);
        unit(torso, "right_core_pillar", 56, 18, -.8F, 4.5F, -2.96F, .4F, 2.4F, .2F);
        unit(torso, "left_core_pillar", 56, 18, .8F, 4.5F, -2.96F, .4F, 2.4F, .2F);
        unit(torso, "core_lintel", 56, 18, 0, 3.15F, -2.96F, 2.1F, .45F, .2F);
        for (int segment = 0; segment < 5; segment++) {
            unit(torso, "ancient_vine_" + segment, 56, 11,
                    -3.05F + (segment % 2) * .35F, 1.4F + segment * 1.15F, -2.98F, .45F, 1.25F, .2F);
        }
        for (int side : new int[]{-1, 1}) {
            var arm = mesh.getRoot().addOrReplaceChild(side < 0 ? "right_gauntlet" : "left_gauntlet", CubeListBuilder.create(), PartPose.ZERO);
            unit(arm, "heavy_cuff", 56, 24, 0, 22.0F, 0, 2.55F, 2.8F, 2.5F);
            unit(arm, "stone_palm", 56, 14, 0, 25.0F, 0, 2.45F, 4.0F, 2.35F);
            for (int digit = 0; digit < 3; digit++)
                unit(arm, "finger_" + digit, 56, 14, -.8F + digit * .8F, 28.1F, -.24F, .7F, 2.7F, 1.75F);
            arm.addOrReplaceChild("thumb", CubeListBuilder.create().texOffs(56, 14).addBox(-.5F, -.5F, -.5F, 1, 1, 1),
                    new PartPose(-side * 1.35F, 25.5F, -.25F, 0, 0, side * .3F, .85F, 2.3F, 1.6F));
            var leg = mesh.getRoot().addOrReplaceChild(side < 0 ? "right_greave" : "left_greave", CubeListBuilder.create(), PartPose.ZERO);
            unit(leg, "knee_plate", 56, 24, 0, 14.2F, -1.06F, 2.45F, 2.6F, .55F);
            unit(leg, "stone_boot", 56, 24, 0, 28.5F, -.25F, 2.75F, 3, 3.1F);
        }
        return LayerDefinition.create(mesh, 64, 32);
    }
    private static void unit(PartDefinition parent, String name, int u, int v, float x, float y, float z, float sx, float sy, float sz) {
        parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(-.5F, -.5F, -.5F, 1, 1, 1),
                new PartPose(x, y, z, 0, 0, 0, sx, sy, sz));
    }
    @Override public void setupAnim(EndermanRenderState state) {
        super.setupAnim(state);
        // Fixed base anatomy; the native renderer applies the synchronized SCALE attribute once afterwards.
        this.root().xScale = this.root().yScale = this.root().zScale = BODY_SCALE;
        this.root().y = 24 * (1 - BODY_SCALE);
        this.head.y = -16.5F;
        this.body.y = -16; this.body.xScale = 1.3F; this.body.yScale = 1.45F; this.body.zScale = 1.7F;
        this.rightArm.x = -6.7F; this.leftArm.x = 6.7F;
        this.rightArm.y = this.leftArm.y = -13.7F;
        this.rightArm.xScale = this.leftArm.xScale = 2.1F;
        this.rightArm.yScale = this.leftArm.yScale = .95F;
        this.rightArm.zScale = this.leftArm.zScale = 2.0F;
        this.rightLeg.x = -3.2F; this.leftLeg.x = 3.2F;
        this.rightLeg.y = this.leftLeg.y = 0;
        this.rightLeg.xScale = this.leftLeg.xScale = 2.0F;
        this.rightLeg.yScale = this.leftLeg.yScale = .8F;
        this.rightLeg.zScale = this.leftLeg.zScale = 2.0F;
        var render = (EndermanGolemRenderer.GolemRenderState)state;
        float breath = Mth.sin(state.ageInTicks * .055F), rise = Mth.clamp(render.phaseAge / 16, 0, 1);
        float stride = Mth.cos(state.walkAnimationPos * .45F) * Math.min(1, state.walkAnimationSpeed) * .32F;
        this.rightLeg.xRot = stride; this.leftLeg.xRot = -stride;
        this.rightArm.xRot = -stride * .42F; this.leftArm.xRot = stride * .42F;
        this.rightArm.zRot = -.015F; this.leftArm.zRot = .015F;
        switch (render.phase) {
            case IDLE, OBSERVING, PATROLLING -> {
                this.body.zScale += .012F * breath;
                if (render.phase == com.ronm19.enderism.entity.EndermanGolem.CombatPhase.OBSERVING) this.head.yRot += .035F * breath;
            }
            case DEFENSIVE_WARNING, DEFENDING -> {
                this.rightArm.xRot = -.28F * rise; this.leftArm.xRot = -.28F * rise;
                this.head.xRot += .07F * rise;
            }
            case INTERCEPTING -> {
                this.rightArm.xRot = -.15F - stride * .55F; this.leftArm.xRot = -.3F + stride * .55F;
                this.head.xRot += .07F;
            }
            case CRUSHER_WINDUP -> {
                this.rightArm.xRot = -1.45F * rise; this.leftArm.xRot = -.45F * rise;
                this.rightArm.yRot = -.16F * rise;
            }
            case CRUSHER_STRIKE -> {
                float retract = Mth.clamp(render.phaseAge / 8, 0, 1);
                this.rightArm.xRot = -.65F * (1 - retract); this.leftArm.xRot = -.3F * (1 - retract);
            }
            case JUDGMENT_WINDUP -> {
                this.rightArm.xRot = this.leftArm.xRot = -1.7F * rise;
                this.rightArm.yRot = -.1F * rise; this.leftArm.yRot = .1F * rise;
                this.head.xRot -= .08F * rise;
            }
            case JUDGMENT_IMPACT -> {
                float retract = Mth.clamp(render.phaseAge / 12, 0, 1);
                this.rightArm.xRot = this.leftArm.xRot = .02F * (1 - retract);
                this.head.xRot += .2F * (1 - retract);
                float weight = 8.5F * (1 - retract);
                this.body.y += weight; this.head.y += weight; this.rightArm.y += weight; this.leftArm.y += weight;
            }
            case DE_ESCALATING, RECOVERY -> {
                this.rightArm.xRot = this.leftArm.xRot = -.25F * (1 - rise);
            }
        }
    }
    public static final class Ornaments extends Model<EndermanRenderState> {
        private final EndermanGolemModel body;
        Ornaments(ModelPart root, EndermanGolemModel body) { super(root, RenderTypes::entityCutout); this.body = body; }
        @Override public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state); this.body.setupAnim(state);
            follow(this.root, this.body.root());
            follow(this.root.getChild("head_armor"), this.body.head);
            follow(this.root.getChild("torso_armor"), this.body.body);
            follow(this.root.getChild("right_gauntlet"), this.body.rightArm); follow(this.root.getChild("left_gauntlet"), this.body.leftArm);
            follow(this.root.getChild("right_greave"), this.body.rightLeg); follow(this.root.getChild("left_greave"), this.body.leftLeg);
        }
        private static void follow(ModelPart target, ModelPart source) {
            target.loadPose(source.storePose()); target.xScale = source.xScale; target.yScale = source.yScale; target.zScale = source.zScale;
        }
    }
}
