package dev.muon.dynamic_difficulty.item;

import dev.muon.dynamic_difficulty.api.LevelingAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An item that increases a mob's level when used on it.
 * Cannot be used on players (they use the PlayerLevelProvider system).
 * <p>
 * Max level is retrieved from config at runtime, allowing config changes without restart.
 */
public class LevelUpItem extends Item {
    private final int levelsToAdd;
    private final Supplier<Integer> maxLevelSupplier;
    private final boolean hasGlint;

    /**
     * Creates a level-up item.
     *
     * @param properties       Item properties
     * @param levelsToAdd      How many levels to add per use
     * @param maxLevelSupplier Supplier that provides the max level from config
     * @param hasGlint         Whether this item should have an enchantment glint
     */
    public LevelUpItem(Item.Properties properties, int levelsToAdd, Supplier<Integer> maxLevelSupplier, boolean hasGlint) {
        super(properties);
        this.levelsToAdd = levelsToAdd;
        this.maxLevelSupplier = maxLevelSupplier;
        this.hasGlint = hasGlint;
    }

    /**
     * Gets the current max level for this item from config.
     */
    private int getMaxLevel() {
        return maxLevelSupplier.get();
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return hasGlint || super.isFoil(stack);
    }

    @Override
    @NotNull
    public InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player, @NotNull LivingEntity target, @NotNull InteractionHand hand) {
        // Can't be used on players
        if (target instanceof Player) {
            if (!player.level().isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("item.dynamic_difficulty.level_up.cannot_use_on_player")
                                .withStyle(ChatFormatting.RED),
                        true
                );
            }
            return InteractionResult.FAIL;
        }

        // Check if entity can have levels
        if (!LevelingAPI.canHaveLevel(target)) {
            if (!player.level().isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("item.dynamic_difficulty.level_up.cannot_level_entity")
                                .withStyle(ChatFormatting.RED),
                        true
                );
            }
            return InteractionResult.FAIL;
        }

        if (!player.level().isClientSide()) {
            int currentLevel = LevelingAPI.getLevel(target);
            int maxLevel = getMaxLevel();

            // Check if already at or above max level for this item
            if (currentLevel >= maxLevel) {
                player.displayClientMessage(
                        Component.translatable("item.dynamic_difficulty.level_up.max_level_reached", maxLevel)
                                .withStyle(ChatFormatting.YELLOW),
                        true
                );
                return InteractionResult.FAIL;
            }

            // Add levels, but cap at maxLevel
            int newLevel = Math.min(currentLevel + levelsToAdd, maxLevel);

            try {
                LevelingAPI.setAndUpdateLevel(target, newLevel);

                // Success message
                player.displayClientMessage(
                        Component.translatable("item.dynamic_difficulty.level_up.success",
                                        target.getDisplayName(), currentLevel, newLevel)
                                .withStyle(ChatFormatting.GREEN),
                        true
                );

                // Consume item in survival mode
                if (!player.isCreative()) {
                    stack.shrink(1);
                }

                return InteractionResult.SUCCESS;
            } catch (IllegalArgumentException e) {
                // Shouldn't happen, but handle gracefully
                player.displayClientMessage(
                        Component.literal(e.getMessage()).withStyle(ChatFormatting.RED),
                        true
                );
                return InteractionResult.FAIL;
            }
        }

        return InteractionResult.CONSUME;
    }

    @Override
    @Deprecated
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context, @NotNull TooltipDisplay tooltipDisplay, @NotNull Consumer<Component> tooltipAdder, @NotNull TooltipFlag tooltipFlag) {
        tooltipAdder.accept(
                Component.translatable("item.dynamic_difficulty.level_up.tooltip.levels", levelsToAdd)
                        .withStyle(ChatFormatting.GRAY)
        );
        tooltipAdder.accept(
                Component.translatable("item.dynamic_difficulty.level_up.tooltip.max_level", getMaxLevel())
                        .withStyle(ChatFormatting.DARK_GRAY)
        );
        tooltipAdder.accept(
                Component.translatable("item.dynamic_difficulty.level_up.tooltip.usage")
                        .withStyle(ChatFormatting.DARK_AQUA)
        );
    }
}

