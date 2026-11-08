package com.ronm19.enderism.mixin.village;

import com.ronm19.enderism.entity.EndermanGolem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only the new village guardian is exempted from the native Enemy-based target policy. */
@Mixin(IronGolem.class)
public abstract class IronGolemMixin extends AbstractGolem {
    protected IronGolemMixin(EntityType<? extends AbstractGolem> type, Level level) { super(type, level); }
    @Override protected boolean considersEntityAsAlly(Entity entity) {
        return entity instanceof EndermanGolem || super.considersEntityAsAlly(entity);
    }
    @Override public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target instanceof EndermanGolem ? null : target);
        if (this.getTarget() instanceof EndermanGolem) super.setTarget(null);
    }
    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void enderism$recognizeGuardian(LivingEntity target, CallbackInfoReturnable<Boolean> callback) {
        if (target instanceof EndermanGolem) callback.setReturnValue(false);
    }
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void enderism$protectGuardian(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> callback) {
        if (target instanceof EndermanGolem) callback.setReturnValue(false);
    }
}
