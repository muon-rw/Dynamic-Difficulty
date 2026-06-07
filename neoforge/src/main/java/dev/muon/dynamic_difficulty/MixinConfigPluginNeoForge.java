package dev.muon.dynamic_difficulty;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MixinConfigPluginNeoForge implements IMixinConfigPlugin {


    private static final Logger LOGGER = LogManager.getLogger("DynamicDifficulty-Mixin");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.")) {

            // Each subdirectory within /compat/ is a modid
            String[] parts = mixinClassName.split("\\.");
            List<String> requiredMods = new ArrayList<>();

            // EXCEPT: Directory names that shouldn't be considered mod IDs
            Set<String> excludedDirectories = Set.of("client", "accessor");

            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("compat")) {
                    // Collect all mod IDs in the path after "compat" until the class name
                    for (int j = i + 1; j < parts.length - 1; j++) { // -1 to exclude the class name
                        String potentialModId = parts[j];
                        if (!excludedDirectories.contains(potentialModId)) {
                            requiredMods.add(potentialModId);
                        }
                    }
                    break;
                }
            }

            for (String modId : requiredMods) {
                if (!isModLoaded(modId)) {
                    LOGGER.info("Disabling mixin {} because required mod '{}' is not loaded",
                            getSimpleMixinName(mixinClassName), modId);
                    return false;
                }
            }
            if (!requiredMods.isEmpty()) {
                LOGGER.info("Enabling mixin {} - all required mods {} are loaded",
                        getSimpleMixinName(mixinClassName), requiredMods);
            }
            return true;
        }

        return true;
    }

    private String getSimpleMixinName(String mixinClassName) {
        String[] parts = mixinClassName.split("\\.");
        return parts[parts.length - 1];
    }


    private static boolean isModLoaded(String modId) {
        if (ModList.get() == null) {
            return FMLLoader.getCurrent().getLoadingModList().getModFileById(modId) != null;
        }
        return ModList.get().isLoaded(modId);
    }


    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

}