package dev.muon.dynamic_difficulty.settings;

import java.util.Map;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Common interface for dimension and entity leveling settings.
 * Defines the scaling factors used to calculate mob levels.
 */
public interface LevelingSettings {

  /**
   * Returns the base level before any scaling is applied.
   */
  int startingLevel();

  /**
   * Returns the maximum level cap. Levels above this are capped (unless bypassed).
   * A value of 0 means unlimited.
   */
  int maxLevel();

  /**
   * Returns the levels added per block of horizontal distance from spawn.
   */
  float levelsPerDistance();

  /**
   * Returns the levels added per block below sea level (depth-based scaling).
   */
  float levelsPerDeepness();

  /**
   * Returns the levels added per block above sea level (height-based scaling).
   */
  float levelsPerHeight();

  /**
   * Returns the levels added per in-game day passed.
   */
  float levelsPerDay();

  /**
   * Returns the levels added per point of local difficulty (0.0 to 6.75).
   */
  float levelsPerLocalDifficulty();

  /**
   * Returns the maximum random bonus levels added to each entity.
   * Actual bonus is random from 0 to this value.
   */
  int randomLevelBonus();

  /**
   * Returns the attribute modifiers applied per level.
   * Null means fall back to the next level in the chain (dimension or config).
   */
  Map<Attribute, AttributeModifier> attributeModifiers();
}
