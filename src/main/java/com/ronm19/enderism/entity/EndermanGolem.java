package com.ronm19.enderism.entity;

import com.ronm19.enderism.EndermanGolemConfig;
import com.ronm19.enderism.entity.ai.EndermanGolemBrain;
import com.ronm19.enderism.spawning.GolemVillageSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.Nullable;

/** A neutral, independent village guardian; defensive state is never inherited from another Enderman. */
public final class EndermanGolem extends AbstractEnderman {
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(EndermanGolem.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(EndermanGolem.class, EntityDataSerializers.LONG);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private @Nullable BlockPos homeVillage;
    private long nextVillageCheck;
    private boolean striking;

    public EndermanGolem(EntityType<? extends EndermanGolem> type, Level level) { super(type, level); this.applyRuntimeAttributes(true); }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, EndermanGolemConfig.DEFAULT_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, EndermanGolemConfig.DEFAULT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, EndermanGolemConfig.DEFAULT_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, EndermanGolemConfig.DEFAULT_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, .9).add(Attributes.ATTACK_KNOCKBACK, 0).add(Attributes.SCALE, 1);
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !EndermanGolemConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(EndermanGolemConfig.health());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(EndermanGolemConfig.speed());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(EndermanGolemConfig.damage());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(EndermanGolemConfig.followRange());
        this.getAttribute(Attributes.SCALE).setBaseValue(EndermanGolemConfig.sizeMultiplier());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    public static void registerEvents(IEventBus bus) { bus.addListener(EndermanGolem::acceptedDamage); }
    private static void acceptedDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || event.getHealthDamage() <= 0 && event.getReduction(DamageContainer.Reduction.ABSORPTION) <= 0
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (event.getEntity() instanceof EndermanGolem golem) golem.getMastermindBrain().recordDirectAttack(attacker);
        if (event.getEntity() instanceof AbstractVillager resident) {
            double radius = EndermanGolemConfig.protectionRange();
            for (EndermanGolem golem : level.getEntitiesOfClass(EndermanGolem.class, resident.getBoundingBox().inflate(radius),
                    guardian -> guardian.isAlive() && !guardian.isRemoved() && !guardian.isNoAi() && guardian.distanceToSqr(resident) <= radius * radius))
                golem.getMastermindBrain().recordVillageAttack(resident, attacker);
        }
    }
    @Override protected EndermanGolemBrain createMastermindBrain() { return new EndermanGolemBrain(this); }
    @Override public EndermanGolemBrain getMastermindBrain() { return (EndermanGolemBrain)super.getMastermindBrain(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    public @Nullable BlockPos getHomeVillage() { return this.homeVillage; }
    public @Nullable BlockPos getVillageHome() { return this.homeVillage; }
    public boolean isGuardianAlly(Entity entity) {
        return entity instanceof AbstractVillager || entity instanceof IronGolem || entity instanceof EndermanGolem
                || entity instanceof EnderMan && !(entity instanceof RavagerEnderman);
    }
    @Override protected boolean considersEntityAsAlly(Entity entity) { return this.isGuardianAlly(entity) || super.considersEntityAsAlly(entity); }
    public static boolean isVillageEnemyCandidate(LivingEntity candidate) {
        if (candidate instanceof Zombie || candidate instanceof Raider || candidate instanceof RavagerEnderman) return true;
        if (candidate instanceof Vex vex && vex.getOwner() instanceof Raider) return true;
        return candidate instanceof Mob mob && (mob.getTarget() instanceof AbstractVillager
                || mob.getTarget() instanceof IronGolem || mob.getTarget() instanceof EndermanGolem);
    }
    @Override public boolean canAttack(LivingEntity target) {
        return super.canAttack(target) && this.getMastermindBrain() != null && this.getMastermindBrain().isLegitimateThreat(target);
    }
    @Override public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target != null && (this.getMastermindBrain() == null || !this.getMastermindBrain().isLegitimateThreat(target)) ? null : target);
        if (this.getTarget() != null && (this.getMastermindBrain() == null || !this.getMastermindBrain().isLegitimateThreat(this.getTarget()))) super.setTarget(null);
        // This slow, heavy guardian uses its own speed tuning rather than vanilla Enderman's flat anger sprint bonus.
        this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(Identifier.withDefaultNamespace("attacking"));
    }
    @Override public void updatePersistentAnger(ServerLevel level, boolean stayAngryIfTargetPresent) {
        this.setPersistentAngerTarget(null); this.setPersistentAngerEndTime(0);
        if (this.getTarget() != null && !this.getMastermindBrain().isLegitimateThreat(this.getTarget())) this.setTarget(null);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)CombatPhase.IDLE.ordinal()); data.define(PHASE_START, 0L);
    }
    public CombatPhase getCombatPhase() { int phase = this.entityData.get(PHASE); return phase >= 0 && phase < PHASES.length ? PHASES[phase] : CombatPhase.IDLE; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public void setCombatPhase(CombatPhase phase) {
        if (!this.level().isClientSide() && phase != this.getCombatPhase()) {
            this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal());
        }
    }
    public float getEyeBrightness() {
        return switch (this.getCombatPhase()) { case IDLE, OBSERVING, PATROLLING -> .6F; case DE_ESCALATING, RECOVERY -> .75F; default -> 1; };
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        return !this.striking && target instanceof LivingEntity victim && this.getMastermindBrain().tryBeginCrusher(level, victim, level.getGameTime());
    }
    public boolean performCrusherStrike(ServerLevel level, LivingEntity target) {
        if (this.striking || !this.getMastermindBrain().isCrusherImpactFrame(target) || !this.canAttack(target)
                || !this.isWithinMeleeAttackRange(target) || !this.getSensing().hasLineOfSight(target)) return false;
        float before = target.getHealth() + target.getAbsorptionAmount(); boolean accepted; this.striking = true;
        try { accepted = super.doHurtTarget(level, target); } finally { this.striking = false; }
        if (accepted && target.getHealth() + target.getAbsorptionAmount() < before && this.mayFinishCrusher(target)) {
            target.knockback(EndermanGolemConfig.crusherKnockback(), this.getX() - target.getX(), this.getZ() - target.getZ());
            if (this.mayFinishCrusher(target)) this.impactCue(level, target); return true;
        }
        return false;
    }
    private boolean mayFinishCrusher(LivingEntity target) {
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && this.getMastermindBrain().isCrusherImpactFrame(target)
                && this.canAttack(target) && this.isWithinMeleeAttackRange(target) && this.getSensing().hasLineOfSight(target);
    }
    public void impactCue(ServerLevel level, LivingEntity target) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(target.blockPosition().below())),
                target.getX(), target.getY(.1), target.getZ(), 12, .6, .25, .6, .05);
        this.playSound(SoundEvents.IRON_GOLEM_ATTACK, .9F, .55F);
    }
    public void warningCue(boolean judgment) { this.playSound(judgment ? SoundEvents.IRON_GOLEM_DAMAGE : SoundEvents.IRON_GOLEM_ATTACK, .65F, .55F); }
    public @Nullable Vec3 planGuardianPosition(Vec3 position) { return this.findSafeTeleportDestination(position, 4); }
    public boolean tryGuardianTeleport(Vec3 position) {
        return !this.isNoAi() && this.getCombatPhase() == CombatPhase.INTERCEPTING && this.trySafeTeleport(position, 4);
    }
    @Override protected int teleportAttemptCooldownTicks() { return EndermanGolemConfig.teleportCooldown(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().isCommitted() && super.teleport(); }
    @Override public void travel(Vec3 input) {
        boolean braced = this.getCombatPhase().bracesBody();
        if (!this.level().isClientSide() && braced) this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        super.travel(braced ? Vec3.ZERO : input);
    }
    @Override public void aiStep() {
        if (this.level() instanceof ServerLevel level) {
            if (level.getGameTime() >= this.nextVillageCheck) {
                this.nextVillageCheck = level.getGameTime() + 100;
                BlockPos found = GolemVillageSpawns.findHome(level, this.blockPosition());
                if (found != null) this.homeVillage = found;
                else if (this.homeVillage != null && level.hasChunkAt(this.homeVillage)
                        && !GolemVillageSpawns.isVillageArea(level, this.homeVillage)) this.homeVillage = null;
            }
            if ((!this.isAlive() || this.isNoAi()) && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter();
        }
        super.aiStep();
    }
    @Override public boolean removeWhenFarAway(double distSqr) { return false; }
    @Override protected @Nullable SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { this.playSound(SoundEvents.IRON_GOLEM_STEP, .55F, .55F); }
    @Override public void die(DamageSource source) {
        super.die(source); if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter();
    }
    @Override public void onRemoval(Entity.RemovalReason reason) {
        if (!this.level().isClientSide()) this.getMastermindBrain().clearEncounter(); super.onRemoval(reason);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter();
        this.getMastermindBrain().readCooldowns(input); this.homeVillage = null; this.nextVillageCheck = 0;
    }
    public static boolean checkGolemSpawnRules(EntityType<EndermanGolem> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) return false;
        var support = level.getBlockState(pos.below());
        if (!support.blocksMotion() || RavagerEnderman.isChargeHazard(support)) return false;
        for (int y = 0; y < Mth.ceil(type.getHeight() * EndermanGolemConfig.sizeMultiplier()); y++) {
            BlockPos cell = pos.above(y); var state = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, state, state.getFluidState(), type)) return false;
        }
        return true;
    }
    public static boolean isSpawnTime(ServerLevelAccessor level) { return level.getLevel().dimension() == Level.OVERWORLD; }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) { return isSpawnTime(level) && GolemVillageSpawns.isSpawnHabitat(level, pos); }
    public enum CombatPhase {
        IDLE, OBSERVING, PATROLLING, DEFENSIVE_WARNING, DEFENDING, INTERCEPTING, CRUSHER_WINDUP, CRUSHER_STRIKE,
        JUDGMENT_WINDUP, JUDGMENT_IMPACT, DE_ESCALATING, RECOVERY;
        public boolean bracesBody() { return this == DEFENSIVE_WARNING || this == CRUSHER_WINDUP || this == CRUSHER_STRIKE || this == JUDGMENT_WINDUP || this == JUDGMENT_IMPACT || this == RECOVERY; }
        public boolean isCommitted() { return this.bracesBody() && this != DEFENSIVE_WARNING || this == INTERCEPTING; }
    }
}
