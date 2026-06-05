package com.example.player

import android.net.Uri

enum class PlaybackMode {
    SEQUENTIAL,   // Tümü Sıra Sıra Çal
    REPEAT_ONE,   // Tekrar Et
    STOP_ON_END   // Bitince Dursun
}

data class BandState(
    val bandIndex: Short,
    val centerFreq: Int,
    val level: Short,
    val minLevel: Short,
    val maxLevel: Short
)

data class PlayerState(
    val currentItem: PlayableItem? = null,
    val isPlaying: Boolean = false,
    val isVideo: Boolean = false,
    val duration: Long = 0,
    val currentPosition: Long = 0,
    val speed: Float = 1.0f,
    val aspect_ratio_mode: Int = 0, // 0: Fit, 1: Stretch, 2: Zoom (Fit by default as requested)
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val isLocked: Boolean = false, // Ekran kitleme
    val autoRotateEnabled: Boolean = true, // Otomatik dondurme
    val currentLyrics: String = "",
    val equalizerEnabled: Boolean = true, // Ekolayzır açık/kapalı durumu
    val equalizerBands: List<BandState> = emptyList(),
    val equalizerPresets: List<String> = emptyList(),
    val activePresetIndex: Int = -1,
    val batteryLevel: Int = 100,
    val currentTimeString: String = "12:00",
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val sleepTimerRemainingSeconds: Int = 0,
    val sleepTimerEnabled: Boolean = false,
    val audioEngine: String = "AAudio (Düşük Gecikme)", // Gelişmiş kulaklık/ses motoru: AAudio, AudioTrack, OpenSL ES
    val renderingDriver: String = "Vulkan GPU (Hızlandırılmış)" // Vulkan GPU, OpenGL ES, Software rendering
)
