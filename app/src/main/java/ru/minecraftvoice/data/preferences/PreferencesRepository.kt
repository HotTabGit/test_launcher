package ru.minecraftvoice.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.minecraftvoice.domain.model.AppPreferences

private val Context.dataStore by preferencesDataStore("minecraft_voice_settings")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val nickname = stringPreferencesKey("nickname")
        val microphoneEnabled = booleanPreferencesKey("microphone_enabled")
        val microphoneVolume = floatPreferencesKey("microphone_volume")
        val outputVolume = floatPreferencesKey("output_volume")
        val overlayX = intPreferencesKey("overlay_x")
        val overlayY = intPreferencesKey("overlay_y")
        val autoConnect = booleanPreferencesKey("auto_connect")
        val autoOverlay = booleanPreferencesKey("auto_overlay")
    }
    val settings: Flow<AppPreferences> = context.dataStore.data.map { p ->
        AppPreferences(p[Keys.nickname].orEmpty(), p[Keys.microphoneEnabled] ?: true,
            p[Keys.microphoneVolume] ?: 1f, p[Keys.outputVolume] ?: 1f,
            p[Keys.overlayX] ?: -1, p[Keys.overlayY] ?: 180,
            p[Keys.autoConnect] ?: false, p[Keys.autoOverlay] ?: false)
    }
    suspend fun setNickname(value: String) = context.dataStore.edit { it[Keys.nickname] = value }
    suspend fun setMicrophone(enabled: Boolean) = context.dataStore.edit { it[Keys.microphoneEnabled] = enabled }
    suspend fun setMicrophoneVolume(value: Float) = context.dataStore.edit { it[Keys.microphoneVolume] = value.coerceIn(0f, 1f) }
    suspend fun setOutputVolume(value: Float) = context.dataStore.edit { it[Keys.outputVolume] = value.coerceIn(0f, 1f) }
    suspend fun setOverlayPosition(x: Int, y: Int) = context.dataStore.edit { it[Keys.overlayX] = x; it[Keys.overlayY] = y }
    suspend fun setAutoConnect(value: Boolean) = context.dataStore.edit { it[Keys.autoConnect] = value }
    suspend fun setAutoOverlay(value: Boolean) = context.dataStore.edit { it[Keys.autoOverlay] = value }
}
