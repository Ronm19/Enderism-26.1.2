package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.SkeletonEndermanConfig;
import com.ronm19.enderism.entity.ai.SkeletonEndermanBrain;
import com.ronm19.enderism.entity.projectile.SkeletonArcaneArrow;
import com.ronm19.enderism.entity.projectile.SkeletonArcaneArrow.Element;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** #023: native ground combat and sunlight burning, with three bounded, bowless arrow abilities. */
public final class SkeletonEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_skeleton_enderman"));
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(SkeletonEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(SkeletonEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> PHASE_END = SynchedEntityData.defineId(SkeletonEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> BUDGET = SynchedEntityData.defineId(SkeletonEnderman.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> ELEMENT = SynchedEntityData.defineId(SkeletonEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> HUNTING = SynchedEntityData.defineId(SkeletonEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final CombatPhase[] PHASES = CombatPhase.values();
    private static final Element[] ELEMENTS = Element.values();
    private static final int MAX_OWNED_ARROWS = 4 * SkeletonEndermanConfig.MAX_VOLLEY_ARROWS;
    private static final DustParticleOptions MAGIC = new DustParticleOptions(0xCEC5E4, .6F);
    private final Map<UUID, WeakReference<SkeletonArcaneArrow>> ownedArrows = new HashMap<>();
    private @Nullable Vec3 teleportObservation;

    public SkeletonEnderman(EntityType<? extends SkeletonEnderman> type, Level level) {
        super(type, level); this.setCanPickUpLoot(false); this.applyRuntimeAttributes(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, SkeletonEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, SkeletonEndermanConfig.DEFAULT_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, SkeletonEndermanConfig.DEFAULT_SPEED).add(Attributes.FOLLOW_RANGE, SkeletonEndermanConfig.DEFAULT_RANGE)
                .add(Attributes.ATTACK_KNOCKBACK, .25);
    }
    private void applyRuntimeAttributes(boolean refill) {
        if (this.level().isClientSide() || !SkeletonEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SkeletonEndermanConfig.health());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(SkeletonEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SkeletonEndermanConfig.movementSpeed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SkeletonEndermanConfig.followRange());
        this.setHealth(refill ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }
    @Override protected SkeletonEndermanBrain createMastermindBrain() { return new SkeletonEndermanBrain(this); }
    @Override public SkeletonEndermanBrain getMastermindBrain() { return (SkeletonEndermanBrain)super.getMastermindBrain(); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data); data.define(PHASE, (byte)0); data.define(PHASE_START, 0L); data.define(PHASE_END, 0L);
        data.define(BUDGET, 0); data.define(ELEMENT, (byte)0); data.define(HUNTING, false);
    }
    public CombatPhase getCombatPhase() { int value = this.entityData.get(PHASE); return value >= 0 && value < PHASES.length ? PHASES[value] : CombatPhase.IDLE; }
    public long getCombatPhaseStart() { return this.entityData.get(PHASE_START); }
    public int getWarmupTicksRemaining() { return (int)Math.max(0, this.entityData.get(PHASE_END) - this.level().getGameTime()); }
    public int getVolleyBudget() { return Mth.clamp(this.entityData.get(BUDGET), 0, SkeletonEndermanConfig.MAX_VOLLEY_ARROWS); }
    public int getSelectedElementId() { return this.entityData.get(ELEMENT); }
    public Element getSelectedElement() { int value = this.getSelectedElementId(); return value >= 0 && value < ELEMENTS.length ? ELEMENTS[value] : ELEMENTS[0]; }
    public boolean isActivelyHunting() { return this.entityData.get(HUNTING); }
    public float getEyeBrightness() { return this.getCombatPhase().isCasting() ? 1 : this.isActivelyHunting() ? .9F : .45F; }
    public void setActivelyHunting(boolean hunting) { if (!this.level().isClientSide()) this.entityData.set(HUNTING, hunting); }
    public void setFormation(int budget, Element element) {
        if (this.level().isClientSide()) return;
        this.entityData.set(BUDGET, Mth.clamp(budget, 0, SkeletonEndermanConfig.MAX_VOLLEY_ARROWS));
        this.entityData.set(ELEMENT, (byte)element.ordinal());
    }
    public void setCombatPhase(CombatPhase phase) { this.setCombatPhase(phase, 0); }
    public void setCombatPhase(CombatPhase phase, long end) {
        if (this.level().isClientSide()) return;
        if (phase != this.getCombatPhase()) { this.entityData.set(PHASE_START, this.level().getGameTime()); this.entityData.set(PHASE, (byte)phase.ordinal()); }
        if (this.entityData.get(PHASE_END) != end) this.entityData.set(PHASE_END, end);
    }
    public boolean isLegalArrowTarget(LivingEntity target) {
        return target != this && target.level() == this.level() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !(target instanceof EnderMan) && this.canAttack(target) && !this.isAlliedTo(target)
                && !(target instanceof Player player && player.isCreative())
                && !(target instanceof ServerPlayer player && player.hasDisconnected());
    }
    public Vec3 getRangedAimPoint(LivingEntity target) { return target.position().add(0, target.getBbHeight() * .55, 0); }
    public Vec3 getMuzzlePosition(int index, int budget) {
        double angle = budget <= 1 ? 0 : (index - (budget - 1) * .5) * Math.PI / Math.max(4, budget);
        return this.position().add(Math.sin(angle) * .5, 1.85 + Math.cos(angle) * .12, Math.cos(angle) * .5);
    }
    public boolean hasClearArrowPath(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level) || !this.isLegalArrowTarget(target)) return false;
        Vec3 from = this.getMuzzlePosition(0, 1), to = this.getRangedAimPoint(target);
        BlockPos low = BlockPos.containing(Math.min(from.x, to.x) - 1, Math.min(from.y, to.y) - 1, Math.min(from.z, to.z) - 1);
        BlockPos high = BlockPos.containing(Math.max(from.x, to.x) + 1, Math.max(from.y, to.y) + 1, Math.max(from.z, to.z) + 1);
        return readyChunks(level, low, high) && level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    public boolean canReserveArrows(int count) {
        if (!(this.level() instanceof ServerLevel level) || count <= 0 || count > SkeletonEndermanConfig.MAX_VOLLEY_ARROWS) return false;
        this.pruneArrows(level.getGameTime()); return this.ownedArrows.size() + count <= MAX_OWNED_ARROWS;
    }
    public boolean launchArrow(ServerLevel level, LivingEntity target, Element element, double rawDamage, double speed, int index, int budget) {
        if (this.level() != level || this.isNoAi() || !this.isAlive() || this.isRemoved() || !this.isLegalArrowTarget(target)
                || !this.hasClearArrowPath(target) || this.ownedArrows.size() >= MAX_OWNED_ARROWS
                || !this.getMastermindBrain().claimArrowRelease(target)) return false;
        Vec3 muzzle = this.getMuzzlePosition(index, budget);
        if (!readyChunks(level, BlockPos.containing(muzzle), BlockPos.containing(muzzle))
                || !level.getBlockState(BlockPos.containing(muzzle)).getCollisionShape(level, BlockPos.containing(muzzle)).isEmpty()) return false;
        var arrow = SkeletonArcaneArrow.create(level, this, target, muzzle, this.getRangedAimPoint(target), element, rawDamage, speed);
        this.ownedArrows.put(arrow.getUUID(), new WeakReference<>(arrow));
        if (!level.addFreshEntity(arrow)) { this.ownedArrows.remove(arrow.getUUID()); arrow.discard(); return false; }
        return true;
    }
    public boolean isArrowValid(SkeletonArcaneArrow arrow) {
        var reference = this.ownedArrows.get(arrow.getUUID());
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && arrow.level() == this.level() && reference != null && reference.get() == arrow;
    }
    public void onArrowRemoved(SkeletonArcaneArrow arrow) { var reference = this.ownedArrows.get(arrow.getUUID()); if (reference != null && reference.get() == arrow) this.ownedArrows.remove(arrow.getUUID()); }
    public void clearOwnedArrows() {
        var previous = List.copyOf(this.ownedArrows.values()); this.ownedArrows.clear();
        for (var reference : previous) { var arrow = reference.get(); if (arrow != null && !arrow.isRemoved()) arrow.discard(); }
    }
    private void pruneArrows(long now) {
        for (var reference : List.copyOf(this.ownedArrows.values())) { var arrow = reference.get(); if (arrow != null && !arrow.isRemoved() && arrow.isExpired(now)) arrow.discard(); }
        this.ownedArrows.entrySet().removeIf(entry -> { var arrow = entry.getValue().get(); return arrow == null || arrow.isRemoved(); });
    }
    public void arrowWarning(ServerLevel level) {
        int count = Math.min(12, 2 + this.getVolleyBudget() / 2);
        level.sendParticles(MAGIC, this.getX(), this.getY(0.65), this.getZ(), count, .45, .4, .45, .02);
        this.playSound(SoundEvents.ENDERMAN_STARE, this.getCombatPhase() == CombatPhase.DEATHS_WINDUP ? .7F : .45F, .75F);
    }
    public void arrowReleaseCue(ServerLevel level) { this.playSound(SoundEvents.SKELETON_SHOOT, .8F, .75F); }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.getCombatPhase().isCommitted() || !(target instanceof LivingEntity victim) || !this.isLegalArrowTarget(victim)
                || !this.isWithinMeleeAttackRange(victim) || !this.getSensing().hasLineOfSight(victim)) return false;
        boolean hit = super.doHurtTarget(level, target);
        this.setCombatPhase(CombatPhase.MELEE);
        return hit;
    }
    public @Nullable Vec3 findRangedGroundPosition(Vec3 observed, boolean left) {
        if (!observed.isFinite() || !this.getMastermindBrain().isTargetLocalized()
                || this.getMastermindBrain().getLastLocatedPosition() == null
                || this.getMastermindBrain().getLastLocatedPosition().distanceToSqr(observed) > .01) return null;
        Vec3 away = this.position().subtract(observed).multiply(1,0,1);
        if (away.horizontalDistanceSqr() < .01) away = new Vec3(1,0,0);
        away = away.normalize(); Vec3 side = new Vec3(-away.z,0,away.x).scale(left ? 3 : -3);
        return this.findSafeTeleportDestination(observed.add(away.scale(9)).add(side).add(0,2,0), 5);
    }
    public boolean tryRangedReposition(Vec3 observed, boolean left) {
        if (!(this.level() instanceof ServerLevel level) || !observed.isFinite() || this.isNoAi() || this.getCombatPhase().isCommitted()
                || !this.getMastermindBrain().isTargetLocalized() || this.getMastermindBrain().getLastLocatedPosition() == null
                || this.getMastermindBrain().getLastLocatedPosition().distanceToSqr(observed) > .01) return false;
        Vec3 away = this.position().subtract(observed).multiply(1, 0, 1);
        if (away.horizontalDistanceSqr() < .01) away = new Vec3(1,0,0);
        away = away.normalize(); Vec3 side = new Vec3(-away.z, 0, away.x).scale(left ? 1 : -1);
        this.teleportObservation = observed;
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                Vec3 candidate = observed.add(away.scale(8 + attempt * 2)).add(side.scale(3 + attempt));
                if (this.trySafeTeleport(candidate.add(0,2,0), 5)) return true;
            }
            return false;
        } finally { this.teleportObservation = null; }
    }
    @Override protected int teleportAttemptCooldownTicks() { return SkeletonEndermanConfig.tacticalTeleportCooldownTicks(); }
    @Override protected boolean teleport() { return !this.getCombatPhase().isCommitted() && super.teleport(); }
    @Override protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        if (this.teleportObservation == null) return true;
        double horizontal = destination.subtract(this.teleportObservation).horizontalDistanceSqr();
        return horizontal >= 6 * 6 && horizontal <= 16 * 16 && Math.abs(destination.y - this.teleportObservation.y) <= 4;
    }
    /** Passive movement searches local loaded terrain; it never teleports straight to perfect sun cover. */
    public @Nullable Vec3 findNearbyShade() {
        if (!(this.level() instanceof ServerLevel level) || !this.isOnFire() || !level.isBrightOutside()) return null;
        for (int attempt = 0; attempt < 12; attempt++) {
            Vec3 requested = this.position().add(this.random.nextInt(17) - 8, 2, this.random.nextInt(17) - 8);
            Vec3 candidate = this.findSafeTeleportDestination(requested, 5);
            if (candidate != null && !level.canSeeSky(BlockPos.containing(candidate).above(Mth.ceil(this.getEyeHeight())))) return candidate;
        }
        return null;
    }
    @Override public void aiStep() {
        if (!this.level().isClientSide()) {
            if (this.isNoAi() || !this.isAlive()) this.getMastermindBrain().clearEncounter();
            else if (this.tickCount % 10 == 0) this.pruneArrows(this.level().getGameTime());
        }
        // Mob's BURN_IN_DAYLIGHT tag supplies the exact native Skeleton light/weather/headgear behavior.
        super.aiStep();
    }
    @Override public void travel(Vec3 input) {
        if (!this.level().isClientSide() && this.getCombatPhase().isCommitted()) {
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO);
        } else super.travel(input);
    }
    @Override public void die(DamageSource source) { super.die(source); if (!this.isAlive() && !this.level().isClientSide()) this.getMastermindBrain().clearEncounter(); }
    @Override public void onRemoval(Entity.RemovalReason reason) { if (!this.level().isClientSide() && this.getMastermindBrain() != null) this.getMastermindBrain().clearEncounter(); super.onRemoval(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); this.getMastermindBrain().writeCooldowns(output); }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.applyRuntimeAttributes(false); this.getMastermindBrain().clearEncounter(); this.getMastermindBrain().readCooldowns(input);
    }
    public static boolean isSpawnTime(ServerLevelAccessor level) { return level.getLevel().dimension() == Level.OVERWORLD; }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        if (!isSpawnTime(level) || !readyChunks(level.getLevel(), pos, pos)
                || !(level.getBiome(pos).is(Biomes.PLAINS) || level.getBiome(pos).is(Biomes.DARK_FOREST))) return false;
        return level.getLevel().getEntitiesOfClass(SkeletonEnderman.class, new AABB(pos).inflate(64), mob -> mob.isAlive() && !mob.isRemoved()).size() < SkeletonEndermanConfig.naturalPopulationCap();
    }
    public static boolean checkSkeletonSpawnRules(EntityType<SkeletonEnderman> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        BlockState support = level.getBlockState(pos.below());
        if (!support.isFaceSturdy(level, pos.below(), Direction.UP) || !support.getFluidState().isEmpty() || support.is(Blocks.MAGMA_BLOCK)) return false;
        for (int y = 0; y < Mth.ceil(type.getHeight()); y++) {
            BlockPos cell = pos.above(y); BlockState block = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, block, block.getFluidState(), type)) return false;
        }
        return true;
    }
    public static boolean readyChunks(ServerLevel level, BlockPos low, BlockPos high) {
        for (int x = low.getX() >> 4; x <= high.getX() >> 4; x++) for (int z = low.getZ() >> 4; z <= high.getZ() >> 4; z++)
            if (level.getChunkSource().getChunkNow(x,z) == null) return false;
        return true;
    }
    public enum CombatPhase {
        IDLE, PURSUING, SEARCHING, SEEKING_SHADE, MELEE, ARCANE_WINDUP, ARCANE_VOLLEY,
        CURSED_WINDUP, CURSED_ARROW, DEATHS_WINDUP, DEATHS_BARRAGE, RECOVERY;
        public boolean isCasting() { return this == ARCANE_WINDUP || this == ARCANE_VOLLEY || this == CURSED_WINDUP || this == CURSED_ARROW || this == DEATHS_WINDUP || this == DEATHS_BARRAGE; }
        public boolean isCommitted() { return this.isCasting() || this == RECOVERY; }
    }
}
