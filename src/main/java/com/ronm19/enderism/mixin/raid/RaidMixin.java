package com.ronm19.enderism.mixin.raid;

import com.ronm19.enderism.raid.RavagerRaidAccess;
import com.ronm19.enderism.raid.RavagerRaids;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A counted non-Raider adapter, without proxy entities or changes to vanilla raider membership. */
@Mixin(Raid.class)
public abstract class RaidMixin implements RavagerRaidAccess {
    @Shadow private float totalHealth;
    @Shadow @Final private ServerBossEvent raidEvent;
    @Unique private final Map<UUID, RavagerRaids.Member> enderism$participants = new LinkedHashMap<>();
    @Unique private float enderism$healthBudget;
    @Unique private int enderism$lastAttemptedWave = -1;

    @Override public Map<UUID, RavagerRaids.Member> enderism$participants() { return this.enderism$participants; }
    @Override public float enderism$healthBudget() { return this.enderism$healthBudget; }
    @Override public void enderism$healthBudget(float value) { this.enderism$healthBudget = Math.max(0, value); }
    @Override public int enderism$lastAttemptedWave() { return this.enderism$lastAttemptedWave; }
    @Override public void enderism$lastAttemptedWave(int value) { this.enderism$lastAttemptedWave = value; }

    @Inject(method = "spawnGroup", at = @At("HEAD"))
    private void enderism$beginWave(ServerLevel level, BlockPos pos, CallbackInfo callback) {
        RavagerRaids.beforeWave((Raid)(Object)this);
    }
    @Inject(method = "spawnGroup", at = @At("TAIL"))
    private void enderism$joinWave(ServerLevel level, BlockPos pos, CallbackInfo callback) {
        RavagerRaids.onWaveSpawn(level, (Raid)(Object)this, pos);
    }
    @Inject(method = "tick", at = @At("HEAD"))
    private void enderism$maintainParticipants(ServerLevel level, CallbackInfo callback) {
        RavagerRaids.prune(level, (Raid)(Object)this);
    }
    @Inject(method = "tick", at = @At("TAIL"))
    private void enderism$finishParticipants(ServerLevel level, CallbackInfo callback) {
        RavagerRaids.prune(level, (Raid)(Object)this);
    }
    @Inject(method = "stop", at = @At("TAIL"))
    private void enderism$stopParticipants(CallbackInfo callback) {
        RavagerRaids.clear((Raid)(Object)this);
    }
    @Inject(method = "getTotalRaidersAlive", at = @At("RETURN"), cancellable = true)
    private void enderism$countParticipants(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(callback.getReturnValueI() + RavagerRaids.count((Raid)(Object)this));
    }
    @Inject(method = "getHealthOfLivingRaiders", at = @At("RETURN"), cancellable = true)
    private void enderism$countHealth(CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(callback.getReturnValueF() + RavagerRaids.health((Raid)(Object)this));
    }
    @Inject(method = "getTotalHealth", at = @At("RETURN"), cancellable = true)
    private void enderism$includeBudget(CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(callback.getReturnValueF() + this.enderism$healthBudget);
    }
    @Inject(method = "updateBossbar", at = @At("HEAD"), cancellable = true)
    private void enderism$updateHealthBar(CallbackInfo callback) {
        if (this.enderism$healthBudget <= 0) return;
        Raid raid = (Raid)(Object)this;
        float maximum = this.totalHealth + this.enderism$healthBudget;
        this.raidEvent.setProgress(maximum <= 0 ? 0 : Mth.clamp(raid.getHealthOfLivingRaiders() / maximum, 0, 1));
        callback.cancel();
    }
}
