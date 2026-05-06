package dev.muon.dynamic_difficulty.settings;

import java.util.Map;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

/**
 * Common contract for any tier in the leveling resolution chain
 * (dimension → biome → structure → entity).
 *
 * <p>Each tier supplies the scaling factors below; later tiers may override fields by returning
 * non-null/non-zero values. {@link #attributeModifiers()}, {@link #playerLevelMultiplier()} and
 * {@link #applyLevelBonuses()} are nullable so callers can distinguish "this tier did not specify"
 * from "this tier explicitly set a value".
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
   * {@code null} means fall back to the next level in the chain (or to config).
   */
  @Nullable
  Map<Attribute, AttributeModifier> attributeModifiers();

  /**
   * Returns the player-level multiplier override applied to nearby player bonus contributions.
   * {@code null} means fall back to config.
   */
  @Nullable
  Double playerLevelMultiplier();

  /**
   * Returns which bonus sources (biome / structure / player) are applied for entities resolving
   * through this tier. {@code null} means inherit from the prior tier (or treat all as enabled).
   *
   * <p>Biome and structure tiers cannot set this field directly via their JSON; they always
   * passthrough whatever the dimension or entity tier provided.
   */
  @Nullable
  DimensionLevelingSettings.ApplyLevelBonuses applyLevelBonuses();
}
