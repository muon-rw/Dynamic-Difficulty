package dev.muon.dynamic_difficulty.player;

/**
 * Affects only the level displayed above the player's head. Mob scaling uses each
 * provider's calculateBonusLevels() method, which may aggregate differently (defaults
 * to averaging).
 */
public enum PlayerLevelDisplayStrategy {
    HIGHEST_PRIORITY,

    MAX,

    SUM,

    AVERAGE,

    FIRST
}

