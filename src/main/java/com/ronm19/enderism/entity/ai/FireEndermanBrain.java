package com.ronm19.enderism.entity.ai;

import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.FireEnderman;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** Visible-player acquisition layered onto the shared Brain's pursuit, melee, and finite investigation. */
public final class FireEndermanBrain extends MastermindBrain {
    private static final int PLAYER_SCAN_INTERVAL = 10;
    private final FireEnderman fire;
    private long nextPlayerScanAt;

    public FireEndermanBrain(FireEnderman owner) {
        super(owner);
        this.fire = owner;
    }

    @Override
    protected boolean isLegalThreat(@Nullable LivingEntity target) {
        return super.isLegalThreat(target)
                && (!(target instanceof ServerPlayer player) || !player.hasDisconnected());
    }

    @Override
    protected @Nullable LivingEntity observeThreat() {
        // Shared retaliation/visible memory wins; only acquisition needs a species-specific policy.
        LivingEntity threat = super.observeThreat();
        if (threat != null) return threat;
        if (!(this.owner.level() instanceof ServerLevel level) || level.getGameTime() < this.nextPlayerScanAt) return null;
        this.nextPlayerScanAt = level.getGameTime() + PLAYER_SCAN_INTERVAL;
        double closest = FireEnderman.DETECTION_RANGE * FireEnderman.DETECTION_RANGE;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                this.owner.getBoundingBox().inflate(FireEnderman.DETECTION_RANGE))) {
            double distance = this.owner.distanceToSqr(player);
            if (distance <= closest && this.isVisibleThreat(player)) {
                threat = player;
                closest = distance;
            }
        }
        if (threat != null) this.rememberVisibleThreat(threat);
        return threat;
    }

    @Override
    protected void tick(ServerLevel level, AbstractEnderman body, long now) {
        super.tick(level, body, now);
        this.fire.setHuntState(switch (this.state) {
            case IDLE -> FireEnderman.HuntState.IDLE;
            case COMBAT -> FireEnderman.HuntState.PURSUING;
            case INVESTIGATE -> FireEnderman.HuntState.SEARCHING;
        });
    }

    @Override
    public void clearEncounter() {
        super.clearEncounter();
        this.fire.setHuntState(FireEnderman.HuntState.IDLE);
    }
}
