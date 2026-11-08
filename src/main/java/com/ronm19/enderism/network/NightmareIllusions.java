package com.ronm19.enderism.network;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.NightmareEnderman;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Target-only visual events. There are no server apparition entities or delayed tasks. */
@EventBusSubscriber(modid = Enderism.MOD_ID)
public final class NightmareIllusions {
    private NightmareIllusions() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(NightmareIllusionPayload.TYPE, NightmareIllusionPayload.STREAM_CODEC);
    }

    public static boolean tryShow(ServerPlayer target, NightmareEnderman owner, long encounter,
                                  Vec3 observed, int maximum, int lifetime) {
        if (!(owner.level() instanceof ServerLevel level) || owner.isHuntCommitted()
                || owner.getEncounterId() != encounter || !owner.isEncounterTarget(target)
                || !owner.isAlive() || owner.isRemoved() || owner.isNoAi()
                || target.level() != level || !target.isAlive() || target.isRemoved() || target.hasDisconnected()
                || !target.connection.hasChannel(NightmareIllusionPayload.TYPE)
                || target.isCreative() || target.isSpectator() || owner.distanceToSqr(target) > 64.0 * 64.0
                || !Double.isFinite(observed.x) || !Double.isFinite(observed.y) || !Double.isFinite(observed.z)
                || owner.position().distanceToSqr(observed) > 64.0 * 64.0) return false;
        int count = Mth.clamp(maximum, 2, NightmareIllusionPayload.MAX_APPARITIONS);
        List<Vec3> positions = plan(level, owner, observed, count);
        if (positions.size() < 2) return false;
        PacketDistributor.sendToPlayer(target, new NightmareIllusionPayload(owner.getUUID(), owner.getId(),
                target.getUUID(), level.dimension().identifier(), encounter, false,
                Mth.clamp(lifetime, 1, NightmareIllusionPayload.MAX_LIFETIME), positions));
        return true;
    }

    public static void clear(ServerPlayer target, NightmareEnderman owner, long encounter, boolean committed) {
        if (target.hasDisconnected() || !target.connection.hasChannel(NightmareIllusionPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(target, new NightmareIllusionPayload(owner.getUUID(), owner.getId(),
                target.getUUID(), owner.level().dimension().identifier(), encounter, true, 0, List.of()));
    }

    private static List<Vec3> plan(ServerLevel level, NightmareEnderman owner, Vec3 observed, int maximum) {
        var positions = new ArrayList<Vec3>(maximum);
        double rotation = owner.getRandom().nextDouble() * Math.PI * 2.0;
        for (int attempt = 0; attempt < 12 && positions.size() < maximum; attempt++) {
            double angle = rotation + attempt * 2.399963229728653;
            double distance = 8.0 + attempt % 4 * 2.5;
            int x = Mth.floor(observed.x + Math.cos(angle) * distance);
            int z = Mth.floor(observed.z + Math.sin(angle) * distance);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
            for (int y = Mth.floor(observed.y) + 3; y >= Mth.floor(observed.y) - 5; y--) {
                BlockPos feet = new BlockPos(x, y, z);
                Vec3 point = new Vec3(x + 0.5, y, z + 0.5);
                double sourceDistance = point.distanceToSqr(owner.position());
                if (sourceDistance < 16.0 || sourceDistance > 48.0 * 48.0
                        || positions.stream().anyMatch(existing -> existing.distanceToSqr(point) < 25.0)
                        || !safeStanding(level, feet)) continue;
                positions.add(point);
                break;
            }
        }
        return positions;
    }

    private static boolean safeStanding(ServerLevel level, BlockPos feet) {
        AABB body = new AABB(feet.getX() + 0.2, feet.getY(), feet.getZ() + 0.2,
                feet.getX() + 0.8, feet.getY() + 2.9, feet.getZ() + 0.8);
        if (body.minY <= level.getMinY() || body.maxY >= level.getMaxY()
                || !level.getWorldBorder().isWithinBounds(body)) return false;
        var floor = level.getBlockState(feet.below());
        return floor.blocksMotion() && floor.getFluidState().isEmpty()
                && level.getFluidState(feet).isEmpty() && level.noBlockCollision(null, body)
                && !level.containsAnyLiquid(body);
    }
}
