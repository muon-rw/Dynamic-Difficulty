package dev.muon.dynamic_difficulty.command;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.*;
import java.util.stream.Collectors;


@EventBusSubscriber(modid = DynamicDifficulty.MODID)
public class ModCommands {
  @SubscribeEvent
  public static void onRegisterCommands(RegisterCommandsEvent event) {
    // Level add command
    LiteralArgumentBuilder<CommandSourceStack> addLevelCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("level")
                    .then(
                        Commands.literal("add")
                            .then(
                                Commands.argument("value", IntegerArgumentType.integer())
                                    .executes(ModCommands::executeAddLevelCommand))))
            .requires(ModCommands::hasPermission);
    event.getDispatcher().register(addLevelCommand);
    
    // Level get command
    LiteralArgumentBuilder<CommandSourceStack> getLevelCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("level")
                    .then(
                        Commands.literal("get")
                            .executes(ModCommands::executeGetLevelCommand)))
            .requires(ModCommands::hasPermission);
    event.getDispatcher().register(getLevelCommand);
    
    // Dump structures command
    LiteralArgumentBuilder<CommandSourceStack> dumpStructuresCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("dumpStructures")
                    .executes(ModCommands::executeDumpStructuresCommand))
            .requires(ModCommands::hasPermission);
    event.getDispatcher().register(dumpStructuresCommand);
    
    // Debug location command
    LiteralArgumentBuilder<CommandSourceStack> debugLocationCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("debug")
                    .then(
                        Commands.literal("location")
                            .executes(ModCommands::executeDebugLocationCommand)))
            .requires(ModCommands::hasPermission);
    event.getDispatcher().register(debugLocationCommand);
  }

  private static int executeAddLevelCommand(CommandContext<CommandSourceStack> ctx) {
    // todo: this should target entities
    return 1;
  }

  private static int executeGetLevelCommand(CommandContext<CommandSourceStack> ctx) {
    // todo: implement level getting
    return 1;
  }

  private static boolean hasPermission(CommandSourceStack commandSourceStack) {
    return commandSourceStack.hasPermission(2);
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
      Registry<Structure> structureRegistry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
      
      // Get all structure IDs from target namespaces
      Set<ResourceLocation> allStructureIdsInTargetNamespaces = structureRegistry.keySet().stream()
          .filter(id -> TARGET_NAMESPACES.contains(id.getNamespace()))
          .collect(Collectors.toSet());
      
      Set<ResourceLocation> categorizedStructureIds = new HashSet<>();
      
      DynamicDifficulty.LOGGER.info("--- Note: Structure level bonuses are now configured via datapacks ---");
      DynamicDifficulty.LOGGER.info("--- Place structure settings in: data/<namespace>/leveling_settings/structures/<structure_id>.json ---");
      DynamicDifficulty.LOGGER.info("--- Place structure tag settings in: data/<namespace>/leveling_settings/structure_tags/<tag_id>.json ---");
      
      DynamicDifficulty.LOGGER.info("--- Dumping Structure IDs by Tags ---");
      
      // Dump structures by all available tags (users can configure bonuses via datapacks)
      for (TagKey<Structure> tagKey : structureRegistry.getTagNames()
          .sorted((a, b) -> a.location().compareTo(b.location()))
          .collect(Collectors.toList())) {
        
        List<ResourceLocation> structuresInThisTag = new ArrayList<>();
        
        structureRegistry.getTagOrEmpty(tagKey).forEach(holder -> {
          ResourceLocation id = holder.unwrapKey().get().location();
          if (allStructureIdsInTargetNamespaces.contains(id)) {
            structuresInThisTag.add(id);
          }
        });
        
        if (!structuresInThisTag.isEmpty()) {
          DynamicDifficulty.LOGGER.info("--- Structures in Tag: " + tagKey.location() + " ---");
          structuresInThisTag.stream()
              .sorted(ResourceLocation::compareTo)
              .forEach(id -> {
                DynamicDifficulty.LOGGER.info(id.toString());
                categorizedStructureIds.add(id);
              });
        }
      }
      
      // Dump uncategorized structures
      DynamicDifficulty.LOGGER.info("--- Uncategorized Structures (from target namespaces) ---");
      List<ResourceLocation> uncategorizedStructures = allStructureIdsInTargetNamespaces.stream()
          .filter(id -> !categorizedStructureIds.contains(id))
          .sorted(ResourceLocation::compareTo)
          .collect(Collectors.toList());
      
      if (uncategorizedStructures.isEmpty()) {
        DynamicDifficulty.LOGGER.info("No uncategorized structures found in target namespaces.");
      } else {
        uncategorizedStructures.forEach(id -> DynamicDifficulty.LOGGER.info(id.toString()));
      }
      
      // Also dump all structure tags that exist
      DynamicDifficulty.LOGGER.info("--- All Available Structure Tags ---");
      structureRegistry.getTagNames()
          .sorted((a, b) -> a.location().compareTo(b.location()))
          .forEach(tagKey -> {
            List<ResourceLocation> structuresInTag = new ArrayList<>();
            structureRegistry.getTagOrEmpty(tagKey).forEach(holder -> {
              structuresInTag.add(holder.unwrapKey().get().location());
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
    
    ServerLevel level = player.serverLevel();
    BlockPos pos = player.blockPosition();
    
    // Get dimension settings
    ResourceKey<Level> dimension = level.dimension();
    ResourceLocation dimensionId = dimension.location();
    Registry<Level> dimensionRegistry = level.registryAccess().registryOrThrow(Registries.DIMENSION);
    DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(dimension, dimensionRegistry);
    
    // Get spawn position (considering override)
    BlockPos spawnPos = dimSettings.spawnPosOverride() != null ? 
        dimSettings.spawnPosOverride() : level.getSharedSpawnPos();
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
    if (pos.getY() < seaLevel && dimSettings.levelsPerDeepness() > 0) {
      depthBlocks = seaLevel - pos.getY();
      depthBonus = (int)(depthBlocks * dimSettings.levelsPerDeepness());
    }
    if (pos.getY() > seaLevel && dimSettings.levelsPerHeight() > 0) {
      heightBlocks = pos.getY() - seaLevel;
      heightBonus = (int)(heightBlocks * dimSettings.levelsPerHeight());
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
    
    // Player bonus
    int playerBonus = 0;
    if (Config.COMMON.applyPlayerBasedLeveling.get()) {
      int rawBonus = PlayerLevelProvider.getProviders().stream()
          .filter(PlayerLevelProvider::isEnabled)
          .mapToInt(provider -> provider.calculateBonusLevels(List.of(player)))
          .sum();
      double multiplier = Config.COMMON.playerLevelMultiplier.get();
      playerBonus = (int) (rawBonus * multiplier);
    }
    
    // Calculate totals
    int totalCapped = structureBonus.nonBypassingBonus() + biomeBonus.nonBypassingBonus();
    int totalBypassing = structureBonus.bypassingBonus() + biomeBonus.bypassingBonus() + playerBonus;
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
      String biomeBonusStr = formatBonus(biomeBonus.nonBypassingBonus(), biomeBonus.bypassingBonus());
      source.sendSystemMessage(Component.literal("§7Biome: §f" + biomeBonus.biomeId() + " §7→ " + biomeBonusStr));
    } else {
      source.sendSystemMessage(Component.literal("§7Biome: §cunknown"));
    }
    
    // Structure line
    if (structureBonus.hasStructure()) {
      String structureBonusStr = formatBonus(structureBonus.nonBypassingBonus(), structureBonus.bypassingBonus());
      source.sendSystemMessage(Component.literal("§7Structure: §f" + structureBonus.structureId() + " §7→ " + structureBonusStr));
    } else {
      source.sendSystemMessage(Component.literal("§7Structure: §fnone"));
    }
    
    // Player line
    source.sendSystemMessage(Component.literal("§7Player: §f+" + playerBonus + " §7(bypasses cap)"));
    source.sendSystemMessage(Component.literal(""));
    
    // Final line with ranges
    String baseStr = formatRange(baseLow, baseHigh);
    String cappedStr = formatRange(cappedLow, cappedHigh);
    String finalStr = formatRange(finalLow, finalHigh);
    
    source.sendSystemMessage(Component.literal("§7Final: §f" + baseStr + " base + " + totalCapped + " §7→(Cap)§7→ §f" + cappedStr + " §7+ §f" + totalBypassing + " §7= §f§l" + finalStr));
    
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
