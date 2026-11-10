package de.rpicloud.ipv64net.main.views

import android.content.Context
import android.content.Intent
import android.widget.Toast
import de.rpicloud.ipv64net.helper.PreferencesManager
import de.rpicloud.ipv64net.helper.findActivity
import de.rpicloud.ipv64net.main.startup.activity.LoginActivity
import de.rpicloud.ipv64net.models.User
import de.rpicloud.ipv64net.models.UserRemovalResult

// SavedStateHandle-Schlüssel: Meldung, die die Account-Liste nach dem Entfernen als Snackbar zeigt
internal const val ACCOUNT_REMOVED_MESSAGE = "ACCOUNT_REMOVED_MESSAGE"

// Text für den Bestätigungsdialog; nennt die Folgen, falls der aktive Account entfernt wird
internal fun accountRemovalConfirmText(user: User, isActive: Boolean, remaining: List<User>): String {
    val base = "\"${user.Username}\" will only be removed from this device. " +
            "The account and its API key stay valid on ipv64.net, and you can add it again at any time. " +
            "Push notifications for this account keep arriving on this device until you delete its device " +
            "channel under Channels (do this before removing)."
    if (!isActive) return base
    val next = remaining.firstOrNull { it.id != user.id }
    return if (next != null) {
        "$base\n\nThis is the active account. The app will switch to \"${next.Username}\"."
    } else {
        "$base\n\nThis is the last account. You will be logged out."
    }
}

// Meldung nach dem Entfernen (null, wenn nichts anzuzeigen ist)
internal fun accountRemovalMessage(user: User, result: UserRemovalResult): String? = when (result) {
    UserRemovalResult.NotFound -> null
    UserRemovalResult.Removed -> "\"${user.Username}\" removed from this app."
    is UserRemovalResult.SwitchedTo -> "\"${user.Username}\" removed. Switched to account \"${result.user.Username}\"."
    UserRemovalResult.LoggedOut -> "\"${user.Username}\" removed. You have been logged out."
}

/**
 * Entfernt [user] nur lokal (keine Server-API). War es der letzte Account, geht es direkt zum Login;
 * sonst wird [onRemaining] mit der anzuzeigenden Meldung (null, wenn nichts anzuzeigen ist) aufgerufen.
 */
internal fun removeAccount(ctx: Context, user: User, onRemaining: (message: String?) -> Unit) {
    val result = User.remove(user.id)
    val message = accountRemovalMessage(user, result)
    if (result == UserRemovalResult.LoggedOut) {
        logoutAfterLastAccountRemoved(ctx, message)
    } else {
        onRemaining(message)
    }
}

/**
 * Logout nach dem Entfernen des letzten Accounts, entspricht dem Logout in [SettingsView]:
 * Sperrbildschirm aus, zurück zur LoginActivity. APIKEY und Caches hat [User.remove] bereits geleert.
 */
private fun logoutAfterLastAccountRemoved(ctx: Context, message: String?) {
    // SettingsView setzt den Schalter über compose-preference (Default-SharedPreferences) und spiegelt ihn
    // in PreferencesManager; hier beide Speicherorte direkt zurücksetzen
    PreferencesManager.saveBool(ctx, "LOCKSCREEN_ENABLED", false)
    ctx.getSharedPreferences("${ctx.packageName}_preferences", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("LOCKSCREEN_ENABLED", false)
        .apply()

    if (message != null) {
        Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_LONG).show()
    }

    val activity = ctx.findActivity()
    val intent = Intent(activity, LoginActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    activity?.startActivity(intent)
    activity?.finish()
}
