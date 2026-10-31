package com.ronm19.enderism.spawning;

import com.ronm19.enderism.EnderismConfig;
import com.ronm19.enderism.entity.BrownEnderman;
import com.ronm19.enderism.entity.GrayEnderman;
import com.ronm19.enderism.entity.ArcticEnderman;
import com.ronm19.enderism.entity.FireEnderman;
import com.ronm19.enderism.entity.FrostEnderman;
import com.ronm19.enderism.registry.ModEntities;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;
import org.jspecify.annotations.Nullable;

/** Level-owned fallback spawning; its temporary absence timers are independent of creature AI. */
public final class EnderismSpawnForcer implements CustomSpawner {
    private static final int CHECK_INTERVAL = 20;
    private static final int POSITION_ATTEMPTS = 24;
    private static final double NEARBY_RADIUS = 96.0;
    private static final List<Rule<?>> RULES = List.of(
            new Rule<>(ModEntities.BROWN_ENDERMAN, BrownEnderman::isSpawnTime, BrownEnderman::isSpawnHabitat),
            new Rule<>(ModEntities.GRAY_ENDERMAN, GrayEnderman::isSpawnTime, GrayEnderman::isSpawnHabitat),
            new Rule<>(ModEntities.ARCTIC_ENDERMAN, ArcticEnderman::isSpawnTime, ArcticEnderman::isSpawnHabitat),
            new Rule<>(ModEntities.FIRE_ENDERMAN, FireEnderman::isSpawnTime, FireEnderman::isSpawnHabitat),
            new Rule<>(ModEntities.FROST_ENDERMAN, FrostEnderman::isSpawnTime, FrostEnderman::isSpawnHabitat));

    private final Map<UUID, PlayerAbsence> players = new HashMap<>();
    private long lastTick = Long.MIN_VALUE;
    private long nextCheck;
    private int playerCursor;

    public static void addToLevel(ModifyCustomSpawnersEvent event) {
        event.addCustomSpawner(new EnderismSpawnForcer());
    }

    @Override
    public void tick(ServerLevel level, boolean spawnEnemies) {
        long now = level.getGameTime();
        // Vanilla skips custom-spawner callbacks while mob spawning is disabled.
        // A skipped interval must not count as eligible waiting time on resume.
        if (this.lastTick != Long.MIN_VALUE && now != this.lastTick + 1L) {
            this.players.clear();
            this.nextCheck = now;
        }
        this.lastTick = now;
        if (!spawnEnemies || !level.isSpawningMonsters() || !EnderismConfig.spawnForcerEnabled()) {
            this.players.clear();
            this.nextCheck = now + CHECK_INTERVAL;
            return;
        }
        if (now < this.nextCheck) {
            return;
        }
        this.nextCheck = now + CHECK_INTERVAL;
        List<ServerPlayer> currentPlayers = List.copyOf(level.players());
        if (currentPlayers.isEmpty()) {
            this.players.clear();
            this.playerCursor = 0;
            return;
        }

        Set<UUID> present = new HashSet<>();
        boolean attemptAvailable = true;
        int start = Math.floorMod(this.playerCursor, currentPlayers.size());
        long delay = EnderismConfig.spawnForcerDelaySeconds() * 20L;
        long retry = EnderismConfig.spawnForcerRetrySeconds() * 20L;
        for (int offset = 0; offset < currentPlayers.size(); offset++) {
            int index = (start + offset) % currentPlayers.size();
            ServerPlayer player = currentPlayers.get(index);
            UUID id = player.getUUID();
            present.add(id);
            if (!eligiblePlayer(level, player)) {
                this.players.remove(id);
                continue;
            }
            PlayerAbsence absence = this.players.get(id);
            if (absence == null || absence.player.get() != player) {
                // A respawn may reuse a UUID/entity ID; the old encounter timer must still reset.
                absence = new PlayerAbsence(player);
                this.players.put(id, absence);
            }
            int ruleStart = Math.floorMod(absence.ruleCursor, RULES.size());
            for (int ruleOffset = 0; ruleOffset < RULES.size(); ruleOffset++) {
                int ruleIndex = (ruleStart + ruleOffset) % RULES.size();
                Rule<?> rule = RULES.get(ruleIndex);
                if (!rule.activePeriod.test(level) || hasNearby(level, player, rule.type.get())) {
                    absence.timers.remove(rule);
                    continue;
                }
                if (!rule.habitat.test(level, player.blockPosition())) {
                    AbsenceTimer paused = absence.timers.get(rule);
                    if (paused != null) {
                        paused.pause(now);
                    }
                    continue;
                }
                AbsenceTimer timer = absence.timers.computeIfAbsent(rule, ignored -> new AbsenceTimer(now));
                // Resume independently of the shared search budget, so other players' eligible time still counts.
                timer.resume(now);
                if (attemptAvailable && timer.isDue(now, delay)) {
                    // At most one bounded placement search per level per second.
                    attemptAvailable = false;
                    this.playerCursor = (index + 1) % currentPlayers.size();
                    absence.ruleCursor = (ruleIndex + 1) % RULES.size();
                    if (trySpawn(level, player, rule)) {
                        timer.reset(now);
                    } else {
                        timer.failed(now, retry);
                    }
                }
            }
            if (absence.timers.isEmpty()) {
                this.players.remove(id);
            }
        }
        this.players.keySet().removeIf(id -> !present.contains(id));
    }

