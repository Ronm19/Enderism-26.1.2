package com.ronm19.enderism.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ronm19.enderism.codex.EnderCodex;
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
            String entity = EnderismDataGenerators.entityId(variant);
            String egg = entity + "_spawn_egg";
            String displayName = variant.equals("enderman_golem") ? "Enderman Golem"
                    : variant.equals("soul_sand") ? "Soul Sand Enderman"
                    : variant.substring(0, 1).toUpperCase(Locale.ROOT) + variant.substring(1) + " Enderman";
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
        language.addProperty("itemGroup.enderism.items", "Enderism Items");
        language.addProperty("item.enderism.ender_codex", "Ender Codex");
        language.addProperty("codex.enderism.title", "Ender Codex");
        language.addProperty("codex.enderism.index", "Index");
        language.addProperty("codex.enderism.all_species", "All 30 species");
        language.addProperty("codex.enderism.overview", "Overview");
        language.addProperty("codex.enderism.survival", "Survival notes");
        language.addProperty("codex.enderism.habitat", "Habitat");
        language.addProperty("codex.enderism.activity", "Activity");
        language.addProperty("codex.enderism.gender", "Gender");
        language.addProperty("codex.enderism.avoidance", "Avoidance");
        language.addProperty("codex.enderism.escape", "Escape");
        language.addProperty("codex.enderism.combat", "Combat");
        language.addProperty("codex.enderism.previous", "Previous");
        language.addProperty("codex.enderism.next", "Next");
        language.addProperty("codex.enderism.species", "Species");
        language.addProperty("codex.enderism.search", "Search species...");
        language.addProperty("codex.enderism.search_help", "Search by name, number or habitat (Ctrl+F)");
        language.addProperty("codex.enderism.clear_search", "Clear search");
        language.addProperty("codex.enderism.all_entries", "All species");
        language.addProperty("codex.enderism.results", "Results %s-%s of %s");
        language.addProperty("codex.enderism.no_results", "No matches");
        language.addProperty("codex.enderism.no_results_hint", "Try a name, number or habitat.");
        language.addProperty("codex.enderism.empty_detail", "Clear your search or choose a species from the list.");
        language.addProperty("codex.enderism.description", "Description");
        language.addProperty("codex.enderism.illustration", "Portrait");
        language.addProperty("codex.enderism.previous_species", "Previous species");
        language.addProperty("codex.enderism.next_species", "Next species");
        language.addProperty("codex.enderism.read_hint", "Scroll to read");
        EnderCodex.translations().forEach(language::addProperty);
        JsonObject codexModelRef = new JsonObject();
        codexModelRef.addProperty("type", "minecraft:model");
        codexModelRef.addProperty("model", "enderism:item/ender_codex");
        JsonObject codexItem = new JsonObject();
        codexItem.add("model", codexModelRef);
        resources.put("assets/enderism/items/ender_codex.json", codexItem);
        JsonObject codexModel = new JsonObject();
        codexModel.addProperty("parent", "minecraft:item/generated");
        JsonObject codexTextures = new JsonObject();
        codexTextures.addProperty("layer0", "enderism:item/ender_codex");
        codexModel.add("textures", codexTextures);
        resources.put("assets/enderism/models/item/ender_codex.json", codexModel);
        language.addProperty("entity.enderism.storm_lightning", "Storm Lightning");
        language.addProperty("entity.enderism.dark_magic_bolt", "Dark Magic Bolt");
        language.addProperty("entity.enderism.infernal_meteor", "Infernal Meteor");
        language.addProperty("entity.enderism.bogged_venom", "Bogged Venom");
        language.addProperty("entity.enderism.bogged_toxic_mire", "Toxic Mire");
        language.addProperty("entity.enderism.skeleton_arcane_arrow", "Skeletal Arcane Arrow");
        language.addProperty("effect.enderism.arctic_stun", "Absolute Zero");
        language.addProperty("effect.enderism.frost_paralysis", "Frost Paralysis");
        language.addProperty("effect.enderism.forest_roots", "Entangling Vines");
        language.addProperty("effect.enderism.armor_fracture", "Armor Fracture");
        language.addProperty("effect.enderism.golden_stun", "Golden Stun");
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
                  "mixins": ["raid.RaidMixin", "village.IronGolemMixin", "entity.VoidEndermanDamageMixin", "entity.WardenSculkAllyMixin"],
                  "client": ["client.EndermanParticleMixin"],
                  "injectors": { "defaultRequire": 1 },
                  "overwrites": { "requireAnnotations": true }
                }
                """));
        return EnderismDataGenerators.save(cache, this.output, resources);
    }

    @Override
    public String getName() { return "Enderism asset resources"; }
}
