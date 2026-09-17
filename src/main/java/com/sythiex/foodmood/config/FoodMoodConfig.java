package com.sythiex.foodmood.config;

import java.util.List;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class FoodMoodConfig {
    private static final ModConfigSpec.Builder SERVER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue CRAVING_COUNT = SERVER.comment("Number of food cravings assigned each day").defineInRange("cravingsPerDay", 1, 1, 64);
    public static final ModConfigSpec.BooleanValue AUTODETECT_FOODS = SERVER.comment("Automatically detect foods with positive hunger values and add them to the pool. If false, cravings will only be taken from addFoods.").define("automaticFoodDetection", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADD_LIST = SERVER.comment("Item IDs added to the pool").defineListAllowEmpty("addFoods", List.of(), () -> "minecraft:apple", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REMOVE_LIST = SERVER.comment("Item IDs excluded from the pool. Removals always take precedence over additions.").defineListAllowEmpty("removeFoods", List.of(), () -> "minecraft:rotten_flesh", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<String> REWARD_EFFECT = SERVER.comment("Effect ID of the reward. Invalid effects fall back to \"foodmood:satisfied\". Changes apply on the next in-game day.").define("rewardEffect", "foodmood:satisfied");
    public static final ModConfigSpec.IntValue REWARD_EFFECT_LEVEL = SERVER.comment("Level of the reward effect, expressed as an integer").defineInRange("rewardEffectLevel", 1, 1, 256);
    public static final ModConfigSpec.DoubleValue SATISFIED_SPEED = SERVER.comment("Satisfied effect movement speed bonus per level. 0.10 means +10%.").defineInRange("satisfiedSpeedBonus", 0.10, 0.0, 100.0);
    public static final ModConfigSpec SERVER_SPEC = SERVER.build();

    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue SHOW_CLOUD_INVENTORY = CLIENT.define("showInventoryCloud", true);
    public static final ModConfigSpec.BooleanValue SHOW_CLOUD_HUD = CLIENT.define("showHudIcon", true);
    public static final ModConfigSpec.IntValue INVENTORY_X = CLIENT.defineInRange("inventoryOffsetX", 0, -1000, 1000);
    public static final ModConfigSpec.IntValue INVENTORY_Y = CLIENT.defineInRange("inventoryOffsetY", 0, -1000, 1000);
    public static final ModConfigSpec.IntValue HUD_X = CLIENT.defineInRange("hudOffsetX", 0, -1000, 1000);
    public static final ModConfigSpec.IntValue HUD_Y = CLIENT.defineInRange("hudOffsetY", 0, -1000, 1000);
    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }

    private FoodMoodConfig() {}
}
