package dev.muon.dynamic_difficulty.player;

/**
 * Defines how multiple PlayerLevelProviders should be aggregated for display purposes.
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

