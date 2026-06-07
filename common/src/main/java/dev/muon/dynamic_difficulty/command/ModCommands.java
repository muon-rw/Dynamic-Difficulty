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
import dev.muon.dynamic_difficulty.config.Configs;
import dev.muon.dynamic_difficulty.data.DimensionLevelingSettingsStore;
import dev.muon.dynamic_difficulty.settings.DimensionLevelingSettings;
import dev.muon.dynamic_difficulty.settings.LevelingSettings;
import dev.muon.dynamic_difficulty.settings.LocationLevelingSettings;
import dev.muon.dynamic_difficulty.util.LevelingUtils;
import dev.muon.dynamic_difficulty.util.LocationBonusUtils;
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

public class ModCommands {

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
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

    ResolvedSettings resolved = resolveSettings(level, pos);
    BaseLevel base = computeBaseLevel(level, pos, resolved);
    PlayerBonus player2 = computePlayerBonus(player, resolved);
    FinalRanges ranges = computeFinalRanges(resolved, base, player2);

    sendDebugReport(source, level, pos, resolved, base, player2, ranges);
    return 1;
  }

  private record ResolvedSettings(
      Identifier dimensionId,
      DimensionLevelingSettings dimSettings,
      LevelingSettings settings,
      LocationLevelingSettings.RawSettings biomeRaw,
      LocationLevelingSettings.RawSettings structureRaw,
      BiomeBonus biomeBonus,
      StructureBonus structureBonus,
      Double playerMultiplierOverride,
      double playerMultiplier,
      DimensionLevelingSettings.ApplyLevelBonuses applyBonusesOverride,
      boolean applyBiome,
      boolean applyStructure,
      boolean applyPlayer) {}

  private static ResolvedSettings resolveSettings(ServerLevel level, BlockPos pos) {
    ResourceKey<Level> dimension = level.dimension();
    Identifier dimensionId = dimension.identifier();
    Registry<Level> dimensionRegistry = level.registryAccess().lookupOrThrow(Registries.DIMENSION);
    DimensionLevelingSettings dimSettings = DimensionLevelingSettingsStore.get(dimension, dimensionRegistry);
    LevelingSettings settings = LocationBonusUtils.resolveLocationSettings(level, pos);

    LocationLevelingSettings.RawSettings biomeRaw = LocationBonusUtils.getBiomeSettingsAt(level, pos);
    LocationLevelingSettings.RawSettings structureRaw = LocationBonusUtils.getStructureSettingsAt(level, pos);

    BiomeBonus biomeBonus = LevelingAPI.getBiomeBonus(level, pos);
    StructureBonus structureBonus = LevelingAPI.getStructureBonus(level, pos);

    Double playerMultiplierOverride = settings.playerLevelMultiplier();
    double playerMultiplier = playerMultiplierOverride != null ? playerMultiplierOverride : Configs.SYNC.playerLevelMultiplier.get();

    DimensionLevelingSettings.ApplyLevelBonuses applyBonusesOverride = settings.applyLevelBonuses();
    boolean applyBiome = applyBonusesOverride == null || applyBonusesOverride.biome();
    boolean applyStructure = applyBonusesOverride == null || applyBonusesOverride.structure();
    boolean applyPlayer = applyBonusesOverride == null || applyBonusesOverride.player();

    return new ResolvedSettings(dimensionId, dimSettings, settings, biomeRaw, structureRaw, biomeBonus, structureBonus,
        playerMultiplierOverride, playerMultiplier, applyBonusesOverride, applyBiome, applyStructure, applyPlayer);
  }

  private record BaseLevel(
      BlockPos spawnPos,
      double distance,
      int distanceBonus,
      int seaLevel,
      int depthBonus,
      int heightBonus,
      int depthBlocks,
      int heightBlocks,
      long days,
      int dayBonus,
      float effectiveDifficulty,
      int localDifficultyBonus,
      int startingLevel,
      int baseLevel,
      int maxLevel,
      String maxLevelStr) {}

  private static BaseLevel computeBaseLevel(ServerLevel level, BlockPos pos, ResolvedSettings resolved) {
    LevelingSettings settings = resolved.settings();
    DimensionLevelingSettings dimSettings = resolved.dimSettings();

    BlockPos spawnPos = LevelingUtils.getEffectiveSpawnPos(level, dimSettings);
    double distance = LevelingUtils.horizontalDistance(spawnPos, pos);
    int distanceBonus = (int)(distance * settings.levelsPerDistance());

    int seaLevel = dimSettings.seaLevel();
    int depthBonus = 0;
    int heightBonus = 0;
    int depthBlocks = 0;
    int heightBlocks = 0;
    if (pos.getY() < seaLevel) {
      depthBlocks = seaLevel - pos.getY();
      if (settings.levelsPerDepth() > 0) {
        depthBonus = (int) (depthBlocks * settings.levelsPerDepth());
      }
    }
    if (pos.getY() > seaLevel) {
      heightBlocks = pos.getY() - seaLevel;
      if (settings.levelsPerHeight() > 0) {
        heightBonus = (int) (heightBlocks * settings.levelsPerHeight());
      }
    }

    long days = level.getOverworldClockTime() / 24000L;
    int dayBonus = (int)(days * settings.levelsPerDay());

    net.minecraft.world.DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
    float effectiveDifficulty = difficulty.getEffectiveDifficulty();
    int localDifficultyBonus = (int)(effectiveDifficulty * settings.levelsPerLocalDifficulty());

    int startingLevel = settings.startingLevel();
    int baseLevel = startingLevel + distanceBonus + depthBonus + heightBonus + dayBonus + localDifficultyBonus;
    int maxLevel = settings.maxLevel();
    String maxLevelStr = maxLevel > 0 ? String.valueOf(maxLevel) : "unlimited";

    return new BaseLevel(spawnPos, distance, distanceBonus, seaLevel, depthBonus, heightBonus, depthBlocks, heightBlocks,
        days, dayBonus, effectiveDifficulty, localDifficultyBonus, startingLevel, baseLevel, maxLevel, maxLevelStr);
  }

  private record PlayerBonus(int playerBonus, boolean playerBypassesCap) {}

  private static PlayerBonus computePlayerBonus(ServerPlayer player, ResolvedSettings resolved) {
    int playerBonus = 0;
    boolean playerBypassesCap = Configs.SYNC.playerLevelBypassesCap.get();
    if (Configs.SYNC.applyPlayerBasedLeveling.get() && resolved.applyPlayer()) {
      int rawBonus = PlayerLevelProvider.getProviders().stream()
          .filter(PlayerLevelProvider::isEnabled)
          .mapToInt(provider -> provider.calculateBonusLevels(List.of(player)))
          .sum();
      playerBonus = (int) (rawBonus * resolved.playerMultiplier());
    }
    return new PlayerBonus(playerBonus, playerBypassesCap);
  }

  private record FinalRanges(
      int totalCapped,
      int totalBypassing,
      int randomBonus,
      int baseLow,
      int baseHigh,
      int cappedLow,
      int cappedHigh,
      int finalLow,
      int finalHigh) {}

  private static FinalRanges computeFinalRanges(ResolvedSettings resolved, BaseLevel base, PlayerBonus player) {
    int structureNonBypassing = resolved.applyStructure() ? resolved.structureBonus().nonBypassingBonus() : 0;
    int structureBypassing = resolved.applyStructure() ? resolved.structureBonus().bypassingBonus() : 0;
    int biomeNonBypassing = resolved.applyBiome() ? resolved.biomeBonus().nonBypassingBonus() : 0;
    int biomeBypassing = resolved.applyBiome() ? resolved.biomeBonus().bypassingBonus() : 0;

    int totalCapped = structureNonBypassing + biomeNonBypassing + (player.playerBypassesCap() ? 0 : player.playerBonus());
    int totalBypassing = structureBypassing + biomeBypassing + (player.playerBypassesCap() ? player.playerBonus() : 0);
    int randomBonus = resolved.settings().randomLevelBonus();

    int baseLow = base.baseLevel();
    int baseHigh = base.baseLevel() + randomBonus;

    int preCappedLow = baseLow + totalCapped;
    int preCappedHigh = baseHigh + totalCapped;

    int maxLevel = base.maxLevel();
    int cappedLow = maxLevel > 0 ? Math.min(preCappedLow, maxLevel) : preCappedLow;
    int cappedHigh = maxLevel > 0 ? Math.min(preCappedHigh, maxLevel) : preCappedHigh;

    int finalLow = cappedLow + totalBypassing;
    int finalHigh = cappedHigh + totalBypassing;

    return new FinalRanges(totalCapped, totalBypassing, randomBonus, baseLow, baseHigh, cappedLow, cappedHigh, finalLow, finalHigh);
  }

  private static void sendDebugReport(CommandSourceStack source, ServerLevel level, BlockPos pos,
      ResolvedSettings resolved, BaseLevel base, PlayerBonus player, FinalRanges ranges) {
    LevelingSettings settings = resolved.settings();
    int seaLevel = base.seaLevel();
    BlockPos spawnPos = base.spawnPos();

    source.sendSystemMessage(Component.literal("§6=== Dynamic Difficulty Debug ==="));
    source.sendSystemMessage(Component.literal("§7Position: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
    source.sendSystemMessage(Component.literal("§7Dimension: §f" + resolved.dimensionId() +" §7(sea lvl: §f" + seaLevel + "§7, spawn: §f" + spawnPos.getX() + ", " + spawnPos.getZ() + "§7)"));
    source.sendSystemMessage(Component.literal("§7Scaling: §f" + settings.levelsPerDistance() + "§7/dist, §f" + settings.levelsPerDepth() + "§7/depth, §f" + settings.levelsPerHeight() + "§7/height, §f" + settings.levelsPerDay() + "§7/day, §f" + settings.levelsPerLocalDifficulty() + "§7/local, random: §f0-" + settings.randomLevelBonus()));

    String biomeOverridesDesc = resolved.biomeRaw() != null ? describeOverrides(resolved.biomeRaw()) : "";
    String structureOverridesDesc = resolved.structureRaw() != null ? describeOverrides(resolved.structureRaw()) : "";
    if (resolved.playerMultiplierOverride() != null || resolved.applyBonusesOverride() != null
            || !biomeOverridesDesc.isEmpty() || !structureOverridesDesc.isEmpty()) {
      source.sendSystemMessage(Component.literal(""));
      source.sendSystemMessage(Component.literal("§7Overrides:"));
      if (resolved.playerMultiplierOverride() != null) {
        source.sendSystemMessage(Component.literal("§7  Player Multiplier: §f" + resolved.playerMultiplier()));
      }
      if (resolved.applyBonusesOverride() != null) {
        source.sendSystemMessage(Component.literal("§7  Apply Bonuses: §fbiome=" + resolved.applyBiome() + ", structure=" + resolved.applyStructure() + ", player=" + resolved.applyPlayer()));
      }
      if (!biomeOverridesDesc.isEmpty()) {
        source.sendSystemMessage(Component.literal("§7  Biome overrides: §f" + biomeOverridesDesc));
      }
      if (!structureOverridesDesc.isEmpty()) {
        source.sendSystemMessage(Component.literal("§7  Structure overrides: §f" + structureOverridesDesc));
      }
    }
    source.sendSystemMessage(Component.literal(""));

    String locationStr = (int)base.distance() + " blocks from spawn";
    if (pos.getY() < seaLevel) {
      locationStr += ", " + base.depthBlocks() + " below sea lvl";
    } else {
      locationStr += ", " + base.heightBlocks() + " above sea lvl";
    }
    source.sendSystemMessage(Component.literal("§7Location: §f" + locationStr));

    source.sendSystemMessage(Component.literal("§7Base Calculation:"));
    source.sendSystemMessage(Component.literal("§7  Starting: §f" + base.startingLevel()));
    source.sendSystemMessage(Component.literal("§7  + Distance: §f" + base.distanceBonus() + " §7(" + (int)base.distance() + " × " + settings.levelsPerDistance() + ")"));
    if (pos.getY() < seaLevel) {
      source.sendSystemMessage(Component.literal("§7  + Depth: §f" + base.depthBonus() + " §7(" + base.depthBlocks() + " × " + settings.levelsPerDepth() + ")"));
    } else {
      source.sendSystemMessage(Component.literal("§7  + Height: §f" + base.heightBonus() + " §7(" + base.heightBlocks() + " × " + settings.levelsPerHeight() + ")"));
    }
    source.sendSystemMessage(Component.literal("§7  + Days: §f" + base.dayBonus() + " §7(" + base.days() + " × " + settings.levelsPerDay() + ")"));
    source.sendSystemMessage(Component.literal("§7  + Local: §f" + base.localDifficultyBonus() + " §7(" + String.format("%.2f", base.effectiveDifficulty()) + " × " + settings.levelsPerLocalDifficulty() + ")"));
    String baseRangeStr = ranges.randomBonus() > 0 ? base.baseLevel() + " (+0-" + ranges.randomBonus() + " random)" : String.valueOf(base.baseLevel());
    source.sendSystemMessage(Component.literal("§7  = Base Level: §f" + baseRangeStr + " §7(max: " + base.maxLevelStr() + ")"));
    source.sendSystemMessage(Component.literal(""));

    BiomeBonus biomeBonus = resolved.biomeBonus();
    if (biomeBonus.biomeId() != null) {
      String biomeBonusStr = resolved.applyBiome() ? formatBonus(biomeBonus.nonBypassingBonus(), biomeBonus.bypassingBonus()) : "§cdisabled";
      String biomeStatus = resolved.applyBiome() ? "" : " §7(disabled by override)";
      source.sendSystemMessage(Component.literal("§7Biome: §f" + biomeBonus.biomeId() + " §7→ " + biomeBonusStr + biomeStatus));
    } else {
      source.sendSystemMessage(Component.literal("§7Biome: §cunknown"));
    }

    StructureBonus structureBonus = resolved.structureBonus();
    if (structureBonus.hasStructure()) {
      String structureBonusStr = resolved.applyStructure() ? formatBonus(structureBonus.nonBypassingBonus(), structureBonus.bypassingBonus()) : "§cdisabled";
      String structureStatus = resolved.applyStructure() ? "" : " §7(disabled by override)";
      source.sendSystemMessage(Component.literal("§7Structure: §f" + structureBonus.structureId() + " §7→ " + structureBonusStr + structureStatus));
    } else {
      source.sendSystemMessage(Component.literal("§7Structure: §fnone"));
    }

    String playerCapBehavior = player.playerBypassesCap() ? "bypasses cap" : "respects cap";
    String playerStatus = resolved.applyPlayer() ? "" : " §7(disabled by override)";
    source.sendSystemMessage(Component.literal("§7Player: §f+" + player.playerBonus() + " §7(" + playerCapBehavior + ")" + playerStatus));
    source.sendSystemMessage(Component.literal(""));

    String baseStr = formatRange(ranges.baseLow(), ranges.baseHigh());
    String cappedStr = formatRange(ranges.cappedLow(), ranges.cappedHigh());
    String finalStr = formatRange(ranges.finalLow(), ranges.finalHigh());

    source.sendSystemMessage(Component.literal("§7Final: §f" + baseStr + " base + " + ranges.totalCapped() + " §7→(Cap:" + base.maxLevelStr() + ")§7→ §f" + cappedStr + " §7+ §f" + ranges.totalBypassing() + " §7= §f§l" + finalStr));
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
    if (sb.length() > 0) sb.append(", ");
    sb.append(name).append("=").append(value);
  }
}
