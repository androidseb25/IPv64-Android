package de.rpicloud.ipv64net.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Diagrammfarben, unabhängig vom App-Theme (dynamische Farben sind auf manchen Geräten fast grau).
 *
 * 8 kategoriale Slots in fester Reihenfolge (Farbenblindheits-Schutz, nicht ändern). Im Hell-Modus
 * sind Orange/Aqua/Gelb/Magenta gegenüber der Referenzpalette abgedunkelt, damit jede Serienfarbe
 * mindestens 3:1 gegen Weiß und gegen getönte Flächen (~#E6E0E9) hat; im Dark-Modus ist Grün
 * aufgehellt (>= 3:1 gegen #36343B).
 *
 * Nutzung: Farbe folgt der Entität, nicht der aktuellen Zeilennummer. Texte bleiben in Theme-Textfarben.
 */
@Immutable
class ChartPalette internal constructor(
    private val series: List<Color>,
    /** Leise Spur hinter Balken (leere Werte, Fortschrittsbahn). */
    val track: Color,
    /** Haarlinien-Raster. */
    val grid: Color,
    /** Grundlinie / Achse. */
    val baseline: Color
) {
    /**
     * Kategoriale Serienfarbe für Slot [i] (0-basiert: 0 Blau, 1 Orange, 2 Aqua, 3 Gelb, 4 Magenta,
     * 5 Grün, 6 Violett, 7 Rot). Jenseits von 8 wird bewusst zyklisch fortgesetzt, damit nie ein
     * Fehler entsteht; Diagramme mit mehr als 8 Serien sollten stattdessen falten oder aufteilen.
     */
    fun categorical(i: Int): Color = series[Math.floorMod(i, series.size)]
}

// Kategorial: Slot 1..8 (Blau, Orange, Aqua, Gelb, Magenta, Grün, Violett, Rot)
private val LightSeries = listOf(
    Color(0xFF2A78D6), Color(0xFFD35A28), Color(0xFF0C8C60), Color(0xFFB07400),
    Color(0xFFCC4F7D), Color(0xFF008300), Color(0xFF6250D6), Color(0xFFE34948)
)
private val DarkSeries = listOf(
    Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
    Color(0xFFD55181), Color(0xFF2E9F2E), Color(0xFF9085E9), Color(0xFFE66767)
)

// Chrome: Die App-Karten haben dynamische Hintergründe, daher Tinte mit Alpha statt fester Grautöne.
private val LightInk = Color(0xFF0B0B0B)
private val DarkInk = Color(0xFFFFFFFF)

private val LightChartPalette = ChartPalette(
    series = LightSeries,
    track = LightInk.copy(alpha = 0.08f),
    grid = LightInk.copy(alpha = 0.10f),
    baseline = LightInk.copy(alpha = 0.25f)
)

private val DarkChartPalette = ChartPalette(
    series = DarkSeries,
    track = DarkInk.copy(alpha = 0.10f),
    grid = DarkInk.copy(alpha = 0.10f),
    baseline = DarkInk.copy(alpha = 0.25f)
)

/** Diagrammpalette passend zum tatsächlich verwendeten Theme (Hell/Dunkel nach Oberflächen-Helligkeit). */
@Composable
fun chartPalette(): ChartPalette =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkChartPalette else LightChartPalette
