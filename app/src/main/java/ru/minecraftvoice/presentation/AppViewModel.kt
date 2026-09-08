package ru.minecraftvoice.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.minecraftvoice.MinecraftVoiceApplication
import ru.minecraftvoice.data.preferences.PreferencesRepository

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PreferencesRepository = (application as MinecraftVoiceApplication).preferences
    val settings = repository.settings
    fun saveNickname(value: String) = viewModelScope.launch { repository.setNickname(value.trim()) }
    fun setMicrophone(value: Boolean) = viewModelScope.launch { repository.setMicrophone(value) }
    fun setMicVolume(value: Float) = viewModelScope.launch { repository.setMicrophoneVolume(value) }
    fun setOutputVolume(value: Float) = viewModelScope.launch { repository.setOutputVolume(value) }
    fun setAutoConnect(value: Boolean) = viewModelScope.launch { repository.setAutoConnect(value) }
    fun setAutoOverlay(value: Boolean) = viewModelScope.launch { repository.setAutoOverlay(value) }
}
