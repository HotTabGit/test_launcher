package ru.minecraftvoice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.minecraftvoice.domain.model.AppPreferences
import ru.minecraftvoice.presentation.AppViewModel
import ru.minecraftvoice.service.VoiceForegroundService
import ru.minecraftvoice.voice.VoiceConnectionState
import ru.minecraftvoice.voice.VoiceRuntime

class MainActivity : ComponentActivity() {
    private var permissionTick by mutableStateOf(0)
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = viewModel()
            val prefs by viewModel.settings.collectAsStateWithLifecycle(initialValue = AppPreferences())
            permissionTick // cause recomposition when system permission changes
            var settingsOpen by remember { mutableStateOf(false) }
            VoiceTheme {
                when {
                    prefs.nickname.isBlank() -> LoginScreen(viewModel::saveNickname)
                    !hasMicrophonePermission() || !Settings.canDrawOverlays(this) -> PermissionScreen(
                        microphoneGranted = hasMicrophonePermission(), overlayGranted = Settings.canDrawOverlays(this),
                        requestMicrophone = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                        requestOverlay = { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                    )
                    settingsOpen -> SettingsScreen(prefs, viewModel, onBack = { settingsOpen = false })
                    else -> HomeScreen(prefs, onEnable = {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        VoiceForegroundService.command(this, VoiceForegroundService.ACTION_START)
                        moveTaskToBack(true)
                    }, onSettings = { settingsOpen = true })
                }
            }
        }
    }
    override fun onResume() { super.onResume(); permissionTick++ }
    private fun hasMicrophonePermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

private val Background = Color(0xFF101417)
private val Surface = Color(0xFF1B2327)
private val Accent = Color(0xFF83C5BE)
private val TextPrimary = Color(0xFFF1F5F4)

@Composable private fun VoiceTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Accent, surface = Surface, background = Background, onBackground = TextPrimary, onSurface = TextPrimary), content = content)

@Composable private fun Page(content: @Composable ColumnScope.() -> Unit) = Box(Modifier.fillMaxSize().background(Background).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center, content = content)
}

@Composable private fun LoginScreen(onLogin: (String) -> Unit) = Page {
    Spacer(Modifier.height(48.dp)); Text("🎙", fontSize = MaterialTheme.typography.displayLarge.fontSize); Spacer(Modifier.height(16.dp))
    Text("Minecraft Voice", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Text("Общий голосовой чат для игры", color = Color(0xFFB7C5C3)); Spacer(Modifier.height(40.dp))
    var nickname by remember { mutableStateOf("") }; var attempted by remember { mutableStateOf(false) }
    OutlinedTextField(nickname, { nickname = it.take(16) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Введите ник из Minecraft") }, isError = attempted && nickname.trim().isEmpty(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), supportingText = { if (attempted && nickname.trim().isEmpty()) Text("Ник не может быть пустым") else Text("До 16 символов") })
    Spacer(Modifier.height(16.dp)); PrimaryButton("Войти") { attempted = true; if (nickname.trim().isNotEmpty()) onLogin(nickname.trim()) }; Spacer(Modifier.height(48.dp))
}

@Composable private fun PermissionScreen(microphoneGranted: Boolean, overlayGranted: Boolean, requestMicrophone: () -> Unit, requestOverlay: () -> Unit) = Page {
    Text("Почти готово", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp))
    Text("Чтобы голосовой чат работал поверх Minecraft, нужны два разрешения.", color = Color(0xFFB7C5C3)); Spacer(Modifier.height(24.dp))
    if (!microphoneGranted) PermissionCard("Доступ к микрофону", "Для голосового общения приложению необходим доступ к микрофону.", "Разрешить микрофон", requestMicrophone)
    else if (!overlayGranted) PermissionCard("Разрешение поверх других приложений", "Это разрешение необходимо для отображения плавающей кнопки управления голосовым чатом поверх Minecraft и других приложений.", "Разрешить Overlay", requestOverlay)
}

@Composable private fun PermissionCard(title: String, message: String, button: String, action: () -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); Text(message, color = Color(0xFFCAD5D3)); Spacer(Modifier.height(20.dp)); PrimaryButton(button, action) } }

