package de.rpicloud.ipv64net.main.views

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.rpicloud.ipv64net.models.HealthEvents

@Composable
fun EventsBarView(
    ctx: Context,
    events: List<HealthEvents>,            // immutable Liste bevorzugen
    modifier: Modifier = Modifier,
    maxVisible: Int? = null,               // null => so viele wie reinpassen
) {
    // Feste Maße einmal definieren
    val itemW = 5.dp
    val itemH = 20.dp
    val gap   = 5.dp
    val outerPad = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Wie viele passen rein? (wenn maxVisible gesetzt ist, nimm den kleineren)
        val visibleCount = remember(maxWidth, maxVisible) {
            if (maxVisible != null) maxVisible
            else {
                // floor((W - Außenpadding + gap) / (itemW + gap))
                val innerW = maxWidth - (outerPad.calculateLeftPadding(LayoutDirection.Ltr)
                        + outerPad.calculateRightPadding(LayoutDirection.Ltr))
                ((innerW + gap) / (itemW + gap)).toInt().coerceAtLeast(1)
            }
        }

        // Slice der Daten nur einmal berechnen, und ohne .reversed()–Allokation
        // -> iteriere rückwärts per indices
        val displayEvents by remember(events, visibleCount) {
            derivedStateOf {
                val n = minOf(visibleCount, events.size)
                // neueste/letzte zuerst (wie dein reversed())
                val out = ArrayList<HealthEvents>(n)
                var i = events.lastIndex
                repeat(n) {
                    out.add(events[i--])
                }
                out
            }
        }

        LazyRow(
            modifier = Modifier.padding(outerPad),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            items(
                items = displayEvents,
                key = { e -> e.id }, // stabile Keys
                contentType = { _ -> 0 } // gleicher Typ → effizienteres Recycling
            ) { e ->
                // ultraleicht: Box + background statt Card
                Box(
                    modifier = Modifier
                        .size(width = itemW, height = itemH)
                        .background(Color(ContextCompat.getColor(ctx, e.Status.color)), shape = RectangleShape)
                )
            }
        }
    }
}

@Composable
fun EventsBarCanvas(
    events: List<HealthEvents>,
    modifier: Modifier = Modifier,
    maxVisible: Int? = null,            // null => so viele wie reinpassen
    barWidth: Dp = 5.dp,
    barHeight: Dp = 20.dp,
    gap: Dp = 5.dp,
    cornerRadius: Dp = 5.dp,
    horizontalPadding: Dp = 16.dp,
    bottomPadding: Dp = 16.dp,
    // Farb-Mapper: falls deine Events schon eine Compose-Farbe haben, reiche sie hier durch
    statusToColor: (HealthEvents) -> Color
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val maxW = this.maxWidth

        // wie viele Balken passen rein?
        val visibleCount = remember(maxW, maxVisible, barWidth, gap, horizontalPadding) {
            if (maxVisible != null) maxVisible
            else {
                val innerW = maxW - horizontalPadding * 2
                val per = barWidth + gap
                ((innerW + gap) / per).toInt().coerceAtLeast(1)
            }
        }

        val revEvents = events.reversed()
        // letzte N Events (neueste zuerst), ohne ständig neue Kopien zu bauen
        val displayEvents by remember(revEvents, visibleCount) {
            derivedStateOf {
                val n = minOf(visibleCount, revEvents.size)
                ArrayList<HealthEvents>(n).apply {
                    var i = revEvents.lastIndex
                    repeat(n) { add(revEvents[i--]) }
                }
            }
        }

        val height = barHeight + bottomPadding

        Canvas(
            modifier = Modifier
                .height(height)
                .fillMaxWidth()
                .padding(start = horizontalPadding, end = horizontalPadding, bottom = bottomPadding)
        ) {
            val bw = with(density) { barWidth.toPx() }
            val g  = with(density) { gap.toPx() }
            val h  = with(density) { barHeight.toPx() }
            val r  = with(density) { cornerRadius.toPx() }

            var x = 0f
            displayEvents.reversed().forEach { e ->
                val color = statusToColor(e)
                drawRoundRect(
                    cornerRadius = CornerRadius(r, r),
                    color = color,
                    topLeft = Offset(x, size.height - h),  // unten ausrichten
                    size = Size(bw, h)
                )
                x += bw + g
            }
        }
    }
}