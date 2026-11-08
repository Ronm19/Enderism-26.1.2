package com.ronm19.enderism.entity;

import com.ronm19.enderism.AngelEndermanConfig;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.ai.AngelEndermanBrain;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.Nullable;

/** #018: an independent, grounded guardian; ally support never confers player hostility. */
public final class AngelEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_angel_enderman"));
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(AngelEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(AngelEnderman.class, EntityDataSerializers.LONG);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private boolean striking;

    public AngelEnderman(EntityType<? extends AngelEnderman> type, Level level) {
        super(type, level); this.applyRuntimeAttributes(true);
    }
    public static void registerEvents(IEventBus bus) { bus.addListener(AngelEnderman::acceptedDamage); }
    private static void acceptedDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof AngelEnderman angel
                && (event.getHealthDamage() > 0 || event.getReduction(DamageContainer.Reduction.ABSORPTION) > 0)
                && event.getSource().getEntity() instanceof LivingEntity attacker)
            angel.getMastermindBrain().recordDirectAttack(attacker);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, AngelEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, AngelEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, AngelEndermanConfig.DEFAULT_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, AngelEndermanConfig.DEFAULT_RANGE);
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !AngelEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(AngelEndermanConfig.health());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(AngelEndermanConfig.speed());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(AngelEndermanConfig.damage());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(AngelEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected AngelEndermanBrain createMastermindBrain() { return new AngelEndermanBrain(this); }
    @Override public AngelEndermanBrain getMastermindBrain() { return (AngelEndermanBrain)super.getMastermindBrain(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override public boolean canAttack(LivingEntity target) {
        return super.canAttack(target) && this.getMastermindBrain() != null && this.getMastermindBrain().isLegitimateThreat(target);
    }
    @Override public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target != null && (this.getMastermindBrain() == null || !this.getMastermindBrain().isLegitimateThreat(target)) ? null : target);
        // A native target event may replace the requested target; the territorial policy still wins.
        LivingEntity actual = this.getTarget();
        if (actual != null && (this.getMastermindBrain() == null || !this.getMastermindBrain().isLegitimateThreat(actual))) super.setTarget(null);
    }
    @Override public void updatePersistentAnger(ServerLevel level, boolean stayAngryIfTargetPresent) {
        // Native Enderman anger refreshes indefinitely while a target exists. Territorial records own all hostility here.
        this.setPersistentAngerTarget(null); this.setPersistentAngerEndTime(0L);
        if (this.getTarget() != null && !this.getMastermindBrain().isLegitimateThreat(this.getTarget())) this.setTarget(null);
    }
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
    public float getEyeBrightness() {
        return switch (this.getCombatPhase()) {
            case IDLE -> .55F;
            case OBSERVING, DE_ESCALATING -> .65F;
            case HEALING, RECOVERY -> .85F;
            default -> 1;
        };
    }
    public void supportCue(ServerLevel level, LivingEntity ally, boolean benediction) {
        level.sendParticles(ParticleTypes.END_ROD, ally.getX(), ally.getY(.6), ally.getZ(), benediction ? 10 : 7, .4, .7, .4, .025);
        level.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY(.75), ally.getZ(), benediction ? 2 : 3, .35, .25, .35, .01);
    }
    public void castCue(ServerLevel level, boolean benediction) {
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(.65), this.getZ(), benediction ? 9 : 5, .45, .7, .45, .015);
        this.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, .45F, benediction ? 1.35F : 1.65F);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.striking || !(target instanceof LivingEntity victim) || !this.getMastermindBrain().canUseStrike(level, victim)) return false;
        this.getMastermindBrain().onStrikeAttempt(level); this.striking = true;
        boolean hit;
        try { hit = super.doHurtTarget(level, victim); } finally { this.striking = false; }
        if (hit) {
            this.setCombatPhase(CombatPhase.STRIKE);
            level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY(.6), victim.getZ(), 5, .25, .35, .25, .025);
            this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, .6F, 1.4F);
        }
        return hit;
    }
    @Override protected int teleportAttemptCooldownTicks() { return AngelEndermanConfig.teleportCooldown(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().bracesCaster() && super.teleport(); }
    @Override public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().bracesCaster()) {
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO);
        } else super.travel(input);
    }
    @Override public void aiStep() {
        if (!this.level().isClientSide() && (!this.isAlive() || this.isNoAi()) && this.getMastermindBrain() != null)
            this.getMastermindBrain().clearEncounter();
        super.aiStep();
        if (this.level().isClientSide() && this.isAlive() && this.tickCount % 36 == 0)
            this.level().addParticle(ParticleTypes.END_ROD, this.getRandomX(.4), this.getRandomY(), this.getRandomZ(.4), 0, .012, 0);
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
    public static boolean checkAngelSpawnRules(EntityType<AngelEnderman> type, ServerLevelAccessor level,
                                              EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) return false;
        var support = level.getBlockState(pos.below());
        if (!support.blocksMotion() || !support.getFluidState().isEmpty() || support.is(Blocks.MAGMA_BLOCK)
                || support.is(Blocks.CACTUS) || support.is(Blocks.CAMPFIRE) || support.is(Blocks.SOUL_CAMPFIRE)
                || support.is(Blocks.POWDER_SNOW) || support.is(Blocks.WITHER_ROSE) || support.is(Blocks.SWEET_BERRY_BUSH)) return false;
        for (int height = 0; height < Mth.ceil(type.getHeight()); height++) {
            BlockPos cell = pos.above(height); var state = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, state, state.getFluidState(), type)) return false;
        }
        return true;
    }
    public static boolean isSpawnTime(ServerLevelAccessor level) { return level.getLevel().dimension() == Level.OVERWORLD; }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        return isSpawnTime(level) && level.getBiome(pos).is(Biomes.CHERRY_GROVE);
    }
    public enum CombatPhase {
        IDLE, OBSERVING, TERRITORIAL_WARNING, DEFENDING, STRIKE, HEALING, BENEDICTION, DE_ESCALATING, RECOVERY;
        public boolean bracesCaster() { return this == HEALING || this == BENEDICTION || this == TERRITORIAL_WARNING || this == RECOVERY; }
    }
}
