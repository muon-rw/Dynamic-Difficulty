package dev.muon.dynamic_difficulty;

import com.mojang.logging.LogUtils;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.compat.puffish.PuffishSkillsProvider;
import dev.muon.dynamic_difficulty.compat.reskillable.ReskillableReimaginedProvider;
import dev.muon.dynamic_difficulty.config.Config;
import dev.muon.dynamic_difficulty.attribute.ModAttributes;
import dev.muon.dynamic_difficulty.item.ModItems;
import dev.muon.dynamic_difficulty.player.PlayerLevelUpdateHandler;
import dev.muon.dynamic_difficulty.loot.condition.ModLootConditions;
import dev.muon.dynamic_difficulty.loot.modifier.ModLootModifiers;
import dev.muon.dynamic_difficulty.player.PlaytimePlayerLevelProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.loading.LoadingModList;
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
        ModItems.REGISTRY.register(bus);
        ModLootConditions.REGISTRY.register(bus);
        ModLootModifiers.REGISTRY.register(bus);
        EntityLevelAttachment.REGISTRY.register(bus);
        Config.register(container);
        bus.addListener(this::onInterMod);

        PlayerLevelUpdateHandler.registerCallback(PlayerLevelUpdateHandler::updatePlayerLevel);
    }

    private void onInterMod(InterModEnqueueEvent event) {
        // Register built-in playtime provider (always available)
        LevelingAPI.registerPlayerLevelProvider(new PlaytimePlayerLevelProvider());
        
        // Register mod-specific providers
        if (isModLoaded("puffish_skills")) {
            LevelingAPI.registerPlayerLevelProvider(new PuffishSkillsProvider());
        }
        if (isModLoaded("reskillable")) {
            LevelingAPI.registerPlayerLevelProvider(new ReskillableReimaginedProvider());
        }
    }

    public static boolean isModLoaded(String modId) {
        return LoadingModList.get().getModFileById(modId) != null;
    }
}
