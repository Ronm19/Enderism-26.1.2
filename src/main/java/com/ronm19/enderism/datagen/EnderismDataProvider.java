package com.ronm19.enderism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import static java.util.Map.entry;

/** Enderism's entity loot, biome spawning, and damage-type data. */
public final class EnderismDataProvider implements DataProvider {
    private final PackOutput output;

    public EnderismDataProvider(PackOutput output) { this.output = output; }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<String, JsonElement> resources = new LinkedHashMap<>();
        for (String variant : EnderismDataGenerators.VARIANTS) {
            resources.put("data/enderism/loot_table/entities/" + EnderismDataGenerators.entityId(variant) + ".json", endermanLoot());
        }

        Map<String, Integer> weights = Map.ofEntries(
                entry("brown", 30), entry("gray", 10), entry("arctic", 10), entry("frost", 10),
                entry("forest", 8), entry("storm", 8), entry("sand", 8), entry("silver", 8),
                entry("phantom", 8), entry("dark", 24), entry("infernal", 12), entry("golden", 18), entry("nightmare", 16), entry("blood", 16), entry("spectral", 12), entry("angel", 8), entry("void", 4), entry("bogged", 16), entry("skeleton", 12), entry("zombie", 12), entry("sculk", 10), entry("vampire", 8), entry("wither", 6), entry("white", 3), entry("soul_sand", 30));
        for (String variant : EnderismDataGenerators.VARIANTS) {
            if (!variant.equals("fire") && !variant.equals("demon") && !variant.equals("ravager") && !variant.equals("enderman_golem") && !variant.equals("husk")) {
                resources.put("data/enderism/neoforge/biome_modifier/" + variant + "_enderman_spawns.json",
                        spawns(variant, new JsonPrimitive("#enderism:spawns_" + variant + "_enderman"), weights.get(variant)));
            }
        }
        resources.put("data/enderism/neoforge/biome_modifier/fire_enderman_crimson_forest_spawns.json",
                spawns("fire", values("minecraft:crimson_forest"), 6));
        resources.put("data/enderism/neoforge/biome_modifier/fire_enderman_nether_wastes_spawns.json",
                spawns("fire", values("minecraft:nether_wastes"), 24));
        resources.put("data/enderism/neoforge/biome_modifier/fire_enderman_overworld_spawns.json",
                spawns("fire", new JsonPrimitive("#enderism:spawns_fire_enderman_overworld"), 6));
        resources.put("data/enderism/neoforge/biome_modifier/demon_enderman_crimson_forest_spawns.json",
                spawns("demon", values("minecraft:crimson_forest"), 18));
        resources.put("data/enderism/neoforge/biome_modifier/demon_enderman_nether_wastes_spawns.json",
                spawns("demon", values("minecraft:nether_wastes"), 6));

