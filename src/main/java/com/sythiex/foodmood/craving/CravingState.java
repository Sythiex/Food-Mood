package com.sythiex.foodmood.craving;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class CravingState implements INBTSerializable<CompoundTag> {
    public long day = Long.MIN_VALUE;
    private List<ResourceLocation> foods = List.of();
    private final Set<ResourceLocation> completed = new HashSet<>();
    /** Derived from progress on mutation/load, never persisted */
    private boolean complete;
    public ResourceLocation reward = ResourceLocation.fromNamespaceAndPath("foodmood", "satisfied");
    public int amplifier;
    /** The external effect is kept independently so a daily reward cannot erase or extend a potion */
    public boolean managingReward;
    public MobEffectInstance externalEffect;
    public boolean rewardDirty = true;

    public List<ResourceLocation> foods() { return foods; }
    public boolean hasCompleted(ResourceLocation food) { return completed.contains(food); }
    public int completedCount() { return completed.size(); }
    public boolean complete() { return complete; }
    public boolean outstanding(ResourceLocation food) { return !complete && foods.contains(food) && !completed.contains(food); }
    public boolean consume(ResourceLocation food) {
        if (!outstanding(food)) return false;
        completed.add(food);
        updateCompletion();
        return true;
    }

    private void updateCompletion() {
        complete = !foods.isEmpty() && completed.containsAll(foods);
    }

    /** Client snapshots contain progress only, reward ownership remains server-side */
    public static CravingState fromSnapshot(long day, List<ResourceLocation> foods, Set<ResourceLocation> completed) {
        var state = new CravingState();
        state.day = day;
        state.foods = List.copyOf(foods);
        for (var food : completed) if (state.foods.contains(food)) state.completed.add(food);
        state.updateCompletion();
        return state;
    }

    public void assign(long day, List<ResourceLocation> foods, ResourceLocation reward, int amplifier) {
        this.day = day;
        this.foods = List.copyOf(foods);
        this.completed.clear();
        this.complete = false;
        this.reward = reward;
        this.amplifier = amplifier;
        this.managingReward = false;
        this.externalEffect = null;
        this.rewardDirty = true;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putLong("day", day);
        var selected = new ListTag();
        foods.forEach(id -> selected.add(StringTag.valueOf(id.toString())));
        tag.put("foods", selected);
        var eaten = new ListTag();
        completed.forEach(id -> eaten.add(StringTag.valueOf(id.toString())));
        tag.put("completed", eaten);
        tag.putString("reward", reward.toString());
        tag.putInt("amplifier", amplifier);
        tag.putBoolean("managingReward", managingReward);
        if (externalEffect != null) tag.put("externalEffect", externalEffect.save());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        day = tag.contains("day") ? tag.getLong("day") : Long.MIN_VALUE;
        var selected = new ArrayList<ResourceLocation>();
        ListTag list = tag.getList("foods", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(list.size(), 64); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null && !selected.contains(id)) selected.add(id);
        }
        foods = List.copyOf(selected);
        completed.clear();
        list = tag.getList("completed", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (foods.contains(id)) completed.add(id);
        }
        updateCompletion();
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("reward"));
        if (id != null) reward = id;
        amplifier = Math.clamp(tag.getInt("amplifier"), 0, 255);
        managingReward = tag.getBoolean("managingReward");
        externalEffect = tag.contains("externalEffect", Tag.TAG_COMPOUND) ? MobEffectInstance.load(tag.getCompound("externalEffect")) : null;
        rewardDirty = true;
    }
}
