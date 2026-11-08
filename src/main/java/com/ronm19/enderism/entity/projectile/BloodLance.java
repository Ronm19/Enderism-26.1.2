package com.ronm19.enderism.entity.projectile;

import com.ronm19.enderism.BloodEndermanConfig;
import com.ronm19.enderism.entity.BloodEnderman;
import com.ronm19.enderism.registry.ModEntities;
import java.lang.ref.WeakReference;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

/** A finite, straight crimson lance using native swept collision and attributed projectile damage. */
public final class BloodLance extends Projectile {
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(0xC51C37, .65F);
    private static final DustParticleOptions CORE = new DustParticleOptions(0xFF6474, .45F);
    private @Nullable UUID intendedTarget;
    private @Nullable WeakReference<LivingEntity> targetReference;
    private @Nullable WeakReference<BloodEnderman> sourceReference;
    private Vec3 origin = Vec3.ZERO;
    private long expiresAt;
    private int lifetime;
    private double maximumRange;
    private float damage;
    private boolean impacted;

    public BloodLance(EntityType<? extends BloodLance> type, Level level) {
        super(type, level);
        this.lifetime = BloodEndermanConfig.lanceLifetime();
        this.maximumRange = BloodEndermanConfig.lanceRange();
    }

    /** The caster registers ownership before adding this shot, reserving its bounded cast slot. */
    public static BloodLance create(ServerLevel level, BloodEnderman source, LivingEntity target,
                                    Vec3 muzzle, Vec3 aimPoint) {
        var shot = new BloodLance(ModEntities.BLOOD_LANCE.get(), level);
        shot.setOwner(source);
        shot.sourceReference = new WeakReference<>(source);
        shot.intendedTarget = target.getUUID();
        shot.targetReference = new WeakReference<>(target);
        shot.origin = muzzle;
        shot.expiresAt = level.getGameTime() + shot.lifetime;
        shot.damage = (float)BloodEndermanConfig.lanceDamage();
        shot.setPos(muzzle);
        Vec3 direction = aimPoint.subtract(muzzle);
        // Small native dispersion is committed at launch; there is no later homing or retargeting.
        shot.shoot(direction.x, direction.y, direction.z, (float)BloodEndermanConfig.lanceSpeed(), .45F);
        return shot;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {}
    public @Nullable UUID getIntendedTargetUuid() { return this.intendedTarget; }
    public boolean flightExpired(long now) { return now >= this.expiresAt; }
    public boolean hasImpacted() { return this.impacted; }

    @Override public void tick() {
        Vec3 movement = this.getDeltaMovement();
        if (this.level() instanceof ServerLevel level) {
            BloodEnderman source = this.bloodOwner();
            Entity intended = this.intendedTarget == null ? null : level.getEntity(this.intendedTarget);
            if (source == null || !(intended instanceof LivingEntity target) || this.targetReference == null
                    || this.targetReference.get() != target || !source.isLegalRangedTarget(target)
                    || !source.isLanceValid(this) || this.flightExpired(level.getGameTime()) || this.tickCount >= this.lifetime
                    || !finite(this.position()) || !finite(movement) || movement.lengthSqr() < .000001
                    || this.position().distanceToSqr(this.origin) > this.maximumRange * this.maximumRange
                    || !this.loadedMovement(movement)) {
                this.discard(); return;
            }
            double remaining = this.maximumRange - this.position().distanceTo(this.origin);
            if (remaining <= .000001) { this.discard(); return; }
            if (movement.lengthSqr() > remaining * remaining) {
                movement = movement.normalize().scale(remaining);
                this.setDeltaMovement(movement);
            }
            HitResult hit = this.firstImpact(level, movement);
            if (hit.getType() != HitResult.Type.MISS) {
                this.setPos(hit.getLocation());
                if (!EventHooks.onProjectileImpact(this, hit)) this.onHit(hit);
                // Cancelled impacts still end this cast; it never phases through intervening cover.
                if (!this.isRemoved()) this.discard();
                return;
            }
        } else {
            if (!finite(this.position()) || !finite(movement)) return;
            if ((this.tickCount & 1) == 0) {
                this.level().addParticle(CRIMSON, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            }
        }
        super.tick();
        if (!this.isRemoved()) {
            this.setPos(this.position().add(movement));
            this.updateRotation();
        }
    }

    private boolean loadedMovement(Vec3 movement) {
        // ProjectileUtil queries this inflated swept box; validate it before collision block lookups.
        AABB swept = this.getBoundingBox().expandTowards(movement).inflate(1.0);
        BlockPos minimum = BlockPos.containing(swept.minX, swept.minY, swept.minZ);
        BlockPos maximum = BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ);
        return swept.minY >= this.level().getMinY() && swept.maxY < this.level().getMaxY()
                && this.level().getWorldBorder().isWithinBounds(swept) && this.level().hasChunksAt(minimum, maximum);
    }

    private HitResult firstImpact(ServerLevel level, Vec3 movement) {
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        Vec3 from = this.position();
        // A block at the starting point retains precedence over an overlapping creature.
        if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(from) <= .000001) return hit;
        float margin = ProjectileUtil.computeMargin(this);
        Entity overlapping = null;
        double nearest = Double.MAX_VALUE;
        // Native computeMargin grows with age. Its entry-only box clip can miss a creature
        // when that growth puts the next tick's start inside its box. Resolve that zero-distance
        // collision explicitly, including intervening creatures rather than forcing a target hit.
        for (Entity candidate : level.getEntities(this,
                this.getBoundingBox().expandTowards(movement).inflate(1.0), this::canHitEntity)) {
            if (!candidate.getBoundingBox().inflate(margin).contains(from)) continue;
            double distance = candidate.getBoundingBox().getCenter().distanceToSqr(from);
            if (overlapping == null || distance < nearest
                    || distance == nearest && candidate.getId() < overlapping.getId()) {
                overlapping = candidate; nearest = distance;
            }
        }
        return overlapping == null ? hit : new EntityHitResult(overlapping, from);
    }

