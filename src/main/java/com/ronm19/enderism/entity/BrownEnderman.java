package com.ronm19.enderism.entity;

import com.ronm19.enderism.EnderismConfig;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.ai.BrownEndermanBrain;
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
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.phys.Vec3;

/** Enderism #001. Each instance owns its own native Brain and temporary encounter state. */
public final class BrownEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_brown_enderman"));
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(
            BrownEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> GAZE_TARGET = SynchedEntityData.defineId(
            BrownEnderman.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FEAR_VISUAL = SynchedEntityData.defineId(
            BrownEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final HuntState[] HUNT_STATES = HuntState.values();
    private long fearVisualUntil;

    public BrownEnderman(EntityType<? extends BrownEnderman> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Initial Normal-difficulty tuning: 13 raw -> 8.58 damage through unenchanted iron armor.
        // Two hits do not kill; three at the default interval also allow for ordinary food regeneration.
        return AbstractEnderman.createAttributes()
                .add(Attributes.ATTACK_DAMAGE, 13.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.8);
    }

    @Override
    protected MastermindBrain createMastermindBrain() {
        return new BrownEndermanBrain(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(HUNT_STATE, (byte) HuntState.IDLE.ordinal());
        data.define(GAZE_TARGET, -1);
        data.define(FEAR_VISUAL, false);
    }

    public HuntState getHuntState() {
        int ordinal = this.entityData.get(HUNT_STATE);
        return ordinal >= 0 && ordinal < HUNT_STATES.length ? HUNT_STATES[ordinal] : HuntState.IDLE;
    }

    public boolean isIntimidating() {
        return this.getHuntState() == HuntState.INTIMIDATING;
    }

    public float getEyeBrightness() {
        return switch (this.getHuntState()) {
            case IDLE -> 0.55F;
            case SEARCHING -> 0.75F;
            default -> 1.0F;
        };
    }

    public int getGazeTargetId() {
        return this.entityData.get(GAZE_TARGET);
    }

    public boolean isFearVisualActive() {
        return this.entityData.get(FEAR_VISUAL);
    }

    public void setHuntState(HuntState state) {
        if (!this.level().isClientSide()) {
            this.entityData.set(HUNT_STATE, (byte) state.ordinal());
        }
    }

    public void beginFearVisual(ServerPlayer target) {
        if (this.level() instanceof ServerLevel level) {
            this.entityData.set(GAZE_TARGET, target.getId());
            this.entityData.set(FEAR_VISUAL, true);
            // A fast movement response must not vanish before clients can receive the visual state.
            this.fearVisualUntil = level.getGameTime() + 20L;
        }
    }

    public void clearFearVisual() {
        if (!this.level().isClientSide()) {
            this.entityData.set(FEAR_VISUAL, false);
            this.entityData.set(GAZE_TARGET, -1);
            this.fearVisualUntil = 0L;
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.isFearVisualActive() && !this.isIntimidating() && level.getGameTime() >= this.fearVisualUntil) {
            this.clearFearVisual();
        }
    }

    @Override
    protected int teleportAttemptCooldownTicks() {
        return EnderismConfig.teleportAttemptCooldownTicks();
    }

    public boolean attemptInterception(Vec3 observedDestination) {
        // Start just above the observed path and search down within a bounded local column.
        return this.trySafeTeleport(observedDestination.add(0, 2.0, 0), 12);
    }

    public void beginChargeEffects(ServerLevel level) {
        this.playSound(SoundEvents.ENDERMAN_SCREAM, 0.8F, 0.8F);
        level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.2, this.getZ(),
                12, 0.3, 0.7, 0.3, 0.05);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean successful = super.doHurtTarget(level, target);
        if (successful) {
            this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.8F, 0.75F);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.6), target.getZ(),
                    5, 0.2, 0.2, 0.2, 0.02);
        }
        return successful;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.clearFearVisual();
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // Encounter memories/visuals are deliberately not persistent knowledge about players.
        this.getMastermindBrain().clearEncounter();
        this.setHuntState(HuntState.IDLE);
        this.clearFearVisual();
    }

    public static boolean checkBrownSpawnRules(EntityType<BrownEnderman> type, ServerLevelAccessor level,
                                              EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }
        if (!isSpawnHabitat(level, pos)
                || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) {
            return false;
        }
        // ON_GROUND's generic predicate only reserves two cells; this silhouette needs all three.
        for (int offset = 0; offset < 3; offset++) {
            BlockPos cell = pos.above(offset);
            var block = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, block, block.getFluidState(), type)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        return isSpawnTime(level) && level.getBiome(pos).is(SPAWN_BIOMES);
    }

    public static boolean isSpawnTime(ServerLevelAccessor level) {
        long clock = Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L);
        return level.getLevel().dimension() == Level.OVERWORLD && clock >= 13000L && clock < 23000L;
    }

    public enum HuntState {
        IDLE, INTIMIDATING, CHARGING, HUNTING, ATTACKING, SEARCHING
    }
}
