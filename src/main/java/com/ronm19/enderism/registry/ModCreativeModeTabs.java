package com.ronm19.enderism.registry;

import com.ronm19.enderism.Enderism;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Enderism.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SPAWN_EGGS = TABS.register(
            "spawn_eggs", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.enderism.spawn_eggs"))
                    .icon(() -> ModItems.BROWN_ENDERMAN_SPAWN_EGG.get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((parameters, output) -> ModItems.ITEMS.getEntries().forEach(entry -> {
                        if (entry.get() instanceof SpawnEggItem egg) {
                            output.accept(egg);
                        }
                    }))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ITEMS = TABS.register(
            "items", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.enderism.items"))
                    .icon(() -> ModItems.ENDER_CODEX.get().getDefaultInstance())
                    .withTabsBefore(SPAWN_EGGS.getKey())
                    .displayItems((parameters, output) -> ModItems.ITEMS.getEntries().forEach(entry -> {
                        if (!(entry.get() instanceof SpawnEggItem)) output.accept(entry.get());
                    }))
                    .build());

    private ModCreativeModeTabs() {
    }
}
