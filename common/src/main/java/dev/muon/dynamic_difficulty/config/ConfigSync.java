package dev.muon.dynamic_difficulty.config;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.player.PlayerLevelDisplayStrategy;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.event.api.ServerUpdateContext;
import me.fzzyhmstrs.fzzy_config.util.Walkable;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.minecraft.ValidatedIdentifier;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedAny;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server-authoritative gameplay configuration, synced from server to connected clients.
 *
 * <p>Holds every Dynamic Difficulty gameplay value that both sides must agree on:
 * leveling parameters, scaling curves, blacklists, attribute bonuses, item level caps.
 *
 * <p>File: <code>config/dynamic_difficulty/dynamic_difficulty-sync.toml</code>
 *
 * @see ConfigClient for client-only rendering settings
 */
public class ConfigSync extends Config {

    public ConfigSync() {
        super(Identifier.fromNamespaceAndPath(DynamicDifficulty.MODID, "sync"));
    }

    @Override
    public void onSyncServer() {
        rebuildCaches();
    }

    @Override
    public void onUpdateServer(@NotNull ServerUpdateContext context) {
        super.onUpdateServer(context);
        rebuildCaches();
    }

    private static void rebuildCaches() {
        reloadAttributeBonuses();
        LevelingUtils.reloadConfigCache();
    }

    // --- Built-in Datapack ---

    @Comment("--- Built-in Datapack ---\n" +
            "Whether to load the built-in default leveling settings datapack\n" +
            "This includes dimension, entity, biome, and structure level bonuses for vanilla and various mods\n" +
            "Disable this if you want to start with a clean slate and define all settings yourself\n" +
            "Requires a game restart to take effect")
    public ValidatedBoolean useDefaultLevelingSettings = new ValidatedBoolean(true);

    // --- Base Leveling ---

    @Comment("--- Base Leveling ---\n" +
            "Base level for all entities")
    public ValidatedInt startingLevel = new ValidatedInt(1);

    @Comment("Maximum level cap (0 for unlimited)")
    public ValidatedInt maxLevel = new ValidatedInt(0);

    @Comment("Random bonus levels added to entities (0-value)")
    public ValidatedInt randomLevelBonus = new ValidatedInt(0);

    @Comment("Additional experience multiplier per level")
    public ValidatedDouble expBonus = new ValidatedDouble(0.1D);

    // --- Environmental Scaling ---

    @Comment("--- Environmental Leveling ---\n" +
            "How many levels to add per block from world spawn")
    public ValidatedDouble levelsPerDistance = new ValidatedDouble(0.01D);

    @Comment("How many levels to add per block below sea level (default sea level is Y=64, can be overridden per dimension)")
    public ValidatedDouble levelsPerDeepness = new ValidatedDouble(0.0D);

    @Comment("How many levels to add per block above sea level (default sea level is Y=64, can be overridden per dimension)")
    public ValidatedDouble levelsPerHeight = new ValidatedDouble(0.0D);

    @Comment("How many levels to add per in-game day passed")
    public ValidatedDouble levelsPerDay = new ValidatedDouble(0.0D);

    @Comment("How many levels to add per point of local difficulty at mob spawn location\n" +
            "Local difficulty considers regional difficulty, chunk inhabited time, moon phase, and world difficulty")
    public ValidatedDouble levelsPerLocalDifficulty = new ValidatedDouble(0.0D);

    @Comment("Exponential level scaling with distance from spawn")
    public ValidatedDouble levelPowerPerDistance = new ValidatedDouble(0.0D);

    @Comment("Exponential level scaling with depth")
    public ValidatedDouble levelPowerPerDeepness = new ValidatedDouble(0.0D);

    // --- Player-Based Scaling ---

    @Comment("--- Player-Based Bonus Scaling ---\n" +
            "Radius to search for players when calculating level bonuses")
    public ValidatedDouble playerLevelRadius = new ValidatedDouble(128.0D);

