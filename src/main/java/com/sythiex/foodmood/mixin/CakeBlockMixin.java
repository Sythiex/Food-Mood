package com.sythiex.foodmood.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sythiex.foodmood.craving.CravingService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CakeBlock.class)
public abstract class CakeBlockMixin {
    @ModifyExpressionValue(method = "eat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;canEat(Z)Z"))
    private static boolean foodmood$allowCake(boolean canEat, LevelAccessor level, BlockPos pos, BlockState state, Player player) {
        return canEat || CravingService.outstanding(player, ResourceLocation.withDefaultNamespace("cake"));
    }

    @Inject(method = "eat", at = @At("RETURN"))
    private static void foodmood$ateCake(LevelAccessor level, BlockPos pos, BlockState state, Player player, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() == InteractionResult.SUCCESS && player instanceof ServerPlayer serverPlayer && state.hasProperty(CakeBlock.BITES)) {
            int previousBites = state.getValue(CakeBlock.BITES);
            BlockState after = level.getBlockState(pos);
            boolean ateSlice = previousBites < CakeBlock.MAX_BITES
                    && after.is(state.getBlock()) && after.hasProperty(CakeBlock.BITES)
                    && after.getValue(CakeBlock.BITES) == previousBites + 1;
            boolean ateLastSlice = previousBites == CakeBlock.MAX_BITES && after.isAir();
            if (ateSlice || ateLastSlice) CravingService.consume(serverPlayer, ResourceLocation.withDefaultNamespace("cake"));
        }
    }
}
