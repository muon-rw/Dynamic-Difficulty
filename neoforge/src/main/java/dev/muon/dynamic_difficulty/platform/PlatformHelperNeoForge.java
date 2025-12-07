package dev.muon.dynamic_difficulty.platform;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;

public class PlatformHelperNeoForge implements PlatformHelper {
    
    private static final LevelAttachmentHelper LEVEL_ATTACHMENT_HELPER = new LevelAttachmentHelperNeoForge();
    private static final NetworkHelper NETWORK_HELPER = new NetworkHelperNeoForge();

    @Override
    public Platform getPlatform() {
        return Platform.NEOFORGE;
    }

    @Override
    public boolean isModLoaded(String modId) {
        if (ModList.get() == null) {
            return LoadingModList.get().getMods().stream().map(ModInfo::getModId).anyMatch(modId::equals);
        }
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }
    
    @Override
    public LevelAttachmentHelper getLevelAttachmentHelper() {
        return LEVEL_ATTACHMENT_HELPER;
    }
    
    @Override
    public NetworkHelper getNetworkHelper() {
        return NETWORK_HELPER;
    }
}