package com.ronm19.enderism.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.util.Mth;

/** Native Enderman bones and approved atlas, with grounded soul-fire casting poses. */
public final class SoulSandEndermanModel extends EndermanModel<EndermanRenderState> {
    public SoulSandEndermanModel(ModelPart root) { super(root); }

    @Override public void setupAnim(EndermanRenderState state) {
        state.rightArmPose = state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        state.isUsingItem = false; state.carriedBlock.clear();
        super.setupAnim(state);
        if (state.deathTime > 0) return;
        var render = (SoulSandEndermanRenderer.SoulSandRenderState)state;
        float breath = Mth.sin(state.ageInTicks * .045F);
        float rise = Mth.clamp(render.phaseAge / Math.max(1, render.phaseAge + render.warmupTicks), 0, 1);
        if (render.mastery && state.walkAnimationSpeed > .01F) {
            this.rightArm.xRot *= 1.16F; this.leftArm.xRot *= 1.16F;
            this.rightLeg.xRot *= 1.12F; this.leftLeg.xRot *= 1.12F;
        }
        switch (render.phase) {
            case IDLE -> { this.head.zRot += breath * .008F; this.rightArm.zRot += breath * .008F; this.leftArm.zRot -= breath * .008F; }
            case PURSUING, DOMINION -> { lean(render.dominion ? .07F : .045F); this.rightArm.xRot *= 1.08F; this.leftArm.xRot *= 1.08F; }
            case SEARCHING -> { this.head.yRot += .10F * breath; this.head.xRot += .035F; }
            case STRIKE_WINDUP -> { this.rightArm.xRot = -1.2F * rise; this.rightArm.yRot = -.18F * rise; this.leftArm.xRot = -.3F * rise; lean(-.02F * rise); }
            case STRIKE -> { float hit = 1 - Mth.clamp(render.phaseAge / 6, 0, 1); this.rightArm.xRot = -.18F - 1.0F * hit; this.leftArm.xRot = -.25F * hit; lean(.12F * hit); }
            case ERUPTION_WINDUP -> { this.rightArm.xRot = -.22F * rise; this.leftArm.xRot = -.22F * rise; this.rightArm.zRot = -.16F * rise; this.leftArm.zRot = .16F * rise; this.head.xRot += .12F * rise; }
            case ERUPTION -> { float pulse = 1 - Mth.clamp(render.phaseAge / 8, 0, 1); this.rightArm.xRot = -.4F * pulse; this.leftArm.xRot = -.4F * pulse; this.rightArm.zRot = -.24F * pulse; this.leftArm.zRot = .24F * pulse; lean(.055F * pulse); }
            case DOMINION_WINDUP -> { this.rightArm.xRot = -.65F * rise; this.leftArm.xRot = -.65F * rise; this.rightArm.zRot = -.20F * rise; this.leftArm.zRot = .20F * rise; this.head.xRot -= .10F * rise; }
            case RECOVERY -> { float rest = 1 - Mth.clamp(render.phaseAge / 10, 0, 1); this.rightArm.xRot = -.12F * rest; this.leftArm.xRot = -.12F * rest; }
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
