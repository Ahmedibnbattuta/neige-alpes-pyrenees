package fr.neige.stations.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale
import kotlin.math.roundToInt

/** Système d'unités affiché. Les données restent stockées en cm / m / °C. */
enum class UnitSystem {
    METRIC, US;

    val depthUnit: String get() = if (this == METRIC) "cm" else "in"
    val altitudeUnit: String get() = if (this == METRIC) "m" else "ft"

    companion object {
        /** Par défaut : unités américaines pour les téléphones réglés sur les États-Unis. */
        fun default(locale: Locale = Locale.getDefault()): UnitSystem =
            if (locale.country in setOf("US", "LR", "MM")) US else METRIC
    }
}

val LocalUnits = staticCompositionLocalOf { UnitSystem.METRIC }

private const val CM_PER_IN = 2.54
private const val FT_PER_M = 3.28084

/** Hauteur de neige en cm -> texte dans l'unité choisie. */
fun fmtDepth(cm: Int?, u: UnitSystem): String = when {
    cm == null -> "–"
    u == UnitSystem.METRIC -> "$cm cm"
    else -> "${(cm / CM_PER_IN).roundToInt()} in"
}

/** Chutes de neige prévues (cm) -> "+12 cm" / "+4.7 in". */
fun fmtFall(cm: Double?, u: UnitSystem): String {
    if (cm == null) return "–"
    if (u == UnitSystem.METRIC) return "+${cm.roundToInt()} cm"
    val inches = cm / CM_PER_IN
    return if (inches < 10) "+" + String.format(Locale.US, "%.1f", inches) + " in" else "+${inches.roundToInt()} in"
}

/** Altitude en m -> "3456 m" / "11,339 ft". */
fun fmtAlt(m: Int, u: UnitSystem): String =
    if (u == UnitSystem.METRIC) "$m m" else String.format(Locale.US, "%,d ft", (m * FT_PER_M).roundToInt())

fun fmtTemp(c: Double, u: UnitSystem): String =
    if (u == UnitSystem.METRIC) "${c.roundToInt()} °C" else "${(c * 9 / 5 + 32).roundToInt()} °F"

// Conversions pour la saisie des relevés
fun cmToInput(cm: Int, u: UnitSystem): Int = if (u == UnitSystem.METRIC) cm else (cm / CM_PER_IN).roundToInt()
fun inputToCm(v: Int, u: UnitSystem): Int = if (u == UnitSystem.METRIC) v else (v * CM_PER_IN).roundToInt()
fun mToInput(m: Int, u: UnitSystem): Int = if (u == UnitSystem.METRIC) m else (m * FT_PER_M).roundToInt()
fun inputToM(v: Int, u: UnitSystem): Int = if (u == UnitSystem.METRIC) v else (v / FT_PER_M).roundToInt()
