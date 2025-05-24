package dev.muon.dynamic_difficulty;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.attribute.ModAttributes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
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
  }
}
