package com.ronm19.enderism.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.util.Mth;

/** Native Enderman anatomy and atlas, with dry, deliberate movement and three distinct casts. */
public final class HuskEndermanModel extends EndermanModel<EndermanRenderState> {
    public static final float BODY_SCALE = 1.17F;
    public HuskEndermanModel(ModelPart root) { super(root); }

    @Override public void setupAnim(EndermanRenderState state) {
        state.rightArmPose = state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        state.isUsingItem = false; state.carriedBlock.clear();
        super.setupAnim(state);
        this.root().xScale = this.root().yScale = this.root().zScale = BODY_SCALE;
        this.root().y = 24 - 25 * BODY_SCALE;
        if (state.deathTime > 0) return;
        var render = (HuskEndermanRenderer.HuskRenderState)state;
        float breath = Mth.sin(state.ageInTicks * .045F);
        float rise = Mth.clamp(render.phaseAge / Math.max(1, render.phaseAge + render.warmupTicks), 0, 1);
        this.head.xRot += .025F;
        switch (render.phase) {
            case IDLE -> {
                this.head.yRot += .015F * breath;
                this.rightArm.zRot += .014F * breath;
                this.leftArm.zRot -= .01F * breath;
            }
            case PURSUING -> {
                lean(.035F); this.rightArm.xRot *= 1.08F; this.leftArm.xRot *= 1.08F;
            }
            case SEARCHING -> {
                this.head.yRot += .13F * breath; this.head.xRot -= .035F;
                this.rightArm.xRot *= .8F; this.leftArm.xRot *= .8F;
            }
            case STRIKE_WINDUP -> {
                this.rightArm.xRot = -1.9F * rise; this.rightArm.yRot = -.18F * rise;
                this.leftArm.xRot = -.48F * rise; this.leftArm.zRot = .13F * rise;
                lean(-.04F * rise);
            }
            case STRIKE -> {
                float hit = 1 - Mth.clamp(render.phaseAge / 7, 0, 1);
                this.rightArm.xRot = -.2F - 1.1F * hit; this.rightArm.yRot = -.18F * hit;
                this.leftArm.xRot = -.32F * hit; lean(.12F * hit);
            }
            case CURSE_WINDUP -> {
                this.head.xRot -= .1F * rise;
                this.rightArm.xRot = -1.15F * rise; this.rightArm.yRot = -.2F * rise;
                this.leftArm.xRot = -.65F * rise; this.leftArm.yRot = .14F * rise;
                this.rightArm.zRot = -.1F * rise; this.leftArm.zRot = .09F * rise;
            }
            case CURSE -> {
                float cast = 1 - Mth.clamp(render.phaseAge / 8, 0, 1);
                this.rightArm.xRot = -1.35F * cast; this.leftArm.xRot = -.48F * cast;
                this.rightArm.yRot = -.15F * cast; this.head.xRot -= .08F * cast;
            }
            case DELIRIUM_WINDUP -> {
                this.head.xRot -= .16F * rise;
                this.rightArm.xRot = -.4F * rise; this.leftArm.xRot = -.4F * rise;
                this.rightArm.zRot = -.55F * rise; this.leftArm.zRot = .55F * rise;
                lean(-.02F * rise);
            }
            case DELIRIUM -> {
                float pulse = 1 - Mth.clamp(render.phaseAge / 10, 0, 1);
                this.rightArm.xRot = -.12F * pulse; this.leftArm.xRot = -.12F * pulse;
                this.rightArm.zRot = -.85F * pulse; this.leftArm.zRot = .85F * pulse;
                this.head.xRot -= .1F * pulse; lean(.025F * pulse);
            }
            case RECOVERY -> {
                float rest = 1 - Mth.clamp(render.phaseAge / 10, 0, 1);
                this.rightArm.xRot = -.13F * rest; this.leftArm.xRot = -.09F * rest;
                this.head.xRot += .04F * rest;
            }
        }
    }
    private void lean(float angle) {
        float sin = Mth.sin(angle), cos = Mth.cos(angle);
        for (var part : new ModelPart[] {this.body, this.head, this.rightArm, this.leftArm}) {
            float y = part.y + 5;
            part.y = y * cos - part.z * sin - 5; part.z = y * sin + part.z * cos;
            part.xRot += angle;
        }
    }
}
