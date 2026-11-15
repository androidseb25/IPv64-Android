package de.rpicloud.ipv64net.main.views.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeModuleViewTest {
    @Test
    fun labelsKnownModulesCaseInsensitive() {
        assertEquals("Honeypot", moduleLabel("Honeypot"))
        assertTrue(hasModuleScreen("dns64"))
        listOf("cdn64", "dyndns", "checks", "healthchecks", "ipam64").forEach {
            assertNull(moduleLabel(it))
            assertFalse(hasModuleScreen(it))
        }
    }

    @Test
    fun unknownModuleHasNoScreen() {
        assertNull(moduleLabel("other"))
        assertFalse(hasModuleScreen("other"))
    }
}
