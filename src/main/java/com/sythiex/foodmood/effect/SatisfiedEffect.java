package com.sythiex.foodmood.effect;

import com.sythiex.foodmood.config.FoodMoodConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class SatisfiedEffect extends MobEffect {
    public SatisfiedEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB5DBD5);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath("foodmood", "satisfied_speed"),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> (FoodMoodConfig.SERVER_SPEC.isLoaded() ? FoodMoodConfig.SATISFIED_SPEED.get() : 0.10) * (amplifier + 1));
    }
}
