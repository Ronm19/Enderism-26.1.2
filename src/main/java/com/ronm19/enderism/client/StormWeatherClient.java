package com.ronm19.enderism.client;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.StormEnderman;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;

/** Short-lived local rain and atmospheric fog; the world's weather and post effects are untouched. */
@EventBusSubscriber(modid = Enderism.MOD_ID, value = Dist.CLIENT)
public final class StormWeatherClient {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int MAX_VISIBLE_FIELDS = 2;
    private static @Nullable ClientLevel cachedLevel;
    private static List<StormEnderman> nearby = List.of();
    private static long nextScanAt;
    private static long nextRainSoundAt;

    private StormWeatherClient() {}

    /** Called from the existing client post-tick hook. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != cachedLevel) {
            cachedLevel = level;
            nearby = List.of();
            nextScanAt = 0;
            nextRainSoundAt = 0;
        }
        if (level == null || minecraft.player == null || !minecraft.player.isAlive() || minecraft.isPaused()) return;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 observer = camera.position();
        long now = level.getGameTime();
        if (now >= nextScanAt) {
            nextScanAt = now + SCAN_INTERVAL_TICKS;
            nearby = List.copyOf(level.getEntitiesOfClass(StormEnderman.class,
                    // A caster can teleport away from its fixed cloud while remaining within entity tracking range.
                    new AABB(observer, observer).inflate(128.0), storm -> storm.isAlive() && !storm.isRemoved()));
        }
        List<StormEnderman> active = new ArrayList<>();
        for (StormEnderman storm : nearby) {
            if (valid(storm, level) && storm.getTempestCenter().distanceToSqr(observer) < 40.0 * 40.0) active.add(storm);
        }
        active.sort(Comparator.comparingDouble(storm -> storm.getTempestCenter().distanceToSqr(observer)));
        int fieldCount = Math.min(MAX_VISIBLE_FIELDS, active.size());
        if (fieldCount == 0) return;
        int totalRainBudget = switch (minecraft.options.particles().get()) {
            case ALL -> 72;
            case DECREASED -> 36;
            case MINIMAL -> 12;
        };
        int perField = totalRainBudget / fieldCount;
        for (int i = 0; i < fieldCount; i++) paintRain(minecraft, level, active.get(i), perField, now);
        float strength = strengthAt(level, observer);
        if (strength > 0.05F && now >= nextRainSoundAt) {
            nextRainSoundAt = now + 40;
            level.playLocalSound(observer.x, observer.y, observer.z, SoundEvents.WEATHER_RAIN,
                    SoundSource.WEATHER, strength * 0.35F, 0.85F, false);
        }
    }

    private static boolean valid(StormEnderman storm, ClientLevel level) {
        return storm.level() == level && storm.isAlive() && !storm.isRemoved()
                && storm.isTempestActive() && storm.getTempestTicksRemaining() > 0;
    }

    private static void paintRain(Minecraft minecraft, ClientLevel level, StormEnderman storm, int budget, long now) {
        Vec3 center = storm.getTempestCenter();
        double radius = Math.max(0.0, storm.getTempestRadius() - 1.0);
        for (int i = 0; i < budget; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(level.getRandom().nextDouble()) * radius;
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            double y = center.y + 5.0 + level.getRandom().nextDouble() * 8.0;
            BlockPos position = BlockPos.containing(x, y, z);
            if (!exposed(level, position)) continue;
            Particle drop = minecraft.particleEngine.createParticle(ParticleTypes.RAIN, x, y, z, 0, 0, 0);
            if (drop != null) {
                // The native RAIN provider ignores the auxiliary velocity. Set it on the created drop.
                drop.setParticleSpeed(-0.06, -0.85, 0.025);
                drop.setLifetime(12);
                if (drop instanceof SingleQuadParticle quad) quad.setColor(0.62F, 0.68F, 0.90F);
            }
        }
        if (now % 4 == 0) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            double distance = level.getRandom().nextDouble() * Math.max(0.0, radius - 1.0);
            Vec3 position = center.add(Math.cos(angle) * distance, 11.0, Math.sin(angle) * distance);
            if (exposed(level, BlockPos.containing(position))) {
                Particle cloud = minecraft.particleEngine.createParticle(ParticleTypes.CLOUD,
                        position.x, position.y, position.z, -0.025, 0, 0.01);
                if (cloud != null) {
                    cloud.setLifetime(28);
                    cloud.scale(1.8F);
                    if (cloud instanceof SingleQuadParticle quad) quad.setColor(0.25F, 0.27F, 0.36F);
                }
            }
        }
    }

    private static boolean exposed(ClientLevel level, BlockPos position) {
        return level.hasChunkAt(position) && level.canSeeSky(position);
    }

    private static float strengthAt(ClientLevel level, Vec3 position) {
        if (level != cachedLevel || !exposed(level, BlockPos.containing(position))) return 0.0F;
        float strongest = 0.0F;
        for (StormEnderman storm : nearby) {
            if (!valid(storm, level)) continue;
            Vec3 center = storm.getTempestCenter();
            double height = position.y - center.y;
            if (height < -2.0 || height > 16.0) continue;
            double dx = position.x - center.x, dz = position.z - center.z;
            float edge = Mth.clamp((storm.getTempestRadius() - (float) Math.sqrt(dx * dx + dz * dz)) / 2.0F, 0.0F, 1.0F);
            float vertical = Math.min(Mth.clamp((float) (height + 2.0) / 2.0F, 0.0F, 1.0F),
                    Mth.clamp((float) (16.0 - height) / 2.0F, 0.0F, 1.0F));
            float ending = Mth.clamp(storm.getTempestTicksRemaining() / 10.0F, 0.0F, 1.0F);
            strongest = Math.max(strongest, edge * vertical * ending);
        }
        return strongest;
    }

    private static float cameraStrength(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()
                || camera.getFluidInCamera() != FogType.NONE) return 0.0F;
        if (camera.entity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return 0.0F;
        return strengthAt(minecraft.level, camera.position());
    }

    @SubscribeEvent
    public static void color(ViewportEvent.ComputeFogColor event) {
        float strength = cameraStrength(event.getCamera()) * 0.45F;
        if (strength <= 0.0F) return;
        event.setRed(Mth.lerp(strength, event.getRed(), event.getRed() * 0.55F));
        event.setGreen(Mth.lerp(strength, event.getGreen(), event.getGreen() * 0.60F));
        event.setBlue(Mth.lerp(strength, event.getBlue(), event.getBlue() * 0.80F));
    }

    @SubscribeEvent
    public static void distance(ViewportEvent.RenderFog event) {
        if (!(event.getEnvironment() instanceof AtmosphericFogEnvironment)) return;
        float strength = cameraStrength(event.getCamera());
        if (strength <= 0.0F) return;
        float previousFar = event.getFarPlaneDistance();
        float visibleFar = Math.min(previousFar, event.getFogData().renderDistanceEnd);
        if (!Float.isFinite(visibleFar) || visibleFar <= 0.0F) return;
        float far = Mth.lerp(strength, visibleFar, Math.min(visibleFar, 14.0F));
        event.setFarPlaneDistance(Math.min(previousFar, far));
        float previousNear = event.getNearPlaneDistance();
        float near = Math.min(previousNear, far * 0.75F);
        event.setNearPlaneDistance(Mth.lerp(strength, near, Math.min(near, 3.0F)));
    }
}

