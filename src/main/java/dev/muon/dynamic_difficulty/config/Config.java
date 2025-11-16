package dev.muon.dynamic_difficulty.config;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.PlayerLevelDisplayStrategy;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

public class Config {
  public static final Common COMMON;
  public static final ModConfigSpec COMMON_SPEC;
  public static final Client CLIENT;
  public static final ModConfigSpec CLIENT_SPEC;
  private static final Map<ResourceKey<Attribute>, AttributeModifier> ATTRIBUTE_BONUSES = new HashMap<>();
  private static final Map<ResourceLocation, Integer> STRUCTURE_BONUSES = new HashMap<>();
  private static final Map<ResourceLocation, Integer> STRUCTURE_TAG_BONUSES = new HashMap<>();

  public enum RenderBehavior {
    ALWAYS,
    NEVER,
    LOOKING_AT
  }

  public static void register(ModContainer container) {
    container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
  }

  public static void reloadAttributeBonuses() {
    synchronized (ATTRIBUTE_BONUSES) {
      ATTRIBUTE_BONUSES.clear();
      COMMON.attributesBonuses.get().forEach(Config::readAttributeBonus);
      DynamicDifficulty.LOGGER.info("Reloaded {} attribute bonuses from config", ATTRIBUTE_BONUSES.size());
    }
  }
  
  public static void reloadStructureBonuses() {
    synchronized (STRUCTURE_BONUSES) {
      synchronized (STRUCTURE_TAG_BONUSES) {
        STRUCTURE_BONUSES.clear();
        STRUCTURE_TAG_BONUSES.clear();
        COMMON.structureBonuses.get().forEach(Config::readStructureBonus);
        COMMON.structureTagBonuses.get().forEach(Config::readStructureTagBonus);
        DynamicDifficulty.LOGGER.info("Reloaded {} structure bonuses and {} structure tag bonuses from config", 
                                    STRUCTURE_BONUSES.size(), STRUCTURE_TAG_BONUSES.size());
      }
    }
  }

  static {
    Pair<Common, ModConfigSpec> commonSpec = new ModConfigSpec.Builder().configure(Common::new);
    COMMON_SPEC = commonSpec.getRight();
    COMMON = commonSpec.getLeft();

    Pair<Client, ModConfigSpec> clientSpec = new ModConfigSpec.Builder().configure(Client::new);
    CLIENT_SPEC = clientSpec.getRight();
    CLIENT = clientSpec.getLeft();
  }

  public static class Common {
    // Base Leveling
    public final ConfigValue<Integer> startingLevel;
    public final ConfigValue<Integer> maxLevel;
    public final ConfigValue<Integer> randomLevelBonus;
    public final ConfigValue<Double> expBonus;

    // Environmental Scaling
    public final ConfigValue<Double> levelsPerDistance;
    public final ConfigValue<Double> levelsPerDeepness;
    public final ConfigValue<Double> levelsPerDay;
    public final ConfigValue<Double> levelPowerPerDistance;
    public final ConfigValue<Double> levelPowerPerDeepness;

    // Player-Based Scaling
    public final ConfigValue<Double> playerLevelRadius;
    public final ModConfigSpec.DoubleValue playerLevelMultiplier;
    public final ConfigValue<Boolean> applyPlayerBasedLeveling;
    public final ModConfigSpec.EnumValue<PlayerLevelDisplayStrategy> playerLevelDisplayStrategy;
    public final ConfigValue<Integer> playerLevelUpdateInterval;

    // Blacklist / Whitelist
    public final ConfigValue<Boolean> cancelLevelsForPassives;
    public final ConfigValue<List<String>> blacklistedMobs;
    public final ConfigValue<List<String>> whitelistedMobs;

    // Attribute Bonuses
    public final ConfigValue<List<? extends List<Object>>> attributesBonuses;
    
    // Structure Bonuses
    public final ConfigValue<List<? extends List<Object>>> structureBonuses;
    public final ConfigValue<List<? extends List<Object>>> structureTagBonuses;

