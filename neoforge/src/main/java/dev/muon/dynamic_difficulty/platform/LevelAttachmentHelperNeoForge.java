package dev.muon.dynamic_difficulty.platform;

import dev.muon.dynamic_difficulty.EntityLevelAttachmentNeoForge;
import net.minecraft.world.entity.LivingEntity;

public class LevelAttachmentHelperNeoForge implements LevelAttachmentHelper {
    
    @Override
    public int getLevel(LivingEntity entity) {
        return entity.getData(EntityLevelAttachmentNeoForge.LEVEL);
    }
    
    @Override
    public void setLevel(LivingEntity entity, int level) {
        entity.setData(EntityLevelAttachmentNeoForge.LEVEL, level);
    }
    
    @Override
    public boolean hasLevel(LivingEntity entity) {
        return entity.getExistingDataOrNull(EntityLevelAttachmentNeoForge.LEVEL) != null;
    }
}
