package dev.muon.dynamic_difficulty.settings;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
  
  private static final Codec<Map<Attribute, AttributeModifier>> ATTRIBUTE_MODIFIERS_CODEC = 
      Codec.list(ATTRIBUTE_MODIFIER_ENTRY_CODEC)
      .xmap(
          list -> {
            Map<Attribute, AttributeModifier> map = new HashMap<>();
            for (AttributeModifierEntry entry : list) {
              Attribute attribute = BuiltInRegistries.ATTRIBUTE.getValue(entry.attribute());
              if (attribute != null) {
                AttributeModifier.Operation operation = parseOperation(entry.operation());
                ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, 
                    "leveling_bonus_" + entry.attribute().getPath().replace("/", "_"));
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
  
  public static final Codec<EntityLevelingSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Codec.INT.fieldOf("starting_level").forGetter(EntityLevelingSettings::startingLevel),
      Codec.INT.fieldOf("max_level").forGetter(EntityLevelingSettings::maxLevel),
      Codec.FLOAT.fieldOf("levels_per_distance").forGetter(EntityLevelingSettings::levelsPerDistance),
      Codec.FLOAT.fieldOf("levels_per_deepness").forGetter(EntityLevelingSettings::levelsPerDeepness),
      Codec.INT.fieldOf("random_level_bonus").forGetter(EntityLevelingSettings::randomLevelBonus),
      ATTRIBUTE_MODIFIERS_CODEC.optionalFieldOf("attribute_modifiers", Map.of()).forGetter(EntityLevelingSettings::attributeModifiers)
  ).apply(instance, EntityLevelingSettings::new));
}