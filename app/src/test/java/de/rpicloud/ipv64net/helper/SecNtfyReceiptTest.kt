package de.rpicloud.ipv64net.helper

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SecNtfyReceiptTest {
    @Test
    fun confirmsReceiptEvenIfPostingThrows() = runBlocking {
        val calls = mutableListOf<String>()
        val postingError = IllegalStateException()
        val result = runCatching {
            withSecNtfyReceipt("push-id", { calls.add(it) }) {
                calls.add("post")
                throw postingError
            }
        }
        assertSame(postingError, result.exceptionOrNull())
        assertEquals(listOf("post", "push-id"), calls)
    }

    @Test
    fun acknowledgesAnUndisplayedMessageAndSkipsMissingIds() = runBlocking {
        val ids = mutableListOf<String>()
        assertNull(withSecNtfyReceipt("push-id", { ids.add(it) }) { null })
        assertNull(withSecNtfyReceipt(" ", { ids.add(it) }) { null })
        assertEquals(listOf("push-id"), ids)
    }
}
