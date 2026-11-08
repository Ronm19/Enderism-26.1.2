package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.GroupTacticsConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.AngelEnderman;
import com.ronm19.enderism.entity.EndermanGolem;
import com.ronm19.enderism.entity.SculkEnderman;
import com.ronm19.enderism.entity.WhiteEnderman;
import com.ronm19.enderism.entity.ZombieEnderman;
import com.ronm19.enderism.entity.VoidEnderman;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An encounter is shared only by its bounded, loaded members. There is no world registry.
 * Personal sight admits members; communication carries only a frozen, expiring observation.
 */
public final class GroupTactics {
    private final MastermindBrain brain;
    private final AbstractEnderman owner;
    private @Nullable Encounter encounter;
    private @Nullable WeakReference<LivingEntity> personalTarget;
    private long personalObservedAt = Long.MIN_VALUE, lastCombatAt = Long.MIN_VALUE;
    private long nextFormationAt, nextSearchAt, nextPathAt, retreatUntil, regroupUntil, flankUntil;
    private boolean retreated;
    private int lane;
    private Phase phase = Phase.NONE;
    private @Nullable Vec3 destination;

    GroupTactics(MastermindBrain brain, AbstractEnderman owner) { this.brain = brain; this.owner = owner; }

    public boolean supportsGroups() {
        // These species have deliberately different neutrality, sensing, horde, tracking or rescue rules.
        return !(this.owner instanceof AngelEnderman || this.owner instanceof EndermanGolem
                || this.owner instanceof SculkEnderman || this.owner instanceof WhiteEnderman
                || this.owner instanceof ZombieEnderman || this.owner instanceof VoidEnderman);
    }
    public @Nullable UUID groupId() { return this.encounter == null ? null : this.encounter.id; }
    public @Nullable UUID leaderId() { return this.encounter == null ? null : this.encounter.leader; }
    public int memberCount() { return this.encounter == null ? 0 : this.encounter.members.size(); }
    public int lane() { return this.encounter == null ? 0 : this.lane; }
    public Role role() { return this.encounter == null ? Role.NONE : this.lane == 0 ? Role.LEADER : this.lane < 0 ? Role.LEFT_FLANK : Role.RIGHT_FLANK; }
    public Phase phase() { return this.phase; }
    public boolean hasRetreated() { return this.retreated; }
    public @Nullable Vec3 tacticalDestination() { return this.destination; }
    public long expiresAt() { return this.encounter == null ? 0 : this.encounter.expiresAt; }
    public long observedAt() { return this.validObservation() ? this.encounter.observedAt : 0; }
    public @Nullable Vec3 observedPosition() { return this.validObservation() ? this.encounter.observedPosition : null; }

    /** Called before the species Brain. It validates leases and never changes movement or casts. */
    public void beforeBrainTick(ServerLevel level) {
        long now = level.getGameTime();
        if (!this.eligible() || !GroupTacticsConfig.enabled()) { this.leave(now, false); return; }
        Encounter group = this.encounter;
        if (group == null) return;
        LivingEntity target = group.target.get();
        if (now >= group.expiresAt || now - group.observedAt > GroupTacticsConfig.observationTicks()
                || !this.legal(target)) { this.disband(group, now); return; }
        if (group.maintainedAt != now) {
            group.maintainedAt = now;
            for (WeakReference<GroupTactics> reference : List.copyOf(group.members)) {
                GroupTactics member = reference.get();
                if (member == null) { group.members.remove(reference); continue; }
                if (!member.eligible() || member.owner.level() != this.owner.level()
                        || !member.legal(target) || member.personal() != target
                        || member.owner.distanceToSqr(group.origin) > square(GroupTacticsConfig.encounterRadius())) {
                    member.leave(now, false);
                }
            }
            if (group.members.size() < 2) { this.disband(group, now); return; }
        }
        if (this.encounter != group) return;
        if (now >= group.nextElectionAt || this.findMember(group, group.leader) == null) this.elect(group, now);
        if (now >= this.regroupUntil && this.phase == Phase.REGROUP || now >= this.retreatUntil && this.phase == Phase.RETREAT) {
            this.phase = this.retreatUntil != 0 && now < this.regroupUntil ? Phase.REGROUP : Phase.PRESSURE;
            this.destination = null; this.nextPathAt = 0;
        }
    }

