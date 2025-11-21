package dev.muon.dynamic_difficulty;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.compat.puffish.PuffishSkillsProvider;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.attribute.ModAttributes;
import dev.muon.dynamic_difficulty.command.ModCommands;
import dev.muon.dynamic_difficulty.item.ModItems;
import dev.muon.dynamic_difficulty.EntityLevelAttachment;
import dev.muon.dynamic_difficulty.LevelingEvents;
import dev.muon.dynamic_difficulty.player.PlaytimePlayerLevelProvider;
import dev.muon.dynamic_difficulty.player.PlayerLevelUpdateHandler;
import dev.muon.dynamic_difficulty.loot.condition.ModLootConditions;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class DynamicDifficulty implements ModInitializer {
  public static final Logger LOGGER = LogUtils.getLogger();
  public static final String MODID = "dynamic_difficulty";

  public static ResourceLocation loc(String path) {
    return ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, path);
  }

  @Override
  public void onInitialize() {
    ModAttributes.register();
    ModItems.register();
    ModLootConditions.register();
    Config.register();
    LevelingEvents.register();
    ModCommands.register();
    NetworkDispatcher.register();

    // Register built-in playtime provider (always available)
    LevelingAPI.registerPlayerLevelProvider(new PlaytimePlayerLevelProvider());
    
    // Register mod-specific providers
    if (isModLoaded("puffish_skills")) {
      LevelingAPI.registerPlayerLevelProvider(new PuffishSkillsProvider());
    }

    PlayerLevelUpdateHandler.registerCallback(PlayerLevelUpdateHandler::updatePlayerLevel);
  }

  public static boolean isModLoaded (String modId) {
    return FabricLoader.getInstance().isModLoaded(modId);
  }
}
