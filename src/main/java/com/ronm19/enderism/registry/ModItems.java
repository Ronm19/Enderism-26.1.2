package com.ronm19.enderism.registry;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.item.EnderCodexItem;
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

    public static final DeferredItem<SpawnEggItem> PHANTOM_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "phantom_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.PHANTOM_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> DARK_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "dark_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.DARK_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> INFERNAL_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "infernal_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.INFERNAL_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> GOLDEN_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "golden_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.GOLDEN_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> NIGHTMARE_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "nightmare_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.NIGHTMARE_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> DEMON_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "demon_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.DEMON_ENDERMAN.get())));

    public static final DeferredItem<EnderCodexItem> ENDER_CODEX = ITEMS.registerItem("ender_codex", EnderCodexItem::new);

    public static final DeferredItem<SpawnEggItem> BLOOD_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "blood_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.BLOOD_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> SPECTRAL_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "spectral_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.SPECTRAL_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> ANGEL_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "angel_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.ANGEL_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> RAVAGER_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "ravager_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.RAVAGER_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> ENDERMAN_GOLEM_SPAWN_EGG = ITEMS.registerItem(
            "enderman_golem_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.ENDERMAN_GOLEM.get())));

    public static final DeferredItem<SpawnEggItem> VOID_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "void_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.VOID_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> BOGGED_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "bogged_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.BOGGED_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> SKELETON_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "skeleton_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.SKELETON_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> ZOMBIE_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "zombie_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.ZOMBIE_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> SCULK_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "sculk_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.SCULK_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> HUSK_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "husk_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.HUSK_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> VAMPIRE_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "vampire_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.VAMPIRE_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> WITHER_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "wither_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.WITHER_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> WHITE_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "white_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.WHITE_ENDERMAN.get())));

    public static final DeferredItem<SpawnEggItem> SOUL_SAND_ENDERMAN_SPAWN_EGG = ITEMS.registerItem(
            "soul_sand_enderman_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.SOUL_SAND_ENDERMAN.get())));

    private ModItems() {
    }
}
