package com.ronm19.enderism.raid;

import java.util.Map;
import java.util.UUID;

/** The native raid retains its normal Raider collections; Enderism owns only its real participants. */
public interface RavagerRaidAccess {
    Map<UUID, RavagerRaids.Member> enderism$participants();
    float enderism$healthBudget();
    void enderism$healthBudget(float health);
    int enderism$lastAttemptedWave();
    void enderism$lastAttemptedWave(int wave);
}