    @Comment("Multiplier for player level bonuses applied to mobs\n" +
            "This scales the bonus that nearby players add to mob levels\n" +
            "1.0 = 1 level per point (typically), 0.5 = half effect, 2.0 = double effect")
    public ValidatedDouble playerLevelMultiplier = new ValidatedDouble(0.3D, 10.0D, 0.0D);

    @Comment("Whether to factor in player levels when calculating mob levels")
    public ValidatedBoolean applyPlayerBasedLeveling = new ValidatedBoolean(true);

    @Comment("Whether player level bonuses bypass the maximum level cap\n" +
            "When true (default), player bonuses are applied after the cap, allowing mobs to exceed max level\n" +
            "When false, player bonuses are applied before the cap and can be limited by max level\n" +
            "Similar to how structure/biome bonuses can have bypasses_cap set in their data files")
    public ValidatedBoolean playerLevelBypassesCap = new ValidatedBoolean(true);

    @Comment("How to aggregate multiple player level providers for display purposes\n" +
            "This only affects what level is shown above the player's head.\n" +
            "Mob scaling uses each provider's calculateBonusLevels() method instead.\n" +
            "HIGHEST_PRIORITY: Use the provider with the highest priority (defined by the provider itself)\n" +
            "MAX: Use the maximum level from all providers\n" +
            "SUM: Add all provider levels together\n" +
            "AVERAGE: Average all provider levels\n" +
            "FIRST: Use only the first registered provider")
    public ValidatedEnum<PlayerLevelDisplayStrategy> playerLevelDisplayStrategy =
            new ValidatedEnum<>(PlayerLevelDisplayStrategy.HIGHEST_PRIORITY);

    @Comment("How often (in ticks) to update player levels as a fallback (20 ticks = 1 second)\n" +
            "Providers can trigger immediate updates via events, this is just a safety net\n" +
            "Set to 0 to disable periodic updates (only event-driven updates will occur)")
    public ValidatedInt playerLevelUpdateInterval = new ValidatedInt(600, 1200, 0);

    // --- Playtime-Based Scaling ---

    @Comment("--- Playtime-Based Scaling ---\n" +
            "Enable playtime-based level scaling\n" +
            "When enabled, uses Minecraft's built-in playtime statistic (Stats.PLAY_TIME)\n" +
            "Playtime is automatically tracked by Minecraft and persists across sessions")
    public ValidatedBoolean enablePlaytimeScaling = new ValidatedBoolean(false);

    @Comment("How many levels to add per hour of nearby-player playtime\n" +
            "Playtime from all nearby players is averaged and converted to levels\n" +
            "Example: 2 players with 10 and 20 hours = 15 hours average = 15 * this_value levels")
    public ValidatedDouble levelsPerPlaytimeHour = new ValidatedDouble(0.1D);

    // --- Puffish Skills Integration ---

    @Comment("--- Puffish Skills Integration ---\n" +
            "List of Puffish Skills tree IDs that should be excluded from player level calculations\n" +
            "Trees in this list will not contribute their points to player-based mob level scaling\n" +
            "Example: [\"puffish_skills:mining\", \"puffish_skills:combat\"]\n" +
            "Leave empty to include all trees in mob scaling")
    public ValidatedList<String> puffishSkillsTreeBlacklist = ValidatedList.ofString(
            List.of("puffish_skills:mining"));

    // --- Entity Filtering ---

    @Comment("--- Entity Filtering ---\n" +
            "Whether passive mobs (animals) should be prevented from leveling")
    public ValidatedBoolean cancelLevelsForPassives = new ValidatedBoolean(true);

    @Comment("Entities that cannot level up\n" +
            "Supports wildcards: use 'modid:*' to blacklist all entities from a mod\n" +
            "Examples: [\"minecraft:zombie\", \"minecraft:skeleton\", \"cataclysm:*\"]")
    public ValidatedList<String> blacklistedMobs = ValidatedList.ofString(Collections.emptyList());

    @Comment("If not empty, only these entities can level up\n" +
            "Supports wildcards: use 'modid:*' to whitelist all entities from a mod\n" +
            "Examples: [\"minecraft:zombie\", \"cataclysm:*\"]")
    public ValidatedList<String> whitelistedMobs = ValidatedList.ofString(Collections.emptyList());

