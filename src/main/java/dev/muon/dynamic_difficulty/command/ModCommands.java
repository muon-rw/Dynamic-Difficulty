package dev.muon.dynamic_difficulty.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
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
    event.getDispatcher().register(addGlobalLevelCommand);
    LiteralArgumentBuilder<CommandSourceStack> getGlobalLevelCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("level")
                    .then(
                        Commands.literal("get")
                            .executes(ModCommands::executeGetLevelCommand)))
            .requires(ModCommands::hasPermission);
    event.getDispatcher().register(getGlobalLevelCommand);
    
    LiteralArgumentBuilder<CommandSourceStack> dumpStructuresCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("dumpStructures")
                    .executes(ModCommands::executeDumpStructuresCommand))
            .requires(ModCommands::hasPermission);
    event.getDispatcher().register(dumpStructuresCommand);
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
      
      // First, show configured individual structure bonuses
      DynamicDifficulty.LOGGER.info("--- Configured Individual Structure Bonuses ---");
      Map<ResourceLocation, Integer> individualBonuses = Config.getStructureBonuses();
      if (individualBonuses.isEmpty()) {
        DynamicDifficulty.LOGGER.info("No individual structure bonuses configured.");
      } else {
        individualBonuses.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
              DynamicDifficulty.LOGGER.info(entry.getKey() + " (+" + entry.getValue() + " levels)");
            });
      }
      
      DynamicDifficulty.LOGGER.info("--- Dumping Structure IDs by Configured Tags ---");
      
      // Get configured structure tags from config
      Map<ResourceLocation, Integer> configuredTags = Config.getStructureTagBonuses();
      
      // Dump structures by configured tags
      for (ResourceLocation tagLocation : configuredTags.keySet()) {
        TagKey<Structure> tagKey = TagKey.create(Registries.STRUCTURE, tagLocation);
        int levelBonus = configuredTags.get(tagLocation);
        
        DynamicDifficulty.LOGGER.info("--- Structures in Tag: " + tagKey.location() + " (+" + levelBonus + " levels) ---");
        List<ResourceLocation> structuresInThisTag = new ArrayList<>();
        
        structureRegistry.getTagOrEmpty(tagKey).forEach(holder -> {
          ResourceLocation id = holder.unwrapKey().get().location();
          if (allStructureIdsInTargetNamespaces.contains(id)) {
            structuresInThisTag.add(id);
          }
        });
        
        structuresInThisTag.stream()
            .sorted(ResourceLocation::compareTo)
            .forEach(id -> {
              DynamicDifficulty.LOGGER.info(id.toString());
              categorizedStructureIds.add(id);
            });
            
        if (structuresInThisTag.isEmpty()) {
          DynamicDifficulty.LOGGER.info("No structures found in this tag and target namespaces.");
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
