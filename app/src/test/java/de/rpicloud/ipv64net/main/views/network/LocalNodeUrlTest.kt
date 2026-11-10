package de.rpicloud.ipv64net.main.views.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNodeUrlTest {

    @Test
    fun bareLocalHostGetsHttpPortAndPath() {
        assertEquals("http://192.168.1.10:8080/api", normalizeLocalNodeUrl("192.168.1.10"))
        assertEquals("http://192.168.1.10:9000/api", normalizeLocalNodeUrl(" 192.168.1.10:9000 "))
    }

    @Test
    fun trailingSlashIsStripped() {
        assertEquals("http://10.0.0.1/api", normalizeLocalNodeUrl("http://10.0.0.1/api/"))
    }

    @Test
    fun localIpv6AndNamesAreAccepted() {
        assertEquals("http://[fd00::1]:8080/api", normalizeLocalNodeUrl("http://[fd00::1]:8080"))
        assertEquals("http://[fe80::1]/api", normalizeLocalNodeUrl("http://[fe80::1]"))
        assertEquals("http://nas.local/api", normalizeLocalNodeUrl("http://nas.local"))
    }

    @Test
    fun publicHostWithoutSchemeUsesHttps() {
        assertEquals("https://node.example.home64.de/api", normalizeLocalNodeUrl("https://node.example.home64.de"))
        assertEquals("https://node.example.home64.de/api", normalizeLocalNodeUrl("node.example.home64.de"))
    }

    @Test
    fun httpToInvalidHostIsRejected() {
        // Domain über http ist erlaubt; dass sie lokal auflöst, prüft erst localOnlyDns beim Verbinden
        assertEquals("http://node.example.home64.de/api", normalizeLocalNodeUrl("http://node.example.home64.de"))
        assertNull(normalizeLocalNodeUrl("ftp://x"))
        assertNull(normalizeLocalNodeUrl(""))
        // Punkt am Ende: OkHttp würde den Namen ohne DNS-Filter auflösen
        assertNull(normalizeLocalNodeUrl("http://192.168.1.1."))
        assertNull(normalizeLocalNodeUrl("http://3232235777"))
        // Typische Heimnetz-Namen sind lokal (laufen über den DNS-Filter)
        assertEquals("http://node64.fritz.box:8080/api", normalizeLocalNodeUrl("node64.fritz.box"))
        assertEquals("http://raspberrypi:8080/api", normalizeLocalNodeUrl("raspberrypi"))
        // Zugangsdaten in der Adresse werden nicht gespeichert
        assertEquals("http://192.168.1.10:8080/api", normalizeLocalNodeUrl("http://user:pw@192.168.1.10:8080"))
    }

    @Test
    fun publicIpsAreAcceptedOverHttp() {
        assertEquals("http://8.8.8.8/api", normalizeLocalNodeUrl("http://8.8.8.8/api"))
        assertEquals("http://203.0.113.5:8080/api", normalizeLocalNodeUrl("203.0.113.5"))
        assertEquals("http://203.0.113.5:9000/api", normalizeLocalNodeUrl("203.0.113.5:9000"))
        assertEquals("http://[2001:db8::1]/api", normalizeLocalNodeUrl("http://[2001:db8::1]"))
        assertEquals("http://172.32.0.1/api", normalizeLocalNodeUrl("http://172.32.0.1"))
        assertEquals("https://203.0.113.5/api", normalizeLocalNodeUrl("https://203.0.113.5"))
        assertTrue(isPublicHttp("http://203.0.113.5:8080/api"))
        assertFalse(isPublicHttp("http://192.168.1.10:8080/api"))
        assertFalse(isPublicHttp("https://203.0.113.5/api"))
    }
}
