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
        assertEquals("CDN64 / Proxy", moduleLabel("cdn64"))
        assertTrue(hasModuleScreen("dns64"))
        assertEquals("Healthchecks", moduleLabel("checks"))
        assertEquals("Healthchecks", moduleLabel("healthchecks"))
    }

    @Test
    fun unknownModuleHasNoScreen() {
        assertNull(moduleLabel("other"))
        assertFalse(hasModuleScreen("other"))
    }
}
