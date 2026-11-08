package com.ronm19.enderism.entity.ai;

import com.google.common.collect.ImmutableList;
import com.ronm19.enderism.CombatBalanceConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Per-entity native Brain decision owner with transient encounter memories. */
public class MastermindBrain extends Behavior<AbstractEnderman> {
    private static final int MEMORY_TICKS = 60;
    protected final AbstractEnderman owner;
    private final GroupTactics groupTactics;
    protected State state = State.IDLE;
    protected int handledHurtTimestamp = Integer.MIN_VALUE;
    private @Nullable MeleeAttackGoal combat;
    private @Nullable FloatGoal floating;
    private @Nullable WaterAvoidingRandomStrollGoal stroll;
    private @Nullable LookAtPlayerGoal lookAtPlayer;
    private @Nullable RandomLookAroundGoal randomLook;
    private @Nullable Goal ambient;
    private boolean combatRunning;
    private boolean floatingRunning;
    private boolean investigationStarted;

    public MastermindBrain(AbstractEnderman owner) {
        super(Map.of(
                MemoryModuleType.HURT_BY_ENTITY, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.REGISTERED));
        this.owner = owner;
        this.groupTactics = new GroupTactics(this, owner);
    }

    public static Brain.Provider<AbstractEnderman> provider(MastermindBrain decisions) {
        // Behavior requirements register only the memories used by this per-entity controller.
        return Brain.provider(List.of(), body -> List.of(
                ActivityData.<AbstractEnderman>create(Activity.CORE, 0, ImmutableList.of(decisions))));
    }

    public State getState() {
        return this.state;
    }

    public final GroupTactics groupTactics() { return this.groupTactics; }
    public final boolean supportsGroups() { return this.groupTactics.supportsGroups(); }

