package dev.muon.dynamic_difficulty.settings;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    @Nullable Map<Attribute, AttributeModifier> attributeModifiers)
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
                ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, 
                    "autoleveling_settings_bonus");
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
  
  public static final Codec<DimensionLevelingSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      Codec.INT.fieldOf("starting_level").forGetter(DimensionLevelingSettings::startingLevel),
      Codec.INT.fieldOf("max_level").forGetter(DimensionLevelingSettings::maxLevel),
      Codec.FLOAT.fieldOf("levels_per_distance").forGetter(DimensionLevelingSettings::levelsPerDistance),
      Codec.FLOAT.fieldOf("levels_per_deepness").forGetter(DimensionLevelingSettings::levelsPerDeepness),
      Codec.INT.fieldOf("random_level_bonus").forGetter(DimensionLevelingSettings::randomLevelBonus),
      BlockPos.CODEC.optionalFieldOf("spawn_pos_override").forGetter(s -> java.util.Optional.ofNullable(s.spawnPosOverride)),
      ATTRIBUTE_MODIFIERS_CODEC.optionalFieldOf("attribute_modifiers").forGetter(s -> java.util.Optional.ofNullable(s.attributeModifiers))
  ).apply(instance, (startingLevel, maxLevel, levelsPerDistance, levelsPerDeepness, randomLevelBonus, spawnPosOverride, attributeModifiers) ->
      new DimensionLevelingSettings(
          startingLevel,
          maxLevel,
          levelsPerDistance,
          levelsPerDeepness,
          randomLevelBonus,
          spawnPosOverride.orElse(null),
          attributeModifiers.orElse(null)
      )
  ));
  
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
