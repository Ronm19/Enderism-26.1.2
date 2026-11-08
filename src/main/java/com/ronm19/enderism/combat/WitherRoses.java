package com.ronm19.enderism.combat;

import com.ronm19.enderism.WitherEndermanConfig;
import com.ronm19.enderism.entity.WitherEnderman;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/** Bounded placement of persistent, ordinary Wither Roses; there is no separate damage or restoration loop. */
public final class WitherRoses {
    private WitherRoses() { }
    public static boolean canPlace(ServerLevel level, WitherEnderman owner, LivingEntity target, Vec3 center) {
        return mayCast(level, owner, target, center) && !candidates(level, owner, center).isEmpty();
    }
    public static int place(ServerLevel level, WitherEnderman owner, LivingEntity target, Vec3 center) {
        if (!isReleasing(level, owner, target, center)) return 0;
        int placed = 0; var rose = Blocks.WITHER_ROSE.defaultBlockState();
        for (BlockPos pos : candidates(level, owner, center)) {
            if (placed >= WitherEndermanConfig.roseCount() || !isReleasing(level, owner, target, center)) break;
            var existing = nearbyRoses(level, center);
            if (existing.size() >= WitherEndermanConfig.roseLocalCap() || !valid(level, owner, center, pos, existing)) continue;
            if (!level.setBlock(pos, rose, 3)) continue;
            // Native EntityPlaceEvent uses snapshot state for mobs, so capture the manifested flower.
            // The replaced state is known to be air; cancellation only removes our unchanged flower.
            var placement = BlockSnapshot.create(level.dimension(), level, pos);
            boolean canceled = EventHooks.onBlockPlace(owner, placement, Direction.UP);
            if (!placementReady(level, pos)) break;
            if (canceled || !isReleasing(level, owner, target, center)) {
                if (level.getBlockState(pos).equals(rose)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                continue;
            }
            if (!level.getBlockState(pos).equals(rose)) continue;
            placed++; level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(owner, rose));
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + .5, pos.getY() + .4, pos.getZ() + .5, 5, .15, .2, .15, .015);
        }
        if (placed > 0) refreshNearbyPaths(level, center);
        return placed;
    }
    private static void refreshNearbyPaths(ServerLevel level, Vec3 center) {
        // Another caster may already have cached a route through what was empty ground.
        // Replan once when flowers manifest so the species' native hazard malus can avoid them.
        double radius = WitherEndermanConfig.roseCastingRange() + WitherEndermanConfig.roseRadius(); int refreshed = 0;
        for (WitherEnderman hunter : level.getEntitiesOfClass(WitherEnderman.class, new AABB(center, center).inflate(radius), mob -> mob.isAlive() && !mob.isRemoved() && !mob.isNoAi())) {
            if (++refreshed > 32) break;
            hunter.getNavigation().stop(); hunter.getMoveControl().setWantedPosition(hunter.getX(), hunter.getY(), hunter.getZ(), 0);
            hunter.setDeltaMovement(0, hunter.getDeltaMovement().y, 0);
        }
    }
    private static boolean isReleasing(ServerLevel level, WitherEnderman owner, LivingEntity target, Vec3 center) {
        return mayCast(level, owner, target, center) && owner.getMastermindBrain().isRoseReleaseFrame(target);
    }
    private static boolean mayCast(ServerLevel level, WitherEnderman owner, LivingEntity target, Vec3 center) {
        return owner.level() == level && owner.isAlive() && !owner.isRemoved() && !owner.isNoAi() && owner.isLegalWitherTarget(target)
                && center.isFinite() && owner.position().distanceToSqr(center) <= WitherEndermanConfig.roseCastingRange() * WitherEndermanConfig.roseCastingRange()
                && target.position().distanceToSqr(center) <= 4 && lineReady(level, owner.getEyePosition(), target.getEyePosition()) && owner.getSensing().hasLineOfSight(target)
                && level.getGameRules().get(GameRules.MOB_GRIEFING) && EventHooks.canEntityGrief(level, owner);
    }
    private static List<BlockPos> candidates(ServerLevel level, WitherEnderman owner, Vec3 center) {
        var existing = nearbyRoses(level, center); if (existing.size() >= WitherEndermanConfig.roseLocalCap()) return List.of();
        int radius = (int)Math.ceil(WitherEndermanConfig.roseRadius()); var origin = BlockPos.containing(center.add(0, .2, 0)); var offsets = new ArrayList<BlockPos>();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
            if (x * x + z * z <= WitherEndermanConfig.roseRadius() * WitherEndermanConfig.roseRadius()) offsets.add(new BlockPos(x, 0, z));
        offsets.sort(Comparator.comparingDouble(offset -> offset.distSqr(BlockPos.ZERO)));
        var result = new ArrayList<BlockPos>(); int checked = 0;
        search: for (BlockPos offset : offsets) {
            if (++checked > WitherEndermanConfig.rosePlacementBudget()) break;
            // Each candidate column has at most five survival checks, including recessed soul-sand footing.
            for (int dy : new int[] {0, -1, 1, -2, 2}) {
            BlockPos pos = origin.offset(offset.getX(), dy, offset.getZ());
            if (valid(level, owner, center, pos, existing)) {
                result.add(pos); existing.add(pos);
                if (result.size() >= Math.min(WitherEndermanConfig.roseCount(), WitherEndermanConfig.roseLocalCap() - (existing.size() - result.size()))) break search;
                break;
            }
            }
        }
        return result;
    }
    private static boolean valid(ServerLevel level, WitherEnderman owner, Vec3 center, BlockPos pos, List<BlockPos> existing) {
        if (!placementReady(level, pos) || pos.getY() <= level.getMinY() || pos.getY() >= level.getMaxY()
                || !level.getWorldBorder().isWithinBounds(pos) || Math.abs(pos.getY() - center.y) > 2.5
                || Vec3.atBottomCenterOf(pos).subtract(center).horizontalDistanceSqr() > WitherEndermanConfig.roseRadius() * WitherEndermanConfig.roseRadius()
                || !level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty() || level.getBlockEntity(pos) != null
                || level.getBlockEntity(pos.below()) != null || !Blocks.WITHER_ROSE.defaultBlockState().canSurvive(level, pos)) return false;
        var box = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(pos));
        if (!level.getWorldBorder().isWithinBounds(box)) return false;
        if (owner.getBoundingBox().inflate(.6).intersects(box) || !level.getEntities((Entity)null, box, entity -> entity.isAlive() && !entity.isSpectator()).isEmpty()) return false;
        double spacing = WitherEndermanConfig.roseSpacing();
        for (BlockPos rose : existing) if (Math.abs(rose.getY() - pos.getY()) <= 3 && horizontalDistanceSqr(rose, pos) < spacing * spacing) return false;
        Vec3 to = Vec3.atCenterOf(pos);
        if (!lineReady(level, owner.getEyePosition(), to)) return false;
        return level.clip(new ClipContext(owner.getEyePosition(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }
    private static boolean lineReady(ServerLevel level, Vec3 from, Vec3 to) {
        var route = new AABB(from, to).inflate(.1);
        for (int x = ((int)Math.floor(route.minX)) >> 4; x <= ((int)Math.floor(route.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(route.minZ)) >> 4; z <= ((int)Math.floor(route.maxZ)) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    private static List<BlockPos> nearbyRoses(ServerLevel level, Vec3 center) {
        var found = new ArrayList<BlockPos>(); var origin = BlockPos.containing(center); int radius = (int)Math.ceil(WitherEndermanConfig.roseRadius() + WitherEndermanConfig.roseSpacing() + 1);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -3, -radius), origin.offset(radius, 3, radius)))
            if (loaded(level, pos) && level.getBlockState(pos).is(Blocks.WITHER_ROSE)) { found.add(pos.immutable()); if (found.size() >= WitherEndermanConfig.roseLocalCap()) break; }
        return found;
    }
    private static double horizontalDistanceSqr(BlockPos a, BlockPos b) { long x = a.getX() - b.getX(), z = a.getZ() - b.getZ(); return x * x + z * z; }
    private static boolean placementReady(ServerLevel level, BlockPos pos) { return loaded(level, pos) && loaded(level, pos.below()) && loaded(level, pos.north()) && loaded(level, pos.south()) && loaded(level, pos.east()) && loaded(level, pos.west()); }
    private static boolean loaded(ServerLevel level, BlockPos pos) { return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null; }
}
