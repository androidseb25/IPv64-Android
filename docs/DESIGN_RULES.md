# Design rules (Material 3, Compose)

Gemeinsame Regeln für alle Screens unter Monitoring und Infrastructure. Grundlage: Skill
`mobile-android-design` (~/.claude/skills/mobile-android-design) und die bereits umgebauten Hubs
(`HubScreen` in `main/views/common/CommonViews.kt`).

## Bausteine (CommonViews.kt) – immer diese verwenden, keine privaten Kopien

| Baustein | Zweck |
|---|---|
| `ListGroup(title)` | Gruppe: optionale `SectionHeader` + eine Fläche (`surfaceContainer`, `shapes.large`) |
| `ListRow(...)` | Zeile in einer Gruppe (M3 `ListItem`): Icon, Titel, Untertitel, Trailing, optional klickbar + Pfeil. Optional: `titleColor`/`iconTint` (z. B. `colorScheme.error` für Logout), `monospaceTitle`/`monospaceSubtitle` (IPs, Record-Inhalte, Token-Hinweise), `subtitleMaxLines` |
| `GroupDivider()` | Trennlinie zwischen Zeilen einer Gruppe (eingerückt) |
| `InfoRow(label, value)` | Bezeichnung (klein, `onSurfaceVariant`) **über** dem Wert (`bodyLarge`, `onSurface`). Optional: `copyable`, `sensitive` (verdeckt), `monospace`, `valueColor` (z. B. `colorScheme.error` für „Down since“) |
| `InfoGroup(title, rows: List<InfoItem>, hideEmpty)` | **Standard für Fakten**: `ListGroup` aus `InfoRow`s mit `GroupDivider` und Einzug. `InfoItem(label, value, copyable, sensitive, monospace, valueColor, onClick)`; `hideEmpty = true` lässt Zeilen ohne Wert weg |
| `InfoRows(rows)` | Wie `InfoGroup`, aber ohne eigene Gruppe: innerhalb einer `ListGroup` mit weiterem Inhalt (Hinweistext, Fortschritt …) aufrufen |
| `GroupText(text, error)` | Hinweis-/Leer-Text innerhalb einer `ListGroup` |
| `GroupRowPadding` | Innenabstand (16/12 dp) für eigene Inhalte oder einzelne `InfoRow`s in einer `ListGroup` |
| `FormSection(title, content)` | Formularabschnitt (und frei gestaltete Karten, z. B. Diagramme): Überschrift + Fläche, 16 dp Innenabstand, 8 dp zwischen Feldern |
| `groupedRows(items, key, title, row)` | Lange Listen (LazyListScope): **ein Lazy-Item pro Zeile**, optisch eine Gruppe (Ecken oben/unten, `GroupDivider`). Der LazyColumn dann `spacedBy(0.dp)` geben und andere Einträge mit `spacedItem(key)` bzw. `GroupGap` absetzen |
| `groupedItemsOrEmpty(items, loaded, emptyText, key, row)` | Listen-Screens (LazyListScope): wie `groupedRows`, leer → `EmptyState` |
| `ActionTile(icon, label, onClick, enabled)` | Bis zu 3–4 Hauptaktionen eines Detail-Screens: gleich breite Kacheln (`Modifier.weight(1f)` in einer `Row`), Icon im Kreis über kurzem Text, statt umbrechender Button-Reihen |
| `SearchField(...)` | Abgerundetes Suchfeld mit Lupe (wie im Infrastructure-Hub) |
| `FilterChipRow(...)` | Eine horizontal scrollbare Reihe `FilterChip` |
| `StatusChip`, `SectionHeader`, `EmptyState`, `ConfirmDialog`, `ScreenScaffold`, `RefreshableList` | wie bisher |

## Listen-Screens
- Einträge stehen in **einer** `ListGroup` als `ListRow` (Titel = Name, Untertitel = 1–2 Zeilen Meta,
  Trailing = `StatusChip`), nicht als eigene Karte pro Eintrag.
- Suche: `SearchField`. Filter: **eine** `FilterChipRow`; mehrere Filterarten in derselben Reihe,
  getrennt durch einen kleinen vertikalen Abstand/Divider – nie zwei Chip-Reihen übereinander.
- Hinzufügen bleibt der FAB.

## Detail-Screens
- Oben eine Zusammenfassung (Status-Chip, wichtigste Meta) in einer `ListGroup`.
- Aktionen direkt darunter als Reihe aus `FilledTonalButton` / `OutlinedButton`; Löschen bleibt in der
  Top-Bar mit `ConfirmDialog`.
- Fakten als `InfoGroup` (bzw. `InfoRows` in einer `ListGroup`), keine eigenen Fakten-Helfer pro Screen. **Werte betonen, nicht Labels.**
- Unterabschnitte (Messpunkte, Checks, Verlauf …) als eigene `ListGroup(title)`.

## Formulare
- Felder in Abschnitte mit `FormSection(title)` gruppieren; Abstände wie unten.
- Speichern-Verhalten nicht ändern.

## Abstände, Typo, Farben
- Inhalt 16 dp Rand, 8 dp zwischen Einträgen, Gruppen-Überschrift mit etwas Abstand nach oben.
- Titel `titleMedium`, Fließtext `bodyMedium`/`bodyLarge`, Meta `bodySmall` + `onSurfaceVariant`.
  Kein `FontWeight.Bold` für Labels.
- Farben nur aus `MaterialTheme.colorScheme` bzw. vorhandenen Status-Farben; Status nie nur über Farbe
  (immer Text im Chip).
- Touch-Ziele ≥ 48 dp; `contentDescription` für Icon-Buttons.

## Nicht ändern
Datenladen, Caches, Navigation/Routen, Texte mit Bedeutung, Validierung, Bestätigungsdialoge,
Funktionsumfang. Reine Darstellung.
