package com.ronm19.enderism.spawning;

import com.ronm19.enderism.SculkEndermanConfig;
import com.ronm19.enderism.entity.SculkEnderman;
import com.ronm19.enderism.registry.ModEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;

/** A species-only exception to Ancient City's empty native structure spawn lists. */
public final class SculkEndermanSpawns {
    private SculkEndermanSpawns() { }
    public static void registerEvents(IEventBus bus) { bus.addListener(SculkEndermanSpawns::potentialSpawns); }
    public static boolean isSpawnPeriod(ServerLevel level) { return level.dimension() == Level.OVERWORLD; }

    /** Inspect only references and starts already present in loaded chunks. */
    public static boolean isAncientCity(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        var city = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.ANCIENT_CITY);
        if (city == null) return false;
        var local = chunk.getStartForStructure(city);
        if (local != null && local.isValid() && local.getBoundingBox().isInside(pos)) return true;
        for (long key : chunk.getReferencesForStructure(city)) {
            var startPos = ChunkPos.unpack(key);
            var startChunk = level.getChunkSource().getChunkNow(startPos.x(), startPos.z());
            if (startChunk == null) continue;
            var start = startChunk.getStartForStructure(city);
            if (start != null && start.isValid() && start.getBoundingBox().isInside(pos)) return true;
        }
        return false;
    }
    public static boolean isUndergroundHabitat(ServerLevel level, BlockPos pos) {
        return isSpawnPeriod(level)
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                && !level.canSeeSky(pos) && (level.getBiome(pos).is(Biomes.DEEP_DARK) || isAncientCity(level, pos));
    }
    public static boolean isSpawnHabitat(ServerLevel level, BlockPos pos) {
        return isUndergroundHabitat(level, pos) && level.getEntitiesOfClass(SculkEnderman.class, new AABB(pos).inflate(64),
                mob -> mob.isAlive() && !mob.isRemoved()).size() < SculkEndermanConfig.naturalPopulationCap();
    }
    private static void potentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getMobCategory() != MobCategory.MONSTER) return;
        var type = ModEntities.SCULK_ENDERMAN.get();
        for (var entry : List.copyOf(event.getSpawnerDataList())) if (entry.value().type() == type) event.removeSpawnerData(entry);
        if (!isUndergroundHabitat(level, event.getPos())) return;
        // The native Ancient City override can be empty. Add this one species, leaving all other entries untouched.
        int weight = isAncientCity(level, event.getPos()) ? SculkEndermanConfig.ancientCitySpawnWeight() : SculkEndermanConfig.deepDarkSpawnWeight();
        event.addSpawnerData(new Weighted<>(new MobSpawnSettings.SpawnerData(type, 1, 1), weight));
    }
    public static boolean checkSpawnRules(EntityType<SculkEnderman> type, ServerLevelAccessor accessor,
                                          EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        ServerLevel level = accessor.getLevel();
        if (!isSpawnHabitat(level, pos) || !Monster.checkMonsterSpawnRules(type, accessor, reason, pos, random)) return false;
        var support = accessor.getBlockState(pos.below());
        if (!support.isFaceSturdy(accessor, pos.below(), Direction.UP) || !support.getFluidState().isEmpty() || support.is(Blocks.MAGMA_BLOCK)) return false;
        for (int y = 0; y < Mth.ceil(type.getHeight()); y++) {
            BlockPos cell = pos.above(y); var state = accessor.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(accessor, cell, state, state.getFluidState(), type)) return false;
        }
        return true;
    }
}
