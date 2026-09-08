package ru.minecraftvoice.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.minecraftvoice.MainActivity
import ru.minecraftvoice.MinecraftVoiceApplication
import ru.minecraftvoice.R
import ru.minecraftvoice.overlay.OverlayController
import ru.minecraftvoice.voice.MockVoiceClient
import ru.minecraftvoice.voice.VoiceClient
import ru.minecraftvoice.voice.VoiceRuntime

class VoiceForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client: VoiceClient by lazy { MockVoiceClient() }
    private lateinit var overlay: OverlayController

    override fun onCreate() {
        super.onCreate()
        createChannel()
        overlay = OverlayController(this, serviceScope)
        serviceScope.launch { client.connectionState.collectLatest { VoiceRuntime.updateState(it); updateNotification() } }
        serviceScope.launch { client.isMuted.collectLatest { VoiceRuntime.updateMuted(it); updateNotification() } }
        serviceScope.launch { client.speechLevel.collectLatest { VoiceRuntime.updateSpeechLevel(it) } }
        serviceScope.launch {
            (application as MinecraftVoiceApplication).preferences.settings.collectLatest { settings ->
                if (settings.microphoneEnabled) client.unmute() else client.mute()
                client.setMicrophoneVolume(settings.microphoneVolume)
                client.setOutputVolume(settings.outputVolume)
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, notification())
                overlay.show()
                serviceScope.launch { client.connect(ROOM) }
            }
            ACTION_TOGGLE_MUTE -> serviceScope.launch {
                (application as MinecraftVoiceApplication).preferences.setMicrophone(VoiceRuntime.muted.value)
            }
            ACTION_DISCONNECT -> serviceScope.launch { client.disconnect() }
            ACTION_CLOSE -> { overlay.hide(); serviceScope.launch { client.disconnect() }; stopSelf() }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { overlay.hide(); serviceScope.launch { client.disconnect() }; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher).setContentTitle("Голосовой чат активен")
        .setContentText(if (VoiceRuntime.muted.value) "Микрофон выключен" else "Подключено к global")
        .setOngoing(true).setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .addAction(0, if (VoiceRuntime.muted.value) "Включить микрофон" else "Выключить микрофон", servicePending(ACTION_TOGGLE_MUTE, 1))
        .addAction(0, "Отключиться", servicePending(ACTION_DISCONNECT, 2)).build()
    private fun servicePending(action: String, id: Int) = PendingIntent.getService(this, id, Intent(this, VoiceForegroundService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun updateNotification() = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification())
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Голосовой чат", NotificationManager.IMPORTANCE_LOW))
    }
    companion object {
        const val ACTION_START = "ru.minecraftvoice.START"; const val ACTION_TOGGLE_MUTE = "ru.minecraftvoice.TOGGLE_MUTE"
        const val ACTION_DISCONNECT = "ru.minecraftvoice.DISCONNECT"; const val ACTION_CLOSE = "ru.minecraftvoice.CLOSE"
        private const val CHANNEL_ID = "voice_chat"; private const val NOTIFICATION_ID = 42; const val ROOM = "global"
        fun command(context: Context, action: String) = ContextCompat.startForegroundService(context, Intent(context, VoiceForegroundService::class.java).setAction(action))
    }
}
