package com.sythiex.foodmood.effect;

import com.sythiex.foodmood.FoodMood;
import com.sythiex.foodmood.config.FoodMoodConfig;
import com.sythiex.foodmood.craving.CravingState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class RewardController {
    private static boolean internalChange;
    private static String warnedEffect;

    public static ResourceLocation configuredEffect() {
        String configured = FoodMoodConfig.REWARD_EFFECT.get();
        var id = ResourceLocation.tryParse(configured);
        if (id != null && BuiltInRegistries.MOB_EFFECT.containsKey(id) && !BuiltInRegistries.MOB_EFFECT.get(id).isInstantenous()) return id;
        if (!configured.equals(warnedEffect)) {
            FoodMood.LOGGER.warn("Reward effect '{}' is missing or instantaneous; using foodmood:satisfied.", configured);
            warnedEffect = configured;
        }
        return FoodMood.SATISFIED.getId();
    }

    public static void maintain(ServerPlayer player, CravingState state) {
        if (!state.complete() || !player.isAlive()) return;
        var holder = rewardEffect(state);
        var current = player.getEffect(holder);
        // Vanilla ticks and promotes its effect chain. Rebuild it only on first grant, after a tracked change/load, or when the visible effect is missing.
        if (state.managingReward && !state.rewardDirty && current != null) return;
        boolean firstGrant = !state.managingReward;
        if (firstGrant) {
            state.externalEffect = EffectLayers.copy(current);
            state.managingReward = true;
        }
        // CravingService expires the effect on day changes. Infinite duration is needed to let Minecraft schedule periodic effects by entity ticks even when daylight is frozen.
        var reward = new MobEffectInstance(holder, -1, state.amplifier, false, false, true);
        var desired = EffectLayers.combine(state.externalEffect, reward);
        internalChange = true;
        try {
            // Initial/reapplied rewards run the start callback (e.g. absorption)
            // Replacing a tracked effect combination should not repeat that callback
            if (firstGrant || current == null) player.addEffect(reward);
            if (current != null || state.externalEffect != null) player.forceAddEffect(desired, null);
        } finally {
            internalChange = false;
        }
        state.rewardDirty = false;
    }

    public static void expire(ServerPlayer player, CravingState state) {
        if (!state.managingReward) return;
        var holder = rewardEffect(state);
        internalChange = true;
        try {
            if (state.externalEffect != null) {
                player.forceAddEffect(EffectLayers.copy(state.externalEffect), null);
            } else {
                player.removeEffect(holder);
            }
        } finally {
            internalChange = false;
        }
        state.managingReward = false;
        state.externalEffect = null;
    }

    private static Holder<MobEffect> rewardEffect(CravingState state) {
        return BuiltInRegistries.MOB_EFFECT.getHolder(state.reward).<Holder<MobEffect>>map(value -> value).orElse(FoodMood.SATISFIED);
    }

    @SubscribeEvent
    public static void postTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var state = player.getData(FoodMood.CRAVINGS);
            // Only age the saved external contribution, vanilla handles the visible chain
            state.externalEffect = EffectLayers.age(state.externalEffect);
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            var state = event.getEntity().getData(FoodMood.CRAVINGS);
            state.externalEffect = null;
            state.managingReward = false;
            state.rewardDirty = true;
        }
    }

    @SubscribeEvent
    public static void added(MobEffectEvent.Added event) {
        if (internalChange || !(event.getEntity() instanceof ServerPlayer player)) return;
        var state = player.getData(FoodMood.CRAVINGS);
        if (!state.managingReward || !BuiltInRegistries.MOB_EFFECT.getKey(event.getEffectInstance().getEffect().value()).equals(state.reward)) return;
        if (state.externalEffect == null) state.externalEffect = EffectLayers.copy(event.getEffectInstance());
        else state.externalEffect.update(event.getEffectInstance());
        state.rewardDirty = true;
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void removed(MobEffectEvent.Remove event) {
        if (internalChange || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        var state = player.getData(FoodMood.CRAVINGS);
        if (state.managingReward && BuiltInRegistries.MOB_EFFECT.getKey(event.getEffect().value()).equals(state.reward)) {
            state.externalEffect = null;
            state.rewardDirty = true;
        }
    }

    private RewardController() {}
}
