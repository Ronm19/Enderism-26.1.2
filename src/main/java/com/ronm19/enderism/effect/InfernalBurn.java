package com.ronm19.enderism.effect;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.InfernalEndermanConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Modifies only native on-fire ticks during the recipient's temporary Infernal burn window. */
public final class InfernalBurn {
    public static final float DAMAGE_MULTIPLIER = 2.0F;
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Enderism.MOD_ID);
    private static final java.util.function.Supplier<AttachmentType<Long>> BURN_UNTIL =
            ATTACHMENTS.register("infernal_burn_until", () -> AttachmentType.builder(() -> 0L).build());

    private InfernalBurn() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(InfernalBurn::onFireDamage);
        NeoForge.EVENT_BUS.addListener(InfernalBurn::expireBurn);
    }

    public static void apply(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || target.fireImmune()
                || target.isInWaterOrRain() || target.hasEffect(MobEffects.FIRE_RESISTANCE)) return;
        int duration = InfernalEndermanConfig.burnTicks();
        target.igniteForTicks(duration);
        if (target.getRemainingFireTicks() > 0) {
            target.setData(BURN_UNTIL, level.getGameTime() + duration);
        }
    }

    private static void onFireDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypes.ON_FIRE)) return;
        LivingEntity target = event.getEntity();
        Long until = target.getExistingDataOrNull(BURN_UNTIL);
        if (until != null && target.level().getGameTime() <= until && target.getRemainingFireTicks() > 0
                && !target.isInWaterOrRain()) {
            event.setAmount(event.getAmount() * DAMAGE_MULTIPLIER);
        }
    }

    private static void expireBurn(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel level)) return;
        Long until = target.getExistingDataOrNull(BURN_UNTIL);
        if (until != null && (level.getGameTime() >= until || target.getRemainingFireTicks() <= 0
                || !target.isAlive() || target.isInWaterOrRain())) {
            target.removeData(BURN_UNTIL);
        }
    }
}
