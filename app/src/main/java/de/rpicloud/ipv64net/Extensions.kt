package de.rpicloud.ipv64net

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.roundToInt


fun Context.setSharedString(prefsName: String, key: String, value: String) {
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        .edit().apply { putString(key, value); apply() }
}

fun Context.setSharedBool(prefsName: String, key: String, value: Boolean) {
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        .edit().apply { putBoolean(key, value); apply() }
}

fun Context.setSharedInt(prefsName: String, key: String, value: Int) {
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        .edit().apply { putInt(key, value); apply() }
}

fun Context.getSharedString(prefsName: String, key: String): String {
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        ?.getString(key, "Value is empty!")?.let { return it }
    return "Preference doesn't exist."
}

fun Context.getSharedBool(prefsName: String, key: String): Boolean {
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        ?.getBoolean(key, false)?.let {
            return it
        }
    return false
}

fun Context.getSharedInt(prefsName: String, key: String): Int {
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        ?.getInt(key, 0)?.let {
            return it
        }
    return 0
}

class InputFilterMinMax(private val min: Float, private val max: Float) : InputFilter {

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        try {
            val input = (dest.subSequence(0, dstart).toString() + source + dest.subSequence(
                dend,
                dest.length
            )).toFloat()
            if (isInRange(min, max, input)) return null
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        return ""
    }

    private fun isInRange(a: Float, b: Float, c: Float): Boolean {
        return if (b > a) c in a..b else c in b..a
    }
}

fun String.toEditable(): Editable = Editable.Factory.getInstance().newEditable(this)

/**
 * @author aminography
 */

private val displayMetrics: DisplayMetrics by lazy { Resources.getSystem().displayMetrics }

/**
 * Returns boundary of the screen in pixels (px).
 */
val screenRectPx: Rect
    get() = displayMetrics.run { Rect(0, 0, widthPixels, heightPixels) }

/**
 * Returns boundary of the screen in density independent pixels (dp).
 */
val screenRectDp: RectF
    get() = screenRectPx.run { RectF(0f, 0f, right.px2dp, bottom.px2dp) }

/**
 * Returns boundary of the physical screen including system decor elements (if any) like navigation
 * bar in pixels (px).
 */
val Context.physicalScreenRectPx: Rect
    get() = (applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .run { DisplayMetrics().also { defaultDisplay.getRealMetrics(it) } }
            .run { Rect(0, 0, widthPixels, heightPixels) }

/**
 * Returns boundary of the physical screen including system decor elements (if any) like navigation
 * bar in density independent pixels (dp).
 */
val Context.physicalScreenRectDp: RectF get() = physicalScreenRectPx.run { RectF(0f, 0f, right.px2dp, bottom.px2dp) }

/**
 * Converts any given number from pixels (px) into density independent pixels (dp).
 */
val Number.px2dp: Float get() = this.toFloat() / displayMetrics.density

/**
 * Converts any given number from density independent pixels (dp) into pixels (px).
 */
val Number.dp2px: Int get() = (this.toFloat() * displayMetrics.density).roundToInt()