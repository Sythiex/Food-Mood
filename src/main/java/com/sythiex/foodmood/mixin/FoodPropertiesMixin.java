package com.sythiex.foodmood.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.sythiex.foodmood.craving.CravingService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IItemStackExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = IItemStackExtension.class, remap = false)
public interface FoodPropertiesMixin {
    @ModifyReturnValue(method = "getFoodProperties", at = @At("RETURN"))
    default FoodProperties foodmood$allowCravedFood(FoodProperties food, LivingEntity entity) {
        if (food != null && !food.canAlwaysEat() && entity instanceof Player player
                && CravingService.outstanding(player, (ItemStack) (Object) this)) {
            return new FoodProperties(food.nutrition(), food.saturation(), true, food.eatSeconds(), food.usingConvertsTo(), food.effects());
        }
        return food;
    }
}