    public Common(ModConfigSpec.Builder builder) {
      builder.push("Base Leveling");
      startingLevel = builder
              .comment("Base level for all entities")
              .define("Starting level", 1);
      maxLevel = builder
              .comment("Maximum level cap (0 for unlimited)")
              .define("Maximum level", 0);
      randomLevelBonus = builder
              .comment("Random bonus levels added to entities (0-value)")
              .define("Random level bonus", 0);
      expBonus = builder
              .comment("Additional experience multiplier per level")
              .define("Experience bonus per level", 0.1D);
      builder.pop();

      builder.push("Environmental Leveling");
      levelsPerDistance = builder
              .comment("How many levels to add per block from world spawn")
              .define("Levels per block from spawn", 0.01D);
      levelsPerDeepness = builder
              .comment("How many levels to add per block below sea level")
              .define("Levels per depth", 0.0D);
      levelsPerDay = builder
              .comment("How many levels to add per in-game day passed")
              .define("Levels per day", 0.0D);
      levelPowerPerDistance = builder
              .comment("Exponential level scaling with distance from spawn")
              .define("Distance power scaling", 0.0D);
      levelPowerPerDeepness = builder
              .comment("Exponential level scaling with depth")
              .define("Depth power scaling", 0.0D);
      builder.pop();

      builder.push("Player-Based Bonus Scaling");
      playerLevelRadius = builder
              .comment("Radius to search for players when calculating level bonuses")
              .define("Player search radius", 128.0D);
      playerLevelMultiplier = builder
              .comment("Multiplier for player level bonuses applied to mobs",
                      "This scales the bonus that nearby players add to mob levels",
                      "1.0 = normal scaling, 0.5 = half effect, 2.0 = double effect")
              .defineInRange("Player level multiplier", 1.0D, 0.0D, 10.0D);
      applyPlayerBasedLeveling = builder
              .comment("Whether to factor in player levels when calculating mob levels")
              .define("Enable player-based leveling", true);
      playerLevelDisplayStrategy = builder
              .comment("How to aggregate multiple player level providers for display",
                      "HIGHEST_PRIORITY: Use the provider with the highest priority (defined by the provider itself)",
                      "MAX: Use the maximum level from all providers",
                      "SUM: Add all provider levels together",
                      "AVERAGE: Average all provider levels",
                      "FIRST: Use only the first registered provider")
              .defineEnum("Player level display strategy", PlayerLevelDisplayStrategy.HIGHEST_PRIORITY);
      playerLevelUpdateInterval = builder
              .comment("How often (in ticks) to update player levels as a fallback (20 ticks = 1 second)",
                      "Providers can trigger immediate updates via events, this is just a safety net",
                      "Set to 0 to disable periodic updates (only event-driven updates will occur)")
              .defineInRange("Player level update interval", 600, 0, 1200);
      builder.pop();

      builder.push("Structure-Based Bonus Scaling");
      structureBonuses = builder
              .comment("List of [structure_id, level_bonus] pairs for individual structures",
                      "structure_id: The resource location of the structure (e.g., \"minecraft:fortress\")",
                      "level_bonus: The number of levels to add for entities spawning in this structure",
                      "Individual structure IDs take precedence over structure tags")
              .defineList("Structure level bonuses",
                      Config::getDefaultStructureBonuses,
                      Config::isValidStructureBonus);
      structureTagBonuses = builder
              .comment("List of [structure_tag, level_bonus] pairs for structure tags",
                      "structure_tag: The tag for structures (e.g., \"minecraft:village\")",
                      "level_bonus: The number of levels to add for entities spawning in structures with this tag",
                      "These are overridden by individual structure bonuses if both are present",
                      "Note that Structure Bonuses apply in addition to scaling from this config, or leveling datapacks" )
              .defineList("Structure tag level bonuses",
                      Config::getDefaultStructureTagBonuses,
                      Config::isValidStructureBonus);
      builder.pop();

      builder.push("Entity Filtering");
      cancelLevelsForPassives = builder
              .comment("Whether passive mobs (animals) should be prevented from leveling")
              .define("Disable passive mob leveling", true);
      blacklistedMobs = builder
              .comment("Entities that cannot level up",
                      "Example: [\"minecraft:zombie\", \"minecraft:skeleton\"]")
              .define("Blacklisted entities", new ArrayList<>());
      whitelistedMobs = builder
              .comment("If not empty, only these entities can level up")
              .define("Whitelisted entities", new ArrayList<>());
      builder.pop();

      builder.push("Attribute Bonuses");
      attributesBonuses = builder
              .comment("List of [attribute_id, bonus_per_level, operation_type] triplets",
                      "attribute_id: The resource location of the attribute (e.g., \"minecraft:generic.attack_damage\")",
                      "bonus_per_level: The amount to add per entity level",
                      "operation_type: 0 = ADD_VALUE (flat addition), 1 = ADD_MULTIPLIED_BASE (percentage), 2 = ADD_MULTIPLIED_TOTAL (percentage of final value)",
                      "If operation_type is omitted, ADD_VALUE is used (except for max_health which defaults to ADD_MULTIPLIED_BASE)")
              .defineList("Level bonus per attribute",
                      Config::getDefaultAttributeBonuses,
                      Config::isValidAttributeBonus);
      builder.pop();

    }
  }

