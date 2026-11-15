package de.rpicloud.ipv64net.helper

import de.rpicloud.ipv64net.helper.SecNtfyLog.Status
import org.junit.Assert.assertEquals
import org.junit.Test

class SecNtfyLogTest {
    @Test
    fun olderLogsKeepTheirOutcomeAndDropTheProviderName() {
        val lines = listOf(
            "I\tSecNtfy device registered" to SecNtfyLog.Entry(123, "Device registered", Status.SUCCESS),
            "I\tNotification received from FCM" to SecNtfyLog.Entry(123, "Notification received from FCM", Status.INFO),
            "W\tReceipt unavailable: missing SecNtfy message ID" to SecNtfyLog.Entry(123, "Receipt unavailable: missing message ID", Status.SKIPPED),
            "W\tDelivery receipt failed" to SecNtfyLog.Entry(123, "Delivery receipt failed", Status.FAILED),
            "S\tDelivery receipt already accepted" to SecNtfyLog.Entry(123, "Delivery receipt already accepted", Status.SUCCESS)
        )

        lines.forEach { (line, entry) -> assertEquals(entry, SecNtfyLog.parseEntry("123\t$line")) }
    }
}
