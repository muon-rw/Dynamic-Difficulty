package dev.muon.dynamic_difficulty.config;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import me.fzzyhmstrs.fzzy_config.annotations.Comment;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.resources.Identifier;

import java.util.Collections;

/**
 * Client-only configuration. Loaded on the client and never synced from the server.
 *
 * <p>Holds every Dynamic Difficulty rendering/display preference: HUD toggles,
 * nameplate injection, title popups, and associated timing/color knobs.
 *
 * <p>File: <code>config/dynamic_difficulty/dynamic_difficulty-client.toml</code>
 *
 * @see ConfigSync for server-authoritative, synced gameplay settings
 */
public class ConfigClient extends Config {

    public ConfigClient() {
        super(Identifier.fromNamespaceAndPath(DynamicDifficulty.MODID, "client"));
    }

    // --- Enum types (moved from old Config.java) ---

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

    // --- Level Plate Settings ---

    @Comment("--- Level Plate Settings ---\n" +
            "Determines when entity levels are rendered: ALWAYS, NEVER, or LOOKING_AT (only when the player is looking directly at/near the entity).")
    public ValidatedEnum<RenderBehavior> renderBehavior = new ValidatedEnum<>(RenderBehavior.LOOKING_AT);

    @Comment("Maximum distance (in blocks) at which entity level nameplates are rendered.")
    public ValidatedDouble renderDistance = new ValidatedDouble(64.0D);

    @Comment("Show Apotheosis world tier in entity level display (if Apotheosis is installed)\n" +
            "This will scan entity attributes for Apotheosis tier modifiers\n" +
            "Tiers: Haven, Frontier, Ascent, Summit, Pinnacle")
    public ValidatedBoolean showApotheosisWorldTier = new ValidatedBoolean(true);

    @Comment("Show Dungeon Difficulty info in entity level display (if Dungeon Difficulty is installed)\n" +
            "Displays the difficulty type and level in brackets after the entity level\n" +
            "Example: 'Lv. 15 [Normal 5]'")
    public ValidatedBoolean showDungeonDifficultyInfo = new ValidatedBoolean(true);

    @Comment("Enable line of sight checks for entity level rendering\n" +
            "When enabled, levels are only shown for entities the player can see (requires raycast)\n" +
            "When disabled, levels are shown based on distance and render behavior only (better performance)")
    public ValidatedBoolean enableLineOfSightCheck = new ValidatedBoolean(true);

    @Comment("Whether to inject level information into mob (non-player) nameplates\n" +
            "When false, mob nameplates show only the vanilla name without level info")
    public ValidatedBoolean injectLevelIntoMobs = new ValidatedBoolean(true);

    @Comment("Whether to inject level information into player nameplates\n" +
            "When false, player nameplates show only the vanilla name without level info")
    public ValidatedBoolean injectLevelIntoPlayers = new ValidatedBoolean(true);

    @Comment("Whether to override the default nameplate visibility for mobs (non-players)\n" +
            "When false, vanilla decides when mob nameplates are shown (sneaking, spectator, etc.)\n" +
            "Level injection (if enabled) still applies when vanilla shows the nameplate")
    public ValidatedBoolean overrideMobNameplateVisibility = new ValidatedBoolean(true);

    @Comment("Whether to override the default nameplate visibility for players\n" +
            "When false, vanilla decides when player nameplates are shown (sneaking, spectator, etc.)\n" +
            "Level injection (if enabled) still applies when vanilla shows the nameplate")
    public ValidatedBoolean overridePlayerNameplateVisibility = new ValidatedBoolean(true);

    // --- Integration Options ---

    @Comment("--- Integration Options ---\n" +
            "Show entity levels in Jade tooltips (requires Jade to be installed)")
    public ValidatedBoolean enableJadeIntegration = new ValidatedBoolean(true);

    // --- Entity Settings ---

    @Comment("--- Entity Settings ---\n" +
            "Entities (by ID or 'modid:*') whose levels should NOT be rendered on nameplates.")
    public ValidatedList<String> hiddenLevelEntities = ValidatedList.ofString(Collections.emptyList());

