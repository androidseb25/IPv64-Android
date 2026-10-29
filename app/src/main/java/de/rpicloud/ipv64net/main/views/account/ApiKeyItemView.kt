package de.rpicloud.ipv64net.main.views.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.TimedCache
import de.rpicloud.ipv64net.helper.formatGermanTime
import de.rpicloud.ipv64net.helper.formatRelative
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.sr.ipv64.model.ApiKey
import de.sr.ipv64.model.ApiKeyCreated

/** API-Key-Liste (`apiKeys.list()`), 60 s; nach Anlegen/Löschen mit put() aktualisieren. Enthält keine Geheimnisse. */
val ApiKeyListCache: TimedCache<List<ApiKey>> = TimedCache(60_000L)

/** Gültigkeitsdauern beim Anlegen (Sekunden, 0 = unbegrenzt). */
val API_KEY_VALIDITIES: List<Pair<String, Int>> = listOf(
    "Unlimited" to 0,
    "1 day" to 86_400,
    "7 days" to 7 * 86_400,
    "30 days" to 30 * 86_400,
    "90 days" to 90 * 86_400,
    "1 year" to 365 * 86_400,
)

/** Listeneintrag ohne Geheimnis (der volle Key wird nur einmal im SecretOnceDialog angezeigt). */
fun ApiKeyCreated.withoutSecret(): ApiKey = ApiKey(
    id = id, name = name, tokenHint = tokenHint, createdAt = createdAt,
    expiresAt = expiresAt, expired = expired, uses = uses
)

/**
 * Zeile eines API-Keys: Name, Token-Hinweis (Anfang/Ende), Erstellt, Ablauf, Aufrufe.
 * [isCurrent]: der Key, mit dem diese App angemeldet ist → Chip "This app", Löschen nicht möglich.
 */
@Composable
fun ApiKeyItemView(key: ApiKey, isCurrent: Boolean, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    key.name?.takeIf { it.isNotBlank() } ?: "Unnamed key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isCurrent) {
                    StatusChip("This app", colorResource(R.color.ipv64_blue), modifier = Modifier.padding(horizontal = 8.dp))
                }
                if (key.expired == true) {
                    StatusChip("Expired", colorResource(R.color.ipv64_red), modifier = Modifier.padding(horizontal = 8.dp))
                }
                IconButton(onClick = onDelete, enabled = !isCurrent) {
                    Icon(
                        painter = painterResource(id = R.drawable.delete_24px),
                        contentDescription = if (isCurrent) "Key in use by this app" else "Delete API key",
                        tint = if (isCurrent) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else colorResource(R.color.ipv64_red)
                    )
                }
            }
            key.tokenHint?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
            val details = listOfNotNull(
                key.createdAt?.let { "Created ${it.formatGermanTime()}" },
                if (key.expiresAt == null) "Never expires" else "Expires ${key.expiresAt!!.formatGermanTime()}",
                "${key.uses ?: 0} calls",
            )
            details.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            if (isCurrent) {
                Text(
                    "This app uses this key and cannot delete it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
    }
}

// Für "zuletzt verwendet"-Anzeigen in Account-Screens
internal fun kotlin.time.Instant?.relativeOrDash(): String = this?.formatRelative() ?: "–"
