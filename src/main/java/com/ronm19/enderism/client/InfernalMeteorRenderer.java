package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.projectile.InfernalMeteor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
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
import net.minecraft.util.Unit;

public final class InfernalMeteorRenderer extends EntityRenderer<InfernalMeteor, InfernalMeteorRenderer.MeteorRenderState> {
    private static final Identifier ROCK = texture("infernal_meteor");
    private static final RenderType MAGMA = RenderTypes.eyes(texture("infernal_meteor_glow"));
    private final Model.Simple model;

    public InfernalMeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("meteor", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5, -5, -5, 10, 10, 10)
                .texOffs(0, 20).addBox(-7, -3, -3, 14, 6, 6)
                .texOffs(0, 32).addBox(-3, -7, -3, 6, 14, 6)
                .texOffs(24, 32).addBox(-3, -3, -7, 6, 6, 14), PartPose.ZERO);
        this.model = new Model.Simple(LayerDefinition.create(mesh, 64, 64).bakeRoot(), RenderTypes::entityCutout);
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/infernal_enderman/" + name + ".png");
    }

    @Override
    protected int getBlockLightLevel(InfernalMeteor meteor, BlockPos position) {
        return Math.max(12, super.getBlockLightLevel(meteor, position));
    }

    @Override
    public MeteorRenderState createRenderState() {
        return new MeteorRenderState();
    }

    @Override
    public void extractRenderState(InfernalMeteor meteor, MeteorRenderState state, float partialTick) {
        super.extractRenderState(meteor, state, partialTick);
        state.diameter = meteor.visualScale();
        state.giant = meteor.isGiant();
    }

    @Override
    public void submit(MeteorRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose();
        poses.translate(0, state.diameter * .5F, 0);
        poses.scale(state.diameter, state.diameter, state.diameter);
        float spin = state.ageInTicks * (state.giant ? 4.0F : 7.5F);
        poses.mulPose(Axis.YP.rotationDegrees(spin));
        poses.mulPose(Axis.XP.rotationDegrees(20.0F + spin * .74F));
        collector.submitModel(this.model, Unit.INSTANCE, poses, this.model.renderType(ROCK),
                state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);
        collector.order(1).submitModel(this.model, Unit.INSTANCE, poses, MAGMA,
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.giant ? -1 : 0xFFE9B094,
                null, state.outlineColor, null);
        poses.popPose();
        super.submit(state, poses, collector, camera);
    }

    public static final class MeteorRenderState extends EntityRenderState {
        private float diameter = .7F;
        private boolean giant;
    }
}
