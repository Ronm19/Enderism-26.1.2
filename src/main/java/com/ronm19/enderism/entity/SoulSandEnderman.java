package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.SoulSandEndermanConfig;
import com.ronm19.enderism.combat.SoulfireCombat;
import com.ronm19.enderism.entity.ai.SoulSandEndermanBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.Nullable;

/** #030: grounded soul-fire hunter with a strictly terrain-owned soul-sand movement advantage. */
public final class SoulSandEnderman extends AbstractEnderman {
    public static final Identifier SOUL_SAND_SPEED_ID = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "soul_sand_mastery");
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(SoulSandEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(SoulSandEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> PHASE_END = SynchedEntityData.defineId(SoulSandEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DOMINION_UNTIL = SynchedEntityData.defineId(SoulSandEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> MASTERY = SynchedEntityData.defineId(SoulSandEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(SoulSandEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private final SoulfireCombat soulfire = new SoulfireCombat(this);
    private long nextTerrainUpdateAt;
    private boolean striking;
    private @Nullable LivingEntity strikingTarget;
    private float nativeStrikeHealthDamage;
    private @Nullable Vec3 teleportAnchor;

    public SoulSandEnderman(EntityType<? extends SoulSandEnderman> type, Level level) {
        super(type, level); this.setCanPickUpLoot(false); this.setPathfindingMalus(PathType.WATER, 2);
        this.setPathfindingMalus(PathType.LAVA, -1); this.setPathfindingMalus(PathType.FIRE, -1); this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 8);
        this.setPathfindingMalus(PathType.DAMAGE_CAUTIOUS, -1); this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, SoulSandEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, SoulSandEndermanConfig.DEFAULT_DAMAGE).add(Attributes.MOVEMENT_SPEED, SoulSandEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.FOLLOW_RANGE, SoulSandEndermanConfig.DEFAULT_RANGE).add(Attributes.ATTACK_KNOCKBACK, .25);
    }
    public static void registerEvents(IEventBus bus) { bus.addListener(SoulSandEnderman::acceptedMeleeDamage); }
    private static void acceptedMeleeDamage(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof SoulSandEnderman source && event.getSource().getDirectEntity() == source
                && event.getSource().is(DamageTypes.MOB_ATTACK) && source.striking && source.strikingTarget == event.getEntity()
                && source.getMastermindBrain().isStrikeImpactFrame(event.getEntity()) && Float.isFinite(event.getHealthDamage()) && event.getHealthDamage() > 0)
            source.nativeStrikeHealthDamage = Math.max(source.nativeStrikeHealthDamage, event.getHealthDamage());
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !SoulSandEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SoulSandEndermanConfig.health()); this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(SoulSandEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SoulSandEndermanConfig.movementSpeed()); this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SoulSandEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected SoulSandEndermanBrain createMastermindBrain() { return new SoulSandEndermanBrain(this); }
    @Override public SoulSandEndermanBrain getMastermindBrain() { return (SoulSandEndermanBrain)super.getMastermindBrain(); }
    public SoulfireCombat getSoulfireCombat() { return this.soulfire; }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)0); data.define(PHASE_START, 0L); data.define(PHASE_END, 0L);
        data.define(DOMINION_UNTIL, 0L); data.define(MASTERY, false); data.define(HUNTING, false);
    }
    public CombatPhase getCombatPhase() { int value = this.entityData.get(PHASE); return value >= 0 && value < PHASES.length ? PHASES[value] : CombatPhase.IDLE; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public int getWarmupTicksRemaining() { return (int)Math.max(0, this.entityData.get(PHASE_END) - this.level().getGameTime()); }
    public boolean isActivelyHunting() { return this.entityData.get(HUNTING); }
    public boolean isDominionActive() { return this.level().getGameTime() < this.entityData.get(DOMINION_UNTIL); }
    public long getDominionEndsAt() { return this.entityData.get(DOMINION_UNTIL); }
    public boolean isSoulSandMasteryActive() { return this.entityData.get(MASTERY); }
    public float getEyeBrightness() { return this.getCombatPhase().isCasting() || this.isDominionActive() ? 1 : this.isActivelyHunting() ? .85F : .65F; }
    public void setActivelyHunting(boolean value) { if (!this.level().isClientSide()) this.entityData.set(HUNTING, value); }
    public void setCombatPhase(CombatPhase phase) { this.setCombatPhase(phase, 0); }
    public void setCombatPhase(CombatPhase phase, long end) {
        if (this.level().isClientSide()) return;
        if (phase != this.getCombatPhase()) { this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal()); }
        if (this.entityData.get(PHASE_END) != end) this.entityData.set(PHASE_END, end);
    }
    public boolean isLegalSoulTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.level() == this.level() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !(target instanceof EnderMan) && this.canAttack(target) && !this.isAlliedTo(target)
                && !(target instanceof Player player && player.isCreative()) && !(target instanceof ServerPlayer player && player.hasDisconnected());
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) { return !this.striking && target instanceof LivingEntity victim && this.getMastermindBrain().tryBeginStrike(level, victim, level.getGameTime()); }
    public boolean performSoulfireStrike(ServerLevel level, LivingEntity target) {
        if (this.striking || this.level() != level || !this.getMastermindBrain().isStrikeImpactFrame(target) || !this.isLegalSoulTarget(target)
                || !this.isWithinMeleeAttackRange(target) || !this.getSensing().hasLineOfSight(target)) return false;
        float before = target.getHealth(); boolean accepted;
        this.striking = true; this.strikingTarget = target; this.nativeStrikeHealthDamage = 0;
        try { accepted = super.doHurtTarget(level, target); } finally { this.striking = false; this.strikingTarget = null; }
        if (!accepted || this.nativeStrikeHealthDamage <= 0 || target.getHealth() >= before || !this.isAlive() || this.isRemoved() || this.isNoAi()
                || !this.getMastermindBrain().isStrikeImpactFrame(target) || !this.isLegalSoulTarget(target)) return false;
        this.soulfire.ignite(level, target, level.getGameTime());
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY(.55), target.getZ(), 7, .18, .3, .18, .02);
        this.playSound(SoundEvents.FIRECHARGE_USE, .55F, .75F); return true;
    }
    /** Soul sand's collision surface is fourteen sixteenths high, so blockPosition().below() is insufficient. */
    public boolean isOnMasteredSoulSand() {
        if (!this.onGround() || this.isInWater() || this.isInLava() || !Double.isFinite(this.getY())) return false;
        BlockPos below = this.getBlockPosBelowThatAffectsMyMovement();
        if (this.level() instanceof ServerLevel level && level.getChunkSource().getChunkNow(below.getX() >> 4, below.getZ() >> 4) == null) return false;
        return this.level().getBlockState(below).is(Blocks.SOUL_SAND) && Math.abs(this.getY() - (below.getY() + .875)) <= .13;
    }
    @Override protected float getBlockSpeedFactor() {
        // Cancel only the native soul-sand .4 damping for this supported species. Ordinary fire, lava,
        // other terrain and other mobs retain their normal mechanics; the finite attribute provides the gain.
        return this.isOnMasteredSoulSand() ? 1 : super.getBlockSpeedFactor();
    }
    private void reconcileMastery(long now) {
        if (this.level().isClientSide()) return;
        if (!this.isAlive() || this.isRemoved() || this.isNoAi() || !this.isOnMasteredSoulSand()) { this.clearMastery(); return; }
        if (now < this.nextTerrainUpdateAt) return; this.nextTerrainUpdateAt = now + SoulSandEndermanConfig.terrainUpdateTicks();
        var attribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
        var modifier = new AttributeModifier(SOUL_SAND_SPEED_ID, SoulSandEndermanConfig.soulSandMovementMultiplier() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (!modifier.equals(attribute.getModifier(SOUL_SAND_SPEED_ID))) { attribute.removeModifier(SOUL_SAND_SPEED_ID); attribute.addTransientModifier(modifier); }
        this.entityData.set(MASTERY, true);
    }
    public void clearMastery() { if (!this.level().isClientSide()) { this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SOUL_SAND_SPEED_ID); this.entityData.set(MASTERY, false); } }
    public void clearSoulfire() { if (this.soulfire != null) this.soulfire.clear(); if (!this.level().isClientSide()) this.entityData.set(DOMINION_UNTIL, 0L); }
    public void abilityWarning(ServerLevel level, CombatPhase phase, @Nullable Vec3 anchor) {
        Vec3 point = anchor == null ? this.position() : anchor;
        this.playSound(SoundEvents.SOUL_ESCAPE.value(), phase == CombatPhase.DOMINION_WINDUP ? .8F : .5F, phase == CombatPhase.DOMINION_WINDUP ? .65F : .9F);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y + .15, point.z, phase == CombatPhase.DOMINION_WINDUP ? 15 : 8, .7, .12, .7, .015);
    }
    public boolean trySoulReposition(Vec3 requested, Vec3 observedAnchor) {
        if (!requested.isFinite() || !observedAnchor.isFinite() || this.getCombatPhase().isCommitted() || !this.getMastermindBrain().isTacticalAnchorValid(observedAnchor)
                || this.distanceToSqr(requested) > SoulSandEndermanConfig.teleportRange() * SoulSandEndermanConfig.teleportRange()) return false;
        this.teleportAnchor = observedAnchor;
        try { return this.trySafeTeleport(requested, 4); } finally { this.teleportAnchor = null; }
    }
    @Override protected int teleportAttemptCooldownTicks() { return SoulSandEndermanConfig.tacticalTeleportCooldownTicks(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().isCommitted() && super.teleport(); }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        if (this.teleportAnchor == null) return true;
        double distance = destination.subtract(this.teleportAnchor).horizontalDistanceSqr();
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && this.getMastermindBrain().isTacticalAnchorValid(this.teleportAnchor)
                && distance >= 6.25 && distance <= 64 && Math.abs(destination.y - this.teleportAnchor.y) <= 3
                && this.distanceToSqr(destination) <= SoulSandEndermanConfig.teleportRange() * SoulSandEndermanConfig.teleportRange();
    }
    @Override public void aiStep() {
        if (!this.level().isClientSide()) {
            if (this.isNoAi() || !this.isAlive()) { this.getMastermindBrain().clearEncounter(); this.clearMastery(); }
            else { long now = this.level().getGameTime(); this.reconcileMastery(now); this.soulfire.tick((ServerLevel)this.level(), now); this.entityData.set(DOMINION_UNTIL, this.soulfire.getDominionEndsAt()); }
        }
        super.aiStep();
    }
    @Override public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().isCommitted()) { this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO); } else super.travel(input);
    }
    @Override public void die(DamageSource source) { super.die(source); if (!this.isAlive() && !this.level().isClientSide()) { this.getMastermindBrain().clearEncounter(); this.clearMastery(); } }
    @Override public void onRemoval(Entity.RemovalReason reason) { if (!this.level().isClientSide()) { this.getMastermindBrain().clearEncounter(); this.clearMastery(); } super.onRemoval(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.clearMastery(); this.getMastermindBrain().clearEncounter(); this.getMastermindBrain().readCooldowns(input); }
    public enum CombatPhase {
        IDLE, PURSUING, SEARCHING, STRIKE_WINDUP, STRIKE, ERUPTION_WINDUP, ERUPTION, DOMINION_WINDUP, DOMINION, RECOVERY;
        public boolean isCommitted() { return this == STRIKE_WINDUP || this == STRIKE || this == ERUPTION_WINDUP || this == ERUPTION || this == DOMINION_WINDUP || this == RECOVERY; }
        public boolean isCasting() { return this == STRIKE_WINDUP || this == STRIKE || this == ERUPTION_WINDUP || this == ERUPTION || this == DOMINION_WINDUP; }
    }
}
