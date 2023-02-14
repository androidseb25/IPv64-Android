package de.rpicloud.ipv64net

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import java.sql.Date
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class Functions {

    fun setOrientaiton(activity: AppCompatActivity, isPortrait: Boolean) {
        activity.requestedOrientation = if (isPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    fun GetStringAsDateFromDB(date: Date): String {
        val defaultZoneId: ZoneId = ZoneId.of("Europe/Berlin")
        val localDate = date.toInstant().atZone(defaultZoneId).toLocalDateTime();
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMAN)
        return localDate.format(formatter);
    }

    fun GetFormettedDateString(dbString: String): String {
        val defaultZoneId: ZoneId = ZoneId.of("Europe/Berlin")
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss", Locale.GERMAN)
        val formatterDb = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMAN)
        val date = LocalDateTime.parse(dbString, formatterDb)
        return date.format(formatter);
    }

    fun GetFormettedOnlyDateString(dbString: String): String {
        val defaultZoneId: ZoneId = ZoneId.of("Europe/Berlin")
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)
        val formatterDb = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMAN)
        val date = LocalDateTime.parse(dbString, formatterDb)
        return date.format(formatter);
    }

    /*fun GetConsumptionValue(consumptionType: ConsumptionType, value: Double): String {
        return when (consumptionType) {
            ConsumptionType.Gas-> {
                "%.2f ".format(value)
            }
            ConsumptionType.Strom -> {
                "%.2f ".format(value)
            }
            ConsumptionType.Kaltwasser -> {
                "%.2f ".format(value)
            }
            ConsumptionType.Warmwasser -> {
                "%.2f ".format(value)
            }
            ConsumptionType.Muell -> {
                "%.0f".format(value)
            }
        }
    }

    fun GetConsumptionColor(consumptionType: ConsumptionType): Int {
        return when (consumptionType) {
            ConsumptionType.Gas -> {
                R.color.gas
            }
            ConsumptionType.Strom -> {
                R.color.strom
            }
            ConsumptionType.Kaltwasser -> {
                R.color.kaltwasser
            }
            ConsumptionType.Warmwasser -> {
                R.color.warmwasser
            }
            ConsumptionType.Muell -> {
                R.color.muell
            }
        }
    }

    fun GetConsumptionName(consumptionType: ConsumptionType): String {
        return when (consumptionType) {
            ConsumptionType.Gas -> {
                ConsumptionType.Gas.name
            }
            ConsumptionType.Strom -> {
                ConsumptionType.Strom.name
            }
            ConsumptionType.Kaltwasser -> {
                ConsumptionType.Kaltwasser.name
            }
            ConsumptionType.Warmwasser -> {
                ConsumptionType.Warmwasser.name
            }
            ConsumptionType.Muell -> {
                "Müll"
            }
        }
    }

    fun GetConsumptionUnit(consumptionType: ConsumptionType): String {
        return when (consumptionType) {
            ConsumptionType.Gas -> {
                ConsumptionUnit.KubikUnit.Unit
            }
            ConsumptionType.Strom -> {
                ConsumptionUnit.StromUnit.Unit
            }
            ConsumptionType.Kaltwasser -> {
                ConsumptionUnit.KubikUnit.Unit
            }
            ConsumptionType.Warmwasser -> {
                ConsumptionUnit.KubikUnit.Unit
            }
            ConsumptionType.Muell -> {
                ConsumptionUnit.MuellUnit.Unit
            }
        }
    }

    fun GetConsumptionIcon(consumptionType: ConsumptionType): Int {
        return when (consumptionType) {
            ConsumptionType.Gas -> {
                R.drawable.ic_fire_24
            }
            ConsumptionType.Strom -> {
                R.drawable.ic_bolt_24
            }
            ConsumptionType.Kaltwasser -> {
                R.drawable.ic_water_24
            }
            ConsumptionType.Warmwasser -> {
                R.drawable.ic_water_24
            }
            ConsumptionType.Muell -> {
                R.drawable.ic_delete_24
            }
        }
    }

    fun GetConsumtionType(id: Int): ConsumptionType {
        return when (id) {
            ConsumptionType.Gas.CType -> {
                ConsumptionType.Gas
            }
            ConsumptionType.Strom.CType -> {
                ConsumptionType.Strom
            }
            ConsumptionType.Kaltwasser.CType -> {
                ConsumptionType.Kaltwasser
            }
            ConsumptionType.Warmwasser.CType -> {
                ConsumptionType.Warmwasser
            }
            ConsumptionType.Muell.CType -> {
                ConsumptionType.Muell
            }
            else -> {
                ConsumptionType.Gas
            }
        }
    }*/
}

fun Int.getDarkOrLight(ctx: Context): Int {
    val lum = ColorUtils.calculateLuminance(this)

    return if (lum < 0.4) {
        ContextCompat.getColor(ctx, R.color.white)
    } else {
        ContextCompat.getColor(ctx, R.color.black)
    }
}