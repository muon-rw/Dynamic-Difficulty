package dev.muon.dynamic_difficulty.compat.dungeon_difficulty;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class DungeonDifficultyAttachmentHelperNeoForge implements DungeonDifficultyAttachmentHelper {
    
    @Override
    @Nullable
    public DungeonDifficultyData getData(LivingEntity entity) {
        return entity.getExistingDataOrNull(DungeonDifficultyAttachmentNeoForge.DUNGEON_DIFFICULTY);
    }
    
    @Override
    public void setData(LivingEntity entity, DungeonDifficultyData data) {
        entity.setData(DungeonDifficultyAttachmentNeoForge.DUNGEON_DIFFICULTY, data);
    }
    
    @Override
    public boolean hasData(LivingEntity entity) {
        return entity.getExistingDataOrNull(DungeonDifficultyAttachmentNeoForge.DUNGEON_DIFFICULTY) != null;
    }
}

