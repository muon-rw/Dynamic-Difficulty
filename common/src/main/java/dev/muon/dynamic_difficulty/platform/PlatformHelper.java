package dev.muon.dynamic_difficulty.platform;

public interface PlatformHelper {

    Platform getPlatform();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    LevelAttachmentHelper getLevelAttachmentHelper();

    NetworkHelper getNetworkHelper();

}