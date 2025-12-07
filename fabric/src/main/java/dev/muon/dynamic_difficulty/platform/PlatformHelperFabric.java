package dev.muon.dynamic_difficulty.platform;

import net.fabricmc.loader.api.FabricLoader;

public class PlatformHelperFabric implements PlatformHelper {
    
    private static final LevelAttachmentHelper LEVEL_ATTACHMENT_HELPER = new LevelAttachmentHelperFabric();
    private static final NetworkHelper NETWORK_HELPER = new NetworkHelperFabric();

    @Override
    public Platform getPlatform() {
        return Platform.FABRIC;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
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
