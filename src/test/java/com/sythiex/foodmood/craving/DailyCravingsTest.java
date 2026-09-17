package com.sythiex.foodmood.craving;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DailyCravingsTest {
    @Test void dayBoundaryAndFrozenClock() {
        assertEquals(0, DailyCravings.day(0));
        assertEquals(0, DailyCravings.day(23999));
        assertEquals(1, DailyCravings.day(24000));
        assertEquals(1, DailyCravings.remainingTicks(23999));
        assertEquals(24000, DailyCravings.remainingTicks(24000));
        assertEquals(7000, DailyCravings.remainingTicks(17000));
        assertEquals(-1, DailyCravings.day(-1));
        assertEquals(1, DailyCravings.remainingTicks(-1));
    }

    @Test void exclusionsAlwaysWinAndManualModeDoesNotUseDetectedFoods() {
        assertEquals(List.of("apple", "cake"), DailyCravings.pool(true, List.of("apple", "flesh"), List.of("cake", "flesh", "apple"), List.of("flesh")));
        assertEquals(List.of("cake"), DailyCravings.pool(false, List.of("apple"), List.of("cake", "flesh"), List.of("flesh")));
        assertTrue(DailyCravings.pool(false, List.of("apple"), List.of(), List.of()).isEmpty());
    }

    @Test void selectionNeverDuplicatesAndHandlesSmallOrEmptyPools() {
        assertEquals(2, DailyCravings.select(List.of("apple", "cake", "apple"), 64, new Random(3)).size());
        assertTrue(DailyCravings.select(List.of(), 1, new Random()).isEmpty());
        for (int seed = 0; seed < 100; seed++) {
            var selected = DailyCravings.select(List.of(1, 2, 3, 4, 5), 3, new Random(seed));
            assertEquals(3, new HashSet<>(selected).size());
        }
    }

    @Test void everyCandidateAndPermutationIsReachable() {
        var selections = new HashSet<List<Integer>>();
        var random = new Random(735);
        for (int i = 0; i < 1000; i++) selections.add(DailyCravings.select(List.of(1, 2, 3), 2, random));
        assertEquals(6, selections.size());
    }
}
