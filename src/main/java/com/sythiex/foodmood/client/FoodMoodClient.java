package com.sythiex.foodmood.client;

import com.sythiex.foodmood.FoodMood;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;

@Mod(value = FoodMood.MODID, dist = Dist.CLIENT)
public final class FoodMoodClient {
    public FoodMoodClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new ConfigurationScreen(mod, parent, ConfigSectionScreen::new));
    }

    private static final class ConfigSectionScreen extends ConfigurationSectionScreen {
        private ConfigSectionScreen(Screen parent, ModConfig.Type type, ModConfig config, Component title) {
            super(parent, type, config, title);
        }

        @Override
        protected Element createIntegerValue(String key, ValueSpec spec, Supplier<Integer> source, Consumer<Integer> target) {
            if (key.equals("cravingsPerDay") || key.equals("rewardEffectLevel")) {
                return createNumberBox(key, spec, source, target, null, Integer::decode, 0);
            }
            return super.createIntegerValue(key, spec, source, target);
        }
    }
}
