package de.rpicloud.ipv64net.helper

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.core.net.toUri

/**
 * Text in die Zwischenablage kopieren.
 *
 * @param label Beschreibung des Inhalts (z. B. "Update URL"), wird vom System angezeigt.
 * @param sensitive true für Geheimnisse (Tokens, API-Keys, Update-URLs): Android 13+ blendet die Vorschau aus
 *   (ClipDescription.EXTRA_IS_SENSITIVE).
 *
 * Unter Android 13 zeigt das System selbst eine Bestätigung; darunter erscheint ein Toast "Copied".
 * Der kopierte Text wird nie geloggt.
 */
fun copyText(ctx: Context, label: String, text: String, sensitive: Boolean = false) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= 33) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            } else {
                // Gleicher Schlüssel wie EXTRA_IS_SENSITIVE; ältere Systeme bzw. Tastaturen werten ihn teils aus
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
    }
    cm.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) {
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }
}

/**
 * URL im Browser bzw. in der passenden App öffnen.
 * @return false, wenn keine App die URL öffnen kann (dann erscheint ein Toast).
 */
fun openUrl(ctx: Context, url: String): Boolean {
    return try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "No app found to open this link", Toast.LENGTH_SHORT).show()
        false
    }
}

/**
 * Text über das System-Teilen-Menü teilen (z. B. URL einer Statusseite). Nicht für Geheimnisse verwenden.
 */
fun shareText(ctx: Context, text: String, title: String? = null) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (title != null) putExtra(Intent.EXTRA_SUBJECT, title)
    }
    try {
        ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "No app found to share", Toast.LENGTH_SHORT).show()
    }
}
