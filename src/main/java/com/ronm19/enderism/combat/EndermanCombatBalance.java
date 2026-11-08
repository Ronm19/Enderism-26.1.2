package com.ronm19.enderism.combat;

import com.ronm19.enderism.CombatBalanceConfig;
import com.ronm19.enderism.Enderism;
import com.ronm19.enderism.entity.AbstractEnderman;
import com.ronm19.enderism.entity.ai.MastermindBrain;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Shared native combat adjustments; never selects targets or replaces species abilities. */
public final class EndermanCombatBalance {
    public static final Identifier HEALTH_MODIFIER_ID = id("combat_health");
    public static final Identifier KNOCKBACK_MODIFIER_ID = id("combat_knockback_resistance");
    public static final Identifier PURSUIT_MODIFIER_ID = id("combat_armored_pursuit");
    private static final double LIGHT_KNOCKBACK_LIMIT = .5;
    private static final double PROFILE_KNOCKBACK_CAP = .45;

    private EndermanCombatBalance() {}

    public static void registerEvents(IEventBus bus) {
        bus.addListener(EndermanCombatBalance::joined);
        bus.addListener(EndermanCombatBalance::ticked);
        bus.addListener(EndermanCombatBalance::incomingDamage);
    }

    private static void joined(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof AbstractEnderman enderman && event.getLevel() instanceof ServerLevel)
            reconcile(enderman);
    }

    private static void ticked(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractEnderman enderman)
                || !(enderman.level() instanceof ServerLevel)) return;
        // Stagger work across entities; config changes also reach already loaded creatures.
        int phase = Math.floorMod(enderman.tickCount + enderman.getId(), 20);
        if (phase == 0) reconcileAttributes(enderman);
        if (phase % 5 == 0) reconcilePursuit(enderman);
    }

    /** Idempotent reconciliation, also useful when checking an entity without advancing its world. */
    public static void reconcile(AbstractEnderman enderman) {
        if (!(enderman.level() instanceof ServerLevel)) return;
        reconcileAttributes(enderman);
        reconcilePursuit(enderman);
    }

    private static void reconcileAttributes(AbstractEnderman enderman) {
        boolean enabled = CombatBalanceConfig.enabled();
        AttributeInstance health = enderman.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double amount = enabled ? CombatBalanceConfig.healthMultiplier() - 1 : 0;
            float oldMaximum = enderman.getMaxHealth();
            float oldHealth = enderman.getHealth();
            if (setModifier(health, HEALTH_MODIFIER_ID, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true)) {
                // A permanent modifier is restored before species load hooks clamp health.
                // Equal modifiers are untouched, so joins/reloads cannot refill a wounded creature.
                double fraction = oldMaximum > 0 ? Math.clamp(oldHealth / oldMaximum, 0, 1) : 0;
                enderman.setHealth((float)(enderman.getMaxHealth() * fraction));
            }
        }
        AttributeInstance resistance = enderman.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (resistance != null) {
            double unmodified = valueWithoutOwnModifier(resistance, KNOCKBACK_MODIFIER_ID, 0);
            double amount = 0;
            if (enabled && unmodified < LIGHT_KNOCKBACK_LIMIT) {
                // Account for other modifiers without removing/readding ours every tick.
                double coefficient = valueWithoutOwnModifier(resistance, KNOCKBACK_MODIFIER_ID, 1) - unmodified;
                if (coefficient > 0) amount = Math.min(CombatBalanceConfig.knockbackResistanceBonus(),
                        Math.max(0, PROFILE_KNOCKBACK_CAP - unmodified) / coefficient);
            }
            setModifier(resistance, KNOCKBACK_MODIFIER_ID, amount, AttributeModifier.Operation.ADD_VALUE, true);
        }
    }

    private static void reconcilePursuit(AbstractEnderman enderman) {
        AttributeInstance speed = enderman.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        double amount = CombatBalanceConfig.enabled() && fightingArmoredPlayer(enderman)
                ? CombatBalanceConfig.pursuitSpeedBonus() : 0;
        setModifier(speed, PURSUIT_MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
    }

    private static boolean fightingArmoredPlayer(AbstractEnderman enderman) {
        if (!enderman.isAlive() || enderman.isRemoved() || enderman.isNoAi()
                || enderman.getMastermindBrain().getState() != MastermindBrain.State.COMBAT) return false;
        LivingEntity target = enderman.getTarget();
        if (target == null) target = enderman.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null) target = enderman.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        return target instanceof Player player && eligible(player) && player.level() == enderman.level()
                && enderman.canAttack(player) && !enderman.isAlliedTo(player)
                && player.getAttributeValue(Attributes.ARMOR) > 0;
    }

    private static void incomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && player.level() instanceof ServerLevel)
            event.setAmount(scaledPlayerDamage(player, event.getSource(), event.getAmount()));
    }

    /** Pre-armor scaling only; native shields, armor, enchantments and hit immunity remain authoritative. */
    public static float scaledPlayerDamage(Player player, DamageSource source, float amount) {
        if (!CombatBalanceConfig.enabled() || !Float.isFinite(amount) || amount <= 0 || !eligible(player)
                || !(source.getEntity() instanceof AbstractEnderman)
                || source.is(DamageTypeTags.BYPASSES_ARMOR)) return amount;
        double armor = player.getAttributeValue(Attributes.ARMOR);
        if (armor <= 0) return amount;
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double bonus = Math.clamp(armor / 20, 0, 1) * CombatBalanceConfig.armorDamageBonus()
                + Math.clamp(toughness / 12, 0, 1) * CombatBalanceConfig.toughnessDamageBonus();
        return (float)(amount + Math.min(CombatBalanceConfig.maxAddedDamage(), amount * bonus));
    }

    private static boolean eligible(Player player) {
        return player.isAlive() && !player.isRemoved() && !player.isCreative() && !player.isSpectator()
                && (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.hasDisconnected());
    }

    private static boolean setModifier(AttributeInstance attribute, Identifier id, double amount,
            AttributeModifier.Operation operation, boolean permanent) {
        AttributeModifier current = attribute.getModifier(id);
        if (amount == 0) return current != null && attribute.removeModifier(id);
        AttributeModifier desired = new AttributeModifier(id, amount, operation);
        if (desired.equals(current) && attribute.getPermanentModifiers().contains(current) == permanent) return false;
        attribute.removeModifier(id);
        if (permanent) attribute.addPermanentModifier(desired);
        else attribute.addTransientModifier(desired);
        return true;
    }

    /** Mirrors native attribute arithmetic without our additive resistance modifier. */
    private static double valueWithoutOwnModifier(AttributeInstance attribute, Identifier ownId, double extraBase) {
        double base = attribute.getBaseValue() + extraBase;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (!modifier.id().equals(ownId) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE)
                base += modifier.amount();
        }
        double result = base;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (!modifier.id().equals(ownId) && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
                result += base * modifier.amount();
        }
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (!modifier.id().equals(ownId) && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                result *= 1 + modifier.amount();
        }
        return result;
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Enderism.MOD_ID, path); }
}
