package dev.muon.dynamic_difficulty.api;

/**
 * Defines how multiple PlayerLevelProviders should be aggregated for display purposes.
 * 
 * Note: This strategy only affects what level is displayed above the player's head.
 * Mob scaling uses each provider's calculateBonusLevels() method, which may use
 * different aggregation logic (defaults to averaging).
 */
public enum PlayerLevelDisplayStrategy {
    /**
     * Use only the highest priority provider's level
     */
    HIGHEST_PRIORITY,
    
    /**
     * Use the maximum level from all enabled providers
     */
    MAX,
    
    /**
     * Sum levels from all enabled providers
     */
    SUM,
    
    /**
     * Average levels from all enabled providers
     */
    AVERAGE,
    
    /**
     * Use only the first enabled provider's level
     */
    FIRST
}

