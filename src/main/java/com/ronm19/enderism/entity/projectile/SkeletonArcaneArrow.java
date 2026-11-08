package com.ronm19.enderism.entity.projectile;

import com.ronm19.enderism.SkeletonEndermanConfig;
import com.ronm19.enderism.entity.SkeletonEnderman;
import com.ronm19.enderism.registry.ModEntities;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** One native arrow implementation, with one synchronized elemental payload and bounded ownership. */
public final class SkeletonArcaneArrow extends AbstractArrow {
    public enum Element {
        FREEZING(0x8AD7FF), BURNING(0xFF7836), SLOWNESS(0x8E9FAD), POISON(0x81A735), WITHER(0x53445C);
        private final int color;
        Element(int color) { this.color = color; }
        public int color() { return this.color; }
    }
    private static final EntityDataAccessor<Byte> ELEMENT = SynchedEntityData.defineId(SkeletonArcaneArrow.class, EntityDataSerializers.BYTE);
    private static final Element[] ELEMENTS = Element.values();
    private @Nullable UUID intendedTarget;
    private @Nullable WeakReference<LivingEntity> targetReference;
    private @Nullable WeakReference<SkeletonEnderman> sourceReference;
    private long expiresAt;
    private int lifetime;
    private double maximumRange;
    private double maximumSpeed;
    private double travelled;
    private int frozenTicks, slowTicks, slowAmplifier, poisonTicks, poisonAmplifier, witherTicks, witherAmplifier;
    private float burnSeconds;
    private boolean impacted, elementApplied, entityCollision;
    private @Nullable LivingEntity impactTarget;
    private float healthBeforeImpact;

    public SkeletonArcaneArrow(EntityType<? extends SkeletonArcaneArrow> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
        this.lifetime = SkeletonEndermanConfig.arrowLifetimeTicks();
        this.maximumRange = SkeletonEndermanConfig.arrowRange();
        this.maximumSpeed = SkeletonEndermanConfig.arrowSpeed();
    }

    /** Reserve the generated arrow in its caster before adding it to the world. No item/ammunition is consumed. */
    public static SkeletonArcaneArrow create(ServerLevel level, SkeletonEnderman source, LivingEntity target,
                                            Vec3 muzzle, Vec3 aim, Element element, double rawDamage, double speed) {
        var arrow = new SkeletonArcaneArrow(ModEntities.SKELETON_ARCANE_ARROW.get(), level);
        arrow.setOwner(source); arrow.sourceReference = new WeakReference<>(source);
        arrow.intendedTarget = target.getUUID(); arrow.targetReference = new WeakReference<>(target);
        arrow.entityData.set(ELEMENT, (byte)element.ordinal());
        arrow.maximumSpeed = Math.clamp(speed, .1, 4);
        arrow.expiresAt = level.getGameTime() + arrow.lifetime;
        arrow.frozenTicks = SkeletonEndermanConfig.freezeTicks(); arrow.burnSeconds = (float)SkeletonEndermanConfig.burnSeconds();
        arrow.slowTicks = SkeletonEndermanConfig.slowTicks(); arrow.slowAmplifier = SkeletonEndermanConfig.slowAmplifier();
        arrow.poisonTicks = SkeletonEndermanConfig.poisonTicks(); arrow.poisonAmplifier = SkeletonEndermanConfig.poisonAmplifier();
        arrow.witherTicks = SkeletonEndermanConfig.witherTicks(); arrow.witherAmplifier = SkeletonEndermanConfig.witherAmplifier();
        arrow.setPos(muzzle);
        Vec3 direction = ballisticDirection(aim.subtract(muzzle), arrow.maximumSpeed, arrow.getGravity(), arrow.lifetime);
        arrow.shoot(direction.x, direction.y, direction.z, (float)arrow.maximumSpeed, (float)SkeletonEndermanConfig.arrowInaccuracy());
        // Native arrows calculate ceil(speed * baseDamage). Normalize the launch amount, then retain native drag/gravity.
        arrow.setBaseDamage(Math.clamp(rawDamage, 0, 24) / Math.max(.1, arrow.getDeltaMovement().length()));
        arrow.setCritArrow(false); arrow.pickup = Pickup.DISALLOWED;
        return arrow;
    }

