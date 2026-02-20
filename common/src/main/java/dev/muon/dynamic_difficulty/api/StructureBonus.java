package dev.muon.dynamic_difficulty.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Structure bonus at a position.
 *
 * @param structureId Structure at position (null if none)
 * @param nonBypassingBonus Highest bonus that doesn't bypass cap
 * @param bypassingBonus Highest bonus that bypasses cap
 */
public record StructureBonus(
    ResourceLocation structureId,
    int nonBypassingBonus,
    int bypassingBonus
) {
    /** Empty when no structure or bonus. */
    public static final StructureBonus EMPTY = new StructureBonus(null, 0, 0);
    
    public int totalBonus() {
        return nonBypassingBonus + bypassingBonus;
    }
    
    public boolean hasStructure() {
        return structureId != null;
    }
}

