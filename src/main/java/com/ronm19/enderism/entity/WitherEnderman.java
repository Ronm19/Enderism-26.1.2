package com.ronm19.enderism.entity;

import com.ronm19.enderism.WitherEndermanConfig;
import com.ronm19.enderism.combat.WitherRoses;
import com.ronm19.enderism.entity.ai.WitherEndermanBrain;
import com.ronm19.enderism.entity.projectile.WitherBarrageSkull;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.Nullable;

/** #028: a grounded Soul Sand Valley hunter with three server-authoritative Wither attacks. */
public final class WitherEnderman extends AbstractEnderman {
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(WitherEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(WitherEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> PHASE_END = SynchedEntityData.defineId(WitherEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(WitherEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions DECAY = new DustParticleOptions(0x5C416D, .7F);
    private final Map<UUID, WeakReference<WitherBarrageSkull>> ownedSkulls = new HashMap<>();
    private @Nullable Vec3 teleportObservation;
    private boolean striking, launching, placing;
    private @Nullable LivingEntity strikingTarget;
    private float nativeStrikeHealthDamage;

    public WitherEnderman(EntityType<? extends WitherEnderman> type, Level level) {
        super(type, level); this.setCanPickUpLoot(false);
        this.setPathfindingMalus(PathType.DAMAGE_CAUTIOUS, -1);
        this.setPathfindingMalus(PathType.WATER, 4); this.setPathfindingMalus(PathType.WATER_BORDER, 0);
        this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, WitherEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, WitherEndermanConfig.DEFAULT_DAMAGE).add(Attributes.MOVEMENT_SPEED, WitherEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.FOLLOW_RANGE, WitherEndermanConfig.DEFAULT_RANGE).add(Attributes.ARMOR, WitherEndermanConfig.DEFAULT_ARMOR).add(Attributes.ATTACK_KNOCKBACK, .4);
    }
    public static void registerEvents(IEventBus bus) { bus.addListener(WitherEnderman::acceptedMeleeDamage); }
    private static void acceptedMeleeDamage(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof WitherEnderman source && event.getSource().getDirectEntity() == source
                && event.getSource().is(DamageTypes.MOB_ATTACK) && source.striking && source.strikingTarget == event.getEntity()
                && source.getMastermindBrain().isGraspImpactFrame(event.getEntity()) && Float.isFinite(event.getHealthDamage()) && event.getHealthDamage() > 0)
            source.nativeStrikeHealthDamage = Math.max(source.nativeStrikeHealthDamage, event.getHealthDamage());
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !WitherEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(WitherEndermanConfig.health());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(WitherEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(WitherEndermanConfig.movementSpeed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(WitherEndermanConfig.followRange());
        this.getAttribute(Attributes.ARMOR).setBaseValue(WitherEndermanConfig.armor());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected WitherEndermanBrain createMastermindBrain() { return new WitherEndermanBrain(this); }
    @Override public WitherEndermanBrain getMastermindBrain() { return (WitherEndermanBrain)super.getMastermindBrain(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)0); data.define(PHASE_START, 0L); data.define(PHASE_END, 0L); data.define(HUNTING, false);
    }
    public CombatPhase getCombatPhase() { int value = this.entityData.get(PHASE); return value >= 0 && value < PHASES.length ? PHASES[value] : CombatPhase.IDLE; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public int getWarmupTicksRemaining() { return (int)Math.max(0, this.entityData.get(PHASE_END) - this.level().getGameTime()); }
    public boolean isActivelyHunting() { return this.entityData.get(HUNTING); }
    public float getEyeBrightness() { return this.getCombatPhase().isCasting() ? 1 : this.isActivelyHunting() ? .8F : .5F; }
    public void setActivelyHunting(boolean value) { if (!this.level().isClientSide()) this.entityData.set(HUNTING, value); }
    public void setCombatPhase(CombatPhase phase) { this.setCombatPhase(phase, 0); }
    public void setCombatPhase(CombatPhase phase, long end) {
        if (this.level().isClientSide()) return;
        if (phase != this.getCombatPhase()) { this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal()); }
        if (this.entityData.get(PHASE_END) != end) this.entityData.set(PHASE_END, end);
    }
    public boolean isLegalWitherTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.level() == this.level() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !(target instanceof EnderMan) && this.canAttack(target) && !this.isAlliedTo(target)
                && !(target instanceof Player player && player.isCreative()) && !(target instanceof ServerPlayer player && player.hasDisconnected());
    }
    public boolean canCastOn(LivingEntity target, double range) {
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && this.isLegalWitherTarget(target)
                && this.distanceToSqr(target) <= range * range && this.getSensing().hasLineOfSight(target);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) { return !this.striking && target instanceof LivingEntity victim && this.getMastermindBrain().tryBeginGrasp(level, victim, level.getGameTime()); }
    public boolean performWitheringGrasp(ServerLevel level, LivingEntity target) {
        if (this.striking || !this.getMastermindBrain().isGraspImpactFrame(target) || !this.canCastOn(target, 8) || !this.isWithinMeleeAttackRange(target)) return false;
        float before = target.getHealth(); boolean accepted;
        this.striking = true; this.strikingTarget = target; this.nativeStrikeHealthDamage = 0;
        try { accepted = super.doHurtTarget(level, target); } finally { this.striking = false; this.strikingTarget = null; }
        if (!accepted || this.nativeStrikeHealthDamage <= 0 || target.getHealth() >= before || !this.isAlive() || this.isRemoved() || this.isNoAi()
                || !this.getMastermindBrain().isGraspImpactFrame(target) || !this.isLegalWitherTarget(target)) return false;
        this.applyWitherEffect(target, WitherEndermanConfig.graspWitherTicks());
        level.sendParticles(DECAY, target.getX(), target.getY(.55), target.getZ(), 8, .22, .28, .22, .02);
        this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, .75F, .7F); return true;
    }
    public boolean applyWitherEffect(LivingEntity target, int duration) {
        if (!this.isLegalWitherTarget(target) || !this.isAlive() || this.isRemoved() || this.isNoAi()) return false;
        int amplifier = WitherEndermanConfig.witherAmplifier(); duration = Math.max(1, Math.min(240, duration));
        var current = target.getEffect(MobEffects.WITHER);
        if (current != null && (current.getAmplifier() > amplifier || current.getAmplifier() == amplifier && current.getDuration() >= duration)) return false;
        return target.addEffect(new MobEffectInstance(MobEffects.WITHER, duration, amplifier), this);
    }
    public Vec3 getMuzzlePosition() { return this.position().add(0, 2.35, 0); }
    public Vec3 getRangedAimPoint(LivingEntity target) { return target.position().add(0, target.getBbHeight() * .55, 0); }
    public boolean hasClearProjectilePath(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level) || !this.isLegalWitherTarget(target)) return false;
        Vec3 from = this.getMuzzlePosition(), to = this.getRangedAimPoint(target);
        BlockPos low = BlockPos.containing(Math.min(from.x, to.x) - 1, Math.min(from.y, to.y) - 1, Math.min(from.z, to.z) - 1);
        BlockPos high = BlockPos.containing(Math.max(from.x, to.x) + 1, Math.max(from.y, to.y) + 1, Math.max(from.z, to.z) + 1);
        if (!readyChunks(level, low, high) || level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) return false;
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(.3), ally -> ally != this && (ally instanceof EnderMan || this.isAlliedTo(ally)))
                .stream().noneMatch(ally -> ally.getBoundingBox().inflate(.15).clip(from, to).isPresent());
    }
    public boolean launchSkull(ServerLevel level, LivingEntity target) {
        if (this.launching) return false;
        this.launching = true;
        try {
            this.pruneOwnedSkulls(level.getGameTime());
            if (this.level() != level || !this.canCastOn(target, WitherEndermanConfig.projectileRange()) || !this.hasClearProjectilePath(target)
                    || this.ownedSkulls.size() >= WitherEndermanConfig.barrageProjectileCount() || !this.getMastermindBrain().isBarrageReleaseFrame(target)) return false;
            WitherBarrageSkull shot = WitherBarrageSkull.create(level, this, target, this.getMuzzlePosition(), this.getRangedAimPoint(target));
            this.ownedSkulls.put(shot.getUUID(), new WeakReference<>(shot));
            if (!level.addFreshEntity(shot)) { this.ownedSkulls.remove(shot.getUUID()); shot.discard(); return false; }
            this.playSound(SoundEvents.WITHER_SHOOT, .6F, .95F); return true;
        } finally { this.launching = false; }
    }
    public int getOwnedSkullCount() { this.pruneOwnedSkulls(this.level().getGameTime()); return this.ownedSkulls.size(); }
    public boolean isSkullValid(WitherBarrageSkull shot) {
        var reference = this.ownedSkulls.get(shot.getUUID());
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && shot.level() == this.level() && reference != null && reference.get() == shot;
    }
    public void onSkullRemoved(WitherBarrageSkull shot) { var reference = this.ownedSkulls.get(shot.getUUID()); if (reference != null && reference.get() == shot) this.ownedSkulls.remove(shot.getUUID()); }
    public void clearOwnedSkulls() {
        var shots = List.copyOf(this.ownedSkulls.values()); this.ownedSkulls.clear();
        for (var reference : shots) { var shot = reference.get(); if (shot != null && !shot.isRemoved()) shot.discard(); }
    }
    private void pruneOwnedSkulls(long now) {
        for (var reference : List.copyOf(this.ownedSkulls.values())) { var shot = reference.get(); if (shot != null && !shot.isRemoved() && shot.isExpired(now)) shot.discard(); }
        this.ownedSkulls.entrySet().removeIf(entry -> { var shot = entry.getValue().get(); return shot == null || shot.isRemoved(); });
    }
    public int placeDecayRoses(ServerLevel level, LivingEntity target, Vec3 center) {
        if (this.placing || this.level() != level || !this.canCastOn(target, WitherEndermanConfig.roseCastingRange()) || !this.getMastermindBrain().isRoseReleaseFrame(target)) return 0;
        this.placing = true;
        try { return WitherRoses.place(level, this, target, center); } finally { this.placing = false; }
    }
    public void warningCue(ServerLevel level, boolean roses, @Nullable Vec3 center) {
        Vec3 point = center == null ? this.getMuzzlePosition() : center.add(0, .12, 0);
        level.sendParticles(DECAY, point.x, point.y, point.z, roses ? 14 : 6, roses ? 1.5 : .25, roses ? .04 : .3, roses ? 1.5 : .25, .015);
        this.playSound(SoundEvents.WITHER_AMBIENT, roses ? .65F : .4F, roses ? .65F : .9F);
    }
    public void gatheringCue(ServerLevel level, @Nullable Vec3 center) {
        Vec3 point = center == null ? this.getMuzzlePosition() : center.add(0, .1, 0);
        level.sendParticles(DECAY, point.x, point.y, point.z, 4, center == null ? .15 : 1.25, .06, center == null ? .15 : 1.25, .01);
    }
    public boolean tryWitherReposition(Vec3 observed, boolean left) {
        if (!(this.level() instanceof ServerLevel level) || !observed.isFinite() || this.isNoAi() || this.getCombatPhase().isCommitted()
                || !this.getMastermindBrain().isTargetLocalized() || this.getMastermindBrain().getLastLocatedPosition() == null
                || this.getMastermindBrain().getLastLocatedPosition().distanceToSqr(observed) > .01) return false;
        Vec3 radial = this.position().subtract(observed).multiply(1, 0, 1).normalize(); if (radial.lengthSqr() < .01) radial = new Vec3(1, 0, 0);
        Vec3 side = new Vec3(-radial.z, 0, radial.x).scale(left ? 4 : -4); this.teleportObservation = observed;
        try {
            for (Vec3 candidate : new Vec3[] {observed.add(side), observed.subtract(side), observed.add(radial.scale(5))}) {
                if (this.distanceToSqr(candidate) > 24 * 24 || this.distanceToSqr(candidate) < 4) continue;
                Vec3 destination = this.findSafeTeleportDestination(candidate.add(0, 2, 0), 4);
                if (destination != null && this.trySafeTeleport(destination, 0)) { this.resetFallDistance(); level.sendParticles(DECAY, this.getX(), this.getY(.45), this.getZ(), 7, .3, .6, .3, .02); return true; }
            }
            return false;
        } finally { this.teleportObservation = null; }
    }
    @Override protected int teleportAttemptCooldownTicks() { return WitherEndermanConfig.tacticalTeleportCooldownTicks(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().isCommitted() && super.teleport(); }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        if (level.getBlockState(BlockPos.containing(destination)).is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)) return false;
        if (this.teleportObservation == null) return true;
        double horizontal = destination.subtract(this.teleportObservation).horizontalDistanceSqr(); return horizontal >= 2.5 * 2.5 && horizontal <= 8 * 8 && Math.abs(destination.y - this.teleportObservation.y) <= 3;
    }
    @Override public void aiStep() {
        if (!this.level().isClientSide()) { if (this.isNoAi() || !this.isAlive()) this.getMastermindBrain().clearEncounter(); else if (this.tickCount % 10 == 0) this.pruneOwnedSkulls(this.level().getGameTime()); }
        super.aiStep();
    }
    @Override public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().bracesCaster()) { this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO); } else super.travel(input);
    }
    @Override public void die(DamageSource source) { super.die(source); if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter(); }
    @Override public void onRemoval(Entity.RemovalReason reason) { if (!this.level().isClientSide() && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter(); super.onRemoval(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter(); this.getMastermindBrain().readCooldowns(input); }
    public static boolean readyChunks(ServerLevel level, BlockPos low, BlockPos high) {
        for (int x = low.getX() >> 4; x <= high.getX() >> 4; x++) for (int z = low.getZ() >> 4; z <= high.getZ() >> 4; z++) if (level.getChunkSource().getChunkNow(x, z) == null) return false; return true;
    }
    public enum CombatPhase {
        IDLE, PURSUING, SEARCHING, GRASP_WINDUP, GRASP, BARRAGE_WINDUP, BARRAGE, ROSE_WINDUP, ROSE, RECOVERY;
        public boolean isCasting() { return this == GRASP_WINDUP || this == GRASP || this == BARRAGE_WINDUP || this == BARRAGE || this == ROSE_WINDUP || this == ROSE; }
        public boolean isCommitted() { return this.isCasting() || this == RECOVERY; }
        public boolean bracesCaster() { return this.isCommitted(); }
    }
}
