package dev.muon.dynamic_difficulty.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.LevelingSystem;
import dev.muon.dynamic_difficulty.api.BiomeBonus;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import dev.muon.dynamic_difficulty.api.StructureBonus;
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusUtils.ResolvedLocation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

  private static int executeDebugLocationCommand(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();

    if (!(source.getEntity() instanceof ServerPlayer player)) {
      source.sendFailure(Component.literal("This command can only be executed by a player"));
      return 0;
    }

    ServerLevel level = player.serverLevel();
    BlockPos pos = player.blockPosition();

    ResolvedLocation location = LocationBonusUtils.resolveLocation(level, pos);
    BaseFactors base = computeBaseFactors(level, pos, location);
    int playerBonus = Configs.SYNC.applyPlayerBasedLeveling.get()
        ? LevelingSystem.scalePlayerBonus(PlayerLevelProvider.sumBonusLevels(List.of(player)), location.settings())
        : 0;

    sendDebugReport(source, level.dimension().location(), pos, location, base, playerBonus);
    return 1;
  }

  private record BaseFactors(
      BlockPos spawnPos,
      double distance,
      int depthBlocks,
      int heightBlocks,
      long days,
      float effectiveDifficulty,
      int baseLevel) {}

  private static BaseFactors computeBaseFactors(ServerLevel level, BlockPos pos, ResolvedLocation location) {
    int seaLevel = location.dimension().seaLevel();
    BlockPos spawnPos = LevelingUtils.getEffectiveSpawnPos(level, location.dimension());
    return new BaseFactors(
        spawnPos,
        LevelingUtils.horizontalDistance(spawnPos, pos),
        Math.max(0, seaLevel - pos.getY()),
        Math.max(0, pos.getY() - seaLevel),
        level.getDayTime() / 24000L,
        level.getCurrentDifficultyAt(pos).getEffectiveDifficulty(),
        LevelingUtils.calculateBaseEntityLevel(level, pos, location.settings(), location.dimension()));
  }

  private static void sendDebugReport(CommandSourceStack source, ResourceLocation dimensionId, BlockPos pos,
      ResolvedLocation location, BaseFactors base, int playerBonus) {
    LevelingSettings settings = location.settings();
    int seaLevel = location.dimension().seaLevel();
    BlockPos spawnPos = base.spawnPos();
    String maxLevelStr = settings.maxLevel() > 1 ? String.valueOf(settings.maxLevel()) : "unlimited";

    source.sendSystemMessage(Component.literal("§6=== Dynamic Difficulty Debug ==="));
    source.sendSystemMessage(Component.literal("§7Position: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
    source.sendSystemMessage(Component.literal("§7Dimension: §f" + dimensionId + " §7(sea lvl: §f" + seaLevel + "§7, spawn: §f" + spawnPos.getX() + ", " + spawnPos.getZ() + "§7)"));
    source.sendSystemMessage(Component.literal("§7Scaling: §f" + settings.levelsPerDistance() + "§7/dist, §f" + settings.levelsPerDepth() + "§7/depth, §f" + settings.levelsPerHeight() + "§7/height, §f" + settings.levelsPerDay() + "§7/day, §f" + settings.levelsPerLocalDifficulty() + "§7/local, random: §f0-" + settings.randomLevelBonus()));

    String biomeOverridesDesc = location.biomeSettings() != null ? describeOverrides(location.biomeSettings()) : "";
    String structureOverridesDesc = location.structureSettings() != null ? describeOverrides(location.structureSettings()) : "";
    if (settings.playerLevelMultiplier() != null || settings.applyLevelBonuses() != null
            || !biomeOverridesDesc.isEmpty() || !structureOverridesDesc.isEmpty()) {
      source.sendSystemMessage(Component.literal(""));
      source.sendSystemMessage(Component.literal("§7Overrides:"));
      if (settings.playerLevelMultiplier() != null) {
        source.sendSystemMessage(Component.literal("§7  Player Multiplier: §f" + settings.playerLevelMultiplier()));
      }
      if (settings.applyLevelBonuses() != null) {
        source.sendSystemMessage(Component.literal("§7  Apply Bonuses: §fbiome=" + settings.appliesBiomeBonus() + ", structure=" + settings.appliesStructureBonus() + ", player=" + settings.appliesPlayerBonus()));
      }
      if (!biomeOverridesDesc.isEmpty()) {
        source.sendSystemMessage(Component.literal("§7  Biome overrides: §f" + biomeOverridesDesc));
      }
      if (!structureOverridesDesc.isEmpty()) {
        source.sendSystemMessage(Component.literal("§7  Structure overrides: §f" + structureOverridesDesc));
      }
    }
    source.sendSystemMessage(Component.literal(""));

    String locationStr = (int) base.distance() + " blocks from spawn";
    if (pos.getY() < seaLevel) {
      locationStr += ", " + base.depthBlocks() + " below sea lvl";
    } else {
      locationStr += ", " + base.heightBlocks() + " above sea lvl";
    }
    source.sendSystemMessage(Component.literal("§7Location: §f" + locationStr));

    // Terms are shown unrounded; the base level truncates the way spawning does.
    source.sendSystemMessage(Component.literal("§7Base Calculation:"));
    source.sendSystemMessage(Component.literal("§7  Starting: §f" + settings.startingLevel()));
    source.sendSystemMessage(Component.literal("§7  + Distance: §f" + formatTerm(base.distance() * settings.levelsPerDistance()) + " §7(" + (int) base.distance() + " × " + settings.levelsPerDistance() + ")"));
    if (pos.getY() < seaLevel) {
      source.sendSystemMessage(Component.literal("§7  + Depth: §f" + formatTerm(base.depthBlocks() * settings.levelsPerDepth()) + " §7(" + base.depthBlocks() + " × " + settings.levelsPerDepth() + ")"));
    } else {
      source.sendSystemMessage(Component.literal("§7  + Height: §f" + formatTerm(base.heightBlocks() * settings.levelsPerHeight()) + " §7(" + base.heightBlocks() + " × " + settings.levelsPerHeight() + ")"));
    }
    source.sendSystemMessage(Component.literal("§7  + Days: §f" + formatTerm(base.days() * settings.levelsPerDay()) + " §7(" + base.days() + " × " + settings.levelsPerDay() + ")"));
    source.sendSystemMessage(Component.literal("§7  + Local: §f" + formatTerm(base.effectiveDifficulty() * settings.levelsPerLocalDifficulty()) + " §7(" + String.format("%.2f", base.effectiveDifficulty()) + " × " + settings.levelsPerLocalDifficulty() + ")"));
    int randomBonus = settings.randomLevelBonus();
    String baseRangeStr = randomBonus > 0 ? base.baseLevel() + " (+0-" + randomBonus + " random)" : String.valueOf(base.baseLevel());
    source.sendSystemMessage(Component.literal("§7  = Base Level: §f" + baseRangeStr + " §7(max: " + maxLevelStr + ")"));
    source.sendSystemMessage(Component.literal(""));

    BiomeBonus biomeBonus = location.biomeBonus();
    if (biomeBonus.biomeId() != null) {
      String biomeBonusStr = settings.appliesBiomeBonus() ? formatBonus(biomeBonus.nonBypassingBonus(), biomeBonus.bypassingBonus()) : "§cdisabled";
      String biomeStatus = settings.appliesBiomeBonus() ? "" : " §7(disabled by override)";
      source.sendSystemMessage(Component.literal("§7Biome: §f" + biomeBonus.biomeId() + " §7→ " + biomeBonusStr + biomeStatus));
    } else {
      source.sendSystemMessage(Component.literal("§7Biome: §cunknown"));
    }

    StructureBonus structureBonus = location.structureBonus();
    if (structureBonus.hasStructure()) {
      String structureBonusStr = settings.appliesStructureBonus() ? formatBonus(structureBonus.nonBypassingBonus(), structureBonus.bypassingBonus()) : "§cdisabled";
      String structureStatus = settings.appliesStructureBonus() ? "" : " §7(disabled by override)";
      source.sendSystemMessage(Component.literal("§7Structure: §f" + structureBonus.structureId() + " §7→ " + structureBonusStr + structureStatus));
    } else {
      source.sendSystemMessage(Component.literal("§7Structure: §fnone"));
    }

    String playerCapBehavior = Configs.SYNC.playerLevelBypassesCap.get() ? "bypasses cap" : "respects cap";
    String playerStatus = settings.appliesPlayerBonus() ? "" : " §7(disabled by override)";
    source.sendSystemMessage(Component.literal("§7Player: §f+" + playerBonus + " §7(" + playerCapBehavior + ")" + playerStatus));
    source.sendSystemMessage(Component.literal(""));

    LevelingUtils.BonusSplit bonuses = LevelingUtils.splitBonuses(settings, structureBonus, biomeBonus, playerBonus);
    int baseLow = base.baseLevel();
    int baseHigh = baseLow + randomBonus;
    int cappedLow = LevelingUtils.applyLevelCap(baseLow + bonuses.nonBypassing(), settings.maxLevel());
    int cappedHigh = LevelingUtils.applyLevelCap(baseHigh + bonuses.nonBypassing(), settings.maxLevel());
    String finalStr = formatRange(Math.max(1, cappedLow + bonuses.bypassing()), Math.max(1, cappedHigh + bonuses.bypassing()));

    source.sendSystemMessage(Component.literal("§7Final: §f" + formatRange(baseLow, baseHigh) + " base + " + bonuses.nonBypassing() + " §7→(Cap:" + maxLevelStr + ")§7→ §f" + formatRange(cappedLow, cappedHigh) + " §7+ §f" + bonuses.bypassing() + " §7= §f§l" + finalStr));
  }

  private static String formatTerm(double value) {
    return String.format("%.2f", value);
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

  /** Omits the level_bonus/bypasses_cap pair; the structure/biome lines already display it. */
  private static String describeOverrides(LocationLevelingSettings.RawSettings raw) {
    StringBuilder sb = new StringBuilder();
    raw.startingLevel().ifPresent(v -> appendField(sb, "starting_level", v));
    raw.maxLevel().ifPresent(v -> appendField(sb, "max_level", v));
    raw.levelsPerDistance().ifPresent(v -> appendField(sb, "levels_per_distance", v));
    raw.levelsPerDeepness().ifPresent(v -> appendField(sb, "levels_per_deepness", v));
    raw.levelsPerHeight().ifPresent(v -> appendField(sb, "levels_per_height", v));
    raw.levelsPerDay().ifPresent(v -> appendField(sb, "levels_per_day", v));
    raw.levelsPerLocalDifficulty().ifPresent(v -> appendField(sb, "levels_per_local_difficulty", v));
    raw.randomLevelBonus().ifPresent(v -> appendField(sb, "random_level_bonus", v));
    raw.playerLevelMultiplier().ifPresent(v -> appendField(sb, "player_level_multiplier", v));
    raw.attributeModifiers().ifPresent(map -> appendField(sb, "attribute_modifiers", "[" + map.size() + " entries]"));
    return sb.toString();
  }

  private static void appendField(StringBuilder sb, String name, Object value) {
    if (!sb.isEmpty()) sb.append(", ");
    sb.append(name).append("=").append(value);
  }
}
