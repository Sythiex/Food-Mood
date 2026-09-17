package com.sythiex.foodmood.network;

import com.sythiex.foodmood.FoodMood;
import com.sythiex.foodmood.craving.CravingState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record CravingsPayload(long day, List<ResourceLocation> foods, long completed) implements CustomPacketPayload {
    public static final Type<CravingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FoodMood.MODID, "cravings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CravingsPayload> CODEC = new StreamCodec<>() {
        @Override public CravingsPayload decode(RegistryFriendlyByteBuf buffer) {
            long day = buffer.readLong();
            int count = buffer.readVarInt();
            if (count < 0 || count > 64) throw new IllegalArgumentException("Invalid craving count: " + count);
            var foods = new ArrayList<ResourceLocation>(count);
            for (int i = 0; i < count; i++) foods.add(buffer.readResourceLocation());
            return new CravingsPayload(day, List.copyOf(foods), buffer.readLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CravingsPayload payload) {
            buffer.writeLong(payload.day);
            buffer.writeVarInt(payload.foods.size());
            payload.foods.forEach(buffer::writeResourceLocation);
            buffer.writeLong(payload.completed);
        }
    };

    public static CravingsPayload from(CravingState state) {
        long mask = 0;
        for (int i = 0; i < state.foods().size(); i++) if (state.hasCompleted(state.foods().get(i))) mask |= 1L << i;
        return new CravingsPayload(state.day, state.foods(), mask);
    }

    public CravingState toState() {
        var completedFoods = new HashSet<ResourceLocation>();
        for (int i = 0; i < foods.size(); i++) if ((completed & (1L << i)) != 0) completedFoods.add(foods.get(i));
        return CravingState.fromSnapshot(day, foods, completedFoods);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> {
            context.player().setData(FoodMood.CRAVINGS, payload.toState());
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
