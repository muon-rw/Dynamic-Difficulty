package dev.muon.dynamic_difficulty.settings;

import java.util.Map;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

/** One tier of the dimension, biome, structure, entity resolution chain. */
public interface LevelingSettings {

  int startingLevel();

  /** 0 means unlimited. */
  int maxLevel();

  float levelsPerDistance();

  float levelsPerDepth();

  float levelsPerHeight();

  float levelsPerDay();

  /** Local difficulty ranges from 0.0 to 6.75. */
  float levelsPerLocalDifficulty();

  /** Each entity gets a random bonus from 0 to this value. */
  int randomLevelBonus();

  /** Null falls back to the config bonuses; an empty map disables them. */
  @Nullable
  Map<Attribute, AttributeModifier> attributeModifiers();

  /** Null falls back to config. */
  @Nullable
  Double playerLevelMultiplier();

  /** Null applies every bonus source. Only dimensions and entities can set this. */
  @Nullable
  DimensionLevelingSettings.ApplyLevelBonuses applyLevelBonuses();

  default boolean appliesBiomeBonus() {
    DimensionLevelingSettings.ApplyLevelBonuses apply = applyLevelBonuses();
    return apply == null || apply.biome();
  }

  default boolean appliesStructureBonus() {
    DimensionLevelingSettings.ApplyLevelBonuses apply = applyLevelBonuses();
    return apply == null || apply.structure();
  }

  default boolean appliesPlayerBonus() {
    DimensionLevelingSettings.ApplyLevelBonuses apply = applyLevelBonuses();
    return apply == null || apply.player();
  }
}
