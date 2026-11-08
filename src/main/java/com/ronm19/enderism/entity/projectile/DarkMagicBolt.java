package com.ronm19.enderism.entity.projectile;

import com.ronm19.enderism.DarkEndermanConfig;
import com.ronm19.enderism.entity.DarkEnderman;
import com.ronm19.enderism.registry.ModEntities;
import java.lang.ref.WeakReference;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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

/** A finite, straight magical shot shared by the two ranged abilities. It never damages terrain. */
public final class DarkMagicBolt extends Projectile {
    public enum Kind { BLINDING, WITHERING }
    public static final double MAX_TRAVEL_DISTANCE = 40.0;
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(DarkMagicBolt.class, EntityDataSerializers.BYTE);
    private static final DustParticleOptions SHADOW = new DustParticleOptions(0x8F28BB, .5F);
    private static final DustParticleOptions WITHERING = new DustParticleOptions(0x3D304C, .6F);
    private @Nullable UUID intendedTargetUuid;
    private @Nullable WeakReference<DarkEnderman> sourceReference;
    private Vec3 origin = Vec3.ZERO;
    private float impactDamage;
    private int blindnessTicks;
    private int maximumLifetime;
    private long expiresAt;
    private boolean impactProcessed;

    public DarkMagicBolt(EntityType<? extends DarkMagicBolt> type, Level level) {
        super(type, level);
        this.maximumLifetime = DarkEndermanConfig.projectileLifetimeTicks();
    }

