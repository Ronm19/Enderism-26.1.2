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

/** Exactly two upright legs and two arms; bulk preserves every native Enderman UV rectangle. */
public final class RavagerEndermanModel extends EndermanModel<EndermanRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "ravager_enderman"), "siege_armor");

    RavagerEndermanModel(ModelPart root) { super(root); }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition head = mesh.getRoot().addOrReplaceChild("head_armor", CubeListBuilder.create(), PartPose.ZERO);
        for (int side : new int[] {-1, 1}) {
            PartDefinition horn = head.addOrReplaceChild(side < 0 ? "right_horn" : "left_horn",
                    CubeListBuilder.create().texOffs(56, 24).addBox(-1, -1, -1, 2, 2, 2),
                    PartPose.offset(side * 3.9F, -6.7F, .2F));
            PartDefinition lower = horn.addOrReplaceChild("lower", CubeListBuilder.create().texOffs(56, 24)
                    .addBox(-1, -2.8F, -1, 2, 3, 2),
                    PartPose.offsetAndRotation(side * .45F, -.7F, 0, 0, 0, side * .58F));
            PartDefinition curve = lower.addOrReplaceChild("curve", CubeListBuilder.create().texOffs(56, 24)
                    .addBox(-.75F, -2.5F, -.75F, 1.5F, 3, 1.5F),
                    PartPose.offsetAndRotation(0, -2.4F, 0, 0, 0, -side * .84F));
            curve.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(56, 24)
                    .addBox(-.5F, -2.1F, -.5F, 1, 2.2F, 1),
                    PartPose.offsetAndRotation(0, -2.15F, 0, 0, 0, -side * .28F));
            unit(head, side < 0 ? "right_brow" : "left_brow", 56, 5,
                    side * 2.2F, -4.9F, -4.05F, 2.8F, .65F, .65F);
        }
        unit(head, "snout", 56, 5, 0, -2.5F, -4.25F, 2.2F, 2.0F, 1.3F);
        PartDefinition torso = mesh.getRoot().addOrReplaceChild("torso_armor", CubeListBuilder.create(), PartPose.ZERO);
        for (int side : new int[] {-1, 1}) {
            unit(torso, side < 0 ? "right_pauldron" : "left_pauldron", 56, 5,
                    side * 5.4F, .4F, 0, 3.4F, 2.0F, 4.8F);
            unit(torso, side < 0 ? "right_bone_rim" : "left_bone_rim", 56, 24,
                    side * 5.4F, 1.12F, 0, 3.6F, .35F, 5.0F);
            torso.addOrReplaceChild(side < 0 ? "right_shoulder_spike" : "left_shoulder_spike",
                    CubeListBuilder.create().texOffs(56, 24).addBox(-.45F, -1.8F, -.45F, .9F, 2, .9F),
                    PartPose.offsetAndRotation(side * 5.1F, -.4F, .25F, 0, 0, side * .24F));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }
    private static void unit(PartDefinition parent, String name, int u, int v,
                             float x, float y, float z, float sx, float sy, float sz) {
        parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(-.5F, -.5F, -.5F, 1, 1, 1),
                new PartPose(x, y, z, 0, 0, 0, sx, sy, sz));
    }
    @Override public void setupAnim(EndermanRenderState state) {
        super.setupAnim(state);
        this.head.y = -15.5F;
        this.head.xScale = 1.1F; this.head.yScale = 1.1F; this.head.zScale = 1.1F;
        this.body.y = -15.5F;
        this.body.xScale = 1.4F; this.body.yScale = 1.25F; this.body.zScale = 1.7F;
        this.rightArm.x = -7.8F; this.leftArm.x = 7.8F;
        this.rightArm.y = this.leftArm.y = -13.5F;
        this.rightArm.xScale = this.leftArm.xScale = 2;
        this.rightArm.yScale = this.leftArm.yScale = 1;
        this.rightArm.zScale = this.leftArm.zScale = 1.9F;
        this.rightLeg.x = -3.5F; this.leftLeg.x = 3.5F;
        this.rightLeg.xScale = this.leftLeg.xScale = 1.65F;
        this.rightLeg.yScale = this.leftLeg.yScale = 1;
        this.rightLeg.zScale = this.leftLeg.zScale = 1.65F;
        var render = (RavagerEndermanRenderer.RavagerRenderState)state;
        float breath = Mth.sin(state.ageInTicks * .12F), rise = Mth.clamp(render.phaseAge / 14, 0, 1);
        float stride = Mth.cos(state.walkAnimationPos * .6662F) * Math.min(1, state.walkAnimationSpeed) * .65F;
        this.rightLeg.xRot = stride; this.leftLeg.xRot = -stride;
        this.rightArm.xRot = -stride * .55F; this.leftArm.xRot = stride * .55F;
        this.rightArm.zRot = -.035F + .012F * breath; this.leftArm.zRot = .035F - .012F * breath;
        switch (render.phase) {
            case IDLE, GUARDING -> this.body.zScale += .018F * breath;
            case PURSUIT -> lean(.08F);
            case SEARCHING -> this.head.yRot += .15F * breath;
            case WRATH_WINDUP -> {
                this.rightArm.xRot = -1.75F * rise; this.leftArm.xRot = -.55F * rise;
                this.rightArm.yRot = -.22F * rise; lean(-.055F * rise);
            }
            case WRATH_STRIKE -> {
                // The server commits the hit at phase age zero; the remaining frames retract from contact.
                float followThrough = Mth.clamp(render.phaseAge / 8, 0, 1);
                this.rightArm.xRot = -1.2F + .8F * followThrough;
                this.leftArm.xRot = -.2F - .4F * (1 - followThrough); lean(.16F * (1 - followThrough));
            }
            case CHARGE_WINDUP -> {
                this.head.xRot += .2F * rise; this.rightArm.xRot = this.leftArm.xRot = -.4F * rise;
                lean(.14F * rise);
            }
            case CHARGING -> {
                float run = Mth.cos(state.walkAnimationPos * .8F + render.phaseAge * .35F) * .56F;
                this.rightLeg.xRot = run; this.leftLeg.xRot = -run;
                this.rightArm.xRot = -.15F - run * .75F; this.leftArm.xRot = -.15F + run * .75F;
                this.head.xRot += .15F; lean(.17F);
            }
            case ROAR_WINDUP, ROARING -> {
                float amount = render.phase == com.ronm19.enderism.entity.RavagerEnderman.CombatPhase.ROARING ? 1 : rise;
                this.head.xRot -= .4F * amount;
                this.rightArm.xRot = this.leftArm.xRot = -.4F * amount;
                this.rightArm.zRot = .2F * amount; this.leftArm.zRot = -.2F * amount;
                lean(-.075F * amount);
            }
            case RECOVERY -> lean(.12F * (1 - rise));
        }
    }
    private void lean(float angle) {
        float sin = Mth.sin(angle), cos = Mth.cos(angle);
        for (ModelPart part : new ModelPart[] {this.body, this.head, this.rightArm, this.leftArm}) {
            float y = part.y + 5;
            part.y = y * cos - part.z * sin - 5; part.z = y * sin + part.z * cos; part.xRot += angle;
        }
    }
    public static final class Ornaments extends Model<EndermanRenderState> {
        private final RavagerEndermanModel body;
        Ornaments(ModelPart root, RavagerEndermanModel body) { super(root, RenderTypes::entityCutout); this.body = body; }
        @Override public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state); this.body.setupAnim(state);
            follow(this.root, this.body.root());
            follow(this.root.getChild("head_armor"), this.body.head);
            follow(this.root.getChild("torso_armor"), this.body.body);
        }
        private static void follow(ModelPart target, ModelPart source) {
            target.loadPose(source.storePose());
            target.xScale = source.xScale; target.yScale = source.yScale; target.zScale = source.zScale;
        }
    }
}
