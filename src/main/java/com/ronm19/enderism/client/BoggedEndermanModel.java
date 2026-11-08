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

/** Native atlas and limbs, with grounded casting and a few small, attached swamp ornaments. */
public final class BoggedEndermanModel extends EndermanModel<EndermanRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "bogged_enderman"), "swamp_ornaments");
    public static final float BODY_SCALE = 1.085F;
    public BoggedEndermanModel(ModelPart root) { super(root); }

    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition();
        var head = mesh.getRoot().addOrReplaceChild("head_vegetation", CubeListBuilder.create(), PartPose.ZERO);
        mushroom(head, "head_mushroom", 1.5F, -8.0F, 1.2F, -.12F);
        moss(head, "right_head_moss", -4.25F, -8.0F, .6F, -.08F);
        moss(head, "left_head_moss", 3.3F, -7.7F, 1.8F, .08F);
        moss(head, "back_head_moss", -.5F, -7.8F, 3.3F, .05F);
        var right = mesh.getRoot().addOrReplaceChild("right_shoulder", CubeListBuilder.create(), PartPose.ZERO);
        mushroom(right, "right_mushroom", -.6F, -2.0F, 0, -.14F);
        moss(right, "right_moss", -1.25F, -1.8F, -.6F, -.06F);
        var left = mesh.getRoot().addOrReplaceChild("left_shoulder", CubeListBuilder.create(), PartPose.ZERO);
        mushroom(left, "left_mushroom", .6F, -2.0F, .15F, .12F);
        moss(left, "left_moss", .3F, -1.7F, -.7F, .07F);
        return LayerDefinition.create(mesh, 64, 32);
    }
    private static void mushroom(PartDefinition parent, String name, float x, float y, float z, float tilt) {
        var stem = parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(28, 0)
                .addBox(-.5F, -1.5F, -.5F, 1, 2, 1), PartPose.offsetAndRotation(x, y, z, 0, tilt, tilt));
        stem.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.5F, -2.0F, -1.5F, 3, 1, 3), PartPose.ZERO);
        stem.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(16, 0)
                .addBox(-1, -2.5F, -1, 2, 1, 2), PartPose.ZERO);
    }
    private static void moss(PartDefinition parent, String name, float x, float y, float z, float tilt) {
        parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 12)
                .addBox(0, 0, 0, 1, 5, 1), PartPose.offsetAndRotation(x, y, z, tilt, tilt, tilt));
    }

    @Override public void setupAnim(EndermanRenderState state) {
        super.setupAnim(state);
        this.root().xScale = this.root().yScale = this.root().zScale = BODY_SCALE;
        this.root().y = 24 - 25 * BODY_SCALE;
        if (state.deathTime > 0 || !state.carriedBlock.isEmpty()) return;
        var render = (BoggedEndermanRenderer.BoggedRenderState)state;
        float breath = Mth.sin(state.ageInTicks * .075F), rise = Mth.clamp(render.phaseAge / 12, 0, 1);
        switch (render.phase) {
            case IDLE -> { this.rightArm.zRot += .014F * breath; this.leftArm.zRot -= .014F * breath; this.head.xRot += .025F; }
            case PURSUING -> { this.head.xRot += .08F; this.rightArm.xRot *= 1.1F; this.leftArm.xRot *= 1.1F; }
            case SEARCHING -> this.head.yRot += .12F * breath;
            case GRASP_WINDUP -> { this.rightArm.xRot = -.95F * rise; this.rightArm.yRot = -.3F * rise; this.body.yRot = -.09F * rise; }
            case GRASP -> {
                float strike = 1 - Mth.clamp(render.phaseAge / 7, 0, 1);
                this.rightArm.xRot = -.1F - 1.35F * strike; this.rightArm.yRot = .22F * strike;
                this.leftArm.xRot = -.25F * strike; this.body.yRot = .13F * strike;
            }
            case BARRAGE_WINDUP, BARRAGE -> {
                float channel = render.phase == com.ronm19.enderism.entity.BoggedEnderman.CombatPhase.BARRAGE ? 1 : rise;
                this.rightArm.xRot = -1.25F * channel; this.rightArm.yRot = -.08F * channel;
                this.leftArm.xRot = -.4F * channel; this.leftArm.yRot = .15F * channel;
                this.head.xRot -= .07F * channel;
                if (render.phase == com.ronm19.enderism.entity.BoggedEnderman.CombatPhase.BARRAGE) this.rightArm.xRot += .045F * Mth.sin(render.phaseAge * .7F);
            }
            case MIRE_WINDUP -> {
                this.rightArm.xRot = -.85F * rise; this.leftArm.xRot = -.85F * rise;
                this.rightArm.zRot = -.2F * rise; this.leftArm.zRot = .2F * rise; this.head.xRot += .1F * rise;
            }
            case MIRE -> {
                float release = 1 - Mth.clamp(render.phaseAge / 10, 0, 1);
                this.rightArm.xRot = -.4F * release; this.leftArm.xRot = -.4F * release;
                this.rightArm.zRot = -.22F * release; this.leftArm.zRot = .22F * release;
            }
            case RECOVERY -> { this.rightArm.xRot = -.22F * (1-rise); this.leftArm.xRot = -.12F * (1-rise); }
        }
    }

    public static final class Ornaments extends Model<EndermanRenderState> {
        private final BoggedEndermanModel body;
        public Ornaments(ModelPart root, BoggedEndermanModel body) { super(root, RenderTypes::entityCutout); this.body = body; }
        @Override public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state); this.body.setupAnim(state);
            this.root.loadPose(this.body.root().storePose());
            // ModelPart.storePose preserves translation/rotation only, so copy the body scale explicitly.
            this.root.xScale = this.body.root().xScale;
            this.root.yScale = this.body.root().yScale;
            this.root.zScale = this.body.root().zScale;
            this.root.getChild("head_vegetation").loadPose(this.body.head.storePose());
            this.root.getChild("right_shoulder").loadPose(this.body.rightArm.storePose());
            this.root.getChild("left_shoulder").loadPose(this.body.leftArm.storePose());
        }
    }
}

