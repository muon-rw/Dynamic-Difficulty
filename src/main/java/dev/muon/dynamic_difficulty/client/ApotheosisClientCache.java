package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = DynamicDifficulty.MODID, value = Dist.CLIENT)
public class ApotheosisClientCache {
    private static final boolean APOTHEOSIS_LOADED = ModList.get().isLoaded("apotheosis");
    private static final Map<Integer, String> TIER_CACHE = new HashMap<>();
    private static final Map<Integer, Long> LAST_SEEN = new HashMap<>();
    private static final long CLEANUP_INTERVAL = 20 * 60; // Every 60 seconds
    private static final long ENTITY_TIMEOUT = 5 * 60 * 20; // 5 minutes
    private static long lastCleanup = 0;
    
    // Tier mappings based on the identifier patterns
    private static final Map<String, String> TIER_NAMES = Map.of(
        "haven", "Haven",
        "frontier", "Frontier", 
        "ascent", "Ascent",
        "summit", "Summit",
        "pinnacle", "Pinnacle"
    );
    
    public static String getWorldTier(LivingEntity entity) {
        if (!APOTHEOSIS_LOADED) return null;
        
        int entityId = entity.getId();
        
        // Check cache first
        if (TIER_CACHE.containsKey(entityId)) {
            LAST_SEEN.put(entityId, entity.level().getGameTime());
            return TIER_CACHE.get(entityId);
        }
        
        // Scan for Apotheosis modifiers
        String tier = scanForApotheosisModifiers(entity);
        if (tier != null) {
            TIER_CACHE.put(entityId, tier);
            LAST_SEEN.put(entityId, entity.level().getGameTime());
        }
        
        return tier;
    }
    
    private static String scanForApotheosisModifiers(LivingEntity entity) {
        // Iterate through all attributes and their modifiers
        for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes()) {
            for (AttributeModifier modifier : instance.getModifiers()) {
                ResourceLocation id = modifier.id();
                if (id != null && "apotheosis".equals(id.getNamespace())) {
                    String path = id.getPath();
                    // Check each tier pattern
                    for (Map.Entry<String, String> entry : TIER_NAMES.entrySet()) {
                        if (path.startsWith(entry.getKey() + "/")) {
                            return entry.getValue();
                        }
                    }
                }
            }
        }
        return null;
    }
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null) return;
        
        long currentTime = minecraft.level.getGameTime();
        
        // Periodic cleanup, just in case
        if (currentTime - lastCleanup > CLEANUP_INTERVAL) {
            lastCleanup = currentTime;
            cleanupCache(currentTime);
        }
    }
    
    private static void cleanupCache(long currentTime) {
        Iterator<Map.Entry<Integer, Long>> iterator = LAST_SEEN.entrySet().iterator();
        int removed = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > ENTITY_TIMEOUT) {
                TIER_CACHE.remove(entry.getKey());
                iterator.remove();
                removed++;
            }
        }
    }
    
    // Clear cache entry when entity dies
    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            int entityId = event.getEntity().getId();
            TIER_CACHE.remove(entityId);
            LAST_SEEN.remove(entityId);
        }
    }
    
    // Clear cache when leaving a world
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clearCache();
        }
    }
    
    public static void clearCache() {
        TIER_CACHE.clear();
        LAST_SEEN.clear();
    }
} 