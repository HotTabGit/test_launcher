package ru.minecraftvoice.domain.model

data class AppPreferences(
    val nickname: String = "",
    val microphoneEnabled: Boolean = true,
    val microphoneVolume: Float = 1f,
    val outputVolume: Float = 1f,
    val overlayX: Int = -1,
    val overlayY: Int = 180,
    val autoConnect: Boolean = false,
    val autoOverlay: Boolean = false
)
