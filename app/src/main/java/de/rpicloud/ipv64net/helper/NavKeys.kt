package de.rpicloud.ipv64net.helper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs

/**
 * Schlüssel für Werte, die zwischen Screens über den savedStateHandle übergeben werden
 * (ergänzt [Hc2NavKeys] für Healthchecks/Kanäle).
 *
 * Schreiben (Quell-Screen): `navController.navigateWithArgs(Tab.x, NavKeys.KEY to value)` bzw.
 * `navController.currentBackStackEntry?.savedStateHandle?.set(KEY, value)` vor `navigate(...)`.
 *
 * Lesen (Ziel-Screen): `val id = navController.rememberNavArg<String>(NavKeys.KEY)` bzw.
 * `remember { navController.previousBackStackEntry?.savedStateHandle?.get<T>(KEY) }`.
 *
 * Fehlt der Schlüssel auf einem *_edit-Screen, wird ein neues Objekt angelegt (Create-Modus).
 * Wichtig: Der Wert bleibt im savedStateHandle des Quell-Screens liegen. Wer denselben *_edit-Screen einmal zum
 * Bearbeiten und einmal zum Anlegen öffnet, muss beim Anlegen den Schlüssel mit null überschreiben
 * (`navigateWithArgs(Tab.x_edit, NavKeys.KEY to null)`), sonst öffnet sich wieder das alte Objekt.
 *
 * Die Typen entsprechen dem Parameter, den die Bibliotheks-API erwartet (nicht unbedingt dem Typ im Modell).
 */
object NavKeys {
    /** Long: Wartungsfenster-ID (Hc2Maintenance.id), Liste → maintenance_edit. Fehlt = neues Wartungsfenster. */
    const val SELECTED_MAINTENANCE_ID = "SELECTED_MAINTENANCE_ID"

    /**
     * String: Statusseiten-ID als Text (StatusPage.id ist Long, statusPages.get/update/delete erwarten String),
     * Liste → status_page_edit. Fehlt = neue Statusseite.
     */
    const val SELECTED_STATUS_PAGE_ID = "SELECTED_STATUS_PAGE_ID"

    /** String: Failover-Policy-ID (16 Zeichen), Liste → failover_details. */
    const val SELECTED_FAILOVER_ID = "SELECTED_FAILOVER_ID"

    /** String: Failover-Policy-ID, Details → failover_edit. Fehlt = neue Policy. */
    const val EDIT_FAILOVER_ID = "EDIT_FAILOVER_ID"

    /** String: Offline-Seiten-ID (16 Zeichen), Liste → offline_page_edit. Fehlt = neue Offline-Seite. */
    const val SELECTED_OFFLINE_PAGE_ID = "SELECTED_OFFLINE_PAGE_ID"

    /** String: CDN-Token (CdnDomain.id), Liste → cdn_details und cdn_details → cdn_settings. */
    const val SELECTED_CDN_TOKEN = "SELECTED_CDN_TOKEN"

    /** String: DNS64-Client-Token (Dns64Client.id), Liste → dns64_details. */
    const val SELECTED_DNS64_TOKEN = "SELECTED_DNS64_TOKEN"

    /** String: Node-ID (Hc2Node.nodeId, 32 Zeichen), Liste → node_details. */
    const val SELECTED_NODE_ID = "SELECTED_NODE_ID"

    /** String: Token eines lokalen Checks der Node (Check.token), Healthchecks → node_check_edit (mit SELECTED_NODE_ID). Fehlt = neuer Check. */
    const val NODE_CHECK_TOKEN = "NODE_CHECK_TOKEN"

    /** Long: Proxy64-Host-ID (ProxyHost.id), Proxy64 → proxy_host_edit (mit SELECTED_NODE_ID). Fehlt = neuer Host. */
    const val PROXY_HOST_ID = "PROXY_HOST_ID"

    /** String: Modulname (z. B. "honeypot"), node_details → node_module (zusammen mit SELECTED_NODE_ID). */
    const val NODE_MODULE = "NODE_MODULE"

    /** String: IP-Adresse, die threat_lookup sofort abfragen soll (z. B. aus dem Security-Hub oder einem Alarm). */
    const val THREAT_LOOKUP_IP = "THREAT_LOOKUP_IP"

    /** String: Domainname, domain_details → domain_settings. */
    const val EDIT_DOMAIN_NAME = "EDIT_DOMAIN_NAME"

    /**
     * String: Record-ID als Text (Record.id ist Long, records.get/update/delete erwarten String; der Record-Token
     * wäre ebenfalls gültig), domain_details → domain_record_edit.
     */
    const val EDIT_RECORD_ID = "EDIT_RECORD_ID"

    /** String: Domainname des Records (für DomainCache-Lookup/-Invalidierung), domain_details → domain_record_edit. */
    const val EDIT_RECORD_DOMAIN = "EDIT_RECORD_DOMAIN"
}

/**
 * Setzt die Werte im savedStateHandle des aktuellen Eintrags und navigiert zu [tab].
 * Ein Wert null entfernt den Schlüssel (z. B. Create-Modus eines *_edit-Screens, siehe [NavKeys]).
 * Erlaubte Werttypen: alles, was ein Bundle aufnehmen kann (String, Long, Int, Boolean, ...).
 */
fun NavController.navigateWithArgs(tab: Tab, vararg args: Pair<String, Any?>) {
    val handle = currentBackStackEntry?.savedStateHandle
    args.forEach { (key, value) ->
        if (value == null) handle?.remove<Any>(key) else handle?.set(key, value)
    }
    navigate(Tabs.getRoute(tab))
}

/**
 * Liest einen per [navigateWithArgs] übergebenen Wert aus dem vorherigen Back-Stack-Eintrag.
 * Der Wert wird einmal pro Screen gemerkt (stabil über Recompositions; Prozess-Neustart über savedStateHandle).
 * null, wenn der Schlüssel fehlt oder der Screen keinen Vorgänger hat.
 */
@Composable
fun <T> NavController.rememberNavArg(key: String): T? = remember(key) {
    previousBackStackEntry?.savedStateHandle?.get<T>(key)
}
