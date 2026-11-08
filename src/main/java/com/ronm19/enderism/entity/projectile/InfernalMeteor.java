package com.ronm19.enderism.entity.projectile;

import com.ronm19.enderism.InfernalMeteorConfig;
import com.ronm19.enderism.effect.InfernalBurn;
import com.ronm19.enderism.entity.InfernalEnderman;
import com.ronm19.enderism.registry.ModEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

/** A straight, non-griefing falling meteor with one swept collision and one radial impact. */
public final class InfernalMeteor extends Projectile {
    public static final float SMALL_DIAMETER = 0.7F;
    private static final int MAXIMUM_FLIGHT_TICKS = 70;
    private static final EntityDataAccessor<Float> DIAMETER =
            SynchedEntityData.defineId(InfernalMeteor.class, EntityDataSerializers.FLOAT);
    private @Nullable UUID castTarget;
    private long expiresAt;
    private float damage;
    private double radius;
    private boolean impacted;

    public InfernalMeteor(EntityType<? extends InfernalMeteor> type, Level level) {
        super(type, level);
    }

    public static InfernalMeteor create(ServerLevel level, InfernalEnderman caster, UUID target, Vec3 launch,
                                        boolean giant, InfernalMeteorConfig.Settings settings) {
        var meteor = new InfernalMeteor(ModEntities.INFERNAL_METEOR.get(), level);
        meteor.setOwner(caster);
        meteor.castTarget = target;
        meteor.expiresAt = level.getGameTime() + MAXIMUM_FLIGHT_TICKS;
        meteor.damage = (float)(giant ? settings.finalDamage() : settings.smallDamage());
        meteor.radius = giant ? settings.finalRadius() : settings.smallRadius();
        meteor.setPos(launch);
        meteor.entityData.set(DIAMETER, giant ? (float)settings.finalSize() : SMALL_DIAMETER);
        meteor.shoot(0.0, -1.0, 0.0, (float)(giant ? settings.finalSpeed() : settings.smallSpeed()), 0.0F);
        return meteor;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DIAMETER, SMALL_DIAMETER);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DIAMETER.equals(accessor)) {
            this.refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float diameter = this.visualScale();
        return EntityDimensions.fixed(diameter, diameter);
    }

    public float visualScale() {
        return this.entityData.get(DIAMETER);
    }

    public boolean isGiant() {
        return this.visualScale() > SMALL_DIAMETER;
    }

    public boolean hasImpacted() {
        return this.impacted;
    }

    public boolean flightExpired(long now) {
        return now >= this.expiresAt;
    }

    @Override
    public void tick() {
        if (!(this.level() instanceof ServerLevel level)) {
            super.tick();
            this.setPos(this.position().add(this.getDeltaMovement()));
            return;
        }

        var caster = this.getOwner();
        var intended = this.castTarget == null ? null : level.getEntity(this.castTarget);
        var motion = this.getDeltaMovement();
        AABB swept = this.getBoundingBox().expandTowards(motion);
        if (!(caster instanceof InfernalEnderman owner) || !owner.isAlive() || owner.isRemoved() || owner.isNoAi()
                || owner.level() != level || !(intended instanceof LivingEntity target)
                || !owner.canDamageWithInfernalAbility(target) || this.flightExpired(level.getGameTime())
                || !Double.isFinite(motion.y) || motion.y >= 0.0 || motion.x != 0.0 || motion.z != 0.0
                || !loadedArea(level, swept.inflate(1.0, 0.0, 1.0), true)) {
            this.discard();
            return;
        }

        super.tick();
        HitResult hit = this.firstImpact(level, owner, swept, motion.y);
        if (hit == null) {
            this.setPos(this.position().add(motion));
            if ((this.tickCount & 1) == 0) {
                this.sendTrail(level);
            }
            return;
        }

        this.setPos(this.getX(), hit.getLocation().y, this.getZ());
        this.impacted = true;
        if (!EventHooks.onProjectileImpact(this, hit) && !this.isRemoved() && owner.isAlive() && !owner.isRemoved()
                && owner.canDamageWithInfernalAbility(target)) {
            this.impact(level, owner, hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null);
        }
        this.discard();
    }

    private @Nullable HitResult firstImpact(ServerLevel level, InfernalEnderman owner, AABB swept, double step) {
        double fall = step;
        BlockPos block = null;
        var contacts = new BlockCollisions<>(level, this, swept, false,
                (pos, shape) -> new BlockContact(pos.immutable(), shape));
        while (contacts.hasNext()) {
            var contact = contacts.next();
            double candidate = contact.shape().collide(Direction.Axis.Y, this.getBoundingBox(), fall);
            if (candidate > fall) {
                fall = candidate;
                block = contact.pos();
            }
        }

        Vec3 feet = this.position();
        var surface = level.clip(new ClipContext(feet, feet.add(0.0, fall, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (surface.getType() == HitResult.Type.BLOCK) {
            fall = surface.getLocation().y - feet.y;
            block = surface.getBlockPos();
        }

        LivingEntity entity = null;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, swept,
                candidate -> this.canHitEntity(candidate) && owner.canDamageWithInfernalAbility(candidate))) {
            double candidate = Math.min(0.0, victim.getBoundingBox().maxY - feet.y);
            if (candidate > fall) {
                fall = candidate;
                entity = victim;
            }
        }

        Vec3 contact = feet.add(0.0, fall, 0.0);
        if (entity != null) {
            return new EntityHitResult(entity, contact);
        }
        return block == null ? null : new BlockHitResult(contact, Direction.UP, block, false);
    }

    private void impact(ServerLevel level, InfernalEnderman owner, @Nullable Entity directHit) {
        Vec3 point = this.position().add(0.0, 0.08, 0.0);
        var damageSource = this.damageSources().thrown(this, owner);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(point, point).inflate(this.radius), owner::canDamageWithInfernalAbility)) {
            double distance = Math.sqrt(victim.getBoundingBox().distanceToSqr(point));
            if (distance >= this.radius || (victim != directHit && !this.hasBlastExposure(level, point, victim))) {
                continue;
            }
            float damage = this.damage * (float)(1.0 - 0.7 * distance / this.radius);
            if (victim.hurtServer(level, damageSource, damage) && owner.canDamageWithInfernalAbility(victim)) {
                InfernalBurn.apply(victim);
                double power = (this.isGiant() ? 0.85 : 0.35) * (1.0 - distance / this.radius);
                if (victim.getX() != point.x || victim.getZ() != point.z) {
                    victim.knockback(power, point.x - victim.getX(), point.z - victim.getZ());
                }
            }
        }

        boolean giant = this.isGiant();
        double spread = giant ? 1.65 : 0.7;
        level.gameEvent(GameEvent.PROJECTILE_LAND, point, GameEvent.Context.of(this, null));
        level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE,
                giant ? 2.8F : 1.1F, giant ? 0.65F : 1.18F);
        level.sendParticles(giant ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION,
                point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.FLAME, point.x, point.y + 0.3, point.z,
                giant ? 42 : 16, spread, 0.5, spread, giant ? 0.13 : 0.08);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, point.y + 0.4, point.z,
                giant ? 24 : 7, spread, 0.55, spread, 0.055);
    }

    private boolean hasBlastExposure(ServerLevel level, Vec3 point, LivingEntity victim) {
        Vec3 eyes = victim.getEyePosition();
        if (!loadedArea(level, new AABB(point, eyes).inflate(0.01), false)) {
            return false;
        }
        Vec3 body = victim.getBoundingBox().getCenter();
        return this.clearBlastRay(level, point, eyes) || this.clearBlastRay(level, point, body);
    }

    private boolean clearBlastRay(ServerLevel level, Vec3 start, Vec3 end) {
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    private void sendTrail(ServerLevel level) {
        double y = this.getY() + this.visualScale() * 0.7;
        boolean giant = this.isGiant();
        double spread = giant ? 0.65 : 0.17;
        level.sendParticles(ParticleTypes.FLAME, this.getX(), y, this.getZ(), giant ? 7 : 2,
                spread, spread, spread, 0.035);
        level.sendParticles(ParticleTypes.SMOKE, this.getX(), y, this.getZ(), giant ? 4 : 1,
                spread, spread, spread, 0.025);
    }

    public static AABB bodyAt(Vec3 feet, float diameter) {
        return EntityDimensions.fixed(diameter, diameter).makeBoundingBox(feet);
    }

    /** Check resident chunks before tracing blocks, including the swept body or any cover ray. */
    public static boolean loadedArea(ServerLevel level, AABB area, boolean requireEntityTicking) {
        if (area.minY < level.getMinY() || area.maxY >= level.getMaxY()
                || !level.getWorldBorder().isWithinBounds(area)) {
            return false;
        }
        BlockPos min = BlockPos.containing(area.minX, area.minY, area.minZ);
        BlockPos max = BlockPos.containing(area.maxX, area.maxY, area.maxZ);
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null
                        || (requireEntityTicking && !level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(x, z)))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    private record BlockContact(BlockPos pos, VoxelShape shape) {
    }
}