    // --- Attribute Bonuses ---

    @Comment("--- Attribute Bonuses ---\n" +
            "Attribute bonuses applied per entity level. Each entry has a three-part editor:\n" +
            " - attribute: resource location of a registered attribute (e.g. minecraft:attack_damage)\n" +
            " - bonusPerLevel: amount added per entity level (floating-point)\n" +
            " - operation: add_value (flat), add_multiplied_base (% of base), add_multiplied_total (% of final)\n" +
            "Leave empty to disable all attribute bonuses.")
    public ValidatedList<AttributeBonus> attributesBonuses =
            new ValidatedAny<>(new AttributeBonus()).toList(getDefaultAttributeBonuses());

    // --- Level-Up Items ---

    @Comment("--- Level-Up Items ---\n" +
            "Maximum level that Potion of Growth can raise an entity to")
    public ValidatedInt potionOfGrowthMaxLevel = new ValidatedInt(20, 10000, 1);

    @Comment("Maximum level that Elixir of Nurturing can raise an entity to")
    public ValidatedInt elixirOfNurturingMaxLevel = new ValidatedInt(40, 10000, 1);

    @Comment("Maximum level that Draught of Ascension can raise an entity to")
    public ValidatedInt draughtOfAscensionMaxLevel = new ValidatedInt(60, 10000, 1);

    @Comment("Maximum level that Essence of Vitality can raise an entity to")
    public ValidatedInt essenceOfVitalityMaxLevel = new ValidatedInt(80, 10000, 1);

    @Comment("Maximum level that Crystal of Awakening can raise an entity to")
    public ValidatedInt crystalOfAwakeningMaxLevel = new ValidatedInt(100, 10000, 1);

    // --- Level-Based Drops ---

    @Comment("--- Level-Based Drops ---\n" +
            "Whether mobs should drop level-up items based on their level\n" +
            "The drops are defined in data/dynamic_difficulty/loot_tables/inject/level_based_drops.json\n" +
            "Users can edit that file to customize drop rates, level ranges, and add custom items")
    public ValidatedBoolean enableLevelBasedDrops = new ValidatedBoolean(true);


    // === Attribute Bonus Static Helpers ===
    // Preserve the old Config.java helper API so leveling code keeps working.

    private static final Map<ResourceKey<Attribute>, AttributeModifier> ATTRIBUTE_BONUSES = new HashMap<>();

