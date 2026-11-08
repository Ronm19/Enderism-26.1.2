package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.AngelEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.AngelEnderman;
import com.ronm19.enderism.entity.AngelEnderman.CombatPhase;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shared native Brain actuators, with a strictly independent territorial decision policy. */
public final class AngelEndermanBrain extends MastermindBrain {
    public static final String HEALING_RECIPIENT_LOCK = "enderism:angel_healing_after";
    public static final String BENEDICTION_RECIPIENT_LOCK = "enderism:angel_benediction_after";
    private static final List<Holder<MobEffect>> BLESSINGS = List.of(MobEffects.STRENGTH, MobEffects.REGENERATION,
            MobEffects.RESISTANCE, MobEffects.ABSORPTION);
    private final AngelEnderman angel;
    private final Map<UUID, Threat> threats = new LinkedHashMap<>();
    private final Map<UUID, WeakReference<LivingEntity>> retreatedIntruders = new LinkedHashMap<>();
    private @Nullable SupportCast pendingSupport;
    private long nextStrikeAt, nextHealingAt, nextBenedictionAt, nextSupportScan, recoveryUntil;
    private long supportEpoch;

    public AngelEndermanBrain(AngelEnderman angel) { super(angel); this.angel = angel; }
    public int getThreatCount() { return this.threats.size(); }
    public boolean hasPendingSupport() { return this.pendingSupport != null; }
    public @Nullable LivingEntity getPendingSupportTarget() {
        return this.pendingSupport == null || this.pendingSupport.recipients.isEmpty() ? null : this.pendingSupport.recipients.getFirst().get();
    }
    public boolean isLegitimateThreat(@Nullable LivingEntity candidate) {
        if (!this.baseLegal(candidate)) return false;
        Threat threat = this.threats.get(candidate.getUUID());
        long now = this.angel.level().getGameTime();
        return threat != null && threat.entity.get() == candidate && now < threat.expiresAt
                && (!this.hasRetreated(candidate, threat) || now < threat.attackedAt + AngelEndermanConfig.directAttackMemoryTicks());
    }
    private boolean baseLegal(@Nullable LivingEntity target) {
        return target != null && target != this.angel && !(target instanceof EnderMan) && target.isAlive() && !target.isRemoved()
                && target.level() == this.angel.level() && target.canBeSeenAsEnemy() && !this.angel.isAlliedTo(target)
                && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))
                && !(target instanceof ServerPlayer p && p.hasDisconnected());
    }
    /** Only a successful native damage event may establish ranged direct-attack provocation. */
    public void recordDirectAttack(LivingEntity attacker) {
        if (!(this.angel.level() instanceof ServerLevel level) || !this.angel.isAlive() || this.angel.isRemoved()
                || this.angel.isNoAi() || !this.baseLegal(attacker)) return;
        long now = level.getGameTime();
        this.retreatedIntruders.remove(attacker.getUUID());
        Threat threat = this.threats.get(attacker.getUUID());
        if (threat == null || threat.entity.get() != attacker) {
            threat = new Threat(attacker, this.angel.position(), now); this.putThreat(attacker.getUUID(), threat);
        }
        threat.attackedAt = now; threat.expiresAt = now + AngelEndermanConfig.memoryTicks();
        threat.warningUntil = now; threat.retreatSince = -1;
        this.cancelSupport(); this.recoveryUntil = 0;
    }
    private void putThreat(UUID id, Threat threat) {
        if (this.threats.size() >= 16 && !this.threats.containsKey(id)) this.threats.remove(this.threats.keySet().iterator().next());
        this.threats.put(id, threat);
    }
    private boolean hasRetreated(LivingEntity target, Threat threat) {
        double limit = square(AngelEndermanConfig.retreatDistance());
        // The original confrontation point is fixed: following a retreating player cannot move the boundary.
        return target.position().distanceToSqr(threat.anchor) > limit || this.angel.distanceToSqr(target) > limit;
    }
    private void updateThreats(ServerLevel level, long now) {
        this.retreatedIntruders.values().removeIf(reference -> {
            LivingEntity player = reference.get();
            return !this.baseLegal(player) || this.angel.distanceToSqr(player) > square(AngelEndermanConfig.territoryRadius() + 2);
        });
        Iterator<Threat> it = this.threats.values().iterator();
        while (it.hasNext()) {
            Threat threat = it.next(); LivingEntity target = threat.entity.get();
            if (!this.baseLegal(target)) { it.remove(); continue; }
            if (now >= threat.expiresAt) {
                if (this.hasRetreated(target, threat)) this.suppressRetreatedIntruder(target);
                it.remove(); continue;
            }
            if (this.hasRetreated(target, threat) && now >= threat.attackedAt + AngelEndermanConfig.directAttackMemoryTicks()) {
                if (threat.retreatSince < 0) threat.retreatSince = now;
                if (now - threat.retreatSince >= AngelEndermanConfig.calmDelayTicks()) {
                    this.suppressRetreatedIntruder(target); it.remove();
                }
            } else threat.retreatSince = -1;
        }
        // Only close, actually observed players create territorial threats. Sight or an ally's target alone never does.
        if (this.angel.tickCount % 4 == 0) {
            double radius = AngelEndermanConfig.territoryRadius();
            for (Player player : level.getEntitiesOfClass(Player.class, this.angel.getBoundingBox().inflate(radius),
                    p -> this.baseLegal(p) && this.angel.distanceToSqr(p) <= square(radius) && this.angel.getSensing().hasLineOfSight(p))) {
                Threat existing = this.threats.get(player.getUUID());
                WeakReference<LivingEntity> suppressed = this.retreatedIntruders.get(player.getUUID());
                if (suppressed != null && suppressed.get() == player) continue;
                if (existing == null || existing.entity.get() != player)
                    this.putThreat(player.getUUID(), new Threat(player, this.angel.position(), now));
            }
        }
    }
    private void suppressRetreatedIntruder(LivingEntity player) {
        if (this.retreatedIntruders.size() >= 16 && !this.retreatedIntruders.containsKey(player.getUUID()))
            this.retreatedIntruders.remove(this.retreatedIntruders.keySet().iterator().next());
        this.retreatedIntruders.put(player.getUUID(), new WeakReference<>(player));
    }
    @Override protected @Nullable LivingEntity observeThreat() {
        // Deliberately ignore inherited HURT_BY_ENTITY, arbitrary setTarget and shared ally recruitment.
        return this.threats.values().stream().map(t -> t.entity.get()).filter(this::isVisibleThreat)
                .min(Comparator.comparingDouble(this.angel::distanceToSqr)).orElse(null);
    }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) { return this.isLegitimateThreat(target); }
    @Override protected int memoryTicks() { return AngelEndermanConfig.memoryTicks(); }
    @Override protected int meleeCooldownTicks() { return AngelEndermanConfig.meleeInterval(); }
    @Override protected boolean brainControlsMeleeCooldown() { return true; }
    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators(); this.updateThreats(level, now);
        // Native attribution has already been copied from the accepted-damage event into weak, finite records.
        this.angel.setLastHurtByMob(null);
        LivingEntity threat = this.observeThreat();
        if (this.tickFloating()) { this.cancelSupport(); this.angel.setCombatPhase(CombatPhase.IDLE); return; }
        if (threat != null) {
            this.cancelSupport(); this.stopAmbient();
            Threat record = this.threats.get(threat.getUUID());
            if (now < record.warningUntil) {
                this.stopCombat(); this.clearDefensiveTarget(); this.angel.getNavigation().stop();
                this.angel.getLookControl().setLookAt(threat, 30, 30); this.angel.setCombatPhase(CombatPhase.TERRITORIAL_WARNING);
                this.state = State.COMBAT; return;
            }
            // Do not chase beyond the fixed territorial boundary, including against distant ranged attackers.
            if (this.angel.position().distanceToSqr(record.anchor) >= square(AngelEndermanConfig.retreatDistance())
                    && !this.angel.isWithinMeleeAttackRange(threat)) {
                this.stopCombat(); this.clearDefensiveTarget(); this.angel.getNavigation().stop();
                this.angel.setCombatPhase(CombatPhase.DE_ESCALATING); this.state = State.COMBAT; return;
            }
            this.rememberVisibleThreat(threat);
            if (this.angel.getCombatPhase() != CombatPhase.STRIKE || now - this.angel.getCombatPhaseStart() >= 8)
                this.angel.setCombatPhase(CombatPhase.DEFENDING);
            this.tickVisibleCombat(threat); return;
        }
        this.stopCombat(); this.clearDefensiveTarget();
        this.state = State.IDLE;
        if (!this.threats.isEmpty()) {
            // Lost sight and retreat never use an endlessly refreshed player position or inherited investigation chase.
            this.angel.getNavigation().stop(); this.cancelSupport(); this.angel.setCombatPhase(CombatPhase.DE_ESCALATING); return;
        }
        this.angel.setLastHurtByMob(null);
        if (this.tickSupport(level, now)) return;
        if (now < this.recoveryUntil) {
            this.stopAmbient(); this.angel.getNavigation().stop(); this.angel.setCombatPhase(CombatPhase.RECOVERY); return;
        }
        if (now >= this.nextSupportScan) {
            this.nextSupportScan = now + AngelEndermanConfig.scanInterval();
            if (this.assessSupport(level, now)) return;
        }
        boolean observing = !level.getEntitiesOfClass(Player.class, this.angel.getBoundingBox().inflate(8), this::baseLegal).isEmpty();
        this.angel.setCombatPhase(observing ? CombatPhase.OBSERVING : CombatPhase.IDLE); this.tickAmbient();
    }
    private void clearDefensiveTarget() {
        this.clearTarget(); this.angel.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        this.angel.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
    public boolean canUseStrike(ServerLevel level, LivingEntity target) {
        Threat threat = this.threats.get(target.getUUID()); long now = level.getGameTime();
        return this.angel.level() == level && this.angel.isAlive() && !this.angel.isRemoved() && !this.angel.isNoAi()
                && this.isLegitimateThreat(target) && threat != null && now >= threat.warningUntil
                && this.pendingSupport == null && now >= this.recoveryUntil && now >= this.nextStrikeAt
                && !this.angel.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
                && this.angel.isWithinMeleeAttackRange(target) && this.angel.getSensing().hasLineOfSight(target);
    }
    public void onStrikeAttempt(ServerLevel level) {
        this.nextStrikeAt = level.getGameTime() + AngelEndermanConfig.meleeInterval();
        this.angel.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, AngelEndermanConfig.meleeInterval() - 1);
    }
    public boolean isEligibleAlly(@Nullable LivingEntity ally, double range) {
        return ally instanceof EnderMan && ally != this.angel && ally.isAlive() && !ally.isRemoved()
                && ally.level() == this.angel.level() && this.angel.distanceToSqr(ally) <= square(range)
                && this.angel.level().hasChunkAt(ally.blockPosition()) && this.angel.getSensing().hasLineOfSight(ally);
    }
    private boolean canHeal(EnderMan ally, long now) {
        return this.isEligibleAlly(ally, AngelEndermanConfig.supportRange()) && ally.getHealth() < ally.getMaxHealth()
                && now >= ally.getPersistentData().getLongOr(HEALING_RECIPIENT_LOCK, 0L);
    }
    private boolean canBless(EnderMan ally, long now) {
        if (!this.isEligibleAlly(ally, AngelEndermanConfig.benedictionRadius())
                || now < ally.getPersistentData().getLongOr(BENEDICTION_RECIPIENT_LOCK, 0L)) return false;
        LivingEntity enemy = ally.getTarget();
        if (ally.getHealth() >= ally.getMaxHealth() && (enemy == null || !enemy.isAlive() || enemy.isRemoved() || enemy.level() != ally.level())) return false;
        return BLESSINGS.stream().anyMatch(effect -> !ally.hasEffect(effect) && (effect != MobEffects.ABSORPTION || ally.getAbsorptionAmount() <= 0));
    }
    private boolean assessSupport(ServerLevel level, long now) {
        List<EnderMan> allies = level.getEntitiesOfClass(EnderMan.class,
                this.angel.getBoundingBox().inflate(AngelEndermanConfig.supportRange()), a -> this.isEligibleAlly(a, AngelEndermanConfig.supportRange()));
        if (now >= this.nextHealingAt) {
            EnderMan injured = allies.stream().filter(a -> this.canHeal(a, now))
                    .min(Comparator.comparingDouble(a -> a.getHealth() / a.getMaxHealth())).orElse(null);
            if (injured != null) {
                this.nextHealingAt = now + AngelEndermanConfig.healingCooldown();
                this.beginSupport(level, List.of(injured), false, now + AngelEndermanConfig.healCastTicks()); return true;
            }
        }
        if (now >= this.nextBenedictionAt) {
            List<EnderMan> recipients = allies.stream().filter(a -> this.canBless(a, now))
                    .sorted(Comparator.comparingDouble(a -> a.getHealth() / a.getMaxHealth()))
                    .limit(AngelEndermanConfig.maximumRecipients()).toList();
            if (!recipients.isEmpty()) {
                this.nextBenedictionAt = now + AngelEndermanConfig.benedictionCooldown();
                this.beginSupport(level, recipients, true, now + AngelEndermanConfig.benedictionCastTicks()); return true;
            }
        }
        return false;
    }
    private void beginSupport(ServerLevel level, List<EnderMan> recipients, boolean benediction, long commitAt) {
        this.supportEpoch++;
        this.stopAmbient(); this.angel.getNavigation().stop();
        this.pendingSupport = new SupportCast(recipients, benediction, commitAt);
        this.angel.setCombatPhase(benediction ? CombatPhase.BENEDICTION : CombatPhase.HEALING);
        this.angel.castCue(level, benediction);
    }
    private boolean tickSupport(ServerLevel level, long now) {
        SupportCast cast = this.pendingSupport;
        if (cast == null) return false;
        this.stopAmbient(); this.angel.getNavigation().stop();
        LivingEntity first = this.getPendingSupportTarget();
        if (first != null) this.angel.getLookControl().setLookAt(first, 20, 20);
        boolean any = cast.recipients.stream().map(WeakReference::get).anyMatch(a -> a instanceof EnderMan ally
                && (cast.benediction ? this.canBless(ally, now) : this.canHeal(ally, now)));
        if (!any || now > cast.commitAt + 2) { this.cancelSupport(); return false; }
        if (now < cast.commitAt) return true;
        // Remove the pending action before invoking event-bearing heal/effect APIs: no reentrant duplicate commit.
        this.pendingSupport = null; this.recoveryUntil = now + AngelEndermanConfig.recoveryTicks(); long transaction = this.supportEpoch;
        CombatPhase supportPhase = cast.benediction ? CombatPhase.BENEDICTION : CombatPhase.HEALING;
        for (WeakReference<LivingEntity> reference : cast.recipients) {
            if (!(reference.get() instanceof EnderMan ally)) continue;
            if (!this.mayFinishSupport(level, transaction, supportPhase)) break;
            if (cast.benediction ? !this.canBless(ally, now) : !this.canHeal(ally, now)) continue;
            if (cast.benediction) {
                // A recipient-wide lease prevents two casters from refreshing consumed Absorption or creating buff loops.
                ally.getPersistentData().putLong(BENEDICTION_RECIPIENT_LOCK, now + AngelEndermanConfig.recipientBenedictionInterval());
                for (Holder<MobEffect> effect : BLESSINGS) {
                    if (!this.mayFinishSupport(level, transaction, supportPhase) || !this.isEligibleAlly(ally, AngelEndermanConfig.benedictionRadius())) break;
                    // Never replace/refresh an existing effect, including higher amplifiers and depleted Absorption I.
                    if (!ally.hasEffect(effect) && (effect != MobEffects.ABSORPTION || ally.getAbsorptionAmount() <= 0))
                        ally.addEffect(new MobEffectInstance(effect, AngelEndermanConfig.benedictionDuration(), 0, false, true), this.angel);
                }
            } else {
                ally.getPersistentData().putLong(HEALING_RECIPIENT_LOCK, now + AngelEndermanConfig.recipientHealingInterval());
                ally.heal(Math.min(AngelEndermanConfig.healingAmount(), ally.getMaxHealth() - ally.getHealth()));
            }
            if (this.mayFinishSupport(level, transaction, supportPhase) && ally.isAlive() && !ally.isRemoved() && ally.level() == level) this.angel.supportCue(level, ally, cast.benediction);
        }
        if (this.mayFinishSupport(level, transaction, supportPhase)) this.angel.setCombatPhase(CombatPhase.RECOVERY);
        return true;
    }
    private boolean mayFinishSupport(ServerLevel level, long epoch, CombatPhase phase) {
        return this.supportEpoch == epoch && this.angel.level() == level && this.angel.isAlive() && !this.angel.isRemoved()
                && !this.angel.isNoAi() && this.angel.getCombatPhase() == phase && this.threats.isEmpty();
    }
    private void cancelSupport() {
        this.supportEpoch++;
        this.pendingSupport = null;
        if (this.angel.getCombatPhase() == CombatPhase.HEALING || this.angel.getCombatPhase() == CombatPhase.BENEDICTION)
            this.angel.setCombatPhase(CombatPhase.IDLE);
    }
    @Override public void clearEncounter() {
        this.cancelSupport(); this.threats.clear(); this.retreatedIntruders.clear(); this.recoveryUntil = 0;
        super.clearEncounter(); this.angel.setLastHurtByMob(null); this.angel.setCombatPhase(CombatPhase.IDLE);
    }
    public void writeCooldowns(ValueOutput output) {
        output.putLong("AngelStrikeAfter", this.nextStrikeAt); output.putLong("AngelHealingAfter", this.nextHealingAt);
        output.putLong("AngelBenedictionAfter", this.nextBenedictionAt);
    }
    public void readCooldowns(ValueInput input) {
        long now = this.angel.level().getGameTime();
        this.nextStrikeAt = Math.min(input.getLongOr("AngelStrikeAfter", 0), now + AngelEndermanConfig.meleeInterval());
        this.nextHealingAt = Math.min(input.getLongOr("AngelHealingAfter", 0), now + AngelEndermanConfig.healingCooldown());
        this.nextBenedictionAt = Math.min(input.getLongOr("AngelBenedictionAfter", 0), now + AngelEndermanConfig.benedictionCooldown());
    }
    private static double square(double n) { return n * n; }
    private static final class Threat {
        final WeakReference<LivingEntity> entity;
        final Vec3 anchor;
        long expiresAt, warningUntil, attackedAt = Long.MIN_VALUE / 2, retreatSince = -1;
        Threat(LivingEntity entity, Vec3 anchor, long now) {
            this.entity = new WeakReference<>(entity); this.anchor = anchor;
            this.expiresAt = now + AngelEndermanConfig.memoryTicks(); this.warningUntil = now + AngelEndermanConfig.warningTicks();
        }
    }
    private static final class SupportCast {
        final List<WeakReference<LivingEntity>> recipients = new ArrayList<>();
        final boolean benediction;
        final long commitAt;
        SupportCast(List<EnderMan> recipients, boolean benediction, long commitAt) {
            for (EnderMan recipient : recipients) this.recipients.add(new WeakReference<>(recipient));
            this.benediction = benediction; this.commitAt = commitAt;
        }
    }
}
