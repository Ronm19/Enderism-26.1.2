package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.NightmareEndermanConfig;
import com.ronm19.enderism.entity.ai.NightmareEndermanBrain;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #014: cosmetic pre-hunt terror followed by a one-way commitment to ordinary physical pursuit. */
public final class NightmareEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_nightmare_enderman"));
    private static final Identifier HUNT_SPEED = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "nightmare_hunt_speed");
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(
            NightmareEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(
            NightmareEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> ENCOUNTER = SynchedEntityData.defineId(
            NightmareEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> COMMITTED = SynchedEntityData.defineId(
            NightmareEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions RED = new DustParticleOptions(0xA50D24, 0.8F);
    private @Nullable Vec3 teleportObservation;

    public NightmareEnderman(EntityType<? extends NightmareEnderman> type, Level level) {
        super(type, level);
        this.applyRuntimeAttributes(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes()
                .add(Attributes.MAX_HEALTH, NightmareEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, NightmareEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, NightmareEndermanConfig.DEFAULT_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, NightmareEndermanConfig.DEFAULT_RANGE);
    }

    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !NightmareEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(NightmareEndermanConfig.health());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(NightmareEndermanConfig.speed());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(NightmareEndermanConfig.damage());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(NightmareEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }

    @Override
    protected NightmareEndermanBrain createMastermindBrain() {
        return new NightmareEndermanBrain(this);
    }

    @Override
    public NightmareEndermanBrain getMastermindBrain() {
        return (NightmareEndermanBrain) super.getMastermindBrain();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(PHASE, (byte) CombatPhase.IDLE.ordinal());
        data.define(PHASE_START, 0L);
        data.define(ENCOUNTER, 0L);
        data.define(COMMITTED, false);
    }

    public CombatPhase getCombatPhase() {
        int value = this.entityData.get(PHASE);
        return value >= 0 && value < PHASES.length ? PHASES[value] : CombatPhase.IDLE;
    }

    public long getCombatPhaseStart() {
        return this.entityData.get(PHASE_START);
    }

    public long getEncounterId() {
        return this.entityData.get(ENCOUNTER);
    }

    public boolean isHuntCommitted() {
        return this.entityData.get(COMMITTED);
    }

    public boolean isEncounterTarget(ServerPlayer player) {
        return this.getMastermindBrain().isEncounterTarget(player);
    }

    public void nextEncounter() {
        if (!this.level().isClientSide()) this.entityData.set(ENCOUNTER, this.getEncounterId() + 1L);
    }

    public void setHuntCommitted(boolean committed) {
        if (!this.level().isClientSide()) {
            this.entityData.set(COMMITTED, committed);
            this.updateHuntSpeed();
        }
    }

    public void setCombatPhase(CombatPhase phase) {
        if (!this.level().isClientSide() && phase != this.getCombatPhase()) {
            this.entityData.set(PHASE_START, this.level().getGameTime());
            this.entityData.set(PHASE, (byte) phase.ordinal());
        }
    }

    public float getEyeBrightness() {
        if (this.isHuntCommitted()) return 1.0F;
        return this.getCombatPhase() == CombatPhase.IDLE ? 0.55F : 0.73F;
    }

    private void updateHuntSpeed() {
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean active = this.isHuntCommitted() && this.isAlive() && !this.isNoAi();
        double amount = NightmareEndermanConfig.huntSpeedBonus();
        var modifier = speed.getModifier(HUNT_SPEED);
        if (modifier != null && (!active || modifier.amount() != amount)) speed.removeModifier(HUNT_SPEED);
        if (active && amount > 0.0 && !speed.hasModifier(HUNT_SPEED)) {
            speed.addTransientModifier(new AttributeModifier(HUNT_SPEED, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
    }

    @Override
    public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase() == CombatPhase.REVEAL) {
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            super.travel(Vec3.ZERO);
        } else {
            super.travel(input);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity living) || !this.getMastermindBrain().canUseGrasp(living)) return false;
        float before = living.getHealth() + living.getAbsorptionAmount();
        boolean hit = super.doHurtTarget(level, target);
        if (hit && living.getHealth() + living.getAbsorptionAmount() < before) {
            this.setCombatPhase(CombatPhase.GRASP);
            level.sendParticles(RED, target.getX(), target.getY(0.55), target.getZ(), 7, 0.24, 0.35, 0.24, 0.01);
            this.playSound(SoundEvents.ENDERMAN_HURT, 0.55F, 0.70F);
        }
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hit = super.hurtServer(level, source, amount);
        if (hit && this.isAlive() && source.getEntity() instanceof LivingEntity attacker) {
            this.getMastermindBrain().onAttacked(level, attacker);
        }
        return hit;
    }

    public void revealHunt(ServerLevel level) {
        this.playSound(SoundEvents.ENDERMAN_SCREAM, 1.05F, 0.68F);
        level.sendParticles(RED, this.getX(), this.getY(0.58), this.getZ(), 17, 0.48, 0.72, 0.48, 0.025);
    }

    @Override
    protected int teleportAttemptCooldownTicks() {
        return this.isHuntCommitted() ? NightmareEndermanConfig.huntTeleportCooldown()
                : NightmareEndermanConfig.teleportCooldown();
    }

    @Override
    protected boolean teleport() {
        return this.getCombatPhase() != CombatPhase.REVEAL && super.teleport();
    }

    public boolean tryTacticalTeleport(Vec3 requested, Vec3 observed) {
        if (!(this.level() instanceof ServerLevel level) || this.isNoAi() || this.isInWater()
                || this.getCombatPhase() == CombatPhase.REVEAL || !nearObservation(requested, observed)) return false;
        this.teleportObservation = observed;
        try {
            return this.trySafeTeleport(requested, 5);
        } finally {
            this.teleportObservation = null;
        }
    }

    @Override
    protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        return this.teleportObservation == null || nearObservation(destination, this.teleportObservation);
    }

    private static boolean nearObservation(Vec3 destination, Vec3 observed) {
        double distance = destination.subtract(observed).horizontalDistanceSqr();
        return distance >= 2.8 * 2.8 && distance <= 9.0 * 9.0 && Math.abs(destination.y - observed.y) <= 4.0;
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide()) {
            if (this.isNoAi() && (this.isHuntCommitted() || this.getCombatPhase() != CombatPhase.IDLE)) {
                this.getMastermindBrain().clearEncounter();
            }
            this.updateHuntSpeed();
        }
        super.aiStep();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter();
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        if (!this.level().isClientSide() && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter();
        super.onRemoval(reason);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putLong("NightmareEncounterSerial", this.getEncounterId());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.applyRuntimeAttributes(false);
        // Unloading ends the transient confrontation. Its serial survives to reject old illusion packets.
        this.getMastermindBrain().clearEncounter();
        this.entityData.set(ENCOUNTER, Math.max(0L, input.getLongOr("NightmareEncounterSerial", 0L)));
    }

    public static boolean checkNightmareSpawnRules(EntityType<NightmareEnderman> type, ServerLevelAccessor level,
                                                   EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        for (int height = 0; height < Mth.ceil(type.getHeight()); height++) {
            BlockPos cell = pos.above(height);
            var state = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, state, state.getFluidState(), type)) return false;
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

    public enum CombatPhase {
        IDLE, OBSERVING, STALKING, ILLUSIONS, REVEAL, PURSUIT, GRASP, SEARCHING
    }
}
