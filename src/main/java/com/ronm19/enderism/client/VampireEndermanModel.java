package com.ronm19.enderism.client;

import com.ronm19.enderism.Enderism;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Slender native Enderman bones and atlas, with small attached noble dress pieces. */
public final class VampireEndermanModel extends EndermanModel<EndermanRenderState> {
    public static final float BODY_SCALE = 1.10F;
    public static final ModelLayerLocation REGALIA_LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "vampire_enderman"), "regalia");
    public VampireEndermanModel(ModelPart root) { super(root); }

    public static LayerDefinition createRegaliaLayer() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        var crown = root.addOrReplaceChild("circlet", CubeListBuilder.create(), PartPose.ZERO);
        piece(crown,"front",32,0,-8.2F,-4.1F,8.5F,.6F,.55F);
        piece(crown,"back",32,0,-8.2F,4.1F,8.5F,.6F,.55F);
        piece(crown,"left",32,4.1F,-8.2F,0,.55F,.6F,8.2F);
        piece(crown,"right",32,-4.1F,-8.2F,0,.55F,.6F,8.2F);
        for (int side : new int[] {-1,1}) {
            piece(crown,"front_tine_"+side,48,side*3.0F,-9.05F,-4.1F,.6F,1.5F,.55F);
            piece(crown,"side_tine_"+side,48,side*4.1F,-8.8F,1.8F,.55F,1.1F,.6F);
        }
        piece(crown,"central_tine",48,0,-9.4F,-4.1F,.7F,2.2F,.6F);
        piece(crown,"ruby",56,0,-8.35F,-4.45F,.85F,.8F,.35F);
        var dress = root.addOrReplaceChild("dress", CubeListBuilder.create(), PartPose.ZERO);
        var left = dress.addOrReplaceChild("left_collar", CubeListBuilder.create(), PartPose.offsetAndRotation(3.2F,-.5F,0,0,0,-.43F));
        piece(left,"panel",32,0,-1.05F,0,2.5F,3.4F,3.5F);
        var right = dress.addOrReplaceChild("right_collar", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.2F,-.5F,0,0,0,.43F));
        piece(right,"panel",32,0,-1.05F,0,2.5F,3.4F,3.5F);
        piece(dress,"brooch",56,0,1.5F,-2.35F,1.2F,1.2F,.4F);
        var cape = dress.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0,8).addBox(-4,0,0,8,22,1), PartPose.offset(0,1,2.25F));
        piece(cape,"tail_left",40,-2.8F,23.7F,.5F,2.2F,3.4F,.65F);
        piece(cape,"tail_middle",40,0,24.7F,.5F,2.3F,5.4F,.65F);
        piece(cape,"tail_right",40,2.8F,23.3F,.5F,2.2F,2.6F,.65F);
        return LayerDefinition.create(mesh,64,32);
    }
    private static void piece(PartDefinition parent,String name,int u,float x,float y,float z,float sx,float sy,float sz) {
        parent.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,0).addBox(-.5F,-.5F,-.5F,1,1,1),new PartPose(x,y,z,0,0,0,sx,sy,sz));
    }
    @Override public void setupAnim(EndermanRenderState state) {
        state.rightArmPose=state.leftArmPose=HumanoidModel.ArmPose.EMPTY; state.isUsingItem=false; state.carriedBlock.clear();
        super.setupAnim(state); this.root().xScale=this.root().yScale=this.root().zScale=BODY_SCALE; this.root().y=24-25*BODY_SCALE;
        if(state.deathTime>0)return;
        var render=(VampireEndermanRenderer.VampireRenderState)state;
        float breath=Mth.sin(state.ageInTicks*.055F), rise=Mth.clamp(render.phaseAge/Math.max(1,render.phaseAge+render.warmupTicks),0,1);
        switch(render.phase) {
            case IDLE -> {this.head.xRot-=.025F;this.head.zRot+=breath*.012F;this.rightArm.zRot+=breath*.012F;this.leftArm.zRot-=breath*.012F;}
            case PURSUING,FURY -> {lean(render.fury?.095F:.055F);this.rightArm.xRot*=render.fury?1.3F:1.12F;this.leftArm.xRot*=render.fury?1.3F:1.12F;}
            case SEARCHING -> {this.head.yRot+=.12F*breath;this.head.xRot-=.05F;}
            case STRIKE_WINDUP -> {this.rightArm.xRot=-1.3F*rise;this.rightArm.yRot=-.3F*rise;this.rightArm.zRot=-.2F*rise;this.leftArm.xRot=-.45F*rise;lean(-.035F*rise);}
            case STRIKE -> {float hit=1-Mth.clamp(render.phaseAge/6,0,1);this.rightArm.xRot=-.2F-.85F*hit;this.rightArm.yRot=.22F*hit;this.leftArm.xRot=-.25F*hit;lean(.14F*hit);}
            case DRAIN -> {float drain=1-Mth.clamp(render.phaseAge/10,0,1);this.rightArm.xRot=-.65F*drain;this.leftArm.xRot=-.55F*drain;this.rightArm.yRot=-.18F*drain;this.leftArm.yRot=.18F*drain;this.head.xRot+=.1F*drain;}
            case DASH_WINDUP -> {lean(.1F*rise);this.rightArm.xRot=.3F*rise;this.leftArm.xRot=.3F*rise;this.head.xRot-=.08F*rise;}
            case DASH -> {lean(.18F);this.rightArm.xRot=.45F;this.leftArm.xRot=.35F;this.rightLeg.xRot=-.24F;this.leftLeg.xRot=.24F;this.head.xRot-=.12F;}
            case LEAP -> {lean(.07F);this.rightArm.xRot=-.5F;this.leftArm.xRot=-.45F;this.rightLeg.xRot=-.25F;this.leftLeg.xRot=.18F;}
            case FURY_WINDUP -> {this.head.xRot-=.17F*rise;this.rightArm.xRot=-.5F*rise;this.leftArm.xRot=-.5F*rise;this.rightArm.zRot=-.38F*rise;this.leftArm.zRot=.38F*rise;lean(-.025F*rise);}
            case RECOVERY -> {float rest=1-Mth.clamp(render.phaseAge/10,0,1);this.rightArm.xRot=-.16F*rest;this.leftArm.xRot=-.1F*rest;}
        }
    }
    private void lean(float angle) {
        float sin=Mth.sin(angle),cos=Mth.cos(angle);
        for(var part:new ModelPart[]{this.body,this.head,this.rightArm,this.leftArm}){float y=part.y+5;part.y=y*cos-part.z*sin-5;part.z=y*sin+part.z*cos;part.xRot+=angle;}
    }
    public static final class Regalia extends Model<EndermanRenderState> {
        private final VampireEndermanModel body;
        public Regalia(ModelPart root,VampireEndermanModel body){super(root,RenderTypes::entityCutout);this.body=body;}
        @Override public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);this.body.setupAnim(state);follow(this.root,this.body.root());
            follow(this.root.getChild("circlet"),this.body.head);var dress=this.root.getChild("dress");follow(dress,this.body.body);
            var render=(VampireEndermanRenderer.VampireRenderState)state;var cape=dress.getChild("cape");
            float haste=render.phase==com.ronm19.enderism.entity.VampireEnderman.CombatPhase.DASH?.4F:render.phase==com.ronm19.enderism.entity.VampireEnderman.CombatPhase.LEAP?.3F:Math.min(.16F,state.walkAnimationSpeed*.2F);
            cape.xRot=.05F+haste+Mth.sin(state.ageInTicks*.07F)*.018F;cape.zRot=Mth.sin(state.ageInTicks*.045F)*.014F;
        }
        private static void follow(ModelPart target,ModelPart source){target.loadPose(source.storePose());target.xScale=source.xScale;target.yScale=source.yScale;target.zScale=source.zScale;}
    }
}
