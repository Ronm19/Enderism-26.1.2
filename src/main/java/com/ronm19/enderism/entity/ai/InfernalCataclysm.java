package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.InfernalMeteorConfig;
import com.ronm19.enderism.entity.InfernalEnderman;
import com.ronm19.enderism.entity.InfernalEnderman.CombatPhase;
import com.ronm19.enderism.entity.projectile.InfernalMeteor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Server-owned Cataclysm timeline. Impact lanes are locked at wind-up. */
public final class InfernalCataclysm {
    private static final int RECOVERY_TICKS = 24;
    private static final int SMALL_WARNING_TICKS = 7;
    private static final int FAILED_CAST_COOLDOWN = 80;
    private static final double CAST_SEPARATION = 80.0;
    private static final DustParticleOptions SMALL_WARNING = new DustParticleOptions(0xFF681E, 0.9F);
    private static final DustParticleOptions FINAL_WARNING = new DustParticleOptions(0xFF300C, 1.45F);
    private final InfernalEnderman owner;
    private final List<InfernalMeteor> airborne = new ArrayList<>();
    private List<ImpactPlan> barrage = List.of();
    private @Nullable ImpactPlan finalPlan;
    private @Nullable UUID targetId;
    private InfernalMeteorConfig.Settings settings = InfernalMeteorConfig.settings();
    private Stage stage = Stage.INACTIVE;
    private long nextAttemptAt;
    private long stageStarted;
    private long stageDeadline;
    private long nextShotAt;
    private int nextShot;

    public InfernalCataclysm(InfernalEnderman owner) {
        this.owner = owner;
    }

    public boolean isActive() {
        return this.stage != Stage.INACTIVE;
    }

    public long cooldownDeadline() {
        return this.nextAttemptAt;
    }

    public void restoreCooldownDeadline(long deadline) {
        this.nextAttemptAt = Math.max(0, deadline);
    }

    public boolean tryBegin(ServerLevel level, LivingEntity target, long now) {
        if (this.isActive() || now < this.nextAttemptAt || !this.owner.canDamageWithInfernalAbility(target)) {
            return false;
        }
        var settings = InfernalMeteorConfig.settings();
        double range = settings.activationRange();
        double distance = this.owner.distanceToSqr(target);
        if (distance < 36.0 || distance > range * range || !this.owner.hasLineOfSight(target)) {
            return false;
        }
        this.nextAttemptAt = now + 30;
        if (!level.getEntitiesOfClass(InfernalEnderman.class, this.owner.getBoundingBox().inflate(CAST_SEPARATION),
                other -> other != this.owner && other.hasActiveCataclysm()).isEmpty()) {
            return false;
        }

        Vec3 focus = target.position();
        ImpactPlan giant = this.findFinalPlan(level, focus, (float)settings.finalSize());
        if (giant == null) {
            return false;
        }
        List<ImpactPlan> small = this.planBarrage(level, focus, settings.smallCount());
        if (small.size() < settings.smallCount()) {
            return false;
        }
        this.settings = settings;
        this.targetId = target.getUUID();
        this.barrage = small;
        this.finalPlan = giant;
        this.nextShot = 0;
        this.nextAttemptAt = now + FAILED_CAST_COOLDOWN;
        this.transition(Stage.PREPARATION, CombatPhase.METEOR_PREP, now, settings.windupTicks());
        this.owner.playSound(SoundEvents.GHAST_WARN, 1.25F, 0.65F);
        return true;
    }

    public void tick(ServerLevel level, long now) {
        if (!this.isActive()) {
            return;
        }
        if (!this.owner.isAlive() || this.owner.isRemoved() || this.owner.isNoAi()) {
            this.cancel();
            return;
        }
        if (this.stage == Stage.RECOVERY) {
            this.tickRecovery(level, now);
            return;
        }
        var target = this.targetId == null ? null : level.getEntity(this.targetId);
        double disengage = this.settings.activationRange() + 28.0;
        if (!(target instanceof LivingEntity victim) || !this.owner.canDamageWithInfernalAbility(victim)
                || this.owner.distanceToSqr(victim) > disengage * disengage || !this.updateAirborne(now)) {
            this.cancel();
            return;
        }

        switch (this.stage) {
            case PREPARATION -> {
                this.castParticles(level, now);
                if (now >= this.stageDeadline) {
                    this.transition(Stage.BARRAGE, CombatPhase.METEOR_BARRAGE, now, 0);
                    this.nextShotAt = now + SMALL_WARNING_TICKS;
                    this.warningRing(level, this.barrage.getFirst().impact(), this.settings.smallRadius(), false);
                }
            }
            case BARRAGE -> this.tickBarrage(level, now);
            case FINAL_WARNING -> this.tickFinalWarning(level, now);
            case FINAL_DESCENT -> {
                this.castParticles(level, now);
                if (this.airborne.isEmpty()) {
                    this.nextAttemptAt = now + this.settings.cooldownTicks();
                    this.transition(Stage.RECOVERY, CombatPhase.RECOVERY, now, RECOVERY_TICKS);
                }
            }
            default -> {
            }
        }
    }

