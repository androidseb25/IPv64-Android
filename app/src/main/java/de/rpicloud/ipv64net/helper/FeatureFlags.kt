package de.rpicloud.ipv64net.helper

/**
 * Compile-Zeit-Schalter für Funktionen, die schrittweise freigeschaltet werden.
 */
object FeatureFlags {
    /**
     * Tab "Network" (CDN, DNS64, Nodes) in der unteren Leiste anzeigen.
     * Runde 1: false (Screens sind Platzhalter); nach Runde 2 auf true setzen.
     * Die Routen existieren unabhängig davon immer.
     */
    const val NETWORK_TAB = true
}
