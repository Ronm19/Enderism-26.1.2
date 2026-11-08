package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.VoidEndermanConfig;
import com.ronm19.enderism.entity.ai.VoidEndermanBrain;
import com.ronm19.enderism.entity.ai.VoidSurvivalInstinct;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #021: physical melee and restrained positional control, with a separate void-survival passive. */
public final class VoidEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_void_enderman"));
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(VoidEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(VoidEnderman.class, EntityDataSerializers.LONG);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions VOID = new DustParticleOptions(0xA63CE5, .65F);
    private final VoidSurvivalInstinct survival;
    private boolean striking;

    public VoidEnderman(EntityType<? extends VoidEnderman> type, Level level) {
        super(type, level); this.survival = new VoidSurvivalInstinct(this); this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, VoidEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, VoidEndermanConfig.DEFAULT_DAMAGE).add(Attributes.MOVEMENT_SPEED, VoidEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.FOLLOW_RANGE, VoidEndermanConfig.DEFAULT_RANGE).add(Attributes.ATTACK_KNOCKBACK, 0);
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !VoidEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VoidEndermanConfig.health());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(VoidEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(VoidEndermanConfig.movementSpeed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(VoidEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected VoidEndermanBrain createMastermindBrain() { return new VoidEndermanBrain(this); }
    @Override public VoidEndermanBrain getMastermindBrain() { return (VoidEndermanBrain)super.getMastermindBrain(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)CombatPhase.IDLE.ordinal()); data.define(PHASE_START, 0L);
    }
    public CombatPhase getCombatPhase() { int index = this.entityData.get(PHASE); return index >= 0 && index < PHASES.length ? PHASES[index] : CombatPhase.IDLE; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public void setCombatPhase(CombatPhase phase) {
        if (!this.level().isClientSide() && phase != this.getCombatPhase()) {
            this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal());
        }
    }
    public float getEyeBrightness() {
        return switch (this.getCombatPhase()) {
            case IDLE -> .66F; case PURSUING, SEARCHING -> .86F; case RECOVERY -> .78F; default -> 1;
        };
    }
    public VoidSurvivalInstinct getVoidSurvival() { return this.survival; }
    public boolean isRecoveringFromVoid() { return this.survival != null && this.survival.isRecovering(); }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        return !this.striking && target instanceof LivingEntity victim
                && this.getMastermindBrain().tryBeginStrike(level, victim, level.getGameTime());
    }
    public boolean performAbyssalStrike(ServerLevel level, LivingEntity target) {
        if (this.striking || this.isRecoveringFromVoid() || !this.getMastermindBrain().isStrikeImpactFrame(target)
                || !this.canAttack(target) || !this.isWithinMeleeAttackRange(target) || !this.getSensing().hasLineOfSight(target)) return false;
        float before = target.getHealth() + target.getAbsorptionAmount(); boolean accepted;
        this.striking = true;
        try { accepted = super.doHurtTarget(level, target); } finally { this.striking = false; }
        if (!accepted || target.getHealth() + target.getAbsorptionAmount() >= before) return false;
        target.knockback(VoidEndermanConfig.strikeKnockback(), this.getX() - target.getX(), this.getZ() - target.getZ());
        level.sendParticles(VOID, target.getX(), target.getY(.5), target.getZ(), 7, .2, .25, .2, .025);
        this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, .6F, .75F); return true;
    }
    public void warningCue(ServerLevel level, boolean pull) {
        level.sendParticles(VOID, this.getX(), this.getY(.55), this.getZ(), pull ? 10 : 5, .3, .6, .3, .015);
        this.playSound(pull ? SoundEvents.ENDERMAN_STARE : SoundEvents.ENDERMAN_TELEPORT, .35F, pull ? .7F : .95F);
    }
    public void arrivalCue(ServerLevel level, Vec3 before) {
        level.sendParticles(VOID, before.x, before.y + 1.2, before.z, 9, .25, .6, .25, .025);
        level.sendParticles(VOID, this.getX(), this.getY(.4), this.getZ(), 9, .25, .6, .25, .025);
    }

    /** One local candidate at five possible nearby heights; the shared actual teleport revalidates event redirection. */
    public @Nullable Vec3 planVoidStepDestination(Vec3 requested) {
        if (!(this.level() instanceof ServerLevel level) || !requested.isFinite() || this.isNoAi() || this.isRecoveringFromVoid()
                || this.isPassenger() || this.isVehicle() || this.distanceToSqr(requested) > square(VoidEndermanConfig.stepRadius())) return null;
        for (int delta : new int[] {0, 1, -1, 2, -2}) {
            Vec3 position = new Vec3(requested.x, Math.floor(requested.y) + delta, requested.z);
            if (this.distanceToSqr(position) > square(VoidEndermanConfig.stepRadius())) continue;
            Vec3 valid = this.findSafeTeleportDestination(position, 0);
            if (valid != null && this.isStableVoidGround(level, valid)) return valid;
        }
        return null;
    }
    public boolean tryVoidStepTeleport(Vec3 requested) {
        if (!(this.level() instanceof ServerLevel level) || this.isRecoveringFromVoid() || this.getCombatPhase() != CombatPhase.STEP_WINDUP) return false;
        Vec3 valid = this.planVoidStepDestination(requested), before = this.position();
        if (valid == null || !this.trySafeTeleport(valid, 0)) return false;
        this.setDeltaMovement(Vec3.ZERO); this.resetFallDistance(); this.arrivalCue(level, before); return true;
    }
    @Override protected int teleportAttemptCooldownTicks() { return VoidEndermanConfig.stepCooldownTicks(); }
    @Override protected boolean teleport() { return false; }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        return this.isStableVoidGround(level, destination)
                && (this.getCombatPhase() != CombatPhase.STEP_WINDUP || this.distanceToSqr(destination) <= square(VoidEndermanConfig.stepRadius()));
    }
    public @Nullable Vec3 planRescueDestination(Vec3 requested, int downwardSearch) { return this.findSafeRecoveryDestination(requested, downwardSearch); }
    public boolean performRescueTeleport(Vec3 requested) { return this.trySafeRecoveryTeleport(requested, 0); }
    public boolean isStableVoidGround(ServerLevel level, Vec3 position) { return VoidSurvivalInstinct.isStableGround(this, level, position); }

    public boolean canSafelyPull(LivingEntity target) { return this.planPullImpulse(target) != null; }
    private @Nullable Vec3 planPullImpulse(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved() || this.isNoAi() || this.isRecoveringFromVoid()
                || !this.canAttack(target) || target.isRemoved() || target.level() != level || !target.isAlive()
                || target.isPassenger() || target.isVehicle() || target.isFallFlying() || target.isInWater() || target.isInLava()
                || !target.onGround() || Math.abs(target.getDeltaMovement().y) > .12 || target.getDeltaMovement().horizontalDistanceSqr() > .16
                || !this.getSensing().hasLineOfSight(target) || !this.isStableVoidGround(level, this.position())) return null;
        Vec3 toward = this.position().subtract(target.position()).multiply(1, 0, 1);
        double distance = toward.length();
        if (distance < 4.5 || distance > VoidEndermanConfig.pullRange() || Math.abs(this.getY() - target.getY()) > .75) return null;
        double resistance = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1);
        double power = VoidEndermanConfig.pullStrength() * (1 - resistance);
        if (power <= .001) return null;
        Vec3 impulse = toward.scale(power / distance);
        Vec3 resultant = target.getDeltaMovement().add(impulse).multiply(1, 0, 1);
        if (resultant.horizontalDistance() > .42) return null;
        double safetyDistance = Math.max(VoidEndermanConfig.pullSafetyDistance(), resultant.length() * 8 + 1);
        Vec3 travel = resultant.lengthSqr() > .0001 ? resultant.normalize().scale(safetyDistance) : impulse.normalize().scale(safetyDistance);
        int samples = Math.min(32, (int)Math.ceil(safetyDistance * 4));
        for (int i = 0; i <= samples; i++) {
            Vec3 point = target.position().add(travel.scale((double)i / samples));
            AABB body = target.getBoundingBox().move(point.subtract(target.position()));
            BlockPos low = BlockPos.containing(body.minX - .2, body.minY - 1, body.minZ - .2);
            BlockPos high = BlockPos.containing(body.maxX + .2, body.maxY, body.maxZ + .2);
            if (!VoidSurvivalInstinct.hasReadyChunks(level, low, high) || !level.getWorldBorder().isWithinBounds(body) || !level.noCollision(target, body)
                    || level.containsAnyLiquid(body)) return null;
            for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                    BlockPos.containing(body.maxX, body.maxY, body.maxZ))) if (hazard(level.getBlockState(cell))) return null;
            int floor = Mth.floor(body.minY - .01);
            for (int x = low.getX(); x <= high.getX(); x++) for (int z = low.getZ(); z <= high.getZ(); z++) {
                BlockPos support = new BlockPos(x, floor, z); BlockState state = level.getBlockState(support);
                if (!state.isFaceSturdy(level, support, Direction.UP) || hazard(state) || state.getFriction(level, support, target) > .8F) return null;
            }
        }
        return impulse;
    }
    public boolean applyAbyssalPull(ServerLevel level, LivingEntity target) {
        if (this.level() != level || !this.getMastermindBrain().isPullImpactFrame(target)) return false;
        Vec3 impulse = this.planPullImpulse(target);
        if (impulse == null) return false;
        target.push(impulse); target.hurtMarked = true;
        level.sendParticles(VOID, target.getX(), target.getY(.5), target.getZ(), 10, .3, .45, .3, .03);
        this.playSound(SoundEvents.ENDERMAN_TELEPORT, .5F, .65F); return true;
    }

    /** Checks only the immediate intended ground segment, never a long downward/world scan. */
    public boolean isSafeGroundSegment(Vec3 destination) {
        if (!(this.level() instanceof ServerLevel level)) return false;
        Vec3 horizontal = destination.subtract(this.position()).multiply(1, 0, 1);
        double distance = Math.min(1.5, horizontal.length());
        if (distance < .1 || !this.onGround()) return true;
        Vec3 step = horizontal.normalize().scale(distance);
        for (int i = 1; i <= 3; i++) {
            Vec3 point = this.position().add(step.scale(i / 3.0));
            AABB box = this.getBoundingBox().move(point.subtract(this.position()));
            BlockPos low = BlockPos.containing(box.minX, box.minY - 1, box.minZ), high = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            if (!VoidSurvivalInstinct.hasReadyChunks(level, low, high) || !level.getWorldBorder().isWithinBounds(box)) return false;
            // Nearby higher terrain is left to ground navigation; only unsupported drops are rejected here.
            for (int x = low.getX(); x <= high.getX(); x++) for (int z = low.getZ(); z <= high.getZ(); z++) {
                boolean supportFound = false;
                for (int y = Mth.floor(point.y); y >= Mth.floor(point.y) - 2; y--) {
                    BlockPos support = new BlockPos(x, y, z); BlockState state = level.getBlockState(support);
                    if (state.isFaceSturdy(level, support, Direction.UP) && !hazard(state)) { supportFound = true; break; }
                }
                if (!supportFound) return false;
            }
        }
        return true;
    }

    @Override public void aiStep() {
        if (this.level() instanceof ServerLevel level) {
            this.survival.tick(level);
            if ((!this.isAlive() || this.isNoAi()) && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter();
        }
        super.aiStep();
    }
    @Override protected void onBelowWorld() {
        if (this.level() instanceof ServerLevel level && this.isAlive() && !this.isRemoved()) this.survival.handleVoidDamage(level);
        else super.onBelowWorld();
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) && this.survival != null && this.survival.handleVoidDamage(level)) return false;
        return super.hurtServer(level, source, damage);
    }
    @Override public void die(DamageSource source) { super.die(source); if (!this.isAlive() && this.survival != null) this.survival.clear(); }
    @Override public void onRemoval(Entity.RemovalReason reason) {
        if (this.survival != null) this.survival.clear(); super.onRemoval(reason);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter();
        this.getMastermindBrain().readCooldowns(input); if (this.survival != null) this.survival.clear();
    }
    public static boolean isSpawnTime(ServerLevelAccessor level) { return level.getLevel().dimension() == Level.END; }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        if (!isSpawnTime(level) || level.getLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                || !level.getBiome(pos).is(SPAWN_BIOMES)) return false;
        return level.getLevel().getEntitiesOfClass(VoidEnderman.class, new AABB(pos).inflate(64),
                entity -> entity.isAlive() && !entity.isRemoved()).size() < VoidEndermanConfig.naturalPopulationCap();
    }
    public static boolean checkVoidSpawnRules(EntityType<VoidEnderman> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) return false;
        BlockState support = level.getBlockState(pos.below());
        if (!support.isFaceSturdy(level, pos.below(), Direction.UP) || support.getBlock() instanceof FallingBlock || hazard(support)) return false;
        for (int y = 0; y < Mth.ceil(type.getHeight()); y++) {
            BlockPos cell = pos.above(y); BlockState state = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, state, state.getFluidState(), type)) return false;
        }
        return true;
    }
    private static double square(double value) { return value * value; }
    private static boolean hazard(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE);
    }
    public enum CombatPhase {
        IDLE, PURSUING, SEARCHING, STRIKE_WINDUP, STRIKE, STEP_WINDUP, STEP, PULL_WINDUP, PULL, RECOVERY, VOID_RESCUE;
        public boolean isCommitted() { return this == STRIKE_WINDUP || this == STRIKE || this == STEP_WINDUP || this == STEP || this == PULL_WINDUP || this == PULL || this == RECOVERY || this == VOID_RESCUE; }
    }
}
