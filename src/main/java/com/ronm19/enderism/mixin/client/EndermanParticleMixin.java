package com.ronm19.enderism.mixin.client;

import com.ronm19.enderism.entity.PhantomEnderman;
import com.ronm19.enderism.entity.AngelEnderman;
import com.ronm19.enderism.entity.BoggedEnderman;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Species-specific presentation preserves the ordinary Enderman particle trail. */
@Mixin(EnderMan.class)
public abstract class EndermanParticleMixin {
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void enderism$stalkingParticles(Level level, ParticleOptions particle,
                                           double x, double y, double z, double dx, double dy, double dz) {
        if ((Object)this instanceof AngelEnderman) return;
        // Bogged's bounded casting/impact cues supply its poisonous presentation.
        if ((Object)this instanceof BoggedEnderman) return;
        if ((Object)this instanceof PhantomEnderman phantom && phantom.isStalking() && phantom.tickCount % 10 != 0) return;
        level.addParticle(particle,x,y,z,dx,dy,dz);
    }
}
