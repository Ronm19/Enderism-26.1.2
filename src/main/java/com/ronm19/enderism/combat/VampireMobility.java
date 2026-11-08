package com.ronm19.enderism.combat;

import com.ronm19.enderism.VampireEndermanConfig;
import com.ronm19.enderism.entity.VampireEnderman;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Physical movement actuator. The shared Brain owns decisions, cast phases and the Crimson Dash cooldown. */
public final class VampireMobility {
    private final VampireEnderman owner;
    private @Nullable DashPlan activeDash;
    private double dashDistance;
    private int dashTicks;
    private long nextLeapAt, nextLeapCheckAt, leapStartedAt;
    private boolean leaping, preferRight;

    public VampireMobility(VampireEnderman owner) { this.owner = owner; }
    public record DashPlan(Vec3 direction, double distance, int ticks, boolean evasive) { }
    public boolean isDashing() { return this.activeDash != null; }
    public boolean isLeaping() {
        if (this.leaping && (!this.canAct() || this.owner.onGround() && this.owner.level().getGameTime() > this.leapStartedAt + 1
                || this.owner.level().getGameTime() - this.leapStartedAt >= 40)) this.leaping = false;
        return this.leaping;
    }
    private boolean canAct() { return this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi() && !this.owner.isPassenger() && !this.owner.isVehicle(); }

    public @Nullable DashPlan planToward(Vec3 observed) {
        if (!(this.owner.level() instanceof ServerLevel level) || !this.canAct() || !this.owner.onGround() || this.owner.isInWater() || !observed.isFinite()) return null;
        Vec3 direction = observed.subtract(this.owner.position()).multiply(1, 0, 1); double distance = direction.horizontalDistance();
        if (distance <= 2.75 || Math.abs(observed.y - this.owner.getY()) > 2) return null;
        direction = direction.normalize(); double length = Math.min(VampireEndermanConfig.dashDistance(), distance - 2.0);
        for (double angle : new double[] {0, .4, -.4, .75, -.75}) {
            Vec3 candidate = rotate(direction, angle);
            if (this.safeGroundPath(level, this.owner.position(), candidate.scale(length))) return new DashPlan(candidate, length, VampireEndermanConfig.dashDurationTicks(), false);
        }
        return null;
    }

