package com.ronm19.enderism.entity;

import com.ronm19.enderism.DarkEndermanConfig;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.ai.DarkEndermanBrain;
import com.ronm19.enderism.entity.ai.MastermindBrain;
import com.ronm19.enderism.entity.projectile.DarkMagicBolt;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #011: a ground-based Dark Forest hybrid hunter with a persistent, per-owner Wither interval. */
public final class DarkEnderman extends AbstractEnderman {
    public static final int WITHER_APPLICATION_COOLDOWN_TICKS = DarkEndermanConfig.WITHER_APPLICATION_COOLDOWN_TICKS;
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID,"spawns_dark_enderman"));
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(DarkEnderman.class,EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> CAST_KIND = SynchedEntityData.defineId(DarkEnderman.class,EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> CAST_END = SynchedEntityData.defineId(DarkEnderman.class,EntityDataSerializers.LONG);
    private static final HuntState[] STATES = HuntState.values();
    private static final DustParticleOptions SHADOW = new DustParticleOptions(0x6F209E,.65F);
    private final Map<UUID,WeakReference<DarkMagicBolt>> ownedBolts = new HashMap<>();
    private @Nullable UUID pendingWitherBolt;
    private long nextWitherApplyAt;
    private int witherApplications;
    private int successfulMeleeHits;
    private @Nullable Vec3 repositionObservation;

    public DarkEnderman(EntityType<? extends DarkEnderman> type, Level level) {
        super(type,level); this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH,DarkEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE,DarkEndermanConfig.DEFAULT_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED,DarkEndermanConfig.DEFAULT_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE,DarkEndermanConfig.DEFAULT_FOLLOW_RANGE);
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !DarkEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(DarkEndermanConfig.health());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(DarkEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(DarkEndermanConfig.movementSpeed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(DarkEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(),this.getMaxHealth()));
    }
    @Override protected MastermindBrain createMastermindBrain() { return new DarkEndermanBrain(this); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(HUNT_STATE,(byte)0); data.define(CAST_KIND,(byte)0); data.define(CAST_END,0L);
    }
    public HuntState getHuntState() { int state=this.entityData.get(HUNT_STATE); return state>=0&&state<STATES.length?STATES[state]:HuntState.IDLE; }
    public void setHuntState(HuntState state) { if(!this.level().isClientSide())this.entityData.set(HUNT_STATE,(byte)state.ordinal()); }
    public int getCastKind() { return this.entityData.get(CAST_KIND); }
    public int getCastTicksRemaining() { return (int)Math.max(0L,this.entityData.get(CAST_END)-this.level().getGameTime()); }
    public void setCastVisuals(int kind,long end) {
        if(!this.level().isClientSide()){this.entityData.set(CAST_KIND,(byte)kind);this.entityData.set(CAST_END,end);}
    }
    public void clearAbilityVisuals() { this.setCastVisuals(0,0); this.setHuntState(HuntState.IDLE); }
    public float getEyeBrightness() {
        if(this.getCastKind()==2)return 1.0F;
        if(this.getCastKind()==1||this.getHuntState()!=HuntState.IDLE)return .96F;
        long clock=Math.floorMod(this.level().getOverworldClockTime(),24000L);
        return clock>=13000&&clock<23000?.84F:.62F;
    }
    public static boolean eligibleTarget(LivingEntity target) {
        return target.isAlive()&&!target.isRemoved()&&!(target instanceof Player p&&(p.isCreative()||p.isSpectator()))
                &&!(target instanceof ServerPlayer p&&p.hasDisconnected());
    }
    public boolean isLegalRangedTarget(LivingEntity target) {
        return eligibleTarget(target)&&target.level()==this.level()&&target!=this&&this.canAttack(target)&&!this.isAlliedTo(target);
    }
    public Vec3 getMuzzlePosition() { return this.position().add(0,1.85,0); }
    public Vec3 getRangedAimPoint(LivingEntity target) { return target.position().add(0,target.getBbHeight()*.55,0); }
    public boolean hasClearProjectilePath(LivingEntity target) {
        if(!(this.level() instanceof ServerLevel level)||!this.isLegalRangedTarget(target))return false;
        Vec3 start=this.getMuzzlePosition(),end=this.getRangedAimPoint(target);
        BlockPos low=BlockPos.containing(Math.min(start.x,end.x),Math.min(start.y,end.y),Math.min(start.z,end.z));
        BlockPos high=BlockPos.containing(Math.max(start.x,end.x),Math.max(start.y,end.y),Math.max(start.z,end.z));
        return level.hasChunksAt(low,high)&&level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    public boolean canUseRangedAttack(LivingEntity target,double range) {
        return this.isAlive()&&!this.isRemoved()&&!this.isNoAi()&&this.isLegalRangedTarget(target)
                &&this.distanceToSqr(target)<=range*range&&this.getSensing().hasLineOfSight(target)&&this.hasClearProjectilePath(target);
    }
    @Override public boolean doHurtTarget(ServerLevel level,Entity target) {
        if(!this.isAlive()||this.isRemoved()||this.isNoAi()||this.getCastKind()!=0||!(target instanceof LivingEntity living)
                ||!this.isLegalRangedTarget(living)||!this.isWithinMeleeAttackRange(living)||!this.getSensing().hasLineOfSight(living))return false;
        boolean accepted=super.doHurtTarget(level,target);
        if(accepted){
            this.successfulMeleeHits++;
            level.sendParticles(SHADOW,living.getX(),living.getY()+living.getBbHeight()*.55,living.getZ(),5,.22,.3,.22,.025);
            this.playSound(SoundEvents.PLAYER_ATTACK_STRONG,.5F,.7F);
        }
        return accepted;
    }
    public int getSuccessfulMeleeHitCount(){return this.successfulMeleeHits;}

    public boolean canApplyWitherNow() { this.pruneBolts(); return this.pendingWitherBolt==null&&this.level().getGameTime()>=this.nextWitherApplyAt; }
    public long nextWitherReadyAt(){return this.nextWitherApplyAt;}
    public int getWitherApplicationCount(){return this.witherApplications;}
    public @Nullable UUID getPendingWitherBoltId(){return this.pendingWitherBolt;}
    public int getOwnedBoltCount(){this.pruneBolts();return this.ownedBolts.size();}
    public boolean launchBarrageBolt(LivingEntity target){return this.launchMagicBolt(target,DarkMagicBolt.Kind.BLINDING);}
    public boolean launchWitherBolt(LivingEntity target){return this.launchMagicBolt(target,DarkMagicBolt.Kind.WITHERING);}
    public boolean launchMagicBolt(LivingEntity target,DarkMagicBolt.Kind kind) {
        double range=kind==DarkMagicBolt.Kind.WITHERING?DarkEndermanConfig.witherRange():DarkEndermanConfig.barrageMaximumRange();
        if(!(this.level() instanceof ServerLevel level)||!this.canUseRangedAttack(target,range)
                ||kind==DarkMagicBolt.Kind.WITHERING&&!this.canApplyWitherNow())return false;
        this.pruneBolts(); if(this.ownedBolts.size()>=6)return false;
        DarkMagicBolt bolt=DarkMagicBolt.create(level,this,target,kind);
        this.ownedBolts.put(bolt.getUUID(),new WeakReference<>(bolt));
        if(kind==DarkMagicBolt.Kind.WITHERING)this.pendingWitherBolt=bolt.getUUID();
        if(!level.addFreshEntity(bolt)){this.onBoltRemoved(bolt);return false;}
        this.playSound(SoundEvents.EVOKER_CAST_SPELL,.4F,kind==DarkMagicBolt.Kind.WITHERING?.6F:1.0F);
        return true;
    }
    public boolean isBoltValid(DarkMagicBolt bolt) {
        WeakReference<DarkMagicBolt> reference=this.ownedBolts.get(bolt.getUUID());
        return this.isAlive()&&!this.isRemoved()&&!this.isNoAi()&&bolt.level()==this.level()
                &&reference!=null&&reference.get()==bolt&&bolt.getOwner()==this;
    }
    /** Only the owned special projectile calls this after its one native damage event was accepted. */
    public boolean tryApplyWither(DarkMagicBolt bolt,LivingEntity target) {
        if(!(this.level() instanceof ServerLevel level)||bolt.getKind()!=DarkMagicBolt.Kind.WITHERING
                ||!bolt.getUUID().equals(this.pendingWitherBolt)||!this.isBoltValid(bolt)||!this.isLegalRangedTarget(target)
                ||!target.getUUID().equals(bolt.getIntendedTargetUuid())||level.getGameTime()<this.nextWitherApplyAt)return false;
        if(!target.addEffect(new MobEffectInstance(MobEffects.WITHER,DarkEndermanConfig.witherDurationTicks(),DarkEndermanConfig.witherAmplifier()),this))return false;
        // This is the only write that starts an application interval; casts, target changes and misses do not.
        this.nextWitherApplyAt=level.getGameTime()+WITHER_APPLICATION_COOLDOWN_TICKS;
        this.witherApplications++;
        level.sendParticles(ParticleTypes.SMOKE,target.getX(),target.getY()+1,target.getZ(),8,.25,.45,.25,.025);
        this.playSound(SoundEvents.WITHER_HURT,.45F,1.4F);
        return true;
    }
    public void onBoltRemoved(DarkMagicBolt bolt) {
        this.ownedBolts.remove(bolt.getUUID());
        if(bolt.getUUID().equals(this.pendingWitherBolt))this.pendingWitherBolt=null;
    }
    private void pruneBolts() {
        if (this.level() instanceof ServerLevel level) {
            // A bolt outside entity-ticking chunks still expires while its owner is active.
            // Removal calls onBoltRemoved, so discard from a snapshot rather than a map iterator.
            for (WeakReference<DarkMagicBolt> reference : List.copyOf(this.ownedBolts.values())) {
                DarkMagicBolt bolt = reference.get();
                if (bolt != null && !bolt.isRemoved() && bolt.hasExpired(level.getGameTime())) bolt.discard();
            }
        }
        this.ownedBolts.entrySet().removeIf(entry->{DarkMagicBolt bolt=entry.getValue().get();return bolt==null||bolt.isRemoved();});
        if(this.pendingWitherBolt!=null&&!this.ownedBolts.containsKey(this.pendingWitherBolt))this.pendingWitherBolt=null;
    }
    public void cancelOwnedBolts() {
        for(WeakReference<DarkMagicBolt> reference:List.copyOf(this.ownedBolts.values())){DarkMagicBolt bolt=reference.get();if(bolt!=null&&!bolt.isRemoved())bolt.discard();}
        this.ownedBolts.clear();this.pendingWitherBolt=null;
    }
    public void warnCast(ServerLevel level,int kind) {
        level.sendParticles(kind==2?ParticleTypes.SMOKE:SHADOW,this.getX(),this.getY()+1.75,this.getZ(),kind==2?8:5,.3,.4,.3,.015);
        this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK,.4F,kind==2?.65F:.9F);
    }

    public @Nullable Vec3 findRepositionDestination(Vec3 requested,Vec3 observed) {
        if(!(this.level() instanceof ServerLevel level)||!finite(requested)||!finite(observed)||this.isNoAi()||this.isInWater())return null;
        for(int offset:new int[]{0,1,-1,2,-2}){
            Vec3 standing=new Vec3(requested.x,BlockPos.containing(requested.x,observed.y,requested.z).getY()+offset,requested.z);
            if(this.validReposition(level,standing,observed))return standing;
        }
        return null;
    }
    private boolean validReposition(ServerLevel level,Vec3 standing,Vec3 observed) {
        if(!finite(standing)||!finite(observed)||this.distanceToSqr(standing)<4||Math.abs(standing.y-observed.y)>2||standing.y<=level.getMinY()
                ||standing.y+this.getBbHeight()>=level.getMaxY()||this.distanceToSqr(observed)>square(DarkEndermanConfig.repositionActivationRange()))return false;
        double radius=standing.subtract(observed).multiply(1,0,1).lengthSqr();
        if(radius<9||radius>square(DarkEndermanConfig.repositionDistance()+1.5))return false;
        AABB box=this.getBoundingBox().move(standing.subtract(this.position()));
        BlockPos low=BlockPos.containing(Math.min(observed.x,box.minX),Math.min(observed.y,box.minY)-1,Math.min(observed.z,box.minZ));
        BlockPos high=BlockPos.containing(Math.max(observed.x,box.maxX),Math.max(observed.y+this.getEyeHeight(),box.maxY),Math.max(observed.z,box.maxZ));
        if(!level.getWorldBorder().isWithinBounds(box)||!level.hasChunksAt(low,high))return false;
        BlockState support=level.getBlockState(BlockPos.containing(standing).below());
        if(!support.blocksMotion()||support.is(BlockTags.LEAVES)||hazard(support)||!level.noCollision(this,box)||level.containsAnyLiquid(box))return false;
        for(BlockPos cell:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))){
            BlockState state=level.getBlockState(cell);if(state.is(BlockTags.LEAVES)||hazard(state))return false;
        }
        return level.clip(new ClipContext(standing.add(0,1.85,0),observed.add(0,1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;
    }
    public boolean tryRepositionTeleport(Vec3 requested,Vec3 observed) {
        if(!(this.level() instanceof ServerLevel))return false;
        Vec3 destination=this.findRepositionDestination(requested,observed);if(destination==null)return false;
        this.repositionObservation=observed;
        try{return this.trySafeTeleport(destination,0);}finally{this.repositionObservation=null;}
    }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level,Vec3 destination) {
        return this.repositionObservation!=null?this.validReposition(level,destination,this.repositionObservation)
                :level.hasChunkAt(BlockPos.containing(destination))&&!level.getBlockState(BlockPos.containing(destination).below()).is(BlockTags.LEAVES);
    }
    private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private static double square(double d){return d*d;}
    private static boolean hazard(BlockState state){return !state.getFluidState().isEmpty()||state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)
            ||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE)||state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.CACTUS)
            ||state.is(Blocks.POWDER_SNOW)||state.is(Blocks.SWEET_BERRY_BUSH)||state.is(Blocks.WITHER_ROSE);}
    @Override public void travel(Vec3 input) {
        if(this.getCastKind()!=0){Vec3 motion=this.getDeltaMovement();this.setDeltaMovement(0,motion.y,0);input=new Vec3(0,input.y,0);}
        super.travel(input);
    }
    @Override protected boolean teleport(){return this.getCastKind()==0&&super.teleport();}
    @Override public void aiStep(){
        if (!this.level().isClientSide()) this.pruneBolts();
        super.aiStep();
        if(!this.level().isClientSide()&&(!this.isAlive()||this.isNoAi()))this.getMastermindBrain().clearEncounter();
    }
    @Override public void setNoAi(boolean noAi){super.setNoAi(noAi);if(noAi&&!this.level().isClientSide()&&this.getMastermindBrain()!=null)this.getMastermindBrain().clearEncounter();}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount){boolean hurt=super.hurtServer(level,source,amount);if(hurt){((DarkEndermanBrain)this.getMastermindBrain()).cancelCast();if(!this.isAlive())this.getMastermindBrain().clearEncounter();}return hurt;}
    @Override public void onRemoval(Entity.RemovalReason reason){if(!this.level().isClientSide()&&this.getMastermindBrain()!=null)this.getMastermindBrain().clearEncounter();super.onRemoval(reason);}
    @Override protected void addAdditionalSaveData(ValueOutput output){super.addAdditionalSaveData(output);output.putLong("DarkWitherReadyAt",this.nextWitherApplyAt);}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);this.getMastermindBrain().clearEncounter();this.nextWitherApplyAt=Math.max(0L,input.getLongOr("DarkWitherReadyAt",0));this.applyRuntimeAttributes(false);}
    public static boolean checkDarkSpawnRules(EntityType<DarkEnderman> type,ServerLevelAccessor level,EntitySpawnReason reason,BlockPos pos,RandomSource random){
        if(reason!=EntitySpawnReason.NATURAL&&reason!=EntitySpawnReason.CHUNK_GENERATION)return true;
        if(!isSpawnHabitat(level,pos)||!Monster.checkMonsterSpawnRules(type,level,reason,pos,random))return false;
        for(int y=0;y<3;y++){BlockPos cell=pos.above(y);BlockState state=level.getBlockState(cell);if(!NaturalSpawner.isValidEmptySpawnBlock(level,cell,state,state.getFluidState(),type))return false;}
        return true;
    }
    public static boolean isSpawnTime(ServerLevelAccessor level){long clock=Math.floorMod(level.getLevel().getOverworldClockTime(),24000L);return level.getLevel().dimension()==Level.OVERWORLD&&clock>=13000&&clock<23000;}
    public static boolean isSpawnHabitat(ServerLevelAccessor level,BlockPos pos){return isSpawnTime(level)&&level.getBiome(pos).is(SPAWN_BIOMES);}
    public enum HuntState{IDLE,APPROACHING,RANGED_COMBAT,MELEE_COMBAT,REPOSITIONING,CASTING_WITHER,PURSUING,SEARCHING}
}
