package com.ronm19.enderism.entity;

import com.ronm19.enderism.RavagerEndermanConfig;
import com.ronm19.enderism.entity.ai.RavagerEndermanBrain;
import com.ronm19.enderism.raid.RavagerRaids;
import com.ronm19.enderism.spawning.RavagerOutpostSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #019: upright siege combat on the shared Enderman foundation. */
public final class RavagerEnderman extends AbstractEnderman {
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(RavagerEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(RavagerEnderman.class, EntityDataSerializers.LONG);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private boolean striking, homeChecked;
    private @Nullable BlockPos outpostHome;
    public RavagerEnderman(EntityType<? extends RavagerEnderman> type, Level level) { super(type, level); this.applyRuntimeAttributes(true); }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, RavagerEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, RavagerEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, RavagerEndermanConfig.DEFAULT_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, RavagerEndermanConfig.DEFAULT_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, .65).add(Attributes.ATTACK_KNOCKBACK, 0);
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !RavagerEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(RavagerEndermanConfig.health());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(RavagerEndermanConfig.speed());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(RavagerEndermanConfig.damage());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(RavagerEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected RavagerEndermanBrain createMastermindBrain() { return new RavagerEndermanBrain(this); }
    @Override public RavagerEndermanBrain getMastermindBrain() { return (RavagerEndermanBrain)super.getMastermindBrain(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)CombatPhase.IDLE.ordinal()); data.define(PHASE_START, 0L);
    }
    public CombatPhase getCombatPhase() { int phase = this.entityData.get(PHASE); return phase >= 0 && phase < PHASES.length ? PHASES[phase] : CombatPhase.IDLE; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public void setCombatPhase(CombatPhase phase) {
        if (!this.level().isClientSide() && this.getCombatPhase() != phase) {
            this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal());
        }
    }
    public float getEyeBrightness() { return this.getCombatPhase() == CombatPhase.IDLE || this.getCombatPhase() == CombatPhase.GUARDING ? .7F : 1; }
    public @Nullable BlockPos getOutpostHome() { return this.outpostHome; }
    public boolean hasActiveRaid() {
        Raid raid = RavagerRaids.getCurrentRaid(this); return raid != null && raid.isActive() && !raid.isStopped() && !raid.isOver();
    }
    public boolean isRaidAlly(Entity entity) {
        return entity instanceof EnderMan && !(entity instanceof EndermanGolem) || RavagerRaids.isRaidAlly(entity);
    }
    @Override protected boolean considersEntityAsAlly(Entity entity) { return this.isRaidAlly(entity) || super.considersEntityAsAlly(entity); }
    public boolean isEligibleEnemy(@Nullable LivingEntity target) {
        if (target == null || target == this || !target.isAlive() || target.isRemoved() || target.level() != this.level()
                || this.isRaidAlly(target) || this.isAlliedTo(target) || !target.canBeSeenAsEnemy()
                || target instanceof ServerPlayer player && player.hasDisconnected()) return false;
        if (target instanceof Player player) return !player.isCreative() && !player.isSpectator();
        if (this.hasActiveRaid() && (target instanceof AbstractVillager || target instanceof IronGolem || target instanceof EndermanGolem)) return true;
        return this.getLastHurtByMob() == target && (target instanceof Monster || target instanceof IronGolem);
    }
    @Override public boolean canAttack(LivingEntity target) { return super.canAttack(target) && this.isEligibleEnemy(target); }
    @Override public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target != null && !this.isEligibleEnemy(target) ? null : target);
        if (this.getTarget() != null && !this.isEligibleEnemy(this.getTarget())) super.setTarget(null);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        return !this.striking && target instanceof LivingEntity victim && this.getMastermindBrain().tryBeginWrath(level, victim, level.getGameTime());
    }
    /** A single native physical hit, reachable only at the committed Wrath impact frame. */
    public boolean performWrathStrike(ServerLevel level, LivingEntity target) {
        if (this.striking || !this.getMastermindBrain().isWrathImpactFrame(target) || !this.canAttack(target)
                || !this.isWithinMeleeAttackRange(target) || !this.getSensing().hasLineOfSight(target)) return false;
        float before = target.getHealth() + target.getAbsorptionAmount();
        boolean hit; this.striking = true;
        try { hit = super.doHurtTarget(level, target); } finally { this.striking = false; }
        if (hit && target.getHealth() + target.getAbsorptionAmount() < before && this.mayFinishWrath(target)) {
            target.knockback(RavagerEndermanConfig.meleeKnockback(), this.getX() - target.getX(), this.getZ() - target.getZ());
            if (this.mayFinishWrath(target)) this.impactCue(level, target); return true;
        }
        return false;
    }
    private boolean mayFinishWrath(LivingEntity target) {
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && this.getMastermindBrain().isWrathImpactFrame(target)
                && this.canAttack(target) && this.isWithinMeleeAttackRange(target) && this.getSensing().hasLineOfSight(target);
    }
    public void impactCue(ServerLevel level, LivingEntity target) {
        var below = target.blockPosition().below();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), target.getX(), target.getY(.15), target.getZ(), 10, .4, .2, .4, .08);
        this.playSound(SoundEvents.RAVAGER_ATTACK, .85F, .75F);
    }
    public void warningCue(boolean roar) { this.playSound(roar ? SoundEvents.RAVAGER_STUNNED : SoundEvents.RAVAGER_ATTACK, .7F, roar ? .85F : .65F); }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.RAVAGER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.RAVAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.RAVAGER_DEATH; }
    @Override protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        this.playSound(SoundEvents.RAVAGER_STEP, .3F, .8F);
    }
    @Override protected int teleportAttemptCooldownTicks() { return RavagerEndermanConfig.teleportCooldown(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().isCommitted() && super.teleport(); }
    public boolean tryTacticalTeleport(Vec3 requested) {
        return this.level() instanceof ServerLevel && !this.isNoAi() && !this.getCombatPhase().isCommitted() && this.trySafeTeleport(requested, 4);
    }
    @Override public void travel(Vec3 input) {
        if (this.level() instanceof ServerLevel level && this.getCombatPhase() == CombatPhase.CHARGING) {
            Vec3 before = this.position(); Vec3 step = this.getMastermindBrain().prepareChargeStep(level);
            this.setDeltaMovement(step.x, Math.min(0, this.getDeltaMovement().y), step.z);
            super.travel(Vec3.ZERO); this.getMastermindBrain().finishChargeStep(level, before);
        } else {
            boolean braced = this.getCombatPhase().bracesBody();
            if (!this.level().isClientSide() && braced) this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
            super.travel(braced ? Vec3.ZERO : input);
        }
    }
    @Override public void aiStep() {
        if (this.level() instanceof ServerLevel level) {
            RavagerRaids.tick(this);
            if (!this.homeChecked) { this.homeChecked = true; this.outpostHome = RavagerOutpostSpawns.findOutpostCenter(level, this.blockPosition()); }
            if ((!this.isAlive() || this.isNoAi()) && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter();
        }
        super.aiStep();
    }
    @Override public void die(DamageSource source) {
        super.die(source);
        if (!this.isAlive() && !this.level().isClientSide()) { this.getMastermindBrain().clearEncounter(); RavagerRaids.onDeath(this, source); }
    }
    @Override public void onRemoval(Entity.RemovalReason reason) {
        if (!this.level().isClientSide()) { this.getMastermindBrain().clearEncounter(); RavagerRaids.onRemoval(this, reason); }
        super.onRemoval(reason);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output);
        output.putBoolean("RavagerHomeChecked", this.homeChecked);
        if (this.outpostHome != null) output.store("RavagerOutpostHome", BlockPos.CODEC, this.outpostHome);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter(); this.getMastermindBrain().readCooldowns(input);
        this.homeChecked = input.getBooleanOr("RavagerHomeChecked", false); this.outpostHome = input.read("RavagerOutpostHome", BlockPos.CODEC).orElse(null);
    }
    public static boolean checkRavagerSpawnRules(EntityType<RavagerEnderman> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) return false;
        var ground = level.getBlockState(pos.below());
        if (!ground.blocksMotion() || !ground.getFluidState().isEmpty() || isChargeHazard(ground)) return false;
        for (int y = 0; y < Mth.ceil(type.getHeight()); y++) {
            BlockPos cell = pos.above(y); var state = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, state, state.getFluidState(), type)) return false;
        }
        return true;
    }
    public static boolean isChargeHazard(net.minecraft.world.level.block.state.BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.SWEET_BERRY_BUSH);
    }
    public static boolean isSpawnTime(ServerLevelAccessor level) { return level.getLevel().dimension() == Level.OVERWORLD; }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) { return isSpawnTime(level) && RavagerOutpostSpawns.isSpawnHabitat(level, pos); }
    public enum CombatPhase {
        IDLE, GUARDING, PURSUIT, SEARCHING, WRATH_WINDUP, WRATH_STRIKE, CHARGE_WINDUP, CHARGING, ROAR_WINDUP, ROARING, RECOVERY;
        public boolean bracesBody() { return this == WRATH_WINDUP || this == WRATH_STRIKE || this == CHARGE_WINDUP || this == ROAR_WINDUP || this == ROARING || this == RECOVERY; }
        public boolean isCommitted() { return this.bracesBody() || this == CHARGING; }
    }
}
