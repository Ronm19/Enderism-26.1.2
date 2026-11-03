package com.ronm19.enderism.registry;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.BrownEnderman;
import com.ronm19.enderism.entity.GrayEnderman;
import com.ronm19.enderism.entity.ArcticEnderman;
import com.ronm19.enderism.entity.FireEnderman;
import com.ronm19.enderism.entity.FrostEnderman;
import com.ronm19.enderism.entity.ForestEnderman;
import com.ronm19.enderism.entity.StormEnderman;
import com.ronm19.enderism.entity.SandEnderman;
import com.ronm19.enderism.entity.SilverEnderman;
import com.ronm19.enderism.entity.StormLightning;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(Enderism.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<BrownEnderman>> BROWN_ENDERMAN =
            ENTITY_TYPES.registerEntityType("brown_enderman", BrownEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .notInPeaceful());
    public static final DeferredHolder<EntityType<?>, EntityType<GrayEnderman>> GRAY_ENDERMAN =
            ENTITY_TYPES.registerEntityType("gray_enderman", GrayEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<ArcticEnderman>> ARCTIC_ENDERMAN =
            ENTITY_TYPES.registerEntityType("arctic_enderman", ArcticEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<FireEnderman>> FIRE_ENDERMAN =
            ENTITY_TYPES.registerEntityType("fire_enderman", FireEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .fireImmune()
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<FrostEnderman>> FROST_ENDERMAN =
            ENTITY_TYPES.registerEntityType("frost_enderman", FrostEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<ForestEnderman>> FOREST_ENDERMAN =
            ENTITY_TYPES.registerEntityType("forest_enderman", ForestEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<StormEnderman>> STORM_ENDERMAN =
            ENTITY_TYPES.registerEntityType("storm_enderman", StormEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<StormLightning>> STORM_LIGHTNING =
            ENTITY_TYPES.registerEntityType("storm_lightning", StormLightning::new, MobCategory.MISC,
                    builder -> builder.noSave().noSummon().noLootTable().sized(0.0F, 0.0F)
                            .clientTrackingRange(16).updateInterval(Integer.MAX_VALUE));

    public static final DeferredHolder<EntityType<?>, EntityType<SandEnderman>> SAND_ENDERMAN =
            ENTITY_TYPES.registerEntityType("sand_enderman", SandEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<SilverEnderman>> SILVER_ENDERMAN =
            ENTITY_TYPES.registerEntityType("silver_enderman", SilverEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    private ModEntities() {
    }
}
