package dev.muon.dynamic_difficulty;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.compat.PuffishSkillsProvider;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.attribute.ModAttributes;
import dev.muon.dynamic_difficulty.leveling.PlayerLevelUpdateHandler;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.loading.LoadingModList;
import org.slf4j.Logger;

@Mod(DynamicDifficulty.MODID)
public class DynamicDifficulty {
  public static final Logger LOGGER = LogUtils.getLogger();
  public static final String MODID = "dynamic_difficulty";

  public static ResourceLocation loc(String path) {
    return ResourceLocation.fromNamespaceAndPath(DynamicDifficulty.MODID, path);
  }

  public DynamicDifficulty(ModContainer container, IEventBus bus) {
    ModAttributes.REGISTRY.register(bus);
    Config.register(container);
    bus.addListener(this::onInterMod);

    PlayerLevelUpdateHandler.registerCallback(PlayerLevelUpdateHandler::handlePlayerLevelUpdate);
  }

  private void onInterMod(InterModEnqueueEvent event) {
    if (isModLoaded("puffish_skills")) {
      LevelingAPI.registerPlayerLevelProvider(new PuffishSkillsProvider());
    }
  }

  public static boolean isModLoaded (String modId) {
    return LoadingModList.get().getModFileById(modId) != null;
  }
}
