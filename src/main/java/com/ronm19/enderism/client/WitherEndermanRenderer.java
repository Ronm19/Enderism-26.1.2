package com.ronm19.enderism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.WitherEnderman;
import com.ronm19.enderism.entity.WitherEnderman.CombatPhase;
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

/** Native Enderman silhouette with restrained bone armor and synchronized violet eyes. */
public final class WitherEndermanRenderer extends EndermanRenderer {
    private static final Identifier BODY=texture("wither_enderman"),REGALIA=texture("wither_enderman_regalia");
    private static final RenderType EYES=RenderTypes.eyes(texture("wither_enderman_eyes"));
    public WitherEndermanRenderer(EntityRendererProvider.Context context){super(context);this.shadowRadius=.5F;this.model=new WitherEndermanModel(this.model.root());this.layers.removeIf(layer->layer instanceof EnderEyesLayer||layer instanceof CarriedBlockLayer);this.addLayer(new BoneAdornment(context));this.addLayer(new WitherEyes());}
    private static Identifier texture(String name){return Identifier.fromNamespaceAndPath(Enderism.MOD_ID,"textures/entity/wither_enderman/"+name+".png");}
    @Override public Identifier getTextureLocation(EndermanRenderState state){return BODY;}
    @Override public WitherRenderState createRenderState(){return new WitherRenderState();}
    @Override public void extractRenderState(EnderMan entity,EndermanRenderState state,float partialTicks){
        super.extractRenderState(entity,state,partialTicks);state.carriedBlock.clear();state.isCreepy=false;
        if(entity instanceof WitherEnderman wither){var render=(WitherRenderState)state;render.phase=wither.getCombatPhase();render.phaseAge=Math.max(0,wither.level().getGameTime()-wither.getCombatPhaseStart()+partialTicks);render.warmupTicks=wither.getWarmupTicksRemaining();render.brightness=Mth.clamp(wither.getEyeBrightness(),0,1);}
    }
    public static final class WitherRenderState extends EndermanRenderState {
        public CombatPhase phase=CombatPhase.IDLE;public float phaseAge,brightness=.5F;public int warmupTicks;
    }
    private final class BoneAdornment extends RenderLayer<EndermanRenderState,EndermanModel<EndermanRenderState>> {
        private final WitherEndermanModel.Regalia regalia;
        BoneAdornment(EntityRendererProvider.Context context){super(WitherEndermanRenderer.this);this.regalia=new WitherEndermanModel.Regalia(context.bakeLayer(WitherEndermanModel.REGALIA_LAYER),(WitherEndermanModel)getParentModel());}
        @Override public void submit(PoseStack poses,SubmitNodeCollector collector,int light,EndermanRenderState state,float yRot,float xRot){if(!state.isInvisible)collector.order(1).submitModel(this.regalia,state,poses,RenderTypes.entityCutout(REGALIA),light,OverlayTexture.NO_OVERLAY,0xFFFFFFFF,null,state.outlineColor,null);}
    }
    private final class WitherEyes extends RenderLayer<EndermanRenderState,EndermanModel<EndermanRenderState>> {
        WitherEyes(){super(WitherEndermanRenderer.this);}
        @Override public void submit(PoseStack poses,SubmitNodeCollector collector,int light,EndermanRenderState state,float yRot,float xRot){
            if(state.isInvisible)return;int channel=Math.round(255*Mth.clamp(((WitherRenderState)state).brightness,0,1));int tint=0xFF000000|channel<<16|channel<<8|channel;
            collector.order(2).submitModel(getParentModel(),state,poses,EYES,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,tint,null,state.outlineColor,null);
        }
    }
}


