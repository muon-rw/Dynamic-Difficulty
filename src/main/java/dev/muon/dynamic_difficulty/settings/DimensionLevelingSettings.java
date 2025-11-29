package dev.muon.dynamic_difficulty.settings;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
    int seaLevel,
    float levelsPerHeight,
    @Nullable Map<Attribute, AttributeModifier> attributeModifiers)
    implements LevelingSettings {

  private record AttributeModifierEntry(ResourceLocation attribute, double amount, String operation) {}

  private static AttributeModifier.Operation parseOperation(String operationStr) {
    for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
      if (op.getSerializedName().equals(operationStr)) {
        return op;
      }
    }
    DynamicDifficulty.LOGGER.warn("Invalid operation '{}'. Must be one of: add_value, add_multiplied_base, add_multiplied_total. Defaulting to add_value.", operationStr);
    return AttributeModifier.Operation.ADD_VALUE;
  }

  // Codec that accepts both string (enum serialized name) and int (legacy numeric ID) for backwards compatibility
  private static final Codec<String> OPERATION_CODEC = Codec.either(Codec.STRING, Codec.INT)
      .xmap(
          either -> {
            if (either.left().isPresent()) {
              return either.left().get(); // String path: use as-is
            } else if (either.right().isPresent()) {
              // Integer path: convert to enum serialized name (deprecated)
              int num = either.right().get();
              DynamicDifficulty.LOGGER.warn("Numeric operation ID {} is deprecated. Please use enum serialized names (add_value, add_multiplied_base, add_multiplied_total) instead.", num);
              AttributeModifier.Operation op = AttributeModifier.Operation.BY_ID.apply(num);
              return op.getSerializedName();
            } else {
              // Fallback: should never happen with Either, but handle gracefully
              DynamicDifficulty.LOGGER.error("Invalid operation format in codec. Defaulting to add_value.");
              return AttributeModifier.Operation.ADD_VALUE.getSerializedName();
            }
          },
          Either::left // Encode direction: always use string (left side of Either)
      );

  private static final Codec<AttributeModifierEntry> ATTRIBUTE_MODIFIER_ENTRY_CODEC = 
      RecordCodecBuilder.create(instance -> instance.group(
          ResourceLocation.CODEC.fieldOf("attribute").forGetter(AttributeModifierEntry::attribute),
          Codec.DOUBLE.fieldOf("amount").forGetter(AttributeModifierEntry::amount),
          OPERATION_CODEC.fieldOf("operation").forGetter(AttributeModifierEntry::operation)
      ).apply(instance, AttributeModifierEntry::new));

  // Custom codec for spawn_pos_override that only parses x and z (2D position for horizontal distance)
  private static final Codec<BlockPos> SPAWN_POS_OVERRIDE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Codec.INT.fieldOf("x").forGetter(BlockPos::getX),
      Codec.INT.fieldOf("z").forGetter(BlockPos::getZ)
  ).apply(instance, (x, z) -> new BlockPos(x, 0, z)));

  private static final Codec<Map<Attribute, AttributeModifier>> ATTRIBUTE_MODIFIERS_CODEC = 
      Codec.list(ATTRIBUTE_MODIFIER_ENTRY_CODEC)
      .xmap(
          list -> {
            Map<Attribute, AttributeModifier> map = new HashMap<>();
            for (AttributeModifierEntry entry : list) {
              Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(entry.attribute());
              if (attribute != null) {
                AttributeModifier.Operation operation = parseOperation(entry.operation());
                // Modifier ID must be unique per attribute (within an entity's AttributeInstance)
                ResourceLocation modifierId = DynamicDifficulty.loc(
                    "dimension_leveling_bonus_" + entry.attribute().getPath().replace("/", "_"));
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

  public static final Codec<DimensionLevelingSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Codec.INT.fieldOf("starting_level").forGetter(DimensionLevelingSettings::startingLevel),
      Codec.INT.fieldOf("max_level").forGetter(DimensionLevelingSettings::maxLevel),
      Codec.FLOAT.fieldOf("levels_per_distance").forGetter(DimensionLevelingSettings::levelsPerDistance),
      Codec.FLOAT.optionalFieldOf("levels_per_deepness", 0.0f).forGetter(DimensionLevelingSettings::levelsPerDeepness),
      Codec.INT.fieldOf("random_level_bonus").forGetter(DimensionLevelingSettings::randomLevelBonus),
      SPAWN_POS_OVERRIDE_CODEC.optionalFieldOf("spawn_pos_override").forGetter(s -> Optional.ofNullable(s.spawnPosOverride)),
      Codec.INT.optionalFieldOf("sea_level", 64).forGetter(DimensionLevelingSettings::seaLevel),
      Codec.FLOAT.optionalFieldOf("levels_per_height", 0.0f).forGetter(DimensionLevelingSettings::levelsPerHeight),
      ATTRIBUTE_MODIFIERS_CODEC.optionalFieldOf("attribute_modifiers").forGetter(s -> Optional.ofNullable(s.attributeModifiers))
  ).apply(instance, (startingLevel, maxLevel, levelsPerDistance, levelsPerDeepness, randomLevelBonus, spawnPosOverride, seaLevel, levelsPerHeight, attributeModifiers) ->
      new DimensionLevelingSettings(
          startingLevel,
          maxLevel,
          levelsPerDistance,
          levelsPerDeepness,
          randomLevelBonus,
          spawnPosOverride.orElse(null),
          seaLevel,
          levelsPerHeight,
          attributeModifiers.orElse(null)
      )
  ));

  public static DimensionLevelingSettings load(JsonObject jsonObject) {
    // Optional dimension-specific fields with hardcoded defaults (not config fallback)
    int seaLevel = jsonObject.has("sea_level") ? jsonObject.get("sea_level").getAsInt() : 64;
    float levelsPerDeepness = jsonObject.has("levels_per_deepness") ? jsonObject.get("levels_per_deepness").getAsFloat() : 0.0f;
    float levelsPerHeight = jsonObject.has("levels_per_height") ? jsonObject.get("levels_per_height").getAsFloat() : 0.0f;
    return new DimensionLevelingSettings(
        jsonObject.get("starting_level").getAsInt(),
        jsonObject.get("max_level").getAsInt(),
        jsonObject.get("levels_per_distance").getAsFloat(),
        levelsPerDeepness,
        jsonObject.get("random_level_bonus").getAsInt(),
        LevelingSettings.readSpawnPosOverride(jsonObject),
        seaLevel,
        levelsPerHeight,
        LevelingSettings.readAttributeModifiers(jsonObject));
  }
}