  public static class Client {
    // Visibility
    public final ModConfigSpec.EnumValue<RenderBehavior> renderBehavior;
    public final ConfigValue<Double> renderDistance;
    public final ConfigValue<List<String>> hiddenLevelEntities;
    public final ConfigValue<Boolean> showApotheosisWorldTier;
    
    // Integration Options
    public final ConfigValue<Boolean> enableJadeIntegration;
    
    // Structure Title Display
    public final ConfigValue<Boolean> showStructureTitles;
    public final ConfigValue<Integer> structureTitleFadeInTime;
    public final ConfigValue<Integer> structureTitleDisplayTime;
    public final ConfigValue<Integer> structureTitleFadeOutTime;
    public final ConfigValue<String> structureTitleTextColor;
    public final ConfigValue<Boolean> structureTitleRenderShadow;
    public final ConfigValue<Double> structureTitleTextSize;
    public final ConfigValue<Integer> structureTitleXOffset;
    public final ConfigValue<Integer> structureTitleYOffset;
    public final ConfigValue<Float> structureSubtitleScale;
    public final ConfigValue<Integer> structureSubtitleSpacing;
    public final ConfigValue<Boolean> structureTitleCenterText;

    public Client(ModConfigSpec.Builder builder) {
      builder.push("Level Plate Settings");
      renderBehavior = builder
              .comment("Determines when entity levels are rendered: ALWAYS, NEVER, or LOOKING_AT (only when the player is looking directly at/near the entity).")
              .defineEnum("Render Behavior", RenderBehavior.LOOKING_AT);
      renderDistance = builder.define("Maximum render distance", 64.0D);
      showApotheosisWorldTier = builder
              .comment("Show Apotheosis world tier in entity level display (if Apotheosis is installed)",
                      "This will scan entity attributes for Apotheosis tier modifiers",
                      "Tiers: Haven, Frontier, Ascent, Summit, Pinnacle")
              .define("Show Apotheosis World Tier", true);
      builder.pop();
      
      builder.push("Integration Options");
      enableJadeIntegration = builder
              .comment("Show entity levels in Jade tooltips (requires Jade to be installed)")
              .define("Enable Jade integration", true);
      builder.pop();
      
      builder.push("Entity Settings");
      hiddenLevelEntities = builder.define("Entities with hidden levels", new ArrayList<>());
      builder.pop();
      
      builder.push("Structure Title Display");
      showStructureTitles = builder
              .comment("Display structure names and level bonuses when entering structures")
              .define("Show structure titles", true);
      structureTitleFadeInTime = builder
              .comment("Time in ticks for structure title to fade in")
              .defineInRange("Fade in time", 10, 0, 100);
      structureTitleDisplayTime = builder
              .comment("Time in ticks to display structure title")
              .defineInRange("Display time", 60, 0, 600);
      structureTitleFadeOutTime = builder
              .comment("Time in ticks for structure title to fade out")
              .defineInRange("Fade out time", 20, 0, 100);
      structureTitleTextColor = builder
              .comment("Text color in hex format (e.g., \"FFFFFF\" for white)")
              .define("Text color", "FFFFFF");
      structureTitleRenderShadow = builder
              .comment("Render text shadow for structure titles")
              .define("Render shadow", true);
      structureTitleTextSize = builder
              .comment("Text size multiplier for structure titles")
              .defineInRange("Text size", 2.0, 0.5, 5.0);
      structureTitleXOffset = builder
              .comment("X offset for structure title position")
              .define("X offset", 0);
      structureTitleYOffset = builder
              .comment("Y offset for structure title position")
              .define("Y offset", 20);
      structureSubtitleScale = builder
                .comment("Size for structure subtitles, relative to title")
                .define("Subtitle Scale", 0.7F);
      structureSubtitleSpacing = builder
                .comment("Vertical distance between title and subtitle")
                .define("Subtitle Spacing", 15);
      structureTitleCenterText = builder
              .comment("Center structure title text on screen")
              .define("Center text", true);
      builder.pop();
    }
  }

