package de.rpicloud.ipv64net.models

import de.sr.ipv64.model.DomainDetail
import de.sr.ipv64.model.Record
import de.sr.ipv64.model.RecordType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainIpTest {
    @Test
    fun noAddressRecordsMeansNoIp() {
        assertFalse(DomainDetail().hasIp())
        assertFalse(DomainDetail(records = listOf(Record(type = RecordType.TXT))).hasIp())
    }

    @Test
    fun aOrAaaaRecordMeansIp() {
        assertTrue(DomainDetail(records = listOf(Record(type = RecordType.A))).hasIp())
        assertTrue(DomainDetail(records = listOf(Record(type = RecordType.AAAA))).hasIp())
    }
}
