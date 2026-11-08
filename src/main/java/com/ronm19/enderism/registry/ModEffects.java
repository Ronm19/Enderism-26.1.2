package com.ronm19.enderism.registry;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.effect.ArmorFracture;
import com.ronm19.enderism.effect.ArcticStun;
import com.ronm19.enderism.effect.FrostParalysis;
import com.ronm19.enderism.effect.ForestRoots;
import com.ronm19.enderism.effect.GoldenStun;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Enderism.MOD_ID);
    public static final DeferredHolder<MobEffect, ArcticStun> ARCTIC_STUN = EFFECTS.register("arctic_stun", ArcticStun::new);
    public static final DeferredHolder<MobEffect, FrostParalysis> FROST_PARALYSIS =
            EFFECTS.register("frost_paralysis", FrostParalysis::new);
    public static final DeferredHolder<MobEffect, ForestRoots> FOREST_ROOTS =
            EFFECTS.register("forest_roots", ForestRoots::new);
    public static final DeferredHolder<MobEffect, ArmorFracture> ARMOR_FRACTURE =
            EFFECTS.register("armor_fracture", ArmorFracture::new);
    public static final DeferredHolder<MobEffect, GoldenStun> GOLDEN_STUN =
            EFFECTS.register("golden_stun", GoldenStun::new);

    private ModEffects() {
    }
}
