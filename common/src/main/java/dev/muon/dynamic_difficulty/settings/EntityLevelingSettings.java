package dev.muon.dynamic_difficulty.settings;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.muon.dynamic_difficulty.DynamicDifficulty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

/**
 * Resolved entity leveling settings. All fields have values.
 * Created by resolving RawSettings with dimension settings as fallback.
 */
public record EntityLevelingSettings(
    int startingLevel,
    int maxLevel,
    float levelsPerDistance,
    float levelsPerDeepness,
    float levelsPerHeight,
    float levelsPerDay,
    float levelsPerLocalDifficulty,
    int randomLevelBonus,
    @Nullable Map<Attribute, AttributeModifier> attributeModifiers)
    implements LevelingSettings {

  /**
   * Raw settings as parsed from JSON. All fields are Optional to support "omit = use dimension default".
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
      Optional<Map<Attribute, AttributeModifier>> attributeModifiers
  ) {
    /**
     * Resolve raw settings into final settings, using dimension settings as fallback.
     */
    public EntityLevelingSettings resolve(DimensionLevelingSettings dimSettings) {
      return new EntityLevelingSettings(
          startingLevel.orElse(dimSettings.startingLevel()),
          maxLevel.orElse(dimSettings.maxLevel()),
          levelsPerDistance.orElse(dimSettings.levelsPerDistance()),
          levelsPerDeepness.orElse(dimSettings.levelsPerDeepness()),
          levelsPerHeight.orElse(dimSettings.levelsPerHeight()),
          levelsPerDay.orElse(dimSettings.levelsPerDay()),
          levelsPerLocalDifficulty.orElse(dimSettings.levelsPerLocalDifficulty()),
          randomLevelBonus.orElse(dimSettings.randomLevelBonus()),
          attributeModifiers.orElse(dimSettings.attributeModifiers())
      );
    }
  }

  // === Codecs ===

  private record AttributeModifierEntry(ResourceLocation attribute, double amount, String operation) {}

  private static AttributeModifier.Operation parseOperation(String operationStr) {
    for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
      if (op.getSerializedName().equals(operationStr)) {
        return op;
      }
    }
    DynamicDifficulty.LOGGER.warn("Invalid operation '{}'. Defaulting to add_value.", operationStr);
    return AttributeModifier.Operation.ADD_VALUE;
  }

  // Codec that accepts both string (enum name) and int (legacy) for backwards compatibility
  private static final Codec<String> OPERATION_CODEC = Codec.either(Codec.STRING, Codec.INT)
      .xmap(
          either -> either.map(
              str -> str,
              num -> {
                DynamicDifficulty.LOGGER.warn("Numeric operation ID {} is deprecated. Use enum names instead.", num);
                return AttributeModifier.Operation.BY_ID.apply(num).getSerializedName();
              }
          ),
          str -> Either.left(str)
      );

  private static final Codec<AttributeModifierEntry> ATTRIBUTE_MODIFIER_ENTRY_CODEC =
      RecordCodecBuilder.create(instance -> instance.group(
          ResourceLocation.CODEC.fieldOf("attribute").forGetter(AttributeModifierEntry::attribute),
          Codec.DOUBLE.fieldOf("amount").forGetter(AttributeModifierEntry::amount),
          OPERATION_CODEC.fieldOf("operation").forGetter(AttributeModifierEntry::operation)
      ).apply(instance, AttributeModifierEntry::new));

  private static final Codec<Map<Attribute, AttributeModifier>> ATTRIBUTE_MODIFIERS_CODEC =
      Codec.list(ATTRIBUTE_MODIFIER_ENTRY_CODEC)
          .xmap(
              list -> {
                Map<Attribute, AttributeModifier> map = new HashMap<>();
                for (AttributeModifierEntry entry : list) {
                  Attribute attribute = BuiltInRegistries.ATTRIBUTE.getValue(entry.attribute());
                  if (attribute != null) {
                    AttributeModifier.Operation operation = parseOperation(entry.operation());
                    ResourceLocation modifierId = DynamicDifficulty.loc(
                        "entity_leveling_bonus_" + entry.attribute().getPath().replace("/", "_"));
                    map.put(attribute, new AttributeModifier(modifierId, entry.amount(), operation));
                  }
                }
                return map;
              },
              map -> {
                List<AttributeModifierEntry> list = new ArrayList<>();
                map.forEach((attr, modifier) -> {
                  ResourceLocation attrId = BuiltInRegistries.ATTRIBUTE.getKey(attr);
                  list.add(new AttributeModifierEntry(attrId, modifier.amount(), modifier.operation().getSerializedName()));
                });
                return list;
              }
          );

  /**
   * Codec for parsing raw settings from JSON. ALL fields are optional.
   */
  public static final Codec<RawSettings> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Codec.INT.optionalFieldOf("starting_level").forGetter(RawSettings::startingLevel),
      Codec.INT.optionalFieldOf("max_level").forGetter(RawSettings::maxLevel),
      Codec.FLOAT.optionalFieldOf("levels_per_distance").forGetter(RawSettings::levelsPerDistance),
      Codec.FLOAT.optionalFieldOf("levels_per_deepness").forGetter(RawSettings::levelsPerDeepness),
      Codec.FLOAT.optionalFieldOf("levels_per_height").forGetter(RawSettings::levelsPerHeight),
      Codec.FLOAT.optionalFieldOf("levels_per_day").forGetter(RawSettings::levelsPerDay),
      Codec.FLOAT.optionalFieldOf("levels_per_local_difficulty").forGetter(RawSettings::levelsPerLocalDifficulty),
      Codec.INT.optionalFieldOf("random_level_bonus").forGetter(RawSettings::randomLevelBonus),
      ATTRIBUTE_MODIFIERS_CODEC.optionalFieldOf("attribute_modifiers").forGetter(RawSettings::attributeModifiers)
  ).apply(instance, RawSettings::new));
}
