package com.ronm19.enderism.datagen;

import com.google.gson.JsonElement;
import com.ronm19.enderism.Enderism;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** One datagen entry point for every Enderism JSON resource. */
@EventBusSubscriber(modid = Enderism.MOD_ID)
public final class EnderismDataGenerators {
    public static final List<String> VARIANTS = List.of(
            "brown", "gray", "arctic", "fire", "frost", "forest", "storm", "sand", "silver");

    private EnderismDataGenerators() {}

    @SubscribeEvent
    public static void gather(GatherDataEvent.Client event) {
        event.createProvider(EnderismAssetsProvider::new);
        event.createProvider(EnderismDataProvider::new);
    }

    static CompletableFuture<?> save(CachedOutput cache, PackOutput output, Map<String, JsonElement> resources) {
        Path root = output.getOutputFolder().toAbsolutePath().normalize();
        return CompletableFuture.allOf(resources.entrySet().stream().map(entry -> {
            Path target = root.resolve(entry.getKey()).normalize();
            if (!target.startsWith(root) || !entry.getKey().endsWith(".json"))
                throw new IllegalArgumentException("Invalid generated JSON resource: " + entry.getKey());
            return DataProvider.saveStable(cache, entry.getValue(), target);
        }).toArray(CompletableFuture[]::new));
    }
}
