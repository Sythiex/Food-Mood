package com.sythiex.foodmood.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEffectInstance.class)
public interface EffectInstanceAccess {
    @Accessor("duration") void foodmood$duration(int duration);
    @Accessor("hiddenEffect") MobEffectInstance foodmood$hidden();
    @Accessor("hiddenEffect") void foodmood$hidden(MobEffectInstance effect);
}
