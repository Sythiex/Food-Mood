package com.sythiex.foodmood.craving;

import com.sythiex.foodmood.FoodMood;
import com.sythiex.foodmood.config.FoodMoodConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class FoodPool {
    private static List<ResourceLocation> candidates = List.of();
    /** Lazily scanned once per server session */
    private static List<ResourceLocation> detectedFoods;
    private static Settings lastSettings;

    public static List<ResourceLocation> current() {
        var settings = new Settings(FoodMoodConfig.AUTODETECT_FOODS.get(), List.copyOf(FoodMoodConfig.ADD_LIST.get()), List.copyOf(FoodMoodConfig.REMOVE_LIST.get()));
        if (settings.equals(lastSettings)) return candidates;
        var detected = settings.automatic ? detectedFoods() : List.<ResourceLocation>of();
        candidates = DailyCravings.pool(settings.automatic, detected, resolve(settings.added), resolve(settings.removed));
        lastSettings = settings;
        FoodMood.LOGGER.info("Food Mood craving pool contains {} foods", candidates.size());
        if (candidates.isEmpty()) FoodMood.LOGGER.warn("Food Mood craving pool is empty; daily cravings and rewards are disabled until foods are configured.");
        return candidates;
    }

    private static List<ResourceLocation> detectedFoods() {
        if (detectedFoods == null) {
            var detected = new ArrayList<ResourceLocation>();
            for (var item : BuiltInRegistries.ITEM) {
                try {
                    var food = new ItemStack(item).getFoodProperties(null);
                    if (food != null && food.nutrition() > 0) detected.add(BuiltInRegistries.ITEM.getKey(item));
                } catch (RuntimeException exception) {
                    FoodMood.LOGGER.warn("Failed to inspect item {} for food properties. Use addFoods if appropriate.", BuiltInRegistries.ITEM.getKey(item), exception);
                }
            }
            detected.add(BuiltInRegistries.ITEM.getKey(Items.CAKE));
            detectedFoods = List.copyOf(detected);
        }
        return detectedFoods;
    }

    private static List<ResourceLocation> resolve(List<? extends String> entries) {
        var result = new ArrayList<ResourceLocation>();
        for (String entry : entries) {
            var id = ResourceLocation.tryParse(entry);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR) {
                FoodMood.LOGGER.warn("Ignoring invalid or missing food item ID: {}", entry);
            } else result.add(id);
        }
        return result;
    }

    /** Reset both caches when a server starts, including switching singleplayer worlds. */
    public static void invalidate() {
        lastSettings = null;
        detectedFoods = null;
        candidates = List.of();
    }
    private record Settings(boolean automatic, List<? extends String> added, List<? extends String> removed) {}
    private FoodPool() {}
}
