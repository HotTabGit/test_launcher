package ru.minecraftvoice.voice

import kotlinx.coroutines.flow.StateFlow

enum class VoiceConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
interface VoiceClient {
    val connectionState: StateFlow<VoiceConnectionState>
    val isMuted: StateFlow<Boolean>
    /** Normalized local microphone activity for the overlay animation. */
    val speechLevel: StateFlow<Float>
    suspend fun connect(room: String)
    suspend fun disconnect()
    fun mute()
    fun unmute()
    fun setMicrophoneVolume(value: Float)
    fun setOutputVolume(value: Float)
}
