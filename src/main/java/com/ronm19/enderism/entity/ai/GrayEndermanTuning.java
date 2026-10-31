package com.ronm19.enderism.entity.ai;

/** Provisional Gray balancing values; none of these ranges/cooldowns are established lore. */
public final class GrayEndermanTuning {
    public static final double DETECTION_RANGE = 24.0;
    public static final int SENSOR_INTERVAL_TICKS = 10;
    public static final int LOCATION_MEMORY_TICKS = 100;
    public static final double MOVEMENT_THRESHOLD = 0.025;
    public static final double MAXIMUM_MOVEMENT_SAMPLE = 4.0;
    public static final int FORCED_MOVEMENT_GRACE_TICKS = 10;
    public static final double PRESENCE_CELL_SIZE = 8.0;
    public static final int OBSERVATION_TICKS = 20;
    public static final int PATH_REFRESH_TICKS = 20;
    public static final int IDLE_STILLNESS_TICKS = 35;
    public static final int IDLE_STILLNESS_INTERVAL_TICKS = 140;
    public static final double ATTACK_DAMAGE = 10.0;
    public static final double ATTACK_KNOCKBACK = 0.45;
    public static final int MELEE_COOLDOWN_TICKS = 20;
    public static final double STALK_SPEED = 1.12;
    public static final int PALE_AMBUSH_COOLDOWN_TICKS = 160;
    public static final int PALE_AMBUSH_SCAN_RADIUS = 12;
    public static final int PALE_AMBUSH_VERTICAL_RADIUS = 4;
    public static final int PALE_AMBUSH_BLOCK_BUDGET = 4096;
    public static final int PALE_AMBUSH_DESTINATION_BUDGET = 128;
    public static final double PALE_AMBUSH_MIN_TARGET_DISTANCE = 3.0;
    public static final double PALE_AMBUSH_MAX_TARGET_DISTANCE = 12.0;
    public static final int PALE_AMBUSH_VISUAL_TICKS = 12;
    public static final double ECLIPSE_RANGE = 12.0;
    public static final int ECLIPSE_WARNING_TICKS = 20;
    public static final int ECLIPSE_COOLDOWN_TICKS = 240;
    // Confirmed ability rule: exactly five seconds, never a configurable substitute for Darkness.
    public static final int BLINDNESS_TICKS = 100;

    private GrayEndermanTuning() {
    }
}
