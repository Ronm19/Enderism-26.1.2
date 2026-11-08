package com.ronm19.enderism.entity;

import com.ronm19.enderism.HuskEndermanConfig;
import com.ronm19.enderism.entity.ai.HuskEndermanBrain;
import java.util.Comparator;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #026: a sunlight-resilient attrition hunter using three separate native harmful effects. */
public final class HuskEnderman extends AbstractEnderman {
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(HuskEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(HuskEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> PHASE_END = SynchedEntityData.defineId(HuskEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(HuskEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions DUST = new DustParticleOptions(0xC9A36A, .7F);
    private @Nullable Vec3 teleportObservation;
    private boolean striking;

    public HuskEnderman(EntityType<? extends HuskEnderman> type, Level level) {
        super(type, level); this.setCanPickUpLoot(false); this.setPathfindingMalus(PathType.WATER, 4); this.setPathfindingMalus(PathType.WATER_BORDER, 0);
        this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, HuskEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, HuskEndermanConfig.DEFAULT_DAMAGE).add(Attributes.MOVEMENT_SPEED, HuskEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.FOLLOW_RANGE, HuskEndermanConfig.DEFAULT_RANGE).add(Attributes.ATTACK_KNOCKBACK, .4);
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !HuskEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HuskEndermanConfig.health());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(HuskEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(HuskEndermanConfig.movementSpeed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(HuskEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected HuskEndermanBrain createMastermindBrain() { return new HuskEndermanBrain(this); }
    @Override public HuskEndermanBrain getMastermindBrain() { return (HuskEndermanBrain)super.getMastermindBrain(); }
    // No native idle daylight escape: Husk patrols and fights normally after sunrise.
    // Mob's sunlight ignition is gated by BURN_IN_DAYLIGHT, which deliberately excludes this species.
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected AABB getAttackBoundingBox(double horizontalExpansion) {
        double extra = this.getActiveItem().get(DataComponents.ATTACK_RANGE) == null ? HuskEndermanConfig.meleeReachBonus() : 0;
        return super.getAttackBoundingBox(horizontalExpansion + extra);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)0); data.define(PHASE_START, 0L); data.define(PHASE_END, 0L); data.define(HUNTING, false);
    }
    public CombatPhase getCombatPhase() { int value = this.entityData.get(PHASE); return value >= 0 && value < PHASES.length ? PHASES[value] : CombatPhase.IDLE; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public int getWarmupTicksRemaining() { return (int)Math.max(0, this.entityData.get(PHASE_END) - this.level().getGameTime()); }
    public boolean isActivelyHunting() { return this.entityData.get(HUNTING); }
    public float getEyeBrightness() { return this.getCombatPhase().isCasting() ? 1 : this.isActivelyHunting() ? .85F : .55F; }
    public void setActivelyHunting(boolean value) { if (!this.level().isClientSide()) this.entityData.set(HUNTING, value); }
    public void setCombatPhase(CombatPhase phase) { this.setCombatPhase(phase, 0); }
    public void setCombatPhase(CombatPhase phase, long end) {
        if (this.level().isClientSide()) return;
        if (phase != this.getCombatPhase()) { this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal()); }
        if (this.entityData.get(PHASE_END) != end) this.entityData.set(PHASE_END, end);
    }
    public boolean isLegalHuskTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.level() == this.level() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !(target instanceof EnderMan) && this.canAttack(target) && !this.isAlliedTo(target)
                && !(target instanceof Player player && player.isCreative()) && !(target instanceof ServerPlayer player && player.hasDisconnected());
    }
    public boolean canCastOn(LivingEntity target, double range) {
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && this.isLegalHuskTarget(target)
                && this.distanceToSqr(target) <= range * range && this.getSensing().hasLineOfSight(target);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) { return !this.striking && target instanceof LivingEntity victim && this.getMastermindBrain().tryBeginStrike(level, victim, level.getGameTime()); }
    public boolean performDesiccatingStrike(ServerLevel level, LivingEntity target) {
        if (this.striking || !this.getMastermindBrain().isStrikeImpactFrame(target) || !this.canCastOn(target, 8)
                || !this.isWithinMeleeAttackRange(target)) return false;
        float before = target.getHealth() + target.getAbsorptionAmount(); boolean accepted;
        this.striking = true;
        try { accepted = super.doHurtTarget(level, target); } finally { this.striking = false; }
        if (!accepted || target.getHealth() + target.getAbsorptionAmount() >= before || !this.isAlive() || this.isRemoved() || this.isNoAi()
                || !this.getMastermindBrain().isStrikeImpactFrame(target)) return false;
        this.applyFiniteEffect(target, MobEffects.WEAKNESS, HuskEndermanConfig.weaknessTicks(), HuskEndermanConfig.weaknessAmplifier());
        level.sendParticles(DUST, target.getX(), target.getY(.55), target.getZ(), 8, .22, .28, .22, .02);
        this.playSound(SoundEvents.SAND_BREAK, .7F, .65F); return true;
    }
    public boolean applyStarvationCurse(ServerLevel level, Player target) {
        if (this.level() != level || !this.getMastermindBrain().isCurseReleaseFrame(target) || !this.canCastOn(target, HuskEndermanConfig.curseRange())) return false;
        this.applyFiniteEffect(target, MobEffects.HUNGER, HuskEndermanConfig.hungerTicks(), HuskEndermanConfig.hungerAmplifier());
        level.sendParticles(DUST, target.getX(), target.getY(.6), target.getZ(), 10, .3, .45, .3, .015);
        this.playSound(SoundEvents.HUSK_AMBIENT, .8F, .65F); return true;
    }
    public int releaseDesertDelirium(ServerLevel level) {
        if (this.level() != level || !this.isAlive() || this.isNoAi() || !this.getMastermindBrain().isDeliriumReleaseFrame()) return 0;
        double radius = HuskEndermanConfig.deliriumRadius();
        var targets = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(radius), target -> this.canCastOn(target, radius));
        targets.sort(Comparator.comparingDouble((Player target) -> this.distanceToSqr(target)).thenComparing(Entity::getUUID));
        int affected = 0;
        for (Player target : targets) {
            if (!this.isAlive() || this.isRemoved() || this.isNoAi() || !this.getMastermindBrain().isDeliriumReleaseFrame()) break;
            if (affected >= HuskEndermanConfig.maxDeliriumTargets()) break;
            if (!this.canCastOn(target, radius)) continue;
            this.applyFiniteEffect(target, MobEffects.NAUSEA, HuskEndermanConfig.nauseaTicks(), 0); affected++;
        }
        level.sendParticles(DUST, this.getX(), this.getY(.45), this.getZ(), 24, radius * .35, .35, radius * .35, .025);
        this.playSound(SoundEvents.HUSK_AMBIENT, .9F, .5F); return affected;
    }
    private void applyFiniteEffect(LivingEntity target, Holder<MobEffect> effect, int duration, int amplifier) {
        if (!this.isLegalHuskTarget(target)) return;
        var current = target.getEffect(effect);
        if (current != null && (current.getAmplifier() > amplifier || current.getAmplifier() == amplifier && current.getDuration() >= duration)) return;
        target.addEffect(new MobEffectInstance(effect, duration, amplifier), this);
    }
    public void abilityWarning(ServerLevel level, CombatPhase phase) {
        int count = phase == CombatPhase.STRIKE_WINDUP ? 3 : phase == CombatPhase.CURSE_WINDUP ? 7 : 12;
        level.sendParticles(DUST, this.getX(), this.getY(.65), this.getZ(), count, .3, .35, .3, .01);
        this.playSound(phase == CombatPhase.STRIKE_WINDUP ? SoundEvents.SAND_STEP : SoundEvents.HUSK_AMBIENT, .6F, phase == CombatPhase.DELIRIUM_WINDUP ? .55F : .8F);
    }
    public boolean tryHuskReposition(Vec3 observed, boolean left) {
        if (!(this.level() instanceof ServerLevel) || !observed.isFinite() || this.isNoAi() || this.getCombatPhase().isCommitted()
                || !this.getMastermindBrain().isTargetLocalized() || this.getMastermindBrain().getLastLocatedPosition() == null
                || this.getMastermindBrain().getLastLocatedPosition().distanceToSqr(observed) > .01) return false;
        Vec3 away = this.position().subtract(observed).multiply(1, 0, 1); if (away.horizontalDistanceSqr() < .01) away = new Vec3(1, 0, 0);
        away = away.normalize(); this.teleportObservation = observed;
        try { return this.trySafeTeleport(observed.add(away.scale(4.5)).add(new Vec3(-away.z, 0, away.x).scale(left ? 3 : -3)).add(0, 2, 0), 5); }
        finally { this.teleportObservation = null; }
    }
    @Override protected int teleportAttemptCooldownTicks() { return HuskEndermanConfig.tacticalTeleportCooldownTicks(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().isCommitted() && super.teleport(); }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        if (this.teleportObservation == null) return true;
        double distance = destination.subtract(this.teleportObservation).horizontalDistanceSqr();
        return distance >= 2.5 * 2.5 && distance <= 8 * 8 && Math.abs(destination.y - this.teleportObservation.y) <= 3;
    }
    @Override public void aiStep() { if (!this.level().isClientSide() && (this.isNoAi() || !this.isAlive())) this.getMastermindBrain().clearEncounter(); super.aiStep(); }
    @Override public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().isCommitted()) { this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO); }
        else super.travel(input);
    }
    @Override public void die(DamageSource source) { super.die(source); if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter(); }
    @Override public void onRemoval(Entity.RemovalReason reason) { if (!this.level().isClientSide() && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter(); super.onRemoval(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter(); this.getMastermindBrain().readCooldowns(input); }
    public enum CombatPhase {
        IDLE, PURSUING, SEARCHING, STRIKE_WINDUP, STRIKE, CURSE_WINDUP, CURSE, DELIRIUM_WINDUP, DELIRIUM, RECOVERY;
        public boolean isCommitted() { return this != IDLE && this != PURSUING && this != SEARCHING; }
        public boolean isCasting() { return this == STRIKE_WINDUP || this == STRIKE || this == CURSE_WINDUP || this == CURSE || this == DELIRIUM_WINDUP || this == DELIRIUM; }
    }
}