    // --- Structure Title Display ---

    @Comment("--- Structure Title Display ---\n" +
            "Display structure names and level bonuses when entering structures\n" +
            "Defaults to false if Structure Credits mod is loaded")
    public ValidatedBoolean showStructureTitles = new ValidatedBoolean(!DynamicDifficulty.isModLoaded("structurecredits"));

    @Comment("Only display structure titles if the structure provides a level bonus\n" +
            "When false, structure titles always display when entering structures\n" +
            "When true (default), structure titles only display if structureBonus > 0")
    public ValidatedBoolean structureTitleOnlyAnnounceIfModified = new ValidatedBoolean(true);

    @Comment("Time in ticks for structure title to fade in")
    public ValidatedInt structureTitleFadeInTime = new ValidatedInt(10, 100, 0);

    @Comment("Time in ticks to display structure title")
    public ValidatedInt structureTitleDisplayTime = new ValidatedInt(60, 600, 0);

    @Comment("Time in ticks for structure title to fade out")
    public ValidatedInt structureTitleFadeOutTime = new ValidatedInt(20, 100, 0);

    @Comment("Text color in hex format (e.g., \"FFFFFF\" for white)")
    public ValidatedString structureTitleTextColor = new ValidatedString("FFFFFF");

    @Comment("Render text shadow for structure titles")
    public ValidatedBoolean structureTitleRenderShadow = new ValidatedBoolean(true);

    @Comment("Text size multiplier for structure titles")
    public ValidatedDouble structureTitleTextSize = new ValidatedDouble(2.0D, 5.0D, 0.5D);

    @Comment("Anchor point for structure title positioning")
    public ValidatedEnum<AnchorPoint> structureTitleAnchor = new ValidatedEnum<>(AnchorPoint.BOTTOM_CENTER);

    @Comment("X offset from anchor point for structure title position")
    public ValidatedInt structureTitleXOffset = new ValidatedInt(0);

    @Comment("Y offset from anchor point for structure title position")
    public ValidatedInt structureTitleYOffset = new ValidatedInt(-82);

    // --- Biome Title Display ---

    @Comment("--- Biome Title Display ---\n" +
            "Display biome names when entering biomes\n" +
            "Defaults to false if Traveler's Titles mod is loaded")
    public ValidatedBoolean showBiomeTitles = new ValidatedBoolean(!DynamicDifficulty.isModLoaded("travelerstitles"));

    @Comment("Only display biome titles if the biome provides a level bonus\n" +
            "When false (default), biome titles always display when entering biomes\n" +
            "When true, biome titles only display if biomeBonus > 0")
    public ValidatedBoolean biomeTitleOnlyAnnounceIfModified = new ValidatedBoolean(false);

    @Comment("Time in ticks for biome title to fade in")
    public ValidatedInt biomeTitleFadeInTime = new ValidatedInt(10, 100, 0);

    @Comment("Time in ticks to display biome title")
    public ValidatedInt biomeTitleDisplayTime = new ValidatedInt(60, 600, 0);

    @Comment("Time in ticks for biome title to fade out")
    public ValidatedInt biomeTitleFadeOutTime = new ValidatedInt(20, 100, 0);

    @Comment("Text color in hex format (e.g., \"FFFFFF\" for white)")
    public ValidatedString biomeTitleTextColor = new ValidatedString("FFFFFF");

    @Comment("Render text shadow for biome titles")
    public ValidatedBoolean biomeTitleRenderShadow = new ValidatedBoolean(true);

    @Comment("Text size multiplier for biome titles")
    public ValidatedDouble biomeTitleTextSize = new ValidatedDouble(1.4D, 5.0D, 0.5D);

    @Comment("Anchor point for biome title positioning")
    public ValidatedEnum<AnchorPoint> biomeTitleAnchor = new ValidatedEnum<>(AnchorPoint.TOP_CENTER);

    @Comment("X offset from anchor point for biome title position")
    public ValidatedInt biomeTitleXOffset = new ValidatedInt(0);