    private void tickBarrage(ServerLevel level, long now) {
        this.castParticles(level, now);
        if (this.nextShot < this.barrage.size()) {
            ImpactPlan plan = this.barrage.get(this.nextShot);
            if (now == this.nextShotAt - SMALL_WARNING_TICKS || now == this.nextShotAt - 2) {
                this.warningRing(level, plan.impact(), this.settings.smallRadius(), false);
            }
            if (now >= this.nextShotAt) {
                if (!this.launch(level, plan, false)) {
                    this.cancel();
                    return;
                }
                this.nextShot++;
                this.nextShotAt = now + this.settings.barrageInterval();
            }
        } else if (this.airborne.isEmpty()) {
            // The last small impact, rather than just its launch, gates the final warning.
            this.transition(Stage.FINAL_WARNING, CombatPhase.METEOR_FINAL, now, this.settings.finalWarning());
            ImpactPlan plan = this.finalPlan;
            if (plan != null) {
                Vec3 point = plan.impact();
                level.playSound(null, point.x, point.y, point.z, SoundEvents.ENDER_DRAGON_GROWL,
                        SoundSource.HOSTILE, 1.8F, 0.63F);
            }
        }
    }

    private void tickFinalWarning(ServerLevel level, long now) {
        ImpactPlan plan = this.finalPlan;
        if (plan == null || !this.laneLoaded(level, plan, true)) {
            this.cancel();
            return;
        }
        this.castParticles(level, now);
        if ((now - this.stageStarted) % 6 == 1) {
            this.warningRing(level, plan.impact(), this.settings.finalRadius(), true);
        }
        if (now >= this.stageDeadline) {
            if (!this.launch(level, plan, true)) {
                this.cancel();
                return;
            }
            this.transition(Stage.FINAL_DESCENT, CombatPhase.METEOR_FINAL, now, 0);
        }
    }

    private void tickRecovery(ServerLevel level, long now) {
        if (this.finalPlan != null && now < this.stageDeadline && (now - this.stageStarted) % 6 == 0) {
            Vec3 point = this.finalPlan.impact();
            level.sendParticles(ParticleTypes.ASH, point.x, point.y + 0.5, point.z, 7, 1.7, 0.35, 1.7, 0.018);
            level.sendParticles(ParticleTypes.SMOKE, point.x, point.y + 0.3, point.z, 6, 1.2, 0.3, 1.2, 0.025);
        }
        if (now >= this.stageDeadline) {
            this.reset();
        }
    }

    private void transition(Stage stage, CombatPhase phase, long now, int duration) {
        this.stage = stage;
        this.stageStarted = now;
        this.stageDeadline = now + duration;
        this.owner.setCombatPhase(phase);
    }

    public void cancel() {
        if (!this.isActive()) {
            return;
        }
        this.nextAttemptAt = Math.max(this.nextAttemptAt, this.owner.level().getGameTime() + FAILED_CAST_COOLDOWN);
        List<InfernalMeteor> cancelled = List.copyOf(this.airborne);
        this.reset();
        cancelled.forEach(InfernalMeteor::discard);
    }

    private void reset() {
        this.airborne.clear();
        this.barrage = List.of();
        this.finalPlan = null;
        this.targetId = null;
        this.stage = Stage.INACTIVE;
        this.owner.setCombatPhase(CombatPhase.IDLE);
    }

    private boolean updateAirborne(long now) {
        var iterator = this.airborne.iterator();
        while (iterator.hasNext()) {
            InfernalMeteor meteor = iterator.next();
            if (meteor.isRemoved()) {
                if (!meteor.hasImpacted()) {
                    return false;
                }
                iterator.remove();
            } else if (meteor.flightExpired(now)) {
                // The caster also owns the deadline if a projectile chunk stops ticking.
                return false;
            }
        }
        return true;
    }

    private boolean launch(ServerLevel level, ImpactPlan plan, boolean giant) {
        UUID target = this.targetId;
        float diameter = giant ? (float)this.settings.finalSize() : InfernalMeteor.SMALL_DIAMETER;
        if (target == null || !this.laneLoaded(level, plan, giant)
                || !level.noBlockCollision(null, InfernalMeteor.bodyAt(plan.launch(), diameter))) {
            return false;
        }
        InfernalMeteor meteor = InfernalMeteor.create(level, this.owner, target, plan.launch(), giant, this.settings);
        this.airborne.add(meteor);
        if (!level.addFreshEntity(meteor) || meteor.isRemoved() || !meteor.isAddedToLevel() || !this.isActive()) {
            this.airborne.remove(meteor);
            meteor.discard();
            return false;
        }
        Vec3 point = plan.launch();
        level.playSound(null, point.x, point.y, point.z, giant ? SoundEvents.GHAST_SHOOT : SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE, giant ? 1.7F : 0.8F, giant ? 0.63F : 0.84F);
        return true;
    }

