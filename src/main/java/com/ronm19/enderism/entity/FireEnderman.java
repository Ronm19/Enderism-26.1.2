package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.FireEndermanConfig;
import com.ronm19.enderism.entity.ai.FireEndermanBrain;
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
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.Nullable;

/** #004: Flame Strike, Hunger Hex and Cinder Burst use native damage and finite effects. */
public final class FireEnderman extends AbstractEnderman {
    public static final float MELEE_BURN_SECONDS = 2.0F;
    public static final double DETECTION_RANGE = 32.0;
    public static final TagKey<Biome> OVERWORLD_SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_fire_enderman_overworld"));
    public static final TagKey<Biome> NETHER_SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_fire_enderman_nether"));
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(
            FireEnderman.class, EntityDataSerializers.BYTE);
    private static final HuntState[] HUNT_STATES = HuntState.values();
    private static final EntityDataAccessor<Byte> ABILITY = SynchedEntityData.defineId(FireEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> ABILITY_UNTIL = SynchedEntityData.defineId(FireEnderman.class, EntityDataSerializers.LONG);
    private static final Ability[] ABILITIES = Ability.values();
    private boolean striking;
    private @Nullable LivingEntity receiptTarget;
    private float nativeHealthDamage;
    private boolean areaDamage;

    public FireEnderman(EntityType<? extends FireEnderman> type, Level level) {
        super(type, level);
        // Fire is harmless, but ordinary ground navigation must still avoid immersion in lava.
        this.setPathfindingMalus(PathType.FIRE, 0.0F);
        this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0F);
        this.setPathfindingMalus(PathType.LAVA, -1.0F);
        this.applyRuntimeAttributes(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Vanilla Enderman health, speed and seven-point melee are the provisional baseline.
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, FireEndermanConfig.health())
                .add(Attributes.ATTACK_DAMAGE, FireEndermanConfig.attackDamage()).add(Attributes.MOVEMENT_SPEED, FireEndermanConfig.movementSpeed())
                .add(Attributes.FOLLOW_RANGE, FireEndermanConfig.followRange());
    }

