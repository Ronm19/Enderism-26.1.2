package com.ronm19.enderism.registry;

import com.ronm19.enderism.Enderism;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Enderism.MOD_ID);
    public static final DeferredItem<SpawnEggItem> BROWN_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "brown_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.BROWN_ENDERMAN.get())));
    public static final DeferredItem<SpawnEggItem> GRAY_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "gray_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.GRAY_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> ARCTIC_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "arctic_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.ARCTIC_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> FIRE_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "fire_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.FIRE_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> FROST_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "frost_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.FROST_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> FOREST_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "forest_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.FOREST_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> STORM_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "storm_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.STORM_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> SAND_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "sand_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.SAND_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> SILVER_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "silver_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.SILVER_ENDERMAN.get())));

    private ModItems() {
    }
}
