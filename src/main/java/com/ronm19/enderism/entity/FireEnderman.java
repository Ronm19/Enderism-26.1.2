package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.ai.FireEndermanBrain;
import com.ronm19.enderism.entity.ai.MastermindBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;

/** #004: successful melee strikes carry a short, vanilla fire burn. */
public final class FireEnderman extends AbstractEnderman {
    public static final double DETECTION_RANGE = 32.0;
    public static final TagKey<Biome> OVERWORLD_SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_fire_enderman_overworld"));
    public static final TagKey<Biome> NETHER_SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_fire_enderman_nether"));
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(
            FireEnderman.class, EntityDataSerializers.BYTE);
    private static final HuntState[] HUNT_STATES = HuntState.values();

    public FireEnderman(EntityType<? extends FireEnderman> type, Level level) {
        super(type, level);
        // Fire is harmless, but ordinary ground navigation must still avoid immersion in lava.
        this.setPathfindingMalus(PathType.FIRE, 0.0F);
        this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0F);
        this.setPathfindingMalus(PathType.LAVA, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Vanilla Enderman health, speed and seven-point melee are the provisional baseline.
        return AbstractEnderman.createAttributes().add(Attributes.FOLLOW_RANGE, DETECTION_RANGE);
    }

    @Override
    protected MastermindBrain createMastermindBrain() {
        return new FireEndermanBrain(this);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target.isAlive()) {
            target.igniteForSeconds(2.0F);
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
            case IDLE -> 0.72F;
            case SEARCHING -> 0.85F;
            case PURSUING -> 1.0F;
        };
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // This species inhabits bright daytime terrain; vanilla idle sunlight escape is inappropriate.
        this.getBrain().tick(level, this);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && this.isAlive() && this.tickCount % 10 == 0) {
            // Cosmetic only, one small ember per half-second; no fire placement or damage aura.
            this.level().addParticle(ParticleTypes.SMALL_FLAME,
                    this.getX() + (this.random.nextDouble() - 0.5) * this.getBbWidth(),
                    this.getY() + 0.2 + this.random.nextDouble() * (this.getBbHeight() - 0.2),
                    this.getZ() + (this.random.nextDouble() - 0.5) * this.getBbWidth(),
                    0.0, 0.012, 0.0);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.getMastermindBrain().clearEncounter();
        this.setHuntState(HuntState.IDLE);
    }

    public static boolean checkFireSpawnRules(EntityType<FireEnderman> type, ServerLevelAccessor level,
                                             EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || level.getDifficulty() == Difficulty.PEACEFUL) return false;

        if (level.getLevel().dimension() == Level.NETHER) {
            if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        } else {
            // Deliberately preserve ground checks without Monster's darkness gate in the Overworld.
            if (!Mob.checkMobSpawnRules(type, level, reason, pos, random)) return false;
        }
        for (int height = 0; height < 3; height++) {
            BlockPos cell = pos.above(height);
            var block = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, block, block.getFluidState(), type)) return false;
        }
        // Same biome weight, with half of otherwise valid night attempts rejected only in the Overworld.
        // This produces a 2:1 day/night opportunity ratio without changing the Nether spawn pool.
        return level.getLevel().dimension() != Level.OVERWORLD || isOverworldDay(level) || random.nextBoolean();
    }

    public static boolean isOverworldDay(ServerLevelAccessor level) {
        return Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L) < 12000L;
    }

    public static boolean isSpawnTime(ServerLevelAccessor level) {
        return level.getLevel().dimension() == Level.OVERWORLD || level.getLevel().dimension() == Level.NETHER;
    }

    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        if (level.getLevel().dimension() == Level.OVERWORLD) return level.getBiome(pos).is(OVERWORLD_SPAWN_BIOMES);
        return level.getLevel().dimension() == Level.NETHER && level.getBiome(pos).is(NETHER_SPAWN_BIOMES);
    }

    public enum HuntState { IDLE, PURSUING, SEARCHING }
}
