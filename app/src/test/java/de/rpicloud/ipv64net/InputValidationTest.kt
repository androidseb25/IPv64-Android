package de.rpicloud.ipv64net

import de.rpicloud.ipv64net.main.views.network.addressError
import de.rpicloud.ipv64net.main.views.network.normalizeDns64Domain
import de.rpicloud.ipv64net.models.isIpv4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Nur reine JVM-Pfade: IPv6 braucht android.net.InetAddresses und wird hier nicht geprüft
class InputValidationTest {

    @Test
    fun ipv4_accepts_plain_addresses() {
        assertTrue(isIpv4("0.0.0.0"))
        assertTrue(isIpv4("1.2.3.4"))
        assertTrue(isIpv4("255.255.255.255"))
    }

    @Test
    fun ipv4_rejects_malformed_addresses() {
        assertFalse(isIpv4("010.1.1.1"))
        assertFalse(isIpv4("256.1.1.1"))
        assertFalse(isIpv4("1.2.3"))
        assertFalse(isIpv4("+1.2.3.4"))
        assertFalse(isIpv4("1.2.3.4 "))
    }

    @Test
    fun cidr_prefix_is_validated() {
        assertNull(addressError("1.2.3.4"))
        assertNull(addressError("1.2.3.4/24"))
        assertNull(addressError("1.2.3.4/0"))
        assertNotNull(addressError("1.2.3.4/-0"))
        assertNotNull(addressError("1.2.3.4/+8"))
        assertNotNull(addressError("1.2.3.4/"))
        assertNotNull(addressError("1.2.3.4/33"))
        assertNotNull(addressError("1.2.3.4/24/1"))
        assertNotNull(addressError("010.1.1.1/24"))
        assertNotNull(addressError(""))
    }

    @Test
    fun dns64_domain_is_normalized() {
        assertEquals("example.com", normalizeDns64Domain("https://Example.com/x?y"))
        assertEquals("example.com", normalizeDns64Domain("  example.com. "))
        assertNull(normalizeDns64Domain("ex ample.com"))
        assertNull(normalizeDns64Domain("localhost"))
        assertNull(normalizeDns64Domain("a..b.com"))
    }
}
