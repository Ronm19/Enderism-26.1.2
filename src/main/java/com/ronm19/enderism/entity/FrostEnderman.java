package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.effect.FrostParalysis;
import com.ronm19.enderism.entity.ai.FrostEndermanBrain;
import com.ronm19.enderism.entity.ai.MastermindBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.ValueInput;

/** #005: nighttime cold awareness, with a brief paralysis on successful melee hits. */
public final class FrostEnderman extends AbstractEnderman {
    public static final double NORMAL_DETECTION_RANGE = 64.0;
    public static final double SNOWSTORM_DETECTION_RANGE = 74.0;
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_frost_enderman"));
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(
            FrostEnderman.class, EntityDataSerializers.BYTE);
    private static final HuntState[] HUNT_STATES = HuntState.values();

    public FrostEnderman(EntityType<? extends FrostEnderman> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Vanilla Enderman health, speed, and seven-point melee; no unapproved cold damage or attack kit.
        return AbstractEnderman.createAttributes().add(Attributes.FOLLOW_RANGE, SNOWSTORM_DETECTION_RANGE);
    }

    @Override
    protected MastermindBrain createMastermindBrain() {
        return new FrostEndermanBrain(this);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof ServerPlayer player) {
            FrostParalysis.tryApplyOnHit(player, this);
        }
        return hit;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(HUNT_STATE, (byte) HuntState.IDLE.ordinal());
    }

    public HuntState getHuntState() {
        int ordinal = this.entityData.get(HUNT_STATE);
        return ordinal >= 0 && ordinal < HUNT_STATES.length ? HUNT_STATES[ordinal] : HuntState.IDLE;
    }

    public void setHuntState(HuntState state) {
        if (!this.level().isClientSide()) this.entityData.set(HUNT_STATE, (byte) state.ordinal());
    }

    public float getEyeBrightness() {
        return switch (this.getHuntState()) {
            case IDLE -> 0.68F;
            case SENSING -> 0.78F;
            case SEARCHING -> 0.86F;
            case PURSUING -> 1.0F;
        };
    }

    /** The native query requires active snow precipitation at this position, not merely a cold biome. */
    public static double detectionRange(ServerLevel level, BlockPos position) {
        return level.precipitationAt(position) == Biome.Precipitation.SNOW
                ? SNOWSTORM_DETECTION_RANGE : NORMAL_DETECTION_RANGE;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && this.isAlive() && this.tickCount % 10 == 0) {
            this.level().addParticle(ParticleTypes.SNOWFLAKE,
                    this.getX() + (this.random.nextDouble() - 0.5) * this.getBbWidth(),
                    this.getY() + this.random.nextDouble() * this.getBbHeight(),
                    this.getZ() + (this.random.nextDouble() - 0.5) * this.getBbWidth(),
                    0.0, -0.005, 0.0);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        FrostParalysis.releaseOwnedBy(this);
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.getMastermindBrain().clearEncounter();
        this.setHuntState(HuntState.IDLE);
    }

    public static boolean checkFrostSpawnRules(EntityType<FrostEnderman> type, ServerLevelAccessor level,
                                              EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        for (int height = 0; height < 3; height++) {
            BlockPos cell = pos.above(height);
            var block = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, block, block.getFluidState(), type)) return false;
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

    public enum HuntState { IDLE, SENSING, PURSUING, SEARCHING }
}
