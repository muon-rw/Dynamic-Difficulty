package dev.muon.dynamic_difficulty.settings;

import java.util.Map;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public record EntityLevelingSettings(
        int startingLevel,
        int maxLevel,
        float levelsPerDistance,
        float levelsPerDeepness,
        int randomLevelBonus,
        Map<Attribute, AttributeModifier> attributeModifiers)
        implements LevelingSettings {
}