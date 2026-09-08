package ru.minecraftvoice.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.minecraftvoice.MinecraftVoiceApplication
import ru.minecraftvoice.service.VoiceForegroundService
import ru.minecraftvoice.voice.VoiceConnectionState
import ru.minecraftvoice.voice.VoiceRuntime
import kotlin.math.abs

class OverlayController(private val context: Context, private val scope: CoroutineScope) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val preferences = (context.applicationContext as MinecraftVoiceApplication).preferences
    private var root: FrameLayout? = null
    private var bubble: TextView? = null
    private var menu: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var isShown = false

    fun show() {
        if (isShown || !Settings.canDrawOverlays(context)) return
        scope.launch { preferences.settings.collectLatest { settings -> if (!isShown) addOverlay(settings.overlayX, settings.overlayY, settings.microphoneVolume, settings.outputVolume) } }
        scope.launch { VoiceRuntime.muted.collectLatest { render() } }
        scope.launch { VoiceRuntime.state.collectLatest { render() } }
        scope.launch { VoiceRuntime.speechLevel.collectLatest { render() } }
    }
    private fun addOverlay(savedX: Int, savedY: Int, mic: Float, output: Float) {
        if (isShown) return
        val frame = FrameLayout(context)
        val round = TextView(context).apply {
            text = "🎤"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = circle(Color.rgb(23, 91, 86)); elevation = 12f
        }
        frame.addView(round, FrameLayout.LayoutParams(dp(56), dp(56)))
        val controlMenu = buildMenu(mic, output).also { it.visibility = View.GONE }
        frame.addView(controlMenu, FrameLayout.LayoutParams(dp(250), WindowManager.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(64) })
        val lp = WindowManager.LayoutParams(dp(56), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = if (savedX < 0) 16 else savedX; y = savedY }
        round.setOnTouchListener(dragListener(lp, round, controlMenu))
        root = frame; bubble = round; menu = controlMenu; params = lp
        windowManager.addView(frame, lp); isShown = true; render()
    }
    private fun buildMenu(mic: Float, output: Float): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(Color.rgb(20, 24, 28), dp(4), Color.rgb(67, 78, 78))
        elevation = 14f
        addView(label("VOICE CHAT", 18f, Color.rgb(240, 244, 235)))
        addView(label("CONNECTED  /  READY", 10f, Color.rgb(127, 198, 118)))
        addView(separator())
        addView(button("[ MIC ]    MUTE / UNMUTE", Color.rgb(43, 65, 49)) { VoiceForegroundService.command(context, VoiceForegroundService.ACTION_TOGGLE_MUTE) })
        addView(slider("MIC LEVEL", mic) { value -> scope.launch { preferences.setMicrophoneVolume(value) } })
        addView(slider("PLAYER VOLUME", output) { value -> scope.launch { preferences.setOutputVolume(value) } })
        addView(separator())
        addView(button("[ SET ]    SETTINGS", Color.rgb(34, 41, 44)) { context.startActivity(android.content.Intent(context, ru.minecraftvoice.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) })
        addView(button("[ X ]      DISCONNECT", Color.rgb(57, 39, 39)) { VoiceForegroundService.command(context, VoiceForegroundService.ACTION_DISCONNECT) })
        addView(button("[ - ]      CLOSE OVERLAY", Color.rgb(34, 41, 44)) { VoiceForegroundService.command(context, VoiceForegroundService.ACTION_CLOSE) })
    }
    private fun dragListener(lp: WindowManager.LayoutParams, view: View, controls: View): View.OnTouchListener {
        var initialX = 0; var initialY = 0; var initialTouchX = 0f; var initialTouchY = 0f
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { initialX = lp.x; initialY = lp.y; initialTouchX = event.rawX; initialTouchY = event.rawY; true }
                MotionEvent.ACTION_MOVE -> { lp.x = initialX + (event.rawX - initialTouchX).toInt(); lp.y = initialY + (event.rawY - initialTouchY).toInt(); root?.let { windowManager.updateViewLayout(it, lp) }; true }
                MotionEvent.ACTION_UP -> { val moved = abs(event.rawX - initialTouchX) > 10 || abs(event.rawY - initialTouchY) > 10; if (moved) scope.launch { preferences.setOverlayPosition(lp.x, lp.y) } else { val open = controls.visibility != View.VISIBLE; controls.visibility = if (open) View.VISIBLE else View.GONE; lp.width = if (open) dp(320) else dp(56); root?.let { windowManager.updateViewLayout(it, lp) } }; true }
                else -> false
            }
        }
    }
    private fun render() {
        val muted = VoiceRuntime.muted.value
        bubble?.text = when { muted -> "🔇"; VoiceRuntime.state.value == VoiceConnectionState.ERROR -> "!"; else -> "🎤" }
        val speaking = VoiceRuntime.speechLevel.value > 0.025f && !muted
        bubble?.background = circle(when { muted -> Color.rgb(105, 75, 75); VoiceRuntime.state.value == VoiceConnectionState.CONNECTED && speaking -> Color.rgb(35, 170, 145); VoiceRuntime.state.value == VoiceConnectionState.CONNECTED -> Color.rgb(23, 125, 110); else -> Color.rgb(55, 68, 73) })
        bubble?.animate()?.scaleX(if (speaking) 1.08f else 1f)?.scaleY(if (speaking) 1.08f else 1f)?.setDuration(140)?.start()
    }
    fun hide() { root?.let { runCatching { windowManager.removeView(it) } }; root = null; bubble = null; menu = null; isShown = false }
    private fun label(text: String, size: Float = 15f, color: Int = Color.WHITE) = TextView(context).apply { this.text = text; setTextColor(color); textSize = size; typeface = android.graphics.Typeface.MONOSPACE; setPadding(0, 0, 0, dp(5)) }
    private fun button(text: String, color: Int, action: () -> Unit) = TextView(context).apply { this.text = text; setTextColor(Color.rgb(224, 232, 220)); textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE; setPadding(dp(10), dp(11), dp(10), dp(11)); background = rounded(color, dp(2), Color.rgb(62, 73, 73)); setOnClickListener { action() } }
    private fun slider(title: String, value: Float, change: (Float) -> Unit) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(2)); addView(label("$title  ${(value * 100).toInt()}%", 10f, Color.rgb(183, 197, 181))); addView(SeekBar(context).apply { max = 100; progress = (value * 100).toInt(); progressDrawable?.setTint(Color.rgb(115, 181, 102)); thumb?.setTint(Color.rgb(177, 220, 113)); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, from: Boolean) { if (from) { (s?.parent as? LinearLayout)?.getChildAt(0)?.let { (it as TextView).text = "$title  $p%" }; change(p / 100f) } }; override fun onStartTrackingTouch(s: SeekBar?) = Unit; override fun onStopTrackingTouch(s: SeekBar?) = Unit }) }) }
    private fun separator() = View(context).apply { setBackgroundColor(Color.rgb(54, 65, 64)); layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(8); bottomMargin = dp(5) } }
    private fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    private fun rounded(color: Int, radius: Int = dp(12), stroke: Int? = null) = GradientDrawable().apply { cornerRadius = radius.toFloat(); setColor(color); stroke?.let { setStroke(dp(1), it) } }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
