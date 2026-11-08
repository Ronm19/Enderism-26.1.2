package com.ronm19.enderism.entity;

import com.ronm19.enderism.SculkEndermanConfig;
import com.ronm19.enderism.entity.ai.SculkEndermanBrain;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #025: native vibration hearing, a five-block sensory exception and physical slowing melee. */
public final class SculkEnderman extends AbstractEnderman implements VibrationSystem {
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(SculkEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(SculkEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> PHASE_END = SynchedEntityData.defineId(SculkEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DETECTION_UNTIL = SynchedEntityData.defineId(SculkEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(SculkEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions SCULK = new DustParticleOptions(0x20CDD1, .65F);
    private final VibrationSystem.User vibrationUser = new SculkVibrationUser();
    private final DynamicGameEventListener<VibrationSystem.Listener> vibrationListener = new DynamicGameEventListener<>(new VibrationSystem.Listener(this));
    private VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private @Nullable Vec3 teleportObservation;
    private boolean crushing;

    public SculkEnderman(EntityType<? extends SculkEnderman> type, Level level) {
        super(type, level); this.setCanPickUpLoot(false); this.setPathfindingMalus(PathType.WATER, 4); this.setPathfindingMalus(PathType.WATER_BORDER, 0);
        this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, SculkEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, SculkEndermanConfig.DEFAULT_DAMAGE).add(Attributes.MOVEMENT_SPEED, SculkEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.FOLLOW_RANGE, SculkEndermanConfig.DEFAULT_RANGE).add(Attributes.ATTACK_KNOCKBACK, .65);
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !SculkEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SculkEndermanConfig.health());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(SculkEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SculkEndermanConfig.movementSpeed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SculkEndermanConfig.followRange());
        this.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(SculkEndermanConfig.meleeKnockback());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected SculkEndermanBrain createMastermindBrain() { return new SculkEndermanBrain(this); }
    @Override public SculkEndermanBrain getMastermindBrain() { return (SculkEndermanBrain)super.getMastermindBrain(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected boolean considersEntityAsAlly(Entity entity) { return entity instanceof Warden || entity instanceof SculkEnderman || super.considersEntityAsAlly(entity); }
    @Override public boolean dampensVibrations() { return true; }
    @Override public VibrationSystem.Data getVibrationData() { return this.vibrationData; }
    @Override public VibrationSystem.User getVibrationUser() { return this.vibrationUser; }
    @Override public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> action) {
        if (this.level() instanceof ServerLevel server && this.vibrationListener != null) action.accept(this.vibrationListener, server);
    }
    @Override public void tick() {
        if (this.level() instanceof ServerLevel server) {
            if (this.isNoAi() || !this.isAlive() || this.isRemoved()) this.vibrationData = new VibrationSystem.Data();
            else VibrationSystem.Ticker.tick(server, this.vibrationData, this.vibrationUser);
        }
        super.tick();
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)0); data.define(PHASE_START, 0L); data.define(PHASE_END, 0L);
        data.define(DETECTION_UNTIL, 0L); data.define(HUNTING, false);
    }
    public CombatPhase getCombatPhase() { int value = this.entityData.get(PHASE); return value >= 0 && value < PHASES.length ? PHASES[value] : CombatPhase.LISTENING; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public int getWarmupTicksRemaining() { return (int)Math.max(0, this.entityData.get(PHASE_END) - this.level().getGameTime()); }
    public int getDetectionPulseTicksRemaining() { return (int)Math.max(0, this.entityData.get(DETECTION_UNTIL) - this.level().getGameTime()); }
    public void clearDetectionPulse() { if (!this.level().isClientSide()) this.entityData.set(DETECTION_UNTIL, 0L); }
    public boolean isActivelyHunting() { return this.entityData.get(HUNTING); }
    public float getEyeBrightness() { return this.getDetectionPulseTicksRemaining() > 0 || this.getCombatPhase() == CombatPhase.CRUSHER ? 1 : this.isActivelyHunting() ? .8F : .42F; }
    public void setActivelyHunting(boolean value) { if (!this.level().isClientSide()) this.entityData.set(HUNTING, value); }
    public void setCombatPhase(CombatPhase phase) { this.setCombatPhase(phase, 0); }
    public void setCombatPhase(CombatPhase phase, long end) {
        if (this.level().isClientSide()) return;
        if (phase != this.getCombatPhase()) { this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal()); }
        if (this.entityData.get(PHASE_END) != end) this.entityData.set(PHASE_END, end);
    }
    public boolean isLegalSculkTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.level() == this.level() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !(target instanceof EnderMan) && !(target instanceof Warden) && this.canAttack(target) && !this.isAlliedTo(target)
                && !(target instanceof Player player && player.isCreative()) && !(target instanceof ServerPlayer player && player.hasDisconnected());
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        return !this.crushing && target instanceof LivingEntity victim && this.getMastermindBrain().tryBeginCrusher(level, victim, level.getGameTime());
    }
    public boolean performSculkCrusher(ServerLevel level, LivingEntity target) {
        if (this.crushing || !this.getMastermindBrain().isCrusherImpactFrame(target) || !this.isLegalSculkTarget(target)
                || !this.isWithinMeleeAttackRange(target) || !this.getSensing().hasLineOfSight(target)) return false;
        float before = target.getHealth() + target.getAbsorptionAmount(); boolean accepted;
        this.crushing = true;
        try { accepted = super.doHurtTarget(level, target); } finally { this.crushing = false; }
        if (!accepted || target.getHealth() + target.getAbsorptionAmount() >= before || !this.isAlive() || this.isRemoved() || this.isNoAi()
                || !this.getMastermindBrain().isCrusherImpactFrame(target) || !this.isLegalSculkTarget(target)) return false;
        var current = target.getEffect(MobEffects.SLOWNESS); int amplifier = SculkEndermanConfig.slownessAmplifier(), ticks = SculkEndermanConfig.slownessTicks();
        if (current == null || current.getAmplifier() < amplifier || current.getAmplifier() == amplifier && current.getDuration() < ticks)
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, amplifier), this);
        level.sendParticles(SCULK, target.getX(), target.getY(.55), target.getZ(), 9, .25, .3, .25, .015);
        this.playSound(SoundEvents.WARDEN_ATTACK_IMPACT, .7F, .8F); return true;
    }
    public void crusherWarning(ServerLevel level) { this.playSound(SoundEvents.WARDEN_TENDRIL_CLICKS, .55F, .9F); level.sendParticles(SCULK, this.getX(), this.getY(.7), this.getZ(), 3, .2, .2, .2, .01); }
    public void detectionCue(ServerLevel level) {
        this.entityData.set(DETECTION_UNTIL, level.getGameTime() + 12);
        this.playSound(SoundEvents.SCULK_CLICKING, .65F, .7F); level.sendParticles(SCULK, this.getX(), this.getEyeY(), this.getZ(), 5, .3, .15, .3, .01);
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float before = this.getHealth() + this.getAbsorptionAmount(); boolean result = super.hurtServer(level, source, amount);
        if (result && this.isAlive() && !this.isRemoved() && !this.isNoAi() && this.getHealth() + this.getAbsorptionAmount() < before
                && source.getEntity() instanceof LivingEntity attacker && this.isLegalSculkTarget(attacker))
            this.getMastermindBrain().onDirectAttack(attacker, level.getGameTime());
        return result;
    }
    /** A remembered event point is the only legal tactical destination, never a hidden player's live position. */
    public boolean trySculkReposition(Vec3 observed, boolean left) {
        if (!(this.level() instanceof ServerLevel) || !observed.isFinite() || this.isNoAi() || this.getCombatPhase().isCommitted()
                || !this.getMastermindBrain().hasInvestigation() || this.getMastermindBrain().getLastLocatedPosition() == null
                || this.getMastermindBrain().getLastLocatedPosition().distanceToSqr(observed) > .01) return false;
        Vec3 away = this.position().subtract(observed).multiply(1, 0, 1); if (away.horizontalDistanceSqr() < .01) away = new Vec3(1, 0, 0);
        away = away.normalize(); Vec3 side = new Vec3(-away.z, 0, away.x).scale(left ? 2.5 : -2.5);
        this.teleportObservation = observed;
        try { return this.trySafeTeleport(observed.add(away.scale(4)).add(side).add(0, 2, 0), 5); }
        finally { this.teleportObservation = null; }
    }
    @Override protected int teleportAttemptCooldownTicks() { return SculkEndermanConfig.tacticalTeleportCooldownTicks(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().isCommitted() && super.teleport(); }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        if (this.teleportObservation == null) return true;
        double horizontal = destination.subtract(this.teleportObservation).horizontalDistanceSqr();
        return horizontal >= 2.5 * 2.5 && horizontal <= 8 * 8 && Math.abs(destination.y - this.teleportObservation.y) <= 3;
    }
    @Override public void aiStep() { if (!this.level().isClientSide() && (this.isNoAi() || !this.isAlive())) this.getMastermindBrain().clearEncounter(); super.aiStep(); }
    @Override public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().isCommitted()) { this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO); }
        else super.travel(input);
    }
    @Override public void die(DamageSource source) { super.die(source); if (!this.isAlive() && !this.level().isClientSide()) { this.getMastermindBrain().clearEncounter(); this.vibrationData = new VibrationSystem.Data(); } }
    @Override public void onRemoval(Entity.RemovalReason reason) { if (!this.level().isClientSide() && this.getMastermindBrain() != null) { this.getMastermindBrain().clearEncounter(); this.vibrationData = new VibrationSystem.Data(); } super.onRemoval(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter(); this.getMastermindBrain().readCooldowns(input); this.vibrationData = new VibrationSystem.Data(); }
    public enum CombatPhase {
        LISTENING, DETECTING, INVESTIGATING, PURSUING, SEARCHING, CRUSHER_WINDUP, CRUSHER, RECOVERY;
        public boolean isCommitted() { return this == CRUSHER_WINDUP || this == CRUSHER || this == RECOVERY; }
        public boolean isCasting() { return this == CRUSHER_WINDUP || this == CRUSHER; }
    }
    private final class SculkVibrationUser implements VibrationSystem.User {
        private final PositionSource source = new EntityPositionSource(SculkEnderman.this, 1.5F);
        @Override public int getListenerRadius() { return SculkEndermanConfig.vibrationRange(); }
        @Override public PositionSource getPositionSource() { return this.source; }
        @Override public boolean canTriggerAvoidVibration() { return true; }
        @Override public boolean canReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event, GameEvent.Context context) {
            if (!SculkEnderman.this.isAlive() || SculkEnderman.this.isRemoved() || SculkEnderman.this.isNoAi() || !level.getWorldBorder().isWithinBounds(pos)) return false;
            Entity actor = context.sourceEntity();
            return actor == null || actor instanceof Player player && SculkEnderman.this.isLegalSculkTarget(player)
                    || actor instanceof Projectile projectile && projectile.getOwner() instanceof Player player && SculkEnderman.this.isLegalSculkTarget(player);
        }
        @Override public void onReceiveVibration(ServerLevel level, BlockPos pos, Holder<GameEvent> event, @Nullable Entity actor, @Nullable Entity projectileOwner, float distance) {
            if (!SculkEnderman.this.isAlive() || SculkEnderman.this.isRemoved() || SculkEnderman.this.isNoAi()) return;
            Player player = actor instanceof Player direct ? direct : projectileOwner instanceof Player indirect ? indirect : null;
            if (player != null && !SculkEnderman.this.isLegalSculkTarget(player)) return;
            // The native event retains its original point during travel. Never replace it with the owner's current coordinates.
            var vibration = SculkEnderman.this.vibrationData.getCurrentVibration(); Vec3 point = vibration == null ? Vec3.atCenterOf(pos) : vibration.pos();
            SculkEnderman.this.getMastermindBrain().onVibration(point, player, level.getGameTime());
        }
    }
}
