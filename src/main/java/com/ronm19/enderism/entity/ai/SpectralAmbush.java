package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.SpectralEndermanConfig;
import com.ronm19.enderism.entity.SpectralEnderman;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.Nullable;

/** Finite positional and damage reservations for one real Spectral Enderman. */
public final class SpectralAmbush {
    private final SpectralEnderman owner;
    private final ArrayDeque<RecentPosition> recent = new ArrayDeque<>();
    private @Nullable WeakReference<LivingEntity> target;
    private @Nullable Hit pending;
    private long expiresAt;
    private long generation;
    private double bonus;
    private boolean special;
    private int searchSequence;

    public SpectralAmbush(SpectralEnderman owner) { this.owner = owner; }

    public static void registerEvents(IEventBus bus) { bus.addListener(SpectralAmbush::acceptedNativeDamage); }

    private static void acceptedNativeDamage(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof SpectralEnderman source
                && event.getSource().getDirectEntity() == source && event.getSource().is(DamageTypes.MOB_ATTACK)
                && event.getInflictedDamage() > 0) {
            SpectralAmbush ambush = source.getAmbush();
            Hit hit = ambush.pending;
            if (hit != null && hit.target.get() == event.getEntity()) hit.nativeAcceptedDamage = true;
        }
    }

    /** Searches only bounded, currently loaded terrain around a genuinely observed target. */
    public @Nullable Vec3 selectDestination(ServerLevel level, LivingEntity target, Vec3 observedPosition,
                                            Vec3 observedVelocity, boolean special, long now) {
        if (now != level.getGameTime() || this.owner.level() != level || !this.owner.isAlive()
                || this.owner.isRemoved() || this.owner.isNoAi() || !eligible(target)
                || target.level() != level || !finite(observedPosition) || !finite(observedVelocity)
                || this.owner.position().distanceToSqr(observedPosition) > square(SpectralEndermanConfig.teleportRange())) return null;
        this.recent.removeIf(position -> position.until <= now);
        Vec3 facing = target.getLookAngle().multiply(1, 0, 1).normalize();
        if (facing.lengthSqr() < .01) facing = new Vec3(0, 0, 1);
        double yaw = Math.atan2(facing.z, facing.x);
        // Alternating the first flank and a small rotation avoids a fixed behind-the-player offset.
        int turn = this.searchSequence++;
        double rotation = ((turn & 1) == 0 ? .5 : -.5) * Math.PI + (turn % 3 - 1) * .12;
        Vec3 velocity = observedVelocity.multiply(1, 0, 1);
        if (velocity.lengthSqr() > .0625) velocity = velocity.normalize().scale(.25);
        Vec3 center = observedPosition.add(velocity.scale(special ? 1.0 : .5));
        Vec3 best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int candidate = 0; candidate < 16; candidate++) {
            double angle = yaw + rotation + (candidate % 8) * Math.PI / 4;
            double radius = SpectralEndermanConfig.positioningRadius() + (candidate >= 8 ? .12 : 0);
            Vec3 requested = center.add(Math.cos(angle) * radius, 1.0, Math.sin(angle) * radius);
            Vec3 position = this.owner.findMaterializationDestination(requested);
            if (position == null || !this.isTacticalDestinationValid(level, target, position)
                    || this.owner.position().distanceToSqr(position) < 2.25 || this.wasRecentlyTried(position)) continue;
            Vec3 towardCandidate = position.subtract(target.position()).multiply(1, 0, 1).normalize();
            double exposed = towardCandidate.dot(facing);
            double score = (1 - Math.abs(exposed)) * 2.0 + this.coverScore(level, position) * .55
                    - Math.max(0, exposed) * 1.2 - this.owner.position().distanceTo(position) * .025
                    - candidate * .04;
            if (score > bestScore) { best = position; bestScore = score; }
        }
        if (best != null) {
            this.recent.addLast(new RecentPosition(best, now + Math.max(30, SpectralEndermanConfig.failedPositionRetry() * 3L)));
            while (this.recent.size() > 8) this.recent.removeFirst();
        }
        return best;
    }

    /** Extra species checks are also applied after native teleport listeners redirect a move. */
    public boolean isTacticalDestinationValid(ServerLevel level, LivingEntity target, Vec3 destination) {
        if (!finite(destination) || !eligible(target) || target.level() != level) return false;
        double horizontal = destination.subtract(target.position()).horizontalDistanceSqr();
        if (horizontal < .9 * .9 || horizontal > 2.0 * 2.0 || Math.abs(destination.y - target.getY()) > 1.5) return false;
        AABB body = this.owner.getBoundingBox().move(destination.subtract(this.owner.position()));
        if (body.inflate(.1).intersects(target.getBoundingBox())) return false;
        // Require enough useful vertical and horizontal overlap for an ordinary ground attack.
        if (!body.inflate(.8, .1, .8).intersects(target.getHitbox())) return false;
        Vec3 eye = destination.add(0, this.owner.getEyeHeight(), 0);
        Vec3 aim = target.getEyePosition();
        BlockPos low = BlockPos.containing(Math.min(eye.x, aim.x), Math.min(eye.y, aim.y), Math.min(eye.z, aim.z));
        BlockPos high = BlockPos.containing(Math.max(eye.x, aim.x), Math.max(eye.y, aim.y), Math.max(eye.z, aim.z));
        return level.hasChunksAt(low, high) && level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this.owner)).getType() == HitResult.Type.MISS;
    }

    private boolean wasRecentlyTried(Vec3 position) {
        for (RecentPosition old : this.recent) if (old.position.distanceToSqr(position) < .16) return true;
        return false;
    }
    private int coverScore(ServerLevel level, Vec3 position) {
        BlockPos center = BlockPos.containing(position).above();
        int score = 0;
        for (int[] offset : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockPos cell = center.offset(offset[0], 0, offset[1]);
            if (!level.hasChunkAt(cell)) continue;
            var block = level.getBlockState(cell);
            if (block.is(BlockTags.LOGS) || block.is(BlockTags.LEAVES)) score++;
        }
        return score;
    }

    public void arm(LivingEntity target, long now, int duration, double bonus, boolean special) {
        if (this.pending != null) return;
        this.clearArmedState();
        if (!(this.owner.level() instanceof ServerLevel level) || now != level.getGameTime()
                || !this.owner.isAlive() || this.owner.isRemoved()
                || !eligible(target) || target.level() != level) return;
        this.target = new WeakReference<>(target); this.expiresAt = now + Math.max(1, duration);
        this.bonus = Math.max(0, bonus); this.special = special;
    }

    public @Nullable Hit begin(LivingEntity victim, long now) {
        this.expire(now);
        if (this.pending != null || !this.isArmedFor(victim, now)) return null;
        Hit hit = new Hit(victim, this.generation, this.bonus, this.special);
        this.pending = hit;
        if (this.special) {
            // A committed special has one physical attempt, including a shielded or canceled hit.
            this.target = null; this.expiresAt = 0; this.bonus = 0; this.special = false;
        }
        return hit;
    }

    /** Confirm the matching native attack, then consume at most this activation's reservation. */
    public boolean finish(@Nullable Hit hit, boolean healthOrAbsorptionDamage) {
        if (hit == null || hit.finished || this.pending != hit) return false;
        hit.finished = true; this.pending = null;
        LivingEntity victim = hit.target.get();
        boolean accepted = healthOrAbsorptionDamage && hit.nativeAcceptedDamage && victim != null
                && victim.level() == this.owner.level() && !victim.isRemoved()
                && this.owner.isAlive() && !this.owner.isRemoved();
        if (hit.generation == this.generation && (accepted || hit.special)) this.clearArmedState();
        else this.expire(this.owner.level().getGameTime());
        return accepted;
    }

    public boolean isArmedFor(LivingEntity victim, long now) {
        this.expire(now);
        return this.target != null && this.target.get() == victim && now < this.expiresAt;
    }
    public boolean isSpecial() { this.expire(this.owner.level().getGameTime()); return this.special; }
    public void expire(long now) {
        LivingEntity victim = this.target == null ? null : this.target.get();
        if (this.target != null && (now >= this.expiresAt || victim == null || !eligible(victim)
                || victim.level() != this.owner.level() || !this.owner.isAlive() || this.owner.isRemoved())) this.clearArmedState();
    }
    public void clearArmed() { this.clearArmedState(); }
    public void clear() { this.clearArmedState(); this.recent.clear(); this.searchSequence = 0; }
    private void clearArmedState() {
        this.target = null; this.expiresAt = 0; this.bonus = 0; this.special = false; this.generation++;
    }
    private static boolean eligible(LivingEntity target) {
        return target.isAlive() && !target.isRemoved() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))
                && !(target instanceof ServerPlayer p && p.hasDisconnected());
    }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static double square(double n) { return n * n; }
    private record RecentPosition(Vec3 position, long until) {}
    public static final class Hit {
        private final WeakReference<LivingEntity> target;
        private final long generation;
        private final double bonus;
        private final boolean special;
        private boolean nativeAcceptedDamage;
        private boolean finished;
        private Hit(LivingEntity target, long generation, double bonus, boolean special) {
            this.target = new WeakReference<>(target); this.generation = generation; this.bonus = bonus; this.special = special;
        }
        public double bonusDamage() { return this.bonus; }
        public boolean isSpecial() { return this.special; }
    }
}
