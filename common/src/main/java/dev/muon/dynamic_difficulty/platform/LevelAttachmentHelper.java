package dev.muon.dynamic_difficulty.platform;

import net.minecraft.world.entity.LivingEntity;

public interface LevelAttachmentHelper {

    /** Returns 1 when unset. */
    int getLevel(LivingEntity entity);

    void setLevel(LivingEntity entity, int level);

    /** True only when a level was explicitly set, not the default. */
    boolean hasLevel(LivingEntity entity);
}