    @Override
    protected FireEndermanBrain createMastermindBrain() {
        return new FireEndermanBrain(this);
    }
    @Override public FireEndermanBrain getMastermindBrain() { return (FireEndermanBrain)super.getMastermindBrain(); }
    public static void registerEvents(IEventBus bus) { bus.addListener(FireEnderman::acceptedDamage); }
    private static void acceptedDamage(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof FireEnderman source && event.getSource().getDirectEntity() == source
                && source.receiptTarget == event.getEntity() && (source.striking && event.getSource().is(DamageTypes.MOB_ATTACK)
                || source.areaDamage && event.getSource().is(DamageTypes.IN_FIRE)) && Float.isFinite(event.getHealthDamage()) && event.getHealthDamage() > 0)
            source.nativeHealthDamage = Math.max(source.nativeHealthDamage, event.getHealthDamage());
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !FireEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(FireEndermanConfig.health()); this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(FireEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(FireEndermanConfig.movementSpeed()); this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(FireEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.striking || this.areaDamage || this.getMastermindBrain().isReleasingFireAction() || this.level() != level || !(target instanceof LivingEntity victim)
                || !this.isLegalFireTarget(victim) || this.isAbilityWindingUp() || !this.isWithinMeleeAttackRange(victim) || !this.getSensing().hasLineOfSight(victim)) return false;
        long epoch = this.getMastermindBrain().actionEpoch(); boolean noAi = this.isNoAi(); float before = victim.getHealth(); boolean hit;
        this.striking = true; this.receiptTarget = victim; this.nativeHealthDamage = 0;
        try { hit = super.doHurtTarget(level, target); } finally { this.striking = false; this.receiptTarget = null; }
        if (hit && this.nativeHealthDamage > 0 && victim.getHealth() < before && this.isAlive() && !this.isRemoved() && this.isNoAi() == noAi
                && this.getMastermindBrain().actionEpoch() == epoch && this.isLegalFireTarget(victim)) {
            this.striking = true;
            try {
                this.applyHunger(victim, FireEndermanConfig.strikeHungerTicks(), 0);
                if (this.getMastermindBrain().actionEpoch() == epoch && this.isAlive() && !this.isRemoved() && this.isNoAi() == noAi && this.isLegalFireTarget(victim)) {
                    victim.igniteForSeconds(MELEE_BURN_SECONDS); this.setAbility(Ability.FLAME_STRIKE, level.getGameTime() + 4);
                }
            } finally { this.striking = false; }
        }
        return hit;
    }
    public boolean isLegalFireTarget(@Nullable LivingEntity target) {
        return target != null && target != this && target.level() == this.level() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !(target instanceof EnderMan) && this.canAttack(target) && !this.isAlliedTo(target)
                && !(target instanceof Player p && p.isCreative()) && !(target instanceof ServerPlayer p && p.hasDisconnected());
    }
    public boolean canCastOn(LivingEntity target, double range) { return this.isAlive() && !this.isRemoved() && !this.isNoAi() && this.isLegalFireTarget(target) && this.distanceToSqr(target) <= range * range && this.getSensing().hasLineOfSight(target); }
    public boolean isResolvingFireAction() { return this.striking || this.areaDamage; }
    private boolean applyHunger(LivingEntity target, int ticks, int amplifier) {
        var current = target.getEffect(MobEffects.HUNGER);
        if (current != null && (current.getAmplifier() > amplifier || current.getAmplifier() == amplifier && current.getDuration() >= ticks)) return false;
        return target.addEffect(new MobEffectInstance(MobEffects.HUNGER, ticks, amplifier), this);
    }
    public boolean releaseHungerHex(ServerLevel level, LivingEntity target) {
        if (this.level() != level || !this.getMastermindBrain().isHexReleaseFrame(target) || !(target instanceof Player) || !this.canCastOn(target, FireEndermanConfig.hexRange())) return false;
        boolean applied = this.applyHunger(target, FireEndermanConfig.hexHungerTicks(), FireEndermanConfig.hexHungerAmplifier());
        if (this.getMastermindBrain().isHexReleaseFrame(target) && this.canCastOn(target, FireEndermanConfig.hexRange())) level.sendParticles(ParticleTypes.SMALL_FLAME, target.getX(), target.getY(.6), target.getZ(), 8, .3, .4, .3, .02);
        return applied;
    }
    public int releaseCinderBurst(ServerLevel level) {
        if (this.level() != level || this.areaDamage || !this.getMastermindBrain().isBurstReleaseFrame()) return 0;
        long epoch = this.getMastermindBrain().actionEpoch(); double radius = FireEndermanConfig.burstRadius(); int hits = 0, checked = 0;
        var targets = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(radius), target -> this.canCastOn(target, radius));
        targets.sort(java.util.Comparator.comparingDouble((Player target) -> this.distanceToSqr(target)).thenComparing(Entity::getUUID));
        DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.IN_FIRE), this, this);
        for (Player target : targets) {
            if (!this.isAlive() || this.isNoAi() || this.isRemoved() || this.getMastermindBrain().actionEpoch() != epoch || !this.getMastermindBrain().isBurstReleaseFrame()) break;
            if (++checked > 8) break;
            if (!this.canCastOn(target, radius)) continue;
            float before = target.getHealth(); boolean accepted; this.areaDamage = true; this.receiptTarget = target; this.nativeHealthDamage = 0;
            try { accepted = target.hurtServer(level, source, (float)FireEndermanConfig.burstDamage()); } finally { this.areaDamage = false; this.receiptTarget = null; }
            if (!this.isAlive() || this.isRemoved() || this.isNoAi() || this.getMastermindBrain().actionEpoch() != epoch || !this.getMastermindBrain().isBurstReleaseFrame()) break;
            if (accepted && this.nativeHealthDamage > 0 && target.getHealth() < before && this.canCastOn(target, radius)) { target.igniteForSeconds(MELEE_BURN_SECONDS); hits++; }
        }
        if (this.isAlive() && !this.isRemoved() && !this.isNoAi() && this.getMastermindBrain().actionEpoch() == epoch && this.getMastermindBrain().isBurstReleaseFrame()) {
            level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(.4), this.getZ(), 24, radius * .35, .35, radius * .35, .04); this.playSound(SoundEvents.BLAZE_SHOOT, .6F, .8F);
        }
        return hits;
    }
    public void abilityWarning(ServerLevel level) { level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(.6), this.getZ(), 8, .3, .4, .3, .01); this.playSound(SoundEvents.FIRE_AMBIENT, .65F, .8F); }
    public Ability getAbility() { int value = this.entityData.get(ABILITY); return value >= 0 && value < ABILITIES.length ? ABILITIES[value] : Ability.NONE; }
    public long getAbilityEndsAt() { return this.entityData.get(ABILITY_UNTIL); }
    public boolean isAbilityWindingUp() { return (this.getAbility() == Ability.HUNGER_HEX || this.getAbility() == Ability.CINDER_BURST) && this.level().getGameTime() < this.getAbilityEndsAt(); }
    public void setAbility(Ability value, long until) { if (!this.level().isClientSide()) { this.entityData.set(ABILITY, (byte)value.ordinal()); this.entityData.set(ABILITY_UNTIL, until); } }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(HUNT_STATE, (byte) HuntState.IDLE.ordinal());
        data.define(ABILITY, (byte)0); data.define(ABILITY_UNTIL, 0L);
    }

    public HuntState getHuntState() {
        int ordinal = this.entityData.get(HUNT_STATE);
        return ordinal >= 0 && ordinal < HUNT_STATES.length ? HUNT_STATES[ordinal] : HuntState.IDLE;
    }

    public void setHuntState(HuntState state) {
        if (!this.level().isClientSide()) this.entityData.set(HUNT_STATE, (byte) state.ordinal());
    }

    public float getEyeBrightness() {
        return switch (this.getHuntState()) {
            case IDLE -> 0.72F;
            case SEARCHING -> 0.85F;
            case PURSUING -> 1.0F;
        };
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        // This species inhabits bright daytime terrain; vanilla idle sunlight escape is inappropriate.
        this.getBrain().tick(level, this);
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide() && (this.isNoAi() || !this.isAlive())) this.getMastermindBrain().clearEncounter();
        super.aiStep();
        if (this.level().isClientSide() && this.isAlive() && this.tickCount % 10 == 0) {
            // Cosmetic only, one small ember per half-second; no fire placement or damage aura.
            this.level().addParticle(ParticleTypes.SMALL_FLAME,
                    this.getX() + (this.random.nextDouble() - 0.5) * this.getBbWidth(),
                    this.getY() + 0.2 + this.random.nextDouble() * (this.getBbHeight() - 0.2),
                    this.getZ() + (this.random.nextDouble() - 0.5) * this.getBbWidth(),
                    0.0, 0.012, 0.0);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.applyRuntimeAttributes(false);
        this.getMastermindBrain().clearEncounter();
        this.getMastermindBrain().readCooldowns(input);
        this.setHuntState(HuntState.IDLE);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output); }
    @Override public void travel(Vec3 input) { if (!this.level().isClientSide() && this.isAbilityWindingUp()) { this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO); } else super.travel(input); }
    @Override public void setNoAi(boolean value) { super.setNoAi(value); if (value && this.getMastermindBrain() != null && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter(); }
    @Override public void die(DamageSource source) { super.die(source); if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter(); }
    @Override public void onRemoval(Entity.RemovalReason reason) { if (this.getMastermindBrain() != null && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter(); super.onRemoval(reason); }
    public enum Ability { NONE, FLAME_STRIKE, HUNGER_HEX, CINDER_BURST }

    public static boolean checkFireSpawnRules(EntityType<FireEnderman> type, ServerLevelAccessor level,
                                             EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || level.getDifficulty() == Difficulty.PEACEFUL) return false;

        if (level.getLevel().dimension() == Level.NETHER) {
            if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        } else {
            // Deliberately preserve ground checks without Monster's darkness gate in the Overworld.
            if (!Mob.checkMobSpawnRules(type, level, reason, pos, random)) return false;
        }
        for (int height = 0; height < 3; height++) {
            BlockPos cell = pos.above(height);
            var block = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, block, block.getFluidState(), type)) return false;
        }
        // Same biome weight, with half of otherwise valid night attempts rejected only in the Overworld.
        // This produces a 2:1 day/night opportunity ratio without changing the Nether spawn pool.
        return level.getLevel().dimension() != Level.OVERWORLD || isOverworldDay(level) || random.nextBoolean();
    }

    public static boolean isOverworldDay(ServerLevelAccessor level) {
        return Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L) < 12000L;
    }

    public static boolean isSpawnTime(ServerLevelAccessor level) {
        return level.getLevel().dimension() == Level.OVERWORLD || level.getLevel().dimension() == Level.NETHER;
    }

    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        if (level.getLevel().dimension() == Level.OVERWORLD) return level.getBiome(pos).is(OVERWORLD_SPAWN_BIOMES);
        return level.getLevel().dimension() == Level.NETHER && level.getBiome(pos).is(NETHER_SPAWN_BIOMES);
    }

    public enum HuntState { IDLE, PURSUING, SEARCHING }
}
