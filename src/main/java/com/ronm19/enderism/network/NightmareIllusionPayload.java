package com.ronm19.enderism.network;

import com.ronm19.enderism.Enderism;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** A bounded cosmetic snapshot for one victim and one encounter. */
public record NightmareIllusionPayload(UUID owner, int ownerId, UUID target, Identifier dimension,
                                      long encounter, boolean closed, int lifetime, List<Vec3> positions)
        implements CustomPacketPayload {
    public static final int MAX_APPARITIONS = 3;
    public static final int MAX_LIFETIME = 120;
    public static final Type<NightmareIllusionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "nightmare_illusions"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NightmareIllusionPayload> STREAM_CODEC =
            StreamCodec.of(NightmareIllusionPayload::encode, NightmareIllusionPayload::decode);

    public NightmareIllusionPayload {
        positions = List.copyOf(positions);
        if (encounter < 0 || lifetime < 0 || lifetime > MAX_LIFETIME || positions.size() > MAX_APPARITIONS
                || (closed && !positions.isEmpty()) || positions.stream().anyMatch(point ->
                        !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z))) {
            throw new IllegalArgumentException("Invalid Nightmare illusion snapshot");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NightmareIllusionPayload payload) {
        buffer.writeUUID(payload.owner);
        buffer.writeVarInt(payload.ownerId);
        buffer.writeUUID(payload.target);
        buffer.writeIdentifier(payload.dimension);
        buffer.writeVarLong(payload.encounter);
        buffer.writeBoolean(payload.closed);
        buffer.writeVarInt(payload.lifetime);
        buffer.writeVarInt(payload.positions.size());
        for (Vec3 point : payload.positions) {
            buffer.writeDouble(point.x);
            buffer.writeDouble(point.y);
            buffer.writeDouble(point.z);
        }
    }

    private static NightmareIllusionPayload decode(RegistryFriendlyByteBuf buffer) {
        UUID owner = buffer.readUUID();
        int ownerId = buffer.readVarInt();
        UUID target = buffer.readUUID();
        Identifier dimension = buffer.readIdentifier();
        long encounter = buffer.readVarLong();
        boolean closed = buffer.readBoolean();
        int lifetime = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_APPARITIONS) throw new IllegalArgumentException("Too many Nightmare apparitions");
        var positions = new ArrayList<Vec3>(count);
        for (int index = 0; index < count; index++) {
            positions.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        }
        return new NightmareIllusionPayload(owner, ownerId, target, dimension, encounter, closed, lifetime, positions);
    }

    @Override
    public Type<NightmareIllusionPayload> type() {
        return TYPE;
    }
}
