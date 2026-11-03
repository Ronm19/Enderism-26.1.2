package com.ronm19.enderism.effect;

import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.SilverEndermanConfig;
import com.ronm19.enderism.entity.SilverEnderman;
import com.ronm19.enderism.registry.ModEffects;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Owned, temporary armor protection reduction. Never accesses or changes equipment. */
public final class ArmorFracture extends MobEffect {
    public static final Identifier ARMOR_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(Enderism.MOD_ID, "silver_armor_fracture");
    private static final Map<UUID, Fracture> FRACTURES = new HashMap<>();
    private static final Map<UUID, Recovery> RECOVERY = new HashMap<>();

    public ArmorFracture() {
        super(MobEffectCategory.HARMFUL, 0xD9DEF0);
        // Describe the same amount in the native effect tooltip, independent of amplifier.
        addAttributeModifier(Attributes.ARMOR, ARMOR_MODIFIER_ID,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, ignored -> -reductionFraction());
    }

    public static void registerEvents(IEventBus bus) {
        bus.addListener(ArmorFracture::serverTick);
        bus.addListener(ArmorFracture::logout);
        bus.addListener(ArmorFracture::login);
        bus.addListener(ArmorFracture::dimensionChanged);
        bus.addListener(ArmorFracture::death);
        bus.addListener(ArmorFracture::serverStopping);
        bus.addListener(ArmorFracture::serverStopped);
    }

    public static boolean canApply(ServerPlayer player) {
        if (!eligible(player) || FRACTURES.containsKey(player.getUUID())
                || player.hasEffect(ModEffects.ARMOR_FRACTURE) || hasModifier(player)
                || player.getAttributeValue(Attributes.ARMOR) <= 0 || reductionFraction() <= 0) return false;
        Recovery recovery = RECOVERY.get(player.getUUID());
        MinecraftServer server = player.level().getServer();
        return recovery == null || recovery.server.get() != server || server.getTickCount() >= recovery.until;
    }

    /** The first successful owner wins; subsequent hits and other Silvers cannot extend this window. */
    public static boolean tryApply(ServerPlayer player, SilverEnderman source) {
        int duration = SilverEndermanConfig.fractureDurationTicks();
        if (!canApply(player) || duration < 1 || !source.isAlive() || source.isRemoved()
                || source.level() != player.level()) return false;
        ServerLevel level = (ServerLevel) player.level();
        Fracture fracture = new Fracture(player, source, level, level.getGameTime() + duration);
        FRACTURES.put(player.getUUID(), fracture);
        boolean added = player.addEffect(new MobEffectInstance(ModEffects.ARMOR_FRACTURE,
                duration, 0, false, false, true), source);
        if (!added || FRACTURES.get(player.getUUID()) != fracture
                || !player.hasEffect(ModEffects.ARMOR_FRACTURE) || !hasModifier(player) || !valid(fracture)) {
            if (FRACTURES.remove(player.getUUID(), fracture) || !FRACTURES.containsKey(player.getUUID())) {
                player.removeEffect(ModEffects.ARMOR_FRACTURE);
                removeModifier(player.getAttributes());
            }
            return false;
        }
        showShards(level, player, 7);
        return true;
    }

    public static boolean isAppliedBy(ServerPlayer player, SilverEnderman source) {
        Fracture fracture = FRACTURES.get(player.getUUID());
        return fracture != null && fracture.player.get() == player && fracture.source.get() == source
                && valid(fracture) && player.hasEffect(ModEffects.ARMOR_FRACTURE) && hasModifier(player);
    }

    public static void release(ServerPlayer player, SilverEnderman source) {
        Fracture fracture = FRACTURES.get(player.getUUID());
        if (fracture != null && fracture.player.get() == player && fracture.source.get() == source)
            release(player.getUUID(), fracture, true, true);
    }

    /** Also clears a stale status/modifier after login or an interrupted encounter. */
    public static void release(ServerPlayer player) {
        clearPlayer(player);
    }

    public static void releaseOwnedBy(SilverEnderman source) {
        for (var entry : Map.copyOf(FRACTURES).entrySet()) {
            if (entry.getValue().source.get() == source) release(entry.getKey(), entry.getValue(), true, true);
        }
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.hasDisconnected()
                && !player.isCreative() && !player.isSpectator();
    }

    private static boolean valid(Fracture fracture) {
        ServerPlayer player = fracture.player.get();
        SilverEnderman source = fracture.source.get();
        return player != null && source != null && eligible(player) && source.isAlive() && !source.isRemoved()
                && player.level() == fracture.level && source.level() == fracture.level
                && fracture.level.getGameTime() < fracture.until;
    }

