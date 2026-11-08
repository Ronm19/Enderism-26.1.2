package com.ronm19.enderism.combat;

import com.ronm19.enderism.WhiteEndermanConfig;
import com.ronm19.enderism.entity.WhiteEnderman;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Small encounter-local ground plans, based exclusively on observations supplied by the native Brain. */
public final class WhiteHuntPlanner {
    private final WhiteEnderman owner;
    private final Map<BlockPos, Long> failed = new LinkedHashMap<>();
    private int lastCandidateChecks;
    public WhiteHuntPlanner(WhiteEnderman owner) { this.owner = owner; }
    public record Plan(Vec3 destination, Path path, boolean covered, double angleScore) { }
    public int getLastCandidateChecks() { return this.lastCandidateChecks; }
    public int getRememberedFailures() { this.expire(this.owner.level().getGameTime()); return this.failed.size(); }
    public void clear() { this.failed.clear(); this.lastCandidateChecks = 0; }
    public void failed(Vec3 point, long now) {
        this.expire(now);
        BlockPos pos = BlockPos.containing(point);
        if (this.failed.size() >= 16 && !this.failed.containsKey(pos)) this.failed.remove(this.failed.keySet().iterator().next());
        this.failed.put(pos, now + Math.max(80, WhiteEndermanConfig.memoryTicks()));
    }
    private void expire(long now) { this.failed.entrySet().removeIf(entry -> entry.getValue() <= now); }

    public @Nullable Plan flank(ServerLevel level, Vec3 anchor, Vec3 observedFacing, Vec3 observedEye, boolean left, boolean apex, long now) {
        this.expire(now); this.lastCandidateChecks = 0;
        if (!anchor.isFinite() || !observedFacing.isFinite() || !observedEye.isFinite()) return null;
        Vec3 facing = observedFacing.multiply(1, 0, 1).normalize();
        if (facing.lengthSqr() < .01) facing = new Vec3(0, 0, 1);
        double radius = WhiteEndermanConfig.flankRadius() * (apex ? .85 : 1);
        Plan best = null; double bestScore = -Double.MAX_VALUE;
        int budget = WhiteEndermanConfig.flankCandidates();
        for (int index = 0; index < budget; index++) {
            this.lastCandidateChecks++;
            double angle = Math.toRadians(65 + (index / 2) * 30), sign = ((index & 1) == 0) == left ? 1 : -1;
            Vec3 radial = rotate(facing, sign * angle);
            Vec3 requested = anchor.add(radial.scale(radius));
            Plan plan = this.reachable(level, requested, observedEye, now);
            if (plan == null || plan.destination().distanceToSqr(this.owner.position()) < 2.25) continue;
            double rear = -facing.dot(plan.destination().subtract(anchor).multiply(1, 0, 1).normalize());
            double score = rear * (apex ? 4 : 2) + (plan.covered() ? 3 : 0) - plan.path().getNodeCount() * .055;
            if (score > bestScore) { bestScore = score; best = new Plan(plan.destination(), plan.path(), plan.covered(), rear); }
        }
        return best;
    }

    /** Each candidate is a plausible fixed place around the last seen point; there is no live hidden target argument. */
    public @Nullable Plan investigate(ServerLevel level, Vec3 anchor, Vec3 observedDirection, Vec3 observedEye, int index, long now) {
        this.expire(now); this.lastCandidateChecks = 1;
        if (index < 0 || index >= WhiteEndermanConfig.trackingCandidates() || !anchor.isFinite() || !observedDirection.isFinite()) return null;
        Vec3 direction = observedDirection.multiply(1, 0, 1).normalize();
        if (direction.lengthSqr() < .01) direction = anchor.subtract(this.owner.position()).multiply(1, 0, 1).normalize();
        if (direction.lengthSqr() < .01) direction = new Vec3(0, 0, 1);
        Vec3 point;
        if (index == 0) point = anchor;
        else {
            int pair = index / 2; double sign = (index & 1) == 0 ? -1 : 1;
            double distance = Math.min(WhiteEndermanConfig.trackingRadius(), 2.5 + pair * 1.5);
            double angle = sign * Math.toRadians(pair * 60);
            point = anchor.add(rotate(direction, angle).scale(distance));
        }
        return this.reachable(level, point, observedEye, now);
    }

