package dev.muon.dynamic_difficulty.settings;

import com.google.gson.JsonObject;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

public record DimensionLevelingSettings(
    int startingLevel,
    int maxLevel,
    float levelsPerDistance,
    float levelsPerDeepness,
    int randomLevelBonus,
    @Nullable BlockPos spawnPosOverride,
    @Nullable Map<Attribute, AttributeModifier> attributeModifiers)
    implements LevelingSettings {
  public static DimensionLevelingSettings load(JsonObject jsonObject) {
    return new DimensionLevelingSettings(
        jsonObject.get("starting_level").getAsInt(),
        jsonObject.get("max_level").getAsInt(),
        jsonObject.get("levels_per_distance").getAsFloat(),
        jsonObject.get("levels_per_deepness").getAsFloat(),
        jsonObject.get("random_level_bonus").getAsInt(),
        LevelingSettings.readSpawnPosOverride(jsonObject),
        LevelingSettings.readAttributeModifiers(jsonObject));
  }
}
