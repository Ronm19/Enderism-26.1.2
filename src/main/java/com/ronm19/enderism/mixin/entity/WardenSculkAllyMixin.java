package com.ronm19.enderism.mixin.entity;

import com.ronm19.enderism.entity.SculkEnderman;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native anger already respects the symmetric alliance; direct retaliation must do so as well. */
@Mixin(Warden.class)
public abstract class WardenSculkAllyMixin {
    @Inject(method = "setAttackTarget", at = @At("HEAD"), cancellable = true)
    private void enderism$keepSculkAlly(LivingEntity target, CallbackInfo callback) {
        if (target instanceof SculkEnderman) {
            ((Warden)(Object)this).clearAnger(target);
            callback.cancel();
        }
    }

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void enderism$avoidSculkFriendlyFire(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> callback) {
        if (target instanceof SculkEnderman) callback.setReturnValue(false);
    }
}
