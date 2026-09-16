package com.sythiex.foodmood;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(FoodMood.MODID)
public class FoodMood {
    public static final String MODID = "foodmood";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FoodMood(IEventBus modEventBus, ModContainer modContainer) {
    }
}
