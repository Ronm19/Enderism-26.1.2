package com.ronm19.enderism.client;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.item.EnderCodexItem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Client-only reader registration; the reusable item and its event remain server safe. */
@EventBusSubscriber(modid = Enderism.MOD_ID, value = Dist.CLIENT)
public final class EnderCodexClient {
    private EnderCodexClient() {}

    @SubscribeEvent public static void open(EnderCodexItem.OpenEvent event) {
        Minecraft.getInstance().setScreen(new EnderCodexScreen());
    }
}
