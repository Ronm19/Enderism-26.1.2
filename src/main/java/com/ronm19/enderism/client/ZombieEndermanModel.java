package com.ronm19.enderism.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.util.Mth;

/** Original Enderman limbs and atlas, with a decayed stance and a head-led bite. */
public final class ZombieEndermanModel extends EndermanModel<EndermanRenderState> {
    public static final float BODY_SCALE = 1.17F;
    public ZombieEndermanModel(ModelPart root) { super(root); }

    @Override public void setupAnim(EndermanRenderState state) {
        state.rightArmPose = state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        state.isUsingItem = false; state.carriedBlock.clear();
        super.setupAnim(state);
        this.root().xScale = this.root().yScale = this.root().zScale = BODY_SCALE;
        this.root().y = 24 - 25 * BODY_SCALE;
        if (state.deathTime > 0) return;
        var render = (ZombieEndermanRenderer.ZombieRenderState)state;
        float breath = Mth.sin(state.ageInTicks * .06F), rise = Mth.clamp(render.phaseAge / 10, 0, 1);
        this.body.xRot += .025F; this.head.xRot += .06F; this.head.z -= .12F;
        switch (render.phase) {
            case IDLE -> { this.rightArm.zRot += .014F * breath; this.leftArm.zRot -= .018F * breath; this.head.yRot += .018F * breath; }
            case PURSUING -> { this.body.xRot += .035F; this.head.xRot += .055F; this.rightArm.xRot *= 1.12F; this.leftArm.xRot *= 1.07F; }
            case SEARCHING -> this.head.yRot += .14F * breath;
            case RESPONDING -> { this.head.xRot -= .04F; this.rightArm.xRot = -.19F + this.rightArm.xRot * .75F; this.leftArm.xRot = -.25F + this.leftArm.xRot * .75F; }
            case SEEKING_SHADE -> { this.head.xRot += .13F; this.body.xRot += .045F; }
            case BITE_WINDUP -> {
                this.body.xRot += .06F * rise; this.head.xRot -= .13F * rise;
                this.head.z += .45F * rise; this.head.y -= .15F * rise;
                this.rightArm.xRot = -.36F * rise; this.leftArm.xRot = -.3F * rise;
                this.rightArm.yRot = -.12F * rise; this.leftArm.yRot = .12F * rise;
            }
            case BITE -> {
                float snap = 1 - Mth.clamp(render.phaseAge / 7, 0, 1);
                this.body.xRot += .12F * snap; this.head.xRot += .27F * snap;
                this.head.z -= 1.2F * snap; this.head.y += .65F * snap;
                this.rightArm.xRot = -.56F * snap; this.leftArm.xRot = -.48F * snap;
                this.rightArm.yRot = -.18F * snap; this.leftArm.yRot = .18F * snap;
            }
            case FRENZY_WINDUP -> {
                this.head.xRot -= .23F * rise; this.body.xRot -= .025F * rise;
                this.rightArm.xRot = -.35F * rise; this.leftArm.xRot = -.35F * rise;
                this.rightArm.zRot = -.22F * rise; this.leftArm.zRot = .22F * rise;
            }
            case FRENZIED -> {
                this.body.xRot += .06F; this.head.xRot += .075F;
                this.rightArm.xRot = -.27F + this.rightArm.xRot * 1.2F;
                this.leftArm.xRot = -.34F + this.leftArm.xRot * 1.2F;
                this.rightArm.zRot -= .04F; this.leftArm.zRot += .04F;
            }
            case RECOVERY -> {
                float relax = 1 - Mth.clamp(render.phaseAge / 10, 0, 1);
                this.head.xRot += .1F * relax; this.rightArm.xRot = -.18F * relax; this.leftArm.xRot = -.14F * relax;
            }
        }
        // The reactive signal is a brief overlay, never a competing attack/cast phase.
        if (render.signalTicks > 0 && render.phase != com.ronm19.enderism.entity.ZombieEnderman.CombatPhase.BITE
                && render.phase != com.ronm19.enderism.entity.ZombieEnderman.CombatPhase.BITE_WINDUP) {
            float alert = Mth.clamp(render.signalTicks / 10F, 0, 1);
            this.head.xRot -= .12F * alert; this.rightArm.zRot -= .045F * alert; this.leftArm.zRot += .045F * alert;
        }
    }
}
