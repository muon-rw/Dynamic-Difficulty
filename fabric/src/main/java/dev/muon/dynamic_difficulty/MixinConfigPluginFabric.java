package dev.muon.dynamic_difficulty;

import com.bawnorton.mixinsquared.adjuster.MixinAnnotationAdjusterRegistrar;
import com.bawnorton.mixinsquared.canceller.MixinCancellerRegistrar;
import dev.muon.dynamic_difficulty.mixin.DDMixinAdjuster;
import dev.muon.dynamic_difficulty.mixin.DDMixinCanceller;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MixinConfigPluginFabric implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogManager.getLogger("DynamicDifficulty-Mixin");

    @Override
    public void onLoad(String mixinPackage) {
        MixinCancellerRegistrar.register(new DDMixinCanceller());
        MixinAnnotationAdjusterRegistrar.register(new DDMixinAdjuster());
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.")) {
            List<String> requiredMods = extractRequiredMods(mixinClassName);

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

    private List<String> extractRequiredMods(String mixinClassName) {
        String[] parts = mixinClassName.split("\\.");
        List<String> requiredMods = new ArrayList<>();
        Set<String> excludedDirectories = Set.of("client", "accessor");

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("compat")) {
                for (int j = i + 1; j < parts.length - 1; j++) { // -1 to exclude the class name
                    String potentialModId = parts[j];
                    if (!excludedDirectories.contains(potentialModId)) {
                        requiredMods.add(potentialModId);
                    }
                }
                break;
            }
        }
        return requiredMods;
    }

    private String getSimpleMixinName(String mixinClassName) {
        String[] parts = mixinClassName.split("\\.");
        return parts[parts.length - 1];
    }

    private static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
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