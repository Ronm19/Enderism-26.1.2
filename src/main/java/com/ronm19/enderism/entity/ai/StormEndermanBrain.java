package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.StormEndermanConfig;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.StormEnderman;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Three storm decisions reuse the shared native Brain's perception, melee, and finite memory. */
public final class StormEndermanBrain extends MastermindBrain {
    private static final double STRIKE_RADIUS = 1.75;
    private static final int TEMPEST_STRIKE_WARNING = 10;
    private final StormEnderman storm;
    private long nextPlayerScanAt;
    private long nextThunderAt;
    private long nextGustAt;
    private long nextTempestAt;
    private Ability ability = Ability.NONE;
    private @Nullable LivingEntity abilityTarget;
    private @Nullable Vec3 lockedStrikePoint;
    private long abilityEndsAt;
    private long gustUntil;
    private long nextProjectileScanAt;
    private final Set<UUID> deflectedProjectiles = new HashSet<>();
    private long nextTempestSelectionAt;
    private @Nullable Vec3 tempestPendingPoint;
    private long tempestPendingAt;

    public StormEndermanBrain(StormEnderman owner) { super(owner); this.storm = owner; }
    @Override protected int memoryTicks() { return 100; }
    @Override protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target) && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }
    @Override protected @Nullable LivingEntity observeThreat() {
        LivingEntity visible=super.observeThreat();
        if (visible!=null) return visible;
        if (!(this.owner.level() instanceof ServerLevel level) || level.getGameTime()<this.nextPlayerScanAt) return null;
        this.nextPlayerScanAt=level.getGameTime()+StormEndermanConfig.SENSOR_INTERVAL_TICKS;
        double closest=StormEndermanConfig.DEFAULT_FOLLOW_RANGE*StormEndermanConfig.DEFAULT_FOLLOW_RANGE;
        for (ServerPlayer player:level.getEntitiesOfClass(ServerPlayer.class,this.owner.getBoundingBox().inflate(StormEndermanConfig.DEFAULT_FOLLOW_RANGE))) {
            double distance=this.owner.distanceToSqr(player);
            if (distance<=closest && this.isVisibleThreat(player)) { visible=player; closest=distance; }
        }
        if (visible!=null) this.rememberVisibleThreat(visible);
        return visible;
    }
    public Ability getAbility() { return this.ability; }
    public long nextThunderReadyAt() { return this.nextThunderAt; }
    public long nextGustReadyAt() { return this.nextGustAt; }
    public long nextTempestReadyAt() { return this.nextTempestAt; }
    public @Nullable Vec3 getLockedStrikePoint() { return this.lockedStrikePoint; }
    public @Nullable Vec3 getPendingTempestStrikePoint() { return this.tempestPendingPoint; }
    public int getGustTicksRemaining() { return (int)Math.max(0L,this.gustUntil-this.owner.level().getGameTime()); }

    @Override protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        this.initializeActuators();
        this.tickTempest(level,now);
        this.tickGustProjectiles(level,now);
        if (this.ability!=Ability.NONE) { this.tickCast(level,now); return; }
        LivingEntity visible=this.observeThreat();
        if (this.tickFloating()) { this.storm.setHuntState(StormEnderman.HuntState.SEARCHING); return; }
        if (visible!=null) {
            if (this.beginGust(level,visible,now)) return;
            if (visible instanceof ServerPlayer player) {
                if (this.beginTempest(level,player,now) || this.beginThunderStrike(level,player,now)) return;
            }
            this.tickVisibleCombat(visible);
            this.storm.setHuntState(StormEnderman.HuntState.PURSUING);
            return;
        }
        this.stopCombat(); this.clearTarget();
        if (this.tickInvestigation(level)) { this.storm.setHuntState(StormEnderman.HuntState.SEARCHING); return; }
        if (this.state!=State.IDLE) this.clearEncounter();
        this.storm.setHuntState(StormEnderman.HuntState.IDLE);
        this.tickAmbient();
    }

    private boolean eligibleCastTarget(ServerLevel level, LivingEntity target, double range, boolean sky) {
        return this.ability==Ability.NONE && this.isVisibleThreat(target) && this.owner.distanceToSqr(target)<=range*range
                && !target.isInvulnerableTo(level,this.owner.damageSources().mobAttack(this.owner))
                && (!sky || StormEnderman.exposed(level,target.position()));
    }
    private boolean beginThunderStrike(ServerLevel level, ServerPlayer player, long now) {
        if (now<this.nextThunderAt || !this.eligibleCastTarget(level,player,StormEndermanConfig.thunderRange(),true)) return false;
        this.nextThunderAt=now+StormEndermanConfig.thunderCooldownTicks();
        this.startCast(Ability.THUNDER_STRIKE,player,player.position(),now+StormEndermanConfig.thunderWarningTicks());
        this.storm.strikeWarning(level,this.lockedStrikePoint);
        return true;
    }
    private boolean beginGust(ServerLevel level, LivingEntity target, long now) {
        if (now<this.nextGustAt || !this.eligibleCastTarget(level,target,StormEndermanConfig.gustRange(),false)) return false;
        this.nextGustAt=now+StormEndermanConfig.gustCooldownTicks();
        this.startCast(Ability.STORM_GUST,target,null,now+StormEndermanConfig.gustWarningTicks());
        this.storm.gustWarning(level);
        return true;
    }
    private boolean beginTempest(ServerLevel level, ServerPlayer player, long now) {
        if (now<this.nextTempestAt || this.storm.isTempestActive()
                || !this.eligibleCastTarget(level,player,StormEndermanConfig.tempestRange(),true)) return false;
        this.nextTempestAt=now+StormEndermanConfig.tempestCooldownTicks();
        this.startCast(Ability.TEMPEST_SUMMON,player,Vec3.atBottomCenterOf(player.blockPosition()),now+StormEndermanConfig.tempestWarningTicks());
        this.storm.strikeWarning(level,this.lockedStrikePoint);
        return true;
    }
    private void startCast(Ability next, LivingEntity target, @Nullable Vec3 point, long until) {
        this.ability=next; this.abilityTarget=target; this.lockedStrikePoint=point; this.abilityEndsAt=until;
        this.storm.setCast(next,until); this.storm.setHuntState(StormEnderman.HuntState.CASTING);
        this.stopCombat(); this.stopAmbient(); this.clearTarget(); this.freezeMovement(); this.state=State.COMBAT;
    }
    private void tickCast(ServerLevel level, long now) {
        LivingEntity target=this.abilityTarget;
        double range=switch(this.ability) {
            case THUNDER_STRIKE -> StormEndermanConfig.thunderRange();
            case STORM_GUST -> StormEndermanConfig.gustRange();
            case TEMPEST_SUMMON -> StormEndermanConfig.tempestRange();
            default -> 0;
        };
        if (!this.isVisibleThreat(target) || this.owner.distanceToSqr(target)>range*range
                || target.isInvulnerableTo(level,this.owner.damageSources().mobAttack(this.owner))
                || (this.ability!=Ability.STORM_GUST && (this.lockedStrikePoint==null || !StormEnderman.exposed(level,this.lockedStrikePoint)))) {
            this.interruptCast(); return;
        }
        this.freezeMovement(); this.owner.getLookControl().setLookAt(target,60,60);
        if (this.lockedStrikePoint!=null && now%5==0) this.storm.strikeWarning(level,this.lockedStrikePoint);
        if (now<this.abilityEndsAt) return;
        Ability resolved=this.ability; Vec3 point=this.lockedStrikePoint;
        this.interruptCast();
        switch(resolved) {
            case THUNDER_STRIKE -> { if (point!=null) this.resolveStrike(level,point,StormEndermanConfig.thunderDamage(),false); }
            case STORM_GUST -> this.releaseGust(level,now);
            case TEMPEST_SUMMON -> {
                if (point!=null) {
                    this.storm.setTempest(BlockPos.containing(point),(float)StormEndermanConfig.tempestRadius(),now+StormEndermanConfig.tempestDurationTicks());
                    beginWorldThunder(level);
                    this.nextTempestSelectionAt=now; this.tempestPendingPoint=null; this.tempestPendingAt=0;
                }
            }
            default -> { }
        }
    }
    private void resolveStrike(ServerLevel level, Vec3 point, double damage, boolean tempest) {
        if (!StormEnderman.exposed(level,point)) return;
        this.storm.showLightning(level,point);
        DamageSource source=new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.LIGHTNING_BOLT),this.owner);
        AABB area=new AABB(point.x-STRIKE_RADIUS,point.y-1,point.z-STRIKE_RADIUS,point.x+STRIKE_RADIUS,point.y+3,point.z+STRIKE_RADIUS);
        for (LivingEntity victim:level.getEntitiesOfClass(LivingEntity.class,area)) {
            double x=victim.getX()-point.x,z=victim.getZ()-point.z;
            if (x*x+z*z<=STRIKE_RADIUS*STRIKE_RADIUS && (!tempest || this.insideTempest(victim.position())) && this.isLegalThreat(victim)
                    && this.owner.getSensing().hasLineOfSight(victim) && StormEnderman.exposed(level,victim.position()))
                victim.hurtServer(level,source,(float)damage);
        }
    }
    private void releaseGust(ServerLevel level, long now) {
        double range=StormEndermanConfig.gustRange();
        for (LivingEntity victim:level.getEntitiesOfClass(LivingEntity.class,this.owner.getBoundingBox().inflate(range))) {
            if (!this.isVisibleThreat(victim) || this.owner.distanceToSqr(victim)>range*range
                    || victim.isInvulnerableTo(level,this.owner.damageSources().mobAttack(this.owner))) continue;
            victim.knockback(StormEndermanConfig.gustKnockback(),this.owner.getX()-victim.getX(),this.owner.getZ()-victim.getZ());
            victim.hurtMarked=true;
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,StormEndermanConfig.gustSlowTicks(),0),this.owner);
        }
        level.sendParticles(ParticleTypes.CLOUD,this.owner.getX(),this.owner.getY()+1,this.owner.getZ(),24,range*.35,.3,range*.35,.12);
        this.gustUntil=now+StormEndermanConfig.gustProjectileTicks(); this.nextProjectileScanAt=now;
        this.deflectedProjectiles.clear(); this.tickGustProjectiles(level,now);
    }
    private void tickGustProjectiles(ServerLevel level, long now) {
        if (now>=this.gustUntil) { this.deflectedProjectiles.clear(); return; }
        if (now<this.nextProjectileScanAt || this.deflectedProjectiles.size()>=64) return;
        this.nextProjectileScanAt=now+4;
        double range=StormEndermanConfig.gustRange(); int checked=0;
        for (AbstractArrow arrow:level.getEntitiesOfClass(AbstractArrow.class,this.owner.getBoundingBox().inflate(range))) {
            if (++checked>32) break;
            // Native AbstractArrow.isPushedByFluid is public and returns !isInGround().
            if (!arrow.isAlive() || !arrow.isPushedByFluid() || arrow.isNoPhysics() || this.deflectedProjectiles.contains(arrow.getUUID())
                    || arrow.getOwner()==this.owner || this.owner.distanceToSqr(arrow)>range*range
                    || !this.owner.getSensing().hasLineOfSight(arrow)) continue;
            Vec3 velocity=arrow.getDeltaMovement(), toward=this.owner.position().add(0,1,0).subtract(arrow.position());
            if (velocity.lengthSqr()<.01 || velocity.dot(toward)<=0) continue;
            Vec3 radial=arrow.position().subtract(this.owner.position()).multiply(1,0,1);
            if (radial.lengthSqr()<.001) radial=new Vec3(1,0,0);
            radial=radial.normalize();
            Vec3 sideways=new Vec3(-radial.z,.2,radial.x).normalize();
            arrow.setDeltaMovement(velocity.scale(.35).add(sideways.scale(Math.min(1.2,velocity.length()*.65))));
            arrow.needsSync=true; arrow.hurtMarked=true;
            this.deflectedProjectiles.add(arrow.getUUID());
        }
    }
    private void tickTempest(ServerLevel level, long now) {
        if (!this.storm.isTempestActive()) { this.tempestPendingPoint=null; this.tempestPendingAt=0; return; }
        if (this.tempestPendingPoint!=null) {
            if (now%5==0) this.storm.strikeWarning(level,this.tempestPendingPoint);
            if (now>=this.tempestPendingAt) {
                Vec3 point=this.tempestPendingPoint; this.tempestPendingPoint=null;
                this.resolveStrike(level,point,StormEndermanConfig.tempestDamage(),true);
            }
        }
        if (now<this.nextTempestSelectionAt || this.tempestPendingPoint!=null) return;
        this.nextTempestSelectionAt=now+StormEndermanConfig.tempestStrikeIntervalTicks();
        Vec3 center=this.storm.getTempestCenter(); double radius=this.storm.getTempestRadius();
        Vec3 point=null; double closest=radius*radius;
        for (ServerPlayer player:level.getEntitiesOfClass(ServerPlayer.class,new AABB(center,center).inflate(radius,4,radius))) {
            double x=player.getX()-center.x,z=player.getZ()-center.z,distance=x*x+z*z;
            if (distance<=closest && this.isVisibleThreat(player) && StormEnderman.exposed(level,player.position())) {
                point=player.position(); closest=distance;
            }
        }
        if (point==null) {
            double angle=level.getRandom().nextDouble()*Math.PI*2, distance=level.getRandom().nextDouble()*radius;
            double x=center.x+Math.cos(angle)*distance,z=center.z+Math.sin(angle)*distance;
            BlockPos column=BlockPos.containing(x,center.y,z);
            if (!level.hasChunkAt(column)) return;
            point=new Vec3(x,level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,column.getX(),column.getZ()),z);
        }
        if (!StormEnderman.exposed(level,point)) return;
        this.tempestPendingPoint=point; this.tempestPendingAt=now+TEMPEST_STRIKE_WARNING;
        this.storm.strikeWarning(level,point);
    }
    private void freezeMovement() {
        this.owner.getNavigation().stop();
        this.owner.getMoveControl().setWantedPosition(this.owner.getX(),this.owner.getY(),this.owner.getZ(),0);
        this.owner.setDeltaMovement(0,this.owner.getDeltaMovement().y,0);
    }
    private static void beginWorldThunder(ServerLevel level) {
        if (level.dimension()!=Level.OVERWORLD || !level.getGameRules().get(GameRules.ADVANCE_WEATHER)) return;
        var weather=level.getWeatherData();
        if (weather.isRaining() && weather.isThundering()) return;
        // Let native WeatherData own expiration and packets. Never schedule an old restore that
        // could overwrite a later /weather command, sleep, or another weather controller.
        int duration=Math.max(120,StormEndermanConfig.tempestDurationTicks());
        weather.setClearWeatherTime(0);
        if (!weather.isRaining()) { weather.setRaining(true); weather.setRainTime(duration); }
        if (!weather.isThundering()) { weather.setThundering(true); weather.setThunderTime(duration); }
    }
    private boolean insideTempest(Vec3 point) {
        Vec3 center=this.storm.getTempestCenter(); double radius=this.storm.getTempestRadius();
        double x=point.x-center.x,z=point.z-center.z;
        return this.storm.isTempestActive() && x*x+z*z<=radius*radius && point.y>=center.y-4 && point.y<=center.y+8;
    }
    public void interruptCast() {
        this.ability=Ability.NONE; this.abilityTarget=null; this.lockedStrikePoint=null; this.abilityEndsAt=0;
        this.storm.setCast(Ability.NONE,0L);
    }
    public void cancelAll() {
        this.interruptCast(); this.storm.endTempest(); this.tempestPendingPoint=null; this.tempestPendingAt=0;
        this.gustUntil=0; this.deflectedProjectiles.clear();
    }
    @Override public void clearEncounter() {
        super.clearEncounter(); this.interruptCast(); this.storm.setHuntState(StormEnderman.HuntState.IDLE);
        // Already summoned weather and gusts remain finite; their clocks never follow hidden targets.
        // Source removal, death, loading, and NoAI call cancelAll explicitly.
    }
    public enum Ability { NONE, THUNDER_STRIKE, STORM_GUST, TEMPEST_SUMMON }
}
