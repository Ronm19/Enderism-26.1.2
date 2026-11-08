package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.projectile.BoggedVenom;
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

/** A small emissive venom globule; its server projectile owns collision and Poison. */
public final class BoggedVenomRenderer extends EntityRenderer<BoggedVenom, EntityRenderState> {
    private static final RenderType SPRITE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(Enderism.MOD_ID,
            "textures/entity/bogged_enderman/bogged_venom.png"));
    public BoggedVenomRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override protected int getBlockLightLevel(BoggedVenom entity, BlockPos position) { return 15; }
    @Override public EntityRenderState createRenderState() { return new EntityRenderState(); }
    @Override public void submit(EntityRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose(); poses.scale(.38F, .38F, .38F); poses.mulPose(camera.orientation);
        collector.submitCustomGeometry(poses, SPRITE, (pose, vertices) -> {
            vertex(vertices, pose, state.lightCoords, 0, 0, 0, 1);
            vertex(vertices, pose, state.lightCoords, 1, 0, 1, 1);
            vertex(vertices, pose, state.lightCoords, 1, 1, 1, 0);
            vertex(vertices, pose, state.lightCoords, 0, 1, 0, 0);
        });
        poses.popPose(); super.submit(state, poses, collector, camera);
    }
    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, int light, float x, float y, int u, int v) {
        vertices.addVertex(pose, x - .5F, y - .5F, 0).setColor(-1).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }
}