    private boolean laneLoaded(ServerLevel level, ImpactPlan plan, boolean giant) {
        float size = giant ? (float)this.settings.finalSize() : InfernalMeteor.SMALL_DIAMETER;
        AABB lane = InfernalMeteor.bodyAt(plan.impact(), size)
                .expandTowards(0.0, plan.launch().y - plan.impact().y, 0.0);
        return InfernalMeteor.loadedArea(level, lane.inflate(1.0, 0.0, 1.0), true);
    }

    private @Nullable ImpactPlan findFinalPlan(ServerLevel level, Vec3 focus, float diameter) {
        ImpactPlan atFocus = this.findLane(level, focus, diameter, true);
        if (atFocus != null) {
            return atFocus;
        }
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            var plan = this.findLane(level, focus.add(Math.cos(angle) * 2.8, 0.0, Math.sin(angle) * 2.8),
                    diameter, true);
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    private List<ImpactPlan> planBarrage(ServerLevel level, Vec3 focus, int count) {
        var plans = new ArrayList<ImpactPlan>(count);
        double rotation = this.owner.getRandom().nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < count * 3 && plans.size() < count; i++) {
            double angle = rotation + i * 2.399963229728653;
            double radius = i == 0 ? 1.6 : 3.1 + (i % 3) * 0.8;
            var plan = this.findLane(level, focus.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius),
                    InfernalMeteor.SMALL_DIAMETER, false);
            if (plan != null && plans.stream().noneMatch(other ->
                    other.impact().subtract(plan.impact()).horizontalDistanceSqr() < 2.25)) {
                plans.add(plan);
            }
        }
        return plans;
    }

    private @Nullable ImpactPlan findLane(ServerLevel level, Vec3 focus, float diameter, boolean giant) {
        double ceiling = Math.min(level.getMaxY() - 0.4, focus.y + (giant ? 23.0 : 17.0));
        double bottom = Math.max(level.getMinY() + 0.1, focus.y - 7.0);
        Vec3 center = new Vec3(Math.floor(focus.x) + 0.5, focus.y, Math.floor(focus.z) + 0.5);
        AABB search = new AABB(center.x, bottom, center.z, center.x, ceiling, center.z)
                .inflate(diameter / 2.0 + 1.0, 0.0, diameter / 2.0 + 1.0);
        if (!InfernalMeteor.loadedArea(level, search, true)) {
            return null;
        }
        var floor = level.clip(new ClipContext(center.add(0.0, 1.35, 0.0), new Vec3(center.x, bottom, center.z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this.owner));
        if (floor.getType() != HitResult.Type.BLOCK || floor.isInside()) {
            return null;
        }
        Vec3 impact = floor.getLocation().add(0.0, 0.06, 0.0);
        double minFall = giant ? 5.0 : 3.5;
        AABB base = InfernalMeteor.bodyAt(impact, diameter);
        var overhead = level.clip(new ClipContext(impact, new Vec3(impact.x, ceiling, impact.z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.owner));
        double top = overhead.getType() == HitResult.Type.BLOCK ? overhead.getLocation().y : ceiling;
        double fall = Math.min(giant ? 18.0 : 12.0, top - diameter - impact.y - 0.12);
        // The full meteor width must fit through the approach, including beneath uneven Nether ceilings.
        for (int attempt = 0; attempt < 5 && fall >= minFall; attempt++, fall -= 2.0) {
            if (level.noBlockCollision(null, base.expandTowards(0.0, fall, 0.0))) {
                return new ImpactPlan(impact, impact.add(0.0, fall, 0.0));
            }
        }
        return null;
    }

    private void castParticles(ServerLevel level, long now) {
        if (now % 5 != 0) {
            return;
        }
        double x = this.owner.getX();
        double y = this.owner.getY() + 1.75;
        double z = this.owner.getZ();
        boolean climax = this.stage == Stage.FINAL_WARNING || this.stage == Stage.FINAL_DESCENT;
        level.sendParticles(ParticleTypes.FLAME, x, y, z, climax ? 10 : 6, 0.44, 0.64, 0.44, 0.045);
        level.sendParticles(ParticleTypes.ASH, x, y, z, 4, 0.54, 0.7, 0.54, 0.035);
    }

    private void warningRing(ServerLevel level, Vec3 point, double radius, boolean giant) {
        int particles = giant ? 24 : 12;
        for (int i = 0; i < particles; i++) {
            double angle = i * Math.PI * 2.0 / particles;
            level.sendParticles(giant ? FINAL_WARNING : SMALL_WARNING,
                    point.x + Math.cos(angle) * radius, point.y + 0.18, point.z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.FLAME, point.x, point.y + 0.35, point.z,
                giant ? 13 : 5, 0.2, 0.4, 0.2, 0.025);
    }

    private enum Stage { INACTIVE, PREPARATION, BARRAGE, FINAL_WARNING, FINAL_DESCENT, RECOVERY }

    private record ImpactPlan(Vec3 impact, Vec3 launch) {
    }
}
