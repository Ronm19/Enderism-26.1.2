package com.ronm19.enderism.combat;

import com.ronm19.enderism.SoulSandEndermanConfig;
import com.ronm19.enderism.entity.SoulSandEnderman;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/** Bounded native ground paths; soul-sand travel time can justify a small, safe local detour. */
public final class SoulSandHuntPlanner {
    private final SoulSandEnderman owner;
    private final Map<BlockPos, Long> failed = new LinkedHashMap<>();
    private int lastCandidateChecks;
    public SoulSandHuntPlanner(SoulSandEnderman owner) { this.owner = owner; }
    public record Plan(Vec3 destination, Path path, int soulSandNodes, double score) { }
    public int getLastCandidateChecks() { return this.lastCandidateChecks; }
    public void clear() { this.failed.clear(); this.lastCandidateChecks = 0; }
    public void failed(Vec3 point, long now) {
        this.failed.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (this.failed.size() >= 12) this.failed.remove(this.failed.keySet().iterator().next());
        this.failed.put(BlockPos.containing(point), now + 80);
    }
    public @Nullable Plan plan(ServerLevel level, Vec3 observedAnchor, long now) {
        this.lastCandidateChecks = 0; this.failed.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (!observedAnchor.isFinite()) return null;
        Vec3 delta = observedAnchor.subtract(this.owner.position()).multiply(1, 0, 1); double distance = delta.length();
        if (distance < .5 || distance > SoulSandEndermanConfig.followRange() + 10) return null;
        Vec3 forward = delta.normalize(), side = new Vec3(-forward.z, 0, forward.x);
        // Intermediates allow a common hunter to favor locally faster terrain without scanning the valley.
        Vec3 center = distance > 10 ? this.owner.position().add(forward.scale(9)) : observedAnchor;
        Plan best = null;
        int budget = SoulSandEndermanConfig.routeCandidates();
        for (int index = 0; index < budget; index++) {
            Vec3 requested;
            if (index == 0) requested = center;
            else {
                double offset = SoulSandEndermanConfig.routePreferenceRadius() * ((index + 1) / 2) / 3.0;
                double sign = (index & 1) == 0 ? -1 : 1;
                requested = center.add(side.scale(offset * sign));
            }
            this.lastCandidateChecks++;
            Plan plan = this.reachable(level, requested, observedAnchor, now);
            if (plan != null && (index == 0 || plan.soulSandNodes() > 0) && (best == null || plan.score() < best.score())) best = plan;
        }
        // A visible victim can occupy the observation cell. A nearby safe approach cell remains useful.
        if (best == null && distance <= 10) {
            Vec3 requested = observedAnchor.subtract(forward.scale(1.4));
            this.lastCandidateChecks++; best = this.reachable(level, requested, observedAnchor, now);
        }
        return best;
    }
    private @Nullable Plan reachable(ServerLevel level, Vec3 requested, Vec3 goal, long now) {
        Vec3 destination = this.standing(level, requested);
        if (destination == null || this.failed.containsKey(BlockPos.containing(destination)) || !loaded(level, new AABB(this.owner.position(), destination).inflate(2))) return null;
        int length = Math.min(56, Math.max(16, (int)Math.ceil(this.owner.position().distanceTo(destination)) + 14));
        Path path = this.owner.getNavigation().createPath(BlockPos.containing(destination.add(0, .2, 0)), 0, length);
        if (path == null || !path.canReach() || path.getNodeCount() == 0 || path.getNodeCount() > 72) { this.failed(destination, now); return null; }
        int sand = 0; double cost = 0; Vec3 previous = this.owner.position();
        for (int i = 0; i < path.getNodeCount(); i++) {
            var node = path.getNode(i); Vec3 point = new Vec3(node.x + .5, node.y, node.z + .5);
            if (!this.safeStanding(level, point)) { this.failed(destination, now); return null; }
            boolean soul = level.getBlockState(BlockPos.containing(point.x, point.y - .2, point.z)).is(Blocks.SOUL_SAND);
            if (soul) sand++;
            cost += previous.distanceTo(point) / (soul ? SoulSandEndermanConfig.soulSandMovementMultiplier() : 1); previous = point;
        }
        return new Plan(destination, path, sand, cost + destination.distanceTo(goal));
    }
    public @Nullable Vec3 standing(ServerLevel level, Vec3 requested) {
        if (!requested.isFinite()) return null;
        BlockPos base = BlockPos.containing(requested);
        for (int offset : new int[] {0, -1, 1, -2, 2}) {
            BlockPos support = base.offset(0, offset, 0);
            if (level.getChunkSource().getChunkNow(support.getX() >> 4, support.getZ() >> 4) == null) continue;
            BlockState state = level.getBlockState(support); var shape = state.getCollisionShape(level, support, CollisionContext.of(this.owner));
            if (shape.isEmpty() || hazard(state)) continue;
            Vec3 point = new Vec3(support.getX() + .5, support.getY() + shape.max(Direction.Axis.Y), support.getZ() + .5);
            if (this.safeStanding(level, point)) return point;
        }
        return null;
    }
    public boolean safeStanding(ServerLevel level, Vec3 point) {
        if (!point.isFinite()) return false;
        AABB body = this.owner.getBoundingBox().move(point.subtract(this.owner.position()));
        if (body.minY < level.getMinY() + 1 || body.maxY >= level.getMaxY() || !level.getWorldBorder().isWithinBounds(body)
                || !loaded(level, body.expandTowards(0, -1, 0).inflate(.02)) || !level.noCollision(this.owner, body) || level.containsAnyLiquid(body)) return false;
        for (double x : new double[] {body.minX + .02, body.maxX - .02}) for (double z : new double[] {body.minZ + .02, body.maxZ - .02}) {
            BlockPos support = BlockPos.containing(x, point.y - .2, z); BlockState state = level.getBlockState(support);
            var shape = state.getCollisionShape(level, support, CollisionContext.of(this.owner));
            if (shape.isEmpty() || hazard(state)) return false;
            double gap = point.y - (support.getY() + shape.max(Direction.Axis.Y));
            if (gap < -.001 || gap > .2 || !state.isFaceSturdy(level, support, Direction.UP)) return false;
        }
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ), BlockPos.containing(body.maxX, body.maxY, body.maxZ))) if (hazard(level.getBlockState(cell))) return false;
        return true;
    }
    public static boolean loaded(ServerLevel level, AABB box) {
        for (int x = ((int)Math.floor(box.minX)) >> 4; x <= ((int)Math.floor(box.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(box.minZ)) >> 4; z <= ((int)Math.floor(box.maxZ)) >> 4; z++) if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    private static boolean hazard(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.SWEET_BERRY_BUSH);
    }
}
