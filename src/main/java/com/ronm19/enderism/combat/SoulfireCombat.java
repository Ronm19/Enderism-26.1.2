package com.ronm19.enderism.combat;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.SoulSandEndermanConfig;
import com.ronm19.enderism.entity.SoulSandEnderman;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/** One finite blue burn model and fixed, non-destructive ground hazards for the three Soul Sand abilities. */
public final class SoulfireCombat {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Enderism.MOD_ID);
    // No serializer, codec, copy-on-death, or network payload: this is strictly temporary server state.
    private static final Supplier<AttachmentType<Pressure>> PRESSURE = ATTACHMENTS.register("soulfire_pressure", () -> AttachmentType.builder(Pressure::empty).build());
    private final SoulSandEnderman owner;
    private final Map<UUID, WeakReference<LivingEntity>> ignited = new LinkedHashMap<>();
    private List<Vec3> patches = List.of();
    private @Nullable Vec3 dominionCenter;
    private long dominionEndsAt, nextHazardAt, nextVisualAt, epoch;
    private boolean applyingArea;

    public SoulfireCombat(SoulSandEnderman owner) { this.owner = owner; }
    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus); NeoForge.EVENT_BUS.addListener(SoulfireCombat::tickRecipient); NeoForge.EVENT_BUS.addListener(SoulfireCombat::leaveLevel); NeoForge.EVENT_BUS.addListener(SoulfireCombat::acceptedFireDamage);
    }
    public boolean isDominionActive() { return this.dominionCenter != null && this.owner.level().getGameTime() < this.dominionEndsAt; }
    public long getDominionEndsAt() { return this.isDominionActive() ? this.dominionEndsAt : 0; }
    public @Nullable Vec3 getDominionCenter() { return this.dominionCenter; }
    public List<Vec3> getPatches() { return this.patches; }
    public int getTrackedBurnCount() { this.pruneBurns(); return this.ignited.size(); }
    public static boolean hasSoulfireBurn(LivingEntity target) { Pressure state = target.getExistingDataOrNull(PRESSURE); return state != null && state.owner() != null && target.level().getGameTime() < state.until(); }
    public static long getBurnEndsAt(LivingEntity target) { Pressure state = target.getExistingDataOrNull(PRESSURE); return state == null ? 0 : state.until(); }
    public static @Nullable UUID getBurnOwner(LivingEntity target) { Pressure state = target.getExistingDataOrNull(PRESSURE); return state == null ? null : state.owner(); }

    public boolean ignite(ServerLevel level, LivingEntity target, long now) {
        if (!this.owner.getMastermindBrain().isStrikeImpactFrame(target)) return false;
        return this.applyBurn(level, target, now, SoulSandEndermanConfig.strikeBurnTicks());
    }
    private boolean applyBurn(ServerLevel level, LivingEntity target, long now, int duration) {
        if (!this.ready(level) || !this.owner.isLegalSoulTarget(target) || !mayBurn(target)) return false;
        int ticks = (int)Math.ceil(duration * Math.clamp(target.getAttributeValue(Attributes.BURNING_TIME), 0, 1));
        if (ticks <= 0) return false;
        Pressure existing = target.getExistingDataOrNull(PRESSURE);
        long gate = existing == null ? 0 : existing.nextAreaAt();
        SoulSandEnderman current = existing == null ? null : source(level, target, existing);
        // One recipient has at most one periodic soul-burn owner. Other casters cannot multiply it.
        if (current != null && current != this.owner) return false;
        this.pruneBurns(); if (this.ignited.size() >= 64 && !this.ignited.containsKey(target.getUUID())) return false;
        long nextPulse = current == this.owner ? existing.nextPulse() : now + SoulSandEndermanConfig.burnIntervalTicks();
        target.setData(PRESSURE, new Pressure(this.owner.getUUID(), level.dimension(), now + ticks, nextPulse, gate));
        this.ignited.put(target.getUUID(), new WeakReference<>(target));
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY(.55), target.getZ(), 4, .18, .25, .18, .012);
        return true;
    }
    public boolean canManifest(ServerLevel level, Vec3 anchor, boolean dominion) {
        double range = dominion ? SoulSandEndermanConfig.dominionCastRange() : SoulSandEndermanConfig.eruptionCastRange();
        return this.ready(level) && anchor.isFinite() && this.owner.getMastermindBrain().isAbilityAnchorValid(anchor)
                && this.owner.position().distanceToSqr(anchor) <= range * range && surface(level, anchor) != null
                && line(level, this.owner.getEyePosition(), anchor.add(0, .6, 0));
    }
    public int erupt(ServerLevel level, Vec3 anchor, long now) {
        if (this.applyingArea || !this.owner.getMastermindBrain().isEruptionReleaseFrame() || !this.canManifest(level, anchor, false)) return 0;
        var flames = this.sample(level, anchor, SoulSandEndermanConfig.eruptionRadius(), Math.min(12, SoulSandEndermanConfig.dominionHazards()));
        if (flames.isEmpty()) return 0;
        for (Vec3 point : flames) level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y + .25, point.z, 5, .22, .5, .22, .022);
        this.owner.playSound(SoundEvents.FIRECHARGE_USE, .8F, .75F);
        int affected = 0; double radius = SoulSandEndermanConfig.eruptionRadius(); long transaction = this.epoch;
        this.applyingArea = true;
        try {
            for (LivingEntity target : this.enemies(level, anchor, radius)) {
                if (!this.ready(level) || this.epoch != transaction || !this.owner.getMastermindBrain().isEruptionReleaseFrame()) break;
                if (target.position().subtract(anchor).horizontalDistanceSqr() > radius * radius || Math.abs(target.getY() - anchor.y) > 2
                        || surface(level, target.position()) == null || !line(level, anchor.add(0, .6, 0), target.getBoundingBox().getCenter())
                        || !line(level, this.owner.getEyePosition(), target.getEyePosition())) continue;
                if (this.areaHit(level, target, now, (float)SoulSandEndermanConfig.eruptionDamage(), transaction)) {
                    affected++; this.applyBurn(level, target, now, SoulSandEndermanConfig.eruptionBurnTicks());
                    Vec3 outward = target.position().subtract(anchor).multiply(1, 0, 1).normalize();
                    target.knockback(SoulSandEndermanConfig.eruptionKnockback(), -outward.x, -outward.z);
                }
            }
        } finally { this.applyingArea = false; }
        return affected;
    }
    public boolean beginDominion(ServerLevel level, Vec3 anchor, long now) {
        if (this.applyingArea || !this.owner.getMastermindBrain().isDominionReleaseFrame() || this.isDominionActive() || !this.canManifest(level, anchor, true)) return false;
        List<Vec3> field = this.sample(level, anchor, SoulSandEndermanConfig.dominionRadius(), SoulSandEndermanConfig.dominionHazards());
        if (field.isEmpty()) return false;
        this.epoch++; this.patches = List.copyOf(field); this.dominionCenter = anchor; this.dominionEndsAt = now + SoulSandEndermanConfig.dominionDurationTicks(); this.nextHazardAt = now; this.nextVisualAt = now;
        this.owner.playSound(SoundEvents.FIRECHARGE_USE, .9F, .55F); return true;
    }
    public void tick(ServerLevel level, long now) {
        if (this.applyingArea) return;
        this.pruneBurns();
        if (!this.ready(level)) { this.clear(); return; }
        if (!this.isDominionActive()) { this.clearDominion(); return; }
        Vec3 center = this.dominionCenter;
        if (center == null) return;
        if (now >= this.nextVisualAt) {
            this.nextVisualAt = now + 10;
            for (Vec3 point : this.patches) if (patchValid(level, point)) level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y + .12, point.z, 2, .22, .2, .22, .012);
        }
        if (now < this.nextHazardAt) return; this.nextHazardAt = now + SoulSandEndermanConfig.dominionDamageIntervalTicks();
        this.applyingArea = true; long transaction = this.epoch;
        try {
            for (LivingEntity target : this.enemies(level, center, SoulSandEndermanConfig.dominionRadius())) {
                if (!this.ready(level) || this.epoch != transaction || !this.isDominionActive()) break;
                for (Vec3 point : this.patches) if (patchValid(level, point) && volume(point).intersects(target.getBoundingBox())
                        && line(level, point.add(0, .5, 0), target.getBoundingBox().getCenter())) {
                    if (this.areaHit(level, target, now, (float)SoulSandEndermanConfig.dominionDamage(), transaction)) this.applyBurn(level, target, now, SoulSandEndermanConfig.dominionBurnTicks());
                    break;
                }
            }
        } finally { this.applyingArea = false; }
    }
    private boolean areaHit(ServerLevel level, LivingEntity target, long now, float damage, long transaction) {
        if (!this.owner.isLegalSoulTarget(target) || !mayBurn(target) || !Float.isFinite(damage) || damage <= 0) return false;
        Pressure existing = target.getExistingDataOrNull(PRESSURE);
        if (existing != null && now < existing.nextAreaAt()) return false;
        Pressure state = existing == null ? Pressure.empty() : existing;
        target.setData(PRESSURE, new Pressure(state.owner(), state.dimension(), state.until(), state.nextPulse(), now + SoulSandEndermanConfig.dominionDamageIntervalTicks()));
        float health = target.getHealth(); SoulfireDamage source = fire(level, this.owner, false); source.victim = target;
        boolean accepted = target.hurtServer(level, source, damage);
        return accepted && source.healthDamage > 0 && target.getHealth() < health && this.ready(level) && this.epoch == transaction && this.owner.isLegalSoulTarget(target);
    }
    private List<LivingEntity> enemies(ServerLevel level, Vec3 anchor, double radius) {
        var result = level.getEntitiesOfClass(LivingEntity.class, new AABB(anchor, anchor).inflate(radius, 2, radius), target -> this.owner.isLegalSoulTarget(target)
                && (target instanceof Player || target instanceof Monster || target == this.owner.getTarget() || target == this.owner.getLastHurtByMob()));
        result.sort(Comparator.comparingDouble(target -> target.position().distanceToSqr(anchor)));
        return result.size() <= SoulSandEndermanConfig.maximumAffectedTargets() ? result : result.subList(0, SoulSandEndermanConfig.maximumAffectedTargets());
    }
    private List<Vec3> sample(ServerLevel level, Vec3 anchor, double radius, int maximum) {
        var result = new ArrayList<Vec3>(); var occupied = new java.util.HashSet<BlockPos>();
        for (int index = 0; index < SoulSandEndermanConfig.dominionSampleBudget() && result.size() < maximum; index++) {
            double angle = index == 0 ? 0 : (index - 1) * Math.PI / 4 + ((index - 1) / 8) * Math.PI / 8;
            double distance = index == 0 ? 0 : radius * (((index - 1) / 8) % 2 == 0 ? .8 : .45);
            Vec3 requested = anchor.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance); Vec3 point = surface(level, requested);
            if (point != null && point.subtract(anchor).horizontalDistanceSqr() <= radius * radius && occupied.add(BlockPos.containing(point.add(0, .2, 0)))
                    && line(level, this.owner.getEyePosition(), point.add(0, .6, 0))) result.add(point);
        }
        return result;
    }
    private boolean ready(ServerLevel level) { return this.owner.level() == level && this.owner.isAlive() && !this.owner.isRemoved() && !this.owner.isNoAi(); }
    private void pruneBurns() { this.ignited.entrySet().removeIf(entry -> { LivingEntity target = entry.getValue().get(); return target == null || !this.owner.getUUID().equals(getBurnOwner(target)) || !hasSoulfireBurn(target); }); }
    public void clear() {
        this.epoch++;
        this.clearDominion();
        for (var reference : this.ignited.values()) { LivingEntity target = reference.get(); if (target != null && this.owner.getUUID().equals(getBurnOwner(target))) clearBurn(target); }
        this.ignited.clear();
    }
    private void clearDominion() { if (this.dominionCenter != null || !this.patches.isEmpty()) this.epoch++; this.patches = List.of(); this.dominionCenter = null; this.dominionEndsAt = this.nextHazardAt = this.nextVisualAt = 0; }
    private static void tickRecipient(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel level)) return;
        Pressure state = target.getExistingDataOrNull(PRESSURE); if (state == null) return;
        long now = level.getGameTime(); SoulSandEnderman source = source(level, target, state);
        if (source == null) { clearBurn(target); return; }
        if (now >= state.nextPulse()) {
            target.setData(PRESSURE, new Pressure(state.owner(), state.dimension(), state.until(), now + SoulSandEndermanConfig.burnIntervalTicks(), state.nextAreaAt()));
            // Native ordinary/Infernal burning already owns its one burn loop. Never add a
            // second loop or feed InfernalBurn's multiplier while that unrelated fire exists.
            if (target.getRemainingFireTicks() <= 0) target.hurtServer(level, fire(level, source, true), (float)SoulSandEndermanConfig.burnDamage());
            if (source.isAlive() && !source.isRemoved() && !source.isNoAi() && target.isAlive() && mayBurn(target)) level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY(.6), target.getZ(), 3, .18, .3, .18, .008);
        }
    }
    private static @Nullable SoulSandEnderman source(ServerLevel level, LivingEntity target, Pressure state) {
        if (state.owner() == null || !state.dimension().equals(level.dimension()) || level.getGameTime() >= state.until() || !target.isAlive() || target.isRemoved() || !mayBurn(target)) return null;
        var entity = level.getEntity(state.owner());
        return entity instanceof SoulSandEnderman owner && owner.isAlive() && !owner.isRemoved() && !owner.isNoAi() && owner.level() == level && owner.isLegalSoulTarget(target) ? owner : null;
    }
    private static boolean mayBurn(LivingEntity target) { return !target.fireImmune() && !target.isInWaterOrRain() && !target.hasEffect(MobEffects.FIRE_RESISTANCE); }
    private static void clearBurn(LivingEntity target) {
        Pressure state = target.getExistingDataOrNull(PRESSURE); if (state == null) return;
        if (target.level().getGameTime() < state.nextAreaAt() && target.isAlive() && !target.isRemoved()) { if (state.owner() != null) target.setData(PRESSURE, new Pressure(null, target.level().dimension(), 0, 0, state.nextAreaAt())); } else target.removeData(PRESSURE);
    }
    private static void leaveLevel(EntityLeaveLevelEvent event) { if (event.getEntity().hasData(PRESSURE)) event.getEntity().removeData(PRESSURE); }
    private static void acceptedFireDamage(LivingDamageEvent.Post event) { if (event.getSource() instanceof SoulfireDamage source && source.victim == event.getEntity() && event.getHealthDamage() > 0 && Float.isFinite(event.getHealthDamage())) source.healthDamage = Math.max(source.healthDamage, event.getHealthDamage()); }
    private static SoulfireDamage fire(ServerLevel level, SoulSandEnderman owner, boolean periodic) { return new SoulfireDamage(level, owner, periodic); }
    private static final class SoulfireDamage extends DamageSource {
        private @Nullable LivingEntity victim;
        private float healthDamage;
        private SoulfireDamage(ServerLevel level, SoulSandEnderman owner, boolean periodic) { super(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(periodic ? DamageTypes.ON_FIRE : DamageTypes.IN_FIRE), owner, owner); }
    }
    private static @Nullable Vec3 surface(ServerLevel level, Vec3 requested) {
        if (!requested.isFinite()) return null;
        var cell = BlockPos.containing(requested.add(0, .2, 0));
        for (int offset : new int[] {0, -1, 1, -2, 2}) {
            BlockPos floor = cell.offset(0, offset - 1, 0); if (!loaded(level, new AABB(floor).inflate(1, 2, 1)) || !level.getWorldBorder().isWithinBounds(floor)) continue;
            var state = level.getBlockState(floor); var shape = state.getCollisionShape(level, floor);
            if (!state.isFaceSturdy(level, floor, Direction.UP) || shape.isEmpty() || !state.getFluidState().isEmpty() || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) continue;
            Vec3 point = new Vec3(floor.getX() + .5, floor.getY() + shape.max(Direction.Axis.Y), floor.getZ() + .5);
            if (Math.abs(point.y - requested.y) > 2.1) continue;
            if (patchValid(level, point)) return point;
        }
        return null;
    }
    private static AABB volume(Vec3 point) { return new AABB(point.x - .42, point.y + .015, point.z - .42, point.x + .42, point.y + 1.2, point.z + .42); }
    private static boolean patchValid(ServerLevel level, Vec3 point) {
        AABB volume = volume(point); if (!loaded(level, volume.expandTowards(0, -1, 0)) || !level.getWorldBorder().isWithinBounds(volume) || !level.noBlockCollision(null, volume) || level.containsAnyLiquid(volume)) return false;
        BlockPos floor = BlockPos.containing(point.add(0, -.015, 0)); var state = level.getBlockState(floor); var shape = state.getCollisionShape(level, floor);
        return state.isFaceSturdy(level, floor, Direction.UP) && !shape.isEmpty() && state.getFluidState().isEmpty() && Math.abs(floor.getY() + shape.max(Direction.Axis.Y) - point.y) < .01;
    }
    private static boolean line(ServerLevel level, Vec3 from, Vec3 to) { return from.isFinite() && to.isFinite() && loaded(level, new AABB(from, to).inflate(.03)) && level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS; }
    private static boolean loaded(ServerLevel level, AABB bounds) {
        if (bounds.minY < level.getMinY() || bounds.maxY >= level.getMaxY()) return false;
        for (int x = ((int)Math.floor(bounds.minX)) >> 4; x <= ((int)Math.floor(bounds.maxX)) >> 4; x++) for (int z = ((int)Math.floor(bounds.minZ)) >> 4; z <= ((int)Math.floor(bounds.maxZ)) >> 4; z++) if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    private record Pressure(@Nullable UUID owner, ResourceKey<Level> dimension, long until, long nextPulse, long nextAreaAt) { static Pressure empty() { return new Pressure(null, Level.OVERWORLD, 0, 0, 0); } }
}
