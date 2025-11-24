package dev.muon.dynamic_difficulty.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

public interface LevelingSettings {

  int startingLevel();

  int maxLevel();

  float levelsPerDistance();

  float levelsPerDeepness();

  int randomLevelBonus();

  Map<Attribute, AttributeModifier> attributeModifiers();

  static Map<Attribute, AttributeModifier> readAttributeModifiers(JsonObject jsonObject) {
    if (!jsonObject.has("attribute_modifiers")) {
      return Map.of();
    }
    Map<Attribute, AttributeModifier> modifiers = new HashMap<>();
    JsonArray jsonPairs = jsonObject.get("attribute_modifiers").getAsJsonArray();
    jsonPairs.forEach(
        jsonElement -> {
          JsonObject elementJson = jsonElement.getAsJsonObject();
          Attribute attribute = readAttribute(elementJson);
          AttributeModifier modifier = readAttributeModifier(elementJson);
          if (attribute != null && modifier != null) {
            modifiers.put(attribute, modifier);
          }
        });
    return modifiers;
  }

  static @Nullable Attribute readAttribute(JsonObject jsonObject) {
    ResourceLocation attributeId = ResourceLocation.tryParse(jsonObject.get("attribute").getAsString());
    if (attributeId == null) return null;
    Attribute attribute = BuiltInRegistries.ATTRIBUTE.getValue(attributeId);
    if (attribute == null) {
      DynamicDifficulty.LOGGER.warn("Attribute not found: {}", attributeId);
    }
    return attribute;
  }

  static @Nullable AttributeModifier readAttributeModifier(JsonObject jsonObject) {
    ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, "autoleveling_settings_bonus");
    double amount = jsonObject.get("amount").getAsDouble();
    
    // Support both legacy numeric IDs and new enum serialized names for backwards compatibility
    AttributeModifier.Operation operation;
    if (jsonObject.get("operation").isJsonPrimitive()) {
      var operationElement = jsonObject.get("operation");
      if (operationElement.getAsJsonPrimitive().isNumber()) {
        // Legacy numeric ID support
        DynamicDifficulty.LOGGER.warn("Numeric operation IDs are deprecated. Please use enum serialized names (add_value, add_multiplied_base, add_multiplied_total) instead.");
        operation = AttributeModifier.Operation.BY_ID.apply(operationElement.getAsInt());
      } else {
        // New enum serialized name
        String operationStr = operationElement.getAsString();
        operation = parseOperation(operationStr);
      }
    } else {
      DynamicDifficulty.LOGGER.warn("Invalid operation format. Defaulting to add_value.");
      operation = AttributeModifier.Operation.ADD_VALUE;
    }
    
    return new AttributeModifier(modifierId, amount, operation);
  }
  
  static AttributeModifier.Operation parseOperation(String operationStr) {
    for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
      if (op.getSerializedName().equals(operationStr)) {
        return op;
      }
    }
    DynamicDifficulty.LOGGER.warn("Invalid operation '{}'. Must be one of: add_value, add_multiplied_base, add_multiplied_total. Defaulting to add_value.", operationStr);
    return AttributeModifier.Operation.ADD_VALUE;
  }

  static @Nullable BlockPos readSpawnPosOverride(JsonObject jsonObject) {
    if (!jsonObject.has("spawn_pos_override")) return null;
    JsonObject posJson = jsonObject.get("spawn_pos_override").getAsJsonObject();
    int x = posJson.get("x").getAsInt();
    int y = posJson.get("y").getAsInt();
    int z = posJson.get("z").getAsInt();
    return new BlockPos(x, y, z);
  }
}
