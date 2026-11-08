package com.ronm19.enderism.spawning;

import com.ronm19.enderism.RavagerEndermanConfig;
import com.ronm19.enderism.entity.RavagerEnderman;
import com.ronm19.enderism.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;

/** Adds one configurable natural spawn entry only inside a real, already-loaded outpost area. */
public final class RavagerOutpostSpawns {
    private RavagerOutpostSpawns() {}
    public static void registerEvents(IEventBus bus) { bus.addListener(RavagerOutpostSpawns::potentialSpawns); }
    private static void potentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER || !(event.getLevel() instanceof ServerLevel level)
                || RavagerEndermanConfig.naturalSpawnWeight() <= 0 || !isSpawnHabitat(level, event.getPos())) return;
        if (event.getSpawnerDataList().stream().noneMatch(e -> e.value().type() == ModEntities.RAVAGER_ENDERMAN.get()))
            event.addSpawnerData(new Weighted<>(new MobSpawnSettings.SpawnerData(ModEntities.RAVAGER_ENDERMAN.get(), 1, 1),
                    RavagerEndermanConfig.naturalSpawnWeight()));
    }
    public static boolean isSpawnHabitat(ServerLevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof ServerLevel level) || findOutpostCenter(level, pos) == null) return false;
        return level.getEntitiesOfClass(RavagerEnderman.class, new AABB(pos).inflate(48),
                entity -> entity.isAlive() && !entity.isRemoved()).size() < RavagerEndermanConfig.outpostPopulationCap();
    }
    public static @Nullable BlockPos findOutpostCenter(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD || !level.hasChunkAt(pos)) return null;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.PILLAGER_OUTPOST).value();
        StructureStart direct = chunk.getStartForStructure(structure);
        if (contains(direct, pos)) return standingCenter(direct, pos);
        int checked = 0;
        for (long reference : chunk.getReferencesForStructure(structure)) {
            if (++checked > 64) break;
            ChunkPos start = ChunkPos.unpack(reference);
            var loaded = level.getChunkSource().getChunkNow(start.x(), start.z());
            if (loaded == null) continue;
            StructureStart candidate = loaded.getStartForStructure(structure);
            if (contains(candidate, pos)) return standingCenter(candidate, pos);
        }
        return null;
    }
    private static boolean contains(@Nullable StructureStart start, BlockPos pos) {
        return start != null && start.isValid() && start.getBoundingBox().isInside(pos);
    }
    private static BlockPos standingCenter(StructureStart start, BlockPos feet) {
        var center = start.getBoundingBox().getCenter();
        return new BlockPos(center.getX(), feet.getY(), center.getZ());
    }
}
