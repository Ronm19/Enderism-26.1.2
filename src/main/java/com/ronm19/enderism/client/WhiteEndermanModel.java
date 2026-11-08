package com.ronm19.enderism.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.util.Mth;

/** The native slim Enderman skeleton and atlas, with deliberate hunting posture. */
public final class WhiteEndermanModel extends EndermanModel<EndermanRenderState> {
    public static final float BODY_SCALE = 1.17F;
    public WhiteEndermanModel(ModelPart root) { super(root); }

    @Override public void setupAnim(EndermanRenderState state) {
        state.rightArmPose = state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        state.isUsingItem = false; state.carriedBlock.clear();
        super.setupAnim(state);
        this.root().xScale = this.root().yScale = this.root().zScale = BODY_SCALE;
        this.root().y = 24 - 25 * BODY_SCALE;
        if (state.deathTime > 0) return;
        var render = (WhiteEndermanRenderer.WhiteRenderState)state;
        float breath = Mth.sin(state.ageInTicks * .045F);
        float rise = Mth.clamp(render.phaseAge / Math.max(1, render.phaseAge + render.warmupTicks), 0, 1);
        switch (render.phase) {
            case IDLE -> { this.head.xRot -= .025F; this.head.zRot += breath * .009F; this.rightArm.zRot += breath * .008F; this.leftArm.zRot -= breath * .008F; }
            case OBSERVING -> { this.head.yRot += .08F * breath; this.head.xRot -= .055F; this.rightArm.xRot *= .45F; this.leftArm.xRot *= .45F; }
            case STALKING -> { lean(.035F); this.rightArm.xRot *= .7F; this.leftArm.xRot *= .7F; this.head.xRot -= .035F; }
            case FLANKING -> { lean(.055F); this.head.yRot *= .85F; this.rightArm.zRot -= .04F; this.leftArm.zRot += .04F; }
            case PURSUING, APEX -> { lean(render.apex ? .09F : .055F); this.rightArm.xRot *= render.apex ? 1.25F : 1.08F; this.leftArm.xRot *= render.apex ? 1.25F : 1.08F; }
            case MARK_WINDUP -> { this.rightArm.xRot = -1.35F * rise; this.rightArm.yRot = -.25F * rise; this.rightArm.zRot = -.16F * rise; this.leftArm.xRot = -.3F * rise; lean(-.02F * rise); }
            case MARK -> { float hit = 1 - Mth.clamp(render.phaseAge / 6, 0, 1); this.rightArm.xRot = -.2F - .9F * hit; this.rightArm.yRot = .3F * hit; this.leftArm.xRot = -.2F * hit; lean(.13F * hit); }
            case TRACKING_WINDUP -> { this.head.xRot += .12F * rise; this.head.yRot += .08F * breath; this.rightArm.xRot = -.2F * rise; this.leftArm.xRot = -.15F * rise; }
            case TRACKING, SEARCHING -> { this.head.xRot += .04F; this.head.yRot += .12F * breath; lean(.025F); }
            case APEX_WINDUP -> { this.head.xRot -= .13F * rise; this.rightArm.xRot = -.35F * rise; this.leftArm.xRot = -.35F * rise; this.rightArm.zRot = -.16F * rise; this.leftArm.zRot = .16F * rise; lean(-.02F * rise); }
            case RECOVERY -> { float rest = 1 - Mth.clamp(render.phaseAge / 10, 0, 1); this.rightArm.xRot = -.12F * rest; this.leftArm.xRot = -.07F * rest; }
        }
    }
    private void lean(float angle) {
        float sin = Mth.sin(angle), cos = Mth.cos(angle);
        for (var part : new ModelPart[]{this.body, this.head, this.rightArm, this.leftArm}) {
            float y = part.y + 5;
            part.y = y * cos - part.z * sin - 5; part.z = y * sin + part.z * cos; part.xRot += angle;
        }
    }
}
