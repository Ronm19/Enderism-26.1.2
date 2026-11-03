package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.SilverEndermanConfig;
import com.ronm19.enderism.entity.ai.MastermindBrain;
import com.ronm19.enderism.entity.ai.SilverEndermanBrain;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #009: a female Dark Forest huntress with fast native melee and temporary armor weakening. */
public final class SilverEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_silver_enderman"));
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(SilverEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> SLASH_PATTERN = SynchedEntityData.defineId(SilverEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> STEP_WARMUP_END = SynchedEntityData.defineId(SilverEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> FRACTURE_PULSE_END = SynchedEntityData.defineId(SilverEnderman.class, EntityDataSerializers.LONG);
    private static final HuntState[] STATES = HuntState.values();
    private static final DustParticleOptions SILVER = new DustParticleOptions(0xE9ECFF, 0.65F);
    private int nextSlashPattern;

    public SilverEnderman(EntityType<? extends SilverEnderman> type, Level level) {
        super(type, level);
        this.applyRuntimeAttributes(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Attribute registration precedes COMMON configuration loading.
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, SilverEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, SilverEndermanConfig.DEFAULT_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, SilverEndermanConfig.DEFAULT_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, SilverEndermanConfig.DEFAULT_FOLLOW_RANGE);
    }
    private void applyRuntimeAttributes(boolean fillHealth) {
        if (this.level().isClientSide() || !SilverEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SilverEndermanConfig.health());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(SilverEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SilverEndermanConfig.movementSpeed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SilverEndermanConfig.followRange());
        this.setHealth(fillHealth ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected MastermindBrain createMastermindBrain() { return new SilverEndermanBrain(this); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(HUNT_STATE, (byte) HuntState.IDLE.ordinal());
        data.define(SLASH_PATTERN, (byte) 0);
        data.define(STEP_WARMUP_END, 0L);
        data.define(FRACTURE_PULSE_END, 0L);
    }
    public HuntState getHuntState() {
        int index = this.entityData.get(HUNT_STATE);
        return index >= 0 && index < STATES.length ? STATES[index] : HuntState.IDLE;
    }
    public void setHuntState(HuntState state) {
        if (!this.level().isClientSide()) this.entityData.set(HUNT_STATE, (byte) state.ordinal());
    }
    public int getSlashPattern() { return this.entityData.get(SLASH_PATTERN); }
    public boolean isStepWindingUp() { return this.getWarmupTicksRemaining() > 0; }
    public int getWarmupTicksRemaining() {
        return (int) Math.max(0L, this.entityData.get(STEP_WARMUP_END) - this.level().getGameTime());
    }
    public void setStepWarmupEnd(long end) {
        if (!this.level().isClientSide()) this.entityData.set(STEP_WARMUP_END, end);
    }
    public boolean isFracturePulseActive() {
        return this.entityData.get(FRACTURE_PULSE_END) > this.level().getGameTime();
    }
    public float getEyeBrightness() {
        if (this.isFracturePulseActive() || this.isStepWindingUp()) return 1.0F;
        if (this.getHuntState() != HuntState.IDLE) return 0.96F;
        long time = Math.floorMod(this.level().getOverworldClockTime(), 24000L);
        return time >= 13000L && time < 23000L ? 0.86F : 0.62F;
    }
    public void clearAbilityVisuals() {
        if (!this.level().isClientSide()) {
            this.entityData.set(STEP_WARMUP_END, 0L);
            this.entityData.set(FRACTURE_PULSE_END, 0L);
            this.setHuntState(HuntState.IDLE);
        }
    }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!this.isAlive() || this.isRemoved() || this.isNoAi() || this.isStepWindingUp()
                || !(target instanceof LivingEntity living) || !this.isWithinMeleeAttackRange(living)
                || !this.getSensing().hasLineOfSight(living)) return false;
        this.entityData.set(SLASH_PATTERN, (byte) this.nextSlashPattern);
        this.nextSlashPattern = (this.nextSlashPattern + 1) % 3;
        this.silverCrescent(level, living);
        // One native hit per controlled melee event; pose/particle ticks never deal damage.
        boolean hit = super.doHurtTarget(level, target);
        if (hit) ((SilverEndermanBrain) this.getMastermindBrain()).onSuccessfulMelee(level, living);
        return hit;
    }

    private void silverCrescent(ServerLevel level, LivingEntity target) {
        Vec3 forward = target.position().subtract(this.position()).multiply(1, 0, 1).normalize();
        if (forward.lengthSqr() < 0.01) forward = this.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 origin = this.position().add(0, 1.65, 0);
        int pattern = this.getSlashPattern();
        for (int point = 0; point < 12; point++) {
            double angle = -1.15 + 2.3 * point / 11.0;
            double lateral = Math.sin(angle) * 1.65;
            double vertical = pattern == 0 ? 0.0 : lateral * (pattern == 1 ? 0.52 : -0.52);
            Vec3 spot = origin.add(forward.scale(Math.cos(angle) * 1.65)).add(side.scale(lateral)).add(0, vertical, 0);
            level.sendParticles(SILVER, spot.x, spot.y, spot.z, 1, 0, 0, 0, 0);
        }
        Vec3 center = origin.add(forward.scale(1.1));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.55F, pattern == 0 ? 1.35F : 1.55F);
    }
    public void fractureImpact(ServerLevel level, LivingEntity target) {
        this.entityData.set(FRACTURE_PULSE_END, level.getGameTime() + 12);
        this.setHuntState(HuntState.FRACTURING);
        level.sendParticles(SILVER, target.getX(), target.getY() + target.getBbHeight() * 0.55,
                target.getZ(), 12, 0.32, 0.45, 0.32, 0.06);
        this.playSound(SoundEvents.GLASS_BREAK, 0.5F, 1.65F);
    }
    public void stepWarning(ServerLevel level) {
        level.sendParticles(SILVER, this.getX(), this.getY() + 1.0, this.getZ(), 8, 0.35, 0.7, 0.35, 0.015);
        this.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.3F, 1.55F);
    }

    /** Coordinates come from a finite visible observation, never a hidden target's current position. */
    public boolean tryPhantomStepTeleport(Vec3 requested, Vec3 observedPoint) {
        if (!(this.level() instanceof ServerLevel level)) return false;
        Vec3 standing = this.findPhantomStepDestination(requested, observedPoint);
        if (standing == null) return false;
        Vec3 before = this.position();
        // The shared utility owns collision/hazard checks, teleport events, sounds, and its attempt cooldown.
        if (!this.trySafeTeleport(standing, 0)) return false;
        level.sendParticles(SILVER, before.x, before.y + 1.1, before.z, 7, 0.25, 0.7, 0.25, 0.025);
        level.sendParticles(SILVER, this.getX(), this.getY() + 1.1, this.getZ(), 7, 0.25, 0.7, 0.25, 0.025);
        return true;
    }
    /** Bounded geometric preflight lets the Brain choose one of three flanks before one actual attempt. */
    public @Nullable Vec3 findPhantomStepDestination(Vec3 requested, Vec3 observedPoint) {
        if (!(this.level() instanceof ServerLevel level) || !finite(requested) || !finite(observedPoint)
                || this.isNoAi() || this.isInWater() || this.distanceToSqr(observedPoint) > square(SilverEndermanConfig.stepActivationRange())) return null;
        double flank = SilverEndermanConfig.stepFlankDistance();
        double x = requested.x - observedPoint.x, z = requested.z - observedPoint.z;
        if (x * x + z * z < 4.0 || x * x + z * z > square(flank + 1.5)) return null;
        // At most five tiny height checks per requested flank, with no vertical cave search.
        int[] heights = {0, 1, -1, 2, -2};
        for (int offset : heights) {
            BlockPos feet = BlockPos.containing(requested.x, observedPoint.y, requested.z).above(offset);
            if (Math.abs(feet.getY() - observedPoint.y) > 2.0 || feet.getY() <= level.getMinY()
                    || feet.getY() + this.getBbHeight() >= level.getMaxY()) continue;
            Vec3 standing = new Vec3(requested.x, feet.getY(), requested.z);
            AABB box = this.getBoundingBox().move(standing.subtract(this.position()));
            BlockPos low = BlockPos.containing(Math.min(observedPoint.x, box.minX), Math.min(observedPoint.y, box.minY) - 1,
                    Math.min(observedPoint.z, box.minZ));
            BlockPos high = BlockPos.containing(Math.max(observedPoint.x, box.maxX), Math.max(observedPoint.y + this.getEyeHeight(), box.maxY),
                    Math.max(observedPoint.z, box.maxZ));
            if (!level.getWorldBorder().isWithinBounds(box) || !level.hasChunksAt(low, high)) continue;
            BlockState support = level.getBlockState(feet.below());
            if (!support.blocksMotion() || support.is(BlockTags.LEAVES) || isStepHazard(support)
                    || !level.noCollision(this, box) || level.containsAnyLiquid(box)) continue;
            boolean unsafe = false;
            for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                BlockState state = level.getBlockState(cell);
                if (state.is(BlockTags.LEAVES) || isStepHazard(state)) { unsafe = true; break; }
            }
            if (unsafe || level.clip(new ClipContext(observedPoint.add(0, 1.6, 0), standing.add(0, this.getEyeHeight(), 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
            return standing;
        }
        return null;
    }
    private static double square(double value) { return value * value; }
    private static boolean finite(Vec3 position) {
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
    }
    private static boolean isStepHazard(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE);
    }
    @Override protected boolean teleport() { return !this.isStepWindingUp() && super.teleport(); }
    @Override public void travel(Vec3 input) {
        if (this.isStepWindingUp()) { this.setDeltaMovement(Vec3.ZERO); return; }
        super.travel(input);
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            SilverEndermanBrain brain = (SilverEndermanBrain) this.getMastermindBrain();
            brain.interruptStep();
            if (!this.isAlive()) brain.clearEncounter();
        }
        return hurt;
    }
    @Override public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && (this.isNoAi() || !this.isAlive())) this.getMastermindBrain().clearEncounter();
    }
    @Override public void setNoAi(boolean noAi) {
        super.setNoAi(noAi);
        if (noAi && !this.level().isClientSide() && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter();
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.getMastermindBrain().clearEncounter();
        this.applyRuntimeAttributes(false);
    }
    public static boolean checkSilverSpawnRules(EntityType<SilverEnderman> type, ServerLevelAccessor level,
                                              EntitySpawnReason reason, BlockPos position, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, position) || !Monster.checkMonsterSpawnRules(type, level, reason, position, random)) return false;
        for (int height = 0; height < 3; height++) {
            BlockPos cell = position.above(height); BlockState block = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, block, block.getFluidState(), type)) return false;
        }
        return true;
    }
    public static boolean isSpawnTime(ServerLevelAccessor level) {
        long time = Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L);
        return level.getLevel().dimension() == Level.OVERWORLD && time >= 13000L && time < 23000L;
    }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos position) {
        return isSpawnTime(level) && level.getBiome(position).is(SPAWN_BIOMES);
    }
    public enum HuntState { IDLE, DETECTING, STALKING, APPROACHING, FLANKING, ATTACKING, FRACTURING, PURSUING, SEARCHING, RESETTING }
}
