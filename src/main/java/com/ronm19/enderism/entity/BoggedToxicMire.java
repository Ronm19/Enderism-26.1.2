package com.ronm19.enderism.entity;

import com.ronm19.enderism.BoggedEndermanConfig;
import com.ronm19.enderism.combat.BoggedPoison;
import com.ronm19.enderism.registry.ModEntities;
import java.lang.ref.WeakReference;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Native cloud lifetime/radius synchronization with species-owned, finite, terrain-safe applications. */
public final class BoggedToxicMire extends AreaEffectCloud {
    private static final DustParticleOptions MIST = new DustParticleOptions(0x77962D, .7F);
    private @Nullable UUID intendedTarget;
    private @Nullable WeakReference<LivingEntity> targetReference;
    private @Nullable WeakReference<BoggedEnderman> sourceReference;
    private long expiresAt;
    private long nextApplication;
    private int interval;
    private int poisonTicks;
    private int amplifier;
    private float directDamage;

    public BoggedToxicMire(EntityType<? extends BoggedToxicMire> type, Level level) { super(type, level); }

    /** The caster must reserve this single zone before adding it to the world. */
    public static BoggedToxicMire create(ServerLevel level, BoggedEnderman source, LivingEntity target, Vec3 center) {
        var mire = new BoggedToxicMire(ModEntities.BOGGED_TOXIC_MIRE.get(), level);
        mire.setOwner(source);
        mire.sourceReference = new WeakReference<>(source);
        mire.intendedTarget = target.getUUID(); mire.targetReference = new WeakReference<>(target);
        mire.setPos(center);
        mire.setRadius((float)BoggedEndermanConfig.mireRadius());
        mire.setDuration(BoggedEndermanConfig.mireLifetimeTicks()); mire.setWaitTime(0);
        mire.setRadiusPerTick(0); mire.setRadiusOnUse(0); mire.setDurationOnUse(0);
        // Native potion contents stay empty: our bounded, faction-filtered pass alone applies Poison.
        mire.setCustomParticle(MIST);
        mire.interval = BoggedEndermanConfig.mireApplicationIntervalTicks();
        mire.poisonTicks = BoggedEndermanConfig.mirePoisonTicks();
        mire.amplifier = BoggedEndermanConfig.mirePoisonAmplifier();
        mire.directDamage = (float)BoggedEndermanConfig.mireDirectDamage();
        mire.expiresAt = level.getGameTime() + mire.getDuration();
        mire.nextApplication = level.getGameTime();
        return mire;
    }

    public @Nullable UUID getTargetUuid() { return this.intendedTarget; }
    public boolean isExpired(long now) { return now >= this.expiresAt || this.tickCount >= this.getDuration(); }

    @Override public void tick() {
        if (!(this.level() instanceof ServerLevel level)) {
            // Keep Entity housekeeping while replacing vanilla's area-proportional cloud density.
            this.baseTick();
            if (this.tickCount % 3 == 0) {
                for (int i = 0; i < 6; i++) {
                    double angle = this.random.nextDouble() * Math.PI * 2;
                    double distance = Math.sqrt(this.random.nextDouble()) * this.getRadius();
                    this.level().addParticle(MIST, this.getX() + Math.cos(angle) * distance,
                            this.getY() + .10 + this.random.nextDouble() * .35,
                            this.getZ() + Math.sin(angle) * distance, 0, .005, 0);
                }
            }
            return;
        }
        BoggedEnderman source = this.boggedOwner();
        Entity intended = this.intendedTarget == null ? null : level.getEntity(this.intendedTarget);
        AABB area = this.getBoundingBox().inflate(0, 1.5, 0);
        if (source == null || !(intended instanceof LivingEntity target) || this.targetReference == null
                || this.targetReference.get() != target || !source.isLegalVenomTarget(target)
                || !source.isMireValid(this) || this.isExpired(level.getGameTime())
                || !finite(this.position()) || !this.loadedArea(level, area)) {
            this.discard(); return;
        }
        super.tick();
        if (this.isRemoved() || level.getGameTime() < this.nextApplication) return;
        this.nextApplication = level.getGameTime() + Math.max(5, this.interval);
        double radiusSquared = this.getRadius() * this.getRadius();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area, source::mayPoisonInMire)) {
            if (!source.isMireValid(this)) break;
            double dx = victim.getX() - this.getX(), dz = victim.getZ() - this.getZ();
            if (dx * dx + dz * dz > radiusSquared || !this.hasClearMistPath(level, victim)) continue;
            if (this.directDamage > 0)
                victim.hurtServer(level, this.damageSources().mobAttack(source), this.directDamage);
            if (this.isRemoved() || !source.isMireValid(this)) break;
            BoggedPoison.applyVenom(source, victim, this.poisonTicks, this.amplifier);
        }
    }

    private boolean hasClearMistPath(ServerLevel level, LivingEntity victim) {
        Vec3 from = this.position().add(0, .35, 0);
        Vec3 to = new Vec3(victim.getX(), victim.getY(.35), victim.getZ());
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }
    private boolean loadedArea(ServerLevel level, AABB area) {
        if (area.minY < level.getMinY() || area.maxY >= level.getMaxY()
                || !level.getWorldBorder().isWithinBounds(area)) return false;
        BlockPos minimum = BlockPos.containing(area.minX, area.minY, area.minZ);
        BlockPos maximum = BlockPos.containing(area.maxX, area.maxY, area.maxZ);
        for (int x = minimum.getX() >> 4; x <= maximum.getX() >> 4; x++)
            for (int z = minimum.getZ() >> 4; z <= maximum.getZ() >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
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
        if (!this.level().isClientSide() && source != null) source.onMireRemoved(this);
        this.targetReference = null; this.sourceReference = null;
        super.onRemoval(reason);
    }
    @Override public boolean canUsePortal(boolean ignorePassenger) { return false; }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.intendedTarget = null; this.sourceReference = null; this.targetReference = null;
    }
}
