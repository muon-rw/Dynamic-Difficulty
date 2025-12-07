package dev.muon.dynamic_difficulty.mixin;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class DDMixinAdjuster implements MixinAnnotationAdjuster {

    @Override
    public AdjustableAnnotationNode adjust(List<String> targetClassNames, String mixinClassName, MethodNode method, AdjustableAnnotationNode annotation) {
//        if (mixinClassName.equals("io.github.apace100.apoli.mixin.EntityAttributeInstanceMixin")) {
//            if (method.name.equals("apoli$modifyAttribute") && annotation.is(ModifyReturnValue.class)) {
//                return null;
//            }
//        }
        return annotation;
    }
}