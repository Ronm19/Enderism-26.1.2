package com.ronm19.enderism.spawning;

import com.ronm19.enderism.VoidEndermanConfig;
import com.ronm19.enderism.registry.ModEntities;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Keeps the data-driven End spawn entry unique while applying the live configurable weight. */
public final class VoidEndermanSpawns {
    private VoidEndermanSpawns() {}
    public static void registerEvents(IEventBus bus) { bus.addListener(VoidEndermanSpawns::potentialSpawns); }
    private static void potentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.END || event.getMobCategory() != MobCategory.MONSTER) return;
        var entries = List.copyOf(event.getSpawnerDataList()).stream()
                .filter(entry -> entry.value().type() == ModEntities.VOID_ENDERMAN.get()).toList();
        if (entries.isEmpty()) return;
        for (var entry : entries) event.removeSpawnerData(entry);
        event.addSpawnerData(new Weighted<>(new MobSpawnSettings.SpawnerData(ModEntities.VOID_ENDERMAN.get(), 1, 1), VoidEndermanConfig.naturalSpawnWeight()));
    }
}
