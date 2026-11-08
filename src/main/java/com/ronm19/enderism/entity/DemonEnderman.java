package com.ronm19.enderism.entity;

import com.ronm19.enderism.DemonEndermanConfig;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.effect.DemonAsh;
import com.ronm19.enderism.entity.ai.DemonEndermanBrain;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #015: grounded melee pressure, finite Onslaught, and damage-confirmed Ash. */
public final class DemonEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_demon_enderman"));
    private static final Identifier FRENZY_SPEED = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "demon_frenzy_speed");
    private static final Identifier FRENZY_DAMAGE = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "demon_frenzy_damage");
    private static final Identifier CLAW_BURST = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "demon_claw_burst");
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(DemonEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(DemonEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> FRENZY_END = SynchedEntityData.defineId(DemonEnderman.class, EntityDataSerializers.LONG);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(0xD32D37, 1.05F);
    private @Nullable Vec3 teleportObservation;
    private boolean striking;

    public DemonEnderman(EntityType<? extends DemonEnderman> type, Level level) {
        super(type, level);
        this.applyRuntimeAttributes(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes()
                .add(Attributes.MAX_HEALTH, DemonEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, DemonEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, DemonEndermanConfig.DEFAULT_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, DemonEndermanConfig.DEFAULT_RANGE);
    }

    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !DemonEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(DemonEndermanConfig.health());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(DemonEndermanConfig.speed());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(DemonEndermanConfig.damage());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(DemonEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }

    @Override protected DemonEndermanBrain createMastermindBrain() { return new DemonEndermanBrain(this); }
    @Override public DemonEndermanBrain getMastermindBrain() { return (DemonEndermanBrain) super.getMastermindBrain(); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(PHASE, (byte) CombatPhase.IDLE.ordinal());
        data.define(PHASE_START, 0L);
        data.define(FRENZY_END, 0L);
    }

    public CombatPhase getCombatPhase() { return PHASES[this.entityData.get(PHASE)]; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public long getFrenzyEnd() { return this.entityData.get(FRENZY_END); }
    public boolean isFrenzied() { return this.isAlive() && this.getFrenzyEnd() > this.level().getGameTime(); }

    public void setCombatPhase(CombatPhase phase) {
        if (!this.level().isClientSide() && this.getCombatPhase() != phase) {
            this.entityData.set(PHASE_START, this.level().getGameTime());
            this.entityData.set(PHASE, (byte) phase.ordinal());
        }
    }

    public float getEyeBrightness() {
        if (this.isFrenzied()) return 1.0F;
        return switch (this.getCombatPhase()) {
            case IDLE -> 0.62F;
            case SEARCHING, RECOVERY -> 0.76F;
            case PURSUIT -> 0.88F;
            default -> 1.0F;
        };
    }

    public void activateFrenzy(ServerLevel level) {
        this.entityData.set(FRENZY_END, level.getGameTime() + DemonEndermanConfig.frenzyDuration());
        this.updateFrenzyModifiers();
        this.playSound(SoundEvents.ENDERMAN_SCREAM, 0.95F, 0.64F);
        level.sendParticles(CRIMSON, this.getX(), this.getY(0.55), this.getZ(), 18, 0.5, 0.8, 0.5, 0.04);
        level.sendParticles(ParticleTypes.ASH, this.getX(), this.getY(0.55), this.getZ(), 10, 0.5, 0.8, 0.5, 0.02);
    }

    public void endFrenzy() {
        if (this.level().isClientSide()) return;
        this.entityData.set(FRENZY_END, 0L);
        this.updateFrenzyModifiers();
    }

    private void updateFrenzyModifiers() {
        boolean active = this.isFrenzied() && !this.isNoAi() && !this.isRemoved();
        updateModifier(this.getAttribute(Attributes.MOVEMENT_SPEED), FRENZY_SPEED,
                active ? DemonEndermanConfig.frenzySpeedBonus() : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        updateModifier(this.getAttribute(Attributes.ATTACK_DAMAGE), FRENZY_DAMAGE,
                active ? DemonEndermanConfig.frenzyDamageBonus() : 0.0, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void updateModifier(net.minecraft.world.entity.ai.attributes.AttributeInstance attribute,
                                       Identifier id, double amount, AttributeModifier.Operation operation) {
        var previous = attribute.getModifier(id);
        if (previous != null && (amount == 0.0 || previous.amount() != amount)) attribute.removeModifier(id);
        if (amount != 0.0 && !attribute.hasModifier(id)) attribute.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }

    @Override public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().bracesCaster()) {
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            super.travel(Vec3.ZERO);
        } else super.travel(input);
    }

    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.striking || !(target instanceof LivingEntity victim)
                || !this.getMastermindBrain().canUseClaws(level, victim)) return false;
        this.getMastermindBrain().onClawAttempt(level);
        DemonAsh.Hit ash = DemonAsh.begin(victim, this);
        var damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        double bonus = (ash == null ? 0.0 : ash.bonusDamage())
                + (this.getCombatPhase() == CombatPhase.ONSLAUGHT ? DemonEndermanConfig.onslaughtDamageBonus() : 0.0);
        float before = victim.getHealth() + victim.getAbsorptionAmount();
        boolean acceptedDamage = false;
        boolean hit;
        this.striking = true;
        try {
            if (bonus > 0.0) damage.addTransientModifier(new AttributeModifier(CLAW_BURST, bonus, AttributeModifier.Operation.ADD_VALUE));
            hit = super.doHurtTarget(level, victim);
            acceptedDamage = hit && victim.getHealth() + victim.getAbsorptionAmount() < before;
        } finally {
            damage.removeModifier(CLAW_BURST);
            this.striking = false;
            DemonAsh.finish(ash, acceptedDamage);
        }
        if (acceptedDamage) {
            this.getMastermindBrain().onClawConnected();
            if (this.getCombatPhase() != CombatPhase.ONSLAUGHT) this.setCombatPhase(CombatPhase.CLAWS);
            level.sendParticles(CRIMSON, victim.getX(), victim.getY(0.55), victim.getZ(), 5, 0.22, 0.30, 0.22, 0.02);
            this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.65F, 0.80F);
        }
        return hit;
    }

    public void beginAbilityWarning(ServerLevel level, boolean frenzy) {
        this.playSound(SoundEvents.ENDERMAN_STARE, frenzy ? 0.85F : 0.6F, frenzy ? 0.58F : 0.85F);
        this.abilityWindup(level, frenzy);
    }

    public void abilityWindup(ServerLevel level, boolean frenzy) {
        level.sendParticles(CRIMSON, this.getX(), this.getY(0.65), this.getZ(), frenzy ? 7 : 4, 0.35, 0.45, 0.35, 0.02);
    }

    @Override protected int teleportAttemptCooldownTicks() {
        return this.isFrenzied() ? DemonEndermanConfig.frenzyTeleportCooldown() : DemonEndermanConfig.teleportCooldown();
    }

    @Override protected boolean teleport() {
        return !this.getCombatPhase().bracesCaster() && this.getCombatPhase() != CombatPhase.ONSLAUGHT && super.teleport();
    }

    public boolean tryTacticalTeleport(Vec3 requested, Vec3 observed) {
        if (!(this.level() instanceof ServerLevel level) || this.getCombatPhase().bracesCaster()
                || this.getCombatPhase() == CombatPhase.ONSLAUGHT || this.isNoAi() || !nearObservation(requested, observed)) return false;
        Vec3 origin = this.position();
        this.teleportObservation = observed;
        try {
            if (!this.trySafeTeleport(requested, 4)) return false;
        } finally { this.teleportObservation = null; }
        level.sendParticles(ParticleTypes.ASH, origin.x, origin.y + 1.4, origin.z, 5, 0.25, 0.4, 0.25, 0.01);
        return true;
    }

    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        return this.teleportObservation == null || nearObservation(destination, this.teleportObservation);
    }

    private static boolean nearObservation(Vec3 destination, Vec3 observed) {
        double distance = destination.subtract(observed).horizontalDistanceSqr();
        return distance >= 2.25 * 2.25 && distance <= 6.0 * 6.0 && Math.abs(destination.y - observed.y) <= 3.0;
    }

    @Override public void aiStep() {
        if (!this.level().isClientSide()) {
            if ((this.isNoAi() || !this.isAlive()) && (this.getCombatPhase() != CombatPhase.IDLE || this.getFrenzyEnd() != 0L)) {
                this.getMastermindBrain().clearEncounter();
            }
            if (this.getFrenzyEnd() != 0L && !this.isFrenzied()) this.endFrenzy();
            this.updateFrenzyModifiers();
            if (this.isFrenzied() && this.tickCount % 12 == 0 && this.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ASH, this.getX(), this.getY(0.6), this.getZ(), 2, 0.3, 0.6, 0.3, 0.01);
            }
        }
        super.aiStep();
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter();
    }

    @Override public void onRemoval(Entity.RemovalReason reason) {
        if (!this.level().isClientSide() && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter();
        super.onRemoval(reason);
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.getMastermindBrain().writeCooldowns(output);
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.applyRuntimeAttributes(false);
        this.getMastermindBrain().clearEncounter();
        this.getMastermindBrain().readCooldowns(input);
    }

    public static boolean checkDemonSpawnRules(EntityType<DemonEnderman> type, ServerLevelAccessor level,
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

    public static boolean isSpawnTime(ServerLevelAccessor level) { return level.getLevel().dimension() == Level.NETHER; }

    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        return isSpawnTime(level) && (level.getBiome(pos).is(Biomes.CRIMSON_FOREST) || level.getBiome(pos).is(Biomes.NETHER_WASTES));
    }

    public enum CombatPhase {
        IDLE, PURSUIT, CLAWS, ONSLAUGHT_WINDUP, ONSLAUGHT, FRENZY_WINDUP, RECOVERY, SEARCHING;

        public boolean bracesCaster() { return this == ONSLAUGHT_WINDUP || this == FRENZY_WINDUP || this == RECOVERY; }
    }
}
