package dev.muon.dynamic_difficulty.mixin.compat.puffish_skills;

import dev.muon.dynamic_difficulty.api.PlayerLevelProvider;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.config.CategoryConfig;
import net.puffish.skillsmod.config.skill.SkillConfig;
import net.puffish.skillsmod.server.data.CategoryData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkillsMod.class, remap = false)
public class SkillsModMixin {

    @Inject(
        method = "updateRewards(Lnet/minecraft/server/level/ServerPlayer;Lnet/puffish/skillsmod/config/CategoryConfig;Lnet/puffish/skillsmod/server/data/CategoryData;)V",
        at = @At("RETURN"),
        remap = false
    )
    private void onUpdateRewards(ServerPlayer player, CategoryConfig category, CategoryData categoryData, CallbackInfo ci) {
        PlayerLevelProvider.requestPlayerLevelUpdate(player);
    }

    @Inject(
        method = "updateSkillRewards(Lnet/minecraft/server/level/ServerPlayer;Lnet/puffish/skillsmod/config/CategoryConfig;Lnet/puffish/skillsmod/server/data/CategoryData;Lnet/puffish/skillsmod/config/skill/SkillConfig;Z)V",
        at = @At("RETURN"),
        remap = false
    )
    private void onUpdateSkillRewards(ServerPlayer player, CategoryConfig category, CategoryData categoryData, SkillConfig skill, boolean isUnlock, CallbackInfo ci) {
        PlayerLevelProvider.requestPlayerLevelUpdate(player);
    }
}

