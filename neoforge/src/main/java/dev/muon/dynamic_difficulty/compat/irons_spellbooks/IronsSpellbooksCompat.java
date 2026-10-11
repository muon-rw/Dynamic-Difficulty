package dev.muon.dynamic_difficulty.compat.irons_spellbooks;

import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
// import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.world.entity.Entity;

public final class IronsSpellbooksCompat {

    public static boolean isSummon(Entity entity) {
        return entity instanceof AntiMagicSusceptible;
    }
}
