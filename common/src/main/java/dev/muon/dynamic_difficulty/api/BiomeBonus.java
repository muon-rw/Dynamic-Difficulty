package dev.muon.dynamic_difficulty.api;

import net.minecraft.resources.Identifier;

/**
 * Result containing biome bonus information at a position.
 * 
 * @param biomeId The biome at the position (null if lookup failed)
 * @param nonBypassingBonus Bonus that doesn't bypass cap
 * @param bypassingBonus Bonus that bypasses cap
 */
public record BiomeBonus(
    Identifier biomeId,
    int nonBypassingBonus,
    int bypassingBonus
) {
    /** Empty result for when there's no biome bonus configured */
    public static final BiomeBonus EMPTY = new BiomeBonus(null, 0, 0);
    
    public int totalBonus() {
        return nonBypassingBonus + bypassingBonus;
    }
}

