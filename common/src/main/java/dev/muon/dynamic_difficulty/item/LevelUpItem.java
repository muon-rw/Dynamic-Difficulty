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
 * Cannot be used on players; they use the PlayerLevelProvider system.
 * Max level is read from config at runtime, so config changes apply without a restart.
 */
public class LevelUpItem extends Item {
    private final int levelsToAdd;
    private final Supplier<Integer> maxLevelSupplier;
    private final boolean hasGlint;

    public LevelUpItem(Item.Properties properties, int levelsToAdd, Supplier<Integer> maxLevelSupplier, boolean hasGlint) {
        super(properties);
        this.levelsToAdd = levelsToAdd;
        this.maxLevelSupplier = maxLevelSupplier;
        this.hasGlint = hasGlint;
    }

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
        if (target instanceof Player) {
            if (!player.level().isClientSide()) {
                player.sendOverlayMessage(
                        Component.translatable("item.dynamic_difficulty.level_up.cannot_use_on_player")
                                .withStyle(ChatFormatting.RED)
                );
            }
            return InteractionResult.FAIL;
        }

        if (!LevelingAPI.canHaveLevel(target)) {
            if (!player.level().isClientSide()) {
                player.sendOverlayMessage(
                        Component.translatable("item.dynamic_difficulty.level_up.cannot_level_entity")
                                .withStyle(ChatFormatting.RED)
                );
            }
            return InteractionResult.FAIL;
        }

        if (!player.level().isClientSide()) {
            int currentLevel = LevelingAPI.getLevel(target);
            int maxLevel = getMaxLevel();

            if (currentLevel >= maxLevel) {
                player.sendOverlayMessage(
                        Component.translatable("item.dynamic_difficulty.level_up.max_level_reached", maxLevel)
                                .withStyle(ChatFormatting.YELLOW)
                );
                return InteractionResult.FAIL;
            }

            int newLevel = Math.min(currentLevel + levelsToAdd, maxLevel);

            try {
                LevelingAPI.setAndUpdateLevel(target, newLevel);

                player.sendOverlayMessage(
                        Component.translatable("item.dynamic_difficulty.level_up.success",
                                        target.getDisplayName(), currentLevel, newLevel)
                                .withStyle(ChatFormatting.GREEN)
                );

                if (!player.isCreative()) {
                    stack.shrink(1);
                }

                return InteractionResult.SUCCESS;
            } catch (IllegalArgumentException e) {
                player.sendOverlayMessage(
                        Component.literal(e.getMessage()).withStyle(ChatFormatting.RED)
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

