package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only mod class that registers the config screen for the NeoForge mods page.
 * The config button appears when selecting this mod in the mod list.
 */
@Mod(value = DynamicDifficulty.MODID, dist = Dist.CLIENT)
public class DynamicDifficultyClientNeoForge {

    public DynamicDifficultyClientNeoForge(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
