package de.rpicloud.ipv64net.main.views

import de.sr.ipv64.model.Hc2Check
import de.rpicloud.ipv64net.models.label
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HealthcheckViewsTest {
    private val base = 1_800_000_000_000L
    private val step = 300_000L // 5 Minuten

    @Test
    fun legacyChecksDecodeAndKeepTheirTypeSpecificParametersUntouched() {
        listOf("docker" to "Docker", "proxmox" to "Proxmox").forEach { (apiType, label) ->
            val type = requireNotNull(Json.decodeFromString<Hc2Check>("""{"type":"$apiType"}""").type)
            val params = ParamsState.initial(type)

            assertEquals(label, type.label)
            assertFalse(type.hasEssentialParams())
            assertFalse(type.hasAdvancedParams())
            assertNull(params.toParams(type, params))
        }
    }

    @Test
    fun healthcheckNamesSortCaseInsensitivelyWithTargetFallback() {
        val checks = listOf(
            Hc2Check(name = "zeta", target = "example.org"),
            Hc2Check(name = "  ", target = "Bravo.example"),
            Hc2Check(name = "Alpha", target = "z.example"),
            Hc2Check(name = "alpha", target = "a.example")
        )
        assertEquals(
            listOf("a.example", "z.example", "Bravo.example", "example.org"),
            checks.sortedWith(healthcheckNameOrder).map { it.target }
        )
    }

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
