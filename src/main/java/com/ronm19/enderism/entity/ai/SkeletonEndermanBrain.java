package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.SkeletonEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.SkeletonEnderman;
import com.ronm19.enderism.entity.SkeletonEnderman.CombatPhase;
import com.ronm19.enderism.entity.projectile.SkeletonArcaneArrow.Element;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shared Brain actuators with an immutable, one-ticket-per-player formation for each activation. */
public final class SkeletonEndermanBrain extends MastermindBrain {
    private final SkeletonEnderman archer;
    private @Nullable WeakReference<LivingEntity> trackedTarget;
    private @Nullable Vec3 lastLocated, progressPosition;
    private long locatedUntil, encounterStarted, nextDetectionAt, nextDecisionAt, nextPathAt, nextProgressAt;
    private long nextVolleyAt, nextCursedAt, nextBarrageAt, nextTeleportAt, phaseEndsAt, nextShadeAt, shadeUntil, burningSince;
    private int blockedTicks, blockedArrows, castsThisEncounter, elementCursor;
    private boolean leftFlank, released, releaseClaimed;
    private @Nullable LivingEntity releaseTarget;
    private List<Shot> pendingShots = List.of();

    public SkeletonEndermanBrain(SkeletonEnderman owner) { super(owner); this.archer = owner; }
    @Override protected int memoryTicks() { return SkeletonEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return SkeletonEndermanConfig.meleeIntervalTicks(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) { return target != null && super.isLegalThreat(target) && this.archer.isLegalArrowTarget(target); }
    private @Nullable LivingEntity tracked() { return this.trackedTarget == null ? null : this.trackedTarget.get(); }
    public @Nullable Vec3 getLastLocatedPosition() { return this.lastLocated; }
    public boolean isTargetLocalized() { return this.lastLocated != null && this.isLegalThreat(this.tracked()) && this.owner.level().getGameTime() < this.locatedUntil; }
    public boolean hasPendingAbility() { return this.archer.getCombatPhase().isCommitted(); }
    public List<UUID> getVolleyTargetUuids() { return this.pendingShots.stream().map(Shot::uuid).toList(); }

    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity previous = super.observeThreat();
        if (previous != null) return previous;
        if (!(this.owner.level() instanceof ServerLevel level)) return null;
        if (level.getGameTime() < this.nextDetectionAt) return this.isVisibleThreat(this.tracked()) ? this.tracked() : null;
        this.nextDetectionAt = level.getGameTime() + 10;
        double range = Math.min(64, this.owner.getAttributeValue(Attributes.FOLLOW_RANGE)), nearest = range * range;
        ServerPlayer chosen = null;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= nearest && this.isVisibleThreat(player)) { chosen = player; nearest = distance; }
        }
        return chosen;
    }
    @Override protected void rememberVisibleThreat(LivingEntity target) {
        long now = this.owner.level().getGameTime();
        if (this.trackedTarget == null) { this.encounterStarted = now; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20; }
        super.rememberVisibleThreat(target); this.trackedTarget = new WeakReference<>(target);
        this.lastLocated = target.position(); this.locatedUntil = now + this.memoryTicks(); this.archer.setActivelyHunting(true);
    }
    /** Visible eligible players, deduplicated, spherical-range checked and selected once by stable priority. */
    public List<ServerPlayer> selectVolleyPlayers(ServerLevel level) {
        double range = SkeletonEndermanConfig.nearbyPlayerRadius(), squared = range * range;
        LivingEntity threat = this.tracked(); var unique = new HashSet<UUID>(); var players = new ArrayList<ServerPlayer>();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.owner.getBoundingBox().inflate(range))) {
            if (this.isLegalThreat(player) && this.owner.distanceToSqr(player) <= squared
                    && this.owner.getSensing().hasLineOfSight(player) && this.archer.hasClearArrowPath(player)
                    && unique.add(player.getUUID())) players.add(player);
        }
        players.sort(Comparator.<ServerPlayer>comparingInt(player -> player == threat ? 0 : 1)
                .thenComparingDouble(this.owner::distanceToSqr).thenComparing(player -> player.getUUID().toString()));
        return List.copyOf(players.subList(0, Math.min(SkeletonEndermanConfig.MAX_VOLLEY_ARROWS, players.size())));
    }
    private boolean mayBegin(ServerLevel level, LivingEntity target) {
        return this.owner.level() == level && this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi()
                && !this.owner.isPassenger() && !this.owner.isVehicle() && !this.hasPendingAbility() && this.isVisibleThreat(target)
                && this.owner.distanceToSqr(target) <= SkeletonEndermanConfig.arrowRange() * SkeletonEndermanConfig.arrowRange()
                && !this.owner.isWithinMeleeAttackRange(target) && this.archer.hasClearArrowPath(target);
    }
    public boolean tryBeginArcaneVolley(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level,target) || now < this.nextVolleyAt) return false;
        var players = this.selectVolleyPlayers(level); if (players.isEmpty() || !this.archer.canReserveArrows(players.size())) return false;
        this.begin(target, players, this.chooseElement(target), CombatPhase.ARCANE_WINDUP, now + SkeletonEndermanConfig.volleyPreparationTicks());
        this.nextVolleyAt = now + SkeletonEndermanConfig.volleyCooldownTicks(); return true;
    }
    public boolean tryBeginCursedArrow(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level,target) || now < this.nextCursedAt || !this.archer.canReserveArrows(1)) return false;
        this.begin(target, List.of(target), this.chooseElement(target), CombatPhase.CURSED_WINDUP, now + SkeletonEndermanConfig.cursedPreparationTicks());
        this.nextCursedAt = now + SkeletonEndermanConfig.cursedCooldownTicks(); return true;
    }
    public boolean tryBeginDeathsBarrage(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level,target) || now < this.nextBarrageAt) return false;
        var players = this.selectVolleyPlayers(level); if (players.isEmpty() || !this.archer.canReserveArrows(players.size())) return false;
        this.begin(target, players, this.chooseElement(target), CombatPhase.DEATHS_WINDUP, now + SkeletonEndermanConfig.barragePreparationTicks());
        this.nextBarrageAt = now + SkeletonEndermanConfig.barrageCooldownTicks(); return true;
    }
    private Element chooseElement(LivingEntity target) {
        Element[] values = Element.values(); Element selected = values[Math.floorMod(this.elementCursor++, values.length)];
        if (selected == Element.BURNING && (target.fireImmune() || target.hasEffect(MobEffects.FIRE_RESISTANCE))) selected = Element.SLOWNESS;
        if (selected == Element.FREEZING && !target.canFreeze()) selected = Element.POISON;
        return selected;
    }
    private void begin(LivingEntity target, List<? extends LivingEntity> targets, Element seed, CombatPhase phase, long end) {
        this.rememberVisibleThreat(target); this.stopCombat(); this.stopAmbient(); this.stopFloating(); this.clearTarget(); this.brace();
        Element[] elements = Element.values(); var shots = new ArrayList<Shot>();
        int count = Math.min(SkeletonEndermanConfig.MAX_VOLLEY_ARROWS, targets.size());
        for (int i = 0; i < count; i++) {
            LivingEntity recipient = targets.get(i); Element element = phase == CombatPhase.CURSED_WINDUP ? seed : elements[(seed.ordinal() + i) % elements.length];
            shots.add(new Shot(recipient.getUUID(), new WeakReference<>(recipient), element));
        }
        this.pendingShots = List.copyOf(shots); this.released = false; this.releaseTarget = null; this.releaseClaimed = false; this.phaseEndsAt = end;
        this.archer.setFormation(count, seed); this.archer.setActivelyHunting(true); this.archer.setCombatPhase(phase,end); this.state = State.COMBAT;
        this.archer.arrowWarning((ServerLevel)this.owner.level());
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) { this.clearEncounter(); return; }
        // A primary player leaving does not discard other players' reserved tickets.
        if (this.tickCommitted(level, now)) return;
        if (this.trackedTarget != null && !this.isLegalThreat(this.tracked())) this.clearEncounter();
        if (this.tickFloating()) { this.archer.setCombatPhase(CombatPhase.PURSUING); return; }
        if (this.tickShade(level,now)) return;
        LivingEntity visible = this.observeThreat();
        if (visible != null) {
            this.rememberVisibleThreat(visible); this.state = State.COMBAT; this.sampleProgress(now);
            if (this.owner.isWithinMeleeAttackRange(visible)) { this.archer.setCombatPhase(CombatPhase.MELEE); this.tickVisibleCombat(visible); return; }
            this.stopCombat(); this.clearTarget(); this.owner.getLookControl().setLookAt(visible, 35, 35);
            if (now >= this.nextDecisionAt) {
                this.nextDecisionAt = now + 10;
                if ((this.blockedTicks >= 40 || this.blockedArrows >= 2) && this.tryReposition(level,now)) return;
                if (now - this.encounterStarted >= 100 && this.tryBeginDeathsBarrage(level,visible,now)) return;
                if (this.castsThisEncounter > 0 && this.tryBeginCursedArrow(level,visible,now)) return;
                if (this.tryBeginArcaneVolley(level,visible,now)) return;
            }
            this.archer.setCombatPhase(CombatPhase.PURSUING); this.positionForRange(level,visible,now); return;
        }
        this.stopCombat(); this.clearTarget();
        if (this.isTargetLocalized()) {
            this.stopAmbient(); this.state = State.INVESTIGATE; this.archer.setCombatPhase(CombatPhase.SEARCHING); this.archer.setActivelyHunting(true);
            this.sampleProgress(now);
            if (this.blockedTicks >= 40 && now >= this.nextDecisionAt) { this.nextDecisionAt = now + 10; if (this.tryReposition(level,now)) return; }
            this.investigate(level,now); return;
        }
        if (this.trackedTarget != null || this.state != State.IDLE) this.clearEncounter();
        this.archer.setCombatPhase(CombatPhase.IDLE); this.archer.setActivelyHunting(false); this.tickAmbient();
    }
    private boolean validReservedTarget(LivingEntity target) {
        CombatPhase phase = this.archer.getCombatPhase();
        double radius = phase == CombatPhase.CURSED_WINDUP || phase == CombatPhase.CURSED_ARROW
                ? SkeletonEndermanConfig.arrowRange() : SkeletonEndermanConfig.nearbyPlayerRadius();
        return this.isLegalThreat(target) && this.owner.distanceToSqr(target) <= radius * radius
                && this.owner.getSensing().hasLineOfSight(target) && this.archer.hasClearArrowPath(target);
    }
    private boolean tickCommitted(ServerLevel level, long now) {
        CombatPhase phase = this.archer.getCombatPhase(); if (!phase.isCommitted()) return false;
        this.brace();
        if (phase == CombatPhase.RECOVERY) { if (now >= this.phaseEndsAt) this.archer.setCombatPhase(CombatPhase.PURSUING); return true; }
        if (this.owner.isPassenger() || this.owner.isVehicle()) { this.finish(now); return true; }
        if (this.released) { if (now >= this.phaseEndsAt) this.finish(now); return true; }
        LivingEntity remaining = null;
        for (Shot shot : this.pendingShots) { LivingEntity candidate = shot.target().get(); if (candidate != null && this.validReservedTarget(candidate)) { remaining = candidate; break; } }
        if (remaining == null) { this.finish(now); return true; }
        this.rememberVisibleThreat(remaining); this.owner.getLookControl().setLookAt(remaining,40,40);
        if (now < this.phaseEndsAt) return true;
        CombatPhase releasedPhase = switch (phase) { case ARCANE_WINDUP -> CombatPhase.ARCANE_VOLLEY; case CURSED_WINDUP -> CombatPhase.CURSED_ARROW; default -> CombatPhase.DEATHS_BARRAGE; };
        double damage = phase == CombatPhase.CURSED_WINDUP ? SkeletonEndermanConfig.cursedDamage()
                : phase == CombatPhase.DEATHS_WINDUP ? SkeletonEndermanConfig.barrageDamage() : SkeletonEndermanConfig.arrowDamage();
        double speed = SkeletonEndermanConfig.arrowSpeed() * (phase == CombatPhase.DEATHS_WINDUP ? SkeletonEndermanConfig.barrageSpeedMultiplier() : 1);
        this.archer.setCombatPhase(releasedPhase); this.released = true;
        List<Shot> reserved = this.pendingShots; int launched = 0;
        for (int i = 0; i < reserved.size() && i < SkeletonEndermanConfig.MAX_VOLLEY_ARROWS; i++) {
            if (!this.owner.isAlive() || this.owner.isRemoved() || this.owner.isNoAi() || this.archer.getCombatPhase() != releasedPhase) break;
            Shot shot = reserved.get(i); LivingEntity recipient = shot.target().get();
            if (recipient == null || !recipient.getUUID().equals(shot.uuid()) || !this.validReservedTarget(recipient)) continue;
            this.releaseTarget = recipient; this.releaseClaimed = false;
            try { if (this.archer.launchArrow(level,recipient,shot.element(),damage,speed,i,reserved.size())) launched++; }
            finally { this.releaseTarget = null; this.releaseClaimed = false; }
        }
        if (launched > 0) { this.castsThisEncounter++; this.archer.arrowReleaseCue(level); }
        this.phaseEndsAt = now + 5;
        return true;
    }
    /** Consumed before spawning, so reentrant callbacks cannot spawn the same player's arrow twice. */
    public boolean claimArrowRelease(LivingEntity target) {
        if (!this.released || this.releaseClaimed || this.releaseTarget != target || !this.archer.getCombatPhase().isCasting()) return false;
        this.releaseClaimed = true; return true;
    }
    public void onArrowBlocked() { this.blockedArrows = Math.min(8,this.blockedArrows + 1); }
    private void finish(long now) {
        this.pendingShots = List.of(); this.releaseTarget = null; this.releaseClaimed = false; this.released = false;
        this.archer.setFormation(0,this.archer.getSelectedElement()); this.phaseEndsAt = now + SkeletonEndermanConfig.recoveryTicks();
        this.archer.setCombatPhase(CombatPhase.RECOVERY,this.phaseEndsAt);
    }
    private void brace() { this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(),this.owner.getY(),this.owner.getZ(),0); this.owner.setDeltaMovement(0,this.owner.getDeltaMovement().y,0); }
    private void positionForRange(ServerLevel level, LivingEntity target, long now) {
        if (now < this.nextPathAt) return; this.nextPathAt = now + 10;
        double distance = this.owner.distanceToSqr(target);
        if (distance < 5 * 5) {
            Vec3 destination = this.archer.findRangedGroundPosition(target.position(),this.leftFlank);
            if (destination != null && this.owner.getNavigation().moveTo(destination.x,destination.y,destination.z,1.05)) return;
        }
        if (distance > 16 * 16 || !this.archer.hasClearArrowPath(target)) this.owner.getNavigation().moveTo(target,1);
        else { this.owner.getNavigation().stop(); this.owner.getMoveControl().setWantedPosition(this.owner.getX(),this.owner.getY(),this.owner.getZ(),0); }
    }
    private void sampleProgress(long now) {
        if (now < this.nextProgressAt) return; this.nextProgressAt = now + 20;
        if (this.progressPosition != null && this.lastLocated != null && this.owner.distanceToSqr(this.lastLocated) > 25
                && this.owner.position().distanceToSqr(this.progressPosition) < .3) this.blockedTicks = Math.min(100,this.blockedTicks + 20); else this.blockedTicks = 0;
        this.progressPosition = this.owner.position();
    }
    private boolean tryReposition(ServerLevel level, long now) {
        if (now < this.nextTeleportAt || !this.isTargetLocalized() || this.lastLocated == null) return false;
        this.nextTeleportAt = now + SkeletonEndermanConfig.tacticalTeleportCooldownTicks(); this.leftFlank = !this.leftFlank;
        this.stopCombat(); this.clearTarget();
        if (!this.archer.tryRangedReposition(this.lastLocated,this.leftFlank)) return false;
        this.blockedTicks = 0; this.blockedArrows = 0; this.progressPosition = this.owner.position(); this.nextProgressAt = now + 20;
        this.archer.setCombatPhase(CombatPhase.PURSUING); return true;
    }
    private void investigate(ServerLevel level,long now) {
        Vec3 point = this.lastLocated; if (point == null) return;
        this.owner.getLookControl().setLookAt(point.x,point.y + 1.5,point.z,35,35);
        if (this.owner.distanceToSqr(point) <= 2.25) { this.brace(); return; }
        if (now >= this.nextPathAt) { this.nextPathAt = now + 20; BlockPos pos = BlockPos.containing(point);
            if (SkeletonEnderman.readyChunks(level,pos,pos)) this.owner.getNavigation().moveTo(point.x,point.y,point.z,1); }
    }
    private boolean tickShade(ServerLevel level,long now) {
        if (!this.owner.isOnFire() || !level.isBrightOutside()) { this.burningSince = 0; this.shadeUntil = 0; return false; }
        if (this.burningSince == 0) this.burningSince = now;
        if (this.shadeUntil > now && !this.owner.getNavigation().isDone()) { this.archer.setCombatPhase(CombatPhase.SEEKING_SHADE); return true; }
        if (now - this.burningSince < 40 || now < this.nextShadeAt) return false;
        this.nextShadeAt = now + 80; Vec3 shade = this.archer.findNearbyShade();
        if (shade == null) return false;
        this.stopCombat(); this.stopAmbient(); this.clearTarget();
        if (!this.owner.getNavigation().moveTo(shade.x,shade.y,shade.z,1.1)) return false;
        this.shadeUntil = now + 60; this.archer.setCombatPhase(CombatPhase.SEEKING_SHADE); return true;
    }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.pendingShots = List.of(); this.releaseTarget = null; this.releaseClaimed = false; this.released = false;
        this.trackedTarget = null; this.lastLocated = null; this.locatedUntil = 0; this.progressPosition = null;
        this.blockedTicks = 0; this.blockedArrows = 0; this.castsThisEncounter = 0; this.phaseEndsAt = 0;
        this.archer.setFormation(0,this.archer.getSelectedElement()); this.archer.setActivelyHunting(false); this.archer.setCombatPhase(CombatPhase.IDLE); this.archer.clearOwnedArrows();
    }
    public void writeCooldowns(ValueOutput output) {
        output.putLong("SkeletonVolleyAfter",this.nextVolleyAt); output.putLong("SkeletonCursedAfter",this.nextCursedAt);
        output.putLong("SkeletonBarrageAfter",this.nextBarrageAt); output.putLong("SkeletonTeleportAfter",this.nextTeleportAt);
    }
    public void readCooldowns(ValueInput input) {
        long now = this.owner.level().getGameTime();
        this.nextVolleyAt = Math.min(input.getLongOr("SkeletonVolleyAfter",0),now + SkeletonEndermanConfig.volleyCooldownTicks());
        this.nextCursedAt = Math.min(input.getLongOr("SkeletonCursedAfter",0),now + SkeletonEndermanConfig.cursedCooldownTicks());
        this.nextBarrageAt = Math.min(input.getLongOr("SkeletonBarrageAfter",0),now + SkeletonEndermanConfig.barrageCooldownTicks());
        this.nextTeleportAt = Math.min(input.getLongOr("SkeletonTeleportAfter",0),now + SkeletonEndermanConfig.tacticalTeleportCooldownTicks());
    }
    private record Shot(UUID uuid, WeakReference<LivingEntity> target, Element element) {}
}
