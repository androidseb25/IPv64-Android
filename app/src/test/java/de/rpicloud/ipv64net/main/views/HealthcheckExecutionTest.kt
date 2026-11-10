package de.rpicloud.ipv64net.main.views

import de.sr.ipv64.model.Hc2Execution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthcheckExecutionTest {
    private val locs = listOf("de", "us")

    @Test
    fun executionFields_sendOnlyWhatTheModeUses() {
        executionFields(Hc2Execution.ONLINE, "n1", locs).let {
            assertNull(it.nodeId); assertEquals(locs, it.locations)
        }
        executionFields(Hc2Execution.LOCAL, "n1", locs).let {
            assertEquals("n1", it.nodeId); assertNull(it.locations)
        }
        executionFields(Hc2Execution.BOTH, "n1", locs).let {
            assertEquals("n1", it.nodeId); assertEquals(locs, it.locations)
        }
    }

    @Test
    fun moveNeeded_onlyForRealChanges() {
        // Unverändert (null = Online, Reihenfolge der Standorte egal)
        assertFalse(executionMoveNeeded(null, null, locs, Hc2Execution.ONLINE, "stale", locs.reversed()))
        assertFalse(executionMoveNeeded(Hc2Execution.LOCAL, "n1", emptyList(), Hc2Execution.LOCAL, "n1", locs))
        // Wechsel des Modus, des Nodes oder der Standorte
        assertTrue(executionMoveNeeded(Hc2Execution.ONLINE, null, locs, Hc2Execution.LOCAL, "n1", locs))
        assertTrue(executionMoveNeeded(Hc2Execution.LOCAL, "n1", emptyList(), Hc2Execution.LOCAL, "n2", locs))
        assertTrue(executionMoveNeeded(Hc2Execution.BOTH, "n1", locs, Hc2Execution.BOTH, "n1", listOf("de")))
        assertTrue(executionMoveNeeded(Hc2Execution.ONLINE, null, locs, Hc2Execution.ONLINE, null, listOf("de")))
    }
}
