package dev.muon.dynamic_difficulty.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.*;
import java.util.stream.Collectors;


public class ModCommands {
  public static void register() {
    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
    LiteralArgumentBuilder<CommandSourceStack> addGlobalLevelCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("level")
                    .then(
                        Commands.literal("add")
                            .then(
                                Commands.argument("value", IntegerArgumentType.integer())
                                    .executes(ModCommands::executeAddLevelCommand))))
            .requires(ModCommands::hasPermission);
      dispatcher.register(addGlobalLevelCommand);
    LiteralArgumentBuilder<CommandSourceStack> getGlobalLevelCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("level")
                    .then(
                        Commands.literal("get")
                            .executes(ModCommands::executeGetLevelCommand)))
            .requires(ModCommands::hasPermission);
      dispatcher.register(getGlobalLevelCommand);
    
    LiteralArgumentBuilder<CommandSourceStack> dumpStructuresCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("dumpStructures")
                    .executes(ModCommands::executeDumpStructuresCommand))
            .requires(ModCommands::hasPermission);
      dispatcher.register(dumpStructuresCommand);
    });
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
          .collect(java.util.stream.Collectors.toList())) {
        
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
    
    return 1; // Command success
  }
}
