package com.ronm19.enderism;

import com.ronm19.enderism.entity.BrownEnderman;
import com.ronm19.enderism.entity.GrayEnderman;
import com.ronm19.enderism.entity.ArcticEnderman;
import com.ronm19.enderism.entity.FireEnderman;
import com.ronm19.enderism.entity.FrostEnderman;
import com.ronm19.enderism.entity.ForestEnderman;
import com.ronm19.enderism.entity.StormEnderman;
import com.ronm19.enderism.entity.SandEnderman;
import com.ronm19.enderism.entity.SilverEnderman;
import com.ronm19.enderism.entity.PhantomEnderman;
import com.ronm19.enderism.entity.DarkEnderman;
import com.ronm19.enderism.entity.InfernalEnderman;
import com.ronm19.enderism.entity.GoldenEnderman;
import com.ronm19.enderism.entity.NightmareEnderman;
import com.ronm19.enderism.entity.DemonEnderman;
import com.ronm19.enderism.entity.BloodEnderman;
import com.ronm19.enderism.entity.SpectralEnderman;
import com.ronm19.enderism.entity.AngelEnderman;
import com.ronm19.enderism.entity.RavagerEnderman;
import com.ronm19.enderism.entity.EndermanGolem;
import com.ronm19.enderism.entity.VoidEnderman;
import com.ronm19.enderism.entity.BoggedEnderman;
import com.ronm19.enderism.entity.SkeletonEnderman;
import com.ronm19.enderism.entity.ZombieEnderman;
import com.ronm19.enderism.entity.SculkEnderman;
import com.ronm19.enderism.entity.HuskEnderman;
import com.ronm19.enderism.entity.VampireEnderman;
import com.ronm19.enderism.entity.WitherEnderman;
import com.ronm19.enderism.entity.WhiteEnderman;
import com.ronm19.enderism.entity.SoulSandEnderman;
import com.ronm19.enderism.combat.SoulfireCombat;
import com.ronm19.enderism.spawning.SoulSandEndermanSpawns;
import com.ronm19.enderism.spawning.WhiteEndermanSpawns;
import com.ronm19.enderism.spawning.WitherEndermanSpawns;
import com.ronm19.enderism.spawning.VampireEndermanSpawns;
import com.ronm19.enderism.spawning.HuskEndermanSpawns;
import com.ronm19.enderism.spawning.SculkEndermanSpawns;
import com.ronm19.enderism.combat.ZombieHordeSignal;
import com.ronm19.enderism.spawning.ZombieEndermanSpawns;
import com.ronm19.enderism.spawning.SkeletonEndermanSpawns;
import com.ronm19.enderism.spawning.BoggedEndermanSpawns;
import com.ronm19.enderism.effect.ArmorFracture;
import com.ronm19.enderism.effect.ArcticStun;
import com.ronm19.enderism.effect.InfernalBurn;
import com.ronm19.enderism.effect.DemonAsh;
import com.ronm19.enderism.combat.EndermanCombatBalance;
import com.ronm19.enderism.entity.ai.SpectralAmbush;
import com.ronm19.enderism.registry.ModCreativeModeTabs;
import com.ronm19.enderism.registry.ModEntities;
import com.ronm19.enderism.registry.ModItems;
import com.ronm19.enderism.registry.ModEffects;
import com.ronm19.enderism.spawning.EnderismSpawnForcer;
import com.ronm19.enderism.spawning.RavagerOutpostSpawns;
import com.ronm19.enderism.spawning.GolemVillageSpawns;
import com.ronm19.enderism.spawning.VoidEndermanSpawns;
import com.ronm19.enderism.raid.RavagerRaids;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
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
        container.registerConfig(ModConfig.Type.COMMON, EnderismConfig.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, CombatBalanceConfig.SPEC, "enderism-combat-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, GroupTacticsConfig.SPEC, "enderism-groups-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, ArcticEndermanConfig.SPEC, "enderism-arctic-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, FireEndermanConfig.COMMON_SPEC, "enderism-fire-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, FrostEndermanConfig.SPEC, "enderism-frost-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, ForestEndermanConfig.SPEC, "enderism-forest-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, StormEndermanConfig.SPEC, "enderism-storm-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, SandEndermanConfig.SPEC, "enderism-sand-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, SilverEndermanConfig.SPEC, "enderism-silver-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, PhantomEndermanConfig.SPEC, "enderism-phantom-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, DarkEndermanConfig.SPEC, "enderism-dark-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, InfernalEndermanConfig.SPEC, "enderism-infernal-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, InfernalMeteorConfig.SPEC, "enderism-infernal-meteors-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, GoldenEndermanConfig.SPEC, "enderism-golden-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, NightmareEndermanConfig.SPEC, "enderism-nightmare-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, DemonEndermanConfig.SPEC, "enderism-demon-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, BloodEndermanConfig.SPEC, "enderism-blood-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, SpectralEndermanConfig.SPEC, "enderism-spectral-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, AngelEndermanConfig.SPEC, "enderism-angel-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, RavagerEndermanConfig.SPEC, "enderism-ravager-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, EndermanGolemConfig.SPEC, "enderism-golem-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, VoidEndermanConfig.SPEC, "enderism-void-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, BoggedEndermanConfig.SPEC, "enderism-bogged-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, SkeletonEndermanConfig.SPEC, "enderism-skeleton-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, ZombieEndermanConfig.SPEC, "enderism-zombie-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, SculkEndermanConfig.SPEC, "enderism-sculk-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, HuskEndermanConfig.SPEC, "enderism-husk-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, VampireEndermanConfig.SPEC, "enderism-vampire-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, WitherEndermanConfig.SPEC, "enderism-wither-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, WhiteEndermanConfig.SPEC, "enderism-white-common.toml");
        container.registerConfig(ModConfig.Type.COMMON, SoulSandEndermanConfig.SPEC, "enderism-soul-sand-common.toml");
        SoulfireCombat.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(Enderism::protectEndermenFromWater);
        NeoForge.EVENT_BUS.addListener(EnderismSpawnForcer::addToLevel);
        EndermanCombatBalance.registerEvents(NeoForge.EVENT_BUS);
        FireEnderman.registerEvents(NeoForge.EVENT_BUS);
        ArcticStun.registerEvents(NeoForge.EVENT_BUS);
        ArmorFracture.registerEvents(NeoForge.EVENT_BUS);
        InfernalBurn.register(modEventBus);
        DemonAsh.registerEvents(NeoForge.EVENT_BUS);
        SpectralAmbush.registerEvents(NeoForge.EVENT_BUS);
        AngelEnderman.registerEvents(NeoForge.EVENT_BUS);
        VampireEnderman.registerEvents(NeoForge.EVENT_BUS);
        WitherEnderman.registerEvents(NeoForge.EVENT_BUS);
        WhiteEnderman.registerEvents(NeoForge.EVENT_BUS);
        SoulSandEnderman.registerEvents(NeoForge.EVENT_BUS);
        RavagerOutpostSpawns.registerEvents(NeoForge.EVENT_BUS);
        RavagerRaids.registerEvents(NeoForge.EVENT_BUS);
        GolemVillageSpawns.registerEvents(NeoForge.EVENT_BUS);
        EndermanGolem.registerEvents(NeoForge.EVENT_BUS);
        VoidEndermanSpawns.registerEvents(NeoForge.EVENT_BUS);
        BoggedEndermanSpawns.registerEvents(NeoForge.EVENT_BUS);
        SkeletonEndermanSpawns.registerEvents(NeoForge.EVENT_BUS);
        ZombieEndermanSpawns.registerEvents(NeoForge.EVENT_BUS);
        ZombieHordeSignal.registerEvents(NeoForge.EVENT_BUS);
        SculkEndermanSpawns.registerEvents(NeoForge.EVENT_BUS);
        HuskEndermanSpawns.registerEvents(NeoForge.EVENT_BUS);
        VampireEndermanSpawns.registerEvents(NeoForge.EVENT_BUS);
        WitherEndermanSpawns.registerEvents(NeoForge.EVENT_BUS);
        WhiteEndermanSpawns.registerEvents(NeoForge.EVENT_BUS);
        SoulSandEndermanSpawns.registerEvents(NeoForge.EVENT_BUS);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.BROWN_ENDERMAN.get(), BrownEnderman.createAttributes().build());
        event.put(ModEntities.GRAY_ENDERMAN.get(), GrayEnderman.createAttributes().build());
        event.put(ModEntities.ARCTIC_ENDERMAN.get(), ArcticEnderman.createAttributes().build());
        event.put(ModEntities.FIRE_ENDERMAN.get(), FireEnderman.createAttributes().build());
        event.put(ModEntities.FROST_ENDERMAN.get(), FrostEnderman.createAttributes().build());
        event.put(ModEntities.FOREST_ENDERMAN.get(), ForestEnderman.createAttributes().build());
        event.put(ModEntities.STORM_ENDERMAN.get(), StormEnderman.createAttributes().build());
        event.put(ModEntities.SAND_ENDERMAN.get(), SandEnderman.createAttributes().build());
        event.put(ModEntities.SILVER_ENDERMAN.get(), SilverEnderman.createAttributes().build());
        event.put(ModEntities.PHANTOM_ENDERMAN.get(), PhantomEnderman.createAttributes().build());
        event.put(ModEntities.DARK_ENDERMAN.get(), DarkEnderman.createAttributes().build());
        event.put(ModEntities.INFERNAL_ENDERMAN.get(), InfernalEnderman.createAttributes().build());
        event.put(ModEntities.GOLDEN_ENDERMAN.get(), GoldenEnderman.createAttributes().build());
        event.put(ModEntities.NIGHTMARE_ENDERMAN.get(), NightmareEnderman.createAttributes().build());
        event.put(ModEntities.DEMON_ENDERMAN.get(), DemonEnderman.createAttributes().build());
        event.put(ModEntities.BLOOD_ENDERMAN.get(), BloodEnderman.createAttributes().build());
        event.put(ModEntities.SPECTRAL_ENDERMAN.get(), SpectralEnderman.createAttributes().build());
        event.put(ModEntities.ANGEL_ENDERMAN.get(), AngelEnderman.createAttributes().build());
        event.put(ModEntities.RAVAGER_ENDERMAN.get(), RavagerEnderman.createAttributes().build());
        event.put(ModEntities.ENDERMAN_GOLEM.get(), EndermanGolem.createAttributes().build());
        event.put(ModEntities.VOID_ENDERMAN.get(), VoidEnderman.createAttributes().build());
        event.put(ModEntities.BOGGED_ENDERMAN.get(), BoggedEnderman.createAttributes().build());
        event.put(ModEntities.SKELETON_ENDERMAN.get(), SkeletonEnderman.createAttributes().build());
        event.put(ModEntities.ZOMBIE_ENDERMAN.get(), ZombieEnderman.createAttributes().build());
        event.put(ModEntities.SCULK_ENDERMAN.get(), SculkEnderman.createAttributes().build());
        event.put(ModEntities.HUSK_ENDERMAN.get(), HuskEnderman.createAttributes().build());
        event.put(ModEntities.VAMPIRE_ENDERMAN.get(), VampireEnderman.createAttributes().build());
        event.put(ModEntities.WITHER_ENDERMAN.get(), WitherEnderman.createAttributes().build());
        event.put(ModEntities.WHITE_ENDERMAN.get(), WhiteEnderman.createAttributes().build());
        event.put(ModEntities.SOUL_SAND_ENDERMAN.get(), SoulSandEnderman.createAttributes().build());
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
        event.register(ModEntities.FOREST_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ForestEnderman::checkForestSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.STORM_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, StormEnderman::checkStormSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.SAND_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SandEnderman::checkSandSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.SILVER_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SilverEnderman::checkSilverSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.PHANTOM_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PhantomEnderman::checkPhantomSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.DARK_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, DarkEnderman::checkDarkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.INFERNAL_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, InfernalEnderman::checkInfernalSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.GOLDEN_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, GoldenEnderman::checkGoldenSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.NIGHTMARE_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, NightmareEnderman::checkNightmareSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.DEMON_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, DemonEnderman::checkDemonSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.BLOOD_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BloodEnderman::checkBloodSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.SPECTRAL_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SpectralEnderman::checkSpectralSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.ANGEL_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, AngelEnderman::checkAngelSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.RAVAGER_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, RavagerEnderman::checkRavagerSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.ENDERMAN_GOLEM.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndermanGolem::checkGolemSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.VOID_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, VoidEnderman::checkVoidSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.BOGGED_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BoggedEnderman::checkBoggedSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.SKELETON_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SkeletonEnderman::checkSkeletonSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.VAMPIRE_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, VampireEndermanSpawns::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.WITHER_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, WitherEndermanSpawns::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.WHITE_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, WhiteEndermanSpawns::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.HUSK_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, HuskEndermanSpawns::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.SCULK_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SculkEndermanSpawns::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.ZOMBIE_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ZombieEnderman::checkZombieSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.SOUL_SAND_ENDERMAN.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SoulSandEndermanSpawns::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
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
