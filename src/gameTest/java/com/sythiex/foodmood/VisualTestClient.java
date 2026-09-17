package com.sythiex.foodmood;

import com.sythiex.foodmood.craving.CravingService;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Repeatable client smoke test using the game's renderer and screenshot API. Excluded from the release jar. */
@EventBusSubscriber(modid = FoodMood.MODID, value = Dist.CLIENT)
public final class VisualTestClient {
    private static boolean opened;
    private static int ticks;
    private static String capture;
    private static final String WORLD = "Food Mood Visual QA";

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("foodmood.visualTest")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (!opened && mc.screen instanceof TitleScreen) {
            opened = true;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(4);
            if (Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat"))) {
                mc.createWorldOpenFlows().openWorld(WORLD, () -> { throw new IllegalStateException("Could not load visual test world"); });
            } else {
                var settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(721, false, false),
                    registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
            return;
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        ticks++;
        if (ticks == 40) {
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                mc.getSingleplayerServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), "foodmoodtest setup");
                player.teleportTo(0, -60, 0);
                player.serverLevel().setWeatherParameters(6000, 0, false, false);
                player.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, player.server);
            });
        }
        if (ticks == 45) {
            mc.options.keyUse.setDown(true);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
        if (ticks == 85) {
            mc.options.keyUse.setDown(false);
            if (!mc.player.getData(FoodMood.CRAVINGS).hasCompleted(net.minecraft.resources.ResourceLocation.withDefaultNamespace("apple"))) {
                throw new IllegalStateException("Full-hunger meal did not synchronize to the real client");
            }
        }
        if (ticks == 90) { mc.setScreen(null); capture = "foodmood-hud.png"; }
        if (ticks == 110) mc.setScreen(new InventoryScreen(mc.player));
        if (ticks == 120) capture = "foodmood-inventory.png";
        if (ticks == 140 && mc.screen instanceof InventoryScreen screen) {
            com.sythiex.foodmood.client.CravingDisplay.scroll(new ScreenEvent.MouseScrolled.Pre(screen, screen.getGuiLeft() + 51, screen.getGuiTop() - 20, 0, -1));
        }
        if (ticks == 150) capture = "foodmood-page-two.png";
        if (ticks == 160) capture = "foodmood-tooltip.png";
        if (ticks == 170 && mc.screen instanceof InventoryScreen screen) {
            screen.getRecipeBookComponent().toggleVisibility();
            screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
        if (ticks == 180) capture = "foodmood-recipe-book.png";
        if (ticks == 200) { mc.options.guiScale().set(3); mc.resizeDisplay(); }
        if (ticks == 210) capture = "foodmood-scale-three.png";
        if (ticks == 230) {
            mc.options.guiScale().set(2); mc.resizeDisplay();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var state = player.getData(FoodMood.CRAVINGS);
                CravingService.consume(player, state.foods().get(3));
            });
        }
        if (ticks == 245) capture = "foodmood-partial.png";
        if (ticks == 260) mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            mc.getSingleplayerServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), "foodmoodtest complete");
        });
        if (ticks == 280) capture = "foodmood-satisfied.png";
        if (ticks == 300) { mc.setScreen(null); capture = "foodmood-completed-hud.png"; }
        if (ticks == 330) {
            FoodMood.LOGGER.info("FOODMOOD_VISUAL_TEST_COMPLETE: screenshots saved in run/screenshots");
            mc.stop();
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void tooltip(ScreenEvent.Render.Post event) {
        if (Boolean.getBoolean("foodmood.visualTest") && ticks == 160 && event.getScreen() instanceof InventoryScreen screen) {
            // Supply a hover position to the same renderer used by the actual screen event.
            com.sythiex.foodmood.client.CravingDisplay.inventory(new ScreenEvent.Render.Post(screen, event.getGuiGraphics(),
                    screen.getGuiLeft() + 33, screen.getGuiTop() - 22, event.getPartialTick()));
        }
    }

    @SubscribeEvent
    public static void render(RenderFrameEvent.Post event) {
        if (capture == null) return;
        var mc = Minecraft.getInstance();
        String name = capture;
        capture = null;
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> FoodMood.LOGGER.info("Visual QA: {}", message.getString()));
    }
}
