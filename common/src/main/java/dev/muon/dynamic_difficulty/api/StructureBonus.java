package dev.muon.dynamic_difficulty.api;

import net.minecraft.resources.Identifier;

/**
 * @param structureId The structure at the position (null if none)
 * @param nonBypassingBonus Highest bonus from structures that don't bypass cap
 * @param bypassingBonus Highest bonus from structures that bypass cap
 */
public record StructureBonus(
    Identifier structureId,
    int nonBypassingBonus,
    int bypassingBonus
) {
    public static final StructureBonus EMPTY = new StructureBonus(null, 0, 0);
    
    public int totalBonus() {
        return nonBypassingBonus + bypassingBonus;
    }
    
    public boolean hasStructure() {
        return structureId != null;
    }
}

