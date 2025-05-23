package dev.muon.dynamic_difficulty.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.muon.dynamic_difficulty.config.Config;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
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
          modifiers.put(attribute, modifier);
        });
    return modifiers;
  }

  static Attribute readAttribute(JsonObject jsonObject) {
    ResourceLocation attributeId = new ResourceLocation(jsonObject.get("attribute").getAsString());
    return ForgeRegistries.ATTRIBUTES.getValue(attributeId);
  }

  static AttributeModifier readAttributeModifier(JsonObject jsonObject) {
    UUID uuid = UUID.fromString("6a102cb4-d735-4cb7-8ab2-3d383219a44e");
    double amount = jsonObject.get("amount").getAsDouble();
    AttributeModifier.Operation operation =
        AttributeModifier.Operation.fromValue(jsonObject.get("operation").getAsInt());
    return new AttributeModifier(uuid, "AutoLeveling", amount, operation);
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
      JsonObject jsonObject, String name, ForgeConfigSpec.ConfigValue<Double> alternative) {
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
