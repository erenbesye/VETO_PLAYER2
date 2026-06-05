package com.example.player

import android.media.audiofx.Equalizer
import android.util.Log

class EqualizerManager {
    private var equalizer: Equalizer? = null

    fun release() {
        equalizer?.release()
        equalizer = null
    }

    fun initEqualizer(audioSessionId: Int) {
        if (audioSessionId == 0) return
        try {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Ekolayzır başlatılamadı: ${e.message}")
        }
    }

    fun isSupported(): Boolean = equalizer != null

    fun getNumberOfBands(): Short = equalizer?.numberOfBands ?: 0

    fun getBandLevelRange(): Pair<Short, Short> {
        val range = equalizer?.bandLevelRange ?: return Pair(-1500, 1500) // Default range in milliBel
        return Pair(range[0], range[1])
    }

    fun getCenterFreq(band: Short): Int = equalizer?.getCenterFreq(band) ?: 0

    fun getBandLevel(band: Short): Short {
        return equalizer?.getBandLevel(band) ?: 0
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Band seviyesi ayarlanamadı: ${e.message}")
        }
    }

    fun getPresets(): List<String> {
        val count = equalizer?.numberOfPresets ?: return emptyList()
        val presets = mutableListOf<String>()
        for (i in 0 until count) {
            presets.add(equalizer?.getPresetName(i.toShort()) ?: "Preset $i")
        }
        return presets
    }

    fun usePreset(presetIndex: Short) {
        try {
            equalizer?.usePreset(presetIndex)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Preset uygulanamadı: ${e.message}")
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Durum ayarlanamadı: ${e.message}")
        }
    }

    fun isEnabled(): Boolean {
        return equalizer?.enabled ?: false
    }
}
