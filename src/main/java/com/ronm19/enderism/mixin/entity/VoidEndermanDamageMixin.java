package com.ronm19.enderism.mixin.entity;

import com.ronm19.enderism.entity.VoidEnderman;
import com.ronm19.enderism.entity.SkeletonEnderman;
import com.ronm19.enderism.entity.VampireEnderman;
import com.ronm19.enderism.entity.WhiteEnderman;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.jspecify.annotations.Nullable;

/** These species retain ordinary projectile vulnerability; damage processing stays entirely native. */
@Mixin(EnderMan.class)
public abstract class VoidEndermanDamageMixin {
    @Redirect(method = "hurtServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean enderism$projectileDamage(DamageSource source, TagKey<DamageType> tag) {
        return !(((Object)this instanceof VoidEnderman || (Object)this instanceof SkeletonEnderman || (Object)this instanceof VampireEnderman || (Object)this instanceof WhiteEnderman)
                && tag == DamageTypeTags.IS_PROJECTILE) && source.is(tag);
    }

    @ModifyVariable(method = "hurtServer", at = @At("STORE"), index = 4)
    private @Nullable AbstractThrownPotion enderism$vampirePotionDamage(@Nullable AbstractThrownPotion potion) {
        // A potion's separate vanilla exception would otherwise bypass native magical damage entirely.
        return (Object)this instanceof VampireEnderman || (Object)this instanceof WhiteEnderman ? null : potion;
    }
}