  private static List<List<Object>> getDefaultAttributeBonuses() {
    List<List<Object>> attributeBonuses = new ArrayList<>();
    // Format: [attribute_id, bonus_per_level, operation_type]
    // operation_type: 0 = ADD_VALUE, 1 = ADD_MULTIPLIED_BASE, 2 = ADD_MULTIPLIED_TOTAL
    attributeBonuses.add(Arrays.asList("minecraft:generic.attack_damage", 0.2, 0)); // ADD_VALUE
    attributeBonuses.add(Arrays.asList("minecraft:generic.armor", 0.2, 0)); // ADD_VALUE
    attributeBonuses.add(Arrays.asList("minecraft:generic.max_health", 0.05, 1)); // ADD_MULTIPLIED_BASE
    attributeBonuses.add(Arrays.asList("dynamic_difficulty:projectile_damage_bonus", 0.2, 0)); // ADD_VALUE
    attributeBonuses.add(Arrays.asList("dynamic_difficulty:explosion_damage_bonus", 0.2, 0)); // ADD_VALUE
    return attributeBonuses;
  }
  
  private static List<List<Object>> getDefaultStructureBonuses() {
    List<List<Object>> structureBonuses = new ArrayList<>();
    // Format: [structure_id, level_bonus]
    structureBonuses.add(Arrays.asList("minecraft:trial_dungeon", 10));
    return structureBonuses;
  }
  
  private static List<List<Object>> getDefaultStructureTagBonuses() {
    List<List<Object>> structureTagBonuses = new ArrayList<>();
    // Format: [structure_tag, level_bonus]
    // Built-in difficulty tags
    structureTagBonuses.add(Arrays.asList("dynamic_difficulty:level_1", 5));
    structureTagBonuses.add(Arrays.asList("dynamic_difficulty:level_2", 10));
    structureTagBonuses.add(Arrays.asList("dynamic_difficulty:level_3", 15));
    structureTagBonuses.add(Arrays.asList("dynamic_difficulty:level_4", 20));
    structureTagBonuses.add(Arrays.asList("dynamic_difficulty:level_5", 25));
    structureTagBonuses.add(Arrays.asList("dynamic_difficulty:level_6", 30));
    return structureTagBonuses;
  }

  private static <T> boolean isValidAttributeBonus(T object) {
    if (object instanceof List<?> list) {
      // Support both old format (2 elements) and new format (3 elements)
      if (list.size() == 2) {
        return list.get(0) instanceof String && list.get(1) instanceof Double;
      } else if (list.size() == 3) {
        return list.get(0) instanceof String && list.get(1) instanceof Double && list.get(2) instanceof Integer;
      }
    }
    return false;
  }
  
  private static <T> boolean isValidStructureBonus(T object) {
    if (object instanceof List<?> list) {
      if (list.size() == 2) {
        return list.get(0) instanceof String && list.get(1) instanceof Integer;
      }
    }
    return false;
  }

  public static Map<ResourceKey<Attribute>, AttributeModifier> getAttributeBonuses() {
    if (ATTRIBUTE_BONUSES.isEmpty()) {
      // Only initialize once to avoid triggering config reloads
      synchronized (ATTRIBUTE_BONUSES) {
        if (ATTRIBUTE_BONUSES.isEmpty()) {
          COMMON.attributesBonuses.get().forEach(Config::readAttributeBonus);
          DynamicDifficulty.LOGGER.info("Initialized {} attribute bonuses from config", ATTRIBUTE_BONUSES.size());
        }
      }
    }
    return ATTRIBUTE_BONUSES;
  }

  private static void readAttributeBonus(List<Object> attributeBonusConfig) {
    ResourceLocation attributeRL = ResourceLocation.tryParse((String) attributeBonusConfig.get(0));
    if (attributeRL == null) {
        DynamicDifficulty.LOGGER.error("Attribute ID '{}' is invalid!", attributeBonusConfig.get(0));
        return;
    }
    Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(attributeRL);
    float attributeBonus = ((Double) attributeBonusConfig.get(1)).floatValue();

    if (attribute == null) {
      DynamicDifficulty.LOGGER.error("Attribute '{}' can not be found for ID: {}!", attributeRL, attributeBonusConfig.get(0));
      return;
    }

    Optional<ResourceKey<Attribute>> optAttributeKey = BuiltInRegistries.ATTRIBUTE.getResourceKey(attribute);
    if (optAttributeKey.isEmpty()) {
        DynamicDifficulty.LOGGER.error("Could not retrieve ResourceKey for attribute: {} ({})", attribute.getDescriptionId(), attributeRL);
        return;
    }
    ResourceKey<Attribute> attributeKey = optAttributeKey.get();

    String uniqueModifierName = "config_bonus_" + attributeRL.getNamespace().replace(":", "_") + "_" + attributeRL.getPath().replace("/", "_");
    ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, uniqueModifierName);

