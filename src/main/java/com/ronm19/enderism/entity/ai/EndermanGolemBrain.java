package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.EndermanGolemConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.EndermanGolem;
import com.ronm19.enderism.entity.EndermanGolem.CombatPhase;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Guardian decisions reuse the native Mastermind actuators; hostility is independent and finite. */
public final class EndermanGolemBrain extends MastermindBrain {
    private final EndermanGolem golem;
    private final Map<UUID, Threat> threats = new LinkedHashMap<>();
    private final Map<UUID, WeakReference<LivingEntity>> retreated = new LinkedHashMap<>();
    private final List<WeakReference<AbstractVillager>> residents = new ArrayList<>();
    private @Nullable WeakReference<LivingEntity> pendingTarget;
    private @Nullable WeakReference<LivingEntity> sensedThreat;
    private @Nullable WeakReference<AbstractVillager> protectedResident;
    private @Nullable Vec3 interceptPosition, searchPosition;
    private long nextSensorAt, nextDetectionAt, nextDecisionAt, nextCrusherAt, nextInterceptionAt, nextJudgmentAt, phaseEndsAt, searchUntil;
    private long interceptionStartedAt;
    private long actionEpoch;
    private boolean crusherImpact, attemptedInterceptTeleport;

    public EndermanGolemBrain(EndermanGolem golem) { super(golem); this.golem = golem; }
    public int getThreatCount() { return this.threats.size(); }
    public boolean hasPendingAbility() { return this.pendingTarget != null || this.golem.getCombatPhase().isCommitted(); }
    public @Nullable LivingEntity getPendingTarget() { return this.pendingTarget == null ? null : this.pendingTarget.get(); }
    public @Nullable AbstractVillager getProtectedResident() { return this.protectedResident == null ? null : this.protectedResident.get(); }
    private boolean baseLegal(@Nullable LivingEntity target) {
        return target != null && target != this.golem && target.isAlive() && !target.isRemoved() && target.level() == this.golem.level()
                && target.canBeSeenAsEnemy() && !this.golem.isGuardianAlly(target) && !this.golem.isAlliedTo(target)
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))
                && !(target instanceof ServerPlayer p && p.hasDisconnected());
    }
    public boolean isLegitimateThreat(@Nullable LivingEntity target) {
        if (!this.baseLegal(target)) return false;
        Threat record = this.threats.get(target.getUUID()); long now = this.golem.level().getGameTime();
        if (record != null && record.entity.get() == target && now < record.expiresAt
                && (!this.hasRetreated(target, record) || now < record.attackedAt + EndermanGolemConfig.directAttackMemoryTicks())) return true;
        if (target instanceof Player || !EndermanGolem.isVillageEnemyCandidate(target)) return false;
        BlockPos home = this.golem.getHomeVillage();
        if (home != null && target.position().distanceToSqr(Vec3.atBottomCenterOf(home)) <= square(EndermanGolemConfig.patrolRadius() + 8)) return true;
        return this.residents.stream().map(WeakReference::get).anyMatch(resident -> this.validResident(resident)
                && resident.distanceToSqr(target) <= square(EndermanGolemConfig.protectionRange()));
    }
    private boolean validResident(@Nullable AbstractVillager resident) {
        return resident != null && resident.isAlive() && !resident.isRemoved() && resident.level() == this.golem.level()
                && resident.distanceToSqr(this.golem) <= square(EndermanGolemConfig.protectionRange());
    }
    public void recordDirectAttack(LivingEntity attacker) { this.recordAttack(attacker); }
    public void recordVillageAttack(AbstractVillager resident, LivingEntity attacker) {
        if (this.validResident(resident)) this.recordAttack(attacker);
    }
    private void recordAttack(LivingEntity attacker) {
        if (!(this.golem.level() instanceof ServerLevel level) || !this.golem.isAlive() || this.golem.isRemoved() || this.golem.isNoAi() || !this.baseLegal(attacker)) return;
        long now = level.getGameTime(); this.retreated.remove(attacker.getUUID());
        Threat threat = this.threats.get(attacker.getUUID());
        if (threat == null || threat.entity.get() != attacker) { threat = new Threat(attacker, this.golem.position(), now); this.putThreat(attacker, threat); }
        threat.attackedAt = now; threat.expiresAt = now + EndermanGolemConfig.memoryTicks(); threat.warningUntil = now; threat.retreatSince = -1;
    }
    private void putThreat(LivingEntity target, Threat threat) {
        if (this.threats.size() >= 16 && !this.threats.containsKey(target.getUUID())) this.threats.remove(this.threats.keySet().iterator().next());
        this.threats.put(target.getUUID(), threat);
    }
    private boolean hasRetreated(LivingEntity target, Threat record) {
        double range = square(EndermanGolemConfig.retreatDistance());
        return target.position().distanceToSqr(record.anchor) > range || this.golem.distanceToSqr(target) > range;
    }
    private void suppress(LivingEntity target) {
        if (this.retreated.size() >= 16 && !this.retreated.containsKey(target.getUUID())) this.retreated.remove(this.retreated.keySet().iterator().next());
        this.retreated.put(target.getUUID(), new WeakReference<>(target));
    }
    private void updateTerritory(ServerLevel level, long now) {
        this.retreated.values().removeIf(reference -> !this.baseLegal(reference.get())
                || this.golem.distanceToSqr(reference.get()) > square(EndermanGolemConfig.territoryRadius() + 2));
        Iterator<Threat> iterator = this.threats.values().iterator();
        while (iterator.hasNext()) {
            Threat record = iterator.next(); LivingEntity target = record.entity.get();
            if (!this.baseLegal(target)) { iterator.remove(); continue; }
            if (now >= record.expiresAt) { if (this.hasRetreated(target, record)) this.suppress(target); iterator.remove(); continue; }
            if (this.hasRetreated(target, record) && now >= record.attackedAt + EndermanGolemConfig.directAttackMemoryTicks()) {
                if (record.retreatSince < 0) record.retreatSince = now;
                if (now - record.retreatSince >= EndermanGolemConfig.calmDelayTicks()) { this.suppress(target); iterator.remove(); }
            } else record.retreatSince = -1;
        }
        if (this.golem.tickCount % 4 != 0) return;
        double range = EndermanGolemConfig.territoryRadius();
        for (Player player : level.getEntitiesOfClass(Player.class, this.golem.getBoundingBox().inflate(range),
                p -> this.baseLegal(p) && this.golem.distanceToSqr(p) <= square(range) && this.golem.getSensing().hasLineOfSight(p))) {
            WeakReference<LivingEntity> excluded = this.retreated.get(player.getUUID());
            if (excluded != null && excluded.get() == player) continue;
            Threat record = this.threats.get(player.getUUID());
            if (record == null || record.entity.get() != player) this.putThreat(player, new Threat(player, this.golem.position(), now));
        }
    }
    private void refreshResidents(ServerLevel level, long now) {
        if (now < this.nextSensorAt) return;
        this.nextSensorAt = now + 10; this.residents.clear();
        double range = EndermanGolemConfig.protectionRange();
        level.getEntitiesOfClass(AbstractVillager.class, this.golem.getBoundingBox().inflate(range),
                        v -> this.validResident(v) && this.golem.getSensing().hasLineOfSight(v))
                .stream().sorted(Comparator.<AbstractVillager>comparingInt(v -> v.isBaby() ? 0 : 1)
                        .thenComparingDouble(v -> v.getHealth() / v.getMaxHealth())).limit(16)
                .forEach(v -> this.residents.add(new WeakReference<>(v)));
    }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) { return this.isLegitimateThreat(target); }
    @Override protected int memoryTicks() { return EndermanGolemConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return EndermanGolemConfig.crusherInterval(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected @Nullable LivingEntity observeThreat() {
        if (!(this.golem.level() instanceof ServerLevel level)) return null;
        LivingEntity previous = this.sensedThreat == null ? null : this.sensedThreat.get();
        if (level.getGameTime() < this.nextDetectionAt) return this.isVisibleThreat(previous) ? previous : null;
        this.nextDetectionAt = level.getGameTime() + 8;
        LivingEntity found = level.getEntitiesOfClass(LivingEntity.class, this.golem.getBoundingBox().inflate(EndermanGolemConfig.followRange()), this::isVisibleThreat)
                .stream().min(Comparator.comparingDouble(this::threatPriority)).orElse(null);
        this.sensedThreat = found == null ? null : new WeakReference<>(found); return found;
    }
    private double threatPriority(LivingEntity target) {
        double closestResident = this.residents.stream().map(WeakReference::get).filter(this::validResident)
                .mapToDouble(v -> v.distanceToSqr(target)).min().orElse(Double.POSITIVE_INFINITY);
        boolean attackingResident = target instanceof Mob mob && (mob.getTarget() instanceof AbstractVillager);
        return (attackingResident ? -10000 : target instanceof Player ? 10000 : 0) + Math.min(this.golem.distanceToSqr(target), closestResident);
    }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators(); this.refreshResidents(level, now); this.updateTerritory(level, now); this.golem.setLastHurtByMob(null);
        if (!body.isAlive() || body.isRemoved() || body.isNoAi()) { this.clearEncounter(); return; }
        if (this.tickCommitted(level, now)) return;
        if (this.tickFloating()) { this.golem.setCombatPhase(CombatPhase.PATROLLING); return; }
        LivingEntity enemy = this.observeThreat();
        if (enemy != null) {
            Threat record = this.threats.get(enemy.getUUID());
            if (enemy instanceof Player && record != null) {
                if (now < record.warningUntil) {
                    this.stopCombat(); this.stopAmbient(); this.clearDefensiveTarget(); this.stopMovement();
                    this.golem.getLookControl().setLookAt(enemy, 25, 25); this.golem.setCombatPhase(CombatPhase.DEFENSIVE_WARNING); this.state = State.COMBAT; return;
                }
                if (this.golem.position().distanceToSqr(record.anchor) >= square(EndermanGolemConfig.retreatDistance()) && !this.golem.isWithinMeleeAttackRange(enemy)) {
                    this.stopCombat(); this.clearDefensiveTarget(); this.stopMovement(); this.golem.setCombatPhase(CombatPhase.DE_ESCALATING); return;
                }
            }
            this.rememberVisibleThreat(enemy); this.state = State.COMBAT;
            if (!(enemy instanceof Player)) { this.searchPosition = enemy.position(); this.searchUntil = now + this.memoryTicks(); }
            if (now >= this.nextDecisionAt) {
                this.nextDecisionAt = now + 10;
                if (this.tryBeginInterception(level, enemy, now) || this.tryBeginJudgment(level, enemy, now)) return;
            }
            this.golem.setCombatPhase(CombatPhase.DEFENDING); this.tickVisibleCombat(enemy); return;
        }
        this.stopCombat(); this.clearDefensiveTarget(); this.state = State.IDLE;
        if (!this.threats.isEmpty()) { this.stopAmbient(); this.stopMovement(); this.golem.setCombatPhase(CombatPhase.DE_ESCALATING); return; }
        if (this.searchPosition != null && now < this.searchUntil && this.golem.distanceToSqr(this.searchPosition) > 4
                && level.hasChunkAt(BlockPos.containing(this.searchPosition))) {
            this.stopAmbient(); this.golem.getNavigation().moveTo(this.searchPosition.x, this.searchPosition.y, this.searchPosition.z, .8);
            this.golem.setCombatPhase(CombatPhase.PATROLLING); return;
        }
        this.searchPosition = null; this.tickPatrol(level);
    }
    private void tickPatrol(ServerLevel level) {
        BlockPos home = this.golem.getHomeVillage();
        if (home != null && this.golem.position().multiply(1, 0, 1).distanceToSqr(Vec3.atBottomCenterOf(home).multiply(1, 0, 1)) > square(EndermanGolemConfig.patrolRadius())
                && level.hasChunkAt(home)) {
            this.stopAmbient(); this.golem.getNavigation().moveTo(home.getX() + .5, home.getY(), home.getZ() + .5, .8);
            this.golem.setCombatPhase(CombatPhase.PATROLLING); return;
        }
        boolean observing = !level.getEntitiesOfClass(Player.class, this.golem.getBoundingBox().inflate(12), this::baseLegal).isEmpty();
        this.golem.setCombatPhase(observing ? CombatPhase.OBSERVING : home != null ? CombatPhase.PATROLLING : CombatPhase.IDLE); this.tickAmbient();
    }
    private void clearDefensiveTarget() {
        this.clearTarget(); this.golem.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY); this.golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
    private boolean mayBegin(ServerLevel level, LivingEntity target, long now) {
        this.refreshResidents(level, now);
        return this.golem.level() == level && this.golem.isAlive() && !this.golem.isRemoved() && !this.golem.isNoAi()
                && !this.golem.isPassenger() && !this.golem.isVehicle() && !this.hasPendingAbility() && this.isVisibleThreat(target);
    }
    private boolean readyToStrike(LivingEntity target, long now) {
        Threat record = this.threats.get(target.getUUID());
        return !(target instanceof Player) || record != null && now >= record.warningUntil;
    }
    public boolean tryBeginCrusher(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target, now) || !this.readyToStrike(target, now) || now < this.nextCrusherAt
                || this.golem.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN) || !this.golem.isWithinMeleeAttackRange(target)) return false;
        this.beginCommitted(target); this.nextCrusherAt = now + EndermanGolemConfig.crusherInterval();
        this.golem.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, EndermanGolemConfig.crusherInterval());
        this.phaseEndsAt = now + EndermanGolemConfig.crusherWindup(); this.golem.setCombatPhase(CombatPhase.CRUSHER_WINDUP); this.golem.warningCue(false); return true;
    }
    public boolean tryBeginJudgment(ServerLevel level, LivingEntity target, long now) {
        if (!this.mayBegin(level, target, now) || !this.golem.onGround() || !this.readyToStrike(target, now) || now < this.nextJudgmentAt || this.judgmentEnemies(level).size() < 2) return false;
        this.beginCommitted(target); this.nextJudgmentAt = now + EndermanGolemConfig.judgmentCooldown();
        this.phaseEndsAt = now + EndermanGolemConfig.judgmentWindup(); this.golem.setCombatPhase(CombatPhase.JUDGMENT_WINDUP); this.golem.warningCue(true); return true;
    }
    private List<LivingEntity> judgmentEnemies(ServerLevel level) {
        double radius = EndermanGolemConfig.judgmentRadius();
        return level.getEntitiesOfClass(LivingEntity.class, this.golem.getBoundingBox().inflate(radius),
                        enemy -> this.isVisibleThreat(enemy) && this.readyToStrike(enemy, level.getGameTime()) && this.golem.distanceToSqr(enemy) <= square(radius))
                .stream().limit(16).toList();
    }
    public boolean tryBeginInterception(ServerLevel level, LivingEntity enemy, long now) {
        AbstractVillager resident = this.residents.stream().map(WeakReference::get).filter(this::validResident)
                .filter(v -> v.distanceToSqr(enemy) <= 12 * 12 && this.golem.distanceToSqr(v) > 3 * 3)
                .min(Comparator.<AbstractVillager>comparingInt(v -> v.isBaby() ? 0 : 1).thenComparingDouble(v -> v.distanceToSqr(enemy))).orElse(null);
        return resident != null && this.tryBeginInterception(level, resident, enemy, now);
    }
    public boolean tryBeginInterception(ServerLevel level, AbstractVillager resident, LivingEntity enemy, long now) {
        if (!this.mayBegin(level, enemy, now) || now < this.nextInterceptionAt || !this.validResident(resident)
                || !EndermanGolem.isVillageEnemyCandidate(enemy) || enemy.distanceToSqr(resident) > 12 * 12 || enemy.distanceToSqr(resident) < 3.5 * 3.5
                || !this.golem.getSensing().hasLineOfSight(resident)) return false;
        Vec3 toward = enemy.position().subtract(resident.position()).multiply(1, 0, 1);
        Vec3 desired = resident.position().add(toward.normalize().scale(Math.min(4, toward.horizontalDistance() * .5)));
        Vec3 position = this.golem.planGuardianPosition(Vec3.atBottomCenterOf(BlockPos.containing(desired)));
        if (position == null || this.golem.distanceToSqr(position) < 2.25) return false;
        AABB box = this.golem.getBoundingBox().move(position.subtract(this.golem.position()));
        if (box.intersects(resident.getBoundingBox().inflate(.15)) || box.intersects(enemy.getBoundingBox().inflate(.15))) return false;
        this.beginCommitted(enemy); this.protectedResident = new WeakReference<>(resident); this.interceptPosition = position;
        this.nextInterceptionAt = now + EndermanGolemConfig.interceptionCooldown(); this.phaseEndsAt = now + EndermanGolemConfig.interceptionDuration();
        this.interceptionStartedAt = now; this.attemptedInterceptTeleport = false; this.golem.setCombatPhase(CombatPhase.INTERCEPTING);
        this.golem.getNavigation().moveTo(position.x, position.y, position.z, EndermanGolemConfig.interceptionSpeed()); return true;
    }
    private void beginCommitted(LivingEntity target) {
        this.actionEpoch++;
        this.rememberVisibleThreat(target); this.stopCombat(); this.stopFloating(); this.stopAmbient(); this.clearTarget(); this.stopMovement();
        this.pendingTarget = new WeakReference<>(target); this.state = State.COMBAT;
    }
    private boolean tickCommitted(ServerLevel level, long now) {
        CombatPhase phase = this.golem.getCombatPhase(); if (!phase.isCommitted()) return false;
        if (phase == CombatPhase.RECOVERY) { this.stopMovement(); if (now >= this.phaseEndsAt) this.golem.setCombatPhase(CombatPhase.IDLE); return true; }
        if (phase == CombatPhase.CRUSHER_STRIKE || phase == CombatPhase.JUDGMENT_IMPACT) { this.stopMovement(); if (now >= this.phaseEndsAt) this.finishAction(now); return true; }
        LivingEntity target = this.getPendingTarget();
        if (!this.isLegitimateThreat(target) || this.golem.isPassenger() || this.golem.isVehicle()) { this.finishAction(now); return true; }
        if (phase == CombatPhase.JUDGMENT_WINDUP && !this.golem.onGround()) { this.finishAction(now); return true; }
        this.golem.getLookControl().setLookAt(target, 30, 25);
        if (phase == CombatPhase.INTERCEPTING) {
            AbstractVillager resident = this.getProtectedResident(); Vec3 point = this.interceptPosition;
            if (!this.validResident(resident) || point == null || now >= this.phaseEndsAt || this.golem.distanceToSqr(point) < 2.25) { this.finishAction(now); return true; }
            if (now % 10 == 0) this.golem.getNavigation().moveTo(point.x, point.y, point.z, EndermanGolemConfig.interceptionSpeed());
            if (now - this.interceptionStartedAt >= 25 && !this.attemptedInterceptTeleport && this.golem.getNavigation().isDone()) {
                this.attemptedInterceptTeleport = true;
                this.golem.tryGuardianTeleport(point);
            }
            return true;
        }
        this.stopMovement(); if (now < this.phaseEndsAt) return true;
        if (phase == CombatPhase.CRUSHER_WINDUP) {
            this.golem.setCombatPhase(CombatPhase.CRUSHER_STRIKE); this.crusherImpact = true;
            try { this.golem.performCrusherStrike(level, target); } finally { this.crusherImpact = false; this.pendingTarget = null; }
            this.phaseEndsAt = now + 8; return true;
        }
        if (phase == CombatPhase.JUDGMENT_WINDUP) {
            this.pendingTarget = null; this.golem.setCombatPhase(CombatPhase.JUDGMENT_IMPACT); this.phaseEndsAt = now + 8;
            long transaction = this.actionEpoch;
            this.golem.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.2F, .4F);
            level.sendParticles(ParticleTypes.POOF, this.golem.getX(), this.golem.getY(.1), this.golem.getZ(), 22, 2, .2, 2, .06);
            for (LivingEntity enemy : this.judgmentEnemies(level)) {
                if (!this.mayFinishJudgment(level, transaction)) break;
                if (!this.isVisibleThreat(enemy) || !this.readyToStrike(enemy, now) || this.golem.distanceToSqr(enemy) > square(EndermanGolemConfig.judgmentRadius())) continue;
                float before = enemy.getHealth() + enemy.getAbsorptionAmount();
                if (enemy.hurtServer(level, this.golem.damageSources().mobAttack(this.golem), EndermanGolemConfig.judgmentDamage())
                        && enemy.getHealth() + enemy.getAbsorptionAmount() < before && this.mayFinishJudgment(level, transaction) && this.isLegitimateThreat(enemy)) {
                    enemy.knockback(EndermanGolemConfig.judgmentKnockback(), this.golem.getX() - enemy.getX(), this.golem.getZ() - enemy.getZ());
                    if (this.mayFinishJudgment(level, transaction) && this.isLegitimateThreat(enemy)) this.golem.impactCue(level, enemy);
                }
            }
        }
        return true;
    }
    private boolean mayFinishJudgment(ServerLevel level, long epoch) {
        return this.actionEpoch == epoch && this.golem.level() == level && this.golem.isAlive() && !this.golem.isRemoved()
                && !this.golem.isNoAi() && this.golem.getCombatPhase() == CombatPhase.JUDGMENT_IMPACT;
    }
    public boolean isCrusherImpactFrame(LivingEntity target) { return this.crusherImpact && this.getPendingTarget() == target && this.golem.getCombatPhase() == CombatPhase.CRUSHER_STRIKE; }
    private void finishAction(long now) {
        this.actionEpoch++;
        this.pendingTarget = null; this.protectedResident = null; this.interceptPosition = null; this.crusherImpact = false;
        this.phaseEndsAt = now + EndermanGolemConfig.recoveryTicks(); this.stopMovement(); this.golem.setCombatPhase(CombatPhase.RECOVERY);
    }
    private void stopMovement() {
        this.golem.getNavigation().stop(); this.golem.getMoveControl().setWantedPosition(this.golem.getX(), this.golem.getY(), this.golem.getZ(), 0);
        this.golem.setDeltaMovement(0, this.golem.getDeltaMovement().y, 0);
    }
    @Override public void clearEncounter() {
        this.actionEpoch++;
        super.clearEncounter(); this.stopMovement(); this.threats.clear(); this.retreated.clear(); this.residents.clear();
        this.pendingTarget = null; this.sensedThreat = null; this.protectedResident = null; this.interceptPosition = null; this.searchPosition = null;
        this.crusherImpact = false; this.phaseEndsAt = 0; this.golem.setLastHurtByMob(null); this.golem.setCombatPhase(CombatPhase.IDLE);
    }
    public void writeCooldowns(ValueOutput output) {
        output.putLong("GolemCrusherAfter", this.nextCrusherAt); output.putLong("GolemInterceptionAfter", this.nextInterceptionAt); output.putLong("GolemJudgmentAfter", this.nextJudgmentAt);
    }
    public void readCooldowns(ValueInput input) {
        long now = this.golem.level().getGameTime();
        this.nextCrusherAt = Math.min(input.getLongOr("GolemCrusherAfter", 0), now + EndermanGolemConfig.crusherInterval());
        this.nextInterceptionAt = Math.min(input.getLongOr("GolemInterceptionAfter", 0), now + EndermanGolemConfig.interceptionCooldown());
        this.nextJudgmentAt = Math.min(input.getLongOr("GolemJudgmentAfter", 0), now + EndermanGolemConfig.judgmentCooldown());
    }
    private static double square(double value) { return value * value; }
    private static final class Threat {
        final WeakReference<LivingEntity> entity; final Vec3 anchor;
        long expiresAt, warningUntil, attackedAt = Long.MIN_VALUE / 2, retreatSince = -1;
        Threat(LivingEntity entity, Vec3 anchor, long now) {
            this.entity = new WeakReference<>(entity); this.anchor = anchor; this.expiresAt = now + EndermanGolemConfig.memoryTicks(); this.warningUntil = now + EndermanGolemConfig.warningTicks();
        }
    }
}
