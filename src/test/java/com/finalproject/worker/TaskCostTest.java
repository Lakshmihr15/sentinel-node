package com.finalproject.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskCostTest {

    @Test
    void calcCostScalesWithPayload() {
        assertEquals(1, TaskCost.costOf("CALC", "100"));
        assertEquals(1, TaskCost.costOf("CALC", "999999"));
        assertEquals(1, TaskCost.costOf("CALC", "1000000"));
        assertEquals(5, TaskCost.costOf("CALC", "5000000"));
        assertEquals(20, TaskCost.costOf("CALC", "20000000"));
    }

    @Test
    void calcCostIsCappedAt100() {
        assertEquals(100, TaskCost.costOf("CALC", "1000000000"));
    }

    @Test
    void sleepCostScalesWithSeconds() {
        assertEquals(1, TaskCost.costOf("SLEEP", "1"));
        assertEquals(1, TaskCost.costOf("SLEEP", "5"));
        assertEquals(2, TaskCost.costOf("SLEEP", "6"));
        assertEquals(2, TaskCost.costOf("SLEEP", "10"));
        assertEquals(3, TaskCost.costOf("SLEEP", "11"));
    }

    @Test
    void cheapTasksAreOneCredit() {
        assertEquals(1, TaskCost.costOf("SEARCH", "anything"));
        assertEquals(1, TaskCost.costOf("HASH",   "abc"));
        assertEquals(1, TaskCost.costOf("PRINT",  "hello"));
    }

    @Test
    void unknownTypeIsOneCredit() {
        assertEquals(1, TaskCost.costOf("MYSTERY", "x"));
        assertEquals(1, TaskCost.costOf(null, null));
    }

    @Test
    void invalidPayloadFallsBackToDefault() {
        // calc default is 5_000_000 → cost 5
        assertEquals(5, TaskCost.costOf("CALC", "not-a-number"));
        // sleep default is 1 → cost 1
        assertEquals(1, TaskCost.costOf("SLEEP", "garbage"));
    }
}
