package com.ronm19.enderism.entity.projectile;

import com.ronm19.enderism.WitherEndermanConfig;
import com.ronm19.enderism.entity.WitherEnderman;
import com.ronm19.enderism.registry.ModEntities;
import java.lang.ref.WeakReference;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Native Wither skull motion/collision, with finite ownership and a non-griefing combat impact. */
public final class WitherBarrageSkull extends WitherSkull {
    private @Nullable WeakReference<WitherEnderman> sourceReference;
    private @Nullable WeakReference<LivingEntity> targetReference;
    private @Nullable UUID intendedTarget;
    private Vec3 origin = Vec3.ZERO;
    private long expiresAt;
    private final int lifetime;
    private final double maximumRange, maximumSpeed;
    private float damage, explosionRadius, explosionDamage;
    private int witherTicks;
    private boolean impacted;

    public WitherBarrageSkull(EntityType<? extends WitherBarrageSkull> type, Level level) {
        super(type, level); this.accelerationPower = 0;
        this.lifetime = WitherEndermanConfig.projectileLifetimeTicks();
        this.maximumRange = WitherEndermanConfig.projectileRange();
        this.maximumSpeed = WitherEndermanConfig.projectileSpeed();
    }
    /** The caster reserves the returned shot before adding it to the level. */
    public static WitherBarrageSkull create(ServerLevel level, WitherEnderman owner, LivingEntity target, Vec3 muzzle, Vec3 aim) {
        var shot = new WitherBarrageSkull(ModEntities.WITHER_BARRAGE_SKULL.get(), level);
        shot.setOwner(owner); shot.sourceReference = new WeakReference<>(owner); shot.targetReference = new WeakReference<>(target); shot.intendedTarget = target.getUUID();
        shot.origin = muzzle; shot.expiresAt = level.getGameTime() + shot.lifetime; shot.damage = (float)WitherEndermanConfig.projectileDamage();
        shot.witherTicks = WitherEndermanConfig.projectileWitherTicks(); shot.explosionRadius = (float)WitherEndermanConfig.explosionRadius(); shot.explosionDamage = (float)WitherEndermanConfig.explosionDamage();
        shot.setPos(muzzle); Vec3 direction = aim.subtract(muzzle); shot.shoot(direction.x, direction.y, direction.z, (float)shot.maximumSpeed, .65F);
        // Native launch dispersion perturbs vector length as well as direction; honor the speed cap from insertion.
        shot.setDeltaMovement(shot.getDeltaMovement().normalize().scale(shot.maximumSpeed));
        return shot;
    }
    public @Nullable UUID getIntendedTargetUuid() { return this.intendedTarget; }
    public boolean hasImpacted() { return this.impacted; }
    public boolean isExpired(long now) { return now >= this.expiresAt || this.tickCount >= this.lifetime; }
    @Override protected float getInertia() { return 1; }
    @Override protected float getLiquidInertia() { return 1; }
    @Override public void setDangerous(boolean value) { super.setDangerous(false); }
    @Override public boolean canUsePortal(boolean ignorePassenger) { return false; }
    @Override public boolean isPickable() { return false; }

