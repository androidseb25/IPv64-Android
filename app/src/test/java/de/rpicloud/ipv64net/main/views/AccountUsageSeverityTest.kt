package de.rpicloud.ipv64net.main.views

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountUsageSeverityTest {
    @Test
    fun usageSeverityCoversQuotaThresholdsAndMissingLimits() {
        assertEquals(UsageSeverity.UNKNOWN, usageSeverity(null, 50.0))
        assertEquals(UsageSeverity.UNKNOWN, usageSeverity(null, null))
        assertEquals(UsageSeverity.NORMAL, usageSeverity(3.0, null))
        assertEquals(UsageSeverity.NORMAL, usageSeverity(39.0, 50.0))
        assertEquals(UsageSeverity.NEAR_LIMIT, usageSeverity(40.0, 50.0))
        assertEquals(UsageSeverity.LIMIT_REACHED, usageSeverity(50.0, 50.0))
        assertEquals(UsageSeverity.LIMIT_REACHED, usageSeverity(52.0, 50.0))
        assertEquals(UsageSeverity.LIMIT_REACHED, usageSeverity(1.0, null, reached = true))
    }
}
