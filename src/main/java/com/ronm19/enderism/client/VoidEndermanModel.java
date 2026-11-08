package com.ronm19.enderism.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.util.Mth;

/** Native Enderman UVs and bipedal skeleton, with short grounded combat and rescue gestures. */
public final class VoidEndermanModel extends EndermanModel<EndermanRenderState> {
    static final float BODY_SCALE = 1.17F;
    public VoidEndermanModel(ModelPart root) { super(root); }

    @Override public void setupAnim(EndermanRenderState state) {
        super.setupAnim(state);
        this.root().xScale = this.root().yScale = this.root().zScale = BODY_SCALE;
        // Native Enderman legs end at25px; keep the scaled feet on the24px renderer ground plane.
        this.root().y = 24 - 25 * BODY_SCALE;
        if (state.deathTime > 0 || !state.carriedBlock.isEmpty()) return;
        var render = (VoidEndermanRenderer.VoidRenderState)state;
        float breath = Mth.sin(state.ageInTicks * .085F), rise = Mth.clamp(render.phaseAge / 8,0,1);
        switch (render.phase) {
            case IDLE -> { this.rightArm.zRot += .018F * breath; this.leftArm.zRot -= .018F * breath; }
            case PURSUING -> { this.head.xRot += .055F; this.rightArm.xRot *= 1.1F; this.leftArm.xRot *= 1.1F; }
            case SEARCHING -> this.head.yRot += .1F * breath;
            case STRIKE_WINDUP -> {
                this.rightArm.xRot = -1.05F * rise; this.rightArm.yRot = -.35F * rise;
                this.leftArm.xRot = -.16F * rise; this.body.yRot = -.09F * rise;
            }
            case STRIKE -> {
                float strike = Mth.cos(Mth.clamp(render.phaseAge / 6,0,1) * Mth.HALF_PI);
                this.rightArm.xRot = -.15F - 1.45F * strike; this.rightArm.yRot = .28F * strike;
                this.leftArm.xRot = -.2F * strike; this.body.yRot = .16F * strike;
            }
            case STEP_WINDUP -> {
                this.rightArm.xRot = -.32F * rise; this.leftArm.xRot = -.32F * rise;
                this.rightArm.zRot = -.1F * rise; this.leftArm.zRot = .1F * rise;
                this.head.xRot -= .055F * rise;
            }
            case STEP -> {
                float settle = 1 - Mth.clamp(render.phaseAge / 6,0,1);
                this.body.yRot = .13F * settle; this.rightArm.xRot = -.22F * settle; this.leftArm.xRot = -.4F * settle;
            }
            case PULL_WINDUP -> {
                float channel = Mth.clamp(render.phaseAge / 16,0,1);
                this.rightArm.xRot = -1.03F * channel; this.leftArm.xRot = -1.03F * channel;
                this.rightArm.zRot = -.13F * channel; this.leftArm.zRot = .13F * channel;
                this.head.xRot -= .07F * channel;
            }
            case PULL -> {
                float release = Mth.clamp(render.phaseAge / 8,0,1);
                this.rightArm.xRot = -1.25F + .7F * release; this.leftArm.xRot = -1.25F + .7F * release;
                this.rightArm.yRot = .16F; this.leftArm.yRot = -.16F;
            }
            case RECOVERY -> { this.rightArm.xRot = -.2F * (1-rise); this.leftArm.xRot = -.15F * (1-rise); }
            case VOID_RESCUE -> {
                float settle = 1-Mth.clamp(render.phaseAge / 12,0,1);
                this.rightArm.xRot = -.3F * settle; this.leftArm.xRot = -.3F * settle;
                this.rightArm.zRot = -.18F * settle; this.leftArm.zRot = .18F * settle;
                this.head.xRot -= .06F * settle;
            }
        }
    }
}
