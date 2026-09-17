package com.sythiex.foodmood.effect;

import com.sythiex.foodmood.mixin.EffectInstanceAccess;
import net.minecraft.world.effect.MobEffectInstance;

/** Preserve vanilla effect chains without ticking their gameplay effects twice. */
public final class EffectLayers {
    public static MobEffectInstance copy(MobEffectInstance effect) {
        if (effect == null) return null;
        var result = new MobEffectInstance(effect.getEffect(), effect.getDuration(), effect.getAmplifier(),
                effect.isAmbient(), effect.isVisible(), effect.showIcon(), copy(((EffectInstanceAccess) effect).foodmood$hidden()));
        result.getCures().clear();
        result.getCures().addAll(effect.getCures());
        return result;
    }

    public static MobEffectInstance age(MobEffectInstance effect) {
        if (effect == null) return null;
        var access = (EffectInstanceAccess) effect;
        access.foodmood$hidden(age(access.foodmood$hidden()));
        if (!effect.isInfiniteDuration()) access.foodmood$duration(Math.max(0, effect.getDuration() - 1));
        return effect.getDuration() == 0 ? access.foodmood$hidden() : effect;
    }

    public static MobEffectInstance combine(MobEffectInstance external, MobEffectInstance reward) {
        if (external == null) return reward;
        var result = copy(external);
        result.update(reward);
        return result;
    }

    private EffectLayers() {}
}
