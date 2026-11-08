package com.ronm19.enderism.entity;

import com.ronm19.enderism.BoggedEndermanConfig;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.combat.BoggedPoison;
import com.ronm19.enderism.entity.ai.BoggedEndermanBrain;
import com.ronm19.enderism.entity.projectile.BoggedVenom;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #022: a grounded swamp hunter with three finite, server-owned poisonous attacks. */
public final class BoggedEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_bogged_enderman"));
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(BoggedEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(BoggedEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> PHASE_END = SynchedEntityData.defineId(BoggedEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(BoggedEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final DustParticleOptions VENOM = new DustParticleOptions(0x8CB53F, .65F);
    private final Map<UUID, WeakReference<BoggedVenom>> ownedVenom = new HashMap<>();
    private @Nullable WeakReference<BoggedToxicMire> ownedMire;
    private @Nullable Vec3 teleportObservation;
    private boolean striking;

    public BoggedEnderman(EntityType<? extends BoggedEnderman> type, Level level) {
        super(type, level);
        // Land stays preferable; shallow water remains navigable without flight or aquatic powers.
        this.setPathfindingMalus(PathType.WATER, 4);
        this.setPathfindingMalus(PathType.WATER_BORDER, 0);
        this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, BoggedEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, BoggedEndermanConfig.DEFAULT_DAMAGE).add(Attributes.MOVEMENT_SPEED, BoggedEndermanConfig.DEFAULT_SPEED)
                .add(Attributes.FOLLOW_RANGE, BoggedEndermanConfig.DEFAULT_RANGE).add(Attributes.ATTACK_KNOCKBACK, .25);
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !BoggedEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BoggedEndermanConfig.health());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BoggedEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(BoggedEndermanConfig.movementSpeed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(BoggedEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected BoggedEndermanBrain createMastermindBrain() { return new BoggedEndermanBrain(this); }
    @Override public BoggedEndermanBrain getMastermindBrain() { return (BoggedEndermanBrain)super.getMastermindBrain(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected AABB getAttackBoundingBox(double horizontalExpansion) {
        double bonus = this.getActiveItem().get(DataComponents.ATTACK_RANGE) == null ? BoggedEndermanConfig.meleeReachBonus() : 0;
        return super.getAttackBoundingBox(horizontalExpansion + bonus);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)0); data.define(PHASE_START, 0L); data.define(PHASE_END, 0L); data.define(HUNTING, false);
    }
    public CombatPhase getCombatPhase() { int value = this.entityData.get(PHASE); return value >= 0 && value < PHASES.length ? PHASES[value] : CombatPhase.IDLE; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public int getWarmupTicksRemaining() { return (int)Math.max(0, this.entityData.get(PHASE_END) - this.level().getGameTime()); }
    public boolean isActivelyHunting() { return this.entityData.get(HUNTING); }
    public void setActivelyHunting(boolean hunting) { if (!this.level().isClientSide()) this.entityData.set(HUNTING, hunting); }
    public void setCombatPhase(CombatPhase phase) { this.setCombatPhase(phase, 0); }
    public void setCombatPhase(CombatPhase phase, long end) {
        if (this.level().isClientSide()) return;
        if (phase != this.getCombatPhase()) { this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal()); }
        if (this.entityData.get(PHASE_END) != end) this.entityData.set(PHASE_END, end);
    }
    public float getEyeBrightness() {
        if (this.getCombatPhase().isCasting()) return 1;
        return this.isActivelyHunting() ? .95F : .35F;
    }
    public boolean isLegalVenomTarget(LivingEntity target) {
        return target != this && target.level() == this.level() && target.isAlive() && !target.isRemoved()
                && !target.isSpectator() && !(target instanceof EnderMan) && this.canAttack(target) && !this.isAlliedTo(target)
                && !(target instanceof Player player && player.isCreative()) && !(target instanceof ServerPlayer player && player.hasDisconnected());
    }
    public boolean mayPoisonInMire(LivingEntity target) {
        return this.isLegalVenomTarget(target) && (target instanceof Player || target instanceof Monster || target == this.getTarget()
                || this.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null) == target
                || this.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == target);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        return !this.striking && target instanceof LivingEntity victim
                && this.getMastermindBrain().tryBeginGrasp(level, victim, level.getGameTime());
    }
    public boolean performVenomousGrasp(ServerLevel level, LivingEntity target) {
        if (this.striking || !this.getMastermindBrain().isGraspImpactFrame(target) || !this.isLegalVenomTarget(target)
                || !this.isWithinMeleeAttackRange(target) || !this.getSensing().hasLineOfSight(target)) return false;
        float before = target.getHealth() + target.getAbsorptionAmount(); boolean accepted;
        this.striking = true;
        try { accepted = super.doHurtTarget(level, target); } finally { this.striking = false; }
        if (!accepted || target.getHealth() + target.getAbsorptionAmount() >= before) return false;
        BoggedPoison.applyVenom(this, target, BoggedEndermanConfig.graspPoisonTicks(), BoggedEndermanConfig.poisonAmplifier());
        level.sendParticles(VENOM, target.getX(), target.getY(.5), target.getZ(), 9, .22, .28, .22, .02);
        this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, .7F, .72F); return true;
    }
    public Vec3 getMuzzlePosition() { return this.position().add(0, 1.9, 0); }
    public Vec3 getRangedAimPoint(LivingEntity target) { return target.position().add(0, target.getBbHeight() * .55, 0); }
    public boolean hasClearProjectilePath(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level) || !this.isLegalVenomTarget(target)) return false;
        Vec3 from = this.getMuzzlePosition(), to = this.getRangedAimPoint(target);
        BlockPos low = BlockPos.containing(Math.min(from.x, to.x) - 1, Math.min(from.y, to.y) - 1, Math.min(from.z, to.z) - 1);
        BlockPos high = BlockPos.containing(Math.max(from.x, to.x) + 1, Math.max(from.y, to.y) + 1, Math.max(from.z, to.z) + 1);
        return readyChunks(level, low, high) && level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    public boolean launchVenom(ServerLevel level, LivingEntity target) {
        this.pruneOwnedEffects(level.getGameTime());
        if (this.level() != level || this.isNoAi() || !this.isAlive() || this.isRemoved() || !this.hasClearProjectilePath(target)
                || this.ownedVenom.size() >= BoggedEndermanConfig.barrageProjectileCount() || !this.getMastermindBrain().isBarrageReleaseFrame(target)) return false;
        BoggedVenom shot = BoggedVenom.create(level, this, target, this.getMuzzlePosition(), this.getRangedAimPoint(target));
        this.ownedVenom.put(shot.getUUID(), new WeakReference<>(shot));
        if (!level.addFreshEntity(shot)) { this.ownedVenom.remove(shot.getUUID()); shot.discard(); return false; }
        this.playSound(SoundEvents.SLIME_SQUISH_SMALL, .55F, .65F); return true;
    }
    public boolean isVenomValid(BoggedVenom shot) {
        var reference = this.ownedVenom.get(shot.getUUID());
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && shot.level() == this.level() && reference != null && reference.get() == shot;
    }
    public void onVenomRemoved(BoggedVenom shot) {
        var reference = this.ownedVenom.get(shot.getUUID()); if (reference != null && reference.get() == shot) this.ownedVenom.remove(shot.getUUID());
    }
    public boolean hasActiveMire() {
        if (!this.level().isClientSide()) this.pruneOwnedEffects(this.level().getGameTime());
        var mire = this.ownedMire == null ? null : this.ownedMire.get(); return mire != null && !mire.isRemoved();
    }
    public boolean createToxicMire(ServerLevel level, LivingEntity target, Vec3 center) {
        if (this.level() != level || this.isNoAi() || !this.isAlive() || this.hasActiveMire()
                || !this.isLegalVenomTarget(target) || !this.getMastermindBrain().isMireReleaseFrame(target)) return false;
        BoggedToxicMire mire = BoggedToxicMire.create(level, this, target, center);
        this.ownedMire = new WeakReference<>(mire);
        if (!level.addFreshEntity(mire)) { this.ownedMire = null; mire.discard(); return false; }
        return true;
    }
    public boolean isMireValid(BoggedToxicMire mire) {
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && mire.level() == this.level()
                && this.ownedMire != null && this.ownedMire.get() == mire;
    }
    public void onMireRemoved(BoggedToxicMire mire) { if (this.ownedMire != null && this.ownedMire.get() == mire) this.ownedMire = null; }
    public void clearOwnedEffects() {
        var shots = List.copyOf(this.ownedVenom.values()); this.ownedVenom.clear();
        for (var reference : shots) { var shot = reference.get(); if (shot != null && !shot.isRemoved()) shot.discard(); }
        var mire = this.ownedMire == null ? null : this.ownedMire.get(); this.ownedMire = null;
        if (mire != null && !mire.isRemoved()) mire.discard();
    }
    private void pruneOwnedEffects(long now) {
        for (var reference : List.copyOf(this.ownedVenom.values())) {
            var shot = reference.get(); if (shot != null && !shot.isRemoved() && shot.isExpired(now)) shot.discard();
        }
        this.ownedVenom.entrySet().removeIf(entry -> { var shot = entry.getValue().get(); return shot == null || shot.isRemoved(); });
        var mire = this.ownedMire == null ? null : this.ownedMire.get();
        if (mire != null && !mire.isRemoved() && mire.isExpired(now)) mire.discard();
        if (mire == null || mire.isRemoved()) this.ownedMire = null;
    }
    public void warningCue(ServerLevel level, boolean mire, @Nullable Vec3 center) {
        Vec3 point = center == null ? this.position().add(0, 1.8, 0) : center.add(0, .12, 0);
        level.sendParticles(VENOM, point.x, point.y, point.z, mire ? 14 : 6, mire ? 1.5 : .25, mire ? .04 : .3, mire ? 1.5 : .25, .015);
        this.playSound(SoundEvents.ENDERMAN_STARE, mire ? .65F : .4F, mire ? .65F : .9F);
    }
    public void gatheringCue(ServerLevel level, @Nullable Vec3 center) {
        Vec3 point = center == null ? this.getMuzzlePosition() : center.add(0, .1, 0);
        level.sendParticles(VENOM, point.x, point.y, point.z, 4, center == null ? .15 : 1.25, .06, center == null ? .15 : 1.25, .01);
    }
    public boolean trySwampReposition(Vec3 observed, boolean left) {
        if (!(this.level() instanceof ServerLevel level) || !observed.isFinite() || this.isNoAi() || this.getCombatPhase().isCommitted()
                || !this.getMastermindBrain().isTargetLocalized() || this.getMastermindBrain().getLastLocatedPosition() == null
                || this.getMastermindBrain().getLastLocatedPosition().distanceToSqr(observed) > .01) return false;
        Vec3 radial = this.position().subtract(observed).multiply(1, 0, 1).normalize();
        if (radial.lengthSqr() < .01) radial = new Vec3(1, 0, 0);
        Vec3 side = new Vec3(-radial.z, 0, radial.x).scale(left ? 4 : -4);
        this.teleportObservation = observed;
        try {
            for (Vec3 candidate : new Vec3[] { observed.add(side), observed.subtract(side), observed.add(radial.scale(5)) }) {
                if (this.distanceToSqr(candidate) > 24 * 24 || this.distanceToSqr(candidate) < 4) continue;
                Vec3 destination = this.findSafeTeleportDestination(candidate.add(0, 2, 0), 4);
                if (destination != null && this.trySafeTeleport(destination, 0)) {
                    this.setDeltaMovement(0, this.getDeltaMovement().y, 0); this.resetFallDistance();
                    level.sendParticles(VENOM, this.getX(), this.getY(.45), this.getZ(), 7, .3, .6, .3, .02); return true;
                }
            }
            return false;
        } finally { this.teleportObservation = null; }
    }
    @Override protected int teleportAttemptCooldownTicks() { return BoggedEndermanConfig.tacticalTeleportCooldownTicks(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().isCommitted() && super.teleport(); }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        if (this.teleportObservation == null) return true;
        double horizontal = destination.subtract(this.teleportObservation).horizontalDistanceSqr();
        return horizontal >= 2.5 * 2.5 && horizontal <= 8 * 8 && Math.abs(destination.y - this.teleportObservation.y) <= 3;
    }
    @Override public void aiStep() {
        if (!this.level().isClientSide()) {
            if (this.isNoAi() || !this.isAlive()) this.getMastermindBrain().clearEncounter();
            else if (this.tickCount % 10 == 0) this.pruneOwnedEffects(this.level().getGameTime());
        }
        super.aiStep();
    }
    @Override public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().bracesCaster()) {
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO);
        } else super.travel(input);
    }
    @Override public void die(DamageSource source) { super.die(source); if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter(); }
    @Override public void onRemoval(Entity.RemovalReason reason) { if (!this.level().isClientSide() && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter(); super.onRemoval(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter(); this.getMastermindBrain().readCooldowns(input);
    }
    public static boolean isSpawnTime(ServerLevelAccessor level) {
        long time = Math.floorMod(level.getLevel().getOverworldClockTime(), 24000L);
        return level.getLevel().dimension() == Level.OVERWORLD && time >= 13000 && time < 23000;
    }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        if (!isSpawnTime(level) || !readyChunks(level.getLevel(), pos, pos) || !level.getBiome(pos).is(Biomes.SWAMP)) return false;
        return level.getLevel().getEntitiesOfClass(BoggedEnderman.class, new AABB(pos).inflate(64), mob -> mob.isAlive() && !mob.isRemoved()).size() < BoggedEndermanConfig.naturalPopulationCap();
    }
    public static boolean checkBoggedSpawnRules(EntityType<BoggedEnderman> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        BlockState support = level.getBlockState(pos.below());
        if (!support.isFaceSturdy(level, pos.below(), Direction.UP) || !support.getFluidState().isEmpty() || support.is(Blocks.MAGMA_BLOCK)) return false;
        for (int y = 0; y < Mth.ceil(type.getHeight()); y++) {
            BlockPos cell = pos.above(y); BlockState state = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, state, state.getFluidState(), type)) return false;
        }
        return true;
    }
    public static boolean readyChunks(ServerLevel level, BlockPos low, BlockPos high) {
        for (int x = low.getX() >> 4; x <= high.getX() >> 4; x++) for (int z = low.getZ() >> 4; z <= high.getZ() >> 4; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    public enum CombatPhase {
        IDLE, PURSUING, SEARCHING, GRASP_WINDUP, GRASP, BARRAGE_WINDUP, BARRAGE, MIRE_WINDUP, MIRE, RECOVERY;
        public boolean isCasting() { return this == GRASP_WINDUP || this == GRASP || this == BARRAGE_WINDUP || this == BARRAGE || this == MIRE_WINDUP || this == MIRE; }
        public boolean isCommitted() { return this.isCasting() || this == RECOVERY; }
        public boolean bracesCaster() { return this.isCommitted(); }
    }
}
