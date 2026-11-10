package de.rpicloud.ipv64net.main.views.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalNodeStoreTest {

    @Test
    fun listsOnlyNodesWithUrlOfOwner() {
        val prefs = mapOf(
            "b.url" to "http://10.0.0.2/api", "b.key" to "k", "b.owner" to "1", "b.name" to "Beta",
            "a.url" to "http://10.0.0.1/api", "a.key" to "k", "a.owner" to "1",
            "c.url" to "http://10.0.0.3/api", "c.key" to "k", "c.owner" to "2",
            "d.key" to "k", "d.owner" to "1"
        )
        assertEquals(listOf("a", "b"), localNodeIds(prefs, "1"))
        assertEquals(listOf("c"), localNodeIds(prefs, "2"))
        assertEquals(emptyList<String>(), localNodeIds(prefs, "3"))
    }
}
