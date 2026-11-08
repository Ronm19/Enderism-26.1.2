package com.ronm19.enderism.client;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.projectile.SkeletonArcaneArrow;
import com.ronm19.enderism.entity.projectile.SkeletonArcaneArrow.Element;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

/** A native arrow model, with a synchronized material for its single elemental type. */
public final class SkeletonArcaneArrowRenderer extends ArrowRenderer<SkeletonArcaneArrow, SkeletonArcaneArrowRenderer.ArcaneArrowState> {
    private static final Identifier[] TEXTURES = java.util.Arrays.stream(Element.values()).map(element ->
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/skeleton_enderman/arrow_" + element.name().toLowerCase(java.util.Locale.ROOT) + ".png"))
            .toArray(Identifier[]::new);
    public SkeletonArcaneArrowRenderer(EntityRendererProvider.Context context) { super(context); }
    public static Identifier texture(Element element) { return TEXTURES[element.ordinal()]; }
    @Override protected Identifier getTextureLocation(ArcaneArrowState state) { return texture(state.element); }
    @Override public ArcaneArrowState createRenderState() { return new ArcaneArrowState(); }
    @Override public void extractRenderState(SkeletonArcaneArrow entity, ArcaneArrowState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks); state.element = entity.getElement();
    }
    public static final class ArcaneArrowState extends ArrowRenderState { public Element element = Element.FREEZING; }
}
