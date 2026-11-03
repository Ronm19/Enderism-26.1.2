package com.ronm19.enderism.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Client assets and the existing empty mixin configuration, generated without reading resource files. */
public final class EnderismAssetsProvider implements DataProvider {
    private final PackOutput output;

    public EnderismAssetsProvider(PackOutput output) { this.output = output; }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<String, JsonElement> resources = new LinkedHashMap<>();
        JsonObject language = new JsonObject();
        for (String variant : EnderismDataGenerators.VARIANTS) {
            String entity = variant + "_enderman";
            String egg = entity + "_spawn_egg";
            String displayName = variant.substring(0, 1).toUpperCase(Locale.ROOT) + variant.substring(1) + " Enderman";
            language.addProperty("entity.enderism." + entity, displayName);
            language.addProperty("item.enderism." + egg, displayName + " Spawn Egg");

            JsonObject model = new JsonObject();
            model.addProperty("type", "minecraft:model");
            model.addProperty("model", "enderism:item/" + egg);
            JsonObject item = new JsonObject();
            item.add("model", model);
            resources.put("assets/enderism/items/" + egg + ".json", item);

            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", "enderism:item/" + egg);
            JsonObject itemModel = new JsonObject();
            itemModel.addProperty("parent", "minecraft:item/generated");
            itemModel.add("textures", textures);
            resources.put("assets/enderism/models/item/" + egg + ".json", itemModel);
        }
        language.addProperty("itemGroup.enderism.spawn_eggs", "Enderism Spawn Eggs");
        language.addProperty("entity.enderism.storm_lightning", "Storm Lightning");
        language.addProperty("effect.enderism.arctic_stun", "Absolute Zero");
        language.addProperty("effect.enderism.frost_paralysis", "Frost Paralysis");
        language.addProperty("effect.enderism.forest_roots", "Entangling Vines");
        language.addProperty("effect.enderism.armor_fracture", "Armor Fracture");
        for (String suffix : new String[] {"", ".player", ".item"}) {
            language.addProperty("death.attack.enderism.winters_judgment" + suffix,
                    "%1$s froze beneath %2$s's silent judgment");
        }
        resources.put("assets/enderism/lang/en_us.json", language);
        resources.put("assets/enderism/post_effect/brown_hunters_gaze.json", JsonParser.parseString("""
                {
                  "targets": { "swap": {} },
                  "passes": [
                    {
                      "vertex_shader": "minecraft:core/screenquad",
                      "fragment_shader": "minecraft:post/color_convolve",
                      "inputs": [{ "sampler_name": "In", "target": "minecraft:main" }],
                      "output": "swap",
                      "uniforms": {
                        "ColorConfig": [
                          { "name": "RedMatrix", "type": "vec3", "value": [0.3, 0.59, 0.11] },
                          { "name": "GreenMatrix", "type": "vec3", "value": [0.3, 0.59, 0.11] },
                          { "name": "BlueMatrix", "type": "vec3", "value": [0.3, 0.59, 0.11] }
                        ]
                      }
                    },
                    {
                      "vertex_shader": "minecraft:core/screenquad",
                      "fragment_shader": "minecraft:post/blit",
                      "inputs": [{ "sampler_name": "In", "target": "swap" }],
                      "output": "minecraft:main",
                      "uniforms": {
                        "BlitConfig": [
                          { "name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0] }
                        ]
                      }
                    }
                  ]
                }
                """));
        resources.put("enderism.mixins.json", JsonParser.parseString("""
                {
                  "required": true,
                  "package": "com.ronm19.enderism.mixin",
                  "compatibilityLevel": "JAVA_25",
                  "mixins": [],
                  "injectors": { "defaultRequire": 1 },
                  "overwrites": { "requireAnnotations": true }
                }
                """));
        return EnderismDataGenerators.save(cache, this.output, resources);
    }

    @Override
    public String getName() { return "Enderism asset resources"; }
}
