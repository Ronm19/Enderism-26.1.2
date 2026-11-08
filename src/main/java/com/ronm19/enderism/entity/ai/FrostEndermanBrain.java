package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.FrostEndermanConfig;
import com.ronm19.enderism.entity.FrostEnderman.CombatPhase;
import java.lang.ref.WeakReference;
import com.ronm19.enderism.effect.FrostParalysis;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.FrostEnderman;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Sampled cold awareness feeding the existing native Brain's pursuit and investigation actuators. */
public final class FrostEndermanBrain extends MastermindBrain {
    private static final int SENSOR_INTERVAL_TICKS = 10;
    private static final int LOCATION_MEMORY_TICKS = 100;
    private final FrostEnderman frost;
    private @Nullable ServerPlayer sensedPlayer;
    private @Nullable Vec3 lastLocated;
    private long locatedUntil;
    private long nextDetectionAt;
    private long nextPathAt;
    private double awarenessRange = FrostEnderman.NORMAL_DETECTION_RANGE;
    private @Nullable WeakReference<LivingEntity> pendingTarget;
    private long phaseEndsAt,nextParalysisAt,nextHypothermiaAt,actionEpoch,encounterStartedAt=-1;
    private boolean paralysisRelease,hypothermiaRelease;

    public FrostEndermanBrain(FrostEnderman owner) {
        super(owner);
        this.frost = owner;
    }

    @Override
    protected int memoryTicks() { return LOCATION_MEMORY_TICKS; }
    @Override protected int meleeCooldownTicks(){return FrostEndermanConfig.frostStrikeCooldownTicks();}
    @Override protected boolean brainControlsMeleeCooldown(){return true;}
    public long getActionEpoch(){return this.actionEpoch;}
    public boolean hasPendingAbility(){return this.frost.getCombatPhase()!=CombatPhase.IDLE;}
    public @Nullable LivingEntity getPendingTarget(){return this.pendingTarget==null?null:this.pendingTarget.get();}
    public long nextParalysisReadyAt(){return this.nextParalysisAt;}
    public long nextHypothermiaReadyAt(){return this.nextHypothermiaAt;}
    private boolean mayCast(ServerLevel level,LivingEntity target,double range){return this.owner.level()==level&&!this.frost.isResolvingAction()&&!this.hasPendingAbility()&&this.frost.canCastOn(target,range)&&!this.owner.isPassenger()&&!this.owner.isVehicle();}
    public boolean tryBeginParalysis(ServerLevel level,ServerPlayer target,long now){
        if(now<this.nextParalysisAt||!this.mayCast(level,target,FrostEndermanConfig.paralysisRange())
                ||!FrostParalysis.canApply(target)&&!FrostParalysis.isHeldBy(target,this.frost))return false;
        var current=target.getEffect(com.ronm19.enderism.registry.ModEffects.FROST_PARALYSIS);
        if(FrostParalysis.isHeldBy(target,this.frost)&&(current==null||current.getDuration()<=FrostEndermanConfig.paralysisWindupTicks()+1))return false;
        this.beginCast(level,target,CombatPhase.PARALYSIS_WINDUP,now+FrostEndermanConfig.paralysisWindupTicks());
        this.nextParalysisAt=now+FrostEndermanConfig.paralysisCooldownTicks();return true;
    }
    public boolean tryBeginHypothermia(ServerLevel level,LivingEntity target,long now){
        if(now<this.nextHypothermiaAt||!this.mayCast(level,target,FrostEndermanConfig.hypothermiaRadius()))return false;
        this.beginCast(level,target,CombatPhase.HYPOTHERMIA_WINDUP,now+FrostEndermanConfig.hypothermiaWindupTicks());
        this.nextHypothermiaAt=now+FrostEndermanConfig.hypothermiaCooldownTicks();return true;
    }
    private void beginCast(ServerLevel level,LivingEntity target,CombatPhase phase,long end){
        this.actionEpoch++;this.stopCombat();this.stopAmbient();this.stopFloating();this.clearTarget();this.owner.getNavigation().stop();
        this.owner.setDeltaMovement(0,this.owner.getDeltaMovement().y,0);this.pendingTarget=new WeakReference<>(target);this.phaseEndsAt=end;
        this.frost.setHuntState(FrostEnderman.HuntState.PURSUING);this.frost.setCombatPhase(phase,end);this.frost.abilityWarning(level,phase);
    }
    public boolean isParalysisReleaseFrame(ServerPlayer target){return this.paralysisRelease&&this.getPendingTarget()==target&&this.frost.getCombatPhase()==CombatPhase.PARALYSIS&&this.owner.isAlive()&&!this.owner.isRemoved()&&!this.owner.isNoAi();}
    public boolean isHypothermiaReleaseFrame(){return this.hypothermiaRelease&&this.frost.getCombatPhase()==CombatPhase.HYPOTHERMIA&&this.owner.isAlive()&&!this.owner.isRemoved()&&!this.owner.isNoAi();}
    private boolean tickCast(ServerLevel level,long now){
        CombatPhase phase=this.frost.getCombatPhase();if(phase==CombatPhase.IDLE)return false;
        this.owner.getNavigation().stop();this.owner.setDeltaMovement(0,this.owner.getDeltaMovement().y,0);
        if(phase==CombatPhase.RECOVERY){if(now>=this.phaseEndsAt)this.frost.setCombatPhase(CombatPhase.IDLE,0);return true;}
        if(phase==CombatPhase.PARALYSIS||phase==CombatPhase.HYPOTHERMIA){if(now>=this.phaseEndsAt){this.pendingTarget=null;this.phaseEndsAt=now+FrostEndermanConfig.recoveryTicks();this.frost.setCombatPhase(CombatPhase.RECOVERY,this.phaseEndsAt);}return true;}
        LivingEntity target=this.getPendingTarget();double range=phase==CombatPhase.PARALYSIS_WINDUP?FrostEndermanConfig.paralysisRange():FrostEndermanConfig.hypothermiaRadius();
        if(!this.frost.canCastOn(target,range)||this.owner.isPassenger()||this.owner.isVehicle()){this.clearEncounter();return true;}
        this.owner.getLookControl().setLookAt(target,35,25);if(now<this.phaseEndsAt)return true;
        long transaction=this.actionEpoch;
        if(phase==CombatPhase.PARALYSIS_WINDUP&&target instanceof ServerPlayer player){
            this.frost.setCombatPhase(CombatPhase.PARALYSIS,now+4);this.paralysisRelease=true;
            try{this.frost.releaseFrozenParalysis(level,player);}finally{this.paralysisRelease=false;}
        }else if(phase==CombatPhase.HYPOTHERMIA_WINDUP){
            this.frost.setCombatPhase(CombatPhase.HYPOTHERMIA,now+4);this.hypothermiaRelease=true;
            try{this.frost.releaseHypothermia(level);}finally{this.hypothermiaRelease=false;}
        }
        if(this.actionEpoch==transaction)this.phaseEndsAt=now+4;return true;
    }