    @Comment("Y offset from anchor point for biome title position")
    public ValidatedInt biomeTitleYOffset = new ValidatedInt(-60);

    @Comment("Cooldown time in ticks before biome title can be shown again")
    public ValidatedInt biomeTitleCooldownTime = new ValidatedInt(20, 200, 0);

    @Comment("Number of recent biomes to cache (prevents spam)")
    public ValidatedInt biomeRecentCacheSize = new ValidatedInt(5, 20, 0);

    // --- Dimension Title Display ---

    @Comment("--- Dimension Title Display ---\n" +
            "Display dimension names when entering dimensions\n" +
            "Defaults to false if Traveler's Titles mod is loaded")
    public ValidatedBoolean showDimensionTitles = new ValidatedBoolean(!DynamicDifficulty.isModLoaded("travelerstitles"));

    @Comment("Only display dimension titles if the dimension modifies leveling\n" +
            "When false (default), dimension titles always display when entering dimensions\n" +
            "When true, dimension titles only display if the dimension has leveling settings")
    public ValidatedBoolean dimensionTitleOnlyAnnounceIfModified = new ValidatedBoolean(false);

    @Comment("Time in ticks for dimension title to fade in")
    public ValidatedInt dimensionTitleFadeInTime = new ValidatedInt(10, 100, 0);

    @Comment("Time in ticks to display dimension title")
    public ValidatedInt dimensionTitleDisplayTime = new ValidatedInt(60, 600, 0);

    @Comment("Time in ticks for dimension title to fade out")
    public ValidatedInt dimensionTitleFadeOutTime = new ValidatedInt(20, 100, 0);

    @Comment("Text color in hex format (e.g., \"FFFFFF\" for white)")
    public ValidatedString dimensionTitleTextColor = new ValidatedString("FFFFFF");

    @Comment("Render text shadow for dimension titles")
    public ValidatedBoolean dimensionTitleRenderShadow = new ValidatedBoolean(true);

    @Comment("Text size multiplier for dimension titles")
    public ValidatedDouble dimensionTitleTextSize = new ValidatedDouble(2.0D, 5.0D, 0.5D);

    @Comment("Anchor point for dimension title positioning")
    public ValidatedEnum<AnchorPoint> dimensionTitleAnchor = new ValidatedEnum<>(AnchorPoint.TOP_CENTER);

    @Comment("X offset from anchor point for dimension title position")
    public ValidatedInt dimensionTitleXOffset = new ValidatedInt(0);

    @Comment("Y offset from anchor point for dimension title position")
    public ValidatedInt dimensionTitleYOffset = new ValidatedInt(-35);

    // --- Level Info Display ---

    @Comment("--- Level Info Display ---\n" +
            "Time in ticks for level info to fade in")
    public ValidatedInt levelInfoFadeInTime = new ValidatedInt(10, 100, 0);

    @Comment("Time in ticks to display level info")
    public ValidatedInt levelInfoDisplayTime = new ValidatedInt(60, 600, 0);

    @Comment("Time in ticks for level info to fade out")
    public ValidatedInt levelInfoFadeOutTime = new ValidatedInt(20, 100, 0);

    @Comment("Text color in hex format (e.g., \"FFFFFF\" for white)")
    public ValidatedString levelInfoTextColor = new ValidatedString("FFFFFF");

    @Comment("Render text shadow for level info")
    public ValidatedBoolean levelInfoRenderShadow = new ValidatedBoolean(true);

    @Comment("Text size multiplier for level info")
    public ValidatedDouble levelInfoTextSize = new ValidatedDouble(1.4D, 5.0D, 0.5D);

    @Comment("Anchor point for level info positioning")
    public ValidatedEnum<AnchorPoint> levelInfoAnchor = new ValidatedEnum<>(AnchorPoint.BOTTOM_CENTER);

    @Comment("X offset from anchor point for level info position")
    public ValidatedInt levelInfoXOffset = new ValidatedInt(0);

    @Comment("Y offset from anchor point for level info position")
    public ValidatedInt levelInfoYOffset = new ValidatedInt(-62);
}
