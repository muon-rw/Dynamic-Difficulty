package dev.muon.dynamic_difficulty.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;
import java.util.Optional;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

public record EntityLevelingSettings(
    int startingLevel,
    int maxLevel,
    float levelsPerDistance,
    float levelsPerDepth,
    float levelsPerHeight,
    float levelsPerDay,
    float levelsPerLocalDifficulty,
    int randomLevelBonus,
    @Nullable Map<Attribute, AttributeModifier> attributeModifiers,
    @Nullable Double playerLevelMultiplier,
    @Nullable DimensionLevelingSettings.ApplyLevelBonuses applyLevelBonuses)
    implements LevelingSettings {

  /**
   * Optional fields: omit = use dimension default.
   */
  public record RawSettings(
      Optional<Integer> startingLevel,
      Optional<Integer> maxLevel,
      Optional<Float> levelsPerDistance,
      Optional<Float> levelsPerDeepness,
      Optional<Float> levelsPerHeight,
      Optional<Float> levelsPerDay,
      Optional<Float> levelsPerLocalDifficulty,
      Optional<Integer> randomLevelBonus,
      Optional<Map<Attribute, AttributeModifier>> attributeModifiers,
      Optional<Double> playerLevelMultiplier,
      Optional<DimensionLevelingSettings.ApplyLevelBonuses> applyLevelBonuses
  ) {
    public EntityLevelingSettings resolve(LevelingSettings fallback) {
      return new EntityLevelingSettings(
          startingLevel.orElse(fallback.startingLevel()),
          maxLevel.orElse(fallback.maxLevel()),
          levelsPerDistance.orElse(fallback.levelsPerDistance()),
          levelsPerDeepness.orElse(fallback.levelsPerDepth()),
          levelsPerHeight.orElse(fallback.levelsPerHeight()),
          levelsPerDay.orElse(fallback.levelsPerDay()),
          levelsPerLocalDifficulty.orElse(fallback.levelsPerLocalDifficulty()),
          randomLevelBonus.orElse(fallback.randomLevelBonus()),
          attributeModifiers.orElse(fallback.attributeModifiers()),
          playerLevelMultiplier.orElse(fallback.playerLevelMultiplier()),
          applyLevelBonuses.orElse(fallback.applyLevelBonuses())
      );
    }
  }

  private static final Codec<Map<Attribute, AttributeModifier>> ATTRIBUTE_MODIFIERS_CODEC =
      AttributeModifierCodecs.mapCodec("entity_leveling_bonus_");

  public static final Codec<RawSettings> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Codec.INT.optionalFieldOf("starting_level").forGetter(RawSettings::startingLevel),
      Codec.INT.optionalFieldOf("max_level").forGetter(RawSettings::maxLevel),
      Codec.FLOAT.optionalFieldOf("levels_per_distance").forGetter(RawSettings::levelsPerDistance),
      Codec.FLOAT.optionalFieldOf("levels_per_deepness").forGetter(RawSettings::levelsPerDeepness),
      Codec.FLOAT.optionalFieldOf("levels_per_height").forGetter(RawSettings::levelsPerHeight),
      Codec.FLOAT.optionalFieldOf("levels_per_day").forGetter(RawSettings::levelsPerDay),
      Codec.FLOAT.optionalFieldOf("levels_per_local_difficulty").forGetter(RawSettings::levelsPerLocalDifficulty),
      Codec.INT.optionalFieldOf("random_level_bonus").forGetter(RawSettings::randomLevelBonus),
      ATTRIBUTE_MODIFIERS_CODEC.optionalFieldOf("attribute_modifiers").forGetter(RawSettings::attributeModifiers),
      Codec.DOUBLE.optionalFieldOf("player_level_multiplier").forGetter(RawSettings::playerLevelMultiplier),
      DimensionLevelingSettings.ApplyLevelBonuses.CODEC.optionalFieldOf("apply_level_bonuses").forGetter(RawSettings::applyLevelBonuses)
  ).apply(instance, RawSettings::new));
}
