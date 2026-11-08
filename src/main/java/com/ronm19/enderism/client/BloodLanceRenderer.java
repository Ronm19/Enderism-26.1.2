package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.projectile.BloodLance;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/** Two crossed native quads align the visible magical lance with its real flight direction. */
public final class BloodLanceRenderer extends EntityRenderer<BloodLance, BloodLanceRenderer.LanceRenderState> {
    // In 26.1.2, entityCutout uses ENTITY_CUTOUT with culling disabled; both sides stay visible.
    private static final RenderType MATERIAL = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(Enderism.MOD_ID,
            "textures/entity/blood_enderman/blood_lance.png"));

    public BloodLanceRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override protected int getBlockLightLevel(BloodLance entity, BlockPos position) { return 15; }
    @Override public LanceRenderState createRenderState() { return new LanceRenderState(); }
    @Override public void extractRenderState(BloodLance entity, LanceRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.xRot = entity.getXRot(partialTick);
        state.yRot = entity.getYRot(partialTick);
    }

    @Override public void submit(LanceRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(state.yRot - 90));
        poses.mulPose(Axis.ZP.rotationDegrees(state.xRot));
        collector.submitCustomGeometry(poses, MATERIAL, (pose, vertices) -> {
            plane(vertices, pose, false);
            plane(vertices, pose, true);
        });
        poses.popPose();
        super.submit(state, poses, collector, camera);
    }

    private static void plane(VertexConsumer vertices, PoseStack.Pose pose, boolean cross) {
        vertex(vertices, pose, -.62F, -.21F, 0, 1, cross);
        vertex(vertices, pose, .62F, -.21F, 1, 1, cross);
        vertex(vertices, pose, .62F, .21F, 1, 0, cross);
        vertex(vertices, pose, -.62F, .21F, 0, 0, cross);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float radial, float u, float v, boolean cross) {
        vertices.addVertex(pose, x, cross ? 0 : radial, cross ? radial : 0).setColor(-1).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, cross ? 1 : 0, cross ? 0 : 1);
    }

    public static final class LanceRenderState extends EntityRenderState {
        public float xRot;
        public float yRot;
    }
}
