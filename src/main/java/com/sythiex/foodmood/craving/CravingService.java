package com.sythiex.foodmood.craving;

import com.sythiex.foodmood.FoodMood;
import com.sythiex.foodmood.config.FoodMoodConfig;
import com.sythiex.foodmood.effect.RewardController;
import com.sythiex.foodmood.network.CravingsPayload;
import java.util.Random;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CravingService {
    public static boolean eligible(Player player) { return !player.isCreative() && !player.isSpectator(); }

    public static CravingState synchronizeDay(ServerPlayer player) {
        var state = player.getData(FoodMood.CRAVINGS);
        long day = DailyCravings.day(player.server.overworld().getDayTime());
        if (state.day != day) {
            RewardController.expire(player, state);
            state.assign(day, DailyCravings.select(FoodPool.current(), FoodMoodConfig.CRAVING_COUNT.get(), new Random(player.getRandom().nextLong())),
                    RewardController.configuredEffect(), FoodMoodConfig.REWARD_EFFECT_LEVEL.get() - 1);
            sync(player);
        }
        return state;
    }

    public static boolean outstanding(Player player, ResourceLocation id) {
        if (!eligible(player)) return false;
        var state = player instanceof ServerPlayer serverPlayer ? synchronizeDay(serverPlayer) : player.getData(FoodMood.CRAVINGS);
        return state.outstanding(id);
    }

    public static boolean outstanding(Player player, ItemStack stack) {
        return !stack.isEmpty() && outstanding(player, BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static void consume(ServerPlayer player, ResourceLocation id) {
        if (!eligible(player) || !player.isAlive()) return;
        var state = synchronizeDay(player);
        if (state.consume(id)) {
            RewardController.maintain(player, state);
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        if (player.connection != null) PacketDistributor.sendToPlayer(player, CravingsPayload.from(player.getData(FoodMood.CRAVINGS)));
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) { FoodPool.invalidate(); FoodPool.current(); }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) RewardController.maintain(player, synchronizeDay(player));
    }

    @SubscribeEvent
    public static void finished(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var stack = event.getItem();
            var animation = stack.getUseAnimation();
            if (animation == UseAnim.EAT || animation == UseAnim.DRINK) consume(player, BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { RewardController.maintain(player, synchronizeDay(player)); sync(player); }
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { synchronizeDay(player); sync(player); }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { RewardController.maintain(player, synchronizeDay(player)); sync(player); }
    }

    private CravingService() {}
}
