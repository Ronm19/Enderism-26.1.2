package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.DarkEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.DarkEnderman;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A single native Brain arbitrates melee, bounded ranged casts, and tactical ground pursuit. */
public final class DarkEndermanBrain extends MastermindBrain {
    private final DarkEnderman dark;
    private @Nullable LivingEntity trackedTarget;
    private @Nullable Vec3 lastLocated;
    private Vec3 observedVelocity=Vec3.ZERO;
    private long observedAt;
    private long locatedUntil;
    private long nextDetectionAt;
    private long nextDecisionAt;
    private long nextPathAt;
    private long nextProgressAt;
    private long nextBarrageAt;
    private long nextWitherAttemptAt;
    private long nextRepositionAt;
    private @Nullable WeakReference<LivingEntity> castTarget;
    private int castKind;
    private int shotsRemaining;
    private long nextShotAt;
    private @Nullable Vec3 progressPosition;
    private int stuckTicks;
    private boolean leftSide;
    private int barrageShots;
    private int witherCasts;
    private int blockedShots;
    private int repositions;
    private @Nullable Vec3 lastRepositionDestination;

    public DarkEndermanBrain(DarkEnderman owner){super(owner);this.dark=owner;}
    @Override protected int memoryTicks(){return DarkEndermanConfig.memoryTicks();}
    @Override protected int meleeCooldownTicks(){return DarkEndermanConfig.meleeCooldownTicks();}
    @Override protected boolean brainControlsMeleeCooldown(){return true;}
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target){return super.isLegalThreat(target)
            &&!(target instanceof ServerPlayer player&&player.hasDisconnected());}

    @Override protected @Nullable LivingEntity observeThreat(){
        LivingEntity remembered=super.observeThreat();
        if(remembered!=null)return remembered;
        if(!(this.owner.level() instanceof ServerLevel level))return null;
        long now=level.getGameTime();
        if(now<this.nextDetectionAt)return null;
        this.nextDetectionAt=now+DarkEndermanConfig.SENSOR_INTERVAL_TICKS;
        double range=DarkEndermanConfig.followRange(),distance=range*range;
        ServerPlayer selected=null;
        for(ServerPlayer player:level.getEntitiesOfClass(ServerPlayer.class,this.owner.getBoundingBox().inflate(range))){
            double candidate=this.owner.distanceToSqr(player);
            if(candidate<=distance&&this.isVisibleThreat(player)){selected=player;distance=candidate;}
        }
        if(selected!=null)this.rememberVisibleThreat(selected);
        return selected;
    }

    @Override protected void rememberVisibleThreat(LivingEntity target){
        long now=this.owner.level().getGameTime();
        if(target!=this.trackedTarget){
            this.cancelCast();this.dark.cancelOwnedBolts();
            this.observedVelocity=Vec3.ZERO;this.progressPosition=this.owner.position();
            this.stuckTicks=0;this.nextProgressAt=now+20;
        }else if(this.lastLocated!=null&&now>this.observedAt){
            Vec3 velocity=target.position().subtract(this.lastLocated).multiply(1,0,1).scale(1.0/(now-this.observedAt));
            this.observedVelocity=velocity.lengthSqr()>.2025?velocity.normalize().scale(.45):velocity;
        }
        super.rememberVisibleThreat(target);
        this.trackedTarget=target;this.lastLocated=target.position();
        this.observedAt=now;this.locatedUntil=now+this.memoryTicks();
    }

    @Override protected void tick(ServerLevel level,AbstractEnderman body,long now){
        this.initializeActuators();
        if(!body.isAlive()||body.isRemoved()||body.isNoAi()){this.clearEncounter();return;}
        if(this.trackedTarget!=null&&!this.isLegalThreat(this.trackedTarget))this.clearEncounter();
        LivingEntity visible=this.observeThreat();
        if(this.castKind!=0){this.tickCast(level,now);return;}
        if(this.tickFloating()){this.dark.setHuntState(DarkEnderman.HuntState.PURSUING);return;}
        if(visible==null){this.investigateObservation(level,now);return;}
        this.sampleProgress(now);
        if(this.owner.isWithinMeleeAttackRange(visible)){
            this.tickVisibleCombat(visible);this.dark.setHuntState(DarkEnderman.HuntState.MELEE_COMBAT);return;
        }
        if(now>=this.nextDecisionAt){
            this.nextDecisionAt=now+20;
            if(this.dark.hasClearProjectilePath(visible)){
                if(this.beginWither(level,visible,now)||this.beginBarrage(level,visible,now))return;
                if(this.isRetreating()&&this.owner.distanceToSqr(visible)>144&&this.tryReposition(level,this.lastLocated,now))return;
            }else{
                this.blockedShots++;
                if(this.tryReposition(level,this.lastLocated,now))return;
            }
            if(this.stuckTicks>=40&&this.tryReposition(level,this.lastLocated,now))return;
        }
        // The native melee actuator also provides efficient pursuit while ranged actions recover.
        this.tickVisibleCombat(visible);this.dark.setHuntState(DarkEnderman.HuntState.APPROACHING);
    }

    public boolean beginBarrage(ServerLevel level,LivingEntity target,long now){
        double distance=this.owner.distanceToSqr(target);
        if(this.castKind!=0||now<this.nextBarrageAt||distance<square(DarkEndermanConfig.barrageMinimumRange())
                ||!this.isVisibleThreat(target)||!this.dark.canUseRangedAttack(target,DarkEndermanConfig.barrageMaximumRange()))return false;
        this.rememberVisibleThreat(target);
        this.nextBarrageAt=now+DarkEndermanConfig.barrageCooldownTicks();
        this.shotsRemaining=DarkEndermanConfig.barrageProjectileCount();
        this.beginCast(level,target,1,now+DarkEndermanConfig.barrageWindupTicks());
        return true;
    }

    public boolean beginWither(ServerLevel level,LivingEntity target,long now){
        if(this.castKind!=0||now<this.nextWitherAttemptAt||!this.dark.canApplyWitherNow()
                ||this.owner.isWithinMeleeAttackRange(target)||!this.isVisibleThreat(target)
                ||!this.dark.canUseRangedAttack(target,DarkEndermanConfig.witherRange()))return false;
        this.rememberVisibleThreat(target);
        this.nextWitherAttemptAt=now+DarkEndermanConfig.witherAttemptRecoveryTicks();
        this.shotsRemaining=1;
        this.beginCast(level,target,2,now+DarkEndermanConfig.witherWindupTicks());
        return true;
    }

    private void beginCast(ServerLevel level,LivingEntity target,int kind,long firstShot){
        this.castKind=kind;this.castTarget=new WeakReference<>(target);this.nextShotAt=firstShot;
        this.stopCombat();this.stopAmbient();this.clearTarget();this.stopHorizontalMovement();
        this.state=State.COMBAT;
        long finalShot=firstShot+(kind==1?(long)(this.shotsRemaining-1)*DarkEndermanConfig.barrageShotIntervalTicks():0);
        this.dark.setCastVisuals(kind,finalShot);
        this.dark.setHuntState(kind==1?DarkEnderman.HuntState.RANGED_COMBAT:DarkEnderman.HuntState.CASTING_WITHER);
        this.dark.warnCast(level,kind);
    }

    private void tickCast(ServerLevel level,long now){
        LivingEntity target=this.castTarget==null?null:this.castTarget.get();
        double range=this.castKind==2?DarkEndermanConfig.witherRange():DarkEndermanConfig.barrageMaximumRange();
        if(target==null||target!=this.trackedTarget||!this.isVisibleThreat(target)||this.owner.distanceToSqr(target)>range*range
                ||this.owner.isWithinMeleeAttackRange(target)||this.owner.isInWater()){
            this.cancelCast();return;
        }
        this.stopHorizontalMovement();
        this.owner.getLookControl().setLookAt(target,40,40);
        if(now<this.nextShotAt)return;
        // Each release performs a fresh muzzle-to-target collision check; the wind-up never deals damage.
        if(!this.dark.hasClearProjectilePath(target)){
            this.blockedShots++;this.cancelCast();this.tryReposition(level,this.lastLocated,now);return;
        }
        boolean launched=this.castKind==2?this.dark.launchWitherBolt(target):this.dark.launchBarrageBolt(target);
        if(!launched){this.cancelCast();return;}
        if(this.castKind==2)this.witherCasts++;else this.barrageShots++;
        if(--this.shotsRemaining<=0){this.cancelCast();return;}
        this.nextShotAt=now+DarkEndermanConfig.barrageShotIntervalTicks();
    }

    public void cancelCast(){
        this.castTarget=null;this.castKind=0;this.shotsRemaining=0;this.nextShotAt=0;
        this.dark.setCastVisuals(0,0);
    }

    private boolean tryReposition(ServerLevel level,@Nullable Vec3 observed,long now){
        if(observed==null||now<this.nextRepositionAt||this.owner.isInWater()
                ||this.owner.distanceToSqr(observed)>square(DarkEndermanConfig.repositionActivationRange())
                ||this.owner.distanceToSqr(observed)<16)return false;
        this.nextRepositionAt=now+DarkEndermanConfig.repositionCooldownTicks();
        Vec3 radial=this.owner.position().subtract(observed).multiply(1,0,1);
        if(radial.lengthSqr()<.01)radial=new Vec3(1,0,0);
        radial=radial.normalize();
        Vec3 side=new Vec3(-radial.z,0,radial.x).scale(this.leftSide?1:-1);
        this.leftSide=!this.leftSide;
        double distance=DarkEndermanConfig.repositionDistance();
        Vec3[] candidates={observed.add(side.scale(distance)),observed.add(side.scale(-distance)),observed.add(radial.scale(distance))};
        for(Vec3 candidate:candidates){
            Vec3 standing=this.dark.findRepositionDestination(candidate,observed);
            if(standing!=null){
                if(this.dark.tryRepositionTeleport(standing,observed)){
                    this.repositions++;this.lastRepositionDestination=this.owner.position();
                    this.progressPosition=this.owner.position();this.stuckTicks=0;this.nextProgressAt=now+20;
                    this.dark.setHuntState(DarkEnderman.HuntState.REPOSITIONING);return true;
                }
                break; // One actual shared teleport attempt; failed attempts still consume the interval.
            }
        }
        return false;
    }

    private boolean isRetreating(){
        return this.lastLocated!=null&&this.observedVelocity.dot(this.lastLocated.subtract(this.owner.position()).multiply(1,0,1).normalize())>.04;
    }
    private void sampleProgress(long now){
        if(now<this.nextProgressAt)return;this.nextProgressAt=now+20;
        if(this.lastLocated!=null&&this.owner.distanceToSqr(this.lastLocated)>9&&this.progressPosition!=null
                &&this.owner.position().distanceToSqr(this.progressPosition)<.36)this.stuckTicks+=20;
        else this.stuckTicks=0;
        this.progressPosition=this.owner.position();
    }
    private void stopHorizontalMovement(){
        this.owner.getNavigation().stop();this.owner.getMoveControl().setWantedPosition(this.owner.getX(),this.owner.getY(),this.owner.getZ(),0);
        Vec3 motion=this.owner.getDeltaMovement();this.owner.setDeltaMovement(0,motion.y,0);
    }

    private void investigateObservation(ServerLevel level,long now){
        this.stopCombat();this.clearTarget();
        if(!this.isTargetLocalized()){
            if(this.trackedTarget!=null||this.state!=State.IDLE)this.clearEncounter();
            this.dark.setHuntState(DarkEnderman.HuntState.IDLE);this.tickAmbient();return;
        }
        this.stopAmbient();this.state=State.INVESTIGATE;this.dark.setHuntState(DarkEnderman.HuntState.SEARCHING);
        Vec3 point=this.lastLocated;if(point==null)return;
        this.sampleProgress(now);
        this.owner.getLookControl().setLookAt(point.x,point.y+1.5,point.z,35,35);
        if(this.owner.distanceToSqr(point)<=2.25){this.stopHorizontalMovement();return;}
        if(now>=this.nextDecisionAt){
            this.nextDecisionAt=now+20;
            if(this.tryReposition(level,point,now))return;
        }
        if(now>=this.nextPathAt){
            this.nextPathAt=now+20;
            if(level.hasChunkAt(BlockPos.containing(point)))this.owner.getNavigation().moveTo(point.x,point.y,point.z,1);
            // Neither failed pathfinding nor tactical retries extend this frozen observation.
        }
    }
    private static double square(double value){return value*value;}
    public boolean isTargetLocalized(){return this.lastLocated!=null&&this.owner.level().getGameTime()<this.locatedUntil;}
    public int getTrackedTargetId(){return this.trackedTarget==null?-1:this.trackedTarget.getId();}
    public @Nullable Vec3 getLastLocatedPosition(){return this.isTargetLocalized()?this.lastLocated:null;}
    public long getLocatedUntil(){return this.locatedUntil;}
    public long nextBarrageReadyAt(){return this.nextBarrageAt;}
    public long nextWitherAttemptReadyAt(){return this.nextWitherAttemptAt;}
    public long nextRepositionReadyAt(){return this.nextRepositionAt;}
    public int getBarrageShotCount(){return this.barrageShots;}
    public int getWitherCastCount(){return this.witherCasts;}
    public int getBlockedShotCount(){return this.blockedShots;}
    public int getRepositionCount(){return this.repositions;}
    public int getStuckTicks(){return this.stuckTicks;}
    public @Nullable Vec3 getLastRepositionDestination(){return this.lastRepositionDestination;}
    @Override public void clearEncounter(){
        super.clearEncounter();this.cancelCast();this.dark.cancelOwnedBolts();
        this.trackedTarget=null;this.lastLocated=null;this.locatedUntil=0;this.observedVelocity=Vec3.ZERO;
        this.progressPosition=null;this.stuckTicks=0;this.dark.clearAbilityVisuals();
        // Cast recovery, teleport recovery and the owner's persisted successful-Wither deadline are retained.
    }
}
