package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** The vanilla skeleton with head-bound, outward-curving demonic horns. */
public final class DemonEndermanModel extends EndermanModel<EndermanRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "demon_enderman"), "horns");

    DemonEndermanModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        addHorn(mesh.getRoot(), "right_horn", 1);
        addHorn(mesh.getRoot(), "left_horn", -1);
        return LayerDefinition.create(mesh, 16, 16);
    }

    private static void addHorn(PartDefinition root, String name, int side) {
        PartDefinition base = root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1, -1, -1, 2, 2, 2),
                PartPose.offset(side * 3.9F, -6.7F, -1.9F));
        PartDefinition lower = base.addOrReplaceChild("lower",
                CubeListBuilder.create().texOffs(0, 4)
                        .addBox(-1, -2.8F, -1, 2, 3, 2),
                PartPose.offsetAndRotation(side * .45F, -.7F, 0, 0, 0, side * .62F));
        PartDefinition curve = lower.addOrReplaceChild("curve",
                CubeListBuilder.create().texOffs(8, 0)
                        .addBox(-.75F, -2.5F, -.75F, 1.5F, 3, 1.5F),
                PartPose.offsetAndRotation(0, -2.4F, 0, 0, 0, -side * .83F));
        curve.addOrReplaceChild("tip",
                CubeListBuilder.create().texOffs(8, 6)
                        .addBox(-.5F, -1.8F, -.5F, 1, 2, 1),
                PartPose.offsetAndRotation(0, -2.15F, 0, 0, 0, -side * .55F));
    }

    void applyHeadTransform(PoseStack poses) {
        this.root().translateAndRotate(poses);
        this.head.translateAndRotate(poses);
    }

    @Override
    public void setupAnim(EndermanRenderState state) {
        super.setupAnim(state);
        var demon = (DemonEndermanRenderer.DemonRenderState)state;
        if (!state.carriedBlock.isEmpty()) return;
        float breath = Mth.sin(state.ageInTicks * .17F);
        float rise = Mth.clamp(demon.phaseAge / 10.0F, 0, 1);
        switch (demon.phase) {
            case CLAWS -> {
                float slash = Mth.sin(Mth.clamp(demon.phaseAge / 9.0F, 0, 1) * Mth.PI);
                this.rightArm.xRot = -.3F - 1.8F * slash;
                this.rightArm.yRot = -.6F * slash;
                this.rightArm.zRot = -.28F * slash;
                this.leftArm.xRot = -.15F - .6F * slash;
                lean(.18F * slash);
            }
            case ONSLAUGHT_WINDUP -> {
                lean(.24F * rise);
                this.rightArm.xRot = -.95F * rise;
                this.leftArm.xRot = -.95F * rise;
                this.rightArm.yRot = -.4F * rise;
                this.leftArm.yRot = .4F * rise;
            }
            case ONSLAUGHT -> {
                float beat = Mth.sin(demon.phaseAge * .85F);
                this.rightArm.xRot = -1.1F - .75F * beat;
                this.leftArm.xRot = -1.1F + .75F * beat;
                this.rightArm.yRot = -.3F;
                this.leftArm.yRot = .3F;
                lean(.2F);
            }
            case FRENZY_WINDUP -> {
                this.rightArm.xRot = (-1.8F + .06F * breath) * rise;
                this.leftArm.xRot = (-1.8F - .06F * breath) * rise;
                this.rightArm.zRot = -.5F * rise;
                this.leftArm.zRot = .5F * rise;
                this.head.xRot -= .2F * rise;
                lean(-.09F * rise);
            }
            case PURSUIT -> {
                lean(demon.frenzied ? .16F : .08F);
                this.rightLeg.xRot *= demon.frenzied ? 1.3F : 1.08F;
                this.leftLeg.xRot *= demon.frenzied ? 1.3F : 1.08F;
            }
            case SEARCHING -> this.head.yRot += .14F * breath;
            case RECOVERY -> lean(.12F * (1 - rise));
            case IDLE -> {
                this.rightArm.zRot += .025F * breath;
                this.leftArm.zRot -= .025F * breath;
            }
        }
    }

    private void lean(float angle) {
        float sin = Mth.sin(angle), cos = Mth.cos(angle);
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
