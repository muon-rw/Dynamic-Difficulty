package dev.muon.dynamic_difficulty.client;

import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.client.Minecraft;
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
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = DynamicDifficulty.MODID, value = Dist.CLIENT)
public class ApotheosisClientCache {
    private static final boolean APOTHEOSIS_LOADED = ModList.get().isLoaded("apotheosis");
    // Cache both positive (tier found) and negative (no tier) results
    // null value means "no tier" (cached negative result), non-null means tier name
    private static final Map<Integer, String> TIER_CACHE = new HashMap<>();
    private static final Map<Integer, Long> LAST_SEEN = new HashMap<>();
    // Special marker to distinguish "not cached" from "cached as null"
    private static final String NO_TIER_MARKER = "";
    private static final long CLEANUP_INTERVAL = 20 * 60; // Every 60 seconds
    private static final long ENTITY_TIMEOUT = 5 * 60 * 20; // 5 minutes
    private static long lastCleanup = 0;

    // Tier name mapping - ordered by tier progression for potential early exit optimization
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
        
        // Check cache first - includes both positive and negative results
        if (TIER_CACHE.containsKey(entityId)) {
            LAST_SEEN.put(entityId, entity.level().getGameTime());
            String cached = TIER_CACHE.get(entityId);
            // Return null if cached as "no tier", otherwise return the tier name
            return cached.equals(NO_TIER_MARKER) ? null : cached;
        }
        
        // Scan for Apotheosis modifiers - expensive operation
        // Only happens once per entity (result is cached)
        String tier = scanForApotheosisModifiers(entity);
        
        // Cache the result (both positive and negative)
        // Use NO_TIER_MARKER for null to distinguish from "not cached"
        TIER_CACHE.put(entityId, tier != null ? tier : NO_TIER_MARKER);
        LAST_SEEN.put(entityId, entity.level().getGameTime());
        
        return tier;
    }
    
    /**
     * Invalidates the cache for a specific entity.
     * Call if ever updating the world tier of a mob dynamically
     * Apotheosis doesn't, so this is not hooked anywhere
     */
    public static void invalidateEntity(int entityId) {
        TIER_CACHE.remove(entityId);
        LAST_SEEN.remove(entityId);
    }
    
    private static String scanForApotheosisModifiers(LivingEntity entity) {
        // Iterate through all attributes and their modifiers
        // Early return when tier is found to avoid unnecessary iteration
        for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes()) {
            for (AttributeModifier modifier : instance.getModifiers()) {
                ResourceLocation id = modifier.id();
                if (id != null && "apotheosis".equals(id.getNamespace())) {
                    String path = id.getPath();
                    // Check each tier pattern - return immediately when found
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
        var minecraft = Minecraft.getInstance();
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

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            int entityId = event.getEntity().getId();
            TIER_CACHE.remove(entityId);
            LAST_SEEN.remove(entityId);
        }
    }

    @SubscribeEvent
    public static void onEntityUnload(EntityLeaveLevelEvent event) {
        if (event.getEntity().level().isClientSide()) {
            int entityId = event.getEntity().getId();
            TIER_CACHE.remove(entityId);
            LAST_SEEN.remove(entityId);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clearCache();
        }
    }
    
    public static void clearCache() {
        TIER_CACHE.clear();
        LAST_SEEN.clear();
        lastCleanup = 0; // Reset cleanup timer for new world
    }
} 