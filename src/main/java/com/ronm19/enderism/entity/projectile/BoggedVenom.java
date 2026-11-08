package com.ronm19.enderism.entity.projectile;

import com.ronm19.enderism.BoggedEndermanConfig;
import com.ronm19.enderism.combat.BoggedPoison;
import com.ronm19.enderism.entity.BoggedEnderman;
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

/** A straight, finite spore globule; native swept collision and native damage decide each impact. */
public final class BoggedVenom extends Projectile {
    private static final DustParticleOptions VENOM = new DustParticleOptions(0x84A82D, .65F);
    private @Nullable UUID intendedTarget;
    private @Nullable WeakReference<LivingEntity> targetReference;
    private @Nullable WeakReference<BoggedEnderman> sourceReference;
    private Vec3 origin = Vec3.ZERO;
    private long expiresAt;
    private int lifetime;
    private double maximumRange;
    private double maximumSpeed;
    private float damage;
    private int poisonTicks;
    private int poisonAmplifier;
    private boolean impacted;

    public BoggedVenom(EntityType<? extends BoggedVenom> type, Level level) {
        super(type, level);
        this.lifetime = BoggedEndermanConfig.projectileLifetimeTicks();
        this.maximumRange = BoggedEndermanConfig.projectileRange();
        this.maximumSpeed = BoggedEndermanConfig.projectileSpeed();
    }

