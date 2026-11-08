package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.ZombieEnderman;
import com.ronm19.enderism.entity.ZombieEnderman.CombatPhase;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.CarriedBlockLayer;
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

/** World-lit decayed skin and native synchronized green hunting/frenzy eyes. */
public final class ZombieEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY = texture("zombie_enderman");
    private static final RenderType EYES = RenderTypes.eyes(texture("zombie_enderman_eyes"));
    public ZombieEndermanRenderer(EntityRendererProvider.Context context) {
        super(context); this.shadowRadius = .55F; this.model = new ZombieEndermanModel(this.model.root());
        this.layers.removeIf(layer -> layer instanceof EnderEyesLayer || layer instanceof CarriedBlockLayer);
        this.addLayer(new UndeadEyes());
    }
    private static Identifier texture(String name) { return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "textures/entity/zombie_enderman/" + name + ".png"); }
    @Override public Identifier getTextureLocation(EndermanRenderState state) { return BODY; }
    @Override public ZombieRenderState createRenderState() { return new ZombieRenderState(); }
    @Override public void extractRenderState(EnderMan entity, EndermanRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks); state.carriedBlock.clear(); state.isCreepy = false;
        if (entity instanceof ZombieEnderman zombie) {
            var render = (ZombieRenderState)state; render.phase = zombie.getCombatPhase();
            render.phaseAge = Math.max(0, zombie.level().getGameTime() - zombie.getCombatPhaseStart() + partialTicks);
            render.brightness = Mth.clamp(zombie.getEyeBrightness(), 0, 1);
            render.frenzied = zombie.isFrenzied(); render.signalTicks = zombie.getSignalTicksRemaining();
        }
    }
    public static final class ZombieRenderState extends EndermanRenderState {
        public CombatPhase phase = CombatPhase.IDLE;
        public float phaseAge, brightness = .4F;
        public int signalTicks;
        public boolean frenzied;
    }
    private final class UndeadEyes extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
        UndeadEyes() { super(ZombieEndermanRenderer.this); }
        @Override public void submit(PoseStack poses, SubmitNodeCollector collector, int light, EndermanRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            int channel = Math.round(255 * Mth.clamp(((ZombieRenderState)state).brightness, 0, 1));
            int tint = 0xFF000000 | channel << 16 | channel << 8 | channel;
            collector.order(2).submitModel(getParentModel(), state, poses, EYES, LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor, null);
        }
    }
}
