package dev.muon.dynamic_difficulty.config;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.player.PlayerLevelDisplayStrategy;
import java.util.*;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

  public enum RenderBehavior {
    ALWAYS,
    NEVER,
    LOOKING_AT
  }

  public enum AnchorPoint {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT
  }

  public static void register() {
    NeoForgeConfigRegistry.INSTANCE.register(
        DynamicDifficulty.MODID,
        ModConfig.Type.COMMON,
        COMMON_SPEC
    );

    NeoForgeModConfigEvents.reloading(DynamicDifficulty.MODID).register(config -> {
      if (config.getType() == ModConfig.Type.COMMON) {
        reloadAttributeBonuses();
        // Reload whitelist/blacklist cache to avoid string concatenation on hot path
        dev.muon.dynamic_difficulty.util.LevelingUtils.reloadConfigCache();
      }
    });
  }
  
  public static void registerClient() {
    NeoForgeConfigRegistry.INSTANCE.register(
        DynamicDifficulty.MODID,
        ModConfig.Type.CLIENT,
        CLIENT_SPEC
    );
  }

  public static void reloadAttributeBonuses() {
    synchronized (ATTRIBUTE_BONUSES) {
      ATTRIBUTE_BONUSES.clear();
      COMMON.attributesBonuses.get().forEach(Config::readAttributeBonus);
      DynamicDifficulty.LOGGER.info("Reloaded {} attribute bonuses from config", ATTRIBUTE_BONUSES.size());
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
    public final ConfigValue<Double> levelsPerHeight;
    public final ConfigValue<Double> levelsPerDay;
    public final ConfigValue<Double> levelsPerLocalDifficulty;
    public final ConfigValue<Double> levelPowerPerDistance;
    public final ConfigValue<Double> levelPowerPerDeepness;

    // Player-Based Scaling
    public final ConfigValue<Double> playerLevelRadius;
    public final ModConfigSpec.DoubleValue playerLevelMultiplier;
    public final ConfigValue<Boolean> applyPlayerBasedLeveling;
    public final ModConfigSpec.EnumValue<PlayerLevelDisplayStrategy> playerLevelDisplayStrategy;
    public final ConfigValue<Integer> playerLevelUpdateInterval;
    
    // Playtime-Based Scaling
    public final ConfigValue<Boolean> enablePlaytimeScaling;
    public final ConfigValue<Double> levelsPerPlaytimeHour;

    // Blacklist / Whitelist
    public final ConfigValue<Boolean> cancelLevelsForPassives;
    public final ConfigValue<List<String>> blacklistedMobs;
    public final ConfigValue<List<String>> whitelistedMobs;

    // Attribute Bonuses
    public final ConfigValue<List<? extends List<Object>>> attributesBonuses;

    // Level-Up Items
    public final ConfigValue<Integer> potionOfGrowthMaxLevel;
    public final ConfigValue<Integer> elixirOfNurturingMaxLevel;
    public final ConfigValue<Integer> draughtOfAscensionMaxLevel;
    public final ConfigValue<Integer> essenceOfVitalityMaxLevel;
    public final ConfigValue<Integer> crystalOfAwakeningMaxLevel;
    
    // Level-Based Drops
    public final ConfigValue<Boolean> enableLevelBasedDrops;
    
    // Puffish Skills Integration
    public final ConfigValue<List<String>> puffishSkillsTreeBlacklist;

    public Common(ModConfigSpec.Builder builder) {
      builder.push("base_leveling");
      startingLevel = builder
              .comment("Base level for all entities")
              .define("starting_level", 1);
      maxLevel = builder
              .comment("Maximum level cap (0 for unlimited)")
              .define("maximum_level", 0);
      randomLevelBonus = builder
              .comment("Random bonus levels added to entities (0-value)")
              .define("random_level_bonus", 0);
      expBonus = builder
              .comment("Additional experience multiplier per level")
              .define("experience_bonus_per_level", 0.1D);
      builder.pop();

      builder.push("environmental_leveling");
      levelsPerDistance = builder
              .comment("How many levels to add per block from world spawn")
              .define("levels_per_block_from_spawn", 0.01D);
      levelsPerDeepness = builder
              .comment("How many levels to add per block below sea level")
              .define("levels_per_depth", 0.0D);
      levelsPerHeight = builder
              .comment("How many levels to add per block above sea level")
              .define("levels_per_height", 0.0D);
      levelsPerDay = builder
              .comment("How many levels to add per in-game day passed")
              .define("levels_per_day", 0.0D);
      levelsPerLocalDifficulty = builder
              .comment("How many levels to add per point of local difficulty at mob spawn location",
                      "Local difficulty considers regional difficulty, chunk inhabited time, moon phase, and world difficulty")
              .define("levels_per_local_difficulty", 0.0D);
      levelPowerPerDistance = builder
              .comment("Exponential level scaling with distance from spawn")
              .define("distance_power_scaling", 0.0D);
      levelPowerPerDeepness = builder
              .comment("Exponential level scaling with depth")
              .define("depth_power_scaling", 0.0D);
      builder.pop();

      builder.push("player_based_bonus_scaling");
      playerLevelRadius = builder
              .comment("Radius to search for players when calculating level bonuses")
              .define("player_search_radius", 128.0D);
      playerLevelMultiplier = builder
              .comment("Multiplier for player level bonuses applied to mobs",
                      "This scales the bonus that nearby players add to mob levels",
                      "1.0 = normal scaling, 0.5 = half effect, 2.0 = double effect")
              .defineInRange("player_level_multiplier", 1.0D, 0.0D, 10.0D);
      applyPlayerBasedLeveling = builder
              .comment("Whether to factor in player levels when calculating mob levels")
              .define("enable_player_based_leveling", true);
      playerLevelDisplayStrategy = builder
              .comment("How to aggregate multiple player level providers for display",
                      "HIGHEST_PRIORITY: Use the provider with the highest priority (defined by the provider itself)",
                      "MAX: Use the maximum level from all providers",
                      "SUM: Add all provider levels together",
                      "AVERAGE: Average all provider levels",
                      "FIRST: Use only the first registered provider")
              .defineEnum("player_level_display_strategy", PlayerLevelDisplayStrategy.HIGHEST_PRIORITY);
      playerLevelUpdateInterval = builder
              .comment("How often (in ticks) to update player levels as a fallback (20 ticks = 1 second)",
                      "Providers can trigger immediate updates via events, this is just a safety net",
                      "Set to 0 to disable periodic updates (only event-driven updates will occur)")
              .defineInRange("player_level_update_interval", 600, 0, 1200);
      builder.pop();
      
      builder.push("playtime_based_scaling");
      enablePlaytimeScaling = builder
              .comment("Enable playtime-based level scaling",
                      "When enabled, uses Minecraft's built-in playtime statistic (Stats.PLAY_TIME)",
                      "Playtime is automatically tracked by Minecraft and persists across sessions")
              .define("enable_playtime_scaling", false);
      levelsPerPlaytimeHour = builder
              .comment("How many levels to add per hour of nearby-player playtime",
                      "Playtime from all nearby players is averaged and converted to levels",
                      "Example: 2 players with 10 and 20 hours = 15 hours average = 15 * this_value levels")
              .define("levels_per_playtime_hour", 0.1D);
      builder.pop();

      builder.push("entity_filtering");
      cancelLevelsForPassives = builder
              .comment("Whether passive mobs (animals) should be prevented from leveling")
              .define("disable_passive_mob_leveling", true);
      blacklistedMobs = builder
              .comment("Entities that cannot level up",
                      "Example: [\"minecraft:zombie\", \"minecraft:skeleton\"]")
              .define("blacklisted_entities", new ArrayList<>());
      whitelistedMobs = builder
              .comment("If not empty, only these entities can level up")
              .define("whitelisted_entities", new ArrayList<>());
      builder.pop();

      builder.push("attribute_bonuses");
      attributesBonuses = builder
              .comment("List of [attribute_id, bonus_per_level, operation_type] triplets",
                      "attribute_id: The resource location of the attribute (e.g., \"minecraft:generic.attack_damage\")",
                      "bonus_per_level: The amount to add per entity level",
                      "operation_type: 0 = ADD_VALUE (flat addition), 1 = ADD_MULTIPLIED_BASE (percentage), 2 = ADD_MULTIPLIED_TOTAL (percentage of final value)",
                      "If operation_type is omitted, ADD_VALUE is used (except for max_health which defaults to ADD_MULTIPLIED_BASE)")
              .defineList("level_bonus_per_attribute",
                      Config::getDefaultAttributeBonuses,
                      Config::isValidAttributeBonus);
      builder.pop();

      builder.push("level_up_items");
      potionOfGrowthMaxLevel = builder
              .comment("Maximum level that Potion of Growth can raise an entity to")
              .defineInRange("potion_of_growth_max_level", 20, 1, 10000);
      elixirOfNurturingMaxLevel = builder
              .comment("Maximum level that Elixir of Nurturing can raise an entity to")
              .defineInRange("elixir_of_nurturing_max_level", 40, 1, 10000);
      draughtOfAscensionMaxLevel = builder
              .comment("Maximum level that Draught of Ascension can raise an entity to")
              .defineInRange("draught_of_ascension_max_level", 60, 1, 10000);
      essenceOfVitalityMaxLevel = builder
              .comment("Maximum level that Essence of Vitality can raise an entity to")
              .defineInRange("essence_of_vitality_max_level", 80, 1, 10000);
      crystalOfAwakeningMaxLevel = builder
              .comment("Maximum level that Crystal of Awakening can raise an entity to")
              .defineInRange("crystal_of_awakening_max_level", 100, 1, 10000);
      builder.pop();

      builder.push("level_based_drops");
      enableLevelBasedDrops = builder
              .comment("Whether mobs should drop level-up items based on their level",
                      "The drops are defined in data/dynamic_difficulty/loot_table/inject/level_based_drops.json",
                      "Users can edit that file to customize drop rates, level ranges, and add custom items")
              .define("enable_level_based_drops", true);
      builder.pop();
      
      builder.push("puffish_skills_integration");
      puffishSkillsTreeBlacklist = builder
              .comment("List of Puffish Skills tree IDs that should be excluded from player level calculations",
                      "Trees in this list will not contribute their points to player-based mob level scaling",
                      "Example: [\"puffish_skills:mining\", \"puffish_skills:combat\"]",
                      "Leave empty to include all trees in mob scaling")
              .define("puffish_skills_tree_blacklist", new ArrayList<>());
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
    public final ConfigValue<Boolean> structureTitleOnlyAnnounceIfModified;
    public final ConfigValue<Integer> structureTitleFadeInTime;
    public final ConfigValue<Integer> structureTitleDisplayTime;
    public final ConfigValue<Integer> structureTitleFadeOutTime;
    public final ConfigValue<String> structureTitleTextColor;
    public final ConfigValue<Boolean> structureTitleRenderShadow;
    public final ConfigValue<Double> structureTitleTextSize;
    public final ModConfigSpec.EnumValue<AnchorPoint> structureTitleAnchor;
    public final ConfigValue<Integer> structureTitleXOffset;
    public final ConfigValue<Integer> structureTitleYOffset;
    
    // Biome Title Display
    public final ConfigValue<Boolean> showBiomeTitles;
    public final ConfigValue<Boolean> biomeTitleOnlyAnnounceIfModified;
    public final ConfigValue<Integer> biomeTitleFadeInTime;
    public final ConfigValue<Integer> biomeTitleDisplayTime;
    public final ConfigValue<Integer> biomeTitleFadeOutTime;
    public final ConfigValue<String> biomeTitleTextColor;
    public final ConfigValue<Boolean> biomeTitleRenderShadow;
    public final ConfigValue<Double> biomeTitleTextSize;
    public final ModConfigSpec.EnumValue<AnchorPoint> biomeTitleAnchor;
    public final ConfigValue<Integer> biomeTitleXOffset;
    public final ConfigValue<Integer> biomeTitleYOffset;
    public final ConfigValue<Integer> biomeTitleCooldownTime;
    public final ConfigValue<Integer> biomeRecentCacheSize;
    
    // Dimension Title Display
    public final ConfigValue<Boolean> showDimensionTitles;
    public final ConfigValue<Boolean> dimensionTitleOnlyAnnounceIfModified;
    public final ConfigValue<Integer> dimensionTitleFadeInTime;
    public final ConfigValue<Integer> dimensionTitleDisplayTime;
    public final ConfigValue<Integer> dimensionTitleFadeOutTime;
    public final ConfigValue<String> dimensionTitleTextColor;
    public final ConfigValue<Boolean> dimensionTitleRenderShadow;
    public final ConfigValue<Double> dimensionTitleTextSize;
    public final ModConfigSpec.EnumValue<AnchorPoint> dimensionTitleAnchor;
    public final ConfigValue<Integer> dimensionTitleXOffset;
    public final ConfigValue<Integer> dimensionTitleYOffset;
    
    // Level Info Display
    public final ConfigValue<Integer> levelInfoFadeInTime;
    public final ConfigValue<Integer> levelInfoDisplayTime;
    public final ConfigValue<Integer> levelInfoFadeOutTime;
    public final ConfigValue<String> levelInfoTextColor;
    public final ConfigValue<Boolean> levelInfoRenderShadow;
    public final ConfigValue<Double> levelInfoTextSize;
    public final ModConfigSpec.EnumValue<AnchorPoint> levelInfoAnchor;
    public final ConfigValue<Integer> levelInfoXOffset;
    public final ConfigValue<Integer> levelInfoYOffset;

    public Client(ModConfigSpec.Builder builder) {
      builder.push("level_plate_settings");
      renderBehavior = builder
              .comment("Determines when entity levels are rendered: ALWAYS, NEVER, or LOOKING_AT (only when the player is looking directly at/near the entity).")
              .defineEnum("render_behavior", RenderBehavior.LOOKING_AT);
      renderDistance = builder.define("maximum_render_distance", 64.0D);
      showApotheosisWorldTier = builder
              .comment("Show Apotheosis world tier in entity level display (if Apotheosis is installed)",
                      "This will scan entity attributes for Apotheosis tier modifiers",
                      "Tiers: Haven, Frontier, Ascent, Summit, Pinnacle")
              .define("show_apotheosis_world_tier", true);
      builder.pop();
      
      builder.push("integration_options");
      enableJadeIntegration = builder
              .comment("Show entity levels in Jade tooltips (requires Jade to be installed)")
              .define("enable_jade_integration", true);
      builder.pop();
      
      builder.push("entity_settings");
      hiddenLevelEntities = builder.define("entities_with_hidden_levels", new ArrayList<>());
      builder.pop();
      
      builder.push("structure_title_display");
      showStructureTitles = builder
              .comment("Display structure names and level bonuses when entering structures")
              .define("show_structure_titles", true);
      structureTitleOnlyAnnounceIfModified = builder
              .comment("Only display structure titles if the structure provides a level bonus",
                      "When false (default), structure titles always display when entering structures",
                      "When true, structure titles only display if structureBonus > 0")
              .define("structure_title_only_announce_if_modified", false);
      structureTitleFadeInTime = builder
              .comment("Time in ticks for structure title to fade in")
              .defineInRange("structure_title_fade_in_time", 10, 0, 100);
      structureTitleDisplayTime = builder
              .comment("Time in ticks to display structure title")
              .defineInRange("structure_title_display_time", 60, 0, 600);
      structureTitleFadeOutTime = builder
              .comment("Time in ticks for structure title to fade out")
              .defineInRange("structure_title_fade_out_time", 20, 0, 100);
      structureTitleTextColor = builder
              .comment("Text color in hex format (e.g., \"FFFFFF\" for white)")
              .define("structure_title_text_color", "FFFFFF");
      structureTitleRenderShadow = builder
              .comment("Render text shadow for structure titles")
              .define("structure_title_render_shadow", true);
      structureTitleTextSize = builder
              .comment("Text size multiplier for structure titles")
              .defineInRange("structure_title_text_size", 2.0, 0.5, 5.0);
      structureTitleAnchor = builder
              .comment("Anchor point for structure title positioning")
              .defineEnum("structure_title_anchor", AnchorPoint.BOTTOM_CENTER);
      structureTitleXOffset = builder
              .comment("X offset from anchor point for structure title position")
              .define("structure_title_x_offset", 0);
      structureTitleYOffset = builder
              .comment("Y offset from anchor point for structure title position")
              .define("structure_title_y_offset", -82);
      structureSubtitleScale = builder
                .comment("Size for structure subtitles, relative to title")
                .define("structure_subtitle_scale", 0.7F);
      structureSubtitleSpacing = builder
                .comment("Vertical distance between title and subtitle")
                .define("structure_subtitle_spacing", 15);
      structureTitleCenterText = builder
              .comment("Center structure title text on screen")
              .define("structure_title_center_text", true);
      builder.pop();
      
      builder.push("biome_title_display");
      showBiomeTitles = builder
              .comment("Display biome names when entering biomes")
              .define("show_biome_titles", true);
      biomeTitleOnlyAnnounceIfModified = builder
              .comment("Only display biome titles if the biome provides a level bonus",
                      "When false (default), biome titles always display when entering biomes",
                      "When true, biome titles only display if biomeBonus > 0")
              .define("biome_title_only_announce_if_modified", false);
      biomeTitleFadeInTime = builder
              .comment("Time in ticks for biome title to fade in")
              .defineInRange("biome_title_fade_in_time", 10, 0, 100);
      biomeTitleDisplayTime = builder
              .comment("Time in ticks to display biome title")
              .defineInRange("biome_title_display_time", 60, 0, 600);
      biomeTitleFadeOutTime = builder
              .comment("Time in ticks for biome title to fade out")
              .defineInRange("biome_title_fade_out_time", 20, 0, 100);
      biomeTitleTextColor = builder
              .comment("Text color in hex format (e.g., \"FFFFFF\" for white)")
              .define("biome_title_text_color", "FFFFFF");
      biomeTitleRenderShadow = builder
              .comment("Render text shadow for biome titles")
              .define("biome_title_render_shadow", true);
      biomeTitleTextSize = builder
              .comment("Text size multiplier for biome titles")
              .defineInRange("biome_title_text_size", 1.4, 0.5, 5.0);
      biomeTitleAnchor = builder
              .comment("Anchor point for biome title positioning")
              .defineEnum("biome_title_anchor", AnchorPoint.TOP_CENTER);
      biomeTitleXOffset = builder
              .comment("X offset from anchor point for biome title position")
              .define("biome_title_x_offset", 0);
      biomeTitleYOffset = builder
              .comment("Y offset from anchor point for biome title position")
              .define("biome_title_y_offset", -60);
      biomeTitleCooldownTime = builder
              .comment("Cooldown time in ticks before biome title can be shown again")
              .defineInRange("biome_title_cooldown_time", 20, 0, 200);
      biomeRecentCacheSize = builder
              .comment("Number of recent biomes to cache (prevents spam)")
              .defineInRange("biome_recent_cache_size", 5, 0, 20);
      builder.pop();
      
      builder.push("dimension_title_display");
      showDimensionTitles = builder
              .comment("Display dimension names when entering dimensions")
              .define("show_dimension_titles", true);
      dimensionTitleOnlyAnnounceIfModified = builder
              .comment("Only display dimension titles if the dimension modifies leveling",
                      "When false (default), dimension titles always display when entering dimensions",
                      "When true, dimension titles only display if the dimension has leveling settings")
              .define("dimension_title_only_announce_if_modified", false);
      dimensionTitleFadeInTime = builder
              .comment("Time in ticks for dimension title to fade in")
              .defineInRange("dimension_title_fade_in_time", 10, 0, 100);
      dimensionTitleDisplayTime = builder
              .comment("Time in ticks to display dimension title")
              .defineInRange("dimension_title_display_time", 60, 0, 600);
      dimensionTitleFadeOutTime = builder
              .comment("Time in ticks for dimension title to fade out")
              .defineInRange("dimension_title_fade_out_time", 20, 0, 100);
      dimensionTitleTextColor = builder
              .comment("Text color in hex format (e.g., \"FFFFFF\" for white)")
              .define("dimension_title_text_color", "FFFFFF");
      dimensionTitleRenderShadow = builder
              .comment("Render text shadow for dimension titles")
              .define("dimension_title_render_shadow", true);
      dimensionTitleTextSize = builder
              .comment("Text size multiplier for dimension titles")
              .defineInRange("dimension_title_text_size", 2.0, 0.5, 5.0);
      dimensionTitleAnchor = builder
              .comment("Anchor point for dimension title positioning")
              .defineEnum("dimension_title_anchor", AnchorPoint.TOP_CENTER);
      dimensionTitleXOffset = builder
              .comment("X offset from anchor point for dimension title position")
              .define("dimension_title_x_offset", 0);
      dimensionTitleYOffset = builder
              .comment("Y offset from anchor point for dimension title position")
              .define("dimension_title_y_offset", -35);
      builder.pop();
      
      builder.push("level_info_display");
      levelInfoFadeInTime = builder
              .comment("Time in ticks for level info to fade in")
              .defineInRange("level_info_fade_in_time", 10, 0, 100);
      levelInfoDisplayTime = builder
              .comment("Time in ticks to display level info")
              .defineInRange("level_info_display_time", 60, 0, 600);
      levelInfoFadeOutTime = builder
              .comment("Time in ticks for level info to fade out")
              .defineInRange("level_info_fade_out_time", 20, 0, 100);
      levelInfoTextColor = builder
              .comment("Text color in hex format (e.g., \"FFFFFF\" for white)")
              .define("level_info_text_color", "FFFFFF");
      levelInfoRenderShadow = builder
              .comment("Render text shadow for level info")
              .define("level_info_render_shadow", true);
      levelInfoTextSize = builder
              .comment("Text size multiplier for level info")
              .defineInRange("level_info_text_size", 1.4, 0.5, 5.0);
      levelInfoAnchor = builder
              .comment("Anchor point for level info positioning")
              .defineEnum("level_info_anchor", AnchorPoint.BOTTOM_CENTER);
      levelInfoXOffset = builder
              .comment("X offset from anchor point for level info position")
              .define("level_info_x_offset", 0);
      levelInfoYOffset = builder
              .comment("Y offset from anchor point for level info position")
              .define("level_info_y_offset", -62);
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
  
}
