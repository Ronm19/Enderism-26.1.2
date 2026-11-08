package com.ronm19.enderism.entity;

import com.ronm19.enderism.BloodEndermanConfig;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.effect.Bloodlust;
import com.ronm19.enderism.entity.ai.BloodEndermanBrain;
import com.ronm19.enderism.entity.projectile.BloodLance;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #016: grounded hybrid pressure and native kill-attributed Regeneration. */
public final class BloodEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_blood_enderman"));
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(BloodEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(BloodEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> BLOODLUST_AURA_END = SynchedEntityData.defineId(BloodEnderman.class, EntityDataSerializers.LONG);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(0xD4203A, 1.0F);
    private final Map<UUID, WeakReference<BloodLance>> ownedLances = new HashMap<>();
    private @Nullable Vec3 teleportObservation;
    private boolean striking;

    public BloodEnderman(EntityType<? extends BloodEnderman> type, Level level) {
        super(type, level);
        this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes()
                .add(Attributes.MAX_HEALTH, BloodEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BloodEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, BloodEndermanConfig.DEFAULT_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, BloodEndermanConfig.DEFAULT_RANGE);
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !BloodEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BloodEndermanConfig.health());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(BloodEndermanConfig.speed());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BloodEndermanConfig.damage());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(BloodEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected BloodEndermanBrain createMastermindBrain() { return new BloodEndermanBrain(this); }
    @Override public BloodEndermanBrain getMastermindBrain() { return (BloodEndermanBrain)super.getMastermindBrain(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(PHASE, (byte)CombatPhase.IDLE.ordinal()); data.define(PHASE_START, 0L); data.define(BLOODLUST_AURA_END, 0L);
    }
    public CombatPhase getCombatPhase() { return PHASES[this.entityData.get(PHASE)]; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public long getBloodlustAuraEnd() { return this.entityData.get(BLOODLUST_AURA_END); }
    public boolean isBloodlustActive() { return this.isAlive() && this.hasEffect(MobEffects.REGENERATION); }
    public float getEyeBrightness() {
        if (this.level().getGameTime() < this.getBloodlustAuraEnd()) return 1.0F;
        if (this.isBloodlustActive()) return .96F;
        return switch (this.getCombatPhase()) {
            case IDLE -> .62F;
            case SEARCHING, RECOVERY -> .76F;
            case PURSUIT -> .88F;
            default -> 1.0F;
        };
    }
    public void setCombatPhase(CombatPhase phase) {
        if (!this.level().isClientSide() && this.getCombatPhase() != phase) {
            this.entityData.set(PHASE_START, this.level().getGameTime());
            this.entityData.set(PHASE, (byte)phase.ordinal());
        }
    }
    /** Native LivingEntity and ServerPlayer death paths both award the legitimate killer here. */
    @Override public void awardKillScore(Entity victim, DamageSource killingBlow) {
        super.awardKillScore(victim, killingBlow);
        Bloodlust.award(this, victim, killingBlow);
    }
    public void onBloodlust(ServerLevel level) {
        this.entityData.set(BLOODLUST_AURA_END, level.getGameTime() + 24L);
        level.sendParticles(CRIMSON, this.getX(), this.getY(.55), this.getZ(), 18, .5, .8, .5, .04);
        this.playSound(SoundEvents.EVOKER_CAST_SPELL, .6F, .65F);
    }
    public static boolean eligibleTarget(LivingEntity target) {
        return target.isAlive() && !target.isRemoved() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))
                && !(target instanceof ServerPlayer p && p.hasDisconnected());
    }
    public boolean isLegalRangedTarget(LivingEntity target) {
        return eligibleTarget(target) && target.level() == this.level() && target != this
                && this.canAttack(target) && !this.isAlliedTo(target);
    }
    public Vec3 getMuzzlePosition() { return this.position().add(0, 1.9, 0); }
    public Vec3 getRangedAimPoint(LivingEntity target) { return target.position().add(0, target.getBbHeight() * .55, 0); }
    public boolean hasClearProjectilePath(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level) || !this.isLegalRangedTarget(target)) return false;
        Vec3 start = this.getMuzzlePosition(), end = this.getRangedAimPoint(target);
        BlockPos low = BlockPos.containing(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z));
        BlockPos high = BlockPos.containing(Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z));
        return level.hasChunksAt(low, high) && level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    public boolean canUseRangedAttack(LivingEntity target) {
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && this.isLegalRangedTarget(target)
                && this.distanceToSqr(target) <= square(BloodEndermanConfig.lanceRange())
                && this.getSensing().hasLineOfSight(target) && this.hasClearProjectilePath(target);
    }
    public boolean launchBloodLance(LivingEntity target, Vec3 observedVelocity) {
        if (!(this.level() instanceof ServerLevel level) || !this.canUseRangedAttack(target)) return false;
        this.pruneLances(); if (this.ownedLances.size() >= 4) return false;
        double leadTicks = Math.min(6.0, this.distanceTo(target) / BloodEndermanConfig.lanceSpeed());
        Vec3 lead = observedVelocity.multiply(1, 0, 1);
        if (lead.lengthSqr() > .1225) lead = lead.normalize().scale(.35);
        BloodLance lance = BloodLance.create(level, this, target, this.getMuzzlePosition(), this.getRangedAimPoint(target).add(lead.scale(leadTicks)));
        this.ownedLances.put(lance.getUUID(), new WeakReference<>(lance));
        if (!level.addFreshEntity(lance)) { this.onLanceRemoved(lance); return false; }
        this.playSound(SoundEvents.EVOKER_CAST_SPELL, .5F, .85F);
        return true;
    }
    public boolean isLanceValid(BloodLance lance) {
        var reference = this.ownedLances.get(lance.getUUID());
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && lance.level() == this.level()
                && reference != null && reference.get() == lance && lance.getOwner() == this;
    }
    public void onLanceRemoved(BloodLance lance) { this.ownedLances.remove(lance.getUUID()); }
    public int getOwnedLanceCount() { this.pruneLances(); return this.ownedLances.size(); }
    private void pruneLances() {
        for (var entry : List.copyOf(this.ownedLances.entrySet())) {
            BloodLance lance = entry.getValue().get();
            if (lance == null || lance.isRemoved()) this.ownedLances.remove(entry.getKey(), entry.getValue());
            else if (!this.level().isClientSide() && lance.flightExpired(this.level().getGameTime())) {
                lance.discard();
                this.ownedLances.remove(entry.getKey(), entry.getValue());
            }
        }
    }
    public void cancelOwnedLances() {
        for (var reference : List.copyOf(this.ownedLances.values())) {
            BloodLance lance = reference.get(); if (lance != null && !lance.isRemoved()) lance.discard();
        }
        this.ownedLances.clear();
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.striking || !(target instanceof LivingEntity victim) || !this.getMastermindBrain().canUseButchery(level, victim)) return false;
        this.getMastermindBrain().onButcheryAttempt(level);
        float before = victim.getHealth() + victim.getAbsorptionAmount();
        boolean hit;
        this.striking = true;
        try { hit = super.doHurtTarget(level, victim); }
        finally { this.striking = false; }
        if (hit && victim.getHealth() + victim.getAbsorptionAmount() < before) {
            this.setCombatPhase(CombatPhase.BUTCHERY);
            level.sendParticles(CRIMSON, victim.getX(), victim.getY(.55), victim.getZ(), 6, .24, .3, .24, .025);
            this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, .6F, .75F);
        }
        return hit;
    }
    public void warnLance(ServerLevel level) {
        level.sendParticles(CRIMSON, this.getX(), this.getY() + 1.9, this.getZ(), 6, .3, .3, .3, .025);
        this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, .45F, .8F);
    }
    @Override public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().bracesCaster()) {
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO);
        } else super.travel(input);
    }
    @Override protected int teleportAttemptCooldownTicks() { return BloodEndermanConfig.teleportCooldown(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().bracesCaster() && super.teleport(); }
    public boolean tryTacticalTeleport(Vec3 requested, Vec3 observed) {
        if (!(this.level() instanceof ServerLevel) || this.isNoAi() || this.getCombatPhase().bracesCaster()
                || this.distanceToSqr(observed) > square(BloodEndermanConfig.teleportRange()) || !nearObservation(requested, observed)) return false;
        this.teleportObservation = observed;
        try { return this.trySafeTeleport(requested, 3); }
        finally { this.teleportObservation = null; }
    }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        return this.teleportObservation == null || nearObservation(destination, this.teleportObservation);
    }
    private static boolean nearObservation(Vec3 destination, Vec3 observed) {
        double distance = destination.subtract(observed).horizontalDistanceSqr();
        return distance >= 3 * 3 && distance <= square(BloodEndermanConfig.teleportDistance() + 1.5)
                && Math.abs(destination.y - observed.y) <= 3;
    }
    @Override public void aiStep() {
        if (!this.level().isClientSide()) {
            this.pruneLances();
            if ((!this.isAlive() || this.isNoAi()) && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter();
            if (this.isBloodlustActive() && this.tickCount % 16 == 0 && this.level() instanceof ServerLevel level)
                level.sendParticles(CRIMSON, this.getX(), this.getY(.55), this.getZ(), 2, .3, .6, .3, .015);
        }
        super.aiStep();
    }
    @Override public void die(DamageSource source) {
        super.die(source);
        if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter();
    }
    @Override public void onRemoval(Entity.RemovalReason reason) {
        if (!this.level().isClientSide() && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter();
        super.onRemoval(reason);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter();
        this.entityData.set(BLOODLUST_AURA_END, 0L); this.getMastermindBrain().readCooldowns(input);
    }
    public static boolean checkBloodSpawnRules(EntityType<BloodEnderman> type, ServerLevelAccessor level,
                                               EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        for (int height = 0; height < Mth.ceil(type.getHeight()); height++) {
            BlockPos cell = pos.above(height); var state = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, state, state.getFluidState(), type)) return false;
        }
        return true;
    }
    public static boolean isSpawnTime(ServerLevelAccessor level) {
        long clock = Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L);
        return level.getLevel().dimension() == Level.OVERWORLD && clock >= 13000L && clock < 23000L;
    }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        return isSpawnTime(level) && level.getBiome(pos).is(Biomes.BADLANDS);
    }
    private static double square(double n) { return n * n; }
    public enum CombatPhase {
        IDLE, PURSUIT, BUTCHERY, LANCE_WINDUP, LANCE_RELEASE, RECOVERY, SEARCHING;
        public boolean bracesCaster() { return this == LANCE_WINDUP || this == LANCE_RELEASE || this == RECOVERY; }
    }
}
