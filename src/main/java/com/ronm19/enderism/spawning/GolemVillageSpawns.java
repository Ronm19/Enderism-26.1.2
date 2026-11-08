package com.ronm19.enderism.spawning;

import com.ronm19.enderism.EndermanGolemConfig;
import com.ronm19.enderism.entity.EndermanGolem;
import com.ronm19.enderism.registry.ModEntities;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;

/** Loaded, inhabited villages supply a bounded natural guardian spawn entry. */
public final class GolemVillageSpawns {
    private static final int POPULATION_RADIUS = 64;
    private GolemVillageSpawns() {}

    public static void registerEvents(IEventBus bus) { bus.addListener(GolemVillageSpawns::potentialSpawns); }
    private static void potentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER || !(event.getLevel() instanceof ServerLevel level)
                || EndermanGolemConfig.naturalSpawnWeight() <= 0 || !isSpawnHabitat(level, event.getPos())) return;
        if (event.getSpawnerDataList().stream().noneMatch(entry -> entry.value().type() == ModEntities.ENDERMAN_GOLEM.get()))
            event.addSpawnerData(new Weighted<>(new MobSpawnSettings.SpawnerData(ModEntities.ENDERMAN_GOLEM.get(), 1, 1),
                    EndermanGolemConfig.naturalSpawnWeight()));
    }
    public static boolean isSpawnHabitat(ServerLevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof ServerLevel level)) return false;
        BlockPos home = findHome(level, pos);
        return home != null && level.getEntitiesOfClass(EndermanGolem.class, new AABB(home).inflate(POPULATION_RADIUS),
                entity -> entity.isAlive() && !entity.isRemoved()).size() < EndermanGolemConfig.villagePopulationCap();
    }
    public static boolean isVillageArea(ServerLevel level, BlockPos pos) { return findHome(level, pos) != null; }

    /** Uses current residents and occupied HOME POIs; empty buildings and arbitrary beds are insufficient. */
    public static @Nullable BlockPos findHome(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD || !loaded(level, pos) || !level.isVillage(pos)) return null;
        int radius = (int)Math.ceil(EndermanGolemConfig.villageRange());
        List<Villager> residents = level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(radius),
                villager -> villager.isAlive() && !villager.isRemoved() && loaded(level, villager.blockPosition())
                        && villager.blockPosition().distSqr(pos) <= (double)radius * radius);
        if (residents.isEmpty()) return null;

        // Existing claimed homes provide the ordinary fast path, without examining neighboring POI chunks.
        BlockPos claimed = residents.stream().map(villager -> villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null))
                .filter(home -> home != null && home.dimension() == level.dimension() && loaded(level, home.pos())
                        && home.pos().distSqr(pos) <= (double)radius * radius)
                .map(home -> occupiedHomeInChunk(level, home.pos(), home.pos(), 0))
                .filter(home -> home != null).min(Comparator.comparingDouble(pos::distSqr)).orElse(null);
        if (claimed != null) return claimed;

        BlockPos closest = null; double bestDistance = (double)radius * radius;
        for (int x = (pos.getX() - radius) >> 4; x <= (pos.getX() + radius) >> 4; x++) {
            for (int z = (pos.getZ() - radius) >> 4; z <= (pos.getZ() + radius) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) continue;
                // Query only POI data belonging to already loaded terrain; never request or generate world chunks.
                var homes = level.getPoiManager().getInChunk(type -> type.is(PoiTypes.HOME), new ChunkPos(x, z), PoiManager.Occupancy.IS_OCCUPIED);
                try (homes) {
                    for (var iterator = homes.iterator(); iterator.hasNext();) {
                        BlockPos home = iterator.next().getPos(); double distance = home.distSqr(pos);
                        if (distance <= bestDistance && level.getBlockState(home).getBlock() instanceof BedBlock) {
                            closest = home.immutable(); bestDistance = distance;
                        }
                    }
                }
            }
        }
        return closest;
    }
    private static @Nullable BlockPos occupiedHomeInChunk(ServerLevel level, BlockPos chunkPoint, BlockPos center, int radius) {
        if (!loaded(level, chunkPoint)) return null;
        try (var records = level.getPoiManager().getInChunk(type -> type.is(PoiTypes.HOME), ChunkPos.containing(chunkPoint), PoiManager.Occupancy.IS_OCCUPIED)) {
            return records.map(PoiRecord::getPos).filter(home -> home.distSqr(center) <= (double)radius * radius
                    && level.getBlockState(home).getBlock() instanceof BedBlock).findFirst().map(BlockPos::immutable).orElse(null);
        }
    }
    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
}
