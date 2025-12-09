package dev.muon.dynamic_difficulty.compat.dungeon_difficulty;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public class DungeonDifficultyAttachmentHelperFabric implements DungeonDifficultyAttachmentHelper {
    
    @Override
    @Nullable
    public DungeonDifficultyData getData(LivingEntity entity) {
        return entity.getAttached(DungeonDifficultyAttachmentFabric.DUNGEON_DIFFICULTY);
    }
    
    @Override
    public void setData(LivingEntity entity, DungeonDifficultyData data) {
        entity.setAttached(DungeonDifficultyAttachmentFabric.DUNGEON_DIFFICULTY, data);
    }
    
    @Override
    public boolean hasData(LivingEntity entity) {
        return entity.hasAttached(DungeonDifficultyAttachmentFabric.DUNGEON_DIFFICULTY);
    }
}

