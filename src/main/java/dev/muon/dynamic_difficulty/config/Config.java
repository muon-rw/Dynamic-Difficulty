package dev.muon.dynamic_difficulty.config;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;

public class Config {
  public static final Common COMMON;
  public static final ForgeConfigSpec COMMON_SPEC;
  public static final Client CLIENT;
  public static final ForgeConfigSpec CLIENT_SPEC;
  private static final Map<Attribute, AttributeModifier> ATTRIBUTE_BONUSES = new HashMap<>();

  // Define the RenderBehavior enum
  public enum RenderBehavior {
    ALWAYS,
    NEVER,
    LOOKING_AT
  }

  public static void register(FMLJavaModLoadingContext context) {
    context.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
  }

  static {
    Pair<Common, ForgeConfigSpec> commonSpec = new ForgeConfigSpec.Builder().configure(Common::new);
    COMMON_SPEC = commonSpec.getRight();
    COMMON = commonSpec.getLeft();

    Pair<Client, ForgeConfigSpec> clientSpec = new ForgeConfigSpec.Builder().configure(Client::new);
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
    public final ForgeConfigSpec.DoubleValue levelsPerPoint;
    public final ConfigValue<Boolean> applyPlayerBasedLeveling;

    // Blacklist / Whitelist
    public final ConfigValue<Boolean> cancelLevelsForPassives;
    public final ConfigValue<List<String>> blacklistedMobs;
    public final ConfigValue<List<String>> whitelistedMobs;

    // Attribute Bonuses
    public final ConfigValue<List<? extends List<Object>>> attributesBonuses;

    public Common(ForgeConfigSpec.Builder builder) {
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

      builder.push("Environmental Scaling");
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

      builder.push("Player-Based Scaling");
      playerLevelRadius = builder
              .comment("Radius to search for players when calculating level bonuses")
              .define("Player search radius", 128.0D);
      levelsPerPoint = builder
              .comment("How many levels to add per player skill point",
                      "Higher values mean faster level scaling with player progression")
              .defineInRange("Levels per skill point", 0.2D, 0.0D, 10.0D);
      applyPlayerBasedLeveling = builder
              .comment("Whether to factor in player levels when calculating mob levels")
              .define("Enable player-based leveling", false);
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
              .comment("List of [attribute_id, bonus_per_level] pairs")
              .defineList("Level bonus per attribute",
                      Config::getDefaultAttributeBonuses,
                      Config::isValidAttributeBonus);
      builder.pop();
    }
  }

  public static class Client {
    // Visibility
    public final ForgeConfigSpec.EnumValue<RenderBehavior> renderBehavior;
    public final ConfigValue<Double> renderDistance;
    public final ConfigValue<List<String>> hiddenLevelEntities;

    // Visual Settings
    public final ConfigValue<Integer> levelTextShiftX;
    public final ConfigValue<Integer> levelTextShiftY;
    public final ConfigValue<Float> textScale;

    public Client(ForgeConfigSpec.Builder builder) {
      builder.push("Level Plate Settings");
      renderBehavior = builder
              .comment("Determines when entity levels are rendered: ALWAYS, NEVER, or LOOKING_AT (only when the player is looking directly at/near the entity).")
              .defineEnum("Render Behavior", RenderBehavior.LOOKING_AT);
      renderDistance = builder.define("Maximum render distance", 64.0D);
      levelTextShiftX = builder.define("Level text X offset", 0);
      levelTextShiftY = builder.define("Level text Y offset", 0);
      textScale = builder.define("Level text relative size", 0.025F);
      builder.pop();
      builder.push("Entity Settings");
      hiddenLevelEntities = builder.define("Entities with hidden levels", new ArrayList<>());
      builder.pop();
    }
  }

  private static List<List<Object>> getDefaultAttributeBonuses() {
    List<List<Object>> attributeBonuses = new ArrayList<>();
    attributeBonuses.add(Arrays.asList("minecraft:generic.movement_speed", 0.001));
    attributeBonuses.add(Arrays.asList("minecraft:generic.flying_speed", 0.001));
    attributeBonuses.add(Arrays.asList("minecraft:generic.attack_damage", 0.1));
    attributeBonuses.add(Arrays.asList("minecraft:generic.armor", 0.1));
    attributeBonuses.add(Arrays.asList("minecraft:generic.max_health", 0.1));
    attributeBonuses.add(Arrays.asList("dynamic_difficulty:monster.projectile_damage_bonus", 0.1));
    attributeBonuses.add(Arrays.asList("dynamic_difficulty:monster.explosion_damage_bonus", 0.1));
    return attributeBonuses;
  }

  private static <T> boolean isValidAttributeBonus(T object) {
    if (object instanceof List<?> list) {
      return list.size() == 2 && list.get(0) instanceof String && list.get(1) instanceof Double;
    }
    return false;
  }

  public static Map<Attribute, AttributeModifier> getAttributeBonuses() {
    if (ATTRIBUTE_BONUSES.isEmpty()) {
      for (List<Object> objects : Config.COMMON.attributesBonuses.get()) {
        readAttributeBonus(objects);
      }
    }
    return ATTRIBUTE_BONUSES;
  }

  private static void readAttributeBonus(List<Object> attributeBonusConfig) {
    ResourceLocation attributeId = new ResourceLocation((String) attributeBonusConfig.get(0));
    Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(attributeId);
    float attributeBonus = ((Double) attributeBonusConfig.get(1)).floatValue();
    if (attribute == null) {
      DynamicDifficulty.LOGGER.error("Attribute '" + attributeId + "' can not be found!");
      return;
    }
    UUID uuid = UUID.fromString("6a102cb4-d735-4cb7-8ab2-3d383219a44e");
    AttributeModifier.Operation operation = AttributeModifier.Operation.MULTIPLY_BASE;
    AttributeModifier modifier =
            new AttributeModifier(uuid, "AutoLeveling", attributeBonus, operation);
    ATTRIBUTE_BONUSES.put(attribute, modifier);
  }
}
