package dev.muon.dynamic_difficulty.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Delegate to FzzyConfig — which manages all ConfigScreen UI for this mod's configs.
        // Returning null tells ModMenu to use FzzyConfig's own ConfigModMenuCompat registration.
        // We still wire a fallback here in case ModMenu prefers our factory:
        return parent -> {
            ConfigApi.INSTANCE.openScreen(DynamicDifficulty.MODID);
            return parent;
        };
    }
}
