package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.VoidEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.VoidEnderman;
import java.util.ArrayDeque;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Per-creature safe-ground memory. This passive never grants flight, health or offensive damage. */
public final class VoidSurvivalInstinct {
    private static final int MEMORY_LIMIT = 4, SEARCH_BUDGET = 96;
    private static final int[][] DIRECTIONS = {{1,0},{0,1},{-1,0},{0,-1},{1,1},{-1,1},{-1,-1},{1,-1}};
    private final VoidEnderman owner;
    private final ArrayDeque<Ground> safeGround = new ArrayDeque<>();
    private @Nullable ResourceKey<Level> dimension;
    private long nextSampleAt, nextRiskAt, nextAttemptAt, recoveryUntil;
    private int failedAttempts, rescueCount, attemptCount;
    private boolean handling;

    public VoidSurvivalInstinct(VoidEnderman owner) { this.owner = owner; }
    public boolean isRecovering() { return this.owner.level().getGameTime() < this.recoveryUntil; }

    public void tick(ServerLevel level) {
        if (!this.eligible(level)) { this.clear(); return; }
        this.checkDimension(level);
        long now = level.getGameTime();
        this.safeGround.removeIf(ground -> !ground.dimension().equals(level.dimension())
                || now - ground.storedAt() > VoidEndermanConfig.rescueMemoryTicks());
        if (this.owner.onGround()) {
            if (now >= this.nextSampleAt) {
                this.nextSampleAt = now + VoidEndermanConfig.safeGroundSampleIntervalTicks();
                if (isStableGround(this.owner, level, this.owner.position())) this.remember(level, this.owner.position());
            }
            return;
        }
        if (now < this.nextRiskAt) return;
        this.nextRiskAt = now + 4;
        boolean belowTerrain = this.owner.getY() < level.getMinY() - 4;
        Ground recent = this.safeGround.peekFirst();
        boolean deepFall = this.owner.fallDistance >= VoidEndermanConfig.rescueDetectionDepth()
                || recent != null && this.owner.getY() < recent.position().y - VoidEndermanConfig.rescueDetectionDepth();
        if (belowTerrain || this.owner.getDeltaMovement().y < -.08 && deepFall && this.isEmptyColumn(level)) this.attemptRescue(level);
    }

