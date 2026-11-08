package com.ronm19.enderism.spawning;

import com.ronm19.enderism.WhiteEndermanConfig;
import com.ronm19.enderism.entity.WhiteEnderman;
import com.ronm19.enderism.registry.ModEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;

/** An uncommon solitary Jungle spawn, explicitly gated by Overworld nighttime. */
public final class WhiteEndermanSpawns {
    private WhiteEndermanSpawns() { }
    public static void registerEvents(IEventBus bus) { bus.addListener(WhiteEndermanSpawns::potentialSpawns); }
    public static boolean isSpawnTime(ServerLevel level) {
        long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
        return level.dimension() == Level.OVERWORLD && time >= 13000 && time < 23000;
    }
    public static boolean isSpawnHabitat(ServerLevel level, BlockPos pos) {
        return isSpawnTime(level) && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                && level.getBiome(pos).is(Biomes.JUNGLE)
                && level.getEntitiesOfClass(WhiteEnderman.class, new AABB(pos).inflate(64), mob -> mob.isAlive() && !mob.isRemoved()).size() < WhiteEndermanConfig.naturalPopulationCap();
    }
    private static void potentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || event.getMobCategory() != MobCategory.MONSTER) return;
        var entries = List.copyOf(event.getSpawnerDataList()).stream().filter(entry -> entry.value().type() == ModEntities.WHITE_ENDERMAN.get()).toList();
        if (entries.isEmpty()) return;
        for (var entry : entries) event.removeSpawnerData(entry);
        event.addSpawnerData(new Weighted<>(new MobSpawnSettings.SpawnerData(ModEntities.WHITE_ENDERMAN.get(), 1, 1), WhiteEndermanConfig.naturalSpawnWeight()));
    }
    public static boolean checkSpawnRules(EntityType<WhiteEnderman> type, ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return true;
        if (!isSpawnHabitat(accessor.getLevel(), pos)
                || !accessor.getLevel().getWorldBorder().isWithinBounds(type.getSpawnAABB(pos.getX() + .5, pos.getY(), pos.getZ() + .5))
                || !Monster.checkMonsterSpawnRules(type, accessor, reason, pos, random)) return false;
        var support = accessor.getBlockState(pos.below());
        if (!support.isFaceSturdy(accessor, pos.below(), Direction.UP) || !support.getFluidState().isEmpty() || support.is(Blocks.MAGMA_BLOCK)) return false;
        for (int y = 0; y < Mth.ceil(type.getHeight()); y++) { BlockPos cell = pos.above(y); var state = accessor.getBlockState(cell); if (!NaturalSpawner.isValidEmptySpawnBlock(accessor, cell, state, state.getFluidState(), type)) return false; }
        return true;
    }
}
