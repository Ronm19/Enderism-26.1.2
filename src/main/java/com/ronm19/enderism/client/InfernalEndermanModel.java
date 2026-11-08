package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class InfernalEndermanModel extends EndermanModel<EndermanRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "infernal_enderman"), "horns");

    InfernalEndermanModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("right_horn", horn(false), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("left_horn", horn(true), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    private static CubeListBuilder horn(boolean left) {
        return CubeListBuilder.create().mirror(left)
                .texOffs(0, 0).addBox(left ? -5.25F : 3.25F, -7.75F, -3.25F, 2, 2, 2)
                .texOffs(0, 4).addBox(left ? -6.0F : 4.0F, -10.25F, -3.05F, 2, 3, 2)
                .texOffs(0, 9).addBox(left ? -5.65F : 4.65F, -12.75F, -2.7F, 1, 3, 1);
    }

    void applyHeadTransform(PoseStack poses) {
        this.root().translateAndRotate(poses);
        this.head.translateAndRotate(poses);
    }

    @Override
    public void setupAnim(EndermanRenderState state) {
        super.setupAnim(state);
        var infernal = (InfernalEndermanRenderer.InfernalRenderState) state;
        float breath = Mth.sin(state.ageInTicks * .15F);
        float rise = Mth.clamp(infernal.phaseAge / 10.0F, 0, 1);
        if (!state.carriedBlock.isEmpty()) {
            return;
        }
        switch (infernal.phase) {
            case LAVA_RUNNING -> {
                lean(.12F);
                this.rightArm.xRot *= 1.3F;
                this.leftArm.xRot *= 1.3F;
                this.rightLeg.xRot *= 1.2F;
                this.leftLeg.xRot *= 1.2F;
            }
            case RAMPAGE -> {
                float strike = Mth.sin(Mth.clamp(infernal.phaseAge / 10.0F, 0, 1) * (float)Math.PI);
                this.rightArm.xRot = -.24F - 1.92F * strike;
                this.rightArm.yRot = -.36F * strike;
                this.leftArm.xRot = -.62F * strike;
                lean(.18F * strike);
            }
            case CHARGE_WINDUP -> {
                lean(.42F * rise);
                this.head.xRot -= .19F * rise;
                this.rightArm.xRot = .58F * rise;
                this.leftArm.xRot = .58F * rise;
            }
            case CHARGING -> {
                lean(.42F);
                this.head.xRot -= .26F;
                this.rightArm.xRot = .47F + .15F * this.rightLeg.xRot;
                this.leftArm.xRot = .47F + .15F * this.leftLeg.xRot;
                this.rightLeg.xRot *= 1.55F;
                this.leftLeg.xRot *= 1.55F;
            }
            case METEOR_PREP -> summon(rise, .63F, breath);
            case METEOR_BARRAGE -> summon(1.0F, .63F, breath);
            case METEOR_FINAL -> summon(1.0F, .35F, breath);
            case RECOVERY -> lean(.11F * (1.0F - rise));
            case SEARCHING -> this.head.yRot += .11F * breath;
            case IDLE -> {
                this.rightArm.zRot += .025F * breath;
                this.leftArm.zRot -= .025F * breath;
            }
            case PURSUIT -> lean(.06F);
        }
    }

    private void summon(float rise, float spread, float breath) {
        this.rightArm.xRot = (-2.5F + .07F * breath) * rise;
        this.leftArm.xRot = (-2.5F - .07F * breath) * rise;
        this.rightArm.zRot = -spread * rise;
        this.leftArm.zRot = spread * rise;
        this.head.xRot -= .26F * rise;
        lean(-.08F * rise);
    }

    private void lean(float angle) {
        float sin = Mth.sin(angle);
        float cos = Mth.cos(angle);
        rotateFromHip(this.body, angle, sin, cos);
        rotateFromHip(this.head, angle, sin, cos);
        rotateFromHip(this.leftArm, angle, sin, cos);
        rotateFromHip(this.rightArm, angle, sin, cos);
    }

    private static void rotateFromHip(ModelPart part, float angle, float sin, float cos) {
        float y = part.y + 2;
        part.y = y * cos - part.z * sin - 2;
        part.z = y * sin + part.z * cos;
        part.xRot += angle;
    }
}
