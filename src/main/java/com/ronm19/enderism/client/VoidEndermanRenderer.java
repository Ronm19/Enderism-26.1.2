package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.VoidEnderman;
import com.ronm19.enderism.entity.VoidEnderman.CombatPhase;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EnderEyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.EnderMan;

/** Event-driven phase presentation on the real entity, with restrained violet emission. */
public final class VoidEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("void_enderman");
    private static final RenderType EYES = RenderTypes.eyes(texture("void_enderman_eyes"));
    private static final RenderType GLOW = RenderTypes.eyes(texture("void_enderman_glow"));

    public VoidEndermanRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = .55F;
        this.model = new VoidEndermanModel(this.model.root());
        this.layers.removeIf(EnderEyesLayer.class::isInstance);
        this.addLayer(new GlowLayer(GLOW,false)); this.addLayer(new GlowLayer(EYES,true));
    }
    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(Enderism.MOD_ID,"textures/entity/void_enderman/"+name+".png");
    }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public VoidRenderState createRenderState() { return new VoidRenderState(); }
    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTick) {
        super.extractRenderState(entity,state,partialTick);
        if (entity instanceof VoidEnderman body) {
            var render = (VoidRenderState)state;
            render.phase = body.getCombatPhase();
            render.phaseAge = Math.max(0,body.level().getGameTime()-body.getCombatPhaseStart()+partialTick);
            render.brightness = Mth.clamp(body.getEyeBrightness(),0,1);
            state.isCreepy = false;
        }
    }
    public static final class VoidRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.IDLE;
        public float phaseAge;
        public float brightness = .78F;
    }
    private final class GlowLayer extends RenderLayer<EndermanRenderState,EndermanModel<EndermanRenderState>> {
        private final RenderType material;
        private final boolean eyes;
        private GlowLayer(RenderType material,boolean eyes) {
            super(VoidEndermanRenderer.this); this.material = material; this.eyes = eyes;
        }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                                     EndermanRenderState state,float yRot,float xRot) {
            if (state.isInvisible) return;
            var render = (VoidRenderState)state;
            float intensity = this.eyes ? render.brightness : .36F + .28F * render.brightness;
            int channel = Math.round(255 * Mth.clamp(intensity,0,1));
            int color = 0xFF000000 | channel << 16 | channel << 8 | channel;
            collector.order(this.eyes ? 2 : 1).submitModel(this.getParentModel(),state,poses,this.material,
                    LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,color,null,state.outlineColor,null);
        }
    }
}
