package com.sythiex.foodmood.mixin;

import com.sythiex.foodmood.effect.OwnedFoodData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Shadow protected FoodData foodData;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void foodmood$ownFoodData(CallbackInfo ci) {
        ((OwnedFoodData) foodData).foodmood$setOwner((Player) (Object) this);
    }
}
