package com.example.player

import android.net.Uri
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class LrcLine(
    val timestampMs: Long,
    val text: String
)

data class PlayableItem(
    val uri: Uri,
    val title: String,
    val artist: String = "Bilinmeyen Sanatçı",
    val duration: Long = 0,
    val isVideo: Boolean = false,
    val albumArtUri: Uri? = null,
    val lrcLines: List<LrcLine> = emptyList(),
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val size: Long = 0
)

object LyricsParser {
    fun parseLrc(inputStream: InputStream): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                val regex = Regex("\\[(\\d+):(\\d+)[\\.:](\\d+)\\](.*)")
                val match = regex.find(currentLine)
                if (match != null) {
                    val min = match.groupValues[1].toLong()
                    val sec = match.groupValues[2].toLong()
                    val msGroup = match.groupValues[3]
                    val ms = if (msGroup.length == 2) msGroup.toLong() * 10 else msGroup.toLong()
                    val text = match.groupValues[4].trim()
                    val timestamp = min * 60000 + sec * 1000 + ms
                    lines.add(LrcLine(timestamp, text))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return lines.sortedBy { it.timestampMs }
    }

    fun generateMockLyrics(durationMs: Long, title: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val interval = 6000L
        val sampleLyrics = listOf(
            "♫ [Enstrümantal Giriş] ♫",
            "Merhaba, Medya Oynatıcı'ya hoş geldiniz!",
            "Bu şarkı tüm ses formatlarını mükemmel kalitede destekler.",
            "Ekolayzır panelini kullanarak sesi detaylandırabilirsiniz.",
            "Kayıpsız FLAC, WAV, AAC, MP3 hepsi çalınıyor...",
            "Arka planda çalma özelliği şu an aktiftir.",
            "Kilit ekranından da müziği değiştirebilirsiniz.",
            "♫ [Enstrümantal Solo - Bass & Melodi] ♫",
            "Müzik, duyguların ses kazanmış halidir...",
            "Türkçe arayüzümüzle sadelik odaklı tasarım.",
            "Şimdi ses frekanslarını hissetme zamanı!",
            "Eşzamanlı lirik desteği de başarıyla çalışıyor.",
            "Otomatik karartma ve şık koyu temalar aktif.",
            "Dinlediğiniz için teşekkürler, keyifli dinlemeler! ♪",
            "♫ [Sönümlenme - Bitiş] ♫"
        )
        var currentTime = 1000L
        var index = 0
        val effectiveDuration = if (durationMs <= 0) 180000L else durationMs
        while (currentTime < effectiveDuration && index < sampleLyrics.size) {
            lines.add(LrcLine(currentTime, sampleLyrics[index]))
            currentTime += interval
            index++
        }
        return lines
    }
}