    void observe(LivingEntity target) {
        if (!this.supportsGroups() || !this.brain.isVisibleThreat(target)) return;
        long now = this.owner.level().getGameTime();
        if (this.personal() != target && this.encounter != null) this.leave(now, false);
        this.personalTarget = new WeakReference<>(target); this.personalObservedAt = now;
        Encounter group = this.encounter;
        if (group != null && group.target.get() == target) {
            group.observedPosition = target.position(); group.observedAt = now;
            group.observer = new WeakReference<>(this.owner);
        }
    }

    /** Returns true only when a finite tactical ground move owns this ordinary melee frame. */
    boolean tickCombat(LivingEntity target) {
        if (!this.eligible() || !GroupTacticsConfig.enabled() || !this.brain.isVisibleThreat(target)) return false;
        long now = this.owner.level().getGameTime();
        this.observe(target); this.lastCombatAt = now;
        if (now >= this.nextSearchAt) {
            this.nextSearchAt = now + GroupTacticsConfig.coordinationIntervalTicks();
            this.recruit(target, now);
        }
        Encounter group = this.encounter;
        if (group == null || group.target.get() != target || this.owner.isPassenger() || this.owner.isVehicle()
                || this.owner.isInWater() || this.owner.isInLava()) return false;
        if (!this.retreated && this.owner.getHealth() <= this.owner.getMaxHealth() * GroupTacticsConfig.retreatHealthFraction()) {
            this.retreated = true;
            this.retreatUntil = now + GroupTacticsConfig.retreatTicks();
            this.regroupUntil = this.retreatUntil + GroupTacticsConfig.regroupTicks();
            this.phase = Phase.RETREAT; this.destination = null; this.nextPathAt = 0;
            this.elect(group, now);
        }
        Vec3 observation = this.observedPosition();
        if (observation == null) return false;
        if (now < this.retreatUntil) {
            this.phase = Phase.RETREAT;
            Vec3 away = horizontal(this.owner.position().subtract(observation), group.approach.scale(-1));
            return this.move(this.owner.position().add(away.scale(7)).add(this.side(group).scale(this.lane * .7)), now, true);
        }
        if (now < this.regroupUntil) {
            this.phase = Phase.REGROUP;
            GroupTactics leader = this.findMember(group, group.leader);
            Vec3 anchor = leader == null ? group.origin : leader.owner.position();
            return this.move(anchor.subtract(group.approach.scale(3)).add(this.side(group).scale(this.lane * 1.5)), now, true);
        }
        // Flanks approach distinct lanes, then release ordinary native melee near the target.
        if (now < this.flankUntil && this.lane != 0 && !this.owner.isWithinMeleeAttackRange(target) && this.owner.distanceToSqr(observation) > 16) {
            this.phase = Phase.FLANK;
            Vec3 desired = observation.subtract(group.approach.scale(1.5))
                    .add(this.side(group).scale(this.lane * GroupTacticsConfig.flankWidth()));
            if (this.owner.distanceToSqr(desired) > 2.25 && this.move(desired, now, false)) return true;
        }
        this.phase = Phase.PRESSURE; this.destination = null;
        return false;
    }

    private void recruit(LivingEntity target, long now) {
        if (now < this.nextFormationAt || !(this.owner.level() instanceof ServerLevel level)) return;
        Encounter group = this.encounter;
        if (group != null && !this.owner.getUUID().equals(group.leader)) return;
        List<GroupTactics> candidates = new ArrayList<>();
        double radius = GroupTacticsConfig.recruitmentRadius();
        for (AbstractEnderman entity : level.getEntitiesOfClass(AbstractEnderman.class, this.owner.getBoundingBox().inflate(radius))) {
            if (entity == this.owner || this.owner.distanceToSqr(entity) > radius * radius) continue;
            GroupTactics candidate = entity.getMastermindBrain().groupTactics();
            // A nearby idle creature never becomes hostile because another creature called it.
            if (candidate.encounter != null || now < candidate.nextFormationAt || !candidate.eligible()
                    || candidate.personal() != target || now - candidate.personalObservedAt > GroupTacticsConfig.observationTicks()
                    || candidate.lastCombatAt < now - 2 || !candidate.brain.isVisibleThreat(target)
                    || !this.owner.getSensing().hasLineOfSight(entity)) continue;
            candidates.add(candidate);
        }
        candidates.sort(Comparator.comparingDouble((GroupTactics member) -> this.owner.distanceToSqr(member.owner))
                .thenComparing(member -> member.owner.getUUID()));
        if (group == null) {
            if (candidates.isEmpty()) return;
            group = new Encounter(this, target, now);
            this.join(group);
        }
        for (GroupTactics candidate : candidates) {
            if (group.members.size() >= GroupTacticsConfig.maximumMembers()) break;
            if (candidate.owner.distanceToSqr(group.origin) <= square(GroupTacticsConfig.encounterRadius())) candidate.join(group);
        }
        this.elect(group, now);
    }

