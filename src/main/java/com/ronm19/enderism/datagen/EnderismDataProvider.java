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

/** Enderism's entity loot, biome spawning, and damage-type data. */
public final class EnderismDataProvider implements DataProvider {
    private final PackOutput output;

    public EnderismDataProvider(PackOutput output) { this.output = output; }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<String, JsonElement> resources = new LinkedHashMap<>();
        for (String variant : EnderismDataGenerators.VARIANTS) {
            resources.put("data/enderism/loot_table/entities/" + variant + "_enderman.json", endermanLoot());
        }

        Map<String, Integer> weights = Map.of(
                "brown", 30, "gray", 10, "arctic", 10, "frost", 10,
                "forest", 8, "storm", 8, "sand", 8, "silver", 8);
        for (String variant : EnderismDataGenerators.VARIANTS) {
            if (!variant.equals("fire")) {
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
        spawner.addProperty("maxCount", 1);
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
