package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.ai.GrayEndermanBrain;
import com.ronm19.enderism.entity.ai.GrayEndermanTuning;
import com.ronm19.enderism.entity.ai.MastermindBrain;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Enderism #002: a Pale Garden sensory hunter, independent of Brown's gaze/charge mechanics. */
public final class GrayEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_gray_enderman"));
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(
            GrayEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> ECLIPSE_WARNING = SynchedEntityData.defineId(
            GrayEnderman.class, EntityDataSerializers.BOOLEAN);
    private static final HuntState[] HUNT_STATES = HuntState.values();
    private static final DustParticleOptions AMBER = new DustParticleOptions(0xF1AE42, 0.65F);
    private long nextPaleAmbushAt;

    public GrayEnderman(EntityType<? extends GrayEnderman> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes()
                .add(Attributes.FOLLOW_RANGE, GrayEndermanTuning.DETECTION_RANGE)
                .add(Attributes.ATTACK_DAMAGE, GrayEndermanTuning.ATTACK_DAMAGE)
                .add(Attributes.ATTACK_KNOCKBACK, GrayEndermanTuning.ATTACK_KNOCKBACK);
    }

    @Override
    protected MastermindBrain createMastermindBrain() {
        return new GrayEndermanBrain(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(HUNT_STATE, (byte) HuntState.IDLE.ordinal());
        data.define(ECLIPSE_WARNING, false);
    }

    public HuntState getHuntState() {
        int ordinal = this.entityData.get(HUNT_STATE);
        return ordinal >= 0 && ordinal < HUNT_STATES.length ? HUNT_STATES[ordinal] : HuntState.IDLE;
    }

    public void setHuntState(HuntState state) {
        if (!this.level().isClientSide()) {
            this.entityData.set(HUNT_STATE, (byte) state.ordinal());
        }
    }

    public boolean isEclipseWindingUp() {
        return this.entityData.get(ECLIPSE_WARNING);
    }

    public void setEclipseWindingUp(boolean active) {
        if (!this.level().isClientSide()) {
            this.entityData.set(ECLIPSE_WARNING, active);
        }
    }

    public float getEyeBrightness() {
        if (this.isEclipseWindingUp()) {
            return 1.0F;
        }
        return switch (this.getHuntState()) {
            case IDLE, RESETTING -> 0.45F;
            case SENSING, OBSERVING -> 0.62F;
            case SEARCHING -> 0.72F;
            case STALKING -> 0.85F;
            case AMBUSHING, ATTACKING -> 0.95F;
        };
    }

    public void beginEclipseWarning(ServerLevel level) {
        this.setEclipseWindingUp(true);
        this.playSound(SoundEvents.ENDERMAN_STARE, 0.65F, 1.25F);
        level.sendParticles(AMBER, this.getX(), this.getEyeY(), this.getZ(), 5, 0.25, 0.1, 0.25, 0.0);
    }

    public void finishEclipseEffects(ServerLevel level) {
        this.playSound(SoundEvents.ENDERMAN_SCREAM, 0.55F, 1.3F);
        level.sendParticles(AMBER, this.getX(), this.getEyeY(), this.getZ(), 8, 0.3, 0.15, 0.3, 0.0);
    }

    public long nextPaleAmbushReadyAt() {
        return this.nextPaleAmbushAt;
    }

    /** Uses a remembered point, never the hidden player's continuously updated coordinates. */
    public boolean attemptPaleAmbush(Vec3 lastLocated, Vec3 observedMotion) {
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved()
                || this.isPassenger() || this.isVehicle()
                || level.getGameTime() < this.nextPaleAmbushAt
                || !finite(lastLocated) || !finite(observedMotion)
                || this.position().distanceToSqr(lastLocated) > GrayEndermanTuning.DETECTION_RANGE * GrayEndermanTuning.DETECTION_RANGE) {
            return false;
        }
        // Failed tree/destination searches consume the ability cooldown too.
        this.nextPaleAmbushAt = level.getGameTime() + GrayEndermanTuning.PALE_AMBUSH_COOLDOWN_TICKS;
        Vec3 destination = this.findPaleAmbushDestination(level, lastLocated, observedMotion);
        if (destination == null || !this.trySafeTeleport(destination, 0)) {
            return false;
        }
        this.getLookControl().setLookAt(lastLocated.x, lastLocated.y + 1.5, lastLocated.z, 90.0F, 90.0F);
        this.setHuntState(HuntState.AMBUSHING);
        level.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 1.0, this.getZ(), 6, 0.3, 0.8, 0.3, 0.01);
        return true;
    }

    private @Nullable Vec3 findPaleAmbushDestination(ServerLevel level, Vec3 remembered, Vec3 motion) {
        BlockPos center = BlockPos.containing(this.position().lerp(remembered, 0.65));
        int radius = GrayEndermanTuning.PALE_AMBUSH_SCAN_RADIUS;
        int vertical = GrayEndermanTuning.PALE_AMBUSH_VERTICAL_RADIUS;
        int examined = 0;
        int destinationsExamined = 0;
        double bestScore = Double.POSITIVE_INFINITY;
        Vec3 best = null;
        // Visit inner rings first; the strict budget bounds work even in dense player-built forests.
        search:
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    BlockPos column = center.offset(dx, 0, dz);
                    if (!level.hasChunkAt(column)) continue;
                    for (int dy = -vertical; dy <= vertical; dy++) {
                        if (++examined > GrayEndermanTuning.PALE_AMBUSH_BLOCK_BUDGET) break search;
                        BlockPos anchor = column.offset(0, dy, 0);
                        if (!level.getBlockState(anchor).is(Blocks.PALE_OAK_LOG)) continue;
                        for (int[] offset : TREE_OFFSETS) {
                            if (++destinationsExamined > GrayEndermanTuning.PALE_AMBUSH_DESTINATION_BUDGET) break search;
                            Vec3 candidate = this.standingBeside(level, anchor.offset(offset[0], 2, offset[1]));
                            if (candidate == null || this.position().distanceToSqr(candidate) < 9.0) continue;
                            double targetDistance = candidate.distanceToSqr(remembered);
                            if (targetDistance < square(GrayEndermanTuning.PALE_AMBUSH_MIN_TARGET_DISTANCE)
                                    || targetDistance > square(GrayEndermanTuning.PALE_AMBUSH_MAX_TARGET_DISTANCE)) continue;
                            Vec3 towardCandidate = candidate.subtract(remembered);
                            // Favor a side/interception angle, with a modest lead only from sampled movement.
                            double intercept = towardCandidate.x * motion.x + towardCandidate.z * motion.z;
                            double score = Math.abs(targetDistance - 25.0) - Math.min(3.0, intercept)
                                    + this.position().distanceToSqr(candidate) * 0.01;
                            if (score < bestScore) {
                                best = candidate;
                                bestScore = score;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private static final int[][] TREE_OFFSETS = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};

    private @Nullable Vec3 standingBeside(ServerLevel level, BlockPos requested) {
        if (!level.hasChunkAt(requested)) return null;
        BlockPos feet = requested;
        for (int i = 0; i <= 6; i++, feet = feet.below()) {
            if (feet.getY() <= level.getMinY() || feet.getY() + 3 > level.getMaxY()) return null;
            if (!level.getBlockState(feet.below()).blocksMotion()) continue;
            if (!level.getWorldBorder().isWithinBounds(feet)) return null;
            Vec3 position = Vec3.atBottomCenterOf(feet);
            AABB body = this.getBoundingBox().move(position.subtract(this.position()));
            BlockPos min = BlockPos.containing(body.minX, body.minY - 1, body.minZ);
            BlockPos max = BlockPos.containing(body.maxX, body.maxY, body.maxZ);
            if (!level.hasChunksAt(min, max) || !level.getWorldBorder().isWithinBounds(body)
                    || !level.noCollision(this, body) || level.containsAnyLiquid(body)) return null;
            // The shared teleport validator authoritatively repeats hazard/event checks before moving.
            return position;
        }
        return null;
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static double square(double value) {
        return value * value;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.7F, 1.2F);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.6), target.getZ(),
                    3, 0.15, 0.2, 0.15, 0.02);
        }
        return hit;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.setEclipseWindingUp(false);
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.getMastermindBrain().clearEncounter();
        this.setHuntState(HuntState.IDLE);
        this.setEclipseWindingUp(false);
    }

    public static boolean checkGraySpawnRules(EntityType<GrayEnderman> type, ServerLevelAccessor level,
                                             EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        for (int offset = 0; offset < 3; offset++) {
            BlockPos cell = pos.above(offset);
            var block = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, block, block.getFluidState(), type)) return false;
        }
        return true;
    }

    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) {
        return isSpawnTime(level) && level.getBiome(pos).is(SPAWN_BIOMES);
    }

    public static boolean isSpawnTime(ServerLevelAccessor level) {
        return level.getLevel().dimension() == Level.OVERWORLD;
    }

    public enum HuntState {
        IDLE, SENSING, OBSERVING, STALKING, AMBUSHING, ATTACKING, SEARCHING, RESETTING
    }
}
