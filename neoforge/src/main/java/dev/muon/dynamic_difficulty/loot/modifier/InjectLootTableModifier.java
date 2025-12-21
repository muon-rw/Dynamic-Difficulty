package dev.muon.dynamic_difficulty.loot.modifier;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import dev.muon.dynamic_difficulty.config.Config;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Global loot modifier that injects a custom loot table into entity drops.
 * This allows a centralized loot table to be edited by users without touching vanilla tables.
 */
public class InjectLootTableModifier extends LootModifier {
    public static final MapCodec<InjectLootTableModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
        codecStart(instance).and(
            Identifier.CODEC.fieldOf("loot_table").forGetter(m -> m.lootTable)
        ).apply(instance, InjectLootTableModifier::new)
    );

    private final Identifier lootTable;
    
    // ThreadLocal guard to prevent infinite recursion
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> false);

    public InjectLootTableModifier(LootItemCondition[] conditions, Identifier lootTable) {
        super(conditions);
        this.lootTable = lootTable;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!Config.COMMON.enableLevelBasedDrops.get()) {
            return generatedLoot;
        }

        if (!(context.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof LivingEntity)) {
            return generatedLoot;
        }

        if (PROCESSING.get()) {
            return generatedLoot;
        }

        if (findPlayer(context) == null) {
            return generatedLoot;
        }

        try {
            PROCESSING.set(true);
            
            // Get the loot table to inject
            ResourceKey<LootTable> tableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTable);
            LootTable table = context.getLevel().getServer().reloadableRegistries().getLootTable(tableKey);
            
            if (table == LootTable.EMPTY) {
                DynamicDifficulty.LOGGER.warn("Configured inject loot table {} not found or is empty!", lootTable);
                return generatedLoot;
            }
            
            // Add loot from the configured table into the original drop table
            table.getRandomItems(context, generatedLoot::add);

            return generatedLoot;
        } finally {
            PROCESSING.set(false);
        }
    }
    
    /**
     * Comprehensive player search (similar to Apotheosis GenContext.findPlayer)
     */
    @Nullable
    private Player findPlayer(LootContext ctx) {
        if (ctx.getOptionalParameter(LootContextParams.ATTACKING_ENTITY) instanceof Player p) return p;
        if (ctx.getOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY) instanceof Player p) return p;
        if (ctx.getOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER) != null) return ctx.getOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER);
        return null;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
