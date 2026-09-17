package com.sythiex.foodmood;

import com.sythiex.foodmood.craving.CravingService;
import com.sythiex.foodmood.craving.DailyCravings;
import com.sythiex.foodmood.effect.RewardController;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Development-only fixtures; excluded from the release jar. */
@EventBusSubscriber(modid = FoodMood.MODID)
public final class VisualTestCommands {
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("foodmoodtest").requires(source -> source.hasPermission(2))
            .then(Commands.literal("setup").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                var state = player.getData(FoodMood.CRAVINGS);
                RewardController.expire(player, state);
                var level = player.server.overworld();
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
                player.setGameMode(GameType.SURVIVAL);
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5);
                player.setHealth(player.getMaxHealth());
                player.getInventory().clearContent();
                player.getInventory().selected = 0;
                var foods = List.of(Items.APPLE, Items.BREAD, Items.CAKE, Items.MUSHROOM_STEW, Items.CARROT, Items.COOKIE, Items.HONEY_BOTTLE);
                state.assign(DailyCravings.day(level.getDayTime()), foods.stream().map(BuiltInRegistries.ITEM::getKey).toList(), FoodMood.SATISFIED.getId(), 0);
                for (var food : foods) player.getInventory().add(new ItemStack(food, 4));
                CravingService.sync(player);
                context.getSource().sendSuccess(() -> Component.literal("Food Mood visual fixture: 7 cravings, full hunger, frozen noon."), false);
                return 1;
            }))
            .then(Commands.literal("complete").executes(context -> {
                var player = context.getSource().getPlayerOrException();
                for (var food : List.copyOf(player.getData(FoodMood.CRAVINGS).foods())) CravingService.consume(player, food);
                return 1;
            })));
    }
}
