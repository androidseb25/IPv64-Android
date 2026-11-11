package de.rpicloud.ipv64net.main.views.monitoring

import de.sr.ipv64.node64.model.Monitor64AccessType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Monitor64AccessTest {
    @Test
    fun blankFields_areNotSent() {
        val a = buildMonitor64Access(Monitor64AccessType.PVE, " ", "", "", "", "", null)
        assertNull(a.config); assertNull(a.secret); assertNull(a.hostkey)
    }

    @Test
    fun fingerprint_isHostkeyForSshAndPinOtherwise() {
        val ssh = buildMonitor64Access(Monitor64AccessType.SSH, "22", "root", "", "", "pw", "SHA256:x")
        assertEquals("SHA256:x", ssh.hostkey); assertNull(ssh.config?.pin); assertEquals(22, ssh.config?.port); assertEquals("pw", ssh.secret)
        val pve = buildMonitor64Access(Monitor64AccessType.PVE, "", "", "https://h:8006", "u@pve!t", "s", "AB:CD")
        assertNull(pve.hostkey); assertEquals("AB:CD", pve.config?.pin); assertEquals("u@pve!t", pve.config?.tokenId)
    }

    @Test
    fun i18nKeysBecomeReadable() {
        assertEquals("fs /", "i18n:monitor64.svc.fs?name=%2F".m64Text())
        assertEquals("1,0 TB · wear 6", "1,0 TB · i18n:monitor64.txt.wear?v=6".m64Text())
        assertEquals("cores 4", "i18n:monitor64.txt.cores?n=4".m64Text())
        assertEquals("plain", "plain".m64Text())
    }

    @Test
    fun editKeepsHiddenConfigFields() {
        val base = de.sr.ipv64.node64.model.Monitor64AccessConfig(port = 161, traps = true, pin = "OLD", user = "old")
        val a = buildMonitor64Access(Monitor64AccessType.SNMP, "162", "", "", "", "", null, base)
        assertEquals(162, a.config?.port)
        assertEquals(true, a.config?.traps)
        assertEquals("OLD", a.config?.pin)
        assertNull(a.config?.user)
    }

    @Test
    fun clearedFieldOnEditStillSendsConfig() {
        val base = de.sr.ipv64.node64.model.Monitor64AccessConfig(port = 2222)
        val a = buildMonitor64Access(Monitor64AccessType.SSH, "", "", "", "", "", null, base)
        assertEquals(de.sr.ipv64.node64.model.Monitor64AccessConfig(), a.config)
        assertEquals("bad %zz", "bad %zz".m64Text())
        assertEquals("x 100%", "i18n:a.x?v=100%".m64Text())
    }
}