    private static List<AttributeBonus> getDefaultAttributeBonuses() {
        return Arrays.asList(
                new AttributeBonus("minecraft", "attack_damage", 0.25D, AttributeModifier.Operation.ADD_VALUE),
                new AttributeBonus("minecraft", "armor", 0.2D, AttributeModifier.Operation.ADD_VALUE),
                new AttributeBonus("minecraft", "max_health", 0.05D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                new AttributeBonus("dynamic_difficulty", "projectile_damage_bonus", 0.25D, AttributeModifier.Operation.ADD_VALUE),
                new AttributeBonus("dynamic_difficulty", "magic_damage_bonus", 0.25D, AttributeModifier.Operation.ADD_VALUE),
                new AttributeBonus("dynamic_difficulty", "explosion_damage_bonus", 0.25D, AttributeModifier.Operation.ADD_VALUE)
        );
    }

    /**
     * Force a rebuild of the cached {@link AttributeModifier} map from the current config state.
     * Called from config-reload hooks.
     */
    public static void reloadAttributeBonuses() {
        synchronized (ATTRIBUTE_BONUSES) {
            ATTRIBUTE_BONUSES.clear();
            List<? extends AttributeBonus> entries = Configs.SYNC != null
                    ? Configs.SYNC.attributesBonuses.get()
                    : Collections.emptyList();
            for (AttributeBonus entry : entries) {
                buildAttributeBonus(entry);
            }
            DynamicDifficulty.LOGGER.info("Reloaded {} attribute bonuses from config", ATTRIBUTE_BONUSES.size());
        }
    }

    /**
     * Lazily build and return the cached attribute bonus map.
     */
    public static Map<ResourceKey<Attribute>, AttributeModifier> getAttributeBonuses() {
        if (ATTRIBUTE_BONUSES.isEmpty()) {
            synchronized (ATTRIBUTE_BONUSES) {
                if (ATTRIBUTE_BONUSES.isEmpty() && Configs.SYNC != null) {
                    for (AttributeBonus entry : Configs.SYNC.attributesBonuses.get()) {
                        buildAttributeBonus(entry);
                    }
                    DynamicDifficulty.LOGGER.info("Initialized {} attribute bonuses from config", ATTRIBUTE_BONUSES.size());
                }
            }
        }
        return ATTRIBUTE_BONUSES;
    }

    private static void buildAttributeBonus(AttributeBonus bonus) {
        Identifier attributeRL = bonus.attribute.get();
        if (attributeRL == null) {
            DynamicDifficulty.LOGGER.error("Attribute bonus has no attribute ID set; skipping.");
            return;
        }

        Attribute attribute = BuiltInRegistries.ATTRIBUTE.getValue(attributeRL);
        if (attribute == null) {
            DynamicDifficulty.LOGGER.error("Attribute '{}' is not registered; skipping bonus.", attributeRL);
            return;
        }

        Optional<ResourceKey<Attribute>> optAttributeKey = BuiltInRegistries.ATTRIBUTE.getResourceKey(attribute);
        if (optAttributeKey.isEmpty()) {
            DynamicDifficulty.LOGGER.error("Could not retrieve ResourceKey for attribute: {} ({})", attribute.getDescriptionId(), attributeRL);
            return;
        }
        ResourceKey<Attribute> attributeKey = optAttributeKey.get();

        String uniqueModifierName = "config_bonus_" + attributeRL.getNamespace().replace(":", "_") + "_" + attributeRL.getPath().replace("/", "_");
        Identifier modifierId = DynamicDifficulty.id(uniqueModifierName);

        float amount = (float) (double) bonus.bonusPerLevel.get();
        AttributeModifier.Operation operation = bonus.operation.get();

        AttributeModifier modifier = new AttributeModifier(modifierId, amount, operation);
        ATTRIBUTE_BONUSES.put(attributeKey, modifier);
        DynamicDifficulty.LOGGER.info("Config: Registered attribute bonus for ResourceKey {} ({}) with amount {}/level, operation {}, ModID {}",
                attributeKey.identifier(), attribute.getDescriptionId(), amount, operation, modifierId);
    }

    /**
     * A single attribute-bonus entry. Rendered by FzzyConfig as a popup editor with three fields:
     * an identifier picker, a numeric spinner, and an enum dropdown.
     *
     * <p>Implements {@link Walkable} so FzzyConfig reflects on the public {@code Validated*} fields
     * to auto-generate the editor UI. The no-arg constructor is what FzzyConfig calls when a new
     * list entry is created; the convenience constructor is used to build the default list above.
     */
    public static class AttributeBonus implements Walkable {

        @Comment("Attribute resource location (e.g. minecraft:attack_damage)")
        public ValidatedIdentifier attribute = new ValidatedIdentifier(
                Identifier.fromNamespaceAndPath("minecraft", "attack_damage"));

        @Comment("Amount added per entity level")
        public ValidatedDouble bonusPerLevel = new ValidatedDouble(0.0D);

        @Comment("add_value (flat), add_multiplied_base (% of base), add_multiplied_total (% of final)")
        public ValidatedEnum<AttributeModifier.Operation> operation =
                new ValidatedEnum<>(AttributeModifier.Operation.ADD_VALUE);

        public AttributeBonus() {}

        public AttributeBonus(String namespace, String path, double bonus, AttributeModifier.Operation op) {
            this.attribute = new ValidatedIdentifier(Identifier.fromNamespaceAndPath(namespace, path));
            this.bonusPerLevel = new ValidatedDouble(bonus);
            this.operation = new ValidatedEnum<>(op);
        }
    }
}
