package com.ronm19.enderism.entity;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.StormEndermanConfig;
import com.ronm19.enderism.entity.ai.MastermindBrain;
import com.ronm19.enderism.entity.ai.StormEndermanBrain;
import com.ronm19.enderism.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

/** #007: three server-authoritative storm abilities with a synchronized, finite local tempest. */
public final class StormEnderman extends AbstractEnderman {
    public static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "spawns_storm_enderman"));
    private static final EntityDataAccessor<Byte> HUNT_STATE = SynchedEntityData.defineId(StormEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> ABILITY = SynchedEntityData.defineId(StormEnderman.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> CAST_END = SynchedEntityData.defineId(StormEnderman.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<BlockPos> TEMPEST_CENTER = SynchedEntityData.defineId(StormEnderman.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> TEMPEST_RADIUS = SynchedEntityData.defineId(StormEnderman.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> TEMPEST_END = SynchedEntityData.defineId(StormEnderman.class, EntityDataSerializers.LONG);
    private static final DustParticleOptions WARNING = new DustParticleOptions(0xA0ACFF, 0.9F);
    private static final HuntState[] HUNT_STATES = HuntState.values();
    private static final StormEndermanBrain.Ability[] ABILITIES = StormEndermanBrain.Ability.values();

    public StormEnderman(EntityType<? extends StormEnderman> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() {
        return AbstractEnderman.createAttributes().add(Attributes.FOLLOW_RANGE, StormEndermanConfig.DEFAULT_FOLLOW_RANGE);
    }
    @Override protected MastermindBrain createMastermindBrain() { return new StormEndermanBrain(this); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(HUNT_STATE, (byte) HuntState.IDLE.ordinal());
        data.define(ABILITY, (byte) StormEndermanBrain.Ability.NONE.ordinal());
        data.define(CAST_END, 0L); data.define(TEMPEST_CENTER, BlockPos.ZERO);
        data.define(TEMPEST_RADIUS, 0.0F); data.define(TEMPEST_END, 0L);
    }
    public HuntState getHuntState() {
        int value = this.entityData.get(HUNT_STATE);
        return value >= 0 && value < HUNT_STATES.length ? HUNT_STATES[value] : HuntState.IDLE;
    }
    public void setHuntState(HuntState state) { if (!this.level().isClientSide()) this.entityData.set(HUNT_STATE, (byte) state.ordinal()); }
    public StormEndermanBrain.Ability getAbility() {
        int value = this.entityData.get(ABILITY);
        return value >= 0 && value < ABILITIES.length ? ABILITIES[value] : StormEndermanBrain.Ability.NONE;
    }
    public void setCast(StormEndermanBrain.Ability ability, long until) {
        if (!this.level().isClientSide()) { this.entityData.set(ABILITY, (byte) ability.ordinal()); this.entityData.set(CAST_END, until); }
    }
    public int getWarmupTicksRemaining() { return (int) Math.max(0L, this.entityData.get(CAST_END) - this.level().getGameTime()); }
    public float getEyeBrightness() { return this.getAbility() != StormEndermanBrain.Ability.NONE ? 1.0F : this.isTempestActive() ? 0.95F : 0.8F; }
    public boolean isTempestActive() { return this.isAlive() && !this.isRemoved() && this.entityData.get(TEMPEST_END) > this.level().getGameTime(); }
    public Vec3 getTempestCenter() { return Vec3.atBottomCenterOf(this.entityData.get(TEMPEST_CENTER)); }
    public float getTempestRadius() { return this.entityData.get(TEMPEST_RADIUS); }
    public int getTempestTicksRemaining() { return (int) Math.max(0L, this.entityData.get(TEMPEST_END) - this.level().getGameTime()); }
    public void setTempest(BlockPos center, float radius, long until) {
        if (!this.level().isClientSide()) {
            this.entityData.set(TEMPEST_CENTER, center.immutable()); this.entityData.set(TEMPEST_RADIUS, radius); this.entityData.set(TEMPEST_END, until);
        }
    }
    public void endTempest() { if (!this.level().isClientSide()) this.entityData.set(TEMPEST_END, 0L); }
    public static boolean exposed(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        return level.hasChunkAt(pos) && level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY()
                && level.canSeeSky(pos);
    }
    public void strikeWarning(ServerLevel level, Vec3 point) {
        for (int i=0;i<8;i++) {
            double angle = i * Math.PI / 4.0;
            level.sendParticles(WARNING, point.x + Math.cos(angle) * 1.3, point.y + .08,
                    point.z + Math.sin(angle) * 1.3, 1, .03, .02, .03, 0);
        }
    }
    public void showLightning(ServerLevel level, Vec3 point) {
        StormLightning bolt = ModEntities.STORM_LIGHTNING.get().create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) { bolt.snapTo(point.x, point.y, point.z, 0, 0); if (!level.addFreshEntity(bolt)) bolt.discard(); }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y + .4, point.z, 12, .5, .7, .5, .04);
    }
    public void gustWarning(ServerLevel level) {
        level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 1.0, this.getZ(), 10, .4, .3, .4, .02);
        this.playSound(SoundEvents.BREEZE_SHOOT, .7F, 1.1F);
    }
    @Override protected boolean teleport() { return this.getAbility() == StormEndermanBrain.Ability.NONE && super.teleport(); }
    @Override public void travel(Vec3 input) {
        if (this.getAbility() != StormEndermanBrain.Ability.NONE) { this.setDeltaMovement(Vec3.ZERO); return; }
        super.travel(input);
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) ((StormEndermanBrain) this.getMastermindBrain()).interruptCast();
        return hurt;
    }
    @Override public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && (this.isNoAi() || !this.isAlive())) ((StormEndermanBrain) this.getMastermindBrain()).cancelAll();
        if (this.level().isClientSide() && this.isAlive() && this.tickCount % 12 == 0)
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + this.random.nextDouble() * this.getBbHeight(), this.getZ(), 0, .01, 0);
    }
    @Override public void remove(Entity.RemovalReason reason) {
        ((StormEndermanBrain) this.getMastermindBrain()).cancelAll(); super.remove(reason);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input); this.getMastermindBrain().clearEncounter(); ((StormEndermanBrain) this.getMastermindBrain()).cancelAll();
    }
    public static boolean checkStormSpawnRules(EntityType<StormEnderman> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(level, pos) || !Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
        for (int h=0;h<3;h++) {
            BlockPos cell=pos.above(h); var block=level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level,cell,block,block.getFluidState(),type)) return false;
        }
        return true;
    }
    public static boolean isSpawnTime(ServerLevelAccessor level) {
        long clock=Math.floorMod(level.getLevel().getOverworldClockTime(),24000L);
        return level.getLevel().dimension()==Level.OVERWORLD && clock>=13000L && clock<23000L;
    }
    public static boolean isSpawnHabitat(ServerLevelAccessor level, BlockPos pos) { return isSpawnTime(level) && level.getBiome(pos).is(SPAWN_BIOMES); }
    public enum HuntState { IDLE, PURSUING, SEARCHING, CASTING }
}