    /** A bounded approaching-trajectory check; close, late or obstructed shots remain capable of hitting. */
    public @Nullable DashPlan planEvasion(ServerLevel level) {
        if (!this.canAct() || !this.owner.onGround() || this.owner.isInWater() || this.isDashing()) return null;
        double radius = VampireEndermanConfig.projectileAwarenessRadius();
        var projectiles = level.getEntitiesOfClass(Projectile.class, this.owner.getBoundingBox().inflate(radius), projectile -> this.validProjectile(projectile));
        projectiles.sort(Comparator.comparingDouble(projectile -> projectile.distanceToSqr(this.owner)));
        int checked = 0;
        for (Projectile projectile : projectiles) {
            if (++checked > 32) break;
            Vec3 velocity = projectile.getDeltaMovement(), start = projectile.position();
            Vec3 toward = this.owner.getBoundingBox().getCenter().subtract(start);
            if (toward.dot(velocity) <= 0 || velocity.horizontalDistanceSqr() < .005) continue;
            Vec3 end = start.add(velocity.scale(VampireEndermanConfig.projectilePredictionTicks()));
            var intersection = this.owner.getBoundingBox().inflate(projectile.getBbWidth() * .5 + .12).clip(start, end);
            if (intersection.isEmpty()) continue;
            double untilImpact = start.distanceTo(intersection.get()) / velocity.length();
            if (untilImpact <= VampireEndermanConfig.dashPreparationTicks() + 1) continue;
            if (!this.loaded(level, new AABB(start, intersection.get()).inflate(.1))
                    || level.clip(new ClipContext(start, intersection.get(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile)).getType() != HitResult.Type.MISS) continue;
            Vec3 side = new Vec3(-velocity.z, 0, velocity.x).normalize();
            double length = Math.min(3.5, VampireEndermanConfig.dashDistance());
            for (int sign : this.preferRight ? new int[] {1, -1} : new int[] {-1, 1}) {
                Vec3 direction = side.scale(sign);
                if (this.safeGroundPath(level, this.owner.position(), direction.scale(length)))
                    return new DashPlan(direction, length, VampireEndermanConfig.dashDurationTicks(), true);
            }
        }
        return null;
    }
    private boolean validProjectile(Projectile projectile) {
        Entity source = projectile.getOwner();
        return projectile.isAlive() && !projectile.isRemoved()
                && projectile.getDeltaMovement().isFinite() && projectile.getDeltaMovement().lengthSqr() >= .025
                && source != this.owner && (source == null || !this.owner.isAlliedTo(source));
    }

    public boolean beginDash(DashPlan plan) {
        if (!(this.owner.level() instanceof ServerLevel level) || !this.canAct() || !this.owner.onGround() || this.activeDash != null
                || !plan.direction().isFinite() || Math.abs(plan.direction().y) > .001 || plan.distance() <= 0
                || plan.distance() > VampireEndermanConfig.dashDistance() + .001 || plan.ticks() < 1
                || plan.ticks() > VampireEndermanConfig.dashDurationTicks() || plan.direction().horizontalDistanceSqr() < .99
                || plan.direction().horizontalDistanceSqr() > 1.01
                || !this.safeGroundPath(level, this.owner.position(), plan.direction().scale(plan.distance()))) return false;
        this.activeDash = plan; this.dashDistance = 0; this.dashTicks = 0; this.preferRight = !this.preferRight;
        this.owner.getNavigation().stop(); this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0); return true;
    }
    public boolean tickDash(ServerLevel level) {
        DashPlan plan = this.activeDash;
        if (plan == null) return false;
        if (!this.canAct() || !this.owner.onGround() || this.dashTicks >= plan.ticks() || this.dashDistance >= plan.distance() - .001) { this.stopDash(); return false; }
        double distance = Math.min(VampireEndermanConfig.dashSpeed(), plan.distance() - this.dashDistance);
        Vec3 movement = plan.direction().scale(distance), before = this.owner.position();
        if (!this.safeGroundPath(level, before, movement)) { this.stopDash(); return false; }
        this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0);
        this.owner.move(MoverType.SELF, movement);
        double moved = this.owner.position().subtract(before).horizontalDistance();
        this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0); this.dashDistance += moved; this.dashTicks++;
        if (moved < distance * .8 || this.owner.horizontalCollision || this.dashDistance >= plan.distance() - .001 || this.dashTicks >= plan.ticks()) { this.stopDash(); return false; }
        return true;
    }
    private void stopDash() { this.activeDash = null; this.owner.setDeltaMovement(0, this.owner.getDeltaMovement().y, 0); }

    /** A single launch impulse follows ordinary LivingEntity gravity and drag; no sustained upward control. */
    public boolean tryLeapToward(Vec3 observed, long now) {
        if (!(this.owner.level() instanceof ServerLevel level) || !this.canAct() || !this.owner.onGround() || this.owner.isInWater()
                || this.isDashing() || this.isLeaping() || now < this.nextLeapAt || now < this.nextLeapCheckAt || !observed.isFinite()) return false;
        this.nextLeapCheckAt = now + 10;
        Vec3 delta = observed.subtract(this.owner.position()).multiply(1, 0, 1); double distance = delta.horizontalDistance();
        if (distance < 2.25 || observed.y < this.owner.getY() - 2) return false;
        Vec3 direction = delta.normalize(); double maximum = Math.min(VampireEndermanConfig.highJumpDistance(), distance - .75);
        // A shorter elevated landing can clear a ledge that cannot safely be crossed in one ballistic arc.
        for (double length = maximum; length >= 1.5; length -= .5) {
            Vec3 landing = this.findLanding(level, this.owner.position().add(direction.scale(length)));
            if (landing == null || (landing.y - this.owner.getY() < .75 && this.safeGroundPath(level, this.owner.position(), direction.scale(length)))) continue;
            if (this.launchTo(level, direction, length, landing, now)) return true;
        }
        return false;
    }
    private boolean launchTo(ServerLevel level, Vec3 direction, double length, Vec3 landing, long now) {
        double gravity = this.owner.getGravity(), strength = VampireEndermanConfig.highJumpStrength();
        if (!Double.isFinite(gravity) || gravity < .03 || gravity > .15) return false;
        double y = this.owner.getY(), vy = strength; int flightTicks = 0;
        for (int tick = 1; tick <= 36; tick++) { y += vy; vy = (vy - gravity) * .98; if (vy < 0 && y <= landing.y) { flightTicks = tick; break; } }
        if (flightTicks == 0) return false;
        double horizontalSpeed = length * (1 - .91) / (1 - Math.pow(.91, flightTicks));
        Vec3 velocity = new Vec3(direction.x * horizontalSpeed, strength, direction.z * horizontalSpeed), point = this.owner.position();
        Vec3 simulated = velocity;
        for (int tick = 1; tick <= flightTicks; tick++) {
            Vec3 next = point.add(simulated); if (tick == flightTicks) next = new Vec3(next.x, landing.y, next.z);
            if (!this.safeAirPath(level, point, next.subtract(point))) return false;
            point = next; simulated = new Vec3(simulated.x * .91, (simulated.y - gravity) * .98, simulated.z * .91);
        }
        if (!this.safeStanding(level, point)) return false;
        this.nextLeapAt = now + VampireEndermanConfig.highJumpCooldownTicks(); this.leapStartedAt = now; this.leaping = true;
        this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(), this.owner.getY(), this.owner.getZ(), 0);
        this.owner.setDeltaMovement(velocity); this.owner.setOnGround(false); this.owner.hurtMarked = true; return true;
    }
    private @Nullable Vec3 findLanding(ServerLevel level, Vec3 point) {
        int standingY = (int)Math.floor(this.owner.getY());
        for (int y = standingY + 2; y >= standingY - 1; y--) {
            Vec3 candidate = new Vec3(point.x, y, point.z); if (this.safeStanding(level, candidate)) return candidate;
        }
        return null;
    }
    private boolean safeGroundPath(ServerLevel level, Vec3 start, Vec3 movement) {
        int samples = Math.max(1, (int)Math.ceil(movement.length() / .2));
        for (int n = 0; n <= samples; n++) if (!this.safeStanding(level, start.add(movement.scale((double)n / samples)))) return false;
        return true;
    }
    private boolean safeAirPath(ServerLevel level, Vec3 start, Vec3 movement) {
        int samples = Math.max(1, (int)Math.ceil(movement.length() / .15));
        for (int n = 0; n <= samples; n++) if (!this.safeBody(level, start.add(movement.scale((double)n / samples)))) return false;
        return true;
    }
    private boolean safeStanding(ServerLevel level, Vec3 point) {
        if (!this.safeBody(level, point)) return false;
        AABB box = this.boxAt(point); int y = (int)Math.floor(box.minY - .01);
        for (double x : new double[] {box.minX + .02, box.maxX - .02}) for (double z : new double[] {box.minZ + .02, box.maxZ - .02}) {
            BlockPos support = BlockPos.containing(x, y, z); var state = level.getBlockState(support);
            if (!state.isFaceSturdy(level, support, Direction.UP) || hazard(state)) return false;
        }
        return true;
    }
    private AABB boxAt(Vec3 point) { return this.owner.getBoundingBox().move(point.subtract(this.owner.position())); }
    private boolean safeBody(ServerLevel level, Vec3 point) {
        AABB box = this.boxAt(point);
        if (!point.isFinite() || box.minY < level.getMinY() + 1 || box.maxY > level.getMaxY() || !level.getWorldBorder().isWithinBounds(box)
                || !this.loaded(level, box.inflate(.02).expandTowards(0, -1, 0)) || !level.noCollision(this.owner, box) || level.containsAnyLiquid(box)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (hazard(level.getBlockState(pos))) return false;
        return true;
    }
    private boolean loaded(ServerLevel level, AABB box) {
        for (int x = ((int)Math.floor(box.minX)) >> 4; x <= ((int)Math.floor(box.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(box.minZ)) >> 4; z <= ((int)Math.floor(box.maxZ)) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    private static boolean hazard(net.minecraft.world.level.block.state.BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE);
    }
    private static Vec3 rotate(Vec3 direction, double radians) { double c = Math.cos(radians), s = Math.sin(radians); return new Vec3(direction.x * c - direction.z * s, 0, direction.x * s + direction.z * c); }
    public void clear() {
        if (this.activeDash != null || this.leaping) this.stopDash();
        this.leaping = false;
    }
}
