package com.sythiex.foodmood;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import com.sythiex.foodmood.config.FoodMoodConfig;
import com.sythiex.foodmood.craving.CravingService;
import com.sythiex.foodmood.craving.CravingState;
import com.sythiex.foodmood.effect.SatisfiedEffect;
import com.sythiex.foodmood.network.CravingsPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(FoodMood.MODID)
public class FoodMood {
    public static final String MODID = "foodmood";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);
    public static final DeferredHolder<MobEffect, SatisfiedEffect> SATISFIED = EFFECTS.register("satisfied", SatisfiedEffect::new);
    public static final java.util.function.Supplier<AttachmentType<CravingState>> CRAVINGS = ATTACHMENTS.register("cravings",
            () -> AttachmentType.serializable(CravingState::new).copyOnDeath().build());

    public FoodMood(IEventBus modEventBus, ModContainer modContainer) {
        EFFECTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        FoodMoodConfig.register(modContainer);
        modEventBus.addListener(CravingsPayload::register);
        NeoForge.EVENT_BUS.addListener(FoodMoodCommands::register);
        NeoForge.EVENT_BUS.register(CravingService.class);
        NeoForge.EVENT_BUS.register(com.sythiex.foodmood.effect.RewardController.class);
    }
}
