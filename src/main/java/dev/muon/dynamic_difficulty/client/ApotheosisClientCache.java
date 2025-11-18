package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ApotheosisClientCache {
    private static final boolean APOTHEOSIS_LOADED = FabricLoader.getInstance().isModLoaded("apotheosis");
    private static final Map<Integer, String> TIER_CACHE = new HashMap<>();
    private static final Map<Integer, Long> LAST_SEEN = new HashMap<>();
    private static final long CLEANUP_INTERVAL = 20 * 60; // Every 60 seconds
    private static final long ENTITY_TIMEOUT = 5 * 60 * 20; // 5 minutes
    private static long lastCleanup = 0;

    // lol
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
        // Expensive! But should only happen once-ish per entity
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
    
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) return;
            
            long currentTime = client.level.getGameTime();
            
            // Periodic cleanup, just in case
            if (currentTime - lastCleanup > CLEANUP_INTERVAL) {
                lastCleanup = currentTime;
                cleanupCache(currentTime);
            }
        });
        
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            // Entity loaded, cache will be populated on first access
        });
        
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof LivingEntity) {
                int entityId = entity.getId();
                TIER_CACHE.remove(entityId);
                LAST_SEEN.remove(entityId);
            }
        });
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
    
    public static void clearCache() {
        TIER_CACHE.clear();
        LAST_SEEN.clear();
        lastCleanup = 0; // Reset cleanup timer for new world
    }
} 