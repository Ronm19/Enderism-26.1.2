package com.ronm19.enderism.entity;

import com.ronm19.enderism.entity.ai.MastermindBrain;
import com.ronm19.enderism.CombatBalanceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

/** Shared server-authoritative foundation for Enderism Endermen. */
public abstract class AbstractEnderman extends EnderMan {
    private MastermindBrain mastermindBrain;
    private long nextTeleportAttempt;
    private long committedMeleeAttempts;

    protected AbstractEnderman(EntityType<? extends AbstractEnderman> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // Native Brain activities own combat, floating, and ambient movement; no competing selectors.
    }

    @Override
    protected Brain<AbstractEnderman> makeBrain(Brain.Packed packedBrain) {
        // LivingEntity invokes this before Mob creates navigation; actuators initialize on the first Brain tick.
        this.mastermindBrain = this.createMastermindBrain();
        return MastermindBrain.provider(this.mastermindBrain).makeBrain(this, packedBrain);
    }

    protected MastermindBrain createMastermindBrain() {
        return new MastermindBrain(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Brain<AbstractEnderman> getBrain() {
        return (Brain<AbstractEnderman>) super.getBrain();
    }

    public MastermindBrain getMastermindBrain() {
        return this.mastermindBrain;
    }

    @Override
    public boolean isSensitiveToWater() {
        return false;
    }

    @Override
    protected AABB getAttackBoundingBox(double horizontalExpansion) {
        double reach = CombatBalanceConfig.enabled() && this.getActiveItem().get(DataComponents.ATTACK_RANGE) == null
                ? CombatBalanceConfig.meleeReachBonus() : 0.0;
        return super.getAttackBoundingBox(horizontalExpansion + reach);
    }

    /** Counts physical attempts, including native shield blocks and canceled damage. */
    public final long committedMeleeAttempts() {
        return this.committedMeleeAttempts;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        this.committedMeleeAttempts++;
        return super.doHurtTarget(level, target);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);
        // Keep vanilla idle daylight escape from overriding an active encounter.
        if (this.mastermindBrain.getState() == MastermindBrain.State.IDLE) {
            super.customServerAiStep(level);
        }
    }

    @Override
    protected boolean teleport() {
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive()
                || level.getGameTime() < this.nextTeleportAttempt) {
            return false;
        }
        double x = this.getX() + (this.random.nextDouble() - 0.5) * 64.0;
        double z = this.getZ() + (this.random.nextDouble() - 0.5) * 64.0;
        double y = Mth.clamp(this.getY() + this.random.nextInt(64) - 32,
                level.getMinY() + 1.0, level.getMaxY() - this.getBbHeight());
        return this.trySafeTeleport(new Vec3(x, y, z), 32);
    }

    protected int teleportAttemptCooldownTicks() {
        return 20;
    }

