package dev.muon.dynamic_difficulty;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.compat.PuffishSkillsProvider;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.attribute.ModAttributes;
import dev.muon.dynamic_difficulty.command.ModCommands;
import dev.muon.dynamic_difficulty.item.ModItems;
import dev.muon.dynamic_difficulty.leveling.LevelingEvents;
import dev.muon.dynamic_difficulty.leveling.PlayerLevelUpdateHandler;
import dev.muon.dynamic_difficulty.loot.condition.ModLootConditions;
import dev.muon.dynamic_difficulty.network.NetworkDispatcher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class DynamicDifficulty implements ModInitializer {
  public static final Logger LOGGER = LogUtils.getLogger();
  public static final String MODID = "dynamic_difficulty";

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, path);
  }

  @Override
  public void onInitialize() {
    Config.init();
    ModAttributes.init();
    ModCommands.init();
    ModItems.init();
    ModLootConditions.init();
    LevelingEvents.init();
    NetworkDispatcher.init();

    if (isModLoaded("puffish_skills")) {
      LevelingAPI.registerPlayerLevelProvider(new PuffishSkillsProvider());
    }

    PlayerLevelUpdateHandler.registerCallback(PlayerLevelUpdateHandler::handlePlayerLevelUpdate);
  }

  public static boolean isModLoaded (String modId) {
    return FabricLoader.getInstance().isModLoaded(modId);
  }
}
