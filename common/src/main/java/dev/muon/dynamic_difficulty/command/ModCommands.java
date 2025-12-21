package dev.muon.dynamic_difficulty.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Common command registration logic.
 * Platform-specific code should call {@link #register(CommandDispatcher)}.
 */
public class ModCommands {
  
  /**
   * Registers all mod commands. Called from platform-specific event handlers.
   */
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    // Level add command
    LiteralArgumentBuilder<CommandSourceStack> addLevelCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("level")
                    .then(
                        Commands.literal("add")
                            .then(
                                Commands.argument("targets", EntityArgument.entities())
                                    .then(
                                        Commands.argument("value", IntegerArgumentType.integer())
                                            .executes(ModCommands::executeAddLevelCommand)))))
            .requires(ModCommands::hasPermission);
    dispatcher.register(addLevelCommand);
    
    // Level set command
    LiteralArgumentBuilder<CommandSourceStack> setLevelCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("level")
                    .then(
                        Commands.literal("set")
                            .then(
                                Commands.argument("targets", EntityArgument.entities())
                                    .then(
                                        Commands.argument("value", IntegerArgumentType.integer(1))
                                            .executes(ModCommands::executeSetLevelCommand)))))
            .requires(ModCommands::hasPermission);
    dispatcher.register(setLevelCommand);
    
    // Level get command
    LiteralArgumentBuilder<CommandSourceStack> getLevelCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("level")
                    .then(
                        Commands.literal("get")
                            .then(
                                Commands.argument("targets", EntityArgument.entities())
                                    .executes(ModCommands::executeGetLevelCommand))))
            .requires(ModCommands::hasPermission);
    dispatcher.register(getLevelCommand);
    
    // Dump structures command
    LiteralArgumentBuilder<CommandSourceStack> dumpStructuresCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("dumpStructures")
                    .executes(ModCommands::executeDumpStructuresCommand))
            .requires(ModCommands::hasPermission);
    dispatcher.register(dumpStructuresCommand);
    
    // Debug location command
    LiteralArgumentBuilder<CommandSourceStack> debugLocationCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("debug")
                    .then(
                        Commands.literal("location")
                            .executes(ModCommands::executeDebugLocationCommand)))
            .requires(ModCommands::hasPermission);
    dispatcher.register(debugLocationCommand);
  }

  private static int executeAddLevelCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    CommandSourceStack source = ctx.getSource();
    int value = IntegerArgumentType.getInteger(ctx, "value");
    Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
    
    int successCount = 0;
    for (Entity entity : targets) {
      if (entity instanceof LivingEntity livingEntity) {
        if (!LevelingAPI.canHaveLevel(livingEntity)) {
          source.sendFailure(Component.literal("§c" + entity.getName().getString() + " cannot have levels"));
          continue;
        }
        
        int oldLevel = LevelingAPI.getLevel(livingEntity);
        try {
          LevelingAPI.addLevels(livingEntity, value);
          int newLevel = LevelingAPI.getLevel(livingEntity);
          source.sendSystemMessage(Component.literal("§aAdded " + value + " level(s) to " + entity.getName().getString() + " §7(Lv. " + oldLevel + " → " + newLevel + ")"));
          successCount++;
        } catch (IllegalArgumentException e) {
          source.sendFailure(Component.literal("§cFailed to add levels to " + entity.getName().getString() + ": " + e.getMessage()));
        }
      } else {
        source.sendFailure(Component.literal("§c" + entity.getName().getString() + " is not a living entity"));
      }
    }
    
    return successCount;
  }

  private static int executeSetLevelCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    CommandSourceStack source = ctx.getSource();
    Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
    int value = IntegerArgumentType.getInteger(ctx, "value");
    
    int successCount = 0;
    for (Entity entity : targets) {
      if (entity instanceof LivingEntity livingEntity) {
        if (!LevelingAPI.canHaveLevel(livingEntity)) {
          source.sendFailure(Component.literal("§c" + entity.getName().getString() + " cannot have levels"));
          continue;
        }
        
        try {
          LevelingAPI.setAndUpdateLevel(livingEntity, value);
          source.sendSystemMessage(Component.literal("§aSet " + entity.getName().getString() + " to level §f" + value));
          successCount++;
        } catch (IllegalArgumentException e) {
          source.sendFailure(Component.literal("§cFailed to set level for " + entity.getName().getString() + ": " + e.getMessage()));
        }
      } else {
        source.sendFailure(Component.literal("§c" + entity.getName().getString() + " is not a living entity"));
      }
    }
    
    return successCount;
  }

  private static int executeGetLevelCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    CommandSourceStack source = ctx.getSource();
    Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
    
    int successCount = 0;
    for (Entity entity : targets) {
      if (entity instanceof LivingEntity livingEntity) {
        if (!LevelingAPI.hasLevel(livingEntity)) {
          source.sendSystemMessage(Component.literal("§7" + entity.getName().getString() + " has no level assigned"));
        } else {
          int level = LevelingAPI.getLevel(livingEntity);
          source.sendSystemMessage(Component.literal("§a" + entity.getName().getString() + " §7is level §f" + level));
        }
        successCount++;
      } else {
        source.sendFailure(Component.literal("§c" + entity.getName().getString() + " is not a living entity"));
      }
    }
    
    return successCount;
  }

  private static boolean hasPermission(CommandSourceStack commandSourceStack) {
    return commandSourceStack.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
  }
  
  private static final List<String> TARGET_NAMESPACES = Arrays.asList(
      "minecraft",
      "dungeons_arise",
      "aether",
      "apotheosis",
      "twilightforest",
      "undergarden",
      "betterend",
      "betternether",
      "endrem",
      "repurposed_structures",
      "yungsapi",
      "valhelsia_structures",
      "integrated_structures",
      "explorerscompass",
      "waystones"
  );
  
  private static int executeDumpStructuresCommand(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    MinecraftServer server = source.getServer();
    
    source.sendSystemMessage(Component.literal("Starting structure ID dump... Check server logs."));
    DynamicDifficulty.LOGGER.info("Structure dump initiated by command from: " + source.getTextName());
    
    try {
      Registry<Structure> structureRegistry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
      
      // Get all structure IDs from target namespaces
      Set<Identifier> allStructureIdsInTargetNamespaces = structureRegistry.keySet().stream()
          .filter(id -> TARGET_NAMESPACES.contains(id.getNamespace()))
          .collect(Collectors.toSet());
      
      Set<Identifier> categorizedStructureIds = new HashSet<>();
      
      DynamicDifficulty.LOGGER.info("--- Note: Structure level bonuses are now configured via datapacks ---");
      DynamicDifficulty.LOGGER.info("--- Place structure settings in: data/<namespace>/leveling_settings/structures/<structure_id>.json ---");
      DynamicDifficulty.LOGGER.info("--- Place structure tag settings in: data/<namespace>/leveling_settings/structure_tags/<tag_id>.json ---");
      
      DynamicDifficulty.LOGGER.info("--- Dumping Structure IDs by Tags ---");
      
      // Dump structures by all available tags (users can configure bonuses via datapacks)
        for (TagKey<Structure> tagKey : structureRegistry.getTags()
                .map(HolderSet.Named::key)
                .sorted((a, b) -> a.location().compareTo(b.location()))
                .collect(Collectors.toList())) {

            List<Identifier> structuresInThisTag = new ArrayList<>();

            structureRegistry.getTagOrEmpty(tagKey).forEach(holder -> {
                Identifier id = holder.unwrapKey().get().identifier();
                if (allStructureIdsInTargetNamespaces.contains(id)) {
                    structuresInThisTag.add(id);
                }
            });

            if (!structuresInThisTag.isEmpty()) {
                DynamicDifficulty.LOGGER.info("--- Structures in Tag: " + tagKey.location() + " ---");
                structuresInThisTag.stream()
                        .sorted(Identifier::compareTo)
                        .forEach(id -> {
                            DynamicDifficulty.LOGGER.info(id.toString());
                            categorizedStructureIds.add(id);
                        });
            }
        }
      
      // Dump uncategorized structures
      DynamicDifficulty.LOGGER.info("--- Uncategorized Structures (from target namespaces) ---");
      List<Identifier> uncategorizedStructures = allStructureIdsInTargetNamespaces.stream()
          .filter(id -> !categorizedStructureIds.contains(id))
          .sorted(Identifier::compareTo)
          .collect(Collectors.toList());
      
      if (uncategorizedStructures.isEmpty()) {
        DynamicDifficulty.LOGGER.info("No uncategorized structures found in target namespaces.");
      } else {
        uncategorizedStructures.forEach(id -> DynamicDifficulty.LOGGER.info(id.toString()));
      }
      
      // Also dump all structure tags that exist
      DynamicDifficulty.LOGGER.info("--- All Available Structure Tags ---");
        structureRegistry.getTags()
                .map(HolderSet.Named::key)
                .sorted((a, b) -> a.location().compareTo(b.location()))
                .forEach(tagKey -> {
                    List<Identifier> structuresInTag = new ArrayList<>();
                    structureRegistry.getTagOrEmpty(tagKey).forEach(holder -> {
                        structuresInTag.add(holder.unwrapKey().get().identifier());
                    });
                    DynamicDifficulty.LOGGER.info(tagKey.location() + " (" + structuresInTag.size() + " structures)");
                });
      
      DynamicDifficulty.LOGGER.info("--- Structure ID Dump Complete ---");
      source.sendSystemMessage(Component.literal("Structure dump complete. Total structures in target namespaces: " + allStructureIdsInTargetNamespaces.size()));
      
    } catch (Exception e) {
      DynamicDifficulty.LOGGER.error("Error occurred during structure dump command:", e);
      source.sendFailure(Component.literal("Error during structure dump. See logs."));
    }
    
    return 1;
  }
  
  private static int executeDebugLocationCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    CommandSourceStack source = context.getSource();
    
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("This command can only be executed by a player"));
      return 0;
    }
    
    ServerLevel level = player.level();
    BlockPos pos = player.blockPosition();
    
    // Get dimension settings
    ResourceKey<Level> dimension = level.dimension();
    Identifier dimensionId = dimension.identifier();
    Registry<Level> dimensionRegistry = level.registryAccess().lookupOrThrow(Registries.DIMENSION);
    DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(dimension, dimensionRegistry);
    
    // Get spawn position (considering override)
    BlockPos spawnPos = dimSettings.spawnPosOverride() != null ?
        dimSettings.spawnPosOverride() : level.getRespawnData().pos();
    int spawnX = spawnPos.getX();
    int spawnZ = spawnPos.getZ();
    
    // Calculate distance
    double dx = spawnX - pos.getX();
    double dz = spawnZ - pos.getZ();
    double distance = Math.sqrt(dx * dx + dz * dz);
    int distanceBonus = (int)(distance * dimSettings.levelsPerDistance());
    
    // Calculate depth/height
    int seaLevel = dimSettings.seaLevel();
    int depthBonus = 0;
    int heightBonus = 0;
    int depthBlocks = 0;
    int heightBlocks = 0;
    if (pos.getY() < seaLevel) {
      depthBlocks = seaLevel - pos.getY();
      if (dimSettings.levelsPerDeepness() > 0) {
        depthBonus = (int) (depthBlocks * dimSettings.levelsPerDeepness());
      }
    }
    if (pos.getY() > seaLevel) {
      heightBlocks = pos.getY() - seaLevel;
      if (dimSettings.levelsPerHeight() > 0) {
        heightBonus = (int) (heightBlocks * dimSettings.levelsPerHeight());
      }
    }
    
    // Calculate day bonus
    long days = level.getDayTime() / 24000L;
    int dayBonus = (int)(days * dimSettings.levelsPerDay());
    
    // Calculate local difficulty bonus
    net.minecraft.world.DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
    float effectiveDifficulty = difficulty.getEffectiveDifficulty();
    int localDifficultyBonus = (int)(effectiveDifficulty * dimSettings.levelsPerLocalDifficulty());
    
    // Base level calculation
    int startingLevel = dimSettings.startingLevel();
    int baseLevel = startingLevel + distanceBonus + depthBonus + heightBonus + dayBonus + localDifficultyBonus;
    int maxLevel = dimSettings.maxLevel();
    String maxLevelStr = maxLevel > 0 ? String.valueOf(maxLevel) : "unlimited";
    
    // Get bonuses
    BiomeBonus biomeBonus = LevelingAPI.getBiomeBonus(level, pos);
    StructureBonus structureBonus = LevelingAPI.getStructureBonus(level, pos);
    
    // Get player multiplier override (dimension -> config)
    Double playerMultiplierOverride = dimSettings.playerLevelMultiplier();
    double playerMultiplier = playerMultiplierOverride != null ? playerMultiplierOverride : Config.COMMON.playerLevelMultiplier.get();
    String multiplierSource = playerMultiplierOverride != null ? "dimension override" : "config";
    
    // Get apply level bonuses override (dimension -> config)
    DimensionLevelingSettings.ApplyLevelBonuses applyBonusesOverride = dimSettings.applyLevelBonuses();
    boolean applyBiome = applyBonusesOverride == null || applyBonusesOverride.biome();
    boolean applyStructure = applyBonusesOverride == null || applyBonusesOverride.structure();
    boolean applyPlayer = applyBonusesOverride == null || applyBonusesOverride.player();
    String bonusesSource = applyBonusesOverride != null ? "dimension override" : "config";
    
    // Player bonus
    int playerBonus = 0;
    boolean playerBypassesCap = Config.COMMON.playerLevelBypassesCap.get();
    if (Config.COMMON.applyPlayerBasedLeveling.get() && applyPlayer) {
      int rawBonus = PlayerLevelProvider.getProviders().stream()
          .filter(PlayerLevelProvider::isEnabled)
          .mapToInt(provider -> provider.calculateBonusLevels(List.of(player)))
          .sum();
      playerBonus = (int) (rawBonus * playerMultiplier);
    }
    
    // Calculate totals - player bonus goes to capped or bypassing based on config
    // Apply apply_level_bonuses overrides
    int structureNonBypassing = applyStructure ? structureBonus.nonBypassingBonus() : 0;
    int structureBypassing = applyStructure ? structureBonus.bypassingBonus() : 0;
    int biomeNonBypassing = applyBiome ? biomeBonus.nonBypassingBonus() : 0;
    int biomeBypassing = applyBiome ? biomeBonus.bypassingBonus() : 0;
    
    int totalCapped = structureNonBypassing + biomeNonBypassing + (playerBypassesCap ? 0 : playerBonus);
    int totalBypassing = structureBypassing + biomeBypassing + (playerBypassesCap ? playerBonus : 0);
    int randomBonus = dimSettings.randomLevelBonus();
    
    // Final level calculation with ranges (random is applied before cap)
    int baseLow = baseLevel;
    int baseHigh = baseLevel + randomBonus;
    
    int preCappedLow = baseLow + totalCapped;
    int preCappedHigh = baseHigh + totalCapped;
    
    int cappedLow = maxLevel > 0 ? Math.min(preCappedLow, maxLevel) : preCappedLow;
    int cappedHigh = maxLevel > 0 ? Math.min(preCappedHigh, maxLevel) : preCappedHigh;
    
    int finalLow = cappedLow + totalBypassing;
    int finalHigh = cappedHigh + totalBypassing;
    
    // === Output ===
    source.sendSystemMessage(Component.literal("§6=== Dynamic Difficulty Debug ==="));
    source.sendSystemMessage(Component.literal("§7Position: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
    source.sendSystemMessage(Component.literal("§7Dimension: §f" + dimensionId +" §7(sea lvl: §f" + seaLevel + "§7, spawn: §f" + spawnX + ", " + spawnZ + "§7)"));
    source.sendSystemMessage(Component.literal("§7Scaling: §f" + dimSettings.levelsPerDistance() + "§7/dist, §f" + dimSettings.levelsPerDeepness() + "§7/depth, §f" + dimSettings.levelsPerHeight() + "§7/height, §f" + dimSettings.levelsPerDay() + "§7/day, §f" + dimSettings.levelsPerLocalDifficulty() + "§7/local, random: §f0-" + dimSettings.randomLevelBonus()));
    
    // Show overrides if present
    if (playerMultiplierOverride != null || applyBonusesOverride != null) {
      source.sendSystemMessage(Component.literal(""));
      source.sendSystemMessage(Component.literal("§7Overrides:"));
      if (playerMultiplierOverride != null) {
        source.sendSystemMessage(Component.literal("§7  Player Multiplier: §f" + playerMultiplier + " §7(" + multiplierSource + ")"));
      }
      if (applyBonusesOverride != null) {
        source.sendSystemMessage(Component.literal("§7  Apply Bonuses: §fbiome=" + applyBiome + ", structure=" + applyStructure + ", player=" + applyPlayer + " §7(" + bonusesSource + ")"));
      }
    }
    source.sendSystemMessage(Component.literal(""));
    
    // Location line
    String locationStr = (int)distance + " blocks from spawn";
    if (pos.getY() < seaLevel) {
      locationStr += ", " + depthBlocks + " below sea lvl";
    } else {
      locationStr += ", " + heightBlocks + " above sea lvl";
    }
    source.sendSystemMessage(Component.literal("§7Location: §f" + locationStr));
    
    // Base calculation
    source.sendSystemMessage(Component.literal("§7Base Calculation:"));
    source.sendSystemMessage(Component.literal("§7  Starting: §f" + startingLevel));
    source.sendSystemMessage(Component.literal("§7  + Distance: §f" + distanceBonus + " §7(" + (int)distance + " × " + dimSettings.levelsPerDistance() + ")"));
    if (pos.getY() < seaLevel) {
      source.sendSystemMessage(Component.literal("§7  + Depth: §f" + depthBonus + " §7(" + depthBlocks + " × " + dimSettings.levelsPerDeepness() + ")"));
    } else {
      source.sendSystemMessage(Component.literal("§7  + Height: §f" + heightBonus + " §7(" + heightBlocks + " × " + dimSettings.levelsPerHeight() + ")"));
    }
    source.sendSystemMessage(Component.literal("§7  + Days: §f" + dayBonus + " §7(" + days + " × " + dimSettings.levelsPerDay() + ")"));
    source.sendSystemMessage(Component.literal("§7  + Local: §f" + localDifficultyBonus + " §7(" + String.format("%.2f", effectiveDifficulty) + " × " + dimSettings.levelsPerLocalDifficulty() + ")"));
    String baseRangeStr = randomBonus > 0 ? baseLevel + " (+0-" + randomBonus + " random)" : String.valueOf(baseLevel);
    source.sendSystemMessage(Component.literal("§7  = Base Level: §f" + baseRangeStr + " §7(max: " + maxLevelStr + ")"));
    source.sendSystemMessage(Component.literal(""));
    
    // Biome line
    if (biomeBonus.biomeId() != null) {
      String biomeBonusStr = applyBiome ? formatBonus(biomeBonus.nonBypassingBonus(), biomeBonus.bypassingBonus()) : "§cdisabled";
      String biomeStatus = applyBiome ? "" : " §7(disabled by override)";
      source.sendSystemMessage(Component.literal("§7Biome: §f" + biomeBonus.biomeId() + " §7→ " + biomeBonusStr + biomeStatus));
    } else {
      source.sendSystemMessage(Component.literal("§7Biome: §cunknown"));
    }
    
    // Structure line
    if (structureBonus.hasStructure()) {
      String structureBonusStr = applyStructure ? formatBonus(structureBonus.nonBypassingBonus(), structureBonus.bypassingBonus()) : "§cdisabled";
      String structureStatus = applyStructure ? "" : " §7(disabled by override)";
      source.sendSystemMessage(Component.literal("§7Structure: §f" + structureBonus.structureId() + " §7→ " + structureBonusStr + structureStatus));
    } else {
      source.sendSystemMessage(Component.literal("§7Structure: §fnone"));
    }
    
    // Player line
    String playerCapBehavior = playerBypassesCap ? "bypasses cap" : "respects cap";
    String playerStatus = applyPlayer ? "" : " §7(disabled by override)";
    source.sendSystemMessage(Component.literal("§7Player: §f+" + playerBonus + " §7(" + playerCapBehavior + ")" + playerStatus));
    source.sendSystemMessage(Component.literal(""));
    
    // Final line with ranges
    String baseStr = formatRange(baseLow, baseHigh);
    String cappedStr = formatRange(cappedLow, cappedHigh);
    String finalStr = formatRange(finalLow, finalHigh);
    
    source.sendSystemMessage(Component.literal("§7Final: §f" + baseStr + " base + " + totalCapped + " §7→(Cap:" + maxLevelStr + ")§7→ §f" + cappedStr + " §7+ §f" + totalBypassing + " §7= §f§l" + finalStr));
    
    return 1;
  }
  
  private static String formatBonus(int nonBypassing, int bypassing) {
    if (nonBypassing == 0 && bypassing == 0) {
      return "§fno bonus";
    } else if (nonBypassing > 0 && bypassing > 0) {
      return "§f+" + nonBypassing + " §7(respects cap), §f+" + bypassing + " §7(bypasses cap)";
    } else if (bypassing > 0) {
      return "§f+" + bypassing + " §7(bypasses cap)";
    } else {
      return "§f+" + nonBypassing + " §7(respects cap)";
    }
  }
  
  private static String formatRange(int low, int high) {
    return low == high ? String.valueOf(low) : low + "-" + high;
  }
}
