package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.VampireEnderman;
import com.ronm19.enderism.entity.VampireEnderman.CombatPhase;
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

/** Grounded vampiric dress and server-synchronized red eyes that brighten only during Fury. */
public final class VampireEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY=texture("vampire_enderman"),REGALIA=texture("vampire_enderman_regalia");
    private static final RenderType EYES=RenderTypes.eyes(texture("vampire_enderman_eyes"));
    public VampireEndermanRenderer(EntityRendererProvider.Context context){super(context);this.shadowRadius=.5F;this.model=new VampireEndermanModel(this.model.root());this.layers.removeIf(layer->layer instanceof EnderEyesLayer||layer instanceof CarriedBlockLayer);this.addLayer(new NobleDress(context));this.addLayer(new VampireEyes());}
    private static Identifier texture(String name){return Identifier.fromNamespaceAndPath(Enderism.MOD_ID,"textures/entity/vampire_enderman/"+name+".png");}
    @Override public Identifier getTextureLocation(EndermanRenderState state){return BODY;}
    @Override public VampireRenderState createRenderState(){return new VampireRenderState();}
    @Override public void extractRenderState(EnderMan entity,EndermanRenderState state,float partialTicks){
        super.extractRenderState(entity,state,partialTicks);state.carriedBlock.clear();state.isCreepy=false;
        if(entity instanceof VampireEnderman vampire){var render=(VampireRenderState)state;render.phase=vampire.getCombatPhase();render.phaseAge=Math.max(0,vampire.level().getGameTime()-vampire.getCombatPhaseStart()+partialTicks);render.warmupTicks=vampire.getWarmupTicksRemaining();render.brightness=Mth.clamp(vampire.getEyeBrightness(),0,1);render.fury=vampire.isFuryActive();render.drainPulseTicks=vampire.getDrainPulseTicksRemaining();}
    }
    public static final class VampireRenderState extends EndermanRenderState {
        public CombatPhase phase=CombatPhase.IDLE;public float phaseAge,brightness=.45F;public int warmupTicks,drainPulseTicks;public boolean fury;
    }
    private final class NobleDress extends RenderLayer<EndermanRenderState,EndermanModel<EndermanRenderState>> {
        private final VampireEndermanModel.Regalia regalia;
        NobleDress(EntityRendererProvider.Context context){super(VampireEndermanRenderer.this);this.regalia=new VampireEndermanModel.Regalia(context.bakeLayer(VampireEndermanModel.REGALIA_LAYER),(VampireEndermanModel)getParentModel());}
        @Override public void submit(PoseStack poses,SubmitNodeCollector collector,int light,EndermanRenderState state,float yRot,float xRot){if(!state.isInvisible)collector.order(1).submitModel(this.regalia,state,poses,RenderTypes.entityCutout(REGALIA),light,OverlayTexture.NO_OVERLAY,0xFFFFFFFF,null,state.outlineColor,null);}
    }
    private final class VampireEyes extends RenderLayer<EndermanRenderState,EndermanModel<EndermanRenderState>> {
        VampireEyes(){super(VampireEndermanRenderer.this);}
        @Override public void submit(PoseStack poses,SubmitNodeCollector collector,int light,EndermanRenderState state,float yRot,float xRot){
            if(state.isInvisible)return;int channel=Math.round(255*Mth.clamp(((VampireRenderState)state).brightness,0,1));int tint=0xFF000000|channel<<16|channel<<8|channel;
            collector.order(2).submitModel(getParentModel(),state,poses,EYES,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,tint,null,state.outlineColor,null);
        }
    }
}
