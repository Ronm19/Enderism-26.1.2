package com.ronm19.enderism.raid;

import com.ronm19.enderism.RavagerEndermanConfig;
import com.ronm19.enderism.entity.RavagerEnderman;
import com.ronm19.enderism.registry.ModEntities;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.Nullable;

/** Real Enderman raid members, accounted for by the narrow native Raid adapter. */
public final class RavagerRaids {
    private static final String RAID_ID = "enderism:ravager_raid_id";
    private static final String WAVE = "enderism:ravager_raid_wave";
    private RavagerRaids() {}

    public static boolean isRaidAlly(Entity entity) {
        return entity instanceof Raider || entity instanceof RavagerEnderman
                || entity instanceof Vex vex && vex.getOwner() instanceof Raider;
    }
    public static void registerEvents(IEventBus bus) {
        bus.addListener((LivingDamageEvent.Post event) -> {
            if (event.getEntity() instanceof RavagerEnderman entity) {
                Raid raid = getCurrentRaid(entity); if (raid != null) raid.updateBossbar();
            }
        });
    }
    public static @Nullable Raid getCurrentRaid(RavagerEnderman entity) {
        if (!(entity.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return null;
        int id = entity.getPersistentData().getIntOr(RAID_ID, -1);
        Raid raid = id < 0 ? null : level.getRaids().get(id);
        return raid != null && !raid.isStopped() && !raid.isOver() && raid.isActive() ? raid : null;
    }
    public static boolean hasActiveRaid(RavagerEnderman entity) { return getCurrentRaid(entity) != null; }
    public static int getWave(RavagerEnderman entity) { return entity.getPersistentData().getIntOr(WAVE, 0); }
    public static @Nullable Vec3 raidObjective(RavagerEnderman entity) {
        Raid raid = getCurrentRaid(entity);
        return raid == null ? null : Vec3.atBottomCenterOf(raid.getCenter());
    }

    public static void tick(RavagerEnderman entity) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive() || entity.isRemoved()) return;
        int previousId = entity.getPersistentData().getIntOr(RAID_ID, -1);
        Raid raid = getCurrentRaid(entity);
        if (previousId >= 0) {
            if (raid == null || getWave(entity) != raid.getGroupsSpawned()
                    || entity.blockPosition().distSqr(raid.getCenter()) >= Raid.RAID_REMOVAL_THRESHOLD_SQR) {
                detach(entity, true, true); return;
            }
            RavagerRaidAccess state = (RavagerRaidAccess)raid;
            Member existing = state.enderism$participants().get(entity.getUUID());
            if (existing == null || existing.entity.get() != entity) {
                if (!join(raid, entity, getWave(entity))) clearIdentity(entity);
            }
            else existing.lastHealth = entity.getHealth();
            if (entity.tickCount % 10 == 0) raid.updateBossbar();
        } else if (entity.tickCount % 20 == 0 && level.dimension() == Level.OVERWORLD) {
            Raid nearby = level.getRaidAt(entity.blockPosition());
            if (nearby != null && nearby.getGroupsSpawned() > 0) join(nearby, entity, nearby.getGroupsSpawned());
        }
    }

