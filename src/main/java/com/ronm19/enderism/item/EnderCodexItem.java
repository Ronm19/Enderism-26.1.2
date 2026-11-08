package com.ronm19.enderism.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** An ordinary reusable item; opening its local reader never mutates progression or server state. */
public final class EnderCodexItem extends Item {
    public EnderCodexItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) NeoForge.EVENT_BUS.post(new OpenEvent());
        return InteractionResult.SUCCESS;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) NeoForge.EVENT_BUS.post(new OpenEvent());
        return InteractionResult.SUCCESS;
    }

    /** Common event contains no client classes, keeping dedicated-server classloading safe. */
    public static final class OpenEvent extends Event {}
}
