package dev.muon.dynamic_difficulty.mixin;

import java.util.List;
import com.bawnorton.mixinsquared.api.MixinCanceller;

public class DDMixinCanceller implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        /*
        if (mixinClassName.equals("net.dehydration.mixin.PotionItemMixin")) {
            Medieval.LOG.info("Disabled Dehydration PotionItemMixin");
            return true;
        }

         */
        return false;
    }
}