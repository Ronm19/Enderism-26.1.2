package com.ronm19.enderism.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;

/** Native client lightning presentation, without vanilla server block or entity side effects. */
public final class StormLightning extends LightningBolt {
    public StormLightning(EntityType<? extends StormLightning> type, Level level) {
        super(type, level);
        this.setVisualOnly(true);
    }

    @Override
    public void tick() {
        if (this.level().isClientSide()) super.tick();
        else if (this.tickCount >= 60) this.discard();
        // Native LightningBolt's server tick powers rods and changes copper even when visualOnly is true.
        // All Storm damage is explicitly resolved once by the owning Brain instead.
    }
}
