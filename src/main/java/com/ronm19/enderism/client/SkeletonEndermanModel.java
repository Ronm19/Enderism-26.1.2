package com.ronm19.enderism.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.util.Mth;

/** Native Enderman anatomy and the supplied atlas, with open-handed supernatural casting. */
public final class SkeletonEndermanModel extends EndermanModel<EndermanRenderState> {
    public SkeletonEndermanModel(ModelPart root) { super(root); }

    @Override public void setupAnim(EndermanRenderState state) {
        // Casting never borrows equipped-bow or block-carrying poses.
        state.rightArmPose = state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        state.isUsingItem = false;
        state.carriedBlock.clear();
        super.setupAnim(state);
        if (state.deathTime > 0) return;
        var render = (SkeletonEndermanRenderer.SkeletonRenderState)state;
        float rise = Mth.clamp(render.phaseAge / 12, 0, 1);
        float breath = Mth.sin(state.ageInTicks * .065F);
        switch (render.phase) {
            case IDLE -> { this.head.xRot += .025F; this.rightArm.zRot += .012F * breath; this.leftArm.zRot -= .012F * breath; }
            case SEARCHING -> this.head.yRot += .15F * breath;
            case SEEKING_SHADE -> { this.head.xRot += .14F; this.rightArm.xRot *= 1.1F; this.leftArm.xRot *= 1.1F; }
            case PURSUING -> this.head.xRot += .055F;
            case MELEE -> {
                float strike = Mth.sin(Mth.clamp(render.phaseAge / 8, 0, 1) * (float)Math.PI);
                this.rightArm.xRot = -.3F - strike * .9F; this.rightArm.yRot = -.12F + strike * .25F;
                this.body.yRot = -.08F * strike;
            }
            case ARCANE_WINDUP -> {
                this.rightArm.xRot = -1.0F * rise; this.leftArm.xRot = -.8F * rise;
                this.rightArm.zRot = -.12F * rise; this.leftArm.zRot = .12F * rise;
                this.head.xRot -= .045F * rise;
            }
            case ARCANE_VOLLEY -> {
                this.rightArm.xRot = -1.3F; this.leftArm.xRot = -1.15F;
                this.rightArm.zRot = -.08F; this.leftArm.zRot = .08F;
            }
            case CURSED_WINDUP -> {
                this.rightArm.xRot = -1.35F * rise; this.leftArm.xRot = -.35F * rise;
                this.rightArm.yRot = -.12F * rise; this.head.xRot -= .06F * rise;
            }
            case CURSED_ARROW -> {
                this.rightArm.xRot = -1.55F; this.rightArm.yRot = .04F;
                this.leftArm.xRot = -.3F; this.body.yRot = .055F;
            }
            case DEATHS_WINDUP -> {
                this.rightArm.xRot = -1.15F * rise; this.leftArm.xRot = -1.15F * rise;
                this.rightArm.zRot = -.3F * rise; this.leftArm.zRot = .3F * rise;
                this.head.xRot -= .09F * rise;
            }
            case DEATHS_BARRAGE -> {
                this.rightArm.xRot = -1.45F; this.leftArm.xRot = -1.45F;
                this.rightArm.zRot = -.16F; this.leftArm.zRot = .16F;
            }
            case RECOVERY -> {
                float relax = 1 - Mth.clamp(render.phaseAge / 10, 0, 1);
                this.rightArm.xRot = -.45F * relax; this.leftArm.xRot = -.35F * relax;
            }
        }
    }
}