    public @Nullable Vec3 getLastObservedPosition() {
        return this.owner.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target.getTarget().currentPosition()).orElse(null);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, AbstractEnderman body) {
        return body.isAlive() && !body.isRemoved();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, AbstractEnderman body, long timestamp) {
        return body.isAlive() && !body.isRemoved();
    }

    @Override
    protected boolean timedOut(long timestamp) {
        return false;
    }

    @Override
    protected void tick(ServerLevel level, AbstractEnderman body, long timestamp) {
        this.initializeActuators();
        Brain<AbstractEnderman> brain = body.getBrain();
        LivingEntity rememberedThreat = brain.getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        if (rememberedThreat != null && !this.isLegalThreat(rememberedThreat)) {
            this.clearEncounter();
        }
        LivingEntity visibleThreat = this.observeThreat();

        if (this.tickFloating()) {
            return;
        }

        if (visibleThreat != null) {
            this.tickVisibleCombat(visibleThreat);
            return;
        }

        this.stopCombat();
        this.clearTarget();
        if (this.tickInvestigation(level)) {
            return;
        }
        if (this.state != State.IDLE) {
            this.clearEncounter();
        }
        this.tickAmbient();
    }

    @Override
    protected void stop(ServerLevel level, AbstractEnderman body, long timestamp) {
        this.clearEncounter();
    }

    public void clearEncounter() {
        this.groupTactics.clear();
        this.stopCombat();
        this.stopFloating();
        this.stopAmbient();
        this.clearTarget();
        this.owner.getNavigation().stop();
        Brain<AbstractEnderman> brain = this.owner.getBrain();
        brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        // ATTACK_COOLING_DOWN expires in Brain; clearing an encounter must not reset the actual attack interval.
        this.investigationStarted = false;
        this.state = State.IDLE;
    }

    protected @Nullable LivingEntity observeThreat() {
        Brain<AbstractEnderman> brain = this.owner.getBrain();
        LivingEntity threat = brain.getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        LivingEntity attacker = this.owner.getLastHurtByMob();
        int hurtTimestamp = this.owner.getLastHurtByMobTimestamp();
        int elapsed = this.owner.tickCount - hurtTimestamp;
        if (elapsed >= 0 && elapsed <= this.memoryTicks()
                && (hurtTimestamp != this.handledHurtTimestamp || threat != null)
                && this.isVisibleThreat(attacker)) {
            threat = attacker;
            this.handledHurtTimestamp = hurtTimestamp;
        }
        if (!this.isVisibleThreat(threat)) {
            return null;
        }

        this.rememberVisibleThreat(threat);
        return threat;
    }

    protected void rememberVisibleThreat(LivingEntity threat) {
        this.groupTactics.observe(threat);
        Brain<AbstractEnderman> brain = this.owner.getBrain();
        brain.setMemoryWithExpiry(MemoryModuleType.HURT_BY_ENTITY, threat, this.memoryTicks());
        WalkTarget previous = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        Vec3 observedPosition = threat.position();
        if (previous == null || !previous.getTarget().currentPosition().equals(observedPosition)) {
            previous = new WalkTarget(new BlockPosTracker(observedPosition), 1.0F, 1);
        }
        brain.setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, previous, this.memoryTicks());
    }

    protected boolean isVisibleThreat(@Nullable LivingEntity target) {
        if (!this.isLegalThreat(target)) {
            return false;
        }
        double range = Math.min(64.0, this.owner.getAttributeValue(Attributes.FOLLOW_RANGE));
        return this.owner.distanceToSqr(target) <= range * range && this.owner.getSensing().hasLineOfSight(target);
    }

    protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return target != null && target != this.owner && target.isAlive() && !target.isRemoved()
                && target.level() == this.owner.level() && this.owner.canAttack(target)
                && !this.owner.isAlliedTo(target)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    protected int memoryTicks() {
        return MEMORY_TICKS;
    }

    protected int meleeCooldownTicks() {
        return 20;
    }

    /** Opt in only when a species' Brain supplies the complete attack interval. */
    protected boolean brainControlsMeleeCooldown() {
        return false;
    }

    protected void initializeActuators() {
        // Every species calls this entry point, including those with their own server AI method.
        if (this.owner.level() instanceof ServerLevel level) this.groupTactics.beforeBrainTick(level);
        if (this.combat != null) {
            return;
        }
        // Goal helpers are unregistered actuators. Native Brain alone chooses and ticks them.
        this.combat = new MeleeAttackGoal(this.owner, 1.0, false) {
            @Override
            protected boolean isTimeToAttack() {
                // Native resetAttackCooldown hardcodes twenty ticks, even if getAttackInterval is overridden.
                return MastermindBrain.this.brainControlsMeleeCooldown()
                        ? !owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
                        : super.isTimeToAttack();
            }

            @Override
            protected boolean canPerformAttack(LivingEntity target) {
                return !owner.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)
                        && super.canPerformAttack(target);
            }

            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
                if (this.canPerformAttack(target)) {
                    long previousAttempts = owner.committedMeleeAttempts();
                    boolean accepted = owner.doHurtTarget(getServerLevel(owner), target);
                    boolean committed = accepted || owner.committedMeleeAttempts() != previousAttempts;
                    if (committed) {
                        this.resetAttackCooldown();
                        owner.swing(InteractionHand.MAIN_HAND);
                    }
                    // A rejected windup can retry soon; real attacks still spend their full interval,
                    // including hits blocked by shields, native immunity, or other damage listeners.
                    int cooldown = committed || !CombatBalanceConfig.enabled()
                            ? meleeCooldownTicks() : CombatBalanceConfig.rejectedAttackRetryTicks();
                    owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, cooldown);
                }
            }
        };
        this.floating = new FloatGoal(this.owner);
        this.stroll = new WaterAvoidingRandomStrollGoal(this.owner, 1.0, 0.0F);
        this.lookAtPlayer = new LookAtPlayerGoal(this.owner, Player.class, 8.0F);
        this.randomLook = new RandomLookAroundGoal(this.owner);
    }

    protected boolean tickFloating() {
        if (!this.floating.canUse()) {
            this.stopFloating();
            return false;
        }
        this.stopCombat();
        this.stopAmbient();
        this.clearTarget();
        if (!this.floatingRunning) {
            this.floating.start();
            this.floatingRunning = true;
        }
        this.floating.tick();
        this.state = this.owner.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET) ? State.INVESTIGATE : State.IDLE;
        return true;
    }

    protected void tickVisibleCombat(LivingEntity threat) {
        this.stopAmbient();
        this.state = State.COMBAT;
        this.investigationStarted = false;
        if (this.groupTactics.tickCombat(threat)) {
            this.stopCombat();
            this.clearTarget();
            this.owner.getLookControl().setLookAt(threat, 30.0F, 30.0F);
            return;
        }
        this.owner.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, threat, this.memoryTicks());
        if (this.owner.getTarget() != threat) {
            this.owner.setTarget(threat);
        }
        if (!this.combatRunning && this.combat.canUse()) {
            this.combat.start();
            this.combatRunning = true;
        }
        if (this.combatRunning) {
            this.combat.tick();
        } else {
            this.owner.getLookControl().setLookAt(threat, 30.0F, 30.0F);
        }
    }

    protected boolean tickInvestigation(ServerLevel level) {
        WalkTarget lastSeen = this.owner.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (lastSeen == null) {
            return false;
        }
        this.stopAmbient();
        this.state = State.INVESTIGATE;
        Vec3 position = lastSeen.getTarget().currentPosition();
        if (!this.investigationStarted) {
            this.investigationStarted = true;
            if (!level.hasChunkAt(lastSeen.getTarget().currentBlockPosition())
                    || !this.owner.getNavigation().moveTo(position.x, position.y, position.z, lastSeen.getSpeedModifier())) {
                this.clearEncounter();
                return true;
            }
        }
        this.owner.getLookControl().setLookAt(position.x, position.y + 1.5, position.z);
        if (this.owner.distanceToSqr(position) <= 2.25 || this.owner.getNavigation().isDone()) {
            this.clearEncounter();
        }
        return true;
    }

    protected void tickAmbient() {
        if (this.ambient != null && !this.ambient.canContinueToUse()) {
            this.stopAmbient();
        }
        if (this.ambient == null) {
            if (this.stroll.canUse()) {
                this.ambient = this.stroll;
            } else if (this.lookAtPlayer.canUse()) {
                this.ambient = this.lookAtPlayer;
            } else if (this.randomLook.canUse()) {
                this.ambient = this.randomLook;
            }
            if (this.ambient != null) {
                this.ambient.start();
            }
        }
        if (this.ambient != null) {
            this.ambient.tick();
        }
    }

    protected void stopCombat() {
        if (this.combatRunning) {
            this.combat.stop();
            this.combatRunning = false;
        }
    }

    protected void stopFloating() {
        if (this.floatingRunning) {
            this.floating.stop();
            this.floatingRunning = false;
        }
    }

    protected void stopAmbient() {
        if (this.ambient != null) {
            this.ambient.stop();
            this.ambient = null;
        }
    }

    protected void clearTarget() {
        this.owner.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        if (this.owner.getTarget() != null) {
            this.owner.setTarget(null);
        }
        this.owner.setPersistentAngerTarget(null);
        this.owner.setPersistentAngerEndTime(0L);
    }

    public enum State {
        IDLE,
        COMBAT,
        INVESTIGATE
    }
}
