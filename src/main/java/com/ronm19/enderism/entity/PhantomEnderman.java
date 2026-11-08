package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.PhantomEndermanConfig;
import com.ronm19.enderism.entity.ai.MastermindBrain;
import com.ronm19.enderism.entity.ai.PhantomEndermanBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
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

/** #010: a grounded male mountain stalker. Stealth and positioning never disable gravity. */
public final class PhantomEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_phantom_enderman"));
    public static final Identifier NIGHT_SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "phantom_nocturnal_speed");
    public static final Identifier BACKSTAB_MODIFIER_ID = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "phantom_backstab_damage");
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(PhantomEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> APPROACH_END = SynchedEntityData.defineId(PhantomEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> CURSE_END = SynchedEntityData.defineId(PhantomEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> REAR_ATTACK = SynchedEntityData.defineId(PhantomEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final HuntState[] STATES = HuntState.values();
    private static final DustParticleOptions SPECTRAL = new DustParticleOptions(0xA68CE2, 0.65F);
    private long nextBackstabAt;
    private int successfulHits;
    private int successfulRearHits;
    private boolean processingMelee;
    private @Nullable Vec3 approachObservation;

    public PhantomEnderman(EntityType<? extends PhantomEnderman> type, Level level) {
        super(type, level);
        this.applyRuntimeAttributes(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, PhantomEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, PhantomEndermanConfig.DEFAULT_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, PhantomEndermanConfig.DEFAULT_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, PhantomEndermanConfig.DEFAULT_FOLLOW_RANGE);
    }

    private void applyRuntimeAttributes(boolean fillHealth) {
        if (this.level().isClientSide() || !PhantomEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(PhantomEndermanConfig.health());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(PhantomEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(PhantomEndermanConfig.movementSpeed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(PhantomEndermanConfig.followRange());
        this.setHealth(fillHealth ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }

    @Override protected MastermindBrain createMastermindBrain() { return new PhantomEndermanBrain(this); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(HUNT_STATE, (byte) HuntState.IDLE.ordinal());
        data.define(APPROACH_END, 0L);
        data.define(CURSE_END, 0L);
        data.define(REAR_ATTACK, false);
    }

    public HuntState getHuntState() {
        int index = this.entityData.get(HUNT_STATE);
        return index >= 0 && index < STATES.length ? STATES[index] : HuntState.IDLE;
    }
    public void setHuntState(HuntState state) {
        if (!this.level().isClientSide()) this.entityData.set(HUNT_STATE, (byte) state.ordinal());
    }
    public boolean isStalking() { return this.getHuntState() == HuntState.STALKING || this.getHuntState() == HuntState.POSITIONING; }
    public boolean isNightPredator() {
        long time = Math.floorMod(this.level().getOverworldClockTime(), 24000L);
        return time >= 13000L && time < 23000L;
    }
    public void setApproachWarmupEnd(long end) {
        if (!this.level().isClientSide()) this.entityData.set(APPROACH_END, end);
    }
    public void setCurseWarningEnd(long end) {
        if (!this.level().isClientSide()) this.entityData.set(CURSE_END, end);
    }
    public int getWarmupTicksRemaining() { return (int) Math.max(0L, this.entityData.get(APPROACH_END) - this.level().getGameTime()); }
    public int getCursePendingTicks() { return (int) Math.max(0L, this.entityData.get(CURSE_END) - this.level().getGameTime()); }
    public boolean isRearAttackAnimation() { return this.entityData.get(REAR_ATTACK); }
    public float getEyeBrightness() {
        if (this.getCursePendingTicks() > 0 || this.getWarmupTicksRemaining() > 0) return 1.0F;
        if (this.getHuntState() != HuntState.IDLE) return this.isNightPredator() ? .98F : .86F;
        return this.isNightPredator() ? .84F : .62F;
    }
    public void clearAbilityVisuals() {
        if (!this.level().isClientSide()) {
            this.entityData.set(APPROACH_END, 0L);
            this.entityData.set(CURSE_END, 0L);
            this.entityData.set(REAR_ATTACK, false);
            this.setHuntState(HuntState.IDLE);
        }
    }
    public int getSuccessfulHitCount() { return this.successfulHits; }
    public int getSuccessfulRearHitCount() { return this.successfulRearHits; }
    public long nextBackstabReadyAt() { return this.nextBackstabAt; }

    /** Uses server yaw, independent of camera pitch and client rendering. */
    public static Vec3 horizontalFacing(LivingEntity target) { return Vec3.directionFromRotation(0.0F, target.getYRot()); }
    public boolean isBehindTarget(LivingEntity target) {
        Vec3 towardPhantom = this.position().subtract(target.position()).multiply(1, 0, 1);
        return towardPhantom.lengthSqr() > .0001
                && horizontalFacing(target).dot(towardPhantom.normalize()) <= PhantomEndermanConfig.rearDotThreshold();
    }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.processingMelee || !this.isAlive() || this.isRemoved() || this.isNoAi()
                || this.getWarmupTicksRemaining() > 0 || !(target instanceof LivingEntity living)
                || !this.isWithinMeleeAttackRange(living) || !this.getSensing().hasLineOfSight(living)) return false;
        boolean backstab = level.getGameTime() >= this.nextBackstabAt && this.isBehindTarget(living);
        var damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        this.processingMelee = true;
        boolean hit;
        try {
            if (backstab) damage.addTransientModifier(new AttributeModifier(BACKSTAB_MODIFIER_ID,
                    PhantomEndermanConfig.backstabMultiplier() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            // A single vanilla damage event owns armor, shields, invulnerability frames and enchantments.
            hit = super.doHurtTarget(level, target);
        } finally {
            if (backstab) damage.removeModifier(BACKSTAB_MODIFIER_ID);
            this.processingMelee = false;
        }
        if (hit) {
            this.successfulHits++;
            if (backstab) {
                this.successfulRearHits++;
                this.nextBackstabAt = level.getGameTime() + PhantomEndermanConfig.backstabCooldownTicks();
            }
            this.entityData.set(REAR_ATTACK, backstab);
            level.sendParticles(SPECTRAL, living.getX(), living.getY() + living.getBbHeight() * .55, living.getZ(),
                    backstab ? 8 : 4, .2, .3, .2, .025);
            this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, backstab ? .65F : .4F, backstab ? .75F : 1.1F);
            ((PhantomEndermanBrain) this.getMastermindBrain()).onSuccessfulMelee(level, living, backstab);
        }
        return hit;
    }

    public void warnSilentApproach(ServerLevel level) {
        level.sendParticles(SPECTRAL, this.getX(), this.getY() + 1.1, this.getZ(), 5, .2, .5, .2, .01);
        this.playSound(SoundEvents.ENDERMAN_TELEPORT, .25F, .85F);
    }
    public void warnCurse(ServerLevel level, ServerPlayer target) {
        level.sendParticles(SPECTRAL, target.getX(), target.getY() + 1.0, target.getZ(), 6, .25, .4, .25, .01);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENDERMAN_STARE,
                this.getSoundSource(), .35F, .65F);
    }
    public void activateCurse(ServerLevel level, ServerPlayer target) {
        level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 1.0, target.getZ(), 9, .3, .55, .3, .015);
        this.playSound(SoundEvents.ENDERMAN_SCREAM, .3F, .75F);
    }

    /** The Brain supplies a currently observed point; preflight never reads a hidden target. */
    public @Nullable Vec3 findSilentApproachDestination(Vec3 requested, Vec3 observed) {
        if (!(this.level() instanceof ServerLevel level) || !finite(requested) || !finite(observed)
                || !this.isAlive() || this.isRemoved() || this.isNoAi() || this.isInWater()
                || this.distanceToSqr(observed) > square(PhantomEndermanConfig.approachRange())) return null;
        for (int offset : new int[]{0, 1, -1, 2, -2}) {
            Vec3 standing = new Vec3(requested.x, BlockPos.containing(requested.x, observed.y, requested.z).getY() + offset, requested.z);
            if (this.validApproachStanding(level, standing, observed)) return standing;
        }
        return null;
    }

    private boolean validApproachStanding(ServerLevel level, Vec3 standing, Vec3 observed) {
        if (!finite(standing) || !finite(observed) || Math.abs(standing.y - observed.y) > 2.0
                || standing.y <= level.getMinY() || standing.y + this.getBbHeight() >= level.getMaxY()
                || this.distanceToSqr(observed) > square(PhantomEndermanConfig.approachRange())) return false;
        double dx = standing.x - observed.x, dz = standing.z - observed.z;
        if (dx * dx + dz * dz < 4.0 || dx * dx + dz * dz > square(PhantomEndermanConfig.flankDistance() + 1.5)) return false;
        AABB box = this.getBoundingBox().move(standing.subtract(this.position()));
        BlockPos low = BlockPos.containing(Math.min(observed.x, box.minX), Math.min(observed.y, box.minY) - 1,
                Math.min(observed.z, box.minZ));
        BlockPos high = BlockPos.containing(Math.max(observed.x, box.maxX), Math.max(observed.y + this.getEyeHeight(), box.maxY),
                Math.max(observed.z, box.maxZ));
        if (!level.getWorldBorder().isWithinBounds(box) || !level.hasChunksAt(low, high)) return false;
        BlockState support = level.getBlockState(BlockPos.containing(standing).below());
        if (!support.blocksMotion() || support.is(BlockTags.LEAVES) || isApproachHazard(support)
                || !level.noCollision(this, box) || level.containsAnyLiquid(box)) return false;
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = level.getBlockState(cell);
            if (state.is(BlockTags.LEAVES) || isApproachHazard(state)) return false;
        }
        return level.clip(new ClipContext(observed.add(0, 1.6, 0), standing.add(0, this.getEyeHeight(), 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    public boolean trySilentApproachTeleport(Vec3 requested, Vec3 observed) {
        if (!(this.level() instanceof ServerLevel level)) return false;
        Vec3 standing = this.findSilentApproachDestination(requested, observed);
        if (standing == null) return false;
        Vec3 old = this.position();
        this.approachObservation = observed;
        boolean moved;
        try { moved = this.trySafeTeleport(standing, 0); }
        finally { this.approachObservation = null; }
        if (!moved) return false;
        level.sendParticles(SPECTRAL, old.x, old.y + 1.1, old.z, 4, .2, .5, .2, .015);
        level.sendParticles(SPECTRAL, this.getX(), this.getY() + 1.1, this.getZ(), 4, .2, .5, .2, .015);
        return true;
    }

    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        // This hook also validates destinations redirected by another mod's EnderTeleport listener.
        if (this.approachObservation != null) return this.validApproachStanding(level, destination, this.approachObservation);
        BlockPos feet = BlockPos.containing(destination);
        return level.hasChunkAt(feet) && !level.getBlockState(feet.below()).is(BlockTags.LEAVES);
    }
    private static boolean finite(Vec3 value) { return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z); }
    private static double square(double value) { return value * value; }
    private static boolean isApproachHazard(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE);
    }

    @Override public void travel(Vec3 input) {
        if (this.getWarmupTicksRemaining() > 0) {
            Vec3 velocity = this.getDeltaMovement();
            this.setDeltaMovement(0.0, velocity.y, 0.0);
            input = new Vec3(0.0, input.y, 0.0);
        }
        // Always run native ground travel, including gravity and ordinary falling during a warning.
        super.travel(input);
    }
    @Override protected boolean teleport() { return this.getWarmupTicksRemaining() == 0 && super.teleport(); }
    @Override protected void customServerAiStep(ServerLevel level) {
        // Daylight is part of this species' habitat; native idle sunlight escape is inappropriate.
        this.getBrain().tick(level, this);
    }
    @Override public void playAmbientSound() {
        if (this.isStalking()) this.playSound(this.getAmbientSound(), .2F, this.getVoicePitch());
        else super.playAmbientSound();
    }
    @Override protected void playStepSound(BlockPos position, BlockState state) {
        if (this.isStalking()) state.playStepSound(this.level(), position, this, .04F, 1.0F);
        else super.playStepSound(position, state);
    }

    private void updateNocturnalSpeed() {
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        double amount = PhantomEndermanConfig.nightMovementBonus();
        boolean active = this.isAlive() && !this.isNoAi() && this.isNightPredator() && amount > 0;
        var current = speed.getModifier(NIGHT_SPEED_MODIFIER_ID);
        if (current != null && (!active || current.amount() != amount)) speed.removeModifier(NIGHT_SPEED_MODIFIER_ID);
        if (active && !speed.hasModifier(NIGHT_SPEED_MODIFIER_ID)) speed.addTransientModifier(new AttributeModifier(
                NIGHT_SPEED_MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    @Override public void aiStep() {
        if (!this.level().isClientSide()) this.updateNocturnalSpeed();
        super.aiStep();
        if (!this.level().isClientSide() && (!this.isAlive() || this.isNoAi())) this.getMastermindBrain().clearEncounter();
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            ((PhantomEndermanBrain) this.getMastermindBrain()).interruptApproach();
            if (!this.isAlive()) this.getMastermindBrain().clearEncounter();
        }
        return hurt;
    }
    @Override public void setNoAi(boolean noAi) {
        super.setNoAi(noAi);
        if (noAi && !this.level().isClientSide() && this.getMastermindBrain() != null) {
            this.getMastermindBrain().clearEncounter();
            this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(NIGHT_SPEED_MODIFIER_ID);
        }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.getMastermindBrain().clearEncounter();
        this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(BACKSTAB_MODIFIER_ID);
        this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(NIGHT_SPEED_MODIFIER_ID);
        this.applyRuntimeAttributes(false);
    }

    @Override public void onRemoval(Entity.RemovalReason reason) {
        // Native dimension transfer calls setRemoved directly, bypassing AbstractEnderman.remove.
        if (!this.level().isClientSide() && this.getMastermindBrain() != null) {
            this.getMastermindBrain().clearEncounter();
            this.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(BACKSTAB_MODIFIER_ID);
            this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(NIGHT_SPEED_MODIFIER_ID);
        }
        super.onRemoval(reason);
    }

    public static boolean checkPhantomSpawnRules(EntityType<PhantomEnderman> type, ServerLevelAccessor level,
                                               EntitySpawnReason reason, BlockPos position, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        // Native any-light monster checks preserve difficulty and support checks without a darkness gate.
        if (!isSpawnHabitat(level, position) || !Monster.checkAnyLightMonsterSpawnRules(type, level, reason, position, random)) return false;
        for (int height = 0; height < 3; height++) {
            BlockPos cell = position.above(height);
            BlockState state = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, state, state.getFluidState(), type)) return false;
        }
        return true;
    }
    public static boolean isSpawnTime(ServerLevelAccessor level) { return level.getLevel().dimension() == Level.OVERWORLD; }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos position) {
        return isSpawnTime(level) && level.getBiome(position).is(SPAWN_BIOMES);
    }
    public enum HuntState { IDLE, STALKING, POSITIONING, AMBUSHING, ATTACKING, CURSING, PURSUING, SEARCHING }
}