    public @Nullable Plan approach(ServerLevel level, Vec3 point, Vec3 observedEye, long now) {
        this.lastCandidateChecks = 1; this.expire(now);
        Plan plan = this.reachable(level, point, observedEye, now);
        if (plan != null) return plan;
        // A visible victim can occupy the exact observation cell. Approach adjacent reachable
        // ground instead of treating that ordinary collision as an impossible pursuit route.
        Vec3 near = this.owner.position().subtract(point).multiply(1, 0, 1).normalize();
        if (near.lengthSqr() < .01) near = new Vec3(1, 0, 0);
        for (double angle : new double[] {0, Math.PI / 2, -Math.PI / 2}) {
            this.lastCandidateChecks++;
            plan = this.reachable(level, point.add(rotate(near, angle).scale(1.35)), observedEye, now);
            if (plan != null) return plan;
        }
        return null;
    }
    private @Nullable Plan reachable(ServerLevel level, Vec3 requested, Vec3 observedEye, long now) {
        Vec3 destination = this.standing(level, requested);
        if (destination == null || this.failed.containsKey(BlockPos.containing(destination))) return null;
        AABB corridor = new AABB(this.owner.position(), destination).inflate(2);
        if (!loaded(level, corridor)) return null;
        int maximumPathLength = Math.min(64, Math.max(12, (int)Math.ceil(this.owner.position().distanceTo(destination)) + 14));
        // The native PathNavigationRegion uses getChunkNow and empty ungenerated regions. We also
        // reject every actual path node outside loaded safe terrain before handing it to navigation.
        Path path = this.owner.getNavigation().createPath(BlockPos.containing(destination), 0, maximumPathLength);
        if (path == null || !path.canReach() || path.getNodeCount() == 0 || path.getNodeCount() > 80) { this.failed(destination, now); return null; }
        for (int n = 0; n < path.getNodeCount(); n++) {
            var node = path.getNode(n); Vec3 point = new Vec3(node.x + .5, node.y, node.z + .5);
            if (!this.safeStanding(level, point)) { this.failed(destination, now); return null; }
        }
        Vec3 sight = destination.add(0, this.owner.getBbHeight() * .65, 0);
        boolean cover = loaded(level, new AABB(observedEye, sight).inflate(.05))
                && level.clip(new ClipContext(observedEye, sight, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.owner)).getType() != HitResult.Type.MISS;
        return new Plan(destination, path, cover, 0);
    }
    private @Nullable Vec3 standing(ServerLevel level, Vec3 requested) {
        if (!requested.isFinite()) return null;
        int y = (int)Math.floor(requested.y);
        for (int offset : new int[] {0, 1, -1, 2, -2}) {
            Vec3 point = new Vec3(Math.floor(requested.x) + .5, y + offset, Math.floor(requested.z) + .5);
            if (this.safeStanding(level, point)) return point;
        }
        return null;
    }
    public boolean safeStanding(ServerLevel level, Vec3 point) {
        if (!point.isFinite()) return false;
        AABB body = this.owner.getBoundingBox().move(point.subtract(this.owner.position()));
        if (body.minY < level.getMinY() + 1 || body.maxY >= level.getMaxY() || !level.getWorldBorder().isWithinBounds(body)
                || !loaded(level, body.expandTowards(0, -1, 0).inflate(.02)) || !level.noCollision(this.owner, body) || level.containsAnyLiquid(body)) return false;
        int supportY = (int)Math.floor(body.minY - .01);
        for (double x : new double[] {body.minX + .02, body.maxX - .02}) for (double z : new double[] {body.minZ + .02, body.maxZ - .02}) {
            BlockPos support = BlockPos.containing(x, supportY, z); BlockState state = level.getBlockState(support);
            if (!state.isFaceSturdy(level, support, Direction.UP) || hazard(state)) return false;
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
    private static Vec3 rotate(Vec3 vector, double radians) { double c = Math.cos(radians), s = Math.sin(radians); return new Vec3(vector.x * c - vector.z * s, 0, vector.x * s + vector.z * c); }
}