        biomeTag(resources, "brown_enderman", "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest",
                "minecraft:old_growth_birch_forest", "minecraft:dark_forest");
        biomeTag(resources, "gray_enderman", "minecraft:pale_garden");
        for (String variant : List.of("arctic", "frost")) {
            biomeTag(resources, variant + "_enderman", "minecraft:snowy_plains", "minecraft:snowy_taiga",
                    "minecraft:ice_spikes", "minecraft:frozen_peaks", "minecraft:snowy_slopes", "minecraft:grove");
        }
        biomeTag(resources, "fire_enderman_nether", "minecraft:nether_wastes", "minecraft:crimson_forest");
        biomeTag(resources, "fire_enderman_overworld", "minecraft:badlands", "minecraft:eroded_badlands",
                "minecraft:wooded_badlands", "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna");
        biomeTag(resources, "forest_enderman", "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest",
                "minecraft:old_growth_birch_forest", "minecraft:dark_forest", "minecraft:taiga",
                "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga");
        biomeTag(resources, "storm_enderman", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest");
        biomeTag(resources, "sand_enderman", "minecraft:desert");
        biomeTag(resources, "silver_enderman", "minecraft:dark_forest");
        biomeTag(resources, "phantom_enderman", "minecraft:windswept_gravelly_hills");
        biomeTag(resources, "dark_enderman", "minecraft:dark_forest");
        biomeTag(resources, "infernal_enderman", "minecraft:nether_wastes", "minecraft:basalt_deltas");
        biomeTag(resources, "golden_enderman", "minecraft:badlands");
        biomeTag(resources, "nightmare_enderman", "minecraft:taiga");
        biomeTag(resources, "demon_enderman", "minecraft:crimson_forest", "minecraft:nether_wastes");
        biomeTag(resources, "blood_enderman", "minecraft:badlands");
        biomeTag(resources, "spectral_enderman", "minecraft:taiga");
        biomeTag(resources, "angel_enderman", "minecraft:cherry_grove");
        biomeTag(resources, "void_enderman", "#minecraft:is_end");
        biomeTag(resources, "bogged_enderman", "minecraft:swamp");
        biomeTag(resources, "skeleton_enderman", "minecraft:plains", "minecraft:dark_forest");
        biomeTag(resources, "zombie_enderman", "minecraft:plains", "minecraft:forest");
        biomeTag(resources, "sculk_enderman", "minecraft:deep_dark");
        biomeTag(resources, "husk_enderman", "minecraft:desert", "minecraft:badlands");
        biomeTag(resources, "vampire_enderman", "minecraft:dark_forest");
        biomeTag(resources, "wither_enderman", "minecraft:soul_sand_valley");
        biomeTag(resources, "white_enderman", "minecraft:jungle");
        biomeTag(resources, "soul_sand_enderman", "minecraft:soul_sand_valley");
        resources.put("data/enderism/neoforge/biome_modifier/husk_enderman_desert_spawns.json", spawns("husk", values("minecraft:desert"), 12));
        resources.put("data/enderism/neoforge/biome_modifier/husk_enderman_badlands_spawns.json", spawns("husk", values("minecraft:badlands"), 8));
        resources.put("data/minecraft/tags/entity_type/burn_in_daylight.json", tag("enderism:skeleton_enderman", "enderism:zombie_enderman"));

        resources.put("data/minecraft/tags/entity_type/illager_friends.json", tag("enderism:ravager_enderman"));
        resources.put("data/minecraft/tags/entity_type/raiders.json", tag("enderism:ravager_enderman"));

        JsonObject codexRecipe = new JsonObject();
        codexRecipe.addProperty("type", "minecraft:crafting_shapeless");
        codexRecipe.addProperty("category", "misc");
        codexRecipe.add("ingredients", values("minecraft:book", "minecraft:ender_pearl"));
        JsonObject codexResult = new JsonObject();
        codexResult.addProperty("id", "enderism:ender_codex");
        codexResult.addProperty("count", 1);
        codexRecipe.add("result", codexResult);
        resources.put("data/enderism/recipe/ender_codex.json", codexRecipe);

        JsonObject judgment = new JsonObject();
        judgment.addProperty("effects", "freezing");
        judgment.addProperty("exhaustion", 0.0);
        judgment.addProperty("message_id", "enderism.winters_judgment");
        judgment.addProperty("scaling", "never");
        resources.put("data/enderism/damage_type/winters_judgment.json", judgment);
        for (String tag : List.of("bypasses_armor", "bypasses_cooldown", "bypasses_effects", "bypasses_enchantments",
                "bypasses_resistance", "is_freezing", "no_knockback")) {
            resources.put("data/minecraft/tags/damage_type/" + tag + ".json", tag("enderism:winters_judgment"));
        }
        return EnderismDataGenerators.save(cache, this.output, resources);
    }

    private static JsonObject endermanLoot() {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:loot_table");
        entry.addProperty("value", "minecraft:entities/enderman");
        JsonArray entries = new JsonArray();
        entries.add(entry);
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.add("entries", entries);
        JsonArray pools = new JsonArray();
        pools.add(pool);
        JsonObject loot = new JsonObject();
        loot.addProperty("type", "minecraft:entity");
        loot.add("pools", pools);
        return loot;
    }

    private static JsonObject spawns(String variant, JsonElement biomes, int weight) {
        JsonObject spawner = new JsonObject();
        spawner.addProperty("type", "enderism:" + variant + "_enderman");
        spawner.addProperty("weight", weight);
        spawner.addProperty("minCount", 1);
        spawner.addProperty("maxCount", variant.equals("soul_sand") ? 2 : 1);
        JsonObject modifier = new JsonObject();
        modifier.addProperty("type", "neoforge:add_spawns");
        modifier.add("biomes", biomes);
        modifier.add("spawners", spawner);
        return modifier;
    }

    private static void biomeTag(Map<String, JsonElement> resources, String suffix, String... biomes) {
        resources.put("data/enderism/tags/worldgen/biome/spawns_" + suffix + ".json", tag(biomes));
    }

    private static JsonObject tag(String... entries) {
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", values(entries));
        return tag;
    }

    private static JsonArray values(String... entries) {
        JsonArray values = new JsonArray();
        for (String entry : entries) values.add(entry);
        return values;
    }

    @Override
    public String getName() { return "Enderism server data"; }
}
