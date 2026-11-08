package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.FrostEndermanConfig;
import com.ronm19.enderism.effect.FrostParalysis;
import com.ronm19.enderism.entity.ai.FrostEndermanBrain;
import com.ronm19.enderism.entity.ai.MastermindBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;

/** #005: cold-aware melee, warned owned paralysis, and a finite local Hypothermia pulse. */
public final class FrostEnderman extends AbstractEnderman {
    public static final double NORMAL_DETECTION_RANGE = 64.0;
    public static final double SNOWSTORM_DETECTION_RANGE = 74.0;
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_frost_enderman"));
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(
            FrostEnderman.class, EntityDataSerializers.BYTE);
    private static final HuntState[] HUNT_STATES = HuntState.values();
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(FrostEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_END = SynchedEntityData.defineId(FrostEnderman.class, EntityDataSerializers.LONG);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private boolean resolvingAction;
    public boolean isResolvingAction(){return this.resolvingAction;}

    public FrostEnderman(EntityType<? extends FrostEnderman> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Preserve established physical attributes; the separate abilities have bounded cooldowns.
        return AbstractEnderman.createAttributes().add(Attributes.FOLLOW_RANGE, SNOWSTORM_DETECTION_RANGE);
    }

    @Override
    protected MastermindBrain createMastermindBrain() {
        return new FrostEndermanBrain(this);
    }
    @Override public FrostEndermanBrain getMastermindBrain() { return (FrostEndermanBrain)super.getMastermindBrain(); }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.resolvingAction||this.level()!=level||!this.isAlive()||this.isRemoved()||!(target instanceof LivingEntity victim)||!this.isLegalFrostTarget(victim)
                ||!this.isWithinMeleeAttackRange(victim)||!this.getSensing().hasLineOfSight(victim)||this.getMastermindBrain().hasPendingAbility()) return false;
        this.resolvingAction=true;
        try {
        long transaction=this.getMastermindBrain().getActionEpoch();
        float before=target instanceof LivingEntity living?living.getHealth()+living.getAbsorptionAmount():0;
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof ServerPlayer player && player.getHealth()+player.getAbsorptionAmount()<before
                && this.isAlive() && !this.isRemoved() && this.getMastermindBrain().getActionEpoch()==transaction && this.isLegalFrostTarget(player)) {
            FrostParalysis.tryApplyOnHit(player, this);
        }
        return hit;
        }finally{this.resolvingAction=false;}
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(HUNT_STATE, (byte) HuntState.IDLE.ordinal());
        data.define(PHASE,(byte)0);data.define(PHASE_END,0L);
    }
    public CombatPhase getCombatPhase(){int value=this.entityData.get(PHASE);return value>=0&&value<PHASES.length?PHASES[value]:CombatPhase.IDLE;}
    public void setCombatPhase(CombatPhase phase,long end){if(!this.level().isClientSide()){this.entityData.set(PHASE,(byte)phase.ordinal());this.entityData.set(PHASE_END,end);}}
    public int getWarmupTicksRemaining(){return (int)Math.max(0,this.entityData.get(PHASE_END)-this.level().getGameTime());}
    public boolean isLegalFrostTarget(LivingEntity target){return target!=null&&target!=this&&target.level()==this.level()&&target.isAlive()&&!target.isRemoved()
            &&!(target instanceof EnderMan)&&!this.isAlliedTo(target)&&this.canAttack(target)
            &&!(target instanceof Player p&&(p.isCreative()||p.isSpectator()))&&!(target instanceof ServerPlayer p&&p.hasDisconnected());}
    public boolean canCastOn(LivingEntity target,double range){return this.isAlive()&&!this.isRemoved()&&!this.isNoAi()&&this.isLegalFrostTarget(target)
            &&this.distanceToSqr(target)<=range*range&&this.getSensing().hasLineOfSight(target);}
    public void abilityWarning(ServerLevel level,CombatPhase phase){level.sendParticles(ParticleTypes.SNOWFLAKE,this.getX(),this.getY(.6),this.getZ(),phase==CombatPhase.PARALYSIS_WINDUP?7:12,.35,.5,.35,.02);this.playSound(SoundEvents.POWDER_SNOW_STEP,.7F,.65F);}
    public boolean releaseFrozenParalysis(ServerLevel level,ServerPlayer target){
        if(this.resolvingAction||this.level()!=level||!this.getMastermindBrain().isParalysisReleaseFrame(target)||!this.canCastOn(target,FrostEndermanConfig.paralysisRange()))return false;
        this.resolvingAction=true;try{
        long transaction=this.getMastermindBrain().getActionEpoch();boolean applied=FrostParalysis.tryApplyAbility(target,this);
        if(this.getMastermindBrain().getActionEpoch()!=transaction||!this.getMastermindBrain().isParalysisReleaseFrame(target)||!this.canCastOn(target,FrostEndermanConfig.paralysisRange())){FrostParalysis.release(target,this);return false;}
        if(applied){level.sendParticles(ParticleTypes.SNOWFLAKE,target.getX(),target.getY(.5),target.getZ(),10,.3,.6,.3,.02);this.playSound(SoundEvents.GLASS_BREAK,.4F,1.4F);}return applied;
        }finally{this.resolvingAction=false;}
    }
    public int releaseHypothermia(ServerLevel level){
        if(this.resolvingAction||this.level()!=level||!this.getMastermindBrain().isHypothermiaReleaseFrame()||!level.getGameRules().get(GameRules.FREEZE_DAMAGE))return 0;
        this.resolvingAction=true;try{
        double radius=FrostEndermanConfig.hypothermiaRadius();long transaction=this.getMastermindBrain().getActionEpoch();
        var targets=level.getEntitiesOfClass(LivingEntity.class,this.getBoundingBox().inflate(radius),t->this.canCastOn(t,radius)&&t.canFreeze());
        targets.sort(Comparator.comparingDouble((LivingEntity t)->this.distanceToSqr(t)).thenComparing(Entity::getUUID));int affected=0,attempted=0;
        DamageSource damage=new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.FREEZE),this);
        for(LivingEntity target:targets){
            if(this.getMastermindBrain().getActionEpoch()!=transaction||!this.getMastermindBrain().isHypothermiaReleaseFrame()||this.isNoAi()||!this.isAlive()||this.isRemoved())break;
            if(attempted>=FrostEndermanConfig.maximumHypothermiaTargets())break;
            if(!this.canCastOn(target,radius)||!target.canFreeze())continue;
            attempted++;
            float before=target.getHealth()+target.getAbsorptionAmount();
            boolean accepted=target.hurtServer(level,damage,(float)FrostEndermanConfig.hypothermiaDamage());
            if(this.getMastermindBrain().getActionEpoch()!=transaction||!this.getMastermindBrain().isHypothermiaReleaseFrame())break;
            if(!this.canCastOn(target,radius))continue;
            if(!accepted||target.getHealth()+target.getAbsorptionAmount()>=before)continue;
            var current=target.getEffect(MobEffects.SLOWNESS);int ticks=FrostEndermanConfig.hypothermiaSlownessTicks();
            if(current==null||current.getAmplifier()==0&&current.getDuration()<ticks)target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,ticks,0),this);
            affected++;
        }
        if(this.getMastermindBrain().getActionEpoch()==transaction&&this.getMastermindBrain().isHypothermiaReleaseFrame()){level.sendParticles(ParticleTypes.SNOWFLAKE,this.getX(),this.getY(.4),this.getZ(),20,radius*.4,.5,radius*.4,.03);this.playSound(SoundEvents.GLASS_BREAK,.65F,.8F);}return affected;
        }finally{this.resolvingAction=false;}
    }

    public HuntState getHuntState() {
        int ordinal = this.entityData.get(HUNT_STATE);
        return ordinal >= 0 && ordinal < HUNT_STATES.length ? HUNT_STATES[ordinal] : HuntState.IDLE;
    }

    public void setHuntState(HuntState state) {
        if (!this.level().isClientSide()) this.entityData.set(HUNT_STATE, (byte) state.ordinal());
    }

    public float getEyeBrightness() {
        if(this.getCombatPhase()!=CombatPhase.IDLE)return 1;
        return switch (this.getHuntState()) {
            case IDLE -> 0.68F;
            case SENSING -> 0.78F;
            case SEARCHING -> 0.86F;
            case PURSUING -> 1.0F;
        };
    }

    /** The native query requires active snow precipitation at this position, not merely a cold biome. */
    public static double detectionRange(ServerLevel level, BlockPos position) {
        return level.precipitationAt(position) == Biome.Precipitation.SNOW
                ? SNOWSTORM_DETECTION_RANGE : NORMAL_DETECTION_RANGE;
    }

    @Override
    public void aiStep() {
        if(!this.level().isClientSide()&&this.isNoAi()&&this.getMastermindBrain().hasPendingAbility())this.getMastermindBrain().clearEncounter();
        super.aiStep();
        if (this.level().isClientSide() && this.isAlive() && this.tickCount % 10 == 0) {
            this.level().addParticle(ParticleTypes.SNOWFLAKE,
                    this.getX() + (this.random.nextDouble() - 0.5) * this.getBbWidth(),
                    this.getY() + this.random.nextDouble() * this.getBbHeight(),
                    this.getZ() + (this.random.nextDouble() - 0.5) * this.getBbWidth(),
                    0.0, -0.005, 0.0);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if(!this.level().isClientSide())this.getMastermindBrain().clearEncounter();
        super.remove(reason);
    }
    @Override public void setNoAi(boolean value){super.setNoAi(value);if(value&&!this.level().isClientSide()&&this.getMastermindBrain()!=null)this.getMastermindBrain().clearEncounter();}
    @Override public void die(DamageSource source){super.die(source);if(!this.isAlive()&&!this.level().isClientSide())this.getMastermindBrain().clearEncounter();}
    @Override public void travel(Vec3 input){if(!this.level().isClientSide()&&this.getMastermindBrain().hasPendingAbility()){this.setDeltaMovement(0,this.getDeltaMovement().y,0);super.travel(Vec3.ZERO);}else super.travel(input);}
    @Override protected boolean teleport(){return !this.getMastermindBrain().hasPendingAbility()&&super.teleport();}
    @Override protected void addAdditionalSaveData(ValueOutput output){super.addAdditionalSaveData(output);this.getMastermindBrain().writeCooldowns(output);}

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.getMastermindBrain().clearEncounter();
        this.getMastermindBrain().readCooldowns(input);
        this.setHuntState(HuntState.IDLE);
    }

    public static boolean checkFrostSpawnRules(EntityType<FrostEnderman> type, ServerLevelAccessor level,
                                              EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        for (int height = 0; height < 3; height++) {
            BlockPos cell = pos.above(height);
            var block = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, block, block.getFluidState(), type)) return false;
        }
        return true;
    }

    public static boolean isSpawnTime(ServerLevelAccessor level) {
        long clock = Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L);
        return level.getLevel().dimension() == Level.OVERWORLD && clock >= 13000L && clock < 23000L;
    }

    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        return isSpawnTime(level) && level.getBiome(pos).is(SPAWN_BIOMES);
    }

    public enum HuntState { IDLE, SENSING, PURSUING, SEARCHING }
    public enum CombatPhase { IDLE,PARALYSIS_WINDUP,PARALYSIS,HYPOTHERMIA_WINDUP,HYPOTHERMIA,RECOVERY }
}
