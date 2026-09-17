package com.sythiex.foodmood;

import com.mojang.authlib.GameProfile;
import com.sythiex.foodmood.config.FoodMoodConfig;
import com.sythiex.foodmood.craving.*;
import com.sythiex.foodmood.effect.*;
import com.sythiex.foodmood.network.CravingsPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FoodMood.MODID)
@PrefixGameTestTemplate(false)
public final class FoodMoodGameTests {
    static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "FoodMoodTest"));
            setGameMode(GameType.SURVIVAL);
        }
        void finishMeal() { completeUsingItem(); }
        void tickStatusEffects() { tickEffects(); }
    }

    private static ResourceLocation id(Item item) { return BuiltInRegistries.ITEM.getKey(item); }

    @GameTest(template = "empty")
    public static void rerollCommandResetsProgressAndPreservesExternalEffects(GameTestHelper helper) throws Exception {
        var player = new TestPlayer(helper.getLevel());
        var state = assign(player, Items.APPLE);
        state.reward = ResourceLocation.withDefaultNamespace("speed");
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
        meal(player, Items.APPLE);
        helper.assertTrue(state.complete() && state.managingReward, "Fixture must have an earned reward");
        var dispatcher = player.server.getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        int result = dispatcher.execute("foodmood reroll @s", source);
        helper.assertTrue(result == 1 && !state.complete() && state.completedCount() == 0 && !state.managingReward,
                "Reroll must clear progress and reward ownership on the same day");
        helper.assertTrue(state.day == DailyCravings.day(player.server.overworld().getDayTime())
                && state.foods().size() == Math.min(FoodMoodConfig.CRAVING_COUNT.get(), FoodPool.current().size())
                && FoodPool.current().containsAll(state.foods()), "Reroll must use today's configured pool and count");
        var external = player.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(external != null && external.getDuration() == 600 && external.getAmplifier() == 1,
                "Reroll must preserve the independent potion's level and remaining duration");
        var selection = state.foods();
        CravingService.synchronizeDay(player);
        helper.assertTrue(state.foods() == selection, "Next tick must retain the manual reroll");
        try {
            dispatcher.execute("foodmood reroll @s", source.withPermission(0));
            helper.fail("Non-operators must not be able to reroll cravings");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rerollCommandTargetsNamedPlayerOrEveryone(GameTestHelper helper) throws Exception {
        var first = new TestPlayer(helper.getLevel());
        var second = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "OtherFoodTest"));
        var firstState = assign(first, Items.APPLE);
        var secondState = second.getData(FoodMood.CRAVINGS);
        secondState.assign(firstState.day, List.of(id(Items.APPLE)), FoodMood.SATISFIED.getId(), 0);
        firstState.consume(id(Items.APPLE));
        secondState.consume(id(Items.APPLE));
        RewardController.maintain(first, firstState);
        RewardController.maintain(second, secondState);
        var server = helper.getLevel().getServer();
        // Fake players have no login handshake. Add them to the backing roster only
        // for this synchronous selector test; the public roster is read-only.
        var roster = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
        roster.setAccessible(true);
        @SuppressWarnings("unchecked")
        var players = (List<net.minecraft.server.level.ServerPlayer>) roster.get(server.getPlayerList());
        players.add(first);
        players.add(second);
        try {
            var dispatcher = server.getCommands().getDispatcher();
            var source = server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            helper.assertTrue(dispatcher.execute("foodmood reroll FoodMoodTest", source) == 1,
                    "Named target must reroll exactly one player");
            helper.assertTrue(!firstState.complete() && secondState.complete(), "Named reroll must leave other players alone");
            helper.assertTrue(!first.hasEffect(FoodMood.SATISFIED), "Reroll must remove an earned reward without an external potion");
            for (var food : firstState.foods()) firstState.consume(food);
            helper.assertTrue(dispatcher.execute("foodmood reroll @a", source) == players.size(), "All-player selector must reroll everyone online");
            helper.assertTrue(firstState.completedCount() == 0 && secondState.completedCount() == 0
                    && !second.hasEffect(FoodMood.SATISFIED), "All targets must lose progress and earned rewards");
        } finally {
            players.remove(first);
            players.remove(second);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dumpCommandExportsConfiguredPoolAndEmptyPool(GameTestHelper helper) throws Exception {
        var auto = FoodMoodConfig.AUTODETECT_FOODS.get();
        var add = FoodMoodConfig.ADD_LIST.get();
        var remove = FoodMoodConfig.REMOVE_LIST.get();
        try {
            FoodMoodConfig.AUTODETECT_FOODS.set(false);
            FoodMoodConfig.ADD_LIST.set(List.of("minecraft:carrot", "minecraft:bread", "minecraft:apple", "minecraft:apple"));
            FoodMoodConfig.REMOVE_LIST.set(List.of("minecraft:bread"));
            var server = helper.getLevel().getServer();
            var dispatcher = server.getCommands().getDispatcher();
            var source = server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            helper.assertTrue(dispatcher.execute("foodmood dump", source) == 2, "Dump must report the exported count");
            var path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("foodmood/cravings-pool.csv");
            helper.assertTrue(java.nio.file.Files.readAllLines(path).equals(List.of("food_id", "minecraft:apple", "minecraft:carrot")),
                    "CSV must contain sorted unique IDs from the configured pool with a header");
            FoodMoodConfig.ADD_LIST.set(List.of());
            dispatcher.execute("foodmood dump", source);
            helper.assertTrue(java.nio.file.Files.readAllLines(path).equals(List.of("food_id")),
                    "An empty pool must replace the previous export with a header-only CSV");
        } finally {
            FoodMoodConfig.AUTODETECT_FOODS.set(auto);
            FoodMoodConfig.ADD_LIST.set(add);
            FoodMoodConfig.REMOVE_LIST.set(remove);
            FoodPool.invalidate();
        }
        helper.succeed();
    }

    private static CravingState assign(TestPlayer player, Item... foods) {
        var state = player.getData(FoodMood.CRAVINGS);
        state.assign(DailyCravings.day(player.server.overworld().getDayTime()), java.util.Arrays.stream(foods).map(FoodMoodGameTests::id).toList(), FoodMood.SATISFIED.getId(), 0);
        return state;
    }
    private static void meal(TestPlayer player, Item food) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(food));
        player.getItemInHand(InteractionHand.MAIN_HAND).use(player.level(), player, InteractionHand.MAIN_HAND);
        player.finishMeal();
    }

    private static void tickRewardEffects(TestPlayer player, int ticks) {
        for (int i = 0; i < ticks; i++) {
            player.tickCount++;
            CravingService.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player));
            player.tickStatusEffects();
            RewardController.postTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        }
    }

    @GameTest(template = "empty")
    public static void detectsFoodsAndResolvesConfiguration(GameTestHelper helper) {
        var auto = FoodMoodConfig.AUTODETECT_FOODS.get();
        var add = FoodMoodConfig.ADD_LIST.get();
        var remove = FoodMoodConfig.REMOVE_LIST.get();
        try {
            FoodMoodConfig.AUTODETECT_FOODS.set(true);
            FoodMoodConfig.ADD_LIST.set(List.of());
            FoodMoodConfig.REMOVE_LIST.set(List.of());
            FoodPool.invalidate();
            var pool = FoodPool.current();
            for (Item item : List.of(Items.APPLE, Items.MUSHROOM_STEW, Items.HONEY_BOTTLE, Items.ROTTEN_FLESH, Items.CAKE)) helper.assertTrue(pool.contains(id(item)), "Missing food: " + id(item));
            helper.assertTrue(!pool.contains(id(Items.MILK_BUCKET)) && !pool.contains(id(Items.STONE)), "Non-food detected");
            FoodMoodConfig.REMOVE_LIST.set(List.of("minecraft:apple"));
            helper.assertTrue(!FoodPool.current().contains(id(Items.APPLE)), "Updated exclusions must apply to detected foods");
            FoodMoodConfig.REMOVE_LIST.set(List.of());
            helper.assertTrue(FoodPool.current().equals(pool), "Removing exclusions must restore the full detected pool");
            FoodMoodConfig.AUTODETECT_FOODS.set(false);
            FoodMoodConfig.ADD_LIST.set(List.of("minecraft:apple", "minecraft:apple", "minecraft:milk_bucket", "missing:food", "invalid id"));
            FoodMoodConfig.REMOVE_LIST.set(List.of("minecraft:apple"));
            helper.assertTrue(FoodPool.current().equals(List.of(id(Items.MILK_BUCKET))), "Manual list/exclusion precedence");
            FoodMoodConfig.ADD_LIST.set(List.of());
            helper.assertTrue(FoodPool.current().isEmpty(), "Empty pool should stay empty");
            FoodMoodConfig.AUTODETECT_FOODS.set(true);
            FoodMoodConfig.REMOVE_LIST.set(List.of());
            helper.assertTrue(FoodPool.current().equals(pool), "Re-enabling detection must restore the detected pool without manual additions");
        } finally {
            FoodMoodConfig.AUTODETECT_FOODS.set(auto); FoodMoodConfig.ADD_LIST.set(add); FoodMoodConfig.REMOVE_LIST.set(remove); FoodPool.invalidate();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullHungerCancellationAndContainerFoods(GameTestHelper helper) {
        var player = new TestPlayer(helper.getLevel());
        var state = assign(player, Items.APPLE, Items.MUSHROOM_STEW);
        var apple = new ItemStack(Items.APPLE);
        helper.assertTrue(apple.getFoodProperties(player).canAlwaysEat(), "Outstanding craving must allow full hunger");
        helper.assertTrue(!apple.getFoodProperties(null).canAlwaysEat(), "Do not mutate the food stack or registry");
        helper.assertTrue(!new ItemStack(Items.BREAD).getFoodProperties(player).canAlwaysEat(), "Uncraved food must remain normal");
        player.setItemInHand(InteractionHand.MAIN_HAND, apple);
        apple.use(player.level(), player, InteractionHand.MAIN_HAND);
        player.releaseUsingItem();
        helper.assertTrue(state.completedCount() == 0, "Canceled eating must not count");
        meal(player, Items.APPLE);
        helper.assertTrue(state.hasCompleted(id(Items.APPLE)), "Finished meal did not count");
        helper.assertTrue(!player.hasEffect(FoodMood.SATISFIED), "Partial completion must not reward");
        helper.assertTrue(!new ItemStack(Items.APPLE).getFoodProperties(player).canAlwaysEat(), "Fulfilled food should no longer bypass hunger");
        meal(player, Items.MUSHROOM_STEW);
        helper.assertTrue(player.getMainHandItem().is(Items.BOWL), "Bowl remainder lost");
        helper.assertTrue(state.complete() && player.hasEffect(FoodMood.SATISFIED), "All foods should reward immediately");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.11) < 0.00001, "Expected default 10% speed bonus");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cakeAndCandleCakeCountActualBites(GameTestHelper helper) {
        var player = new TestPlayer(helper.getLevel());
        var state = assign(player, Items.CAKE);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var level = helper.getLevel();
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, Blocks.CAKE.defaultBlockState(), 3);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        level.getBlockState(pos).useWithoutItem(level, player, hit);
        helper.assertTrue(state.complete() && level.getBlockState(pos).getValue(CakeBlock.BITES) == 1, "One full-hunger cake bite must count");
        level.getBlockState(pos).useWithoutItem(level, player, hit);
        helper.assertTrue(level.getBlockState(pos).getValue(CakeBlock.BITES) == 1, "Completed cake craving must not allow another full-hunger bite");
        RewardController.expire(player, state);
        state = assign(player, Items.CAKE);
        level.setBlock(pos, Blocks.CANDLE_CAKE.defaultBlockState(), 3);
        level.getBlockState(pos).useWithoutItem(level, player, hit);
        helper.assertTrue(state.complete() && level.getBlockState(pos).is(Blocks.CAKE), "Candle cake must count as cake");
        RewardController.expire(player, state);
        state = assign(player, Items.CAKE);
        level.setBlock(pos, Blocks.CAKE.defaultBlockState().setValue(CakeBlock.BITES, CakeBlock.MAX_BITES), 3);
        level.getBlockState(pos).useWithoutItem(level, player, hit);
        helper.assertTrue(state.complete() && level.getBlockState(pos).isAir(), "The final full-hunger cake slice must count and remove the cake");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void satisfiedPreservesBothRegenerationCosts(GameTestHelper helper) {
        var player = new TestPlayer(helper.getLevel());
        player.addEffect(new MobEffectInstance(FoodMood.SATISFIED, 24000));
        var food = player.getFoodData();
        for (int i = 0; i < 100; i++) { player.causeFoodExhaustion(4); food.addExhaustion(4); food.tick(player); }
        helper.assertTrue(food.getFoodLevel() == 20 && food.getSaturationLevel() == 5 && food.getExhaustionLevel() == 0, "Ordinary exhaustion should be blocked");
        player.setHealth(10);
        food.setSaturation(6);
        for (int i = 0; i < 11; i++) food.tick(player);
        helper.assertTrue(player.getHealth() > 10 && food.getSaturationLevel() < 6, "Fast healing must still cost saturation");
        food.setFoodLevel(18); food.setSaturation(0); food.setExhaustion(0); player.setHealth(10);
        for (int i = 0; i < 81; i++) food.tick(player);
        helper.assertTrue(player.getHealth() > 10 && food.getFoodLevel() == 17, "Slow healing must still cost hunger");
        player.removeEffect(FoodMood.SATISFIED);
        food.setExhaustion(0);
        player.causeFoodExhaustion(5);
        helper.assertTrue(food.getExhaustionLevel() == 5, "Normal exhaustion must return after effect removal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void milkDeathPersistenceAndIndependentPlayers(GameTestHelper helper) {
        var player = new TestPlayer(helper.getLevel());
        var other = new TestPlayer(helper.getLevel());
        var state = assign(player, Items.APPLE, Items.BREAD);
        assign(other, Items.APPLE, Items.BREAD);
        meal(player, Items.APPLE);
        helper.assertTrue(other.getData(FoodMood.CRAVINGS).completedCount() == 0, "Progress leaked to another player");
        var saved = state.serializeNBT(player.registryAccess());
        var restored = new CravingState();
        restored.deserializeNBT(player.registryAccess(), saved);
        player.setData(FoodMood.CRAVINGS, restored);
        CravingService.synchronizeDay(player);
        helper.assertTrue(restored.hasCompleted(id(Items.APPLE)), "Reconnect/save must retain progress");
        meal(player, Items.BREAD);
        new ItemStack(Items.MILK_BUCKET).finishUsingItem(player.level(), player);
        helper.assertTrue(!player.hasEffect(FoodMood.SATISFIED), "Milk should have removed the visible instance before reconciliation");
        RewardController.maintain(player, restored);
        helper.assertTrue(player.hasEffect(FoodMood.SATISFIED), "Daily reward must return after milk");
        var respawned = new TestPlayer(helper.getLevel());
        respawned.restoreFrom(player, false);
        var copied = respawned.getData(FoodMood.CRAVINGS);
        helper.assertTrue(copied.complete() && copied != restored, "Death must copy the attachment independently");
        RewardController.maintain(respawned, copied);
        helper.assertTrue(respawned.hasEffect(FoodMood.SATISFIED), "Death must retain the earned reward");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dawnAndTimeChangesExpireReward(GameTestHelper helper) {
        var level = helper.getLevel().getServer().overworld();
        long original = level.getDayTime();
        try {
            level.setDayTime(23999);
            var player = new TestPlayer(helper.getLevel());
            var state = assign(player, Items.APPLE);
            meal(player, Items.APPLE);
            helper.assertTrue(player.hasEffect(FoodMood.SATISFIED), "Reward must remain active just before dawn");
            level.setDayTime(24000);
            CravingService.synchronizeDay(player);
            helper.assertTrue(!player.hasEffect(FoodMood.SATISFIED) && state.completedCount() == 0 && state.day == 1, "Dawn must expire and reroll");
            var foods = state.foods();
            level.setDayTime(25000);
            CravingService.synchronizeDay(player);
            helper.assertTrue(state.foods() == foods, "Same-day time change must not reroll");
            level.setDayTime(0);
            CravingService.synchronizeDay(player);
            helper.assertTrue(state.day == 0, "Backward day change must reconcile once");
            assign(player, Items.APPLE);
            meal(player, Items.APPLE);
            for (int i = 0; i < 40; i++) {
                player.getEffect(FoodMood.SATISFIED).tick(player, () -> {});
                RewardController.maintain(player, player.getData(FoodMood.CRAVINGS));
            }
            helper.assertTrue(player.hasEffect(FoodMood.SATISFIED), "Frozen daylight must preserve reward until dawn");
            level.setDayTime(24000 * 8L);
            CravingService.synchronizeDay(player);
            helper.assertTrue(player.getData(FoodMood.CRAVINGS).day == 8 && !player.hasEffect(FoodMood.SATISFIED), "Skipped days must produce only the current day");
        } finally { level.setDayTime(original); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void frozenDaylightPreservesRewardRegenerationCadence(GameTestHelper helper) {
        var level = helper.getLevel().getServer().overworld();
        long original = level.getDayTime();
        try {
            // Exercise both a deadline divisible by the healing interval and one outside it.
            for (long frozenTime : new long[] {6000, 6001}) {
                level.setDayTime(frozenTime);
                var player = new TestPlayer(helper.getLevel());
                var state = assign(player, Items.APPLE);
                state.reward = ResourceLocation.withDefaultNamespace("regeneration");
                meal(player, Items.APPLE);
                player.setHealth(5);
                // Tick the real effect with the player lifecycle hooks, excluding food regeneration.
                for (int tick = 1; tick <= 100; tick++) {
                    player.tickCount = tick;
                    CravingService.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player));
                    player.getEffect(MobEffects.REGENERATION).tick(player, () -> {});
                    RewardController.postTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
                }
                helper.assertTrue(player.getHealth() == 7, "Regeneration I must heal 2 HP in 100 ticks at frozen time " + frozenTime + ", got " + player.getHealth());
                // Resuming the day clock must not alter the healing cadence.
                for (int tick = 101; tick <= 200; tick++) {
                    level.setDayTime(frozenTime + tick - 100);
                    player.tickCount = tick;
                    CravingService.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player));
                    player.getEffect(MobEffects.REGENERATION).tick(player, () -> {});
                    RewardController.postTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
                }
                helper.assertTrue(player.getHealth() == 9, "Resumed daylight must preserve Regeneration I cadence");
                level.setDayTime(24000);
                CravingService.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player));
                helper.assertTrue(!player.hasEffect(MobEffects.REGENERATION), "Periodic reward must still expire at dawn");
            }
        } finally { level.setDayTime(original); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void externalEffectsAreNeitherErasedNorExtended(GameTestHelper helper) {
        var player = new TestPlayer(helper.getLevel());
        var state = assign(player, Items.APPLE);
        state.reward = ResourceLocation.withDefaultNamespace("speed");
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1000, 2));
        meal(player, Items.APPLE);
        helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 2, "Stronger external effect should win");
        for (int i = 0; i < 10; i++) state.externalEffect = EffectLayers.age(state.externalEffect);
        RewardController.expire(player, state);
        helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() == 990, "Dawn must preserve external remaining duration");
        state = assign(player, Items.APPLE);
        state.reward = ResourceLocation.withDefaultNamespace("speed");
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        meal(player, Items.APPLE);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0));
        RewardController.maintain(player, state);
        RewardController.expire(player, state);
        helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() == 100, "Same-level external potion must not inherit reward duration");
        state = assign(player, Items.APPLE);
        state.reward = ResourceLocation.withDefaultNamespace("speed");
        state.amplifier = 2;
        meal(player, Items.APPLE);
        var saved = state.serializeNBT(player.registryAccess());
        var restored = new CravingState(); restored.deserializeNBT(player.registryAccess(), saved);
        player.setData(FoodMood.CRAVINGS, restored);
        RewardController.expire(player, restored);
        helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 0, "Weaker potion must survive save/reload beneath stronger reward");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void completionCacheFollowsProgressAndSavedState(GameTestHelper helper) {
        var state = new CravingState();
        var apple = id(Items.APPLE);
        var bread = id(Items.BREAD);
        helper.assertTrue(!state.complete(), "New state must not be complete");
        var foods = new java.util.ArrayList<>(List.of(apple, bread));
        state.assign(1, foods, FoodMood.SATISFIED.getId(), 0);
        foods.clear();
        helper.assertTrue(state.foods().size() == 2, "Assignment must copy its food list");
        try {
            state.foods().clear();
            helper.fail("Food selection must be read-only");
        } catch (UnsupportedOperationException expected) { }
        helper.assertTrue(!state.consume(id(Items.STONE)) && !state.complete(), "Unknown food must not advance progress");
        helper.assertTrue(state.consume(apple) && !state.complete(), "Partial completion must not set the cache");
        helper.assertTrue(!state.consume(apple) && state.completedCount() == 1, "Duplicate consumption must not advance progress");
        var provider = helper.getLevel().registryAccess();
        var partial = state.serializeNBT(provider);
        helper.assertTrue(state.consume(bread) && state.complete(), "Last craving must set the cache");
        var complete = state.serializeNBT(provider);
        state.assign(2, List.of(apple), FoodMood.SATISFIED.getId(), 0);
        helper.assertTrue(!state.complete() && state.completedCount() == 0, "Assignment must reset cached completion");
        state.deserializeNBT(provider, complete);
        helper.assertTrue(state.complete(), "Loading completed progress must rebuild the cache");
        state.deserializeNBT(provider, partial);
        helper.assertTrue(!state.complete() && state.hasCompleted(apple), "Loading partial progress must clear a stale true cache");
        state.consume(bread);
        state.deserializeNBT(provider, new net.minecraft.nbt.CompoundTag());
        helper.assertTrue(!state.complete() && state.foods().isEmpty(), "Loading empty progress must clear the cache");
        state.assign(3, List.of(apple), FoodMood.SATISFIED.getId(), 0);
        state.consume(apple);
        state.assign(4, List.of(), FoodMood.SATISFIED.getId(), 0);
        helper.assertTrue(!state.complete(), "An empty assignment must not grant a reward");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void payloadRetainsEveryCompletionBit(GameTestHelper helper) {
        var state = new CravingState();
        var ids = new java.util.ArrayList<ResourceLocation>();
        for (int i = 0; i < 64; i++) ids.add(ResourceLocation.fromNamespaceAndPath("test", "food_" + i));
        state.assign(19, ids, FoodMood.SATISFIED.getId(), 0);
        state.consume(ids.get(0)); state.consume(ids.get(63));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var payload = CravingsPayload.from(state);
            CravingsPayload.CODEC.encode(buffer, payload);
            var decoded = CravingsPayload.CODEC.decode(buffer);
            helper.assertTrue(decoded.equals(payload) && decoded.completed() == (1L | Long.MIN_VALUE), "Network snapshot must preserve all 64 slots");
            var clientState = decoded.toState();
            helper.assertTrue(!clientState.complete() && clientState.completedCount() == 2
                    && clientState.hasCompleted(ids.get(0)) && clientState.hasCompleted(ids.get(63)), "Partial snapshot must rebuild progress and cache");
            for (var food : ids) state.consume(food);
            CravingsPayload.CODEC.encode(buffer, CravingsPayload.from(state));
            clientState = CravingsPayload.CODEC.decode(buffer).toState();
            helper.assertTrue(clientState.complete() && clientState.completedCount() == 64, "Full snapshot must set cached completion including bit 63");
            helper.assertTrue(!new CravingsPayload(20, List.of(), -1L).toState().complete(), "Empty snapshot must not be complete even with set mask bits");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rewardMaintenanceTrustsPresentEffectsAndRestoresMissingOnes(GameTestHelper helper) {
        var player = new TestPlayer(helper.getLevel());
        var state = assign(player, Items.APPLE);
        meal(player, Items.APPLE);
        var initial = player.getEffect(FoodMood.SATISFIED);
        for (int i = 0; i < 100; i++) RewardController.maintain(player, state);
        helper.assertTrue(player.getEffect(FoodMood.SATISFIED) == initial, "Stable maintenance must retain the active effect");
        // Silent replacements are deliberately trusted until an event or missing effect triggers reconciliation.
        var replacement = new MobEffectInstance(FoodMood.SATISFIED, 5, 1);
        player.forceAddEffect(replacement, null);
        RewardController.maintain(player, state);
        helper.assertTrue(player.getEffect(FoodMood.SATISFIED) == replacement, "Presence check must not compare or replace an untracked effect");
        tickRewardEffects(player, 6);
        var restored = player.getEffect(FoodMood.SATISFIED);
        helper.assertTrue(restored != null && restored.isInfiniteDuration() && restored.getAmplifier() == 0, "Expired replacement must restore the daily reward next tick");
        player.removeEffectNoUpdate(FoodMood.SATISFIED);
        RewardController.maintain(player, state);
        helper.assertTrue(player.hasEffect(FoodMood.SATISFIED), "Presence check must restore a reward even without a removal event");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void externalEffectsUseVanillaFallbackAndKeepTheirDeadline(GameTestHelper helper) {
        var player = new TestPlayer(helper.getLevel());
        var state = assign(player, Items.APPLE);
        state.reward = ResourceLocation.withDefaultNamespace("speed");
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 2));
        meal(player, Items.APPLE);
        var visible = player.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(visible.getAmplifier() == 2, "Initial grant must preserve the stronger external effect");
        tickRewardEffects(player, 60);
        helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED) == visible && visible.isInfiniteDuration() && visible.getAmplifier() == 0,
                "Vanilla must promote the daily reward within the existing instance");
        helper.assertTrue(!state.rewardDirty && state.externalEffect.getDuration() == 100 && state.externalEffect.getAmplifier() == 0,
                "Saved external chain must age independently without marking ordinary ticks dirty");

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 1));
        helper.assertTrue(state.rewardDirty, "Adding a same-type potion must schedule reconciliation");
        RewardController.maintain(player, state);
        helper.assertTrue(!state.rewardDirty && player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1,
                "Reconciliation must process and clear the pending effect change");
        tickRewardEffects(player, 25);
        helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).isInfiniteDuration()
                && player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 0, "Reward must resume after the new potion expires");

        var loaded = new CravingState();
        loaded.deserializeNBT(player.registryAccess(), state.serializeNBT(player.registryAccess()));
        player.setData(FoodMood.CRAVINGS, loaded);
        player.forceAddEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 5, 2), null);
        RewardController.maintain(player, loaded);
        helper.assertTrue(!loaded.rewardDirty && player.getEffect(MobEffects.MOVEMENT_SPEED).isInfiniteDuration()
                && player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 0, "Loading saved state must force reconciliation despite an existing effect");
        RewardController.expire(player, loaded);
        helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() == 75
                && player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 0, "Rollover must restore the external effect with only its own remaining duration");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dawnPreservesExternalAbsorptionHearts(GameTestHelper helper) {
        var level = helper.getLevel().getServer().overworld();
        long original = level.getDayTime();
        try {
            for (boolean external : new boolean[] {true, false}) {
                for (int rewardAmplifier : new int[] {0, 1}) {
                    level.setDayTime(23990);
                    var player = new TestPlayer(helper.getLevel());
                    var state = assign(player, Items.APPLE);
                    state.reward = ResourceLocation.withDefaultNamespace("absorption");
                    state.amplifier = rewardAmplifier;
                    if (external) player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1000, 0));
                    meal(player, Items.APPLE);
                    // Partially spent hearts must survive without refilling. Excess hearts from
                    // a stronger reward must instead clamp to the surviving effect's maximum.
                    player.setAbsorptionAmount(rewardAmplifier == 0 ? 2 : 6);
                    for (int tick = 0; tick < 10; tick++) {
                        level.setDayTime(23990 + tick);
                        tickRewardEffects(player, 1);
                    }
                    level.setDayTime(24000);
                    CravingService.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player));
                    helper.assertTrue(state.day == 1 && !state.managingReward, "Dawn must expire the old reward entitlement");
                    var remaining = player.getEffect(MobEffects.ABSORPTION);
                    if (external) {
                        helper.assertTrue(remaining != null && remaining.getAmplifier() == 0 && remaining.getDuration() == 990,
                                "Dawn must restore the external absorption with its own remaining duration");
                        float expectedHearts = rewardAmplifier == 0 ? 2 : 4;
                        helper.assertTrue(player.getAbsorptionAmount() == expectedHearts,
                                "Dawn must preserve or clamp remaining absorption hearts, expected " + expectedHearts + ", got " + player.getAbsorptionAmount());
                        tickRewardEffects(player, 1);
                        helper.assertTrue(player.hasEffect(MobEffects.ABSORPTION) && remaining.getDuration() == 989
                                && player.getAbsorptionAmount() == expectedHearts, "Restored absorption must survive and tick normally the next day");
                    } else {
                        helper.assertTrue(remaining == null && player.getAbsorptionAmount() == 0,
                                "Reward-only absorption and its hearts must be removed at dawn");
                    }
                }
            }
        } finally { level.setDayTime(original); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeAndSpectatorDoNotAdvanceCravings(GameTestHelper helper) {
        var player = new TestPlayer(helper.getLevel());
        var state = assign(player, Items.APPLE);
        player.setGameMode(GameType.CREATIVE);
        CravingService.consume(player, id(Items.APPLE));
        helper.assertTrue(state.completedCount() == 0, "Creative mode must not fulfill cravings");
        player.setGameMode(GameType.SPECTATOR);
        CravingService.consume(player, id(Items.APPLE));
        helper.assertTrue(state.completedCount() == 0, "Spectator must not fulfill cravings");
        player.setGameMode(GameType.SURVIVAL);
        CravingService.consume(player, id(Items.APPLE));
        helper.assertTrue(state.complete(), "Survival must restore completion");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void configurableRewardLevelSpeedAndInitialCallbacks(GameTestHelper helper) {
        var previousSpeed = FoodMoodConfig.SATISFIED_SPEED.get();
        var previousEffect = FoodMoodConfig.REWARD_EFFECT.get();
        var previousCount = FoodMoodConfig.CRAVING_COUNT.get();
        var previousLevel = FoodMoodConfig.REWARD_EFFECT_LEVEL.get();
        try {
            FoodMoodConfig.REWARD_EFFECT.set("missing:effect");
            helper.assertTrue(RewardController.configuredEffect().equals(FoodMood.SATISFIED.getId()), "Missing reward must fall back");
            FoodMoodConfig.REWARD_EFFECT.set("minecraft:instant_health");
            helper.assertTrue(RewardController.configuredEffect().equals(FoodMood.SATISFIED.getId()), "Instantaneous reward must fall back");
            FoodMoodConfig.REWARD_EFFECT.set("minecraft:haste");
            FoodMoodConfig.CRAVING_COUNT.set(3);
            FoodMoodConfig.REWARD_EFFECT_LEVEL.set(2);
            var player = new TestPlayer(helper.getLevel());
            var state = CravingService.synchronizeDay(player);
            helper.assertTrue(state.foods().size() == 3 && state.amplifier == 1 && state.reward.equals(ResourceLocation.withDefaultNamespace("haste")), "Configurable assignment settings");
            FoodMoodConfig.CRAVING_COUNT.set(5); FoodMoodConfig.REWARD_EFFECT_LEVEL.set(4);
            CravingService.synchronizeDay(player);
            helper.assertTrue(state.foods().size() == 3 && state.amplifier == 1, "Do not change an active assignment on config reload");
            state = assign(player, Items.APPLE);
            FoodMoodConfig.SATISFIED_SPEED.set(0.25);
            state.amplifier = 1;
            meal(player, Items.APPLE);
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.15) < 0.00001, "Configured speed scales by level");
            RewardController.expire(player, state);
            state = assign(player, Items.APPLE);
            state.reward = ResourceLocation.withDefaultNamespace("absorption");
            meal(player, Items.APPLE);
            helper.assertTrue(player.getAbsorptionAmount() == 4, "Initial configurable reward must run effect start callback");
            player.setAbsorptionAmount(2);
            RewardController.maintain(player, state);
            helper.assertTrue(player.getAbsorptionAmount() == 2, "Maintenance must not repeatedly refill absorption");
        } finally {
            FoodMoodConfig.SATISFIED_SPEED.set(previousSpeed); FoodMoodConfig.REWARD_EFFECT.set(previousEffect);
            FoodMoodConfig.CRAVING_COUNT.set(previousCount); FoodMoodConfig.REWARD_EFFECT_LEVEL.set(previousLevel);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dimensionClockAndEmptyPoolDoNotGrantFreeRewards(GameTestHelper helper) {
        var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        var player = new TestPlayer(nether);
        var state = CravingService.synchronizeDay(player);
        helper.assertTrue(state.day == DailyCravings.day(player.server.overworld().getDayTime()), "Nether players must use the Overworld clock");
        state.assign(state.day, List.of(), FoodMood.SATISFIED.getId(), 0);
        RewardController.maintain(player, state);
        helper.assertTrue(!state.complete() && !player.hasEffect(FoodMood.SATISFIED), "Empty pool must not grant a reward");
        helper.succeed();
    }
}
