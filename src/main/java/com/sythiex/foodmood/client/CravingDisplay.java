package com.sythiex.foodmood.client;

import com.sythiex.foodmood.FoodMood;
import com.sythiex.foodmood.config.FoodMoodConfig;
import com.sythiex.foodmood.craving.CravingService;
import com.sythiex.foodmood.craving.CravingState;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = FoodMood.MODID, value = Dist.CLIENT)
public final class CravingDisplay {
    private static final ResourceLocation CLOUD = ResourceLocation.fromNamespaceAndPath(FoodMood.MODID, "textures/gui/thought_cloud.png");
    private static final ResourceLocation HUD = ResourceLocation.fromNamespaceAndPath(FoodMood.MODID, "textures/gui/thought_icon.png");
    private static int page;
    private static long shownDay = Long.MIN_VALUE;
    private static final int WIDTH = 72, HEIGHT = 42;

    private static CravingState visibleState() {
        var player = Minecraft.getInstance().player;
        if (player == null || !CravingService.eligible(player)) return null;
        var state = player.getData(FoodMood.CRAVINGS);
        return state.foods().isEmpty() || state.complete() ? null : state;
    }

    private static int left(InventoryScreen screen) {
        return Math.clamp(screen.getGuiLeft() + 51 - WIDTH / 2 + FoodMoodConfig.INVENTORY_X.get(), 0, Math.max(0, screen.width - WIDTH));
    }
    private static int top(InventoryScreen screen) {
        return Math.clamp(screen.getGuiTop() - HEIGHT - 2 + FoodMoodConfig.INVENTORY_Y.get(), 0, Math.max(0, screen.height - HEIGHT));
    }
    private static boolean inside(InventoryScreen screen, double x, double y) {
        return x >= left(screen) && x < left(screen) + WIDTH && y >= top(screen) && y < top(screen) + HEIGHT;
    }

    @SubscribeEvent
    public static void inventory(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !FoodMoodConfig.SHOW_CLOUD_INVENTORY.get()) return;
        var state = visibleState();
        if (state == null) return;
        if (shownDay != state.day) { page = 0; shownDay = state.day; }
        int pages = (state.foods().size() + 2) / 3;
        page = Math.floorMod(page, pages);
        int x = left(screen), y = top(screen);
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.blit(CLOUD, x, y, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        int count = Math.min(3, state.foods().size() - page * 3);
        for (int i = 0; i < count; i++) {
            var id = state.foods().get(page * 3 + i);
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            int iconX = x + (WIDTH - count * 18) / 2 + i * 18 + 1;
            int iconY = y + 12;
            graphics.renderItem(stack, iconX, iconY);
            boolean done = state.hasCompleted(id);
            if (done) {
                // Item models render above the GUI plane; draw the badge above their depth.
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.fill(iconX + 9, iconY + 12, iconX + 12, iconY + 15, 0xFF245344);
                graphics.fill(iconX + 11, iconY + 10, iconX + 15, iconY + 13, 0xFF245344);
                graphics.fill(iconX + 10, iconY + 12, iconX + 12, iconY + 14, 0xFF72E09B);
                graphics.fill(iconX + 12, iconY + 10, iconX + 14, iconY + 12, 0xFF72E09B);
                graphics.pose().popPose();
            }
            if (event.getMouseX() >= iconX && event.getMouseX() < iconX + 16 && event.getMouseY() >= iconY && event.getMouseY() < iconY + 16) {
                var lines = new ArrayList<Component>();
                lines.add(stack.isEmpty() ? Component.literal(id.toString()) : stack.getHoverName());
                lines.add(Component.translatable(done ? "gui.foodmood.fulfilled" : "gui.foodmood.unfulfilled"));
                if (pages > 1) lines.add(Component.translatable("gui.foodmood.scroll", page + 1, pages));
                graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, event.getMouseX(), event.getMouseY());
            }
        }
        if (pages > 1) {
            if (pages <= 7) {
                for (int i = 0; i < pages; i++) graphics.fill(x + WIDTH / 2 - pages * 3 + i * 6, y + 31, x + WIDTH / 2 - pages * 3 + i * 6 + 2, y + 33, i == page ? 0xFF496578 : 0xFF9FABB7);
            } else {
                graphics.drawCenteredString(Minecraft.getInstance().font, (page + 1) + "/" + pages, x + WIDTH / 2, y + 29, 0xFFEAF3FB);
            }
        }
        graphics.pose().popPose();
    }

    @SubscribeEvent
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !FoodMoodConfig.SHOW_CLOUD_INVENTORY.get()) return;
        var state = visibleState();
        if (state != null && state.foods().size() > 3 && inside(screen, event.getMouseX(), event.getMouseY()) && event.getScrollDeltaY() != 0) {
            page = Math.floorMod(page + (event.getScrollDeltaY() < 0 ? 1 : -1), (state.foods().size() + 2) / 3);
            event.setCanceled(true);
        }
    }

    @EventBusSubscriber(modid = FoodMood.MODID, value = Dist.CLIENT)
    public static final class Layers {
        @SubscribeEvent
        public static void register(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, ResourceLocation.fromNamespaceAndPath(FoodMood.MODID, "cravings"), (graphics, delta) -> {
                var minecraft = Minecraft.getInstance();
                if (!FoodMoodConfig.SHOW_CLOUD_HUD.get() || minecraft.options.hideGui || visibleState() == null || minecraft.gameMode == null
                        || !minecraft.gameMode.canHurtPlayer() || minecraft.player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity) return;
                int x = Math.clamp(graphics.guiWidth() / 2 + 94 + FoodMoodConfig.HUD_X.get(), 0, graphics.guiWidth() - 13);
                int y = Math.clamp(graphics.guiHeight() - 40 + FoodMoodConfig.HUD_Y.get(), 0, graphics.guiHeight() - 11);
                graphics.blit(HUD, x, y, 0, 0, 13, 11, 13, 11);
            });
        }
    }

    private CravingDisplay() {}
}