@Composable private fun HomeScreen(prefs: AppPreferences, onEnable: () -> Unit, onSettings: () -> Unit) = Page {
    val state by VoiceRuntime.state.collectAsStateWithLifecycle(); val muted by VoiceRuntime.muted.collectAsStateWithLifecycle()
    Spacer(Modifier.height(32.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Minecraft Voice", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Button(onClick = onSettings, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Accent), contentPadding = PaddingValues(8.dp)) { Text("Настройки") } }
    Spacer(Modifier.height(20.dp)); Text("Вы вошли как", color = Color(0xFFB7C5C3)); Text(prefs.nickname, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(28.dp))
    StatusCard(state); Spacer(Modifier.height(22.dp)); PrimaryButton("ВКЛЮЧИТЬ ОВЕРЛЕЙ", onEnable); Spacer(Modifier.height(22.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp)) { InfoRow("Микрофон", if (muted || !prefs.microphoneEnabled) "Выключен" else "Включён"); InfoRow("Громкость", "${(prefs.outputVolume * 100).toInt()}%"); InfoRow("Подключение", state.label) } }
    Spacer(Modifier.height(12.dp)); Button(onClick = onSettings, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Accent), contentPadding = PaddingValues(12.dp)) { Text("Открыть настройки") }; Spacer(Modifier.height(36.dp))
}

@Composable private fun StatusCard(state: VoiceConnectionState) { val (title, color) = when (state) { VoiceConnectionState.CONNECTED -> "Подключено" to Color(0xFF83C5BE); VoiceConnectionState.CONNECTING -> "Подключение..." to Color(0xFFFFD166); VoiceConnectionState.ERROR -> "Ошибка соединения" to Color(0xFFFF8383); else -> "Не подключено" to Color(0xFFB7C5C3) }; Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(22.dp)) { Text("Голосовой чат", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp)); Text(title, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } } }

@Composable private fun InfoRow(name: String, value: String) = Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(name, color = Color(0xFFB7C5C3)); Text(value, fontWeight = FontWeight.Medium) }
private val VoiceConnectionState.label get() = when (this) { VoiceConnectionState.CONNECTED -> "Подключено"; VoiceConnectionState.CONNECTING -> "Подключение..."; VoiceConnectionState.ERROR -> "Ошибка"; VoiceConnectionState.DISCONNECTED -> "Не подключено" }

@Composable private fun SettingsScreen(prefs: AppPreferences, viewModel: AppViewModel, onBack: () -> Unit) = Page {
    Spacer(Modifier.height(28.dp)); Text("Настройки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(20.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp)) {
        SettingText("Minecraft ник", prefs.nickname); HorizontalDivider(Modifier.padding(vertical = 12.dp)); ToggleRow("Микрофон", prefs.microphoneEnabled, viewModel::setMicrophone)
        VolumeRow("Громкость микрофона", prefs.microphoneVolume, viewModel::setMicVolume); VolumeRow("Громкость собеседников", prefs.outputVolume, viewModel::setOutputVolume)
        ToggleRow("Автоподключение", prefs.autoConnect, viewModel::setAutoConnect); ToggleRow("Автоматически включать Overlay", prefs.autoOverlay, viewModel::setAutoOverlay)
        SettingText("Позиция Overlay", if (prefs.overlayX < 0) "Стандартная" else "${prefs.overlayX}, ${prefs.overlayY}"); SettingText("Версия приложения", "0.1.0")
    } }; Spacer(Modifier.height(16.dp)); PrimaryButton("Назад", onBack); Spacer(Modifier.height(32.dp))
}
@Composable private fun SettingText(name: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(name, color = Color(0xFFB7C5C3)); Text(value) } }
@Composable private fun ToggleRow(name: String, checked: Boolean, update: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(name); Switch(checked, update) } }
@Composable private fun VolumeRow(name: String, value: Float, update: (Float) -> Unit) { Text("$name ${(value * 100).toInt()}%", modifier = Modifier.padding(top = 12.dp)); Slider(value, update) }
@Composable private fun PrimaryButton(text: String, onClick: () -> Unit) = Button(onClick, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)) { Text(text, fontWeight = FontWeight.Bold) }
