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
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.*;
import java.util.stream.Collectors;


public class ModCommands {
  public static void init() {
    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
    LiteralArgumentBuilder<CommandSourceStack> addGlobalLevelCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("level")
                    .then(
                        Commands.literal("add")
                            .then(
                                Commands.argument("value", IntegerArgumentType.integer())
                                    .then(
                                        Commands.argument("targets", EntityArgument.entities())
                                            .executes(ModCommands::executeAddLevelCommand)))))
            .requires(ModCommands::hasPermission);
      dispatcher.register(addGlobalLevelCommand);
    LiteralArgumentBuilder<CommandSourceStack> getGlobalLevelCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("level")
                    .then(
                        Commands.literal("get")
                            .then(
                                Commands.argument("target", EntityArgument.entity())
                                    .executes(ModCommands::executeGetLevelCommand))))
            .requires(ModCommands::hasPermission);
      dispatcher.register(getGlobalLevelCommand);
    
    LiteralArgumentBuilder<CommandSourceStack> dumpStructuresCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("dumpStructures")
                    .executes(ModCommands::executeDumpStructuresCommand))
            .requires(ModCommands::hasPermission);
      dispatcher.register(dumpStructuresCommand);
    
    LiteralArgumentBuilder<CommandSourceStack> debugLocationCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("debug")
                    .then(
                        Commands.literal("location")
                            .executes(ModCommands::executeDebugLocationCommand)))
            .requires(ModCommands::hasPermission);
      dispatcher.register(debugLocationCommand);
    });
  }

  private static int executeAddLevelCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    CommandSourceStack source = ctx.getSource();
    int levelsToAdd = IntegerArgumentType.getInteger(ctx, "value");
    Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
    
    if (targets.isEmpty()) {
      source.sendFailure(Component.literal("No entities found"));
      return 0;
    }
    
    final int[] successCount = {0};
    final int[] failCount = {0};
    
    for (Entity entity : targets) {
      if (!(entity instanceof LivingEntity livingEntity)) {
        failCount[0]++;
        continue;
      }
      
      // Check if entity can have levels
      if (!LevelingAPI.canHaveLevel(entity)) {
        if (entity instanceof ServerPlayer) {
          source.sendFailure(Component.literal("Cannot modify player levels - players use the PlayerLevelProvider system"));
        } else {
          source.sendFailure(Component.literal("Entity type " + entity.getType().getDescription().getString() + " cannot have levels"));
        }
        failCount[0]++;
        continue;
      }
      
      try {
        int oldLevel = LevelingAPI.getLevel(livingEntity);
        LevelingAPI.addLevels(livingEntity, levelsToAdd);
        int newLevel = LevelingAPI.getLevel(livingEntity);
        
        source.sendSuccess(() -> Component.literal(
          entity.getDisplayName().getString() + ": Level " + oldLevel + " -> " + newLevel + 
          (levelsToAdd >= 0 ? " (+" : " (") + levelsToAdd + ")"
        ), true);
        successCount[0]++;
      } catch (IllegalArgumentException e) {
        source.sendFailure(Component.literal("Error modifying level for " + entity.getDisplayName().getString() + ": " + e.getMessage()));
        failCount[0]++;
      }
    }
    
    if (successCount[0] > 0) {
      final int finalSuccessCount = successCount[0];
      source.sendSuccess(() -> Component.literal(
        "Successfully modified levels for " + finalSuccessCount + " entit" + (finalSuccessCount == 1 ? "y" : "ies")
      ), true);
    }
    
    if (failCount[0] > 0) {
      final int finalFailCount = failCount[0];
      source.sendFailure(Component.literal("Failed to modify levels for " + finalFailCount + " entit" + (finalFailCount == 1 ? "y" : "ies")));
    }
    
    return successCount[0] > 0 ? 1 : 0;
  }

  private static int executeGetLevelCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    CommandSourceStack source = ctx.getSource();
    Entity target = EntityArgument.getEntity(ctx, "target");
    
    if (!(target instanceof LivingEntity livingEntity)) {
      source.sendFailure(Component.literal("Target is not a living entity"));
      return 0;
    }
    
    if (LevelingAPI.hasLevel(target)) {
      int level = LevelingAPI.getLevel(livingEntity);
      String entityName = target.getDisplayName().getString();
      
      if (target instanceof ServerPlayer) {
        source.sendSuccess(() -> Component.literal(
          entityName + " (Player) - Display Level: " + level + 
          " (Note: Player levels are display-only and do not grant attribute bonuses)"
        ), false);
      } else {
        boolean canHaveLevel = LevelingAPI.canHaveLevel(target);
        source.sendSuccess(() -> Component.literal(
          entityName + " - Level: " + level + 
          (canHaveLevel ? "" : " (This entity type cannot have levels)")
        ), false);
      }
    } else {
      source.sendFailure(Component.literal(target.getDisplayName().getString() + " does not have a level assigned"));
      return 0;
    }
    
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
      Registry<Structure> structureRegistry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
      
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
      for (TagKey<Structure> tagKey : structureRegistry.getTags()
          .map(HolderSet.Named::key)
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
      structureRegistry.getTags()
          .map(HolderSet.Named::key)
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
  
  private static int executeDebugLocationCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    CommandSourceStack source = context.getSource();
    
    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("This command can only be executed by a player"));
      return 0;
    }
    
    ServerLevel level = player.level();
    BlockPos pos = player.blockPosition();
    
    source.sendSystemMessage(Component.literal("§6=== Dynamic Difficulty Location Debug ==="));
    source.sendSystemMessage(Component.literal("§7Position: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
    
    // Dimension info
    ResourceKey<Level> dimension = level.dimension();
    ResourceLocation dimensionId = dimension.location();
    DimensionLevelingSettings dimSettings = DimensionsLevelingSettingsReloader.get(
        dimension, level.registryAccess().lookupOrThrow(Registries.DIMENSION));
    
    source.sendSystemMessage(Component.literal("§e--- Dimension ---"));
    source.sendSystemMessage(Component.literal("§7ID: §f" + dimensionId));
    source.sendSystemMessage(Component.literal("§7Starting Level: §f" + dimSettings.startingLevel()));
    source.sendSystemMessage(Component.literal("§7Max Level: §f" + (dimSettings.maxLevel() > 0 ? dimSettings.maxLevel() : "Unlimited")));
    source.sendSystemMessage(Component.literal("§7Levels per Distance: §f" + dimSettings.levelsPerDistance()));
    source.sendSystemMessage(Component.literal("§7Levels per Deepness: §f" + dimSettings.levelsPerDeepness()));
    source.sendSystemMessage(Component.literal("§7Random Level Bonus: §f0-" + dimSettings.randomLevelBonus()));
    
    // Biome info
    Holder<Biome> biomeHolder = level.getBiome(pos);
    Optional<ResourceKey<Biome>> biomeKey = level.registryAccess().lookupOrThrow(Registries.BIOME).getResourceKey(biomeHolder.value());
    Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
    
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
    Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
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
    BlockPos spawnPos = level.getRespawnData().pos();
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
    int biomeBonus = biomeKey.isPresent() ? 
        LevelingAPI.getBiomeLevelBonus(biomeKey.get().location(), biomeRegistry) : 0;
    int structureBonus = LevelingAPI.getStructureLevelBonus(player);
    int playerBonus = 0;
    if (Config.COMMON.applyPlayerBasedLeveling.get()) {
      int rawBonus = PlayerLevelProvider.getProviders().stream()
          .filter(PlayerLevelProvider::isEnabled)
          .mapToInt(provider -> provider.calculateBonusLevels(java.util.List.of(player)))
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
