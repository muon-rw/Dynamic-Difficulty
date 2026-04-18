package dev.muon.dynamic_difficulty.mixin;

import dev.muon.dynamic_difficulty.item.LevelUpItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensures LevelUpItems get a chance to interact with mobs before mob-specific logic (e.g. Wolf sit toggle).
 * Vanilla only calls item.interactLivingEntity for Name Tag and Spawn Egg in checkAndHandleImportantInteractions.
 */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "checkAndHandleImportantInteractions", at = @At("HEAD"), cancellable = true, remap = false)
    private void dynamic_difficulty$handleLevelUpItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof LevelUpItem) {
            InteractionResult result = stack.interactLivingEntity(player, (LivingEntity) (Object) this, hand);
            if (result.consumesAction()) {
                cir.setReturnValue(result);
            }
        }
    }
}
