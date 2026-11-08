package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.InfernalEndermanConfig;
import com.ronm19.enderism.effect.InfernalBurn;
import com.ronm19.enderism.entity.ai.InfernalEndermanBrain;
import com.ronm19.enderism.entity.ai.InfernalLavaNavigation;
import com.ronm19.enderism.entity.ai.MastermindBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;

/** #012: heavy melee and lava-surface locomotion with one native Brain decision owner. */
public final class InfernalEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_infernal_enderman"));
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(
            InfernalEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(
            InfernalEnderman.class, EntityDataSerializers.LONG);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final VoxelShape LAVA_SURFACE = Block.column(16.0, 0.0, InfernalLavaNavigation.SOURCE_SURFACE_HEIGHT * 16.0);
    private boolean onLavaSurface;

    public InfernalEnderman(EntityType<? extends InfernalEnderman> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.FIRE, 0.0F);
        this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0F);
        this.applyRuntimeAttributes(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes()
                .add(Attributes.MAX_HEALTH, InfernalEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, InfernalEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.FOLLOW_RANGE, InfernalEndermanConfig.DEFAULT_RANGE)
                .add(Attributes.ATTACK_DAMAGE, InfernalEndermanConfig.DEFAULT_DAMAGE)
                .add(Attributes.ATTACK_KNOCKBACK, InfernalEndermanConfig.DEFAULT_KNOCKBACK);
    }

    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !InfernalEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(InfernalEndermanConfig.health());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(InfernalEndermanConfig.speed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(InfernalEndermanConfig.followRange());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(InfernalEndermanConfig.damage());
        this.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(InfernalEndermanConfig.knockback());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }

    @Override
    protected MastermindBrain createMastermindBrain() {
        return new InfernalEndermanBrain(this);
    }

    @Override
    public InfernalEndermanBrain getMastermindBrain() {
        return (InfernalEndermanBrain) super.getMastermindBrain();
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new InfernalLavaNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(PHASE, (byte) CombatPhase.IDLE.ordinal());
        data.define(PHASE_START, 0L);
    }

    public CombatPhase getCombatPhase() {
        int ordinal = this.entityData.get(PHASE);
        return ordinal >= 0 && ordinal < PHASES.length ? PHASES[ordinal] : CombatPhase.IDLE;
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
            case IDLE -> 0.63F;
            case SEARCHING, RECOVERY -> 0.78F;
            case PURSUIT, LAVA_RUNNING -> 0.90F;
            default -> 1.0F;
        };
    }

    public boolean hasActiveCataclysm() {
        return this.getMastermindBrain().hasActiveCataclysm();
    }

    public boolean canDamageWithInfernalAbility(LivingEntity target) {
        return target != this && !(target instanceof InfernalEnderman) && target.level() == this.level()
                && target.isAlive() && !target.isRemoved() && this.canAttack(target) && !this.isAlliedTo(target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()))
                && !(target instanceof ServerPlayer player && player.hasDisconnected());
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (target instanceof LivingEntity living && !this.canDamageWithInfernalAbility(living)) return false;
        this.setCombatPhase(CombatPhase.RAMPAGE);
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            if (target instanceof LivingEntity living) InfernalBurn.apply(living);
            level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY(0.55), target.getZ(),
                    11, 0.32, 0.42, 0.32, 0.065);
            this.playSound(SoundEvents.RAVAGER_ATTACK, 0.65F, 0.82F);
        }
        return hit;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return source.is(DamageTypes.DROWN)
                || source.getDirectEntity() instanceof AbstractThrownPotion potion
                        && potion.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)
                || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean canStandOnFluid(FluidState fluid) {
        return fluid.is(FluidTags.LAVA);
    }

    @Override
    public VoxelShape getLiquidCollisionShape() {
        return LAVA_SURFACE;
    }

    @Override
    public boolean isPushedByFluid(FluidType fluidType) {
        return fluidType != NeoForgeMod.LAVA_TYPE.value() && super.isPushedByFluid(fluidType);
    }

    public boolean isOnLavaSurface() {
        return this.onLavaSurface;
    }

    @Override
    public void travel(Vec3 input) {
        if (!(this.level() instanceof ServerLevel level)) {
            super.travel(input);
            return;
        }
        this.updateLavaSupport();
        if (this.getCombatPhase() == CombatPhase.CHARGING) {
            Vec3 before = this.position();
            Vec3 step = this.getMastermindBrain().prepareChargeStep(level);
            Vec3 velocity = this.getDeltaMovement();
            this.setDeltaMovement(step.x, Math.min(0, velocity.y), step.z);
            super.travel(Vec3.ZERO);
            this.updateLavaSupport();
            this.getMastermindBrain().finishChargeStep(level, before);
        } else {
            CombatPhase phase = this.getCombatPhase();
            boolean braced = phase == CombatPhase.CHARGE_WINDUP || phase == CombatPhase.RECOVERY
                    || this.hasActiveCataclysm();
            if (braced) {
                Vec3 velocity = this.getDeltaMovement();
                this.setDeltaMovement(0, velocity.y, 0);
            }
            super.travel(braced ? Vec3.ZERO : input);
            this.updateLavaSupport();
        }
    }

    private void updateLavaSupport() {
        this.onLavaSurface = false;
        if (this.isPassenger() || !this.isAlive()) return;
        BlockPos feet = this.blockPosition();
        if (!this.level().hasChunkAt(feet)) return;
        BlockPos lava = this.level().getFluidState(feet).is(FluidTags.LAVA) ? feet : feet.below();
        double surface = InfernalLavaNavigation.surfaceHeight(this.level(), lava);
        if (!Double.isFinite(surface)) {
            // A mob loaded or knocked deep into a lava column swims up until it reaches a real surface.
            if (this.level().getFluidState(feet).is(FluidTags.LAVA)) {
                Vec3 motion = this.getDeltaMovement();
                this.setDeltaMovement(motion.x, Math.max(0.13, motion.y), motion.z);
                this.resetFallDistance();
            }
            return;
        }
        double rise = surface - this.getY();
        Vec3 motion = this.getDeltaMovement();
        if (rise > 0.0001 && rise <= 1.0) {
            this.move(MoverType.SELF, new Vec3(0, Math.min(rise, 0.35), 0));
        }
        // Release gravity as soon as the mob leaves support. The tolerance only corrects settling.
        if (Math.abs(surface - this.getY()) < 0.001 && motion.y <= 0.001) {
            this.setOnGround(true);
            this.setDeltaMovement(motion.x, 0, motion.z);
            this.resetFallDistance();
            this.onLavaSurface = true;
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide() && (this.isNoAi() || !this.isAlive())
                && this.getCombatPhase() != CombatPhase.IDLE) {
            this.getMastermindBrain().clearEncounter();
        }
        super.aiStep();
        if (this.level().isClientSide() && this.isAlive()
                && (this.getCombatPhase() == CombatPhase.CHARGING || this.getCombatPhase() == CombatPhase.LAVA_RUNNING)
                && this.tickCount % 3 == 0) {
            this.level().addParticle(ParticleTypes.SMALL_FLAME, this.getRandomX(0.8), this.getY() + 0.12,
                    this.getRandomZ(0.8), 0.0, 0.025, 0.0);
        }
    }

    @Override
    protected boolean teleport() {
        boolean moved = super.teleport();
        if (moved && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter();
        return moved;
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
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.applyRuntimeAttributes(false);
        this.getMastermindBrain().clearEncounter();
        this.getMastermindBrain().restoreCooldowns(input.getLongOr("InfernalChargeReadyAt", 0L),
                input.getLongOr("InfernalCataclysmReadyAt", 0L));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putLong("InfernalChargeReadyAt", this.getMastermindBrain().chargeCooldownDeadline());
        output.putLong("InfernalCataclysmReadyAt", this.getMastermindBrain().cataclysmCooldownDeadline());
    }

    public static boolean isSpawnTime(ServerLevelAccessor level) {
        return level.getLevel().dimension() == Level.NETHER;
    }

    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        return isSpawnTime(level) && level.getBiome(pos).is(SPAWN_BIOMES);
    }

    public static boolean checkInfernalSpawnRules(EntityType<InfernalEnderman> type, ServerLevelAccessor level,
                                                  EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)
                || !level.getFluidState(pos.below()).isEmpty()) return false;
        for (int height = 0; height < Mth.ceil(type.getHeight()); height++) {
            BlockPos cell = pos.above(height);
            var state = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, state, state.getFluidState(), type)) return false;
        }
        return true;
    }

    public enum CombatPhase {
        IDLE, PURSUIT, LAVA_RUNNING, RAMPAGE, CHARGE_WINDUP, CHARGING,
        METEOR_PREP, METEOR_BARRAGE, METEOR_FINAL, RECOVERY, SEARCHING
    }
}