    /** Reserve this shot in its caster before adding it to the world. */
    public static BoggedVenom create(ServerLevel level, BoggedEnderman source, LivingEntity target,
                                     Vec3 muzzle, Vec3 aimPoint) {
        var shot = new BoggedVenom(ModEntities.BOGGED_VENOM.get(), level);
        shot.setOwner(source);
        shot.sourceReference = new WeakReference<>(source);
        shot.intendedTarget = target.getUUID();
        shot.targetReference = new WeakReference<>(target);
        shot.origin = muzzle;
        shot.expiresAt = level.getGameTime() + shot.lifetime;
        shot.damage = (float)BoggedEndermanConfig.projectileDamage();
        shot.poisonTicks = BoggedEndermanConfig.projectilePoisonTicks();
        shot.poisonAmplifier = BoggedEndermanConfig.poisonAmplifier();
        shot.setPos(muzzle);
        Vec3 direction = aimPoint.subtract(muzzle);
        // Native launch dispersion is fixed once; these spores never home or acquire another victim.
        shot.shoot(direction.x, direction.y, direction.z, (float)shot.maximumSpeed, .6F);
        return shot;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {}
    public @Nullable UUID getIntendedTargetUuid() { return this.intendedTarget; }
    public boolean isExpired(long now) { return now >= this.expiresAt || this.tickCount >= this.lifetime; }
    public boolean hasImpacted() { return this.impacted; }

    @Override public void tick() {
        Vec3 movement = this.getDeltaMovement();
        if (this.level() instanceof ServerLevel level) {
            if (finite(movement) && movement.lengthSqr() > this.maximumSpeed * this.maximumSpeed) {
                movement = movement.normalize().scale(this.maximumSpeed);
                this.setDeltaMovement(movement);
            }
            BoggedEnderman source = this.boggedOwner();
            Entity intended = this.intendedTarget == null ? null : level.getEntity(this.intendedTarget);
            if (source == null || !(intended instanceof LivingEntity target) || this.targetReference == null
                    || this.targetReference.get() != target || !source.isLegalVenomTarget(target)
                    || !source.isVenomValid(this) || this.isExpired(level.getGameTime())
                    || !finite(this.position()) || !finite(movement) || movement.lengthSqr() < .000001
                    || this.position().distanceToSqr(this.origin) > this.maximumRange * this.maximumRange
                    || !this.loadedMovement(level, movement)) {
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
                // Cancellation never makes a projectile pass through intervening cover.
                if (!this.isRemoved()) this.discard();
                return;
            }
        } else {
            if (!finite(this.position()) || !finite(movement)) return;
            if ((this.tickCount & 1) == 0)
                this.level().addParticle(VENOM, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
        super.tick();
        if (!this.isRemoved()) {
            this.setPos(this.position().add(movement));
            this.updateRotation();
        }
    }

    private boolean loadedMovement(ServerLevel level, Vec3 movement) {
        AABB swept = this.getBoundingBox().expandTowards(movement).inflate(1.0);
        if (swept.minY < level.getMinY() || swept.maxY >= level.getMaxY()
                || !level.getWorldBorder().isWithinBounds(swept)) return false;
        BlockPos minimum = BlockPos.containing(swept.minX, swept.minY, swept.minZ);
        BlockPos maximum = BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ);
        for (int x = minimum.getX() >> 4; x <= maximum.getX() >> 4; x++)
            for (int z = minimum.getZ() >> 4; z <= maximum.getZ() >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    private HitResult firstImpact(ServerLevel level, Vec3 movement) {
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        Vec3 from = this.position();
        if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(from) <= .000001) return hit;
        float margin = ProjectileUtil.computeMargin(this);
        Entity overlapping = null;
        double nearest = Double.MAX_VALUE;
        // Native growing collision margins can encompass the next tick's start point.
        // Resolve those overlaps without skipping terrain or intervening creatures.
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
        return entity != source && !(source != null && source.isAlliedTo(entity)) && super.canHitEntity(entity);
    }

    @Override protected void onHit(HitResult hit) {
        if (!(this.level() instanceof ServerLevel level) || this.impacted) return;
        this.impacted = true;
        if (hit instanceof EntityHitResult entityHit) this.onHitEntity(entityHit);
        else if (hit instanceof BlockHitResult blockHit) this.onHitBlock(blockHit);
        level.gameEvent(GameEvent.PROJECTILE_LAND, hit.getLocation(), GameEvent.Context.of(this, null));
        level.sendParticles(VENOM, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                8, .18, .18, .18, .02);
        this.discard();
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!(this.level() instanceof ServerLevel level) || !(hit.getEntity() instanceof LivingEntity target)
                || !target.getUUID().equals(this.intendedTarget) || this.targetReference == null
                || this.targetReference.get() != target) return;
        BoggedEnderman source = this.boggedOwner();
        if (source == null || !source.isVenomValid(this) || !source.isLegalVenomTarget(target)) return;
        float before = target.getHealth() + target.getAbsorptionAmount();
        if (!target.hurtServer(level, this.damageSources().mobProjectile(this, source), this.damage)
                || target.getHealth() + target.getAbsorptionAmount() >= before
                || !source.isVenomValid(this)) return;
        BoggedPoison.applyVenom(source, target, this.poisonTicks, this.poisonAmplifier);
    }

    @Override protected void onHitBlock(BlockHitResult hit) {
        BoggedEnderman source = this.boggedOwner();
        if (source != null) source.getMastermindBrain().onVenomBlocked();
        // No block activation, fluids, explosions, growth or terrain changes.
    }

    private @Nullable BoggedEnderman boggedOwner() {
        BoggedEnderman remembered = this.sourceReference == null ? null : this.sourceReference.get();
        return this.getOwner() == remembered && remembered != null && remembered.isAlive() && !remembered.isRemoved()
                && !remembered.isNoAi() && remembered.level() == this.level() ? remembered : null;
    }
    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
    @Override public void onRemoval(Entity.RemovalReason reason) {
        BoggedEnderman source = this.sourceReference == null ? null : this.sourceReference.get();
        if (!this.level().isClientSide() && source != null) source.onVenomRemoved(this);
        this.sourceReference = null; this.targetReference = null;
        super.onRemoval(reason);
    }
    @Override public boolean canUsePortal(boolean ignorePassenger) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.intendedTarget = null; this.targetReference = null; this.sourceReference = null;
    }
}
