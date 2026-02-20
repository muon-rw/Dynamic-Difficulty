package dev.muon.dynamic_difficulty.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Biome bonus at a position.
 *
 * @param biomeId Biome at position (null if lookup failed)
 * @param nonBypassingBonus Bonus that doesn't bypass cap
 * @param bypassingBonus Bonus that bypasses cap
 */
public record BiomeBonus(
    ResourceLocation biomeId,
    int nonBypassingBonus,
    int bypassingBonus
) {
    /** Empty when no biome bonus configured. */
    public static final BiomeBonus EMPTY = new BiomeBonus(null, 0, 0);
    
    public int totalBonus() {
        return nonBypassingBonus + bypassingBonus;
    }
}

