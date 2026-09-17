package com.sythiex.foodmood.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sythiex.foodmood.FoodMood;
import com.sythiex.foodmood.effect.OwnedFoodData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class FoodDataMixin implements OwnedFoodData {
    @Unique private Player foodmood$owner;
    @Unique private boolean foodmood$regenerating;

    @Override public void foodmood$setOwner(Player player) { foodmood$owner = player; }

    @Inject(method = "tick", at = @At("HEAD"))
    private void foodmood$refreshOwner(Player player, CallbackInfo ci) {
        foodmood$owner = player;
    }

    @Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
    private void foodmood$filterExhaustion(float amount, CallbackInfo ci) {
        if (!foodmood$regenerating && amount > 0 && foodmood$owner != null && foodmood$owner.hasEffect(FoodMood.SATISFIED)) ci.cancel();
    }

    // Both the fast saturated and slow unsaturated regeneration branches charge through this call
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"), require = 2)
    private void foodmood$allowHealingCost(FoodData data, float amount, Operation<Void> original) {
        boolean wasRegenerating = foodmood$regenerating;
        foodmood$regenerating = true;
        try { original.call(data, amount); }
        finally { foodmood$regenerating = wasRegenerating; }
    }
}
