package io.github.veitkramerschoeggl.trainingtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The prototype's three accent colors (the dots next to the title). */
enum class AccentColor(val key: String) {
    GREEN("green"),
    VIOLET("violet"),
    BLUE("blue"),
    ;

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: GREEN
    }
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small app settings (key-value, DataStore). Training data lives in the Room database. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val accentColor: Flow<AccentColor> = dataStore.data.map { AccentColor.fromKey(it[ACCENT_COLOR]) }

    suspend fun setAccentColor(color: AccentColor) {
        dataStore.edit { it[ACCENT_COLOR] = color.key }
    }

    /**
     * Remembers the version that ran last and returns the previous value, so the app can say
     * "updated to …" after a self-update.
     */
    suspend fun swapLastRunVersionCode(current: Int): Int? {
        val previous = dataStore.data.first()[LAST_RUN_VERSION_CODE]
        if (previous != current) dataStore.edit { it[LAST_RUN_VERSION_CODE] = current }
        return previous
    }

    private companion object {
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val LAST_RUN_VERSION_CODE = intPreferencesKey("last_run_version_code")
    }
}
