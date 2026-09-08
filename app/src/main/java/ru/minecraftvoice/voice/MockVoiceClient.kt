package ru.minecraftvoice.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Replace this class with a LiveKit-backed implementation; no server secrets belong in the APK. */
class MockVoiceClient : VoiceClient {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _connectionState = MutableStateFlow(VoiceConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<VoiceConnectionState> = _connectionState
    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted
    private val _speechLevel = MutableStateFlow(0f)
    override val speechLevel: StateFlow<Float> = _speechLevel
    private var recorder: AudioRecord? = null
    private var recordJob: Job? = null
    private var microphoneGain = 1f

    override suspend fun connect(room: String) {
        if (_connectionState.value == VoiceConnectionState.CONNECTED) return
        _connectionState.value = VoiceConnectionState.CONNECTING
        delay(350)
        try { startCapture(); _connectionState.value = VoiceConnectionState.CONNECTED }
        catch (_: SecurityException) { _connectionState.value = VoiceConnectionState.ERROR }
        catch (_: IllegalStateException) { _connectionState.value = VoiceConnectionState.ERROR }
    }
    override suspend fun disconnect() { stopCapture(); _connectionState.value = VoiceConnectionState.DISCONNECTED }
    override fun mute() { _isMuted.value = true }
    override fun unmute() { _isMuted.value = false }
    override fun setMicrophoneVolume(value: Float) { microphoneGain = value.coerceIn(0f, 1f) }
    override fun setOutputVolume(value: Float) = Unit
    private fun startCapture() {
        if (recorder != null) return
        val rate = 48_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0) { "Microphone is unavailable" }
        val localRecorder = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
        check(localRecorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone cannot be initialized" }
        localRecorder.startRecording(); recorder = localRecorder
        recordJob = scope.launch {
            val buffer = ShortArray(min / 2)
            while (isActive) {
                val count = localRecorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0 && !_isMuted.value) {
                    var total = 0L
                    for (i in 0 until count) total += abs(buffer[i].toInt())
                    _speechLevel.value = ((total.toFloat() / count / Short.MAX_VALUE) * microphoneGain).coerceIn(0f, 1f)
                } else _speechLevel.value = 0f
            }
        }
    }
    private fun stopCapture() {
        recordJob?.cancel(); recordJob = null; _speechLevel.value = 0f
        recorder?.runCatching { stop() }; recorder?.release(); recorder = null
    }
}