    /** Called only by the entity's exact fell_out_of_world path, before ordinary Enderman damage handling. */
    public boolean handleVoidDamage(ServerLevel level) {
        if (!this.eligible(level)) return false;
        this.checkDimension(level);
        if (!this.owner.onGround() || !isStableGround(this.owner, level, this.owner.position())) this.attemptRescue(level);
        return true;
    }
    private boolean eligible(ServerLevel level) {
        return this.owner.level() == level && this.owner.isAlive() && !this.owner.isRemoved();
    }
    private void checkDimension(ServerLevel level) {
        if (!level.dimension().equals(this.dimension)) { this.clear(); this.dimension = level.dimension(); }
    }
    private boolean isEmptyColumn(ServerLevel level) {
        var chunk = level.getChunkSource().getChunkNow(this.owner.blockPosition().getX() >> 4, this.owner.blockPosition().getZ() >> 4);
        // Unknown or covered terrain is not proof of a fatal drop; the below-minimum safeguard handles it later.
        return chunk != null && chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                this.owner.blockPosition().getX(), this.owner.blockPosition().getZ()) < level.getMinY();
    }
    private void remember(ServerLevel level, Vec3 position) {
        this.safeGround.removeIf(ground -> ground.position().distanceToSqr(position) < 4);
        this.safeGround.addFirst(new Ground(level.dimension(), position, level.getGameTime()));
        while (this.safeGround.size() > MEMORY_LIMIT) this.safeGround.removeLast();
    }

    private void attemptRescue(ServerLevel level) {
        long now = level.getGameTime();
        if (this.handling || now < this.nextAttemptAt || !this.eligible(level)) return;
        this.handling = true; this.attemptCount++;
        this.nextAttemptAt = now + VoidEndermanConfig.rescueRetryTicks();
        this.recoveryUntil = now + VoidEndermanConfig.rescueRecoveryTicks();
        this.owner.getMastermindBrain().onVoidRescue(now);
        try {
            Vec3 destination = this.findGround(level);
            if (destination != null && this.owner.performRescueTeleport(destination) && this.eligible(level)) {
                this.owner.setDeltaMovement(Vec3.ZERO); this.owner.resetFallDistance(); this.owner.setOnGround(true);
                this.owner.needsSync = true;
                this.failedAttempts = 0; this.rescueCount++;
                this.nextAttemptAt = now + VoidEndermanConfig.rescueRecoveryTicks();
                this.remember(level, this.owner.position());
                this.owner.setCombatPhase(VoidEnderman.CombatPhase.VOID_RESCUE);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.owner.getX(), this.owner.getY() + 1.3,
                        this.owner.getZ(), 16, .35, .8, .35, .025);
            } else if (this.eligible(level)) {
                this.failedAttempts++;
                if (this.failedAttempts >= VoidEndermanConfig.rescueMaxBurstAttempts()) {
                    this.failedAttempts = 0; this.nextAttemptAt = now + VoidEndermanConfig.rescueBackoffTicks();
                }
            }
        } finally { this.handling = false; }
    }

    private @Nullable Vec3 findGround(ServerLevel level) {
        long now = level.getGameTime(); Ground first = this.safeGround.peekFirst();
        Vec3 hint = first == null ? this.owner.position() : first.position();
        Iterator<Ground> remembered = this.safeGround.iterator();
        while (remembered.hasNext()) {
            Ground ground = remembered.next();
            if (!ground.dimension().equals(level.dimension()) || now - ground.storedAt() > VoidEndermanConfig.rescueMemoryTicks()) {
                remembered.remove(); continue;
            }
            BlockPos pos = BlockPos.containing(ground.position());
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) continue;
            Vec3 destination = this.owner.planRescueDestination(ground.position(), 0);
            if (destination != null) return destination;
            remembered.remove();
        }
        int budget = SEARCH_BUDGET, radius = VoidEndermanConfig.rescueSearchRadius();
        Vec3[] origins = {hint, this.owner.position()};
        for (Vec3 origin : origins) {
            Vec3 center = this.surfaceAt(level, origin.x, origin.z);
            if (center != null) return center;
            for (int distance = 2; distance <= radius && budget > 0; distance += distance < 8 ? 2 : 4) {
                for (int[] direction : DIRECTIONS) {
                    if (--budget < 0) break;
                    Vec3 destination = this.surfaceAt(level, origin.x + direction[0] * distance, origin.z + direction[1] * distance);
                    if (destination != null) return destination;
                }
            }
        }
        return null;
    }
    private @Nullable Vec3 surfaceAt(ServerLevel level, double x, double z) {
        BlockPos column = BlockPos.containing(x, level.getMinY(), z);
        var chunk = level.getChunkSource().getChunkNow(column.getX() >> 4, column.getZ() >> 4);
        if (chunk == null || !level.getWorldBorder().isWithinBounds(column)) return null;
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) + 1;
        if (y <= level.getMinY() || y + this.owner.getBbHeight() > level.getMaxY()) return null;
        return this.owner.planRescueDestination(new Vec3(column.getX() + .5, y, column.getZ() + .5), 0);
    }

    /** Full body clearance and a solid top beneath every foot corner, including listener-adjusted destinations. */
    public static boolean isStableGround(AbstractEnderman owner, ServerLevel level, Vec3 position) {
        if (owner.level() != level || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || position.y < level.getMinY() + 1 || position.y + owner.getBbHeight() > level.getMaxY()) return false;
        AABB box = owner.getBoundingBox().move(position.subtract(owner.position()));
        BlockPos minimum = BlockPos.containing(box.minX, box.minY - 1, box.minZ);
        BlockPos maximum = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        long cells = (long)(maximum.getX() - minimum.getX() + 1) * (maximum.getY() - minimum.getY() + 1) * (maximum.getZ() - minimum.getZ() + 1);
        if (cells > 128 || !level.getWorldBorder().isWithinBounds(box)) return false;
        if (!hasReadyChunks(level, minimum, maximum)) return false;
        if (!level.noCollision(owner, box) || level.containsAnyLiquid(box)
                || !level.getEntities(owner, box.inflate(.05), entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator()).isEmpty()) return false;
        for (BlockPos cell : BlockPos.betweenClosed(minimum, maximum)) if (hazard(level.getBlockState(cell))) return false;
        for (double x : new double[]{box.minX + .001, box.maxX - .001}) for (double z : new double[]{box.minZ + .001, box.maxZ - .001}) {
            BlockPos below = BlockPos.containing(x, position.y - .01, z); BlockState support = level.getBlockState(below);
            if (!support.blocksMotion() || support.getBlock() instanceof FallingBlock || hazard(support)
                    || !support.isFaceSturdy(level, below, Direction.UP)
                    || support.getCollisionShape(level, below).max(Direction.Axis.Y) < .999) return false;
        }
        return true;
    }
    public static boolean hasReadyChunks(ServerLevel level, BlockPos minimum, BlockPos maximum) {
        for (int x = minimum.getX() >> 4; x <= maximum.getX() >> 4; x++) for (int z = minimum.getZ() >> 4; z <= maximum.getZ() >> 4; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    private static boolean hazard(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY) || state.is(Blocks.NETHER_PORTAL);
    }
    public void clear() {
        this.safeGround.clear(); this.dimension = null; this.nextSampleAt = 0; this.nextRiskAt = 0;
        this.nextAttemptAt = 0; this.recoveryUntil = 0; this.failedAttempts = 0; this.handling = false;
    }
    private record Ground(ResourceKey<Level> dimension, Vec3 position, long storedAt) {}
}