    private static boolean eligiblePlayer(ServerLevel level, ServerPlayer player) {
        return player.level() == level && player.isAlive() && !player.isRemoved() && !player.hasDisconnected()
                && !player.isSpectator()
                && level.getChunkSource().getChunkNow(player.chunkPosition().x(), player.chunkPosition().z()) != null
                && level.areEntitiesActuallyLoadedAndTicking(player.chunkPosition());
    }

    private static <T extends Mob> boolean hasNearby(ServerLevel level, ServerPlayer player, EntityType<T> type) {
        return hasNearbyAt(level, player.position(), type);
    }

    private static <T extends Mob> boolean hasNearbyAt(ServerLevel level, Vec3 center, EntityType<T> type) {
        return !level.getEntities(type, new AABB(center, center).inflate(NEARBY_RADIUS),
                entity -> entity.isAlive() && !entity.isRemoved()
                        && entity.position().distanceToSqr(center) <= NEARBY_RADIUS * NEARBY_RADIUS).isEmpty();
    }

    private static <T extends Mob> boolean trySpawn(ServerLevel level, ServerPlayer player, Rule<T> rule) {
        EntityType<T> type = rule.type.get();
        for (int attempt = 0; attempt < POSITION_ATTEMPTS; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = 32.0 + level.getRandom().nextDouble() * 32.0;
            int x = Mth.floor(player.getX() + Math.cos(angle) * radius);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * radius);
            var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null || !level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(x >> 4, z >> 4))) {
                continue;
            }
            BlockPos position;
            if (level.dimension() == Level.NETHER) {
                position = findNetherPosition(level, player, type, x, z);
                if (position == null) continue;
            } else {
                // Preserve the existing surface search for all Overworld species.
                int surfaceY = chunk.getHeight(SpawnPlacements.getHeightmapType(type), x, z) + 1;
                position = new BlockPos(x, surfaceY, z);
            }
            int y = position.getY();
            double centerX = x + 0.5;
            double centerZ = z + 0.5;
            Vec3 center = new Vec3(centerX, y, centerZ);
            AABB body = type.getSpawnAABB(centerX, y, centerZ);
            if (player.distanceToSqr(center) > 64.0 * 64.0 || hasNearbyAt(level, center, type)
                    || !safePosition(level, position, body) || !rule.habitat.test(level, position)
                    || !SpawnPlacements.isSpawnPositionOk(type, level, position)
                    || !SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, position, level.getRandom())) {
                continue;
            }
            T mob = type.create(level, EntitySpawnReason.NATURAL);
            if (mob == null) {
                return false;
            }
            mob.snapTo(centerX, y, centerZ, level.getRandom().nextFloat() * 360.0F, 0.0F);
            if (!EventHooks.checkSpawnPosition(mob, level, EntitySpawnReason.NATURAL)) {
                mob.discard();
                return false;
            }
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(position), EntitySpawnReason.NATURAL, null);
            // Hooks may resize or move the mob. Reject altered locations rather than assuming our first check still holds.
            if (mob.level() != level || mob.isRemoved() || !mob.isAlive()
                    || mob.position().distanceToSqr(center) > 1.0E-6
                    || !safePosition(level, mob.blockPosition(), mob.getBoundingBox())
                    || !rule.habitat.test(level, mob.blockPosition()) || !level.noCollision(mob)
                    || !eligiblePlayer(level, player) || !rule.habitat.test(level, player.blockPosition())
                    || player.distanceToSqr(center) > 64.0 * 64.0 || hasNearbyAt(level, center, type)) {
                mob.discard();
                return false;
            }
            boolean accepted = level.addFreshEntity(mob) && mob.isAddedToLevel() && mob.isAlive() && !mob.isRemoved();
            if (!accepted && !mob.isRemoved()) {
                mob.discard();
            }
            return accepted;
        }
        return false;
    }

    /** Bounded cave-floor search; a Nether heightmap would select the inaccessible bedrock roof. */
    private static <T extends Mob> @Nullable BlockPos findNetherPosition(ServerLevel level, ServerPlayer player,
                                                                       EntityType<T> type, int x, int z) {
        int headroom = Mth.ceil(type.getHeight());
        if (player.blockPosition().getY() >= level.getMinY() + level.dimensionType().logicalHeight()) return null;
        int lower = Math.max(level.getMinY() + 1, player.blockPosition().getY() - 32);
        int upper = Math.min(Math.min(level.getMaxY(), level.getMinY() + level.dimensionType().logicalHeight()) - headroom,
                player.blockPosition().getY() + 16);
        if (lower > upper) return null;
        // Visit standing heights nearest the player first; at most 49 cells per loaded column.
        int center = Mth.clamp(player.blockPosition().getY(), lower, upper);
        for (int distance = 0; distance <= 48; distance++) {
            int above = center + distance;
            if (above <= upper && emptyNetherStandingPosition(level, type, new BlockPos(x, above, z), headroom))
                return new BlockPos(x, above, z);
            int below = center - distance;
            if (distance > 0 && below >= lower && emptyNetherStandingPosition(level, type, new BlockPos(x, below, z), headroom))
                return new BlockPos(x, below, z);
            if (above > upper && below < lower) break;
        }
        return null;
    }

    private static <T extends Mob> boolean emptyNetherStandingPosition(ServerLevel level, EntityType<T> type,
                                                                      BlockPos feet, int headroom) {
        if (!SpawnPlacements.isSpawnPositionOk(type, level, feet)) return false;
        for (int offset = 0; offset < headroom; offset++) {
            BlockPos cell = feet.above(offset);
            var state = level.getBlockState(cell);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, cell, state, state.getFluidState(), type)) return false;
        }
        return true;
    }

    private static boolean safePosition(ServerLevel level, BlockPos position, AABB body) {
        if (body.minY < level.getMinY() || body.maxY > level.getMaxY()
                || !level.getWorldBorder().isWithinBounds(body) || !level.isPositionEntityTicking(position)
                || !level.canSpawnEntitiesInChunk(new ChunkPos(position.getX() >> 4, position.getZ() >> 4))) {
            return false;
        }
        BlockPos minimum = BlockPos.containing(body.minX, body.minY - 1.0, body.minZ);
        BlockPos maximum = BlockPos.containing(body.maxX, body.maxY, body.maxZ);
        long cells = (maximum.getX() - minimum.getX() + 1L) * (maximum.getY() - minimum.getY() + 1L)
                * (maximum.getZ() - minimum.getZ() + 1);
        if (cells <= 0 || cells > 128) {
            return false;
        }
        for (int chunkX = minimum.getX() >> 4; chunkX <= maximum.getX() >> 4; chunkX++) {
            for (int chunkZ = minimum.getZ() >> 4; chunkZ <= maximum.getZ() >> 4; chunkZ++) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null
                        || !level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(chunkX, chunkZ))) {
                    return false;
                }
            }
        }
        if (level.hasNearbyAlivePlayer(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 24.0)) {
            return false;
        }
        var worldSpawn = level.getRespawnData();
        if (worldSpawn.dimension().equals(level.dimension())
                && worldSpawn.pos().closerToCenterThan(new Vec3(position.getX() + 0.5, position.getY(), position.getZ() + 0.5), 24.0)) {
            return false;
        }
        if (!level.noCollision(body) || level.containsAnyLiquid(body)) {
            return false;
        }
        for (BlockPos cell : BlockPos.betweenClosed(minimum, maximum)) {
            if (isHazard(level.getBlockState(cell))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHazard(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE);
    }

    private record Rule<T extends Mob>(Supplier<EntityType<T>> type, Predicate<ServerLevel> activePeriod,
                                      BiPredicate<ServerLevel, BlockPos> habitat) {
    }

    private static final class PlayerAbsence {
        private final WeakReference<ServerPlayer> player;
        private final Map<Rule<?>, AbsenceTimer> timers = new HashMap<>();
        private int ruleCursor;

        private PlayerAbsence(ServerPlayer player) {
            this.player = new WeakReference<>(player);
        }
    }

    /** Absence and retry deadlines measured in world ticks. */
    static final class AbsenceTimer {
        private long absentSince;
        private long nextAttempt;
        private long pausedAt = Long.MIN_VALUE;

        AbsenceTimer(long now) {
            this.reset(now);
        }

        boolean isDue(long now, long delay) {
            if (this.pausedAt != Long.MIN_VALUE) {
                return false;
            }
            if (now < this.absentSince) {
                this.reset(now);
            }
            return now - this.absentSince >= delay && now >= this.nextAttempt;
        }

        void failed(long now, long retry) {
            this.nextAttempt = now + retry;
        }

        void pause(long now) {
            if (now < this.absentSince || (this.pausedAt != Long.MIN_VALUE && now < this.pausedAt)) {
                this.reset(now);
            }
            if (this.pausedAt == Long.MIN_VALUE) {
                this.pausedAt = now;
            }
        }

        void resume(long now) {
            if (this.pausedAt == Long.MIN_VALUE) {
                return;
            }
            if (now < this.pausedAt) {
                this.reset(now);
                return;
            }
            this.absentSince += now - this.pausedAt;
            // Retry is an ordinary game-time cooldown, not additional habitat waiting time.
            this.pausedAt = Long.MIN_VALUE;
        }

        void reset(long now) {
            this.absentSince = now;
            this.nextAttempt = now;
            this.pausedAt = Long.MIN_VALUE;
        }
    }
}
