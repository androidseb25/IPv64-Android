package de.rpicloud.ipv64net

import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.rpicloud.ipv64net.helper.PreferencesManager
import de.rpicloud.ipv64net.helper.SCREENSHOT_PROTECTION_ENABLED
import de.rpicloud.ipv64net.main.views.SettingsView
import de.rpicloud.ipv64net.main.views.common.SecureWindowEffect
import de.rpicloud.ipv64net.main.views.common.screenshotDialogPolicy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenshotProtectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun protectionDefaultsOnAndUpdatesWindowFlagAndDialogPolicy() {
        val key = SCREENSHOT_PROTECTION_ENABLED
        val prefs = compose.activity.getSharedPreferences(key, Context.MODE_PRIVATE)
        val original = if (prefs.contains(key)) prefs.getBoolean(key, true) else null
        var dialogPolicy: SecureFlagPolicy? = null

        fun checkProtection(enabled: Boolean) {
            compose.runOnIdle {
                val flags = compose.activity.window.attributes.flags
                assertEquals(enabled, flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                assertEquals(if (enabled) SecureFlagPolicy.SecureOn else SecureFlagPolicy.SecureOff, dialogPolicy)
            }
        }

        try {
            prefs.edit().remove(key).apply()
            compose.setContent {
                SecureWindowEffect()
                val policy = screenshotDialogPolicy()
                SideEffect { dialogPolicy = policy }
                SettingsView(rememberNavController(), PaddingValues())
            }
            val label = "Screenshot protection"
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
            val setting = compose.onNodeWithText(label)
            setting.assertIsOn()
            checkProtection(true)

            setting.performClick().assertIsOff()
            checkProtection(false)
            assertEquals(false, prefs.getBoolean(key, true))

            setting.performClick().assertIsOn()
            checkProtection(true)
        } finally {
            if (original == null) prefs.edit().remove(key).apply()
            else PreferencesManager.saveBool(compose.activity, key, original)
        }
    }
}