    @Override protected boolean canHitEntity(Entity entity) {
        Entity source = this.getOwner();
        // Intervening creatures physically stop the lance; they do not become incidental victims.
        return entity != source && !(source != null && source.isAlliedTo(entity)) && super.canHitEntity(entity);
    }

    @Override protected void onHit(HitResult hit) {
        if (!(this.level() instanceof ServerLevel level) || this.impacted) return;
        this.impacted = true;
        if (hit instanceof EntityHitResult entityHit) this.onHitEntity(entityHit);
        else if (hit instanceof BlockHitResult blockHit) this.onHitBlock(blockHit);
        level.gameEvent(GameEvent.PROJECTILE_LAND, hit.getLocation(), GameEvent.Context.of(this, null));
        level.sendParticles(CRIMSON, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                7, .16, .16, .16, .03);
        level.sendParticles(CORE, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                3, .09, .09, .09, .015);
        this.discard();
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!(this.level() instanceof ServerLevel level) || !(hit.getEntity() instanceof LivingEntity target)
                || !target.getUUID().equals(this.intendedTarget) || this.targetReference == null
                || this.targetReference.get() != target) return;
        BloodEnderman source = this.bloodOwner();
        if (source == null || !source.isLanceValid(this) || !source.isLegalRangedTarget(target)) return;
        // The native projectile source retains its shooter through both damage and kill attribution.
        // Armor, shields, cancelled damage and native invulnerability frames remain authoritative.
        target.hurtServer(level, this.damageSources().mobProjectile(this, source), this.damage);
    }

    @Override protected void onHitBlock(BlockHitResult hit) {
        BloodEnderman source = this.bloodOwner();
        if (source != null) source.getMastermindBrain().onLanceBlocked();
        // No block activation, fire placement, explosions, or other terrain updates.
    }

    private @Nullable BloodEnderman bloodOwner() {
        return this.getOwner() instanceof BloodEnderman source && source.isAlive() && !source.isRemoved()
                && !source.isNoAi() && source.level() == this.level() ? source : null;
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    @Override public void onRemoval(Entity.RemovalReason reason) {
        BloodEnderman source = this.sourceReference == null ? null : this.sourceReference.get();
        if (source == null && this.getOwner() instanceof BloodEnderman owner) source = owner;
        if (!this.level().isClientSide() && source != null) source.onLanceRemoved(this);
        this.sourceReference = null;
        this.targetReference = null;
        super.onRemoval(reason);
    }
    @Override public boolean canUsePortal(boolean ignorePassenger) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // noSave entities cannot resume old casts; malformed manual restoration fails closed.
        this.intendedTarget = null;
        this.targetReference = null;
    }
}
