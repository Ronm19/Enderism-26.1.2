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
import com.ronm19.enderism.entity.projectile.WitherBarrageSkull;
import com.ronm19.enderism.entity.projectile.SkeletonArcaneArrow;
import com.ronm19.enderism.entity.BoggedToxicMire;
import com.ronm19.enderism.entity.projectile.BoggedVenom;
import com.ronm19.enderism.entity.projectile.BloodLance;
import com.ronm19.enderism.entity.projectile.InfernalMeteor;
import com.ronm19.enderism.entity.projectile.DarkMagicBolt;
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

    public static final DeferredHolder<EntityType<?>, EntityType<PhantomEnderman>> PHANTOM_ENDERMAN =
            ENTITY_TYPES.registerEntityType("phantom_enderman", PhantomEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<DarkEnderman>> DARK_ENDERMAN =
            ENTITY_TYPES.registerEntityType("dark_enderman", DarkEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<DarkMagicBolt>> DARK_MAGIC_BOLT =
            ENTITY_TYPES.registerEntityType("dark_magic_bolt", DarkMagicBolt::new, MobCategory.MISC,
                    builder -> builder.noSave().noSummon().noLootTable().sized(0.25F, 0.25F)
                            .clientTrackingRange(8).updateInterval(1));

    public static final DeferredHolder<EntityType<?>, EntityType<InfernalEnderman>> INFERNAL_ENDERMAN =
            ENTITY_TYPES.registerEntityType("infernal_enderman", InfernalEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.68F, 3.2F)
                            .eyeHeight(2.65F)
                            .passengerAttachments(2.9F)
                            .clientTrackingRange(8)
                            .fireImmune()
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<InfernalMeteor>> INFERNAL_METEOR =
            ENTITY_TYPES.registerEntityType("infernal_meteor", InfernalMeteor::new, MobCategory.MISC,
                    builder -> builder.noSave().noSummon().noLootTable().fireImmune().sized(0.7F, 0.7F)
                            .clientTrackingRange(10).updateInterval(2));

    public static final DeferredHolder<EntityType<?>, EntityType<GoldenEnderman>> GOLDEN_ENDERMAN =
            ENTITY_TYPES.registerEntityType("golden_enderman", GoldenEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<NightmareEnderman>> NIGHTMARE_ENDERMAN =
            ENTITY_TYPES.registerEntityType("nightmare_enderman", NightmareEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F)
                            .eyeHeight(2.55F)
                            .passengerAttachments(2.80625F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<DemonEnderman>> DEMON_ENDERMAN =
            ENTITY_TYPES.registerEntityType("demon_enderman", DemonEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.68F, 3.2F)
                            .eyeHeight(2.65F)
                            .passengerAttachments(2.9F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<BloodEnderman>> BLOOD_ENDERMAN =
            ENTITY_TYPES.registerEntityType("blood_enderman", BloodEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 3.2F)
                            .eyeHeight(2.65F)
                            .passengerAttachments(2.9F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<BloodLance>> BLOOD_LANCE =
            ENTITY_TYPES.registerEntityType("blood_lance", BloodLance::new, MobCategory.MISC,
                    builder -> builder.noSave().noSummon().noLootTable().sized(0.25F, 0.25F)
                            .clientTrackingRange(8).updateInterval(1));

    public static final DeferredHolder<EntityType<?>, EntityType<SpectralEnderman>> SPECTRAL_ENDERMAN =
            ENTITY_TYPES.registerEntityType("spectral_enderman", SpectralEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 3.1F)
                            .eyeHeight(2.65F)
                            .passengerAttachments(2.9F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<AngelEnderman>> ANGEL_ENDERMAN =
            ENTITY_TYPES.registerEntityType("angel_enderman", AngelEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 3.4F)
                            .eyeHeight(2.85F)
                            .passengerAttachments(3.0F)
                            .clientTrackingRange(8)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<RavagerEnderman>> RAVAGER_ENDERMAN =
            ENTITY_TYPES.registerEntityType("ravager_enderman", RavagerEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(1.25F, 3.6F)
                            .eyeHeight(3.0F)
                            .passengerAttachments(3.2F)
                            .clientTrackingRange(10)
                            .notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<EndermanGolem>> ENDERMAN_GOLEM =
            ENTITY_TYPES.registerEntityType("enderman_golem", EndermanGolem::new, MobCategory.MONSTER,
                    builder -> builder.sized(2.2F, 6.4F)
                            .eyeHeight(5.8F)
                            .passengerAttachments(5.9F)
                            .clientTrackingRange(12));

    public static final DeferredHolder<EntityType<?>, EntityType<VoidEnderman>> VOID_ENDERMAN =
            ENTITY_TYPES.registerEntityType("void_enderman", VoidEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 3.4F).eyeHeight(2.95F)
                            .passengerAttachments(3.0F).clientTrackingRange(10).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<BoggedEnderman>> BOGGED_ENDERMAN =
            ENTITY_TYPES.registerEntityType("bogged_enderman", BoggedEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 3.3F).eyeHeight(2.85F).passengerAttachments(3.05F)
                            .clientTrackingRange(8).notInPeaceful());
    public static final DeferredHolder<EntityType<?>, EntityType<BoggedVenom>> BOGGED_VENOM =
            ENTITY_TYPES.registerEntityType("bogged_venom", BoggedVenom::new, MobCategory.MISC,
                    builder -> builder.noSave().noSummon().noLootTable().sized(0.25F, 0.25F)
                            .clientTrackingRange(8).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<BoggedToxicMire>> BOGGED_TOXIC_MIRE =
            ENTITY_TYPES.registerEntityType("bogged_toxic_mire", BoggedToxicMire::new, MobCategory.MISC,
                    builder -> builder.noSave().noSummon().noLootTable().sized(0.5F, 0.5F)
                            .clientTrackingRange(8).updateInterval(10));

    public static final DeferredHolder<EntityType<?>, EntityType<SkeletonEnderman>> SKELETON_ENDERMAN =
            ENTITY_TYPES.registerEntityType("skeleton_enderman", SkeletonEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 2.9F).eyeHeight(2.55F).passengerAttachments(2.80625F)
                            .clientTrackingRange(10).notInPeaceful());
    public static final DeferredHolder<EntityType<?>, EntityType<SkeletonArcaneArrow>> SKELETON_ARCANE_ARROW =
            ENTITY_TYPES.registerEntityType("skeleton_arcane_arrow", SkeletonArcaneArrow::new, MobCategory.MISC,
                    builder -> builder.noSave().noSummon().noLootTable().sized(0.5F, 0.5F)
                            .clientTrackingRange(10).updateInterval(1));

    public static final DeferredHolder<EntityType<?>, EntityType<ZombieEnderman>> ZOMBIE_ENDERMAN =
            ENTITY_TYPES.registerEntityType("zombie_enderman", ZombieEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 3.4F).eyeHeight(2.95F).passengerAttachments(3.05F)
                            .clientTrackingRange(10).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<SculkEnderman>> SCULK_ENDERMAN =
            ENTITY_TYPES.registerEntityType("sculk_enderman", SculkEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 3.4F).eyeHeight(2.95F).passengerAttachments(3.05F)
                            .clientTrackingRange(10).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<HuskEnderman>> HUSK_ENDERMAN =
            ENTITY_TYPES.registerEntityType("husk_enderman", HuskEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 3.4F).eyeHeight(2.95F).passengerAttachments(3.05F)
                            .clientTrackingRange(10).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<VampireEnderman>> VAMPIRE_ENDERMAN =
            ENTITY_TYPES.registerEntityType("vampire_enderman", VampireEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 3.4F).eyeHeight(2.95F).passengerAttachments(3.05F)
                            .clientTrackingRange(10).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<WitherEnderman>> WITHER_ENDERMAN =
            ENTITY_TYPES.registerEntityType("wither_enderman", WitherEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 3.6F).eyeHeight(3.1F).passengerAttachments(3.2F)
                            .clientTrackingRange(10).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<WitherBarrageSkull>> WITHER_BARRAGE_SKULL =
            ENTITY_TYPES.registerEntityType("wither_barrage_skull", WitherBarrageSkull::new, MobCategory.MISC,
                    builder -> builder.noSave().noSummon().noLootTable().sized(0.3125F, 0.3125F)
                            .clientTrackingRange(10).updateInterval(1));

    public static final DeferredHolder<EntityType<?>, EntityType<WhiteEnderman>> WHITE_ENDERMAN =
            ENTITY_TYPES.registerEntityType("white_enderman", WhiteEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 3.4F).eyeHeight(2.95F).passengerAttachments(3.05F)
                            .clientTrackingRange(10).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<SoulSandEnderman>> SOUL_SAND_ENDERMAN =
            ENTITY_TYPES.registerEntityType("soul_sand_enderman", SoulSandEnderman::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 3.0F).eyeHeight(2.55F).passengerAttachments(2.8F)
                            .clientTrackingRange(10).notInPeaceful());

    private ModEntities() {
    }
}