    /** The owner reserves the shot before adding it to the world, so impacts cannot race its cooldown. */
    public static DarkMagicBolt create(ServerLevel level, DarkEnderman source, LivingEntity target, Kind kind) {
        DarkMagicBolt shot = new DarkMagicBolt(ModEntities.DARK_MAGIC_BOLT.get(), level);
        shot.setOwner(source);
        shot.sourceReference = new WeakReference<>(source);
        shot.intendedTargetUuid = target.getUUID();
        shot.entityData.set(KIND, (byte)kind.ordinal());
        shot.impactDamage = (float)(kind == Kind.BLINDING ? DarkEndermanConfig.projectileDamage() : DarkEndermanConfig.witherProjectileDamage());
        shot.blindnessTicks = DarkEndermanConfig.blindnessDurationTicks();
        shot.expiresAt = level.getGameTime() + shot.maximumLifetime;
        shot.origin = source.getMuzzlePosition();
        shot.setPos(shot.origin);
        Vec3 direction = source.getRangedAimPoint(target).subtract(shot.origin);
        double speed = kind == Kind.BLINDING ? DarkEndermanConfig.projectileSpeed() : DarkEndermanConfig.witherProjectileSpeed();
        shot.shoot(direction.x, direction.y, direction.z, (float)speed, 0.0F);
        return shot;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { data.define(KIND, (byte)0); }
    public Kind getKind() { return this.entityData.get(KIND) == Kind.WITHERING.ordinal() ? Kind.WITHERING : Kind.BLINDING; }
    public @Nullable UUID getIntendedTargetUuid() { return this.intendedTargetUuid; }
    /** Absolute server expiry also frees a reservation when this bolt's chunk stops entity-ticking. */
    public boolean hasExpired(long serverGameTime) { return serverGameTime >= this.expiresAt; }

    @Override public void tick() {
        Vec3 movement = this.getDeltaMovement();
        if (this.level() instanceof ServerLevel level) {
            if (this.hasExpired(level.getGameTime()) || this.tickCount >= this.maximumLifetime
                    || !finite(this.position()) || !finite(movement) || movement.lengthSqr() < .000001
                    || !this.loadedMovement(movement)) {
                this.discard(); return;
            }
            DarkEnderman source = this.darkOwner();
            Entity intended = this.intendedTargetUuid == null ? null : level.getEntity(this.intendedTargetUuid);
            if (source == null || !(intended instanceof LivingEntity living) || !eligible(living)
                    || !source.canAttack(living) || source.isAlliedTo(living) || !source.isBoltValid(this)
                    || this.position().distanceToSqr(this.origin) > MAX_TRAVEL_DISTANCE * MAX_TRAVEL_DISTANCE
                    || this.position().add(movement).distanceToSqr(this.origin) > MAX_TRAVEL_DISTANCE * MAX_TRAVEL_DISTANCE) {
                this.discard(); return;
            }
            // Native swept collision clips entity hits at the first collidable block, including leaves.
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                this.setPos(hit.getLocation());
                if (!EventHooks.onProjectileImpact(this, hit)) this.onHit(hit);
                // An externally cancelled impact still ends this bounded cast; it never phases through cover.
                if (!this.isRemoved()) this.discard();
                return;
            }
        } else {
            // Only the server owns expiration and collision. Spawn velocity/chunks can arrive later on a client.
            if (!finite(this.position()) || !finite(movement)) return;
            if (this.tickCount % 3 == 0) this.level().addParticle(this.getKind() == Kind.BLINDING ? SHADOW : WITHERING,
                    this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
        super.tick();
        if (!this.isRemoved()) {
            this.setPos(this.position().add(movement));
            this.updateRotation();
        }
    }

    /** No collision query or block lookup is allowed to walk into an unloaded future segment. */
    private boolean loadedMovement(Vec3 movement) {
        // ProjectileUtil queries this swept area inflated by one for nearby entities.
        AABB swept = this.getBoundingBox().expandTowards(movement).inflate(1.0);
        BlockPos minimum = BlockPos.containing(swept.minX, swept.minY, swept.minZ);
        BlockPos maximum = BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ);
        return swept.minY >= this.level().getMinY() && swept.maxY < this.level().getMaxY()
                && this.level().getWorldBorder().isWithinBounds(swept) && this.level().hasChunksAt(minimum, maximum);
    }

    @Override protected boolean canHitEntity(Entity entity) {
        Entity source = this.getOwner();
        // Other creatures can block a shot, but can never be damaged as incidental targets.
        return entity != source && !(source != null && source.isAlliedTo(entity)) && super.canHitEntity(entity);
    }

    @Override protected void onHit(HitResult hit) {
        if (!(this.level() instanceof ServerLevel level) || this.impactProcessed) return;
        this.impactProcessed = true;
        if (hit instanceof EntityHitResult entityHit) this.onHitEntity(entityHit);
        else if (hit instanceof BlockHitResult blockHit) this.onHitBlock(blockHit);
        level.gameEvent(GameEvent.PROJECTILE_LAND, hit.getLocation(), GameEvent.Context.of(this, null));
        level.sendParticles(this.getKind() == Kind.BLINDING ? SHADOW : WITHERING,
                hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 5, .12, .12, .12, .015);
        this.discard();
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!(this.level() instanceof ServerLevel level) || !(hit.getEntity() instanceof LivingEntity target)
                || !target.getUUID().equals(this.intendedTargetUuid) || !eligible(target)) return;
        DarkEnderman source = this.darkOwner();
        if (source == null || !source.isBoltValid(this) || !source.canAttack(target) || source.isAlliedTo(target)) return;
        // The native thrown damage type respects shields, armor, cancelled damage and invulnerability frames.
        if (!target.hurtServer(level, this.damageSources().thrown(this, source), this.impactDamage)) return;
        if (!eligible(target) || target.level() != source.level() || !source.isBoltValid(this)) return;
        if (this.getKind() == Kind.BLINDING) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, this.blindnessTicks, 0), source);
        } else {
            // The owning entity alone authorizes and records the fixed 400-tick successful-application deadline.
            source.tryApplyWither(this, target);
        }
    }

    @Override protected void onHitBlock(BlockHitResult hit) {
        // Deliberately do not call BlockState.onProjectileHit: no buttons, fires, explosions, or terrain edits.
    }

    private static boolean eligible(LivingEntity target) {
        return target.isAlive() && !target.isRemoved()
                && !(target instanceof ServerPlayer player && player.hasDisconnected())
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
    }
    private @Nullable DarkEnderman darkOwner() {
        return this.getOwner() instanceof DarkEnderman source && source.isAlive() && !source.isRemoved()
                && !source.isNoAi() && source.level() == this.level() ? source : null;
    }
    private static boolean finite(Vec3 value) { return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z); }

    @Override public void onRemoval(Entity.RemovalReason reason) {
        DarkEnderman source = this.sourceReference == null ? null : this.sourceReference.get();
        if (source == null && this.getOwner() instanceof DarkEnderman owner) source = owner;
        if (!this.level().isClientSide() && source != null) source.onBoltRemoved(this);
        this.sourceReference = null;
        super.onRemoval(reason);
    }
    @Override public boolean canUsePortal(boolean ignorePassenger) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // The registered entity type is noSave. Malformed/manual restored shots fail closed on the next tick.
        this.intendedTargetUuid = null;
    }
}
