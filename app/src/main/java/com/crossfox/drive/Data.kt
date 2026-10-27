package com.crossfox.drive

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlin.math.max

val Context.dataStore by preferencesDataStore(name = "crossfox_prefs")

/** Una app conocida; puede tener paquetes alternativos (p. ej. Samsung / Google). */
data class AppDef(val label: String, val pkgs: List<String>)

object Catalog {
    val apps = listOf(
        AppDef("Waze", listOf("com.waze")),
        AppDef("inDrive", listOf("sinet.startup.inDriver")),
        AppDef("YouTube Music", listOf("com.google.android.apps.youtube.music")),
        AppDef("Maps", listOf("com.google.android.apps.maps")),
        AppDef("YouTube", listOf("com.google.android.youtube")),
        AppDef("WhatsApp", listOf("com.whatsapp")),
        AppDef("Galería", listOf("com.sec.android.gallery3d", "com.google.android.apps.photos")),
        AppDef("Cámara", listOf("com.sec.android.app.camera", "com.android.camera")),
        AppDef("Archivos", listOf("com.sec.android.app.myfiles", "com.google.android.documentsui", "com.google.android.apps.nbu.files")),
        AppDef("Calculadora", listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator")),
        AppDef("Play Store", listOf("com.android.vending")),
    )
    fun byPkg(p: String): AppDef? = apps.firstOrNull { p in it.pkgs }
    val defaultFavorites: List<String> = apps.map { it.pkgs.first() }
    val WAZE = apps[0]; val INDRIVE = apps[1]; val YTMUSIC = apps[2]; val MAPS = apps[3]
}

data class AppSettings(
    val currentKm: Int = 141_000,      // referencia histórica; el usuario la actualiza a mano
    val lastOilKm: Int = -1,           // -1 = sin datos
    val intervalKm: Int = 8_000,
    val nightOn: Boolean = false,
    val nightLevel: Float = 0.5f,
    val showMusic: Boolean = true,
    val showNav: Boolean = true,
    val showMaint: Boolean = true,
    val favorites: List<String> = Catalog.defaultFavorites,
    val orientation: Int = 0,          // 0 = horizontal fija, 1 = automática
    val taxiSplit: Boolean = false,    // true = INICIAR intenta pantalla dividida directamente
)

object K {
    val currentKm = intPreferencesKey("current_km")
    val lastOilKm = intPreferencesKey("last_oil_km")
    val interval = intPreferencesKey("interval_km")
    val nightOn = booleanPreferencesKey("night_on")
    val nightLevel = floatPreferencesKey("night_level")
    val showMusic = booleanPreferencesKey("show_music")
    val showNav = booleanPreferencesKey("show_nav")
    val showMaint = booleanPreferencesKey("show_maint")
    val favorites = stringPreferencesKey("favorites")
    val orientation = intPreferencesKey("orientation")
    val taxiSplit = booleanPreferencesKey("taxi_split")
}

class SettingsRepo(private val ctx: Context) {
    val flow = ctx.dataStore.data.map { p ->
        AppSettings(
            currentKm = p[K.currentKm] ?: 141_000,
            lastOilKm = p[K.lastOilKm] ?: -1,
            intervalKm = p[K.interval] ?: 8_000,
            nightOn = p[K.nightOn] ?: false,
            nightLevel = p[K.nightLevel] ?: 0.5f,
            showMusic = p[K.showMusic] ?: true,
            showNav = p[K.showNav] ?: true,
            showMaint = p[K.showMaint] ?: true,
            favorites = p[K.favorites]?.split(",")?.filter { it.isNotBlank() } ?: Catalog.defaultFavorites,
            orientation = p[K.orientation] ?: 0,
            taxiSplit = p[K.taxiSplit] ?: false,
        )
    }
    suspend fun edit(block: (MutablePreferences) -> Unit) {
        ctx.dataStore.edit { block(it) }
    }
}

// ---------- Mantenimiento: aceite ----------
enum class Level { NO_DATA, OK, SOON, DUE }

data class OilStatus(val level: Level, val driven: Int = 0, val remaining: Int = 0, val progress: Float = 0f)

fun computeOil(s: AppSettings): OilStatus {
    if (s.lastOilKm < 0 || s.intervalKm <= 0) return OilStatus(Level.NO_DATA)
    val driven = s.currentKm - s.lastOilKm
    if (driven < 0) return OilStatus(Level.NO_DATA)
    val remaining = s.intervalKm - driven
    val progress = (driven.toFloat() / s.intervalKm).coerceIn(0f, 1f)
    val level = when {
        remaining <= 0 -> Level.DUE
        remaining <= max(500, s.intervalKm / 10) -> Level.SOON
        else -> Level.OK
    }
    return OilStatus(level, driven, remaining, progress)
}

/** Devuelve un mensaje de error o null si los valores son coherentes. [last] null = sin dato. */
fun validateOil(current: Int?, last: Int?, interval: Int?): String? = when {
    current == null -> "Escribe el kilometraje actual"
    interval == null || interval < 500 || interval > 30_000 -> "El intervalo debe estar entre 500 y 30,000 km"
    current < 0 || (last != null && last < 0) -> "Los kilómetros no pueden ser negativos"
    current > 1_500_000 -> "Kilometraje actual poco realista"
    last != null && last > current -> "El km del último cambio no puede ser mayor que el km actual"
    else -> null
}
