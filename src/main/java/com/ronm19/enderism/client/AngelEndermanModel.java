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

/** Native Enderman anatomy with quiet, ground-bound casting poses. */
public final class AngelEndermanModel extends EndermanModel<EndermanRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "angel_enderman"), "celestial_ornaments");

    AngelEndermanModel(ModelPart root) { super(root); }

    /** Static ornaments reuse occupied pearl and gold pixels of the original 64 x 32 atlas. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition halo = mesh.getRoot().addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition ring = halo.addOrReplaceChild("ring", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0, -10.6F, 0, .18F, 0, 0));
        for (int i = 0; i < 20; i++) {
            float angle = i * Mth.TWO_PI / 20;
            ring.addOrReplaceChild("segment_" + i,
                    CubeListBuilder.create().texOffs(8, 0).addBox(-.6F, -.3F, -.6F, 1.2F, .6F, 1.2F),
                    PartPose.offsetAndRotation(4.7F * Mth.cos(angle), 0, 4.7F * Mth.sin(angle), 0, -angle, 0));
        }
        PartDefinition wings = mesh.getRoot().addOrReplaceChild("wings", CubeListBuilder.create(), PartPose.ZERO);
        for (int side : new int[]{-1, 1}) {
            PartDefinition folded = wings.addOrReplaceChild(side < 0 ? "right_fold" : "left_fold",
                    CubeListBuilder.create(), PartPose.offsetAndRotation(side * 2.9F, 1.2F, 2.3F, .12F, side * .25F, -side * .13F));
            for (int feather = 0; feather < 4; feather++) {
                float length = 6 + feather * 1.6F;
                folded.addOrReplaceChild("feather_" + feather,
                        CubeListBuilder.create().texOffs(36 + feather % 2 * 3, 20)
                                .addBox(-.65F, 0, -.35F, 1.3F, length, .7F),
                        PartPose.offset(side * feather * 1.25F, feather * .9F, feather * .12F));
            }
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override public void setupAnim(EndermanRenderState state) {
        super.setupAnim(state);
        if (!state.carriedBlock.isEmpty()) return;
        var angel = (AngelEndermanRenderer.AngelRenderState)state;
        float breath = Mth.sin(state.ageInTicks * .09F);
        float rise = Mth.clamp(angel.phaseAge / 12, 0, 1);
        switch (angel.phase) {
            case IDLE -> {
                this.rightArm.zRot += .015F * breath;
                this.leftArm.zRot -= .015F * breath;
            }
            case OBSERVING -> this.head.zRot += .04F * breath;
            case TERRITORIAL_WARNING, DEFENDING -> {
                this.rightArm.xRot = -.3F * rise;
                this.leftArm.xRot = -.3F * rise;
                this.rightArm.zRot = -.11F * rise;
                this.leftArm.zRot = .11F * rise;
                this.head.xRot -= .07F * rise;
            }
            case STRIKE -> {
                float strike = Mth.sin(Mth.clamp(angel.phaseAge / 8, 0, 1) * Mth.PI);
                this.rightArm.xRot = -.15F - 1.75F * strike;
                this.rightArm.yRot = -.4F * strike;
                this.leftArm.xRot = -.25F - .35F * strike;
                this.head.xRot -= .1F * strike;
            }
            case HEALING -> {
                this.rightArm.xRot = -1.0F * rise;
                this.leftArm.xRot = -.8F * rise;
                this.rightArm.yRot = -.22F * rise;
                this.leftArm.yRot = .22F * rise;
                this.head.xRot -= .12F * rise;
            }
            case BENEDICTION -> {
                this.rightArm.xRot = -1.6F * rise;
                this.leftArm.xRot = -1.6F * rise;
                this.rightArm.zRot = -.26F * rise;
                this.leftArm.zRot = .26F * rise;
                this.head.xRot -= .14F * rise;
            }
            case DE_ESCALATING, RECOVERY -> {
                this.rightArm.xRot = -.28F * (1 - rise);
                this.leftArm.xRot = -.28F * (1 - rise);
            }
        }
    }

    /** Ornaments follow the head and torso; feather panels never flap or alter movement. */
    public static final class Ornaments extends Model<EndermanRenderState> {
        private final AngelEndermanModel body;
        Ornaments(ModelPart root, AngelEndermanModel body) {
            super(root, RenderTypes::entityCutout);
            this.body = body;
        }
        @Override public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);
            this.body.setupAnim(state);
            this.root.loadPose(this.body.root().storePose());
            this.root.getChild("halo").loadPose(this.body.head.storePose());
            this.root.getChild("wings").loadPose(this.body.body.storePose());
        }
    }
}

