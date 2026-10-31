package com.ronm19.enderism.registry;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.BrownEnderman;
import com.ronm19.enderism.entity.GrayEnderman;
import com.ronm19.enderism.entity.ArcticEnderman;
import com.ronm19.enderism.entity.FireEnderman;
import com.ronm19.enderism.entity.FrostEnderman;
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

    private ModEntities() {
    }
}
