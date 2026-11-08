package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.ForestEndermanConfig;
import com.ronm19.enderism.effect.ForestRoots;
import com.ronm19.enderism.entity.ai.ForestEndermanBrain;
import com.ronm19.enderism.entity.ai.MastermindBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

/** #006: a woodland stalker with exactly three themed abilities, using the shared Enderism Brain. */
public final class ForestEnderman extends AbstractEnderman {
    public static final Identifier BURST_MODIFIER_ID = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "forest_hunting_burst");
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_forest_enderman"));
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(ForestEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> CAMOUFLAGED = SynchedEntityData.defineId(ForestEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> VINE_WARNING = SynchedEntityData.defineId(ForestEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> VINE_WARMUP_TICKS = SynchedEntityData.defineId(ForestEnderman.class, EntityDataSerializers.INT);
    private static final HuntState[] HUNT_STATES = HuntState.values();
    private static final DustParticleOptions LEAF_WARNING = new DustParticleOptions(0x79B739, 0.8F);
    private int lastForestStareSound = Integer.MIN_VALUE;

    public ForestEnderman(EntityType<? extends ForestEnderman> type, Level level) {
        super(type, level);
        this.applyRuntimeAttributes(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Static attribute registration runs before COMMON configuration is loaded.
        return AbstractEnderman.createAttributes()
                .add(Attributes.MAX_HEALTH, ForestEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ARMOR, ForestEndermanConfig.DEFAULT_ARMOR)
                .add(Attributes.ATTACK_DAMAGE, ForestEndermanConfig.DEFAULT_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, ForestEndermanConfig.DEFAULT_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, ForestEndermanConfig.DEFAULT_FOLLOW_RANGE);
    }

    private void applyRuntimeAttributes(boolean fillHealth) {
        if (this.level().isClientSide() || !ForestEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ForestEndermanConfig.health());
        this.getAttribute(Attributes.ARMOR).setBaseValue(ForestEndermanConfig.armor());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ForestEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(ForestEndermanConfig.movementSpeed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(ForestEndermanConfig.followRange());
        this.setHealth(fillHealth ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }

    @Override
    protected MastermindBrain createMastermindBrain() { return new ForestEndermanBrain(this); }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // A day-and-night forest resident must not inherit idle sunlight escape.
        this.getBrain().tick(level, this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(HUNT_STATE, (byte) HuntState.IDLE.ordinal());
        data.define(CAMOUFLAGED, false);
        data.define(VINE_WARNING, false);
        data.define(VINE_WARMUP_TICKS, 0);
    }

    public HuntState getHuntState() {
        int index = this.entityData.get(HUNT_STATE);
        return index >= 0 && index < HUNT_STATES.length ? HUNT_STATES[index] : HuntState.IDLE;
    }

    public void setHuntState(HuntState state) {
        if (!this.level().isClientSide()) this.entityData.set(HUNT_STATE, (byte) state.ordinal());
    }

    public boolean isCamouflaged() { return this.entityData.get(CAMOUFLAGED); }
    public float getCamouflageAlpha() { return this.isCamouflaged() ? 0.42F : 1.0F; }
    public void setCamouflaged(boolean value) {
        if (!this.level().isClientSide()) this.entityData.set(CAMOUFLAGED, value);
    }
    public boolean isVineWindingUp() { return this.entityData.get(VINE_WARNING); }
    public void setVineWindingUp(boolean value) {
        if (!this.level().isClientSide()) {
            this.entityData.set(VINE_WARNING, value);
            this.entityData.set(VINE_WARMUP_TICKS, value ? ForestEndermanConfig.vinesWarmupTicks() : 0);
        }
    }
    public void setWarmupTicksRemaining(int ticks) {
        if (!this.level().isClientSide()) this.entityData.set(VINE_WARMUP_TICKS, Math.max(0, ticks));
    }
    public int getWarmupTicksRemaining() { return this.entityData.get(VINE_WARMUP_TICKS); }
    public int getBurstTicksRemaining() { return ((ForestEndermanBrain) this.getMastermindBrain()).getBurstTicksRemaining(); }
    public float getEyeBrightness() { return this.isVineWindingUp() ? 1.0F : this.isCamouflaged() ? 0.5F : 0.88F; }

    public boolean isInForest() {
        return this.level().dimension() == Level.OVERWORLD && this.level().getBiome(this.blockPosition()).is(SPAWN_BIOMES);
    }

    /** Safe shared teleportation; callers supply only legitimately observed/inferred destinations. */
    public boolean tryPursuitTeleport(Vec3 destination) { return !this.isVineWindingUp() && this.trySafeTeleport(destination, 4); }

    public void vinesWarning(ServerLevel level, LivingEntity target) {
        level.sendParticles(LEAF_WARNING, target.getX(), target.getY() + 0.1, target.getZ(), 6, 0.5, 0.05, 0.5, 0.0);
        this.playSound(SoundEvents.AZALEA_LEAVES_STEP, 0.6F, 0.7F);
    }

    public void vinesBurst(ServerLevel level, LivingEntity target) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.VINE.defaultBlockState()),
                target.getX(), target.getY() + 0.45, target.getZ(), 16, 0.35, 0.5, 0.35, 0.02);
        this.playSound(SoundEvents.ROOTED_DIRT_BREAK, 0.7F, 0.7F);
    }

    @Override
    protected float getSoundVolume() { return super.getSoundVolume() * (this.isCamouflaged() ? 0.25F : 1.0F); }

    @Override
    public void playStareSound() {
        // The native stare bypasses getSoundVolume; retain its cooldown while applying woodland quieting.
        if ((long) this.tickCount >= (long) this.lastForestStareSound + 400L) {
            this.lastForestStareSound = this.tickCount;
            if (!this.isSilent()) this.level().playLocalSound(this.getX(), this.getEyeY(), this.getZ(),
                    SoundEvents.ENDERMAN_STARE, this.getSoundSource(), this.isCamouflaged() ? 0.625F : 2.5F, 1.0F, false);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        ((ForestEndermanBrain) this.getMastermindBrain()).reveal();
        return super.doHurtTarget(level, target);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) ((ForestEndermanBrain) this.getMastermindBrain()).onHurt();
        return hurt;
    }

    @Override
    protected boolean teleport() { return !this.isVineWindingUp() && super.teleport(); }

    @Override
    public void travel(Vec3 input) {
        if (this.isVineWindingUp()) { this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO); return; }
        super.travel(input);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && (this.isNoAi() || !this.isAlive()))
            ((ForestEndermanBrain) this.getMastermindBrain()).suspendNatureStalker();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        ForestRoots.releaseOwnedBy(this);
        ((ForestEndermanBrain) this.getMastermindBrain()).suspendNatureStalker();
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.getMastermindBrain().clearEncounter();
        this.applyRuntimeAttributes(false);
    }

    public static boolean checkForestSpawnRules(EntityType<ForestEnderman> type, ServerLevelAccessor level,
                                              EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || level.getDifficulty() == Difficulty.PEACEFUL
                || !Mob.checkMobSpawnRules(type, level, reason, pos, random)) return false;
        for (int height = 0; height < 3; height++) {
            BlockPos cell = pos.above(height);
            var block = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, block, block.getFluidState(), type)) return false;
        }
        return true;
    }

    public static boolean isSpawnTime(ServerLevelAccessor level) { return level.getLevel().dimension() == Level.OVERWORLD; }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        return isSpawnTime(level) && level.getBiome(pos).is(SPAWN_BIOMES);
    }

    public enum HuntState { IDLE, STALKING, PURSUING, SEARCHING, CASTING }
}
