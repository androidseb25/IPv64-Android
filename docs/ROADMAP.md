# IPv64.net Android – Roadmap

Stand: 29.10.2026 · Basis: `dev` @ 20c2654 (alle Tabs auf IPv64-Package / API v2, neues 5-Tab-Menü)

## Release
- [ ] `.kotlin/` in `.gitignore` aufnehmen
- [ ] `versionCode` 21 → 22, `versionName` → 3.0.0
- [ ] `main` (Lizenz-Commits) nach `dev` mergen
- [ ] PR `dev → main` mit Changelog

## Offene Prüfungen (nur mit echtem Konto testbar)
- [ ] Schreibende Abläufe je Bereich mit Test-Objekten (anlegen / bearbeiten / löschen)
- [ ] Akzeptieren DNS-Failover und Statusseiten hc2-Check-Tokens? (Fallback: Token manuell eingeben)
- [ ] PATCH `/hc2/checks/{token}`: werden `params` zusammengeführt oder ersetzt? (Header/Passwort)
- [ ] Format von `GET /hc2/settings` (Ruhezeiten) – Parser ist tolerant, einmal gegenprüfen
- [ ] Push Ende-zu-Ende: App-Channel registrieren → Test-Push → echter Alarm

## Backend / Library
- [ ] **Backend:** `POST /hc2/channels` mit `type=app` und `{device_token}` liefert HTTP 500 `internal_error`
- [ ] **IPv64-Package:** unbekannte Werte in Check-`params` gehen beim Decoding verloren (Rohdaten als `JsonObject` mitliefern)
- [ ] **IPv64-Package:** unbekannte DNS64-Blocklisten-Typen werden verworfen

## Neue Funktionen (Ideen)
### Top-Kandidaten
- [ ] Home-Screen-Widgets (Jetpack Glance): Healthcheck-Status (Up/Down/Paused + Problem-Checks), aktuelle IPv4/IPv6
- [ ] Aktionen in Push-Benachrichtigungen: „Check öffnen“, „Pausieren“, „Jetzt prüfen“; Tipp öffnet die Check-Detailansicht (Deep Link)
- [ ] Handy als DynDNS-Updater: WorkManager-Job aktualisiert eine Domain bei Netzwechsel / periodisch

### Schnellzugriffe
- [ ] Quick-Settings-Kachel: aktuelle IP / DynDNS-Update auslösen
- [ ] App-Shortcuts (Icon lange drücken): My IP, Healthchecks, Domain hinzufügen
- [ ] Suche über alle Domains und Records

### Monitoring
- [ ] Handy als Heartbeat: Push-Check wird regelmäßig vom Handy angepingt
- [ ] Lokale Benachrichtigung bei IP-Wechsel
- [ ] Test-Nachricht über einen Channel senden (`channels.send`)

### Node64
- [ ] Lokale Verwaltung im LAN über den Node64-Client der Library (`de.sr.ipv64.node64`)

### Komfort & Qualität
- [ ] QR-Codes: Statusseiten-URL, DNS64 DoH/DoT
- [ ] Records exportieren/importieren (Zonefile/BIND)
- [ ] Tablet-/Foldable-Layout (Liste + Detail nebeneinander)
- [ ] Deutsche Übersetzung der Oberfläche
- [ ] Offline-Anzeige der zuletzt geladenen Daten („Stand: …“)
- [ ] Unit-Tests für Parser/Validierungen (Ruhezeiten, HTML→Text, IP/CIDR, Plan-Formatierung)

## In Arbeit
- [ ] Farbige Diagramme mit geprüfter Palette (Bedrohungsstatistik, Honeypot, Latenz)
- [ ] Gespeicherte Accounts aus der App entfernen (Dummy-User)

## Bewusst nicht geplant
- Cloud Router, Gateways, Peers
- Klassische Healthchecks / Integrations-API (ersetzt durch Healthchecks 2.0 / Channels)
- Handeln im Namen eines verwalteten Kontos (act-as), neue API-Keys als App-Konto übernehmen
