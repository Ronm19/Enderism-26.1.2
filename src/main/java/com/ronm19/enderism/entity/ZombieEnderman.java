package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.ZombieEndermanConfig;
import com.ronm19.enderism.entity.ai.ZombieEndermanBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #024: native undead sunlight rules, a qualifying infected bite and finite encounter coordination. */
public final class ZombieEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_zombie_enderman"));
    public static final Identifier FRENZY_SPEED_ID = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "zombie_frenzy_speed");
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(ZombieEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(ZombieEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> PHASE_END = SynchedEntityData.defineId(ZombieEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> FRENZY_UNTIL = SynchedEntityData.defineId(ZombieEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> SIGNAL_UNTIL = SynchedEntityData.defineId(ZombieEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(ZombieEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions INFECTION = new DustParticleOptions(0x728D38, .65F);
    private @Nullable Vec3 teleportObservation;
    private boolean biting;

    public ZombieEnderman(EntityType<? extends ZombieEnderman> type, Level level) {
        super(type, level); this.setCanPickUpLoot(false); this.setPathfindingMalus(PathType.WATER, 4); this.setPathfindingMalus(PathType.WATER_BORDER, 0);
        this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, ZombieEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ZombieEndermanConfig.DEFAULT_DAMAGE).add(Attributes.MOVEMENT_SPEED, ZombieEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.FOLLOW_RANGE, ZombieEndermanConfig.DEFAULT_RANGE).add(Attributes.ATTACK_KNOCKBACK, .35);
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !ZombieEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ZombieEndermanConfig.health());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ZombieEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(ZombieEndermanConfig.movementSpeed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(ZombieEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected ZombieEndermanBrain createMastermindBrain() { return new ZombieEndermanBrain(this); }
    @Override public ZombieEndermanBrain getMastermindBrain() { return (ZombieEndermanBrain)super.getMastermindBrain(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected AABB getAttackBoundingBox(double horizontalExpansion) {
        double extra = this.getActiveItem().get(DataComponents.ATTACK_RANGE) == null ? ZombieEndermanConfig.biteReachBonus() : 0;
        return super.getAttackBoundingBox(horizontalExpansion + extra);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)0); data.define(PHASE_START, 0L); data.define(PHASE_END, 0L);
        data.define(FRENZY_UNTIL, 0L); data.define(SIGNAL_UNTIL, 0L); data.define(HUNTING, false);
    }
    public CombatPhase getCombatPhase() { int value = this.entityData.get(PHASE); return value >= 0 && value < PHASES.length ? PHASES[value] : CombatPhase.IDLE; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public int getWarmupTicksRemaining() { return (int)Math.max(0, this.entityData.get(PHASE_END) - this.level().getGameTime()); }
    public int getSignalTicksRemaining() { return (int)Math.max(0, this.entityData.get(SIGNAL_UNTIL) - this.level().getGameTime()); }
    public boolean isActivelyHunting() { return this.entityData.get(HUNTING); }
    public boolean isFrenzied() { return this.level().getGameTime() < this.entityData.get(FRENZY_UNTIL); }
    public long getFrenzyEndsAt() { return this.entityData.get(FRENZY_UNTIL); }
    public float getEyeBrightness() { return this.isFrenzied() || this.getCombatPhase() == CombatPhase.FRENZY_WINDUP ? 1 : this.isActivelyHunting() || this.getSignalTicksRemaining() > 0 ? .9F : .4F; }
    public void setActivelyHunting(boolean value) { if (!this.level().isClientSide()) this.entityData.set(HUNTING, value); }
    public void setCombatPhase(CombatPhase phase) { this.setCombatPhase(phase, 0); }
    public void setCombatPhase(CombatPhase phase, long end) {
        if (this.level().isClientSide()) return;
        if (phase != this.getCombatPhase()) { this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal()); }
        if (this.entityData.get(PHASE_END) != end) this.entityData.set(PHASE_END, end);
    }
    public boolean isLegalInfectedTarget(LivingEntity target) {
        return target != this && target.level() == this.level() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !(target instanceof EnderMan) && this.canAttack(target) && !this.isAlliedTo(target)
                && !(target instanceof Player player && player.isCreative()) && !(target instanceof ServerPlayer player && player.hasDisconnected());
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        return !this.biting && target instanceof LivingEntity victim && this.getMastermindBrain().tryBeginBite(level, victim, level.getGameTime());
    }
    public boolean performInfectedBite(ServerLevel level, LivingEntity target) {
        if (this.biting || !this.getMastermindBrain().isBiteImpactFrame(target) || !this.isLegalInfectedTarget(target)
                || !this.isWithinMeleeAttackRange(target) || !this.getSensing().hasLineOfSight(target)) return false;
        float before = target.getHealth() + target.getAbsorptionAmount(); boolean accepted;
        this.biting = true;
        try { accepted = super.doHurtTarget(level, target); } finally { this.biting = false; }
        if (!accepted || target.getHealth() + target.getAbsorptionAmount() >= before || !this.isAlive() || this.isRemoved() || this.isNoAi()
                || !this.getMastermindBrain().isBiteImpactFrame(target)) return false;
        this.applyInfection(target, MobEffects.POISON, ZombieEndermanConfig.poisonTicks(), ZombieEndermanConfig.poisonAmplifier());
        this.applyInfection(target, MobEffects.SLOWNESS, ZombieEndermanConfig.slownessTicks(), ZombieEndermanConfig.slownessAmplifier());
        level.sendParticles(INFECTION, target.getX(), target.getY(.55), target.getZ(), 8, .22, .25, .22, .02);
        this.playSound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, .5F, .8F); return true;
    }
    private void applyInfection(LivingEntity target, Holder<MobEffect> effect, int ticks, int strength) {
        if (!this.isLegalInfectedTarget(target)) return;
        var current = target.getEffect(effect);
        if (current != null && (current.getAmplifier() > strength || current.getAmplifier() == strength && current.getDuration() >= ticks)) return;
        target.addEffect(new MobEffectInstance(effect, ticks, strength), this);
    }
    public boolean beginFrenzy(ServerLevel level, long now) {
        if (this.level() != level || !this.isAlive() || this.isNoAi() || this.isFrenzied() || !this.getMastermindBrain().isFrenzyReleaseFrame()) return false;
        this.entityData.set(FRENZY_UNTIL, now + ZombieEndermanConfig.frenzyDurationTicks()); this.reconcileFrenzy();
        level.sendParticles(INFECTION, this.getX(), this.getY(.45), this.getZ(), 10, .35, .7, .35, .025);
        this.playSound(SoundEvents.ZOMBIE_AMBIENT, .8F, .6F);
        return true;
    }
    public void clearFrenzy() {
        if (this.level().isClientSide()) return;
        this.entityData.set(FRENZY_UNTIL, 0L); this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(FRENZY_SPEED_ID);
    }
    private void reconcileFrenzy() {
        if (this.level().isClientSide()) return;
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean active = this.isFrenzied() && this.isAlive() && !this.isRemoved() && !this.isNoAi();
        if (!active) { this.clearFrenzy(); return; }
        var desired = new AttributeModifier(FRENZY_SPEED_ID, ZombieEndermanConfig.frenzyMovementBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (!desired.equals(speed.getModifier(FRENZY_SPEED_ID))) { speed.removeModifier(FRENZY_SPEED_ID); speed.addTransientModifier(desired); }
    }
    public void biteWarning(ServerLevel level) { level.sendParticles(INFECTION, this.getX(), this.getY(.75), this.getZ(), 3, .16, .12, .16, .01); }
    public void frenzyWarning(ServerLevel level) { this.playSound(SoundEvents.ZOMBIE_AMBIENT, .75F, .55F); level.sendParticles(INFECTION, this.getX(), this.getY(.65), this.getZ(), 7, .3, .4, .3, .02); }
    /** A reactive cue is independent of bite/frenzy phases and can announce a final accepted attack. */
    public void showHordeSignal(ServerLevel level) {
        if (this.level() != level || this.isRemoved()) return;
        this.entityData.set(SIGNAL_UNTIL, level.getGameTime() + 10);
        this.playSound(SoundEvents.ZOMBIE_AMBIENT, .9F, .65F);
        level.sendParticles(INFECTION, this.getX(), this.getY(.7), this.getZ(), 9, .4, .3, .4, .02);
    }
    public boolean tryHordeReposition(Vec3 observed, boolean left) {
        if (!(this.level() instanceof ServerLevel level) || !observed.isFinite() || this.isNoAi() || this.getCombatPhase().isCommitted()
                || !this.getMastermindBrain().isTargetLocalized() || this.getMastermindBrain().getLastLocatedPosition() == null
                || this.getMastermindBrain().getLastLocatedPosition().distanceToSqr(observed) > .01) return false;
        Vec3 away = this.position().subtract(observed).multiply(1, 0, 1); if (away.horizontalDistanceSqr() < .01) away = new Vec3(1,0,0);
        away = away.normalize(); Vec3 side = new Vec3(-away.z, 0, away.x).scale(left ? 3 : -3);
        this.teleportObservation = observed;
        try { return this.trySafeTeleport(observed.add(away.scale(4.5)).add(side).add(0,2,0), 5); }
        finally { this.teleportObservation = null; }
    }
    @Override protected int teleportAttemptCooldownTicks() { return ZombieEndermanConfig.tacticalTeleportCooldownTicks(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().isCommitted() && super.teleport(); }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        if (this.teleportObservation == null) return true;
        double horizontal = destination.subtract(this.teleportObservation).horizontalDistanceSqr();
        return horizontal >= 2.5 * 2.5 && horizontal <= 8 * 8 && Math.abs(destination.y - this.teleportObservation.y) <= 3;
    }
    public @Nullable Vec3 findNearbyShade() {
        if (!(this.level() instanceof ServerLevel level) || !this.isOnFire() || !level.isBrightOutside()) return null;
        for (int attempt = 0; attempt < 12; attempt++) {
            Vec3 candidate = this.findSafeTeleportDestination(this.position().add(this.random.nextInt(17) - 8, 2, this.random.nextInt(17) - 8), 5);
            if (candidate != null && !level.canSeeSky(BlockPos.containing(candidate).above(Mth.ceil(this.getEyeHeight())))) return candidate;
        }
        return null;
    }
    @Override public void aiStep() {
        if (!this.level().isClientSide()) {
            if (this.isNoAi() || !this.isAlive()) this.getMastermindBrain().clearEncounter(); else this.reconcileFrenzy();
        }
        // Native Mob BURN_IN_DAYLIGHT behavior supplies the same weather/cover/headgear checks as Zombies.
        super.aiStep();
    }
    @Override public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().isCommitted()) { this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO); }
        else super.travel(input);
    }
    @Override public void die(DamageSource source) { super.die(source); if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter(); }
    @Override public void onRemoval(Entity.RemovalReason reason) { if (!this.level().isClientSide() && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter(); super.onRemoval(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter(); this.getMastermindBrain().readCooldowns(input); }
    public static boolean isSpawnTime(ServerLevelAccessor level) { long time = Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L); return level.getLevel().dimension() == Level.OVERWORLD && time >= 13000 && time < 23000; }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        if (!isSpawnTime(level) || !readyChunks(level.getLevel(), pos, pos) || !(level.getBiome(pos).is(Biomes.PLAINS) || level.getBiome(pos).is(Biomes.FOREST))) return false;
        return level.getLevel().getEntitiesOfClass(ZombieEnderman.class, new AABB(pos).inflate(64), mob -> mob.isAlive() && !mob.isRemoved()).size() < ZombieEndermanConfig.naturalPopulationCap();
    }
    public static boolean checkZombieSpawnRules(EntityType<ZombieEnderman> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        BlockState support = level.getBlockState(pos.below()); if (!support.isFaceSturdy(level, pos.below(), Direction.UP) || !support.getFluidState().isEmpty() || support.is(Blocks.MAGMA_BLOCK)) return false;
        for (int y = 0; y < Mth.ceil(type.getHeight()); y++) { BlockPos cell = pos.above(y); BlockState block = level.getBlockState(cell); if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, block, block.getFluidState(), type)) return false; }
        return true;
    }
    public static boolean readyChunks(ServerLevel level, BlockPos low, BlockPos high) { for (int x = low.getX() >> 4; x <= high.getX() >> 4; x++) for (int z = low.getZ() >> 4; z <= high.getZ() >> 4; z++) if (level.getChunkSource().getChunkNow(x,z) == null) return false; return true; }
    public enum CombatPhase {
        IDLE, PURSUING, SEARCHING, RESPONDING, SEEKING_SHADE, BITE_WINDUP, BITE, FRENZY_WINDUP, FRENZIED, RECOVERY;
        public boolean isCommitted() { return this == BITE_WINDUP || this == BITE || this == FRENZY_WINDUP || this == RECOVERY; }
        public boolean isCasting() { return this == BITE_WINDUP || this == BITE || this == FRENZY_WINDUP; }
    }
}
