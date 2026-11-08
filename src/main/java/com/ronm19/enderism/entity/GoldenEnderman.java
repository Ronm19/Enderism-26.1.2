package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.GoldenEndermanConfig;
import com.ronm19.enderism.effect.GoldenStun;
import com.ronm19.enderism.entity.ai.GoldenEndermanBrain;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #013: a daytime Badlands hunter built around temporary movement control. */
public final class GoldenEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_golden_enderman"));
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(
            GoldenEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(
            GoldenEnderman.class, EntityDataSerializers.LONG);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFBF33, 1.1F);
    private static final DustParticleOptions JUDGMENT_GOLD = new DustParticleOptions(0xFFE17A, 1.35F);
    private @Nullable Vec3 teleportObservation;

    public GoldenEnderman(EntityType<? extends GoldenEnderman> type, Level level) {
        super(type, level);
        this.applyRuntimeAttributes(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes()
                .add(Attributes.MAX_HEALTH, GoldenEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, GoldenEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, GoldenEndermanConfig.DEFAULT_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, GoldenEndermanConfig.DEFAULT_RANGE);
    }

    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !GoldenEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(GoldenEndermanConfig.health());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(GoldenEndermanConfig.speed());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(GoldenEndermanConfig.damage());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(GoldenEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }

    @Override
    protected GoldenEndermanBrain createMastermindBrain() {
        return new GoldenEndermanBrain(this);
    }

    @Override
    public GoldenEndermanBrain getMastermindBrain() {
        return (GoldenEndermanBrain) super.getMastermindBrain();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(PHASE, (byte) CombatPhase.IDLE.ordinal());
        data.define(PHASE_START, 0L);
    }

    public CombatPhase getCombatPhase() {
        return PHASES[this.entityData.get(PHASE)];
    }

    public long getCombatPhaseStart() {
        return this.entityData.get(PHASE_START);
    }

    public void setCombatPhase(CombatPhase phase) {
        if (!this.level().isClientSide() && phase != this.getCombatPhase()) {
            this.entityData.set(PHASE_START, this.level().getGameTime());
            this.entityData.set(PHASE, (byte) phase.ordinal());
        }
    }

    public float getEyeBrightness() {
        return switch (this.getCombatPhase()) {
            case IDLE -> 0.64F;
            case SEARCHING, RECOVERY -> 0.76F;
            case PURSUIT -> 0.89F;
            default -> 1.0F;
        };
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // Skip vanilla's idle daylight escape while preserving the shared Brain.
        this.getBrain().tick(level, this);
    }

    @Override
    public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().bracesCaster()) {
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            super.travel(Vec3.ZERO);
        } else {
            super.travel(input);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.getCombatPhase().bracesCaster()) return false;
        float before = target instanceof LivingEntity living ? effectiveHealth(living) : 0.0F;
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living && effectiveHealth(living) < before) {
            this.setCombatPhase(CombatPhase.MIDAS_TOUCH);
            if (this.getMastermindBrain().applyMidas(level, living)) {
                this.goldImpact(level, living, false);
            }
        }
        return hit;
    }

    public boolean strikeJudgment(ServerLevel level, LivingEntity target) {
        float before = effectiveHealth(target);
        return target.hurtServer(level, this.damageSources().mobAttack(this), GoldenEndermanConfig.judgmentDamage())
                && effectiveHealth(target) < before;
    }

    private static float effectiveHealth(LivingEntity living) {
        return living.getHealth() + living.getAbsorptionAmount();
    }

    public void beginGoldCast(ServerLevel level, boolean judgment) {
        this.playSound(judgment ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.AMETHYST_BLOCK_RESONATE,
                judgment ? 0.95F : 0.65F, judgment ? 0.76F : 1.13F);
        this.goldWindup(level, judgment);
    }

    public void goldWindup(ServerLevel level, boolean judgment) {
        level.sendParticles(judgment ? JUDGMENT_GOLD : GOLD, this.getX(), this.getY(0.67), this.getZ(),
                judgment ? 8 : 5, 0.40, 0.23, 0.40, 0.025);
    }

    public void releaseShackles(ServerLevel level, LivingEntity target) {
        Vec3 origin = this.position().add(0.0, this.getBbHeight() * 0.62, 0.0);
        Vec3 reach = target.getBoundingBox().getCenter().subtract(origin);
        for (int i = 1; i <= 7; i++) {
            Vec3 point = origin.add(reach.scale(i / 8.0));
            level.sendParticles(GOLD, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        this.goldImpact(level, target, false);
    }

    public void goldImpact(ServerLevel level, LivingEntity target, boolean judgment) {
        level.sendParticles(judgment ? JUDGMENT_GOLD : GOLD, target.getX(), target.getY(0.56), target.getZ(),
                judgment ? 19 : 9, 0.32, 0.41, 0.32, 0.045);
        if (judgment) {
            level.sendParticles(ParticleTypes.WAX_ON, target.getX(), target.getY(0.55), target.getZ(),
                    12, 0.30, 0.38, 0.30, 0.035);
        }
        this.playSound(judgment ? SoundEvents.AMETHYST_BLOCK_BREAK : SoundEvents.AMETHYST_BLOCK_CHIME,
                judgment ? 0.9F : 0.65F, judgment ? 0.8F : 1.1F);
    }

    @Override
    protected int teleportAttemptCooldownTicks() {
        return GoldenEndermanConfig.teleportCooldown();
    }

    @Override
    protected boolean teleport() {
        // A defensive warp during a warning would make the released attack unpredictable.
        return !this.getCombatPhase().bracesCaster() && super.teleport();
    }

    public boolean tryTacticalTeleport(Vec3 requested, Vec3 observed) {
        if (!(this.level() instanceof ServerLevel level) || this.getCombatPhase().bracesCaster()
                || this.isNoAi() || this.isInWater() || !nearObservation(requested, observed)) return false;
        Vec3 origin = this.position();
        this.teleportObservation = observed;
        try {
            if (!this.trySafeTeleport(requested, 4)) return false;
        } finally {
            this.teleportObservation = null;
        }
        level.sendParticles(GOLD, origin.x, origin.y + 1.25, origin.z, 5, 0.25, 0.36, 0.25, 0.02);
        level.sendParticles(GOLD, this.getX(), this.getY(0.45), this.getZ(), 6, 0.25, 0.38, 0.25, 0.02);
        return true;
    }

    @Override
    protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        return this.teleportObservation == null || nearObservation(destination, this.teleportObservation);
    }

    private static boolean nearObservation(Vec3 destination, Vec3 observed) {
        double horizontalDistance = destination.subtract(observed).horizontalDistanceSqr();
        return horizontalDistance >= 2.5 * 2.5 && horizontalDistance <= 7.0 * 7.0
                && Math.abs(destination.y - observed.y) <= 3.0;
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide() && (this.isNoAi() || !this.isAlive())
                && this.getCombatPhase() != CombatPhase.IDLE) {
            this.getMastermindBrain().clearEncounter();
        }
        super.aiStep();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.isAlive() && !this.level().isClientSide()) {
            this.getMastermindBrain().clearEncounter();
            GoldenStun.releaseOwnedBy(this);
        }
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        if (!this.level().isClientSide() && this.getMastermindBrain() != null) {
            this.getMastermindBrain().clearEncounter();
            GoldenStun.releaseOwnedBy(this);
        }
        super.onRemoval(reason);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.getMastermindBrain().writeCooldowns(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.applyRuntimeAttributes(false);
        this.getMastermindBrain().clearEncounter();
        this.getMastermindBrain().readCooldowns(input);
    }

    public static boolean checkGoldenSpawnRules(EntityType<GoldenEnderman> type, ServerLevelAccessor level,
                                                EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || level.getDifficulty() == Difficulty.PEACEFUL
                || !Mob.checkMobSpawnRules(type, level, reason, pos, random)) return false;
        for (int height = 0; height < Mth.ceil(type.getHeight()); height++) {
            BlockPos cell = pos.above(height);
            var state = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, state, state.getFluidState(), type)) return false;
        }
        return true;
    }

    public static boolean isSpawnTime(ServerLevelAccessor level) {
        long clock = Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L);
        return level.getLevel().dimension() == Level.OVERWORLD && clock < 12000L;
    }

    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        return isSpawnTime(level) && level.getBiome(pos).is(SPAWN_BIOMES);
    }

    public enum CombatPhase {
        IDLE, PURSUIT, MIDAS_TOUCH, SHACKLES_WINDUP, SHACKLES_RELEASE,
        JUDGMENT_WINDUP, JUDGMENT_IMPACT, RECOVERY, SEARCHING;

        boolean bracesCaster() {
            return switch (this) {
                case SHACKLES_WINDUP, SHACKLES_RELEASE, JUDGMENT_WINDUP, JUDGMENT_IMPACT, RECOVERY -> true;
                default -> false;
            };
        }
    }
}
