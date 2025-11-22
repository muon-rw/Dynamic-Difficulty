package dev.muon.dynamic_difficulty.settings;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
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
  
  private record AttributeModifierEntry(ResourceLocation attribute, double amount, int operation) {}
  
  private static final Codec<AttributeModifierEntry> ATTRIBUTE_MODIFIER_ENTRY_CODEC = 
      RecordCodecBuilder.create(instance -> instance.group(
          ResourceLocation.CODEC.fieldOf("attribute").forGetter(AttributeModifierEntry::attribute),
          Codec.DOUBLE.fieldOf("amount").forGetter(AttributeModifierEntry::amount),
          Codec.INT.fieldOf("operation").forGetter(AttributeModifierEntry::operation)
      ).apply(instance, AttributeModifierEntry::new));
  
  private static final Codec<Map<Attribute, AttributeModifier>> ATTRIBUTE_MODIFIERS_CODEC = 
      Codec.list(ATTRIBUTE_MODIFIER_ENTRY_CODEC)
      .xmap(
          list -> {
            Map<Attribute, AttributeModifier> map = new HashMap<>();
            for (AttributeModifierEntry entry : list) {
              Attribute attribute = BuiltInRegistries.ATTRIBUTE.getValue(entry.attribute());
              if (attribute != null) {
                AttributeModifier.Operation operation = AttributeModifier.Operation.BY_ID.apply(entry.operation());
                // For entity settings, we need a unique modifier ID per entity
                // We'll use a generic one here since we don't have entityKey in the Codec context
                ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, 
                    "leveling_bonus_" + entry.attribute().getPath().replace("/", "_"));
                map.put(attribute, new AttributeModifier(modifierId, entry.amount(), operation));
              }
            }
            return map;
          },
          map -> {
            List<AttributeModifierEntry> list = new java.util.ArrayList<>();
            map.forEach((attr, modifier) -> {
              ResourceLocation attrId = BuiltInRegistries.ATTRIBUTE.getKey(attr);
              list.add(new AttributeModifierEntry(attrId, modifier.amount(), modifier.operation().id()));
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