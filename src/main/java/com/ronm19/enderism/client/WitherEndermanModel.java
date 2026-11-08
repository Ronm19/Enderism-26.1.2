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

/** Native Enderman anatomy, with a small bone circlet and attached shoulder armor. */
public final class WitherEndermanModel extends EndermanModel<EndermanRenderState> {
    public static final float BODY_SCALE = 1.16F;
    public static final ModelLayerLocation REGALIA_LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "wither_enderman"), "regalia");
    public WitherEndermanModel(ModelPart root) { super(root); }

    public static LayerDefinition createRegaliaLayer() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        var crown = root.addOrReplaceChild("circlet", CubeListBuilder.create(), PartPose.ZERO);
        piece(crown,"front",32,0,-8.2F,-4.1F,8.5F,.6F,.55F);
        piece(crown,"back",32,0,-8.2F,4.1F,8.5F,.6F,.55F);
        piece(crown,"left",32,4.1F,-8.2F,0,.55F,.6F,8.2F);
        piece(crown,"right",32,-4.1F,-8.2F,0,.55F,.6F,8.2F);
        for (int side : new int[] {-1,1}) {
            piece(crown,"front_tine_"+side,40,side*3.1F,-9.0F,-4.1F,.55F,1.5F,.55F);
            piece(crown,"side_tine_"+side,40,side*4.1F,-8.8F,2.1F,.55F,1.2F,.55F);
        }
        piece(crown,"central_tine",40,0,-9.2F,-4.1F,.65F,1.8F,.6F);
        root.addOrReplaceChild("right_shoulder", CubeListBuilder.create().texOffs(0,0).addBox(-2.5F,-2,-2,5,4,4), PartPose.ZERO);
        root.addOrReplaceChild("left_shoulder", CubeListBuilder.create().texOffs(0,0).mirror().addBox(-2.5F,-2,-2,5,4,4), PartPose.ZERO);
        return LayerDefinition.create(mesh,64,32);
    }
    private static void piece(PartDefinition parent,String name,int u,float x,float y,float z,float sx,float sy,float sz) {
        parent.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,0).addBox(-.5F,-.5F,-.5F,1,1,1),new PartPose(x,y,z,0,0,0,sx,sy,sz));
    }
    @Override public void setupAnim(EndermanRenderState state) {
        state.rightArmPose=state.leftArmPose=HumanoidModel.ArmPose.EMPTY; state.isUsingItem=false; state.carriedBlock.clear();
        super.setupAnim(state); this.root().xScale=this.root().yScale=this.root().zScale=BODY_SCALE; this.root().y=24-25*BODY_SCALE;
        if(state.deathTime>0)return;
        var render=(WitherEndermanRenderer.WitherRenderState)state;
        float breath=Mth.sin(state.ageInTicks*.05F), rise=Mth.clamp(render.phaseAge/Math.max(1,render.phaseAge+render.warmupTicks),0,1);
        switch(render.phase) {
            case IDLE -> {this.head.xRot-=.025F;this.head.zRot+=breath*.008F;this.rightArm.zRot+=breath*.012F;this.leftArm.zRot-=breath*.012F;}
            case PURSUING -> {lean(.06F);this.rightArm.xRot*=1.12F;this.leftArm.xRot*=1.12F;}
            case SEARCHING -> {this.head.yRot+=.13F*breath;this.head.xRot-=.04F;}
            case GRASP_WINDUP -> {this.rightArm.xRot=-1.45F*rise;this.rightArm.yRot=-.28F*rise;this.rightArm.zRot=-.16F*rise;this.leftArm.xRot=-.3F*rise;lean(-.025F*rise);}
            case GRASP -> {float hit=1-Mth.clamp(render.phaseAge/6,0,1);this.rightArm.xRot=-.15F-.9F*hit;this.rightArm.yRot=.22F*hit;this.leftArm.xRot=-.2F*hit;lean(.14F*hit);}
            case BARRAGE_WINDUP -> {this.rightArm.xRot=this.leftArm.xRot=-1.25F*rise;this.rightArm.yRot=-.23F*rise;this.leftArm.yRot=.23F*rise;this.head.xRot-=.07F*rise;}
            case BARRAGE -> {float pulse=Mth.sin(render.phaseAge*.6F)*.07F;this.rightArm.xRot=-1.2F+pulse;this.leftArm.xRot=-1.2F-pulse;this.rightArm.yRot=-.2F;this.leftArm.yRot=.2F;lean(.025F);}
            case ROSE_WINDUP -> {this.rightArm.xRot=this.leftArm.xRot=-1.45F*rise;this.rightArm.zRot=-.28F*rise;this.leftArm.zRot=.28F*rise;this.head.xRot+=.1F*rise;}
            case ROSE -> {float release=1-Mth.clamp(render.phaseAge/12,0,1);this.rightArm.xRot=this.leftArm.xRot=-.55F*release;this.rightArm.zRot=-.22F*release;this.leftArm.zRot=.22F*release;this.head.xRot+=.15F*release;}
            case RECOVERY -> {float rest=1-Mth.clamp(render.phaseAge/10,0,1);this.rightArm.xRot=-.15F*rest;this.leftArm.xRot=-.1F*rest;}
        }
    }
    private void lean(float angle) {
        float sin=Mth.sin(angle),cos=Mth.cos(angle);
        for(var part:new ModelPart[]{this.body,this.head,this.rightArm,this.leftArm}){float y=part.y+5;part.y=y*cos-part.z*sin-5;part.z=y*sin+part.z*cos;part.xRot+=angle;}
    }
    public static final class Regalia extends Model<EndermanRenderState> {
        private final WitherEndermanModel body;
        public Regalia(ModelPart root,WitherEndermanModel body){super(root,RenderTypes::entityCutout);this.body=body;}
        @Override public void setupAnim(EndermanRenderState state) {
            super.setupAnim(state);this.body.setupAnim(state);follow(this.root,this.body.root());
            follow(this.root.getChild("circlet"),this.body.head);
            follow(this.root.getChild("right_shoulder"),this.body.rightArm);
            follow(this.root.getChild("left_shoulder"),this.body.leftArm);
        }
        private static void follow(ModelPart target,ModelPart source){target.loadPose(source.storePose());target.xScale=source.xScale;target.yScale=source.yScale;target.zScale=source.zScale;}
    }
}
