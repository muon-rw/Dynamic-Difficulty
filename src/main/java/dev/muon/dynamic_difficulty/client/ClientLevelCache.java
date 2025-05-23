package dev.muon.dynamic_difficulty.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class ClientLevelCache {
    private static final Map<UUID, Integer> PLAYER_LEVELS = new HashMap<>();
    private static final Map<Integer, Integer> ENTITY_LEVELS = new HashMap<>();

    public static int getLevel(LivingEntity entity) {
        if (entity instanceof Player) {
            return PLAYER_LEVELS.getOrDefault(entity.getUUID(), 0);
        }
        return ENTITY_LEVELS.getOrDefault(entity.getId(), 0);
    }

    public static void updatePlayerLevel(UUID playerId, int level) {
        PLAYER_LEVELS.put(playerId, level);
    }

    public static void updateEntityLevel(int entityId, int level) {
        ENTITY_LEVELS.put(entityId, level);
    }

    public static void clearCache() {
        PLAYER_LEVELS.clear();
        ENTITY_LEVELS.clear();
    }
}