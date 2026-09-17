package com.sythiex.foodmood;

import com.sythiex.foodmood.craving.CravingService;
import com.sythiex.foodmood.craving.FoodPool;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class FoodMoodCommands {
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("foodmood")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("dump").executes(context -> dump(context.getSource())))
                .then(Commands.literal("reroll")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> reroll(context.getSource(), EntityArgument.getPlayers(context, "targets"))))));
    }

    private static int dump(CommandSourceStack source) {
        var foods = FoodPool.current().stream().map(Object::toString).sorted().toList();
        var path = source.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("foodmood").resolve("cravings-pool.csv").toAbsolutePath().normalize();
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("food_id\n");
                for (var food : foods) {
                    writer.write(food);
                    writer.write('\n');
                }
            }
        } catch (IOException exception) {
            FoodMood.LOGGER.error("Failed to export the Food Mood craving pool to {}", path, exception);
            source.sendFailure(Component.literal("Could not export the craving pool to " + path + ". See the server log for details."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Exported " + foods.size() + " food IDs to " + path), false);
        return foods.size();
    }

    private static int reroll(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (var player : players) CravingService.reroll(player);
        source.sendSuccess(() -> Component.literal(players.size() == 1
                ? "Reset and rerolled cravings for " + players.iterator().next().getGameProfile().getName() + "."
                : "Reset and rerolled cravings for " + players.size() + " players."), true);
        return players.size();
    }

    private FoodMoodCommands() {}
}
