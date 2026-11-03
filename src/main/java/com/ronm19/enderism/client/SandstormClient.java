package com.ronm19.enderism.client;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.SandEnderman;
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
import net.minecraft.core.particles.DustParticleOptions;
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

/** Bounded local dust and atmospheric haze; no weather state, status effects, or post effects are changed. */
@EventBusSubscriber(modid = Enderism.MOD_ID, value = Dist.CLIENT)
public final class SandstormClient {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int MAX_VISIBLE_FIELDS = 2;
    private static final int MAX_PARTICLES = 640;
    private static final int DUST_LIFETIME = 10;
    private static final DustParticleOptions SAND_DUST = new DustParticleOptions(0xC7AA72, 1.1F);
    private static @Nullable ClientLevel cachedLevel;
    private static List<SandEnderman> nearby = List.of();
    private static List<SandEnderman> visibleFields = List.of();
    private static final List<OwnedParticle> particles = new ArrayList<>();
    private static long nextScanAt;
    private static long nextWindSoundAt;

    private SandstormClient() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level != cachedLevel) {
            for (OwnedParticle owned : particles) owned.particle.remove();
            particles.clear();
            cachedLevel = level;
            nearby = List.of(); visibleFields = List.of();
            nextScanAt = 0; nextWindSoundAt = 0;
        }
        if (level == null || minecraft.player == null) return;
        if (!minecraft.player.isAlive()) { clearPresentation(); return; }
        if (minecraft.isPaused()) return;
        pruneParticles(level);
        Vec3 observer = minecraft.gameRenderer.getMainCamera().position();
        long now = level.getGameTime();
        if (now >= nextScanAt) {
            nextScanAt = now + SCAN_INTERVAL_TICKS;
            nearby = List.copyOf(level.getEntitiesOfClass(SandEnderman.class,
                    new AABB(observer, observer).inflate(128.0), sand -> sand.isAlive() && !sand.isRemoved()));
        }
        List<SandEnderman> active = new ArrayList<>();
        for (SandEnderman sand : nearby) {
            if (!valid(sand, level) || intensity(sand) <= 0.0F) continue;
            double visibleRange = sand.getSandstormRadius() + 12.0;
            if (sand.getSandstormCenter().distanceToSqr(observer) <= visibleRange * visibleRange) active.add(sand);
        }
        active.sort(Comparator.comparingDouble(sand -> sand.getSandstormCenter().distanceToSqr(observer)));
        int count = Math.min(MAX_VISIBLE_FIELDS, active.size());
        visibleFields = List.copyOf(active.subList(0, count));
        if (count == 0) return;
        int budget = switch (minecraft.options.particles().get()) {
            case ALL -> 64;
            case DECREASED -> 32;
            case MINIMAL -> 8;
        };
        int perField = budget / count;
        for (SandEnderman sand : visibleFields) paintDust(minecraft, level, sand, Math.round(perField * intensity(sand)), now);
        float strength = viewAt(level, observer).strength;
        if (strength > 0.05F && now >= nextWindSoundAt) {
            nextWindSoundAt = now + 50;
            level.playLocalSound(observer.x, observer.y, observer.z, SoundEvents.BREEZE_WHIRL,
                    SoundSource.WEATHER, strength * 0.16F, 0.65F, false);
        }
    }

    private static void clearPresentation() {
        for (OwnedParticle owned : particles) owned.particle.remove();
        particles.clear(); nearby = List.of(); visibleFields = List.of(); nextScanAt = 0;
    }

    private static boolean valid(SandEnderman sand, ClientLevel level) {
        return sand.level() == level && sand.isAlive() && !sand.isRemoved()
                && sand.isSandstormActive() && sand.getSandstormTicksRemaining() > 0;
    }

    private static float intensity(SandEnderman sand) {
        return Mth.clamp(sand.getSandstormIntensity(), 0.0F, 1.0F)
                * Mth.clamp(sand.getSandstormTicksRemaining() / 12.0F, 0.0F, 1.0F);
    }

    /** Shared only by this renderer and local presentation: zero outside the actual finite veil. */
    public static float fieldStrength(SandEnderman sand, Vec3 position) {
        if (!sand.isSandstormActive()) return 0.0F;
        Vec3 center = sand.getSandstormCenter();
        double height = position.y - center.y;
        if (height < -1.5 || height > 6.0) return 0.0F;
        double dx = position.x - center.x, dz = position.z - center.z;
        float edge = Mth.clamp((sand.getSandstormRadius() - (float)Math.sqrt(dx * dx + dz * dz)) / 1.5F, 0.0F, 1.0F);
        float vertical = Math.min(Mth.clamp((float)(height + 1.5) / 1.5F, 0.0F, 1.0F),
                Mth.clamp((float)(6.0 - height) / 1.5F, 0.0F, 1.0F));
        return edge * vertical * intensity(sand);
    }

    private static void pruneParticles(ClientLevel level) {
        particles.removeIf(owned -> {
            boolean remove = !owned.particle.isAlive() || !valid(owned.sand, level) || intensity(owned.sand) <= 0.0F
                    || !insideDust(owned.sand, owned.particle.getPos());
            if (remove) owned.particle.remove();
            return remove;
        });
    }

    private static boolean insideDust(SandEnderman sand, Vec3 position) {
        Vec3 center = sand.getSandstormCenter();
        double dx = position.x - center.x, dz = position.z - center.z;
        return dx * dx + dz * dz < sand.getSandstormRadius() * sand.getSandstormRadius()
                && position.y >= center.y - 0.25 && position.y < center.y + 5.5;
    }

    private static void paintDust(Minecraft minecraft, ClientLevel level, SandEnderman sand, int budget, long now) {
        Vec3 center = sand.getSandstormCenter();
        double radius = Math.max(0.0, sand.getSandstormRadius() - 0.45);
        double phase = now * 0.045;
        double windX = Math.cos(phase) * 0.24, windZ = Math.sin(phase) * 0.24;
        for (int i = 0; i < budget && particles.size() < MAX_PARTICLES; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(level.getRandom().nextDouble()) * radius;
            double dx = Math.cos(angle) * distance, dz = Math.sin(angle) * distance;
            // The straight wind segment stays within the circular field; live particles are also culled at its edge.
            double endX = dx + windX * DUST_LIFETIME, endZ = dz + windZ * DUST_LIFETIME;
            if (endX * endX + endZ * endZ >= radius * radius) continue;
            double y = center.y + 0.3 + level.getRandom().nextDouble() * 4.5;
            BlockPos position = BlockPos.containing(center.x + dx, y, center.z + dz);
            if (!level.hasChunkAt(position) || !level.getBlockState(position).getCollisionShape(level, position).isEmpty()) continue;
            Particle particle = minecraft.particleEngine.createParticle(i % 4 == 0 ? ParticleTypes.CLOUD : SAND_DUST,
                    center.x + dx, y, center.z + dz, 0, 0, 0);
            if (particle == null) continue;
            particle.setParticleSpeed(windX, 0.015, windZ);
            particle.setLifetime(DUST_LIFETIME);
            if (particle instanceof SingleQuadParticle quad) {
                quad.setColor(0.72F, 0.58F, 0.35F);
                if (i % 4 == 0) quad.scale(1.6F);
            }
            particles.add(new OwnedParticle(particle, sand));
        }
    }

    private static View viewAt(ClientLevel level, Vec3 position) {
        if (level != cachedLevel) return View.NONE;
        float strongest = 0.0F, eyeSafeDistance = 18.0F;
        for (SandEnderman sand : visibleFields) {
            if (!valid(sand, level)) continue;
            float strength = fieldStrength(sand, position);
            if (strength <= 0.0F) continue;
            strongest = Math.max(strongest, strength);
            // Native EMISSIVE eyes still receive atmospheric fog. Do not fully fog eyes within this veil's diameter.
            eyeSafeDistance = Math.max(eyeSafeDistance, sand.getSandstormRadius() * 2.0F + 2.0F);
        }
        return new View(strongest, eyeSafeDistance);
    }

    private static View cameraView(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()
                || camera.getFluidInCamera() != FogType.NONE) return View.NONE;
        if (camera.entity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return View.NONE;
        return viewAt(minecraft.level, camera.position());
    }

    @SubscribeEvent
    public static void color(ViewportEvent.ComputeFogColor event) {
        float strength = cameraView(event.getCamera()).strength * 0.8F;
        if (strength <= 0.0F) return;
        float light = Mth.clamp((event.getRed() + event.getGreen() + event.getBlue()) / 3.0F, 0.16F, 0.85F);
        event.setRed(Mth.lerp(strength, event.getRed(), light * 1.1F));
        event.setGreen(Mth.lerp(strength, event.getGreen(), light * 0.87F));
        event.setBlue(Mth.lerp(strength, event.getBlue(), light * 0.54F));
    }

    @SubscribeEvent
    public static void distance(ViewportEvent.RenderFog event) {
        if (!(event.getEnvironment() instanceof AtmosphericFogEnvironment)) return;
        View view = cameraView(event.getCamera());
        if (view.strength <= 0.0F) return;
        float previousFar = event.getFarPlaneDistance();
        float visibleFar = Math.min(previousFar, event.getFogData().renderDistanceEnd);
        if (!Float.isFinite(visibleFar) || visibleFar <= 0.0F) return;
        float far = Mth.lerp(view.strength, visibleFar, Math.min(visibleFar, view.eyeSafeDistance));
        event.setFarPlaneDistance(Math.min(previousFar, far));
        float previousNear = event.getNearPlaneDistance();
        float near = Math.min(previousNear, far * 0.75F);
        event.setNearPlaneDistance(Mth.lerp(view.strength, near, Math.min(near, 1.5F)));
    }

    private record OwnedParticle(Particle particle, SandEnderman sand) {}
    private record View(float strength, float eyeSafeDistance) { private static final View NONE = new View(0.0F, 18.0F); }
}