    private static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        RECOVERY.entrySet().removeIf(entry -> entry.getValue().server.get() == null
                || entry.getValue().server.get() == server && now >= entry.getValue().until);
        for (var entry : Map.copyOf(FRACTURES).entrySet()) {
            Fracture fracture = entry.getValue();
            if (fracture.level.getServer() != server) continue;
            ServerPlayer player = fracture.player.get();
            if (!valid(fracture) || player == null || !player.hasEffect(ModEffects.ARMOR_FRACTURE)
                    || !hasModifier(player)) release(entry.getKey(), fracture, true, true);
        }
    }

    private static void release(UUID id, Fracture fracture, boolean removeStatus, boolean particles) {
        if (!FRACTURES.remove(id, fracture)) return;
        MinecraftServer server = fracture.level.getServer();
        RECOVERY.put(id, new Recovery(new WeakReference<>(server),
                server.getTickCount() + SilverEndermanConfig.fractureRecoveryTicks()));
        ServerPlayer player = fracture.player.get();
        if (player == null) return;
        if (removeStatus) player.removeEffect(ModEffects.ARMOR_FRACTURE);
        // Remove only our unique modifier even if another event cancels status removal.
        removeModifier(player.getAttributes());
        if (particles && player.isAlive() && !player.isRemoved() && !player.hasDisconnected()
                && player.level() == fracture.level) showShards(fracture.level, player, 3);
    }

    private static void clearPlayer(ServerPlayer player) {
        Fracture fracture = FRACTURES.get(player.getUUID());
        if (fracture != null) {
            release(player.getUUID(), fracture, true, false);
            if (fracture.player.get() == player) return;
        }
        player.removeEffect(ModEffects.ARMOR_FRACTURE);
        removeModifier(player.getAttributes());
    }

    private static void showShards(ServerLevel level, ServerPlayer player, int count) {
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + .9, player.getZ(),
                count, .3, .55, .3, .025);
    }

    private static boolean hasModifier(ServerPlayer player) {
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        return armor != null && armor.hasModifier(ARMOR_MODIFIER_ID);
    }

    private static double reductionFraction() {
        return Math.clamp(SilverEndermanConfig.fractureReduction(), 0, .95);
    }

    @Override
    public void addAttributeModifiers(AttributeMap attributes, int amplifier) {
        AttributeInstance armor = attributes.getInstance(Attributes.ARMOR);
        if (armor != null) {
            // Native MobEffect's default adds permanent modifiers; this effect must remain transient.
            armor.removeModifier(ARMOR_MODIFIER_ID);
            armor.addTransientModifier(new AttributeModifier(ARMOR_MODIFIER_ID, -reductionFraction(),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @Override
    public void removeAttributeModifiers(AttributeMap attributes) {
        removeModifier(attributes);
    }

    private static void removeModifier(AttributeMap attributes) {
        AttributeInstance armor = attributes.getInstance(Attributes.ARMOR);
        if (armor != null) armor.removeModifier(ARMOR_MODIFIER_ID);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplifier) {
        if (!(mob instanceof ServerPlayer player)) return false;
        Fracture fracture = FRACTURES.get(player.getUUID());
        return fracture != null && fracture.player.get() == player && valid(fracture) && hasModifier(player);
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity mob, int amplifier, Entity.RemovalReason reason) {
        if (mob instanceof ServerPlayer player) {
            Fracture fracture = FRACTURES.get(player.getUUID());
            // Native death cleanup is iterating its effect collection and clears it immediately afterward.
            if (fracture != null && fracture.player.get() == player)
                release(player.getUUID(), fracture, false, false);
            removeModifier(player.getAttributes());
        }
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clearPlayer(player);
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clearPlayer(player);
    }

    private static void dimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clearPlayer(player);
    }

    private static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clearPlayer(player);
    }

    private static void serverStopping(ServerStoppingEvent event) {
        clearServer(event.getServer());
    }

    private static void serverStopped(ServerStoppedEvent event) {
        clearServer(event.getServer());
    }

    private static void clearServer(MinecraftServer server) {
        for (var entry : Map.copyOf(FRACTURES).entrySet()) {
            if (entry.getValue().level.getServer() == server) release(entry.getKey(), entry.getValue(), true, false);
        }
        if (server.getPlayerList() != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) clearPlayer(player);
        }
        RECOVERY.entrySet().removeIf(entry -> entry.getValue().server.get() == null || entry.getValue().server.get() == server);
    }

    private record Fracture(WeakReference<ServerPlayer> player, WeakReference<SilverEnderman> source,
                            ServerLevel level, long until) {
        private Fracture(ServerPlayer player, SilverEnderman source, ServerLevel level, long until) {
            this(new WeakReference<>(player), new WeakReference<>(source), level, until);
        }
    }

    private record Recovery(WeakReference<MinecraftServer> server, int until) {
    }
}
