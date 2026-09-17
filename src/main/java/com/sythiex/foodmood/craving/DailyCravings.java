package com.sythiex.foodmood.craving;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/** daily selection rules, shared by persistence and tests. */
public final class DailyCravings {
    public static long day(long time) { return Math.floorDiv(time, 24_000L); }
    public static int remainingTicks(long time) { return 24_000 - (int) Math.floorMod(time, 24_000L); }

    public static <T> List<T> select(List<T> pool, int count, Random random) {
        var candidates = new ArrayList<>(new LinkedHashSet<>(pool));
        // partial Fisher-Yates
        int size = Math.min(Math.max(count, 0), candidates.size());
        for (int i = 0; i < size; i++) {
            int j = i + random.nextInt(candidates.size() - i);
            T old = candidates.set(i, candidates.get(j));
            candidates.set(j, old);
        }
        return List.copyOf(candidates.subList(0, size));
    }

    public static <T> List<T> pool(boolean automatic, List<T> detected, List<T> added, List<T> removed) {
        var result = new LinkedHashSet<T>();
        if (automatic) result.addAll(detected);
        result.addAll(added);
        result.removeAll(removed);
        return List.copyOf(result);
    }

    private DailyCravings() {}
}