    AttributeModifier.Operation operation;
    
    // Check if operation is specified (new format with 3 elements)
    if (attributeBonusConfig.size() >= 3) {
      int operationId = ((Integer) attributeBonusConfig.get(2));
      operation = switch (operationId) {
        case 0 -> AttributeModifier.Operation.ADD_VALUE;
        case 1 -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
        case 2 -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        default -> {
          DynamicDifficulty.LOGGER.warn("Unknown operation ID: {}. Defaulting to ADD_VALUE", operationId);
          yield AttributeModifier.Operation.ADD_VALUE;
        }
      };
    } else {
      // Legacy format - use old behavior
      if (attributeKey.location().equals(Attributes.MAX_HEALTH.unwrapKey().orElse(null).location())) {
        operation = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
      } else {
        operation = AttributeModifier.Operation.ADD_VALUE;
      }
    }

    AttributeModifier modifier =
            new AttributeModifier(modifierId, attributeBonus, operation);
    
    ATTRIBUTE_BONUSES.put(attributeKey, modifier);
    DynamicDifficulty.LOGGER.info("Config: Registered attribute bonus for ResourceKey {} ({}) with amount {}/level, operation {}, ModID {}", 
                                attributeKey.location(), attribute.getDescriptionId(), attributeBonus, operation, modifierId);
  }
  
  private static void readStructureBonus(List<Object> structureBonusConfig) {
    String structureId = (String) structureBonusConfig.get(0);
    ResourceLocation structureRL = ResourceLocation.tryParse(structureId);
    if (structureRL == null) {
      DynamicDifficulty.LOGGER.error("Structure ID '{}' is invalid!", structureId);
      return;
    }
    int levelBonus = ((Integer) structureBonusConfig.get(1));
    
    STRUCTURE_BONUSES.put(structureRL, levelBonus);
    DynamicDifficulty.LOGGER.info("Config: Registered structure bonus for {} with {} levels", structureRL, levelBonus);
  }
  
  private static void readStructureTagBonus(List<Object> structureTagBonusConfig) {
    String tagId = (String) structureTagBonusConfig.get(0);
    ResourceLocation tagRL = ResourceLocation.tryParse(tagId);
    if (tagRL == null) {
      DynamicDifficulty.LOGGER.error("Structure tag ID '{}' is invalid!", tagId);
      return;
    }
    int levelBonus = ((Integer) structureTagBonusConfig.get(1));
    
    STRUCTURE_TAG_BONUSES.put(tagRL, levelBonus);
    DynamicDifficulty.LOGGER.info("Config: Registered structure tag bonus for {} with {} levels", tagRL, levelBonus);
  }
  
  public static Map<ResourceLocation, Integer> getStructureBonuses() {
    if (STRUCTURE_BONUSES.isEmpty() && STRUCTURE_TAG_BONUSES.isEmpty()) {
      synchronized (STRUCTURE_BONUSES) {
        synchronized (STRUCTURE_TAG_BONUSES) {
          if (STRUCTURE_BONUSES.isEmpty() && STRUCTURE_TAG_BONUSES.isEmpty()) {
            COMMON.structureBonuses.get().forEach(Config::readStructureBonus);
            COMMON.structureTagBonuses.get().forEach(Config::readStructureTagBonus);
            DynamicDifficulty.LOGGER.info("Initialized {} structure bonuses and {} structure tag bonuses from config", 
                                        STRUCTURE_BONUSES.size(), STRUCTURE_TAG_BONUSES.size());
          }
        }
      }
    }
    return STRUCTURE_BONUSES;
  }
  
  public static Map<ResourceLocation, Integer> getStructureTagBonuses() {
    if (STRUCTURE_BONUSES.isEmpty() && STRUCTURE_TAG_BONUSES.isEmpty()) {
      getStructureBonuses(); // This will initialize both maps
    }
    return STRUCTURE_TAG_BONUSES;
  }
}
