package dev.muon.dynamic_difficulty.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.api.LevelingAPI;
import dev.muon.dynamic_difficulty.config.Config;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Consumer;

@Mixin(LootTable.class)
public abstract class LootTableMixin {
    // ThreadLocal guard to prevent infinite recursion
    @Unique
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);
    
    @WrapMethod(
        method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V"
    )
    private void modifyLoot(LootContext context, Consumer<ItemStack> output, Operation<Void> original) {
        // Prevent infinite recursion - this mixin calls a loot table, which triggers the mixin again
        if (PROCESSING.get()) {
            original.call(context, output);
            return;
        }
        
        if (!context.hasParam(LootContextParams.THIS_ENTITY)) {
            original.call(context, output);
            return;
        }

        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (!(entity instanceof LivingEntity living) || !LevelingAPI.hasLevel(living)) {
            original.call(context, output);
            return;
        }

        // Only apply level-based drops if killed by a player
        if (findPlayer(context) == null) {
            original.call(context, output);
            return;
        }

        if (!Config.COMMON.enableLevelBasedDrops.get()) {
            original.call(context, output);
            return;
        }

        try {
            PROCESSING.set(true);
            
            ObjectArrayList<ItemStack> originalLoot = new ObjectArrayList<>();
            Consumer<ItemStack> collector = originalLoot::add;

            original.call(context, collector);

            if (context.getLevel() instanceof ServerLevel serverLevel) {
                ResourceLocation levelDropsTable = ResourceLocation.fromNamespaceAndPath(
                    DynamicDifficulty.MODID, "inject/level_based_drops");
                ResourceKey<LootTable> levelDropsKey = ResourceKey.create(
                    Registries.LOOT_TABLE, levelDropsTable);
                
                LootTable levelDropsLootTable = serverLevel.getServer()
                    .reloadableRegistries()
                    .getLootTable(levelDropsKey);
                
                if (levelDropsLootTable != LootTable.EMPTY) {
                    levelDropsLootTable.getRandomItemsRaw(context, originalLoot::add);
                }
            }

            originalLoot.forEach(output);
        } finally {
            PROCESSING.set(false);
        }
    }
    
    /**
     * Comprehensive player search (similar to Apotheosis GenContext.findPlayer, licensed MIT)
     */
    @Unique
    @Nullable
    private Player findPlayer(LootContext ctx) {
        if (ctx.getParamOrNull(LootContextParams.ATTACKING_ENTITY) instanceof Player p) return p;
        if (ctx.getParamOrNull(LootContextParams.DIRECT_ATTACKING_ENTITY) instanceof Player p) return p;
        if (ctx.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER) != null) return ctx.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
        return null;
    }
}

