package dev.muon.dynamic_difficulty.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.data.BiomeLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.DimensionsLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.data.StructureLevelingSettingsReloader;
import dev.muon.dynamic_difficulty.settings.BiomeBonusSettings;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.StructureBonusSettings;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
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
    
    source.sendSystemMessage(Component.literal("§6=== Dynamic Difficulty Location Debug ==="));
    source.sendSystemMessage(Component.literal("§7Position: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
    
    // Dimension info
    ResourceKey<Level> dimension = level.dimension();
    ResourceLocation dimensionId = dimension.location();
    Registry<Level> dimensionRegistry = level.registryAccess().registryOrThrow(Registries.DIMENSION);
    DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(dimension, dimensionRegistry);
    
    source.sendSystemMessage(Component.literal("§e--- Dimension ---"));
    source.sendSystemMessage(Component.literal("§7ID: §f" + dimensionId));
    source.sendSystemMessage(Component.literal("§7Starting Level: §f" + dimSettings.startingLevel()));
    source.sendSystemMessage(Component.literal("§7Max Level: §f" + (dimSettings.maxLevel() > 0 ? dimSettings.maxLevel() : "Unlimited")));
    source.sendSystemMessage(Component.literal("§7Levels per Distance: §f" + dimSettings.levelsPerDistance()));
    source.sendSystemMessage(Component.literal("§7Levels per Deepness: §f" + dimSettings.levelsPerDeepness()));
    source.sendSystemMessage(Component.literal("§7Random Level Bonus: §f0-" + dimSettings.randomLevelBonus()));
    
    // Biome info
    Holder<Biome> biomeHolder = level.getBiome(pos);
    Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
    Optional<ResourceKey<Biome>> biomeKey = biomeRegistry.getResourceKey(biomeHolder.value());
    
    source.sendSystemMessage(Component.literal("§e--- Biome ---"));
    if (biomeKey.isPresent()) {
      ResourceLocation biomeId = biomeKey.get().location();
      BiomeBonusSettings biomeSettings = BiomeLevelingSettingsReloader.get(biomeId, biomeRegistry);
      source.sendSystemMessage(Component.literal("§7ID: §f" + biomeId));
      if (biomeSettings != null) {
        source.sendSystemMessage(Component.literal("§7Level Bonus: §f+" + biomeSettings.levelBonus()));
        source.sendSystemMessage(Component.literal("§7Bypasses Cap: §f" + biomeSettings.bypassesCap()));
      } else {
        source.sendSystemMessage(Component.literal("§7Level Bonus: §f0 (no settings configured)"));
      }
    } else {
      source.sendSystemMessage(Component.literal("§7ID: §cUnknown"));
    }
    
    // Structure info
    Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    int highestStructureBonus = 0;
    ResourceLocation currentStructureId = null;
    StructureBonusSettings currentStructureSettings = null;
    
    for (Structure structure : structureRegistry) {
      StructureStart start = level.structureManager().getStructureAt(pos, structure);
      if (start != null && start.isValid()) {
        Optional<ResourceKey<Structure>> optKey = structureRegistry.getResourceKey(structure);
        if (optKey.isPresent()) {
          ResourceLocation structureId = optKey.get().location();
          StructureBonusSettings settings = StructureLevelingSettingsReloader.get(structureId, structureRegistry);
          int bonus = settings != null ? settings.levelBonus() : 0;
          if (bonus > highestStructureBonus) {
            highestStructureBonus = bonus;
            currentStructureId = structureId;
            currentStructureSettings = settings;
          }
        }
      }
    }
    
    source.sendSystemMessage(Component.literal("§e--- Structure ---"));
    if (currentStructureId != null) {
      source.sendSystemMessage(Component.literal("§7ID: §f" + currentStructureId));
      if (currentStructureSettings != null) {
        source.sendSystemMessage(Component.literal("§7Level Bonus: §f+" + currentStructureSettings.levelBonus()));
        source.sendSystemMessage(Component.literal("§7Bypasses Cap: §f" + currentStructureSettings.bypassesCap()));
      } else {
        source.sendSystemMessage(Component.literal("§7Level Bonus: §f0 (no settings configured)"));
      }
    } else {
      source.sendSystemMessage(Component.literal("§7Not in any structure"));
    }
    
    // Calculate base level
    BlockPos spawnPos = dimSettings.spawnPosOverride() != null ? 
        dimSettings.spawnPosOverride() : level.getSharedSpawnPos();
    double distance = Math.sqrt(spawnPos.distSqr(pos));
    int baseLevel = dimSettings.startingLevel();
    int distanceBonus = LevelingUtils.calculateDistanceFactors(player, distance, dimSettings);
    long days = level.getDayTime() / 24000L;
    int dayBonus = (int)(days * Config.COMMON.levelsPerDay.get());
    
    source.sendSystemMessage(Component.literal("§e--- Base Level Calculation ---"));
    source.sendSystemMessage(Component.literal("§7Starting Level: §f" + dimSettings.startingLevel()));
    source.sendSystemMessage(Component.literal("§7Distance from Spawn: §f" + String.format("%.1f", distance) + " blocks"));
    source.sendSystemMessage(Component.literal("§7Distance Bonus: §f" + (distanceBonus >= 0 ? "+" : "") + distanceBonus));
    source.sendSystemMessage(Component.literal("§7Days Passed: §f" + days));
    source.sendSystemMessage(Component.literal("§7Day Bonus: §f+" + dayBonus));
    source.sendSystemMessage(Component.literal("§7§lBase Level: §f§l" + (baseLevel + distanceBonus + dayBonus)));
    
    // Calculate bonuses
    int biomeBonus = LevelingAPI.getBiomeLevelBonus(player);
    int structureBonus = LevelingAPI.getStructureLevelBonus(player);
    int playerBonus = 0;
    if (Config.COMMON.applyPlayerBasedLeveling.get()) {
      int rawBonus = PlayerLevelProvider.getProviders().stream()
          .filter(PlayerLevelProvider::isEnabled)
          .mapToInt(provider -> provider.calculateBonusLevels(List.of(player)))
          .sum();
      double multiplier = Config.COMMON.playerLevelMultiplier.get();
      playerBonus = (int) (rawBonus * multiplier);
    }
    
    source.sendSystemMessage(Component.literal("§e--- Bonuses ---"));
    source.sendSystemMessage(Component.literal("§7Structure Bonus: §f" + (structureBonus > 0 ? "+" : "") + structureBonus));
    source.sendSystemMessage(Component.literal("§7Biome Bonus: §f" + (biomeBonus > 0 ? "+" : "") + biomeBonus));
    source.sendSystemMessage(Component.literal("§7Player Bonus: §f" + (playerBonus > 0 ? "+" : "") + playerBonus));
    
    // Final level calculation (simulate what would be applied to an entity)
    int finalBaseLevel = baseLevel + distanceBonus + dayBonus;
    int finalLevel = finalBaseLevel + structureBonus + biomeBonus + playerBonus;
    
    source.sendSystemMessage(Component.literal("§e--- Final Level (for entities) ---"));
    source.sendSystemMessage(Component.literal("§7Base Level: §f" + finalBaseLevel));
    source.sendSystemMessage(Component.literal("§7+ All Bonuses: §f+" + (structureBonus + biomeBonus + playerBonus)));
    source.sendSystemMessage(Component.literal("§7§lFinal Level: §f§l" + Math.max(1, finalLevel)));
    
    source.sendSystemMessage(Component.literal("§6====================================="));
    
    return 1;
  }
}
