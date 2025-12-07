package dev.muon.dynamic_difficulty.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigurationScreen(DynamicDifficulty.MODID, parent);
    }
}
