package com.ronm19.enderism.entity;

import com.ronm19.enderism.ArcticEndermanConfig;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.effect.ArcticStun;
import com.ronm19.enderism.entity.ai.ArcticEndermanBrain;
import com.ronm19.enderism.entity.ai.MastermindBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

/** #003: daytime winter predator, with cold pressure and a deliberately delayed execution. */
public final class ArcticEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_arctic_enderman"));
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(
            ArcticEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> ABILITY_WARNING = SynchedEntityData.defineId(
            ArcticEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final HuntState[] HUNT_STATES = HuntState.values();
    private static final DustParticleOptions FROST = new DustParticleOptions(0x8EDFF5, 0.65F);

    public ArcticEnderman(EntityType<? extends ArcticEnderman> type, Level level) {
        super(type, level);
        // Attribute registration precedes COMMON config loading. Apply tuning only to runtime server instances.
        if (!level.isClientSide() && ArcticEndermanConfig.SPEC.isLoaded()) {
            var attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attack != null) attack.setBaseValue(ArcticEndermanConfig.attackDamage());
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes()
                .add(Attributes.FOLLOW_RANGE, ArcticEndermanConfig.SNOW_RANGE)
                .add(Attributes.ATTACK_DAMAGE, ArcticEndermanConfig.DEFAULT_ATTACK_DAMAGE)
                .add(Attributes.ATTACK_KNOCKBACK, 0.35);
    }

    @Override
    protected MastermindBrain createMastermindBrain() { return new ArcticEndermanBrain(this); }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // A diurnal species must not inherit vanilla Enderman's idle daylight escape routine.
        this.getBrain().tick(level, this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(HUNT_STATE, (byte) HuntState.IDLE.ordinal());
        data.define(ABILITY_WARNING, false);
    }

    public HuntState getHuntState() {
        int ordinal = this.entityData.get(HUNT_STATE);
        return ordinal >= 0 && ordinal < HUNT_STATES.length ? HUNT_STATES[ordinal] : HuntState.IDLE;
    }

    public void setHuntState(HuntState state) {
        if (!this.level().isClientSide()) this.entityData.set(HUNT_STATE, (byte) state.ordinal());
    }

    public boolean isAbilityWindingUp() { return this.entityData.get(ABILITY_WARNING); }
    public void setAbilityWindingUp(boolean active) {
        if (!this.level().isClientSide()) this.entityData.set(ABILITY_WARNING, active);
    }
    public boolean isObserving() { return this.getHuntState() == HuntState.OBSERVING; }

    public float getEyeBrightness() {
        if (this.isAbilityWindingUp() || this.isObserving()) return 1.0F;
        float nightMinimum = Math.floorMod(this.level().getOverworldClockTime(), 24000L) >= 12000L ? 0.82F : 0.48F;
        return switch (this.getHuntState()) {
            case ATTACKING, FREEZING, EXECUTING -> 0.96F;
            case APPROACHING, SEARCHING -> Math.max(nightMinimum, 0.76F);
            case SENSING -> Math.max(nightMinimum, 0.64F);
            default -> nightMinimum;
        };
    }

    /** The engine checks active weather, local snow precipitation, open sky, and heightmap shelter. */
    public static double detectionRange(ServerLevel level, BlockPos position) {
        return level.precipitationAt(position) == Biome.Precipitation.SNOW
                ? ArcticEndermanConfig.SNOW_RANGE : ArcticEndermanConfig.NORMAL_RANGE;
    }

    public void beginColdWarning(ServerLevel level) {
        this.setAbilityWindingUp(true);
        this.playSound(SoundEvents.POWDER_SNOW_STEP, 0.9F, 0.65F);
        level.sendParticles(FROST, this.getX(), this.getEyeY(), this.getZ(), 6, 0.25, 0.15, 0.25, 0.0);
    }

    public void coldBurst(ServerLevel level, LivingEntity victim) {
        level.sendParticles(FROST, victim.getX(), victim.getY(0.6), victim.getZ(), 9, 0.3, 0.6, 0.3, 0.0);
        this.playSound(SoundEvents.GLASS_BREAK, 0.35F, 1.7F);
    }

    public void executionFrost(ServerLevel level, LivingEntity victim, float progress) {
        level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY(0.5), victim.getZ(),
                2 + Math.round(progress * 3), 0.35, 0.7, 0.35, 0.01);
    }

    public void addColdPressure(ServerLevel level, LivingEntity victim, int ticks) {
        if (victim.canFreeze() && level.getGameRules().get(GameRules.FREEZE_DAMAGE)) {
            // Native freezing supplies pressure/overlay, but cannot short-circuit the controlled lethal phase.
            int maximum = Math.max(0, victim.getTicksRequiredToFreeze() - 1);
            victim.setTicksFrozen(Math.min(maximum, victim.getTicksFrozen() + Math.max(0, ticks)));
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean success = super.doHurtTarget(level, target);
        if (success && target instanceof LivingEntity victim) {
            this.addColdPressure(level, victim, ArcticEndermanConfig.STRIKE_FREEZE_TICKS);
            level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY(0.5), victim.getZ(),
                    5, 0.25, 0.4, 0.25, 0.01);
        }
        return success;
    }

    @Override
    protected boolean teleport() {
        // Defensive vanilla teleportation must not move the watcher during an intentionally stationary phase.
        return !this.isObserving() && !this.isAbilityWindingUp() && super.teleport();
    }

    @Override
    public void travel(Vec3 input) {
        if (this.isAbilityWindingUp() || this.isObserving()) {
            // Suspend only this phase's travel; no persistent NoGravity or movement-attribute changes to leak on reload.
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        super.travel(input);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        ArcticStun.releaseOwnedBy(this);
        this.setAbilityWindingUp(false);
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.getMastermindBrain().clearEncounter();
        this.setHuntState(HuntState.IDLE);
        this.setAbilityWindingUp(false);
    }

    public static boolean checkArcticSpawnRules(EntityType<ArcticEnderman> type, ServerLevelAccessor level,
                                               EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        // Deliberately use the generic ground predicate, not Monster's darkness predicate: Arctic is diurnal.
        if (!isSpawnHabitat(level, pos) || level.getDifficulty() == Difficulty.PEACEFUL
                || !Mob.checkMobSpawnRules(type, level, reason, pos, random)) return false;
        for (int height = 0; height < 3; height++) {
            BlockPos cell = pos.above(height);
            var block = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, block, block.getFluidState(), type)) return false;
        }
        return true;
    }

    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        return isSpawnTime(level) && level.getBiome(pos).is(SPAWN_BIOMES);
    }

    public static boolean isSpawnTime(ServerLevelAccessor level) {
        long clock = Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L);
        return level.getLevel().dimension() == Level.OVERWORLD && clock < 12000L;
    }

    public enum HuntState { IDLE, SENSING, APPROACHING, ATTACKING, FREEZING, EXECUTING, OBSERVING, SEARCHING, RESETTING }
}
