package com.ronm19.enderism.client;

import com.ronm19.enderism.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/** Temporary input wrapper; camera input is untouched and the original input is restored on release. */
public final class ArcticStunClient {
    private ArcticStunClient() {
    }

    public static void tick() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean stunned = player.isAlive() && !player.isCreative() && !player.isSpectator()
                && (player.hasEffect(ModEffects.ARCTIC_STUN) || player.hasEffect(ModEffects.FROST_PARALYSIS));
        if (stunned && !(player.input instanceof FrozenInput)) {
            player.input = new FrozenInput(player.input);
        } else if (!stunned && player.input instanceof FrozenInput frozen) {
            player.input = frozen.original;
        }
    }

    private static final class FrozenInput extends ClientInput {
        private final ClientInput original;

        private FrozenInput(ClientInput original) { this.original = original; }

        @Override
        public void tick() {
            this.original.tick();
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
        }
    }
}
