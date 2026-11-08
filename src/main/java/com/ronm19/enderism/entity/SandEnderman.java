package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.SandEndermanConfig;
import com.ronm19.enderism.entity.ai.MastermindBrain;
import com.ronm19.enderism.entity.ai.SandEndermanBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** #008: modest physical strength, weak poison, and a temporary encounter-bound sand veil. */
public final class SandEnderman extends AbstractEnderman {
    public static final Identifier RABBIT_STRIKE_MODIFIER = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "sand_rabbit_strike");
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_sand_enderman"));
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(SandEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> VEIL_WARMUP_END = SynchedEntityData.defineId(SandEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<BlockPos> VEIL_CENTER = SynchedEntityData.defineId(SandEnderman.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> VEIL_RADIUS = SynchedEntityData.defineId(SandEnderman.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> VEIL_INTENSITY = SynchedEntityData.defineId(SandEnderman.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> VEIL_END = SynchedEntityData.defineId(SandEnderman.class, EntityDataSerializers.LONG);
    private static final HuntState[] STATES = HuntState.values();

    public SandEnderman(EntityType<? extends SandEnderman> type, Level level) {
        super(type, level);
        this.applyRuntimeAttributes(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.MAX_HEALTH, SandEndermanConfig.DEFAULT_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, SandEndermanConfig.DEFAULT_ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, SandEndermanConfig.DEFAULT_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, SandEndermanConfig.DEFAULT_FOLLOW_RANGE);
    }

    private void applyRuntimeAttributes(boolean fillHealth) {
        if (this.level().isClientSide() || !SandEndermanConfig.SPEC.isLoaded()) return;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SandEndermanConfig.health());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(SandEndermanConfig.attackDamage());
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SandEndermanConfig.movementSpeed());
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SandEndermanConfig.followRange());
        this.setHealth(fillHealth ? this.getMaxHealth() : Math.min(this.getHealth(), this.getMaxHealth()));
    }

    @Override protected MastermindBrain createMastermindBrain() { return new SandEndermanBrain(this); }
    @Override protected void customServerAiStep(ServerLevel level) { this.getBrain().tick(level, this); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(HUNT_STATE, (byte) HuntState.IDLE.ordinal()); data.define(VEIL_WARMUP_END, 0L);
        data.define(VEIL_CENTER, BlockPos.ZERO); data.define(VEIL_RADIUS, 0.0F);
        data.define(VEIL_INTENSITY, 0.0F); data.define(VEIL_END, 0L);
    }
    public HuntState getHuntState() {
        int index = this.entityData.get(HUNT_STATE);
        return index >= 0 && index < STATES.length ? STATES[index] : HuntState.IDLE;
    }
    public void setHuntState(HuntState state) { if (!this.level().isClientSide()) this.entityData.set(HUNT_STATE, (byte) state.ordinal()); }
    public boolean isVeilWindingUp() { return this.entityData.get(VEIL_WARMUP_END) > this.level().getGameTime(); }
    public int getWarmupTicksRemaining() { return (int) Math.max(0L, this.entityData.get(VEIL_WARMUP_END) - this.level().getGameTime()); }
    public void setVeilWarmupEnd(long end) { if (!this.level().isClientSide()) this.entityData.set(VEIL_WARMUP_END, end); }
    public boolean isSandstormActive() {
        return this.isAlive() && !this.isRemoved() && !this.isNoAi() && this.entityData.get(VEIL_END) > this.level().getGameTime();
    }
    public Vec3 getSandstormCenter() { return Vec3.atBottomCenterOf(this.entityData.get(VEIL_CENTER)); }
    public float getSandstormRadius() { return this.entityData.get(VEIL_RADIUS); }
    public float getSandstormIntensity() { return this.entityData.get(VEIL_INTENSITY); }
    public int getSandstormTicksRemaining() { return (int) Math.max(0L, this.entityData.get(VEIL_END) - this.level().getGameTime()); }
    public float getEyeBrightness() { return this.isSandstormActive() || this.isVeilWindingUp() ? 1.0F : 0.85F; }
    public void setSandstorm(BlockPos center, float radius, float intensity, long until) {
        if (!this.level().isClientSide()) {
            this.entityData.set(VEIL_CENTER, center.immutable()); this.entityData.set(VEIL_RADIUS, radius);
            this.entityData.set(VEIL_INTENSITY, intensity); this.entityData.set(VEIL_END, until);
        }
    }
    public void endSandstorm() { if (!this.level().isClientSide()) this.entityData.set(VEIL_END, 0L); }
    public boolean isInsideSandstorm(Vec3 position) {
        if (!this.isSandstormActive()) return false;
        Vec3 center = this.getSandstormCenter(); double x = position.x - center.x, z = position.z - center.z;
        return x * x + z * z <= this.getSandstormRadius() * this.getSandstormRadius()
                && position.y >= center.y - 2.0 && position.y <= center.y + 6.0;
    }

    public void veilWarning(ServerLevel level) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                this.getX(), this.getY() + .6, this.getZ(), 8, .5, .4, .5, .03);
        this.playSound(SoundEvents.BREEZE_SHOOT, .4F, .65F);
    }
    public void veilGathering(ServerLevel level) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                this.getX(), this.getY() + .7, this.getZ(), 4, .55, .5, .55, .015);
    }
    private void slashImpact(ServerLevel level, LivingEntity target) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                target.getX(), target.getY() + target.getBbHeight() * .5, target.getZ(), 5, .2, .25, .2, .04);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()),
                target.getX(), target.getY() + target.getBbHeight() * .5, target.getZ(), 3, .2, .25, .2, .04);
        this.playSound(SoundEvents.GLASS_BREAK, .22F, 1.6F);
    }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit;
        var attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
        double damage = attack.getValue();
        double reduction = damage > 0.0 ? Math.min(damage, SandEndermanConfig.rabbitStrikeDamage()) / damage - 1.0 : 0.0;
        boolean scopedRabbitStrike = target instanceof Rabbit && reduction < 0.0 && !attack.hasModifier(RABBIT_STRIKE_MODIFIER);
        if (scopedRabbitStrike) attack.addTransientModifier(new AttributeModifier(RABBIT_STRIKE_MODIFIER, reduction, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        try { hit = super.doHurtTarget(level, target); }
        finally { if (scopedRabbitStrike) attack.removeModifier(RABBIT_STRIKE_MODIFIER); }
        if (hit && target instanceof LivingEntity living) {
            this.slashImpact(level, living);
            ((SandEndermanBrain) this.getMastermindBrain()).onSuccessfulMelee(level, living);
        }
        return hit;
    }

    /** A visible, loaded sight corridor prevents flank destinations in inaccessible rooms or caves. */
    public boolean tryHuntTeleport(Vec3 requested, LivingEntity observedTarget) {
        if (!(this.level() instanceof ServerLevel level) || !this.isSandstormActive() || this.isVeilWindingUp()
                || observedTarget.level() != level || !observedTarget.isAlive() || observedTarget.isRemoved()
                || !this.canAttack(observedTarget) || this.isAlliedTo(observedTarget)
                || observedTarget instanceof Player player && (player.isCreative() || player.isSpectator())
                || observedTarget instanceof ServerPlayer player && player.hasDisconnected()
                || !this.getSensing().hasLineOfSight(observedTarget)
                || this.distanceToSqr(observedTarget) > SandEndermanConfig.huntActivationRange() * SandEndermanConfig.huntActivationRange()
                || !Double.isFinite(requested.x) || !Double.isFinite(requested.y) || !Double.isFinite(requested.z)) return false;
        double dx = requested.x - observedTarget.getX(), dz = requested.z - observedTarget.getZ();
        double allowed = SandEndermanConfig.huntFlankDistance() + 1.5;
        if (dx * dx + dz * dz < 4.0 || dx * dx + dz * dz > allowed * allowed
                || !this.isInsideSandstorm(requested)) return false;
        BlockPos target = observedTarget.blockPosition(), feet = BlockPos.containing(requested);
        BlockPos low = new BlockPos(Math.min(target.getX(), feet.getX()), Math.min(target.getY(), feet.getY()) - 3, Math.min(target.getZ(), feet.getZ()));
        BlockPos high = new BlockPos(Math.max(target.getX(), feet.getX()), Math.max(target.getY(), feet.getY()) + 3, Math.max(target.getZ(), feet.getZ()));
        if (!level.hasChunksAt(low, high)) return false;
        for (int drop = 0; drop < 2 && !level.getBlockState(feet.below()).blocksMotion(); drop++) feet = feet.below();
        if (!level.getBlockState(feet.below()).blocksMotion() || Math.abs(feet.getY() - observedTarget.getY()) > 2.0) return false;
        Vec3 standing = new Vec3(requested.x, feet.getY(), requested.z);
        if (!this.isInsideSandstorm(standing)) return false;
        if (level.clip(new ClipContext(observedTarget.getEyePosition(), standing.add(0, this.getEyeHeight(), 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) return false;
        Vec3 previous = this.position();
        if (!this.trySafeTeleport(standing, 0)) return false;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                previous.x, previous.y + .4, previous.z, 7, .3, .4, .3, .03);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                this.getX(), this.getY() + .4, this.getZ(), 7, .3, .4, .3, .03);
        return true;
    }

    @Override protected boolean teleport() { return !this.isVeilWindingUp() && super.teleport(); }
    @Override public void travel(Vec3 input) {
        if (this.isVeilWindingUp()) { this.setDeltaMovement(0, this.getDeltaMovement().y, 0); super.travel(Vec3.ZERO); return; }
        super.travel(input);
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) ((SandEndermanBrain) this.getMastermindBrain()).interruptVeil();
        return hurt;
    }
    @Override public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && (this.isNoAi() || !this.isAlive())) this.getMastermindBrain().clearEncounter();
    }
    @Override public void remove(Entity.RemovalReason reason) {
        ((SandEndermanBrain) this.getMastermindBrain()).cancelSandstorm();
        super.remove(reason);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.getMastermindBrain().clearEncounter(); this.applyRuntimeAttributes(false);
    }
    public static boolean checkSandSpawnRules(EntityType<SandEnderman> type, ServerLevelAccessor level,
                                            EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || level.getDifficulty() == Difficulty.PEACEFUL
                || !Mob.checkMobSpawnRules(type, level, reason, pos, random)) return false;
        for (int height = 0; height < 3; height++) {
            BlockPos cell = pos.above(height); var block = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, block, block.getFluidState(), type)) return false;
        }
        return true;
    }
    public static boolean isSpawnTime(ServerLevelAccessor level) { return level.getLevel().dimension() == Level.OVERWORLD; }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos position) {
        return isSpawnTime(level) && level.getBiome(position).is(SPAWN_BIOMES);
    }
    public enum HuntState { IDLE, APPROACHING, ATTACKING, SUMMONING_STORM, AMBUSHING, SEARCHING }
}
