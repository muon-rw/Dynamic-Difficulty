package dev.muon.dynamic_difficulty.platform;

import dev.muon.dynamic_difficulty.EntityLevelAttachmentFabric;
import net.minecraft.world.entity.LivingEntity;

@SuppressWarnings("UnstableApiUsage")
public class LevelAttachmentHelperFabric implements LevelAttachmentHelper {
    
    @Override
    public int getLevel(LivingEntity entity) {
        Integer level = entity.getAttached(EntityLevelAttachmentFabric.LEVEL);
        return level != null ? level : 1;
    }
    
    @Override
    public void setLevel(LivingEntity entity, int level) {
        entity.setAttached(EntityLevelAttachmentFabric.LEVEL, level);
    }
    
    @Override
    public boolean hasLevel(LivingEntity entity) {
        return entity.hasAttached(EntityLevelAttachmentFabric.LEVEL);
    }
}
