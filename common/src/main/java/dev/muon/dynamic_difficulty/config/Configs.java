package dev.muon.dynamic_difficulty.config;

import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;

import java.util.function.Supplier;

public final class Configs {

    public static ConfigClient CLIENT;
    public static ConfigSync SYNC;

    private Configs() {}

    /**
     * Registers and loads all configs. Safe to call from common init on both loaders;
     * FzzyConfig handles dist-appropriate gating via {@link RegisterType}.
     */
    public static void register() {
        // Supplier casts disambiguate from the Kotlin Function0 overload.
        CLIENT = ConfigApi.registerAndLoadConfig((Supplier<ConfigClient>) ConfigClient::new, RegisterType.CLIENT);
        SYNC = ConfigApi.registerAndLoadConfig((Supplier<ConfigSync>) ConfigSync::new, RegisterType.BOTH);
    }
}