    /** The native raid owns wave timing. This method can only attach a loaded real entity to its current wave. */
    public static boolean join(Raid raid, RavagerEnderman entity, int wave) {
        if (!(entity.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || !entity.isAlive() || entity.isRemoved() || !entity.isAddedToLevel() || !ongoing(raid)
                || wave <= 0 || wave != raid.getGroupsSpawned() || entity.blockPosition().distSqr(raid.getCenter()) >= Raid.RAID_REMOVAL_THRESHOLD_SQR)
            return false;
        var id = level.getRaids().getId(raid);
        if (id.isEmpty()) return false;
        RavagerRaidAccess state = (RavagerRaidAccess)raid;
        Member previous = state.enderism$participants().get(entity.getUUID());
        if (previous != null && previous.entity.get() == entity) return true;
        if (previous == null && state.enderism$participants().size() >= Math.min(RavagerEndermanConfig.raidMaxActive(), RavagerEndermanConfig.raidMaxPerWave())) return false;
        if (previous != null) state.enderism$healthBudget(state.enderism$healthBudget() - previous.lastHealth);
        state.enderism$participants().put(entity.getUUID(), new Member(entity));
        state.enderism$healthBudget(state.enderism$healthBudget() + entity.getHealth());
        entity.getPersistentData().putInt(RAID_ID, id.getAsInt()); entity.getPersistentData().putInt(WAVE, wave);
        raid.updateBossbar(); level.getRaids().setDirty(); return true;
    }

    public static void onDeath(RavagerEnderman entity, DamageSource source) {
        Raid raid = getCurrentRaid(entity);
        if (raid != null && source.getEntity() instanceof Player player) raid.addHeroOfTheVillage(player);
        detach(entity, false, true);
    }
    public static void onRemoval(RavagerEnderman entity, Entity.RemovalReason reason) {
        // Chunk unload must not block progression. Its saved real entity may rejoin only the same still-current wave.
        detach(entity, entity.isAlive(), !reason.shouldSave());
    }
    private static void detach(RavagerEnderman entity, boolean adjustHealth, boolean clearIdentity) {
        if (entity.level() instanceof ServerLevel level) {
            int id = entity.getPersistentData().getIntOr(RAID_ID, -1);
            Raid raid = id < 0 ? null : level.getRaids().get(id);
            if (raid != null) {
                RavagerRaidAccess state = (RavagerRaidAccess)raid;
                Member member = state.enderism$participants().get(entity.getUUID());
                if (member != null && member.entity.get() == entity) {
                    state.enderism$participants().remove(entity.getUUID());
                    if (adjustHealth) state.enderism$healthBudget(state.enderism$healthBudget() - entity.getHealth());
                    raid.updateBossbar(); level.getRaids().setDirty();
                }
            }
        }
        if (clearIdentity) clearIdentity(entity);
    }
    private static void clearIdentity(RavagerEnderman entity) {
        entity.getPersistentData().remove(RAID_ID); entity.getPersistentData().remove(WAVE);
    }
    private static boolean ongoing(Raid raid) { return raid.isActive() && !raid.isOver() && !raid.isStopped(); }
    private static boolean valid(Raid raid, Member member) {
        RavagerEnderman entity = member.entity.get();
        return entity != null && entity.isAlive() && !entity.isRemoved() && entity.isAddedToLevel()
                && entity.level() instanceof ServerLevel level && level.getEntity(entity.getUUID()) == entity
                && getCurrentRaid(entity) == raid && getWave(entity) == raid.getGroupsSpawned()
                && entity.blockPosition().distSqr(raid.getCenter()) < Raid.RAID_REMOVAL_THRESHOLD_SQR;
    }
    public static int count(Raid raid) {
        int count = 0; for (Member member : ((RavagerRaidAccess)raid).enderism$participants().values()) if (valid(raid, member)) count++;
        return count;
    }
    public static float health(Raid raid) {
        float health = 0;
        for (Member member : ((RavagerRaidAccess)raid).enderism$participants().values())
            if (valid(raid, member)) health += member.entity.get().getHealth();
        return health;
    }
    public static void prune(ServerLevel level, Raid raid) {
        if (!ongoing(raid)) { clear(raid); return; }
        RavagerRaidAccess state = (RavagerRaidAccess)raid;
        Iterator<Member> iterator = state.enderism$participants().values().iterator();
        boolean changed = false;
        while (iterator.hasNext()) {
            Member member = iterator.next(); RavagerEnderman entity = member.entity.get();
            if (!valid(raid, member)) {
                iterator.remove(); changed = true;
                if (entity != null && entity.isAlive()) state.enderism$healthBudget(state.enderism$healthBudget() - member.lastHealth);
                if (entity != null && !entity.isRemoved()) clearIdentity(entity);
            } else member.lastHealth = entity.getHealth();
        }
        if (changed) { raid.updateBossbar(); level.getRaids().setDirty(); }
    }
    public static void clear(Raid raid) {
        RavagerRaidAccess state = (RavagerRaidAccess)raid;
        for (Member member : new ArrayList<>(state.enderism$participants().values())) {
            RavagerEnderman entity = member.entity.get(); if (entity != null) clearIdentity(entity);
        }
        state.enderism$participants().clear(); state.enderism$healthBudget(0);
    }
    public static void beforeWave(Raid raid) {
        clear(raid);
    }
    public static void onWaveSpawn(ServerLevel level, Raid raid, BlockPos origin) {
        if (level.dimension() != Level.OVERWORLD || !ongoing(raid)) return;
        RavagerRaidAccess state = (RavagerRaidAccess)raid;
        int wave = raid.getGroupsSpawned();
        if (wave <= state.enderism$lastAttemptedWave()) return;
        state.enderism$lastAttemptedWave(wave);
        if (wave < RavagerEndermanConfig.raidMinimumWave() || level.getRandom().nextDouble() >= RavagerEndermanConfig.raidWaveChance()) return;
        int wanted = Math.min(RavagerEndermanConfig.raidMaxPerWave(), RavagerEndermanConfig.raidMaxActive() - count(raid));
        for (int index = 0; index < wanted; index++) {
            RavagerEnderman entity = ModEntities.RAVAGER_ENDERMAN.get().create(level, EntitySpawnReason.EVENT);
            if (entity == null) return;
            boolean accepted = false;
            for (int attempt = 0; attempt < RavagerEndermanConfig.raidSpawnAttempts(); attempt++) {
                int x = origin.getX() + level.getRandom().nextInt(17) - 8;
                int z = origin.getZ() + level.getRandom().nextInt(17) - 8;
                var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) continue;
                int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
                if (Math.abs(y - origin.getY()) > 8) continue;
                entity.snapTo(x + .5, y, z + .5, level.getRandom().nextFloat() * 360, 0);
                if (!safeStanding(level, entity)) continue;
                EventHooks.finalizeMobSpawn(entity, level, level.getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.EVENT, null);
                if (!safeStanding(level, entity) || entity.isRemoved() || !entity.isAlive() || entity.level() != level) break;
                if (!level.addFreshEntity(entity) || !entity.isAddedToLevel()) break;
                accepted = join(raid, entity, wave);
                break;
            }
            if (!accepted && !entity.isRemoved()) entity.discard();
        }
    }
    public static boolean safeStanding(ServerLevel level, RavagerEnderman entity) {
        AABB box = entity.getBoundingBox();
        BlockPos min = BlockPos.containing(box.minX, box.minY - 1, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        if (box.minY < level.getMinY() + 1 || box.maxY > level.getMaxY() || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        if (!level.isPositionEntityTicking(entity.blockPosition()) || !level.noCollision(entity, box) || level.containsAnyLiquid(box)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState block = level.getBlockState(pos);
            if (hazard(block) || pos.getY() < box.minY && !block.blocksMotion()) return false;
        }
        return true;
    }
    private static boolean hazard(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.SWEET_BERRY_BUSH);
    }
    public static final class Member {
        private final WeakReference<RavagerEnderman> entity;
        private float lastHealth;
        private Member(RavagerEnderman entity) { this.entity = new WeakReference<>(entity); this.lastHealth = entity.getHealth(); }
    }
}
