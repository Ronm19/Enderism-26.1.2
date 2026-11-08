package com.ronm19.enderism.client;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.NightmareEnderman;
import com.ronm19.enderism.network.NightmareIllusionPayload;
import com.ronm19.enderism.registry.ModEntities;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/** Cosmetic models submitted to the normal world renderer for the addressed player only. */
@EventBusSubscriber(modid = Enderism.MOD_ID, value = Dist.CLIENT)
public final class NightmareIllusionClient {
    private static final Manifestations STORE = new Manifestations();
    private static @Nullable ClientLevel currentLevel;

    private NightmareIllusionClient() {
    }

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(NightmareIllusionPayload.TYPE, (payload, context) -> receive(payload));
    }

    private static void receive(NightmareIllusionPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var player = minecraft.player;
        if (level != currentLevel) {
            STORE.clear();
            currentLevel = level;
        }
        if (level == null || player == null) return;
        if (!payload.closed()) {
            if (!(level.getEntity(payload.ownerId()) instanceof NightmareEnderman nightmare)
                    || !nightmare.getUUID().equals(payload.owner()) || !nightmare.isAlive()
                    || nightmare.getEncounterId() > payload.encounter()
                    || nightmare.getEncounterId() == payload.encounter() && nightmare.isHuntCommitted()) return;
        }
        if (STORE.accept(payload, player.getUUID(), level.dimension().identifier(), level.getGameTime())
                && !payload.closed() && !payload.positions().isEmpty()
                && validOwner(level, payload.ownerId(), payload.owner(), payload.encounter())) {
            Vec3 point = payload.positions().getFirst();
            level.playLocalSound(point.x, point.y + 1.3, point.z, SoundEvents.ENDERMAN_AMBIENT,
                    SoundSource.HOSTILE, 0.35F, 0.70F, false);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var player = minecraft.player;
        if (level != currentLevel || level == null || player == null || !player.isAlive()
                || player.isCreative() || player.isSpectator()) {
            STORE.clear();
            currentLevel = level;
            return;
        }
        STORE.expire(level.getGameTime());
        for (Manifestation entry : STORE.snapshots()) {
            // A custom payload can precede the tracker update that advances the encounter ID.
            // Keep the bounded snapshot pending until that exact encounter is synchronized.
            if (level.getEntity(entry.ownerId()) instanceof NightmareEnderman nightmare
                    && nightmare.getUUID().equals(entry.owner()) && nightmare.isAlive()
                    && nightmare.getEncounterId() < entry.encounter()) continue;
            if (!validOwner(level, entry.ownerId(), entry.owner(), entry.encounter())) {
                STORE.close(entry.owner(), entry.encounter());
                continue;
            }
            for (Vec3 point : entry.positions()) {
                if (player.position().distanceToSqr(point) <= 3.5 * 3.5) STORE.hide(entry.owner(), point);
            }
        }
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        long now = level.getGameTime();
        for (Manifestation entry : STORE.snapshots()) {
            if (entry.expiresAt() <= now || !validOwner(level, entry.ownerId(), entry.owner(), entry.encounter())) continue;
            float fade = Mth.clamp((entry.expiresAt() - now) / 6.0F, 0, 1);
            for (Vec3 point : entry.positions()) {
                if (point.distanceToSqr(player.position()) < 3.5 * 3.5
                        || point.distanceToSqr(camera) > 64.0 * 64.0 || !level.hasChunkAt(BlockPos.containing(point))) continue;
                var state = apparitionState(point, player.position(), now - entry.createdAt(), fade);
                state.lightCoords = LevelRenderer.getLightCoords(level, BlockPos.containing(point));
                minecraft.getEntityRenderDispatcher().submit(state, event.getLevelRenderState().cameraRenderState,
                        point.x - camera.x, point.y - camera.y, point.z - camera.z,
                        event.getPoseStack(), event.getSubmitNodeCollector());
            }
        }
    }

    /** Uses the real model and eye layer with no ticking or damageable client entity. */
    public static NightmareEndermanRenderer.NightmareRenderState apparitionState(
            Vec3 position, Vec3 observer, long age, float fade) {
        var state = new NightmareEndermanRenderer.NightmareRenderState();
        state.entityType = ModEntities.NIGHTMARE_ENDERMAN.get();
        state.x = position.x;
        state.y = position.y;
        state.z = position.z;
        state.boundingBoxWidth = 0.6F;
        state.boundingBoxHeight = 2.9F;
        state.eyeHeight = 2.55F;
        state.bodyRot = (float)(Mth.atan2(observer.z - position.z, observer.x - position.x) * 180 / Math.PI) - 90;
        state.yRot = 0;
        state.ageInTicks = age;
        state.phase = NightmareEnderman.CombatPhase.ILLUSIONS;
        state.phaseAge = age;
        state.brightness = 0.72F;
        state.apparition = true;
        state.opacity = 0.84F * fade;
        return state;
    }

    private static boolean validOwner(ClientLevel level, int ownerId, UUID owner, long encounter) {
        return level.getEntity(ownerId) instanceof NightmareEnderman nightmare
                && nightmare.getUUID().equals(owner) && nightmare.isAlive() && !nightmare.isRemoved()
                && !nightmare.isHuntCommitted() && nightmare.getEncounterId() == encounter;
    }

    /** Event ledger retains encounter closures so delayed snapshots cannot resurrect a finished illusion. */
    public static final class Manifestations {
        private static final int MAX_SOURCES = 16;
        private final LinkedHashMap<UUID, Manifestation> sources = new LinkedHashMap<>();

        public boolean accept(NightmareIllusionPayload payload, UUID viewer, Identifier dimension, long now) {
            if (!payload.target().equals(viewer) || !payload.dimension().equals(dimension)) return false;
            Manifestation current = this.sources.get(payload.owner());
            if (current != null && (payload.encounter() < current.encounter()
                    || payload.encounter() == current.encounter() && current.closed())) return false;
            if (!this.sources.containsKey(payload.owner()) && this.sources.size() >= MAX_SOURCES) {
                this.sources.remove(this.sources.keySet().iterator().next());
            }
            boolean closed = payload.closed();
            this.sources.put(payload.owner(), new Manifestation(payload.owner(), payload.ownerId(), payload.encounter(),
                    now, now + payload.lifetime(), closed, closed ? List.of() : payload.positions()));
            return true;
        }

        public void expire(long now) {
            this.sources.replaceAll((owner, entry) -> entry.expiresAt() <= now && !entry.positions().isEmpty()
                    ? entry.withPositions(List.of()) : entry);
        }

        public void close(UUID owner, long encounter) {
            Manifestation current = this.sources.get(owner);
            if (current != null && current.encounter() == encounter) {
                this.sources.put(owner, new Manifestation(owner, current.ownerId(), encounter,
                        current.createdAt(), current.expiresAt(), true, List.of()));
            }
        }

        public void hide(UUID owner, Vec3 point) {
            Manifestation current = this.sources.get(owner);
            if (current != null) {
                this.sources.put(owner, current.withPositions(current.positions().stream()
                        .filter(existing -> !existing.equals(point)).toList()));
            }
        }

        public int apparitionCount() {
            return this.sources.values().stream().mapToInt(entry -> entry.positions().size()).sum();
        }

        public List<Manifestation> snapshots() {
            return new ArrayList<>(this.sources.values());
        }

        public void clear() {
            this.sources.clear();
        }
    }

    public record Manifestation(UUID owner, int ownerId, long encounter, long createdAt,
                                long expiresAt, boolean closed, List<Vec3> positions) {
        private Manifestation withPositions(List<Vec3> positions) {
            return new Manifestation(this.owner, this.ownerId, this.encounter, this.createdAt,
                    this.expiresAt, this.closed, positions);
        }
    }
}
