package de.rpicloud.ipv64net.main.views.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeLogParserTest {

    @Test
    fun parsesNormalLine() {
        val e = parseNodeLogLine("time=2026-11-04T06:03:15.891Z level=info msg=started module=core")!!
        assertEquals("2026-11-04T06:03:15.891Z", e.time)
        assertEquals("INFO", e.level)
        assertEquals("started", e.msg)
        assertEquals("core", e.module)
    }

    @Test
    fun parsesQuotedMsgWithSpacesAndEscapes() {
        val e = parseNodeLogLine("""level=WARN msg="say \"hi\" there" status=up""")!!
        assertEquals("say \"hi\" there", e.msg)
    }

    @Test
    fun firstOfRepeatedModuleWins() {
        val e = parseNodeLogLine("""level=DEBUG msg="check measured" module=healthchecks module=scheduler token=*** status=up""")!!
        assertEquals("healthchecks", e.module)
    }

    @Test
    fun unparsableLineIsNull() {
        assertNull(parseNodeLogLine("plain text without pairs"))
        assertNull(parseNodeLogLine("a=1 b=2"))
    }
}

class NodeLogParserEscapeTest {
    @Test
    fun mapsNewlineEscapeAndParsesOffsetTime() {
        val e = parseNodeLogLine("""level=INFO msg="a\nb\tc" time=2026-11-04T07:03:15+01:00""")!!
        assertEquals("a\nb\tc", e.msg)
        org.junit.Assert.assertNotNull(nodeLogClock(e.time))
    }
}