    @Override public void tick() {
        if (!(this.level() instanceof ServerLevel level)) { super.tick(); return; }
        WitherEnderman source = this.witherOwner(); LivingEntity target = this.targetReference == null ? null : this.targetReference.get(); Vec3 movement = this.getDeltaMovement();
        if (source == null || target == null || !source.isLegalWitherTarget(target) || !source.isSkullValid(this)
                || this.isExpired(level.getGameTime()) || !this.position().isFinite() || !movement.isFinite()
                || movement.lengthSqr() < .000001 || this.position().distanceToSqr(this.origin) >= this.maximumRange * this.maximumRange) { this.discard(); return; }
        this.accelerationPower = 0; super.setDangerous(false);
        double remaining = this.maximumRange - this.position().distanceTo(this.origin);
        if (movement.length() > Math.min(this.maximumSpeed, remaining)) { movement = movement.normalize().scale(Math.min(this.maximumSpeed, remaining)); this.setDeltaMovement(movement); }
        if (!this.loadedMovement(level, movement)) { this.discard(); return; }
        // The native impact event still controls damage. A canceled impact resolves the skull instead of permitting wall traversal.
        HitResult predicted = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity, this.getClipType());
        super.tick();
        if (predicted.getType() != HitResult.Type.MISS && !this.isRemoved()) { this.setPos(predicted.getLocation()); this.discard(); }
    }
    private boolean loadedMovement(ServerLevel level, Vec3 movement) {
        AABB swept = this.getBoundingBox().expandTowards(movement).inflate(this.explosionRadius * 2 + 1);
        if (swept.minY < level.getMinY() || swept.maxY >= level.getMaxY() || !level.getWorldBorder().isWithinBounds(swept)) return false;
        for (int x = ((int)Math.floor(swept.minX)) >> 4; x <= ((int)Math.floor(swept.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(swept.minZ)) >> 4; z <= ((int)Math.floor(swept.maxZ)) >> 4; z++) if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    @Override protected boolean canHitEntity(Entity entity) {
        Entity source = this.getOwner(); return entity != source && !(source != null && source.isAlliedTo(entity)) && super.canHitEntity(entity);
    }
    private boolean eligible(WitherEnderman source, Entity entity) {
        return !this.isRemoved() && source.isSkullValid(this) && entity instanceof LivingEntity victim && source.isLegalWitherTarget(victim)
                && (victim instanceof Player || victim.getUUID().equals(this.intendedTarget));
    }
    @Override protected void onHit(HitResult hit) {
        if (!(this.level() instanceof ServerLevel level) || this.impacted) return;
        WitherEnderman source = this.witherOwner(); if (source == null || !source.isSkullValid(this)) { this.discard(); return; }
        this.impacted = true; Entity directVictim = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        if (hit instanceof EntityHitResult entityHit) this.onHitEntity(entityHit);
        // Direct and blast damage have disjoint recipient sets, preserving one damage application per skull impact.
        var calculator = new ExplosionDamageCalculator() {
            @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) { return entity != directVictim && WitherBarrageSkull.this.eligible(source, entity); }
            @Override public float getKnockbackMultiplier(Entity entity) { return entity != directVictim && WitherBarrageSkull.this.eligible(source, entity) ? .5F : 0; }
            @Override public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
                double falloff = Math.max(0, 1 - entity.position().distanceTo(explosion.center()) / (explosion.radius() * 2));
                return (float)(WitherBarrageSkull.this.explosionDamage * falloff * exposure);
            }
        };
        if (!this.isRemoved() && source.isAlive() && !source.isRemoved() && !source.isNoAi() && source.level() == level && source.isSkullValid(this))
            level.explode(this, this.damageSources().explosion(this, source), calculator, hit.getLocation(), this.explosionRadius, false, Level.ExplosionInteraction.NONE);
        this.discard();
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!(this.level() instanceof ServerLevel level) || !(hit.getEntity() instanceof LivingEntity target)) return;
        WitherEnderman source = this.witherOwner(); if (source == null || !source.isSkullValid(this) || !this.eligible(source, target)) return;
        float before = target.getHealth() + target.getAbsorptionAmount(); var damageSource = this.damageSources().witherSkull(this, source);
        if (!target.hurtServer(level, damageSource, this.damage) || target.getHealth() + target.getAbsorptionAmount() >= before || !source.isSkullValid(this)) return;
        if (target.isAlive()) { EnchantmentHelper.doPostAttackEffects(level, target, damageSource); source.applyWitherEffect(target, this.witherTicks); }
    }
    private @Nullable WitherEnderman witherOwner() {
        WitherEnderman source = this.sourceReference == null ? null : this.sourceReference.get();
        return source != null && this.getOwner() == source && source.isAlive() && !source.isRemoved() && !source.isNoAi() && source.level() == this.level() ? source : null;
    }
    @Override public void onRemoval(Entity.RemovalReason reason) {
        WitherEnderman source = this.sourceReference == null ? null : this.sourceReference.get(); if (!this.level().isClientSide() && source != null) source.onSkullRemoved(this);
        this.sourceReference = null; this.targetReference = null; super.onRemoval(reason);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.sourceReference = null; this.targetReference = null; this.intendedTarget = null; this.accelerationPower = 0; super.setDangerous(false);
    }
}