    @Override
    protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }

    @Override
    protected boolean isVisibleThreat(@Nullable LivingEntity target) {
        // The shared generic helper caps its range at64; Frost's approved storm range is74.
        return this.isLegalThreat(target) && this.owner.distanceToSqr(target) <= this.awarenessRange * this.awarenessRange
                && this.owner.getSensing().hasLineOfSight(target);
    }

    public int getSensedPlayerId() { return this.sensedPlayer == null ? -1 : this.sensedPlayer.getId(); }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.owner.level().getGameTime() < this.locatedUntil; }
    public @Nullable Vec3 getLastLocatedPosition() { return this.isTargetLocalized() ? this.lastLocated : null; }

    @Override
    protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if(this.owner.isNoAi()||!this.owner.isAlive()||this.owner.isRemoved()){this.clearEncounter();return;}
        if (this.sensedPlayer != null && !this.isLegalThreat(this.sensedPlayer)) this.clearEncounter();
        if(this.tickCast(level,now))return;
        if (now >= this.nextDetectionAt) {
            this.nextDetectionAt = now + SENSOR_INTERVAL_TICKS;
            this.samplePresence(level, now);
        }
        LivingEntity visible = this.isVisibleThreat(this.sensedPlayer) ? this.sensedPlayer : this.observeThreat();
        if (visible != null) {
            if(this.encounterStartedAt<0)this.encounterStartedAt=now;
            this.rememberVisibleThreat(visible);
            this.locate(visible.position(), now);
        }
        if (this.tickFloating()) {
            this.frost.setHuntState(FrostEnderman.HuntState.SEARCHING);
            return;
        }
        if (visible != null) {
            long engaged=now-this.encounterStartedAt;
            if(engaged>=120&&this.tryBeginHypothermia(level,visible,now))return;
            if(engaged>=80&&visible instanceof ServerPlayer player&&this.tryBeginParalysis(level,player,now))return;
            this.tickVisibleCombat(visible);
            this.frost.setHuntState(FrostEnderman.HuntState.PURSUING);
            return;
        }
        this.stopCombat();
        this.clearTarget();
        if (this.isTargetLocalized()) {
            this.stopAmbient();
            this.state = State.INVESTIGATE;
            this.investigatePoint(level, this.lastLocated, now);
            this.frost.setHuntState(FrostEnderman.HuntState.SEARCHING);
            return;
        }
        if (this.lastLocated != null && now >= this.locatedUntil) {
            this.lastLocated = null;
            this.owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            this.owner.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            this.owner.getNavigation().stop();
        }
        if (this.sensedPlayer != null) {
            // Stationary/hidden presence remains awareness, never a continuously updated live attack target.
            this.stopAmbient();
            this.owner.getNavigation().stop();
            this.state = State.INVESTIGATE;
            this.frost.setHuntState(FrostEnderman.HuntState.SENSING);
            return;
        }
        if (this.state != State.IDLE) this.clearEncounter();
        this.frost.setHuntState(FrostEnderman.HuntState.IDLE);
        this.tickAmbient();
    }

    private void samplePresence(ServerLevel level, long now) {
        this.awarenessRange = FrostEnderman.detectionRange(level, this.owner.blockPosition());
        ServerPlayer previous = this.sensedPlayer;
        ServerPlayer candidate = previous;
        double rangeSquared = this.awarenessRange * this.awarenessRange;
        if (!this.isLegalThreat(candidate) || this.owner.distanceToSqr(candidate) > rangeSquared) {
            candidate = null;
            double closest = rangeSquared;
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(this.awarenessRange))) {
                double distance = this.owner.distanceToSqr(player);
                if (distance <= closest && this.isLegalThreat(player)) {
                    candidate = player;
                    closest = distance;
                }
            }
        }
        this.sensedPlayer = candidate;
        if (candidate != previous && candidate != null) {
            // A single detected location can be investigated, but hidden positions are not refreshed by each scan.
            this.owner.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            this.locate(candidate.position(), now);
            this.nextPathAt = now;
        }
    }

    private void locate(Vec3 position, long now) {
        this.lastLocated = position;
        this.locatedUntil = now + this.memoryTicks();
        WalkTarget previous = this.owner.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (previous == null || !previous.getTarget().currentPosition().equals(position))
            previous = new WalkTarget(new BlockPosTracker(position), 1.0F, 1);
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, previous, this.memoryTicks());
    }

    private void investigatePoint(ServerLevel level, Vec3 position, long now) {
        this.owner.getLookControl().setLookAt(position.x, position.y + 1.5, position.z, 45.0F, 45.0F);
        if (this.owner.distanceToSqr(position) <= 2.25) {
            this.owner.getNavigation().stop();
            this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0.0);
            return;
        }
        if (now >= this.nextPathAt) {
            this.nextPathAt = now + 20;
            if (level.hasChunkAt(BlockPos.containing(position)))
                this.owner.getNavigation().moveTo(position.x, position.y, position.z, 1.0);
        }
        // A blocked/completed path does not forget the sensed identity and magically reacquire hidden coordinates.
    }

    @Override
    public void clearEncounter() {
        this.actionEpoch++;this.pendingTarget=null;this.paralysisRelease=false;this.hypothermiaRelease=false;this.phaseEndsAt=0;this.encounterStartedAt=-1;
        super.clearEncounter();
        FrostParalysis.releaseOwnedBy(this.frost);
        this.sensedPlayer = null;
        this.lastLocated = null;
        this.locatedUntil = 0;
        this.frost.setHuntState(FrostEnderman.HuntState.IDLE);
        this.frost.setCombatPhase(CombatPhase.IDLE,0);
    }
    public void writeCooldowns(ValueOutput output){output.putLong("FrostParalysisAfter",this.nextParalysisAt);output.putLong("FrostHypothermiaAfter",this.nextHypothermiaAt);}
    public void readCooldowns(ValueInput input){long now=this.owner.level().getGameTime();this.nextParalysisAt=Math.min(input.getLongOr("FrostParalysisAfter",0),now+FrostEndermanConfig.paralysisCooldownTicks());this.nextHypothermiaAt=Math.min(input.getLongOr("FrostHypothermiaAfter",0),now+FrostEndermanConfig.hypothermiaCooldownTicks());}
}
