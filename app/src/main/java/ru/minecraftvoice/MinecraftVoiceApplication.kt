package ru.minecraftvoice

import android.app.Application
import ru.minecraftvoice.data.preferences.PreferencesRepository

class MinecraftVoiceApplication : Application() {
    val preferences by lazy { PreferencesRepository(this) }
}
