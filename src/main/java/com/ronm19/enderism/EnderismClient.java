package com.ronm19.enderism;

import com.ronm19.enderism.client.BrownEndermanRenderer;
import com.ronm19.enderism.client.GrayEndermanRenderer;
import com.ronm19.enderism.client.ArcticEndermanRenderer;
import com.ronm19.enderism.client.FireEndermanRenderer;
import com.ronm19.enderism.client.FrostEndermanRenderer;
import com.ronm19.enderism.client.ForestEndermanRenderer;
import com.ronm19.enderism.client.StormEndermanRenderer;
import com.ronm19.enderism.client.StormWeatherClient;
import com.ronm19.enderism.client.SandEndermanRenderer;
import com.ronm19.enderism.client.SilverEndermanRenderer;
import com.ronm19.enderism.client.PhantomEndermanRenderer;
import com.ronm19.enderism.client.DarkEndermanRenderer;
import com.ronm19.enderism.client.DarkMagicBoltRenderer;
import com.ronm19.enderism.client.InfernalEndermanModel;
import com.ronm19.enderism.client.InfernalEndermanRenderer;
import com.ronm19.enderism.client.InfernalMeteorRenderer;
import com.ronm19.enderism.client.GoldenEndermanRenderer;
import com.ronm19.enderism.client.NightmareEndermanRenderer;
import com.ronm19.enderism.client.DemonEndermanRenderer;
import com.ronm19.enderism.client.DemonEndermanModel;
import com.ronm19.enderism.client.BloodEndermanRenderer;
import com.ronm19.enderism.client.BloodLanceRenderer;
import com.ronm19.enderism.client.SpectralEndermanRenderer;
import com.ronm19.enderism.client.AngelEndermanRenderer;
import com.ronm19.enderism.client.AngelEndermanModel;
import com.ronm19.enderism.client.RavagerEndermanRenderer;
import com.ronm19.enderism.client.RavagerEndermanModel;
import com.ronm19.enderism.client.EndermanGolemRenderer;
import com.ronm19.enderism.client.EndermanGolemModel;
import com.ronm19.enderism.client.VoidEndermanRenderer;
import com.ronm19.enderism.client.BoggedEndermanRenderer;
import com.ronm19.enderism.client.BoggedEndermanModel;
import com.ronm19.enderism.client.BoggedVenomRenderer;
import com.ronm19.enderism.client.SkeletonEndermanRenderer;
import com.ronm19.enderism.client.SkeletonArcaneArrowRenderer;
import com.ronm19.enderism.client.ZombieEndermanRenderer;
import com.ronm19.enderism.client.SculkEndermanRenderer;
import com.ronm19.enderism.client.HuskEndermanRenderer;
import com.ronm19.enderism.client.VampireEndermanRenderer;
import com.ronm19.enderism.client.VampireEndermanModel;
import com.ronm19.enderism.client.WitherEndermanModel;
import com.ronm19.enderism.client.WitherEndermanRenderer;
import com.ronm19.enderism.client.WhiteEndermanRenderer;
import com.ronm19.enderism.client.SoulSandEndermanRenderer;
import net.minecraft.client.renderer.entity.WitherSkullRenderer;
import com.ronm19.enderism.client.SculkEndermanModel;
import net.minecraft.client.renderer.entity.NoopRenderer;
import com.ronm19.enderism.client.SandstormClient;
import com.ronm19.enderism.client.ArcticStunClient;
import com.ronm19.enderism.entity.BrownEnderman;
import com.ronm19.enderism.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = Enderism.MOD_ID, value = Dist.CLIENT)
public final class EnderismClient {
    private static final Identifier HUNTERS_GAZE = Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "brown_hunters_gaze");
    private static @Nullable ClientLevel fearLevel;
    private static @Nullable BrownEnderman fearSource;
    private static int lookupDelay;
    private static boolean ownsPostEffect;
    private static boolean effectReplaced;

    private EnderismClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BROWN_ENDERMAN.get(), BrownEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.GRAY_ENDERMAN.get(), GrayEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.ARCTIC_ENDERMAN.get(), ArcticEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.FIRE_ENDERMAN.get(), FireEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.FROST_ENDERMAN.get(), FrostEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.FOREST_ENDERMAN.get(), ForestEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.STORM_ENDERMAN.get(), StormEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.SAND_ENDERMAN.get(), SandEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.SILVER_ENDERMAN.get(), SilverEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.PHANTOM_ENDERMAN.get(), PhantomEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.DARK_ENDERMAN.get(), DarkEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.DARK_MAGIC_BOLT.get(), DarkMagicBoltRenderer::new);
        event.registerEntityRenderer(ModEntities.INFERNAL_ENDERMAN.get(), InfernalEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.INFERNAL_METEOR.get(), InfernalMeteorRenderer::new);
        event.registerEntityRenderer(ModEntities.GOLDEN_ENDERMAN.get(), GoldenEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.NIGHTMARE_ENDERMAN.get(), NightmareEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.DEMON_ENDERMAN.get(), DemonEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.BLOOD_ENDERMAN.get(), BloodEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.BLOOD_LANCE.get(), BloodLanceRenderer::new);
        event.registerEntityRenderer(ModEntities.SPECTRAL_ENDERMAN.get(), SpectralEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.ANGEL_ENDERMAN.get(), AngelEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.RAVAGER_ENDERMAN.get(), RavagerEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.ENDERMAN_GOLEM.get(), EndermanGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_ENDERMAN.get(), VoidEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.BOGGED_ENDERMAN.get(), BoggedEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.SKELETON_ENDERMAN.get(), SkeletonEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.ZOMBIE_ENDERMAN.get(), ZombieEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.SCULK_ENDERMAN.get(), SculkEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.HUSK_ENDERMAN.get(), HuskEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.VAMPIRE_ENDERMAN.get(), VampireEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.WITHER_ENDERMAN.get(), WitherEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.WHITE_ENDERMAN.get(), WhiteEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.SOUL_SAND_ENDERMAN.get(), SoulSandEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.WITHER_BARRAGE_SKULL.get(), WitherSkullRenderer::new);
        event.registerEntityRenderer(ModEntities.SKELETON_ARCANE_ARROW.get(), SkeletonArcaneArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.BOGGED_VENOM.get(), BoggedVenomRenderer::new);
        event.registerEntityRenderer(ModEntities.BOGGED_TOXIC_MIRE.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.STORM_LIGHTNING.get(), LightningBoltRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(InfernalEndermanModel.LAYER, InfernalEndermanModel::createLayer);
        event.registerLayerDefinition(DemonEndermanModel.LAYER, DemonEndermanModel::createLayer);
        event.registerLayerDefinition(AngelEndermanModel.LAYER, AngelEndermanModel::createLayer);
        event.registerLayerDefinition(RavagerEndermanModel.LAYER, RavagerEndermanModel::createLayer);
        event.registerLayerDefinition(EndermanGolemModel.LAYER, EndermanGolemModel::createLayer);
        event.registerLayerDefinition(BoggedEndermanModel.LAYER, BoggedEndermanModel::createLayer);
        event.registerLayerDefinition(SculkEndermanModel.TENDRILS_LAYER, SculkEndermanModel::createTendrilsLayer);
        event.registerLayerDefinition(VampireEndermanModel.REGALIA_LAYER, VampireEndermanModel::createRegaliaLayer);
        event.registerLayerDefinition(WitherEndermanModel.REGALIA_LAYER, WitherEndermanModel::createRegaliaLayer);
    }

    @SubscribeEvent
    public static void arcticInput(ClientTickEvent.Pre event) {
        ArcticStunClient.tick();
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        StormWeatherClient.tick();
        SandstormClient.tick();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level != fearLevel) {
            releaseEffect(minecraft);
            fearLevel = level;
            fearSource = null;
            lookupDelay = 0;
            effectReplaced = false;
        }
        if (level == null || player == null || !player.isAlive() || player.isCreative() || player.isSpectator()
                || minecraft.getCameraEntity() != player) {
            releaseEffect(minecraft);
            fearSource = null;
            effectReplaced = false;
            return;
        }

        if (fearSource != null && !isActiveFearSource(fearSource, level, player)) {
            fearSource = null;
        }
        if (--lookupDelay <= 0) {
            lookupDelay = 5;
            if (fearSource == null) {
                var nearby = level.getEntitiesOfClass(BrownEnderman.class, player.getBoundingBox().inflate(64.0),
                        brown -> isActiveFearSource(brown, level, player));
                if (!nearby.isEmpty()) {
                    fearSource = nearby.getFirst();
                }
            }
        }
        if (fearSource == null) {
            releaseEffect(minecraft);
            effectReplaced = false;
            return;
        }

        Identifier currentEffect = minecraft.gameRenderer.currentPostEffect();
        if (ownsPostEffect && !HUNTERS_GAZE.equals(currentEffect)) {
            ownsPostEffect = false;
            effectReplaced = true;
        }
        // An existing camera/mod effect keeps ownership. Never overwrite or clear it.
        if (!ownsPostEffect && !effectReplaced && currentEffect == null) {
            minecraft.gameRenderer.setPostEffect(HUNTERS_GAZE);
            ownsPostEffect = true;
        }
    }

    private static boolean isActiveFearSource(BrownEnderman brown, ClientLevel level, LocalPlayer player) {
        return brown.level() == level && brown.isAlive() && !brown.isRemoved()
                && brown.isFearVisualActive() && brown.getGazeTargetId() == player.getId()
                && brown.distanceToSqr(player) <= 64.0 * 64.0;
    }

    private static void releaseEffect(Minecraft minecraft) {
        if (ownsPostEffect && HUNTERS_GAZE.equals(minecraft.gameRenderer.currentPostEffect())) {
            minecraft.gameRenderer.clearPostEffect();
        }
        ownsPostEffect = false;
    }
}
