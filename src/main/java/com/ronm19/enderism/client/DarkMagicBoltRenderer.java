package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.projectile.DarkMagicBolt;
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

/** Small camera-facing sprite using the native projectile geometry submission pipeline. */
public final class DarkMagicBoltRenderer extends EntityRenderer<DarkMagicBolt, DarkMagicBoltRenderer.BoltRenderState> {
    private static final RenderType SPRITE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(Enderism.MOD_ID,
            "textures/entity/dark_enderman/dark_magic_bolt.png"));

    public DarkMagicBoltRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override protected int getBlockLightLevel(DarkMagicBolt entity, BlockPos position) { return 15; }
    @Override public BoltRenderState createRenderState() { return new BoltRenderState(); }
    @Override public void extractRenderState(DarkMagicBolt entity, BoltRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.withering = entity.getKind() == DarkMagicBolt.Kind.WITHERING;
    }
    @Override public void submit(BoltRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose();
        poses.scale(.65F, .65F, .65F);
        poses.mulPose(camera.orientation);
        int color = state.withering ? 0xFFB39AC8 : 0xFFFFFFFF;
        collector.submitCustomGeometry(poses, SPRITE, (pose, vertices) -> {
            vertex(vertices, pose, state.lightCoords, 0, 0, 0, 1, color);
            vertex(vertices, pose, state.lightCoords, 1, 0, 1, 1, color);
            vertex(vertices, pose, state.lightCoords, 1, 1, 1, 0, color);
            vertex(vertices, pose, state.lightCoords, 0, 1, 0, 0, color);
        });
        poses.popPose();
        super.submit(state, poses, collector, camera);
    }
    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, int light, float x, float y, int u, int v, int color) {
        vertices.addVertex(pose, x - .5F, y - .5F, 0).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }
    public static final class BoltRenderState extends EntityRenderState { private boolean withering; }
}
