package de.rpicloud.ipv64net.helper

import android.content.Context
import android.os.SystemClock
import de.sr.ipv64.IPv64NetworkException
import de.sr.ipv64.model.Hc2Execution
import de.sr.ipv64.model.Hc2MoveCheckRequest
import de.sr.ipv64.model.Hc2Testrun
import kotlinx.coroutines.delay

// Ergebnis eines Standortwechsels (move + Testlauf)
sealed class Hc2MoveOutcome {
    object Applied : Hc2MoveOutcome()
    data class Failed(val message: String) : Hc2MoveOutcome()
    object TimedOut : Hc2MoveOutcome()
}

// Startet den Standortwechsel eines Online-Checks und fragt den Testlauf alle 2 s ab (höchstens 60 s Wanduhrzeit).
// Wirft IPv64Exception bei API-Fehlern; CancellationException wird nicht abgefangen.
object Hc2MoveRunner {
    private const val POLL_INTERVAL_MS = 2_000L
    private const val MAX_WAIT_MS = 60_000L

    suspend fun moveOnline(ctx: Context, token: String, locations: List<String>): Hc2MoveOutcome {
        val client = IPv64Client.get(ctx)
        val deadline = SystemClock.elapsedRealtime() + MAX_WAIT_MS
        var run: Hc2Testrun = ipv64Retrying {
            client.hc2.checks.move(token, Hc2MoveCheckRequest(execution = Hc2Execution.ONLINE, locations = locations))
        }
        classify(run)?.let { return it }
        val id = run.id ?: return Hc2MoveOutcome.Failed("The test run could not be started.")
        var networkErrors = 0
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(POLL_INTERVAL_MS)
            try {
                run = ipv64Retrying { client.hc2.testrun(id) }
            } catch (e: IPv64NetworkException) {
                // Ein einzelner Netzwerkfehler beim Abfragen ist tolerierbar
                if (++networkErrors > 1) throw e
                continue
            }
            classify(run)?.let { return it }
        }
        return Hc2MoveOutcome.TimedOut
    }

    // null = noch nicht fertig (pending/running)
    private fun classify(run: Hc2Testrun): Hc2MoveOutcome? {
        return when (run.status?.lowercase()) {
            "ok" -> Hc2MoveOutcome.Applied
            "failed" -> Hc2MoveOutcome.Failed(failureMessage(run))
            "expired" -> Hc2MoveOutcome.Failed("The test run expired before it finished.")
            else -> null
        }
    }

    private fun failureMessage(run: Hc2Testrun): String {
        val msgs = run.results.filter { it.ok != true }
            .mapNotNull { r -> r.message?.takeIf { it.isNotBlank() }?.let { m -> r.source?.let { "$it: $m" } ?: m } }
        return when {
            msgs.isNotEmpty() -> msgs.joinToString("\n")
            !run.error.isNullOrBlank() -> run.error!!
            else -> "The test run failed. The locations were not changed."
        }
    }
}
