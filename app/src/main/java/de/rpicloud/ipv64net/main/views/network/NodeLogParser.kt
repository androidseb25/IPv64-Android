package de.rpicloud.ipv64net.main.views.network

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Eine geparste logfmt-Zeile der Node; fehlende Felder sind null. */
data class NodeLogEntry(val time: String?, val level: String?, val module: String?, val msg: String?)

/**
 * logfmt (`time=… level=INFO msg="a b" module=x`): Werte optional in Anführungszeichen (\" und \\ maskiert),
 * Schlüssel dürfen mehrfach vorkommen (der erste gilt). null = keine Zeile im logfmt-Format (weder level noch msg).
 */
fun parseNodeLogLine(line: String): NodeLogEntry? {
    val fields = mutableMapOf<String, String>()
    var i = 0
    val n = line.length
    while (i < n) {
        while (i < n && line[i].isWhitespace()) i++
        val keyStart = i
        while (i < n && line[i] != '=' && !line[i].isWhitespace()) i++
        // Wort ohne "=" → kein logfmt
        if (i >= n || line[i] != '=' || i == keyStart) return null
        val key = line.substring(keyStart, i)
        i++
        val value = StringBuilder()
        if (i < n && line[i] == '"') {
            i++
            while (i < n && line[i] != '"') {
                if (line[i] == '\\' && i + 1 < n) {
                    i++
                    value.append(when (line[i++]) { 'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; else -> line[i - 1] })
                } else value.append(line[i++])
            }
            i++
        } else {
            while (i < n && !line[i].isWhitespace()) value.append(line[i++])
        }
        fields.putIfAbsent(key, value.toString())
    }
    if (fields["level"] == null && fields["msg"] == null) return null
    return NodeLogEntry(fields["time"], fields["level"]?.uppercase(), fields["module"], fields["msg"])
}

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

/** ISO-Zeitstempel → lokale Uhrzeit HH:mm:ss; null, wenn nicht lesbar. */
fun nodeLogClock(iso: String?): String? =
    runCatching { clockFormat.format(OffsetDateTime.parse(iso).toInstant().atZone(ZoneId.systemDefault())) }.getOrNull()
