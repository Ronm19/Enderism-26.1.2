package com.ronm19.enderism;

import com.ronm19.enderism.entity.BrownEnderman;
import com.ronm19.enderism.entity.GrayEnderman;
import com.ronm19.enderism.entity.ArcticEnderman;
import com.ronm19.enderism.entity.FireEnderman;
import com.ronm19.enderism.entity.FrostEnderman;
import com.ronm19.enderism.effect.ArcticStun;
import com.ronm19.enderism.registry.ModCreativeModeTabs;
import com.ronm19.enderism.registry.ModEntities;
import com.ronm19.enderism.registry.ModItems;
import com.ronm19.enderism.registry.ModEffects;
import com.ronm19.enderism.spawning.EnderismSpawnForcer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

@Mod(Enderism.MOD_ID)
public final class Enderism {
    public static final String MOD_ID = "enderism";

    public Enderism(IEventBus modEventBus, ModContainer container) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModCreativeModeTabs.TABS.register(modEventBus);
        modEventBus.addListener(Enderism::registerAttributes);
        modEventBus.addListener(Enderism::registerSpawnPlacements);
        modEventBus.addListener(Enderism::addCreativeItems);
        container.registerConfig(ModConfig.Type.COMMON, EnderismConfig.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, ArcticEndermanConfig.SPEC, "enderism-arctic-common.toml");
        NeoForge.EVENT_BUS.addListener(Enderism::protectEndermenFromWater);
        NeoForge.EVENT_BUS.addListener(EnderismSpawnForcer::addToLevel);
        ArcticStun.registerEvents(NeoForge.EVENT_BUS);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.BROWN_ENDERMAN.get(), BrownEnderman.createAttributes().build());
        event.put(ModEntities.GRAY_ENDERMAN.get(), GrayEnderman.createAttributes().build());
        event.put(ModEntities.ARCTIC_ENDERMAN.get(), ArcticEnderman.createAttributes().build());
        event.put(ModEntities.FIRE_ENDERMAN.get(), FireEnderman.createAttributes().build());
        event.put(ModEntities.FROST_ENDERMAN.get(), FrostEnderman.createAttributes().build());
    }

    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.BROWN_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BrownEnderman::checkBrownSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.GRAY_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, GrayEnderman::checkGraySpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.ARCTIC_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ArcticEnderman::checkArcticSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.FIRE_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, FireEnderman::checkFireSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.FROST_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, FrostEnderman::checkFrostSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) {
            event.accept(ModItems.BROWN_ENDERMAN_SPAWN_EGG);
            event.accept(ModItems.GRAY_ENDERMAN_SPAWN_EGG);
            event.accept(ModItems.ARCTIC_ENDERMAN_SPAWN_EGG);
            event.accept(ModItems.FIRE_ENDERMAN_SPAWN_EGG);
            event.accept(ModItems.FROST_ENDERMAN_SPAWN_EGG);
        }
    }

    private static void protectEndermenFromWater(EntityInvulnerabilityCheckEvent event) {
        if (!(event.getEntity() instanceof EnderMan)) {
            return;
        }

        // Enderman rain sensitivity and drowning both use this damage type.
        boolean waterDamage = event.getSource().is(DamageTypes.DROWN);
        if (event.getSource().getDirectEntity() instanceof AbstractThrownPotion potion) {
            waterDamage |= potion.getItem()
                    .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                    .is(Potions.WATER);
        }
        if (waterDamage) {
            // This event runs before EnderMan's damage-triggered teleport response.
            event.setInvulnerable(true);
        }
    }
}