    /** Bounded launch-only compensation for native .99 drag/gravity; aim never changes after launch. */
    private static Vec3 ballisticDirection(Vec3 difference, double speed, double gravity, int lifetime) {
        double horizontal = difference.horizontalDistance();
        if (horizontal < 1 || gravity <= 0) return difference;
        double low = Math.max(1, horizontal / speed), high = low;
        double limit = Math.min(60, lifetime);
        for (int i = 0; i < 24 && high <= limit; i++, high += 2) {
            if (nativeLaunchVector(difference, high, gravity).lengthSqr() <= speed * speed) {
                for (int step = 0; step < 10; step++) {
                    double middle = (low + high) / 2;
                    if (nativeLaunchVector(difference, middle, gravity).lengthSqr() > speed * speed) low = middle;
                    else high = middle;
                }
                return nativeLaunchVector(difference, high, gravity);
            }
            low = high;
        }
        // A target outside this physical launch envelope still receives an ordinary, fallible shot.
        return difference.add(0, Math.min(8, horizontal * .2), 0);
    }
    private static Vec3 nativeLaunchVector(Vec3 difference, double ticks, double gravity) {
        double inertiaSum = (1 - Math.pow(.99, ticks)) / .01;
        double drop = gravity * (ticks - inertiaSum) / .01;
        return new Vec3(difference.x / inertiaSum, (difference.y + drop) / inertiaSum, difference.z / inertiaSum);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { super.defineSynchedData(data); data.define(ELEMENT, (byte)0); }
    public Element getElement() { int value = this.entityData.get(ELEMENT); return value >= 0 && value < ELEMENTS.length ? ELEMENTS[value] : Element.FREEZING; }
    public @Nullable UUID getIntendedTargetUuid() { return this.intendedTarget; }
    public boolean isExpired(long now) { return now >= this.expiresAt || this.tickCount >= this.lifetime || this.travelled >= this.maximumRange; }
    public boolean hasImpacted() { return this.impacted; }

    @Override public void tick() {
        if (!(this.level() instanceof ServerLevel level)) {
            super.tick();
            if (!this.isRemoved() && !this.isInGround() && (this.tickCount & 1) == 0)
                this.level().addParticle(new DustParticleOptions(this.getElement().color(), .45F), this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            return;
        }
        Vec3 movement = this.getDeltaMovement();
        if (finite(movement) && movement.lengthSqr() > this.maximumSpeed * this.maximumSpeed) {
            movement = movement.normalize().scale(this.maximumSpeed); this.setDeltaMovement(movement);
        }
        SkeletonEnderman source = this.skeletonOwner();
        Entity intended = this.intendedTarget == null ? null : level.getEntity(this.intendedTarget);
        if (source == null || !(intended instanceof LivingEntity target) || this.targetReference == null || this.targetReference.get() != target
                || !source.isLegalArrowTarget(target) || !source.isArrowValid(this) || this.isExpired(level.getGameTime())
                || this.isInGround() || !finite(this.position()) || !finite(movement) || movement.lengthSqr() < .000001) {
            this.discard(); return;
        }
        double remaining = this.maximumRange - this.travelled;
        if (movement.lengthSqr() > remaining * remaining) { movement = movement.normalize().scale(remaining); this.setDeltaMovement(movement); }
        if (!this.loadedMovement(level, movement)) { this.discard(); return; }
        Vec3 previous = this.position();
        // Remember native cover collision so a canceled impact cannot make magic pass through that cover next tick.
        boolean blockCollision = level.clipIncludingBorder(new ClipContext(previous, previous.add(movement), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS;
        this.entityCollision = false; this.clearFire();
        super.tick();
        this.travelled += previous.distanceTo(this.position());
        if (!this.isRemoved() && (blockCollision || this.entityCollision || this.isInGround() || this.isExpired(level.getGameTime()))) this.discard();
    }

    @Override protected Collection<EntityHitResult> findHitEntities(Vec3 from, Vec3 to) {
        var hits = super.findHitEntities(from, to); this.entityCollision |= !hits.isEmpty(); return hits;
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!(this.level() instanceof ServerLevel) || this.impacted) return;
        this.impacted = true;
        if (!(hit.getEntity() instanceof LivingEntity target) || !target.getUUID().equals(this.intendedTarget)
                || this.targetReference == null || this.targetReference.get() != target) { this.discard(); return; }
        SkeletonEnderman source = this.skeletonOwner();
        if (source == null || !source.isArrowValid(this) || !source.isLegalArrowTarget(target)) { this.discard(); return; }
        this.impactTarget = target; this.healthBeforeImpact = target.getHealth() + target.getAbsorptionAmount();
        // Native on-fire arrows ignite before damage; this arrow's Burning payload only ignites after a qualifying hit.
        this.clearFire();
        try { super.onHitEntity(hit); }
        finally { this.impactTarget = null; if (!this.isRemoved()) this.discard(); }
    }
    @Override protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        SkeletonEnderman source = this.skeletonOwner();
        if (this.elementApplied || this.impactTarget != target || source == null || !source.isArrowValid(this)
                || !source.isLegalArrowTarget(target) || target.getHealth() + target.getAbsorptionAmount() >= this.healthBeforeImpact) return;
        this.elementApplied = true;
        switch (this.getElement()) {
            case FREEZING -> { if (target.canFreeze()) target.setTicksFrozen(Math.max(target.getTicksFrozen(), Math.clamp(this.frozenTicks, 0, target.getTicksRequiredToFreeze() + 100))); }
            case BURNING -> { if (!target.fireImmune() && !target.hasEffect(MobEffects.FIRE_RESISTANCE)) target.igniteForSeconds(Math.clamp(this.burnSeconds, 0, 10)); }
            case SLOWNESS -> applyEffect(target, MobEffects.SLOWNESS, this.slowTicks, this.slowAmplifier, source);
            case POISON -> applyEffect(target, MobEffects.POISON, this.poisonTicks, this.poisonAmplifier, source);
            case WITHER -> applyEffect(target, MobEffects.WITHER, this.witherTicks, this.witherAmplifier, source);
        }
        if (this.level() instanceof ServerLevel level)
            level.sendParticles(new DustParticleOptions(this.getElement().color(), .65F), target.getX(), target.getY(.5), target.getZ(), 7, .2, .25, .2, .02);
    }
    private static void applyEffect(LivingEntity target, Holder<MobEffect> effect, int duration, int amplifier, SkeletonEnderman source) {
        int ticks = Math.clamp(duration, 0, 400), strength = Math.clamp(amplifier, 0, 2);
        if (ticks == 0) return;
        var current = target.getEffect(effect);
        if (current != null && (current.getAmplifier() > strength || current.getAmplifier() == strength && current.getDuration() >= ticks)) return;
        target.addEffect(new MobEffectInstance(effect, ticks, strength), source);
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        if (!(this.level() instanceof ServerLevel) || this.impacted) return;
        this.impacted = true;
        SkeletonEnderman source = this.skeletonOwner(); if (source != null) source.getMastermindBrain().onArrowBlocked();
        // No fire placement, explosions, ammunition drops, or enduring embedded arrows.
        this.discard();
    }
    private boolean loadedMovement(ServerLevel level, Vec3 movement) {
        AABB area = this.getBoundingBox().expandTowards(movement).inflate(1);
        if (area.minY < level.getMinY() || area.maxY >= level.getMaxY() || !level.getWorldBorder().isWithinBounds(area)) return false;
        BlockPos low = BlockPos.containing(area.minX, area.minY, area.minZ), high = BlockPos.containing(area.maxX, area.maxY, area.maxZ);
        for (int x = low.getX() >> 4; x <= high.getX() >> 4; x++) for (int z = low.getZ() >> 4; z <= high.getZ() >> 4; z++)
            if (level.getChunkSource().getChunkNow(x,z) == null) return false;
        return true;
    }
    private @Nullable SkeletonEnderman skeletonOwner() {
        SkeletonEnderman remembered = this.sourceReference == null ? null : this.sourceReference.get();
        return remembered != null && this.getOwner() == remembered && remembered.isAlive() && !remembered.isRemoved()
                && !remembered.isNoAi() && remembered.level() == this.level() ? remembered : null;
    }
    private static boolean finite(Vec3 vector) { return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z); }
    @Override protected ItemStack getDefaultPickupItem() { return new ItemStack(Items.ARROW); }
    @Override public boolean canUsePortal(boolean ignorePassenger) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public void onRemoval(Entity.RemovalReason reason) {
        SkeletonEnderman source = this.sourceReference == null ? null : this.sourceReference.get();
        if (!this.level().isClientSide() && source != null) source.onArrowRemoved(this);
        this.sourceReference = null; this.targetReference = null; this.impactTarget = null;
        super.onRemoval(reason);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.intendedTarget = null; this.sourceReference = null; this.targetReference = null;
    }
}
