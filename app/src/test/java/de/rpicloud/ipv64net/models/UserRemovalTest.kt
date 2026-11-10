package de.rpicloud.ipv64net.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class UserRemovalTest {

    private fun user(name: String, key: String) = User(Username = name, ApiKey = key, Information = "")

    private val a = user("A", "key-a")
    private val b = user("B", "key-b")
    private val c = user("C", "key-c")

    @Test
    fun removingInactiveEntryKeepsActiveKey() {
        val (list, key, result) = removalDecision(listOf(a, b, c), "key-a", b.id)
        assertEquals(listOf(a, c), list)
        assertEquals("key-a", key)
        assertEquals(UserRemovalResult.Removed, result)
    }

    @Test
    fun removingActiveEntrySwitchesToFirstRemaining() {
        val (list, key, result) = removalDecision(listOf(a, b, c), "key-b", b.id)
        assertEquals(listOf(a, c), list)
        assertEquals("key-a", key)
        assertEquals(UserRemovalResult.SwitchedTo(a), result)
    }

    @Test
    fun removingLastEntryLogsOut() {
        val (list, key, result) = removalDecision(listOf(a), "key-a", a.id)
        assertTrue(list.isEmpty())
        assertEquals("", key)
        assertEquals(UserRemovalResult.LoggedOut, result)
    }

    @Test
    fun sameApiKeyInAnotherEntryDoesNotSwitch() {
        val a2 = user("A again", "key-a")
        val (list, key, result) = removalDecision(listOf(b, a, a2), "key-a", a.id)
        assertEquals(listOf(b, a2), list)
        assertEquals("key-a", key)
        assertEquals(UserRemovalResult.Removed, result)
    }

    @Test
    fun unknownIdIsNotFound() {
        val (list, key, result) = removalDecision(listOf(a, b), "key-a", UUID.randomUUID())
        assertEquals(listOf(a, b), list)
        assertEquals("key-a", key)
        assertEquals(UserRemovalResult.NotFound, result)
    }

    @Test
    fun duplicateIdsAreRepairedToUnique() {
        val dup = b.copy(id = a.id)
        val (list, repaired) = withUniqueIds(listOf(a, dup, c))
        assertTrue(repaired)
        assertEquals(3, list.map { it.id }.toSet().size)
        assertEquals(a.id, list[0].id)
        assertEquals(listOf("A", "B", "C"), list.map { it.Username })
    }

    @Test
    fun uniqueIdsAreUnchanged() {
        val (list, repaired) = withUniqueIds(listOf(a, b))
        assertFalse(repaired)
        assertEquals(listOf(a, b), list)
    }
}
