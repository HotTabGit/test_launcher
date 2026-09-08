package ru.minecraftvoice.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-wide observable state used by Activity, notification and system overlay. */
object VoiceRuntime {
    private val _state = MutableStateFlow(VoiceConnectionState.DISCONNECTED)
    val state: StateFlow<VoiceConnectionState> = _state
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted
    private val _speechLevel = MutableStateFlow(0f)
    val speechLevel: StateFlow<Float> = _speechLevel
    fun updateState(value: VoiceConnectionState) { _state.value = value }
    fun updateMuted(value: Boolean) { _muted.value = value }
    fun updateSpeechLevel(value: Float) { _speechLevel.value = value }
}
