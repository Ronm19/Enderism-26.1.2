package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.SpectralEndermanConfig;
import com.ronm19.enderism.entity.ai.SpectralEndermanBrain;
import com.ronm19.enderism.entity.ai.SpectralAmbush;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #017: a real, collidable huntress whose brief visual fade never changes damage rules. */
public final class SpectralEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_spectral_enderman"));
    private static final Identifier REND_BONUS = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spectral_rend_bonus");
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(SpectralEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(SpectralEnderman.class, EntityDataSerializers.LONG);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions GHOST = new DustParticleOptions(0xB3A4DD, .85F);
    private @Nullable LivingEntity teleportTarget;
    private boolean striking;

    public SpectralEnderman(EntityType<? extends SpectralEnderman> type, Level level) {
        super(type, level); this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, SpectralEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, SpectralEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, SpectralEndermanConfig.DEFAULT_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, SpectralEndermanConfig.DEFAULT_RANGE);
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !SpectralEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SpectralEndermanConfig.health());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SpectralEndermanConfig.speed());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(SpectralEndermanConfig.damage());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SpectralEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected SpectralEndermanBrain createMastermindBrain() { return new SpectralEndermanBrain(this); }
    @Override public SpectralEndermanBrain getMastermindBrain() { return (SpectralEndermanBrain)super.getMastermindBrain(); }
    public SpectralAmbush getAmbush() { return this.getMastermindBrain().ambush(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)CombatPhase.IDLE.ordinal()); data.define(PHASE_START, 0L);
    }
    public CombatPhase getCombatPhase() {
        int phase = this.entityData.get(PHASE); return phase >= 0 && phase < PHASES.length ? PHASES[phase] : CombatPhase.IDLE;
    }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public void setCombatPhase(CombatPhase phase) {
        if (!this.level().isClientSide() && this.getCombatPhase() != phase) {
            this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal());
        }
    }
    public static float visualOpacity(CombatPhase phase, float age) {
        return switch (phase) {
            case TRANSITION -> Mth.lerp(Mth.clamp(age / SpectralEndermanConfig.transitionTicks(), 0, 1), 1, .24F);
            case MATERIALIZING -> Mth.lerp(Mth.clamp(age / SpectralEndermanConfig.materializationTicks(), 0, 1), .24F, 1);
            default -> 1;
        };
    }
    public float getVisualOpacity(float partialTick) {
        return visualOpacity(this.getCombatPhase(), Math.max(0, this.level().getGameTime() - this.getCombatPhaseStart() + partialTick));
    }
    public float getEyeBrightness() {
        return switch (this.getCombatPhase()) {
            case IDLE -> .58F;
            case OBSERVING, STALKING, SEARCHING -> .72F;
            case RECOVERY -> .8F;
            default -> 1;
        };
    }
    /** The caller must still perform the real shared teleport, which revalidates event redirects. */
    public @Nullable Vec3 findMaterializationDestination(Vec3 requested) {
        if (!(this.level() instanceof ServerLevel) || !this.isAlive() || this.isRemoved() || this.isNoAi()
                || this.position().distanceToSqr(requested) > square(SpectralEndermanConfig.teleportRange())) return null;
        return this.findSafeTeleportDestination(requested, 3);
    }
    public boolean tryMaterializationTeleport(Vec3 requested, LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved() || this.isNoAi()
                || target.level() != level || !target.isAlive() || target.isRemoved()
                || this.position().distanceToSqr(requested) > square(SpectralEndermanConfig.teleportRange())) return false;
        this.teleportTarget = target;
        Vec3 origin = this.position();
        try {
            if (!this.trySafeTeleport(requested, 3)) return false;
        } finally { this.teleportTarget = null; }
        level.sendParticles(GHOST, origin.x, origin.y + 1.3, origin.z, 6, .3, .5, .3, .025);
        level.sendParticles(GHOST, this.getX(), this.getY(0.5), this.getZ(), 9, .3, .7, .3, .03);
        return true;
    }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        if (this.teleportTarget == null) return true;
        LivingEntity target = this.teleportTarget;
        double range = destination.subtract(target.position()).horizontalDistanceSqr();
        return !this.isNoAi() && target.isAlive() && !target.isRemoved() && target.level() == level
                && range >= .9 * .9 && range <= 2.0 * 2.0 && Math.abs(destination.y - target.getY()) <= 1.5
                && !this.getBoundingBox().move(destination.subtract(this.position())).inflate(.1).intersects(target.getBoundingBox())
                && this.getAmbush().isTacticalDestinationValid(level, target, destination);
    }
    @Override protected int teleportAttemptCooldownTicks() { return SpectralEndermanConfig.teleportCooldown(); }
    @Override protected boolean teleport() {
        return !this.getCombatPhase().bracesCaster() && !this.getMastermindBrain().hasPendingAmbush() && super.teleport();
    }
    public void warnMaterialization(ServerLevel level, LivingEntity target, boolean special) {
        level.sendParticles(GHOST, this.getX(), this.getY(.6), this.getZ(), special ? 7 : 4, .25, .6, .25, .02);
        level.playSound(null, target.getX(), target.getY() + .8, target.getZ(), SoundEvents.ENDERMAN_STARE,
                this.getSoundSource(), special ? .55F : .32F, special ? .65F : .95F);
    }
    public void materializationComplete(ServerLevel level, boolean special) {
        level.sendParticles(GHOST, this.getX(), this.getY(.55), this.getZ(), special ? 12 : 7, .35, .7, .35, .03);
        this.playSound(SoundEvents.ENDERMAN_TELEPORT, special ? .65F : .45F, special ? .6F : 1.15F);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.striking || !(target instanceof LivingEntity victim) || !this.getMastermindBrain().canUseRend(level, victim)) return false;
        this.getMastermindBrain().onRendAttempt(level);
        var reservation = this.getMastermindBrain().ambush().begin(victim, level.getGameTime());
        double bonus = reservation == null ? 0 : reservation.bonusDamage();
        var damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        float before = victim.getHealth() + victim.getAbsorptionAmount();
        boolean accepted = false; boolean consumed = false; boolean hit;
        this.striking = true;
        try {
            if (bonus > 0) damage.addTransientModifier(new AttributeModifier(REND_BONUS, bonus, AttributeModifier.Operation.ADD_VALUE));
            hit = super.doHurtTarget(level, victim);
            accepted = hit && victim.getHealth() + victim.getAbsorptionAmount() < before;
        } finally {
            damage.removeModifier(REND_BONUS); this.striking = false;
            consumed = this.getMastermindBrain().ambush().finish(reservation, accepted);
            if (reservation != null) accepted = consumed;
        }
        if (accepted) {
            this.setCombatPhase(consumed ? CombatPhase.EMPOWERED_REND : CombatPhase.REND);
            level.sendParticles(GHOST, victim.getX(), victim.getY(.55), victim.getZ(), consumed ? 12 : 5, .25, .4, .25, .04);
            if (consumed) level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY(.5), victim.getZ(), 4, .25, .45, .25, .015);
            this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, consumed ? .9F : .55F, consumed ? .6F : 1.05F);
        }
        this.getMastermindBrain().onRendFinished(level, victim, accepted, consumed);
        return hit;
    }
    @Override public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().bracesCaster()) {
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO);
        } else super.travel(input);
    }
    @Override public void aiStep() {
        if (!this.level().isClientSide() && (!this.isAlive() || this.isNoAi()) && this.getMastermindBrain() != null)
            this.getMastermindBrain().clearEncounter();
        super.aiStep();
    }
    @Override public void die(DamageSource source) {
        super.die(source); if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter();
    }
    @Override public void onRemoval(Entity.RemovalReason reason) {
        if (!this.level().isClientSide() && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter();
        super.onRemoval(reason);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter();
        this.getMastermindBrain().readCooldowns(input);
    }
    public static boolean checkSpectralSpawnRules(EntityType<SpectralEnderman> type, ServerLevelAccessor level,
                                                 EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        for (int height = 0; height < Mth.ceil(type.getHeight()); height++) {
            BlockPos cell = pos.above(height); var state = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, state, state.getFluidState(), type)) return false;
        }
        return true;
    }
    public static boolean isSpawnTime(ServerLevelAccessor level) {
        long time = Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L);
        return level.getLevel().dimension() == Level.OVERWORLD && time >= 13000L && time < 23000L;
    }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        return isSpawnTime(level) && level.getBiome(pos).is(Biomes.TAIGA);
    }
    private static double square(double value) { return value * value; }
    public enum CombatPhase {
        IDLE, OBSERVING, STALKING, MANIFESTATION_PREP, APPARITION_PREP, TRANSITION, MATERIALIZING,
        AMBUSH_READY, REND, EMPOWERED_REND, RECOVERY, SEARCHING;
        public boolean bracesCaster() {
            return this == OBSERVING || this == MANIFESTATION_PREP || this == APPARITION_PREP || this == TRANSITION
                    || this == MATERIALIZING || this == RECOVERY;
        }
    }
}
