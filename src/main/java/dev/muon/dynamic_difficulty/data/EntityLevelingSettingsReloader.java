package dev.muon.dynamic_difficulty.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.settings.EntityLevelingSettings;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

public class EntityLevelingSettingsReloader extends SimpleJsonResourceReloadListener {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson GSON = Deserializers.createLootTableSerializer().create();
  private static final Map<ResourceLocation, EntityLevelingSettings> SETTINGS = new HashMap<>();

  public EntityLevelingSettingsReloader() {
    super(GSON, "leveling_settings/entities");
  }

  @Nullable
  public static EntityLevelingSettings get(EntityType<?> entityType) {
    return SETTINGS.get(ForgeRegistries.ENTITY_TYPES.getKey(entityType));
  }

  @Override
  protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler) {
    LOGGER.debug("Loading entity leveling settings");
    SETTINGS.clear();
    map.forEach(this::loadSettings);
    LOGGER.debug("Loaded {} entity leveling settings", SETTINGS.size());
  }

  private void validateRequiredFields(JsonObject json) {
    String[] required = {
            "starting_level",
            "max_level",
            "levels_per_distance",
            "levels_per_deepness",
            "random_level_bonus"
    };
    for (String field : required) {
      if (!json.has(field)) {
        throw new IllegalArgumentException("Missing required field: " + field);
      }
    }
  }

  private Map<Attribute, AttributeModifier> readAttributeModifiers(JsonObject json) {
    Map<Attribute, AttributeModifier> attributeModifiers = new HashMap<>();
    if (!json.has("attribute_modifiers")) {
      return attributeModifiers;
    }

    JsonArray modifiersArray = json.getAsJsonArray("attribute_modifiers");
    for (JsonElement element : modifiersArray) {
      JsonObject modifierObject = element.getAsJsonObject();
      String attributeKey = modifierObject.get("attribute").getAsString();
      ResourceLocation attributeId = new ResourceLocation(attributeKey);
      Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(attributeId);

      if (attribute != null) {
        double amount = modifierObject.get("amount").getAsDouble();
        AttributeModifier.Operation operation = getOperation(modifierObject.get("operation").getAsInt());
        attributeModifiers.put(attribute, new AttributeModifier(
                "LevelingBonus", amount, operation));
        LOGGER.debug("Added attribute modifier: {} = {} ({})",
                attributeId, amount, operation);
      } else {
        LOGGER.warn("Unknown attribute: {}", attributeId);
      }
    }
    return attributeModifiers;
  }

  private AttributeModifier.Operation getOperation(int operationId) {
    return switch (operationId) {
      case 0 -> AttributeModifier.Operation.ADDITION;
      case 1 -> AttributeModifier.Operation.MULTIPLY_BASE;
      case 2 -> AttributeModifier.Operation.MULTIPLY_TOTAL;
      default -> {
        LOGGER.warn("Unknown operation ID: {}. Defaulting to ADDITION", operationId);
        yield AttributeModifier.Operation.ADDITION;
      }
    };
  }

  private ResourceLocation normalizeResourceLocation(ResourceLocation fileId) {
    String path = fileId.getPath();
    String[] pathParts = path.split("/");
    String entityName = pathParts[pathParts.length - 1].replace(".json", "");
    return new ResourceLocation(fileId.getNamespace(), entityName);
  }

  private void loadSettings(ResourceLocation fileId, JsonElement jsonElement) {
    try {
      JsonObject jsonObject = jsonElement.getAsJsonObject();

      // Validate required fields first
      validateRequiredFields(jsonObject);

      EntityLevelingSettings settings = new EntityLevelingSettings(
              jsonObject.get("starting_level").getAsInt(),
              jsonObject.get("max_level").getAsInt(),
              jsonObject.get("levels_per_distance").getAsFloat(),
              jsonObject.get("levels_per_deepness").getAsFloat(),
              jsonObject.get("random_level_bonus").getAsInt(),
              readAttributeModifiers(jsonObject)
      );

      ResourceLocation entityId = normalizeResourceLocation(fileId);
      SETTINGS.put(entityId, settings);
      LOGGER.debug("Loaded leveling settings for {}", entityId);
    } catch (Exception exception) {
      LOGGER.error("Couldn't load leveling settings {}", fileId, exception);
    }
  }
}