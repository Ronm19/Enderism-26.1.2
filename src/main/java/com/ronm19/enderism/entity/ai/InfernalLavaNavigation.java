package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.entity.InfernalEnderman;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Uses ordinary ground paths with additional walkable nodes immediately above exposed lava. */
public final class InfernalLavaNavigation extends GroundPathNavigation {
    public static final double SOURCE_SURFACE_HEIGHT = 0.875;

    public InfernalLavaNavigation(InfernalEnderman mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new LavaSurfaceEvaluator();
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    public @Nullable Path createPath(BlockPos pos, int reachRange) {
        var chunk = this.level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null && chunk.getFluidState(pos).is(FluidTags.LAVA)
                && !chunk.getFluidState(pos.above()).is(FluidTags.LAVA)) {
            pos = pos.above();
        }
        return super.createPath(pos, reachRange);
    }

    @Override
    protected double getGroundY(Vec3 target) {
        BlockPos feet = BlockPos.containing(target);
        if (this.level.hasChunkAt(feet)) {
            double surface = surfaceHeight(this.level, feet.below());
            if (Double.isFinite(surface)) return surface;
        }
        return super.getGroundY(target);
    }

    @Override
    public boolean isStableDestination(BlockPos pos) {
        return this.level.hasChunkAt(pos)
                && (Double.isFinite(surfaceHeight(this.level, pos.below())) || super.isStableDestination(pos));
    }

    /** Finite only for the top cell of a lava column, including a flowing edge. */
    public static double surfaceHeight(BlockGetter level, BlockPos fluidPos) {
        var fluid = level.getFluidState(fluidPos);
        if (!fluid.is(FluidTags.LAVA) || level.getFluidState(fluidPos.above()).is(FluidTags.LAVA)) {
            return Double.NaN;
        }
        double height = fluid.isSource() ? SOURCE_SURFACE_HEIGHT
                : Math.min(SOURCE_SURFACE_HEIGHT, fluid.getHeight(level, fluidPos));
        return fluidPos.getY() + height;
    }

    private static final class LavaSurfaceEvaluator extends WalkNodeEvaluator {
        @Override
        public Node getStart() {
            BlockPos feet = this.mob.blockPosition();
            if (this.currentContext.getBlockState(feet).getFluidState().is(FluidTags.LAVA)) {
                BlockPos surfaceNode = feet.above();
                if (this.canStartAt(surfaceNode)) return this.getStartNode(surfaceNode);
            }
            return super.getStart();
        }

        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            PathType type = super.getPathType(context, x, y, z);
            if (type == PathType.OPEN && Double.isFinite(surfaceHeight(context.level(), new BlockPos(x, y - 1, z)))) {
                return PathType.WALKABLE;
            }
            return type;
        }

        @Override
        protected double getFloorLevel(BlockPos pos) {
            double surface = surfaceHeight(this.currentContext.level(), pos.below());
            return Double.isFinite(surface) ? surface : super.getFloorLevel(pos);
        }
    }
}
