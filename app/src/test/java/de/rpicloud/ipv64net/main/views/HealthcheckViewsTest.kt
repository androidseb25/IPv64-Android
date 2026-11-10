package de.rpicloud.ipv64net.main.views

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthcheckViewsTest {
    private val base = 1_800_000_000_000L
    private val step = 300_000L // 5 Minuten

    @Test
    fun nearestIndex_epochValuesPickCorrectIndexNearMidpoints() {
        val times = List(5) { base + it * step }
        val tMax = times.last()
        // Knapp unter / über der Mitte zwischen Index 1 und 2 (fraction 0.375)
        assertEquals(1, nearestIndex(times, base, tMax, 0.37f))
        assertEquals(2, nearestIndex(times, base, tMax, 0.38f))
        assertEquals(3, nearestIndex(times, base, tMax, 0.74f))
    }

    @Test
    fun nearestIndex_edgesAndSingleElement() {
        val times = List(4) { base + it * step }
        assertEquals(0, nearestIndex(times, base, times.last(), 0f))
        assertEquals(3, nearestIndex(times, base, times.last(), 1f))
        assertEquals(0, nearestIndex(listOf(base), base, base, 0.5f))
    }

    @Test
    fun durationToSeconds_handlesOverflowAndRange() {
        assertEquals(null, durationToSeconds("", 0))
        assertEquals(90, durationToSeconds("90", 0))
        assertEquals(-1, durationToSeconds("abc", 0))
        // Long-Überlauf darf die Bereichsprüfung nicht umgehen
        assertEquals(-1, durationToSeconds(Long.MAX_VALUE.toString(), 2))
        assertEquals(-1, durationToSeconds("99999999999", 2))
    }
}