    private boolean move(Vec3 requested, long now, boolean defensive) {
        // MeleeAttackGoal.stop also stops navigation, so release it before installing the tactical route.
        this.brain.stopCombat();
        if (now < this.nextPathAt) return this.destination != null || defensive;
        this.nextPathAt = now + GroupTacticsConfig.pathIntervalTicks();
        ServerLevel level = (ServerLevel)this.owner.level();
        Encounter group = this.encounter;
        if (group == null) return false;
        Vec3 offset = requested.subtract(group.origin);
        double leash = GroupTacticsConfig.encounterRadius() - 2;
        if (offset.lengthSqr() > leash * leash) requested = group.origin.add(offset.normalize().scale(leash));
        BlockPos center = BlockPos.containing(requested);
        this.destination = null;
        // At most five ground candidates and one native route per coordination frame; never teleport.
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            Vec3 point = new Vec3(center.getX() + .5, center.getY() + dy, center.getZ() + .5);
            if (this.owner.distanceToSqr(point) > square(GroupTacticsConfig.encounterRadius())
                    || !VoidSurvivalInstinct.isStableGround(this.owner, level, point)) continue;
            // Native path regions may extend around both endpoints. Verify those chunks before creating the region.
            int pathLength = Math.min(48, Math.max(16, (int)Math.ceil(Math.sqrt(this.owner.distanceToSqr(point))) + 4));
            int margin = pathLength + 8;
            BlockPos start = this.owner.blockPosition(); BlockPos end = BlockPos.containing(point);
            if (!VoidSurvivalInstinct.hasReadyChunks(level,
                    start.offset(-margin, 0, -margin), start.offset(margin, 0, margin))) break;
            var path = this.owner.getNavigation().createPath(end, 0, pathLength);
            if (path == null || !path.canReach()) break;
            if (this.owner.getNavigation().moveTo(path, 1.0)) { this.destination = point; return true; }
            break;
        }
        if (defensive) this.owner.getNavigation().stop();
        return defensive;
    }

    private void join(Encounter group) {
        this.encounter = group; group.members.add(new WeakReference<>(this));
        this.retreatUntil = this.regroupUntil = 0; this.retreated = false;
        this.flankUntil = this.owner.level().getGameTime() + GroupTacticsConfig.flankTicks();
        this.phase = Phase.PRESSURE; this.destination = null; this.nextPathAt = 0;
    }
    public void clear() { this.leave(this.owner.level().getGameTime(), true); }
    private void leave(long now, boolean forget) {
        Encounter old = this.encounter;
        this.encounter = null;
        if (old != null) {
            old.members.removeIf(reference -> reference.get() == null || reference.get() == this);
            this.nextFormationAt = Math.max(this.nextFormationAt, now + GroupTacticsConfig.reformDelayTicks());
        }
        this.phase = Phase.NONE; this.destination = null; this.lane = 0;
        this.retreatUntil = this.regroupUntil = 0; this.nextPathAt = 0;
        if (forget) { this.personalTarget = null; this.personalObservedAt = Long.MIN_VALUE; this.lastCombatAt = Long.MIN_VALUE; }
    }
    private void disband(Encounter group, long now) {
        for (WeakReference<GroupTactics> reference : List.copyOf(group.members)) {
            GroupTactics member = reference.get(); if (member != null && member.encounter == group) member.leave(now, false);
        }
        group.members.clear(); group.observer = null; group.observedPosition = null;
    }
    private void elect(Encounter group, long now) {
        List<GroupTactics> members = group.members.stream().map(WeakReference::get).filter(member -> member != null).toList();
        Vec3 anchor = group.observedPosition == null ? group.origin : group.observedPosition;
        GroupTactics leader = members.stream().min(Comparator
                .comparingInt((GroupTactics member) -> member.owner.getHealth() <= member.owner.getMaxHealth() * GroupTacticsConfig.retreatHealthFraction() ? 1 : 0)
                .thenComparingInt(member -> (int)(member.owner.distanceToSqr(anchor) / 64))
                .thenComparing(member -> member.owner.getUUID())).orElse(null);
        if (leader == null) return;
        group.leader = leader.owner.getUUID(); group.nextElectionAt = now + GroupTacticsConfig.coordinationIntervalTicks();
        List<GroupTactics> ordered = new ArrayList<>(members);
        ordered.sort(Comparator.comparing(member -> member.owner.getUUID()));
        int index = 0;
        for (GroupTactics member : ordered) {
            int nextLane = member == leader ? 0 : (++index % 2 == 1 ? -1 : 1) * ((index + 1) / 2);
            if (member.lane != nextLane) { member.destination = null; member.nextPathAt = 0; }
            member.lane = nextLane;
        }
    }
    private @Nullable GroupTactics findMember(Encounter group, @Nullable UUID id) {
        for (WeakReference<GroupTactics> reference : group.members) {
            GroupTactics member = reference.get();
            if (member != null && member.owner.getUUID().equals(id) && member.eligible()) return member;
        }
        return null;
    }
    private boolean eligible() { return this.supportsGroups() && this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi(); }
    private boolean legal(@Nullable LivingEntity target) {
        return this.brain.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }
    private @Nullable LivingEntity personal() { return this.personalTarget == null ? null : this.personalTarget.get(); }
    private boolean validObservation() {
        Encounter group = this.encounter;
        if (group == null || group.observedPosition == null || group.observer == null) return false;
        AbstractEnderman observer = group.observer.get(); long now = this.owner.level().getGameTime();
        return now < group.expiresAt && now - group.observedAt <= GroupTacticsConfig.observationTicks()
                && this.legal(group.target.get()) && observer != null && observer.isAlive() && !observer.isRemoved()
                && !observer.isNoAi() && observer.level() == this.owner.level()
                && observer.getMastermindBrain().groupTactics().encounter == group
                && this.owner.distanceToSqr(observer) <= square(GroupTacticsConfig.recruitmentRadius());
    }
    private Vec3 side(Encounter group) { return new Vec3(-group.approach.z, 0, group.approach.x); }
    private static Vec3 horizontal(Vec3 vector, Vec3 fallback) {
        Vec3 flat = new Vec3(vector.x, 0, vector.z); return flat.lengthSqr() < .01 ? fallback : flat.normalize();
    }
    private static double square(double value) { return value * value; }

    public enum Role { NONE, LEADER, LEFT_FLANK, RIGHT_FLANK }
    public enum Phase { NONE, PRESSURE, FLANK, RETREAT, REGROUP }
    private static final class Encounter {
        final UUID id = UUID.randomUUID();
        final WeakReference<LivingEntity> target;
        final List<WeakReference<GroupTactics>> members = new ArrayList<>();
        final Vec3 origin, approach;
        final long expiresAt;
        @Nullable UUID leader;
        @Nullable WeakReference<AbstractEnderman> observer;
        @Nullable Vec3 observedPosition;
        long observedAt, nextElectionAt, maintainedAt = Long.MIN_VALUE;
        Encounter(GroupTactics founder, LivingEntity target, long now) {
            this.target = new WeakReference<>(target); this.origin = founder.owner.position();
            this.observedPosition = target.position(); this.observedAt = now;
            this.observer = new WeakReference<>(founder.owner);
            this.approach = horizontal(this.observedPosition.subtract(this.origin), new Vec3(1, 0, 0));
            this.expiresAt = now + GroupTacticsConfig.encounterTicks();
        }
    }
}