    /** A single bounded attempt shared by defensive and species-directed teleportation. */
    protected final boolean trySafeTeleport(Vec3 requested, int maximumDownwardSearch) {
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved()
                || this.isPassenger() || this.isVehicle() || level.getGameTime() < this.nextTeleportAttempt) {
            return false;
        }
        this.nextTeleportAttempt = level.getGameTime() + Math.max(1, this.teleportAttemptCooldownTicks());
        return this.teleportToValidatedGround(level, requested, maximumDownwardSearch, false);
    }

    /** Passive recovery has its own species-owned retry budget; active cooldowns cannot veto survival. */
    protected final boolean trySafeRecoveryTeleport(Vec3 requested, int maximumDownwardSearch) {
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved()
                || this.isPassenger() || this.isVehicle()) return false;
        return this.teleportToValidatedGround(level, requested, maximumDownwardSearch, true);
    }

    private boolean teleportToValidatedGround(ServerLevel level, Vec3 requested, int maximumDownwardSearch, boolean recovery) {
        int searchLimit = Mth.clamp(maximumDownwardSearch, 0, 32);
        Vec3 destination = this.findSafeStandingPosition(level, requested, searchLimit, recovery);
        if (destination == null) {
            return false;
        }
        var event = EventHooks.onEnderTeleport(this, destination.x, destination.y, destination.z);
        if (event.isCanceled() || !this.isAlive() || this.isRemoved() || this.level() != level) {
            return false;
        }
        // Listeners may change the destination. Validate that final position before any move/world access.
        destination = this.findSafeStandingPosition(level, event.getTarget(), searchLimit, recovery);
        if (destination == null || !this.isSpeciesTeleportDestinationValid(level, destination)) {
            return false;
        }
        Vec3 oldPosition = this.position();
        this.teleportTo(destination.x, destination.y, destination.z);
        this.getNavigation().stop();
        this.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 0.0);
        level.broadcastEntityEvent(this, (byte) 46);
        level.gameEvent(GameEvent.TELEPORT, oldPosition, GameEvent.Context.of(this));
        if (!this.isSilent()) {
            level.playSound(null, oldPosition.x, oldPosition.y, oldPosition.z,
                    SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0F, 1.0F);
            this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
        }
        return true;
    }

    /** Species may retain extra terrain constraints after teleport listeners redirect a move. */
    protected boolean isSpeciesTeleportDestinationValid(ServerLevel level, Vec3 destination) {
        return true;
    }

    /** Read-only planning; the actual teleport still validates its event-adjusted destination. */
    protected final @Nullable Vec3 findSafeTeleportDestination(Vec3 requested, int maximumDownwardSearch) {
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved()
                || this.isPassenger() || this.isVehicle()) return null;
        Vec3 destination = this.findSafeStandingPosition(level, requested, Mth.clamp(maximumDownwardSearch, 0, 32), false);
        return destination != null && this.isSpeciesTeleportDestinationValid(level, destination) ? destination : null;
    }

    /** Only the vertical range differs: a validated remembered surface can be far above a falling entity. */
    protected final @Nullable Vec3 findSafeRecoveryDestination(Vec3 requested, int maximumDownwardSearch) {
        if (!(this.level() instanceof ServerLevel level) || !this.isAlive() || this.isRemoved()
                || this.isPassenger() || this.isVehicle()) return null;
        Vec3 destination = this.findSafeStandingPosition(level, requested, Mth.clamp(maximumDownwardSearch, 0, 32), true);
        return destination != null && this.isSpeciesTeleportDestinationValid(level, destination) ? destination : null;
    }

    private boolean withinTeleportRange(Vec3 position, boolean recovery) {
        if (!recovery) return this.position().distanceToSqr(position) <= 64.0 * 64.0;
        double dx = position.x - this.getX(), dz = position.z - this.getZ();
        return Double.isFinite(this.getY()) && dx * dx + dz * dz <= 128.0 * 128.0;
    }

    private @Nullable Vec3 findSafeStandingPosition(ServerLevel level, Vec3 requested, int searchLimit, boolean recovery) {
        if (!Double.isFinite(requested.x) || !Double.isFinite(requested.y) || !Double.isFinite(requested.z)
                || !this.withinTeleportRange(requested, recovery)
                || requested.y < level.getMinY() + 1 || requested.y + this.getBbHeight() > level.getMaxY()) {
            return null;
        }
        BlockPos feet = BlockPos.containing(requested);
        if (level.getChunkSource().getChunkNow(feet.getX() >> 4, feet.getZ() >> 4) == null
                || !level.getWorldBorder().isWithinBounds(feet)) {
            return null;
        }
        int searched = 0;
        while (feet.getY() > level.getMinY() + 1 && !level.getBlockState(feet.below()).blocksMotion()
                && searched < searchLimit) {
            feet = feet.below();
            searched++;
        }
        BlockState support = level.getBlockState(feet.below());
        if (!support.blocksMotion() || this.isTeleportHazard(support)) {
            return null;
        }
        Vec3 position = new Vec3(requested.x, feet.getY(), requested.z);
        if (!this.withinTeleportRange(position, recovery)) {
            return null;
        }
        AABB box = this.getBoundingBox().move(position.subtract(this.position()));
        BlockPos minimum = BlockPos.containing(box.minX, box.minY - 1, box.minZ);
        BlockPos maximum = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        long cells = (long) (maximum.getX() - minimum.getX() + 1)
                * (maximum.getY() - minimum.getY() + 1) * (maximum.getZ() - minimum.getZ() + 1);
        if (cells > 128L || !level.getWorldBorder().isWithinBounds(box) || !areTeleportChunksReady(level, minimum, maximum)
                || !level.noCollision(this, box) || level.containsAnyLiquid(box)) {
            return null;
        }
        // Enderman dimensions keep this local check below a few dozen cells.
        for (BlockPos pos : BlockPos.betweenClosed(minimum, maximum)) {
            if (this.isTeleportHazard(level.getBlockState(pos))) {
                return null;
            }
        }
        return position;
    }

    private static boolean areTeleportChunksReady(ServerLevel level, BlockPos minimum, BlockPos maximum) {
        for (int x = minimum.getX() >> 4; x <= maximum.getX() >> 4; x++) {
            for (int z = minimum.getZ() >> 4; z <= maximum.getZ() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    private boolean isTeleportHazard(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE);
    }

    @Override
    public void setNoAi(boolean value) {
        super.setNoAi(value);
        if (value && this.mastermindBrain != null) this.mastermindBrain.groupTactics().clear();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.isAlive() && this.mastermindBrain != null) this.mastermindBrain.groupTactics().clear();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.mastermindBrain != null) this.mastermindBrain.groupTactics().clear();
        if (this.level() instanceof ServerLevel level) {
            this.getBrain().stopAll(level, this);
        } else if (this.mastermindBrain != null) {
            this.mastermindBrain.clearEncounter();
        }
        super.remove(reason);
    }
}
