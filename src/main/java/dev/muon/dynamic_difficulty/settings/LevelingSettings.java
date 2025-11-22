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
    AttributeModifier.Operation operation =
        AttributeModifier.Operation.BY_ID.apply(jsonObject.get("operation").getAsInt());
    return new AttributeModifier(modifierId, amount, operation);
  }

  static @Nullable BlockPos readSpawnPosOverride(JsonObject jsonObject) {
    if (!jsonObject.has("spawn_pos_override")) return null;
    JsonObject posJson = jsonObject.get("spawn_pos_override").getAsJsonObject();
    int x = posJson.get("x").getAsInt();
    int y = posJson.get("y").getAsInt();
    int z = posJson.get("z").getAsInt();
    return new BlockPos(x, y, z);
  }

  static float readOptionalFloat(
      JsonObject jsonObject, String name, ModConfigSpec.ConfigValue<Double> alternative) {
    if (!jsonObject.has(name)) {
      return alternative.get().floatValue();
    }
    return jsonObject.get(name).getAsFloat();
  }

  static float readLevelPowerPerDistance(JsonObject jsonObject) {
    return readOptionalFloat(
        jsonObject, "level_power_per_distance", Config.COMMON.levelPowerPerDistance);
  }

  static float readLevelPowerPerDeepness(JsonObject jsonObject) {
    return readOptionalFloat(
        jsonObject, "level_power_per_deepness", Config.COMMON.levelPowerPerDeepness);
  }
}
