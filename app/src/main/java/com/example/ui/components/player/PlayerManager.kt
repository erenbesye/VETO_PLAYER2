package com.example.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*

@OptIn(UnstableApi::class)
class PlayerManager private constructor(private val context: Context) {

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _playlist = MutableStateFlow<List<PlayableItem>>(emptyList())
    val playlist: StateFlow<List<PlayableItem>> = _playlist.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private val equalizerManager = EqualizerManager()
    private var sleepTimerJob: Job? = null
    private var lastSeekTime = 0L

    var speed: Float = 1.0f
        private set

    var skipIntervalSeconds = 10

    companion object {
        @Volatile
        private var INSTANCE: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PlayerManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    init {
        initPlayer()
        startPositionTracker()
        registerStatusReceivers()
    }

    private fun initPlayer() {
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000,  // minBufferMs (1.0 seconds)
                3000,  // maxBufferMs (3.0 seconds)
                0,     // bufferForPlaybackMs (0 ms for zero start latency!)
                0      // bufferForPlaybackAfterRebufferMs (0 ms for zero seek latency!)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 4K VIDEO VE DONANIM HIZLANDIRMA DESTEĞİ
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setMediaCodecSelector(androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekForwardIncrementMs(skipIntervalSeconds * 1000L)
            .setSeekBackIncrementMs(skipIntervalSeconds * 1000L)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)

        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                _state.update {
                    it.copy(
                        videoWidth = videoSize.width,
                        videoHeight = videoSize.height
                    )
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    val audioSessionId = player.audioSessionId
                    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                        equalizerManager.initEqualizer(audioSessionId)
                        equalizerManager.setEnabled(_state.value.equalizerEnabled)
                        updateEqualizerState()
                    }
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                speed = playbackParameters.speed
                _state.update { it.copy(speed = speed) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val durationVal = player.duration
                    _state.update {
                        it.copy(
                            duration = if (durationVal != C.TIME_UNSET && durationVal > 0) durationVal else it.duration,
                            isVideo = _state.value.currentItem?.isVideo == true
                        )
                    }
                    val audioSessionId = player.audioSessionId
                    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                        equalizerManager.initEqualizer(audioSessionId)
                        equalizerManager.setEnabled(_state.value.equalizerEnabled)
                        updateEqualizerState()
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded()
                }
            }
        })

        exoPlayer = player
        
        // Start the PlaybackService so it can wrap this player in a MediaSession
        try {
            val intent = android.content.Intent(context, com.example.player.PlaybackService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    private fun getMediaDuration(uri: Uri): Long {
        var retriever: android.media.MediaMetadataRetriever? = null
        try {
            retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durationStr != null) {
                return durationStr.toLong()
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever?.release()
            } catch (ex: java.lang.Exception) {
                ex.printStackTrace()
            }
        }
        return 0L
    }

    fun play(item: PlayableItem, list: List<PlayableItem> = emptyList()) {
        val player = exoPlayer ?: return
        
        if (list.isNotEmpty()) {
            _playlist.value = list
        } else if (!_playlist.value.any { it.uri == item.uri }) {
            _playlist.value = listOf(item)
        }
        
        var calculatedDuration = item.duration
        if (calculatedDuration <= 0) {
            calculatedDuration = getMediaDuration(item.uri)
        }
        if (calculatedDuration <= 0) {
            calculatedDuration = 180000L // 3 minutes mock fallback
        }
        
        _state.update {
            it.copy(
                currentItem = item,
                isVideo = item.isVideo,
                isPlaying = true,
                duration = calculatedDuration,
                currentPosition = 0,
                videoWidth = 0,
                videoHeight = 0
            )
        }

        player.stop()
        player.clearMediaItems()
        
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setIsPlayable(true)
            .build()
            
        val mediaItem = MediaItem.Builder()
            .setUri(item.uri)
            .setMediaId(item.uri.toString())
            .setMediaMetadata(metadata)
            .build()
        player.setMediaItem(mediaItem)
        player.setPlaybackParameters(PlaybackParameters(speed))
        player.prepare()
        player.play()
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        _state.update { it.copy(playbackMode = mode) }
    }

    fun playNext() {
        val list = _playlist.value
        val currentItem = _state.value.currentItem ?: return
        if (list.isNotEmpty()) {
            val currentIndex = list.indexOfFirst { it.uri == currentItem.uri }
            if (currentIndex != -1 && currentIndex < list.lastIndex) {
                val nextItem = list[currentIndex + 1]
                play(nextItem, list)
            } else if (currentIndex == list.lastIndex) {
                val firstItem = list[0]
                play(firstItem, list)
            }
        }
    }

    fun playPrevious() {
        val list = _playlist.value
        val currentItem = _state.value.currentItem ?: return
        if (list.isNotEmpty()) {
            val currentIndex = list.indexOfFirst { it.uri == currentItem.uri }
            if (currentIndex > 0) {
                val prevItem = list[currentIndex - 1]
                play(prevItem, list)
            } else if (currentIndex == 0) {
                val lastItem = list[list.lastIndex]
                play(lastItem, list)
            }
        }
    }

    private fun handlePlaybackEnded() {
        val currentItem = _state.value.currentItem ?: return
        val currentList = _playlist.value
        when (_state.value.playbackMode) {
            PlaybackMode.REPEAT_ONE -> {
                seekTo(0)
                resume()
            }
            PlaybackMode.SEQUENTIAL -> {
                if (currentList.isNotEmpty()) {
                    val currentIndex = currentList.indexOfFirst { it.uri == currentItem.uri }
                    if (currentIndex != -1 && currentIndex < currentList.lastIndex) {
                        val nextItem = currentList[currentIndex + 1]
                        play(nextItem, currentList)
                    } else {
                        stop()
                    }
                } else {
                    stop()
                }
            }
            PlaybackMode.STOP_ON_END -> {
                stop()
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun stop() {
        exoPlayer?.stop()
        _state.update { it.copy(isPlaying = false, currentItem = null) }
    }

    fun seekTo(positionMs: Long) {
        lastSeekTime = System.currentTimeMillis()
        exoPlayer?.seekTo(positionMs)
        _state.update { it.copy(currentPosition = positionMs) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _state.update {
                it.copy(
                    sleepTimerEnabled = false,
                    sleepTimerRemainingSeconds = 0
                )
            }
            return
        }
        
        _state.update {
            it.copy(
                sleepTimerEnabled = true,
                sleepTimerRemainingSeconds = minutes * 60
            )
        }
        
        sleepTimerJob = playerScope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                delay(1000)
                remaining--
                _state.update {
                    it.copy(
                        sleepTimerRemainingSeconds = remaining,
                        sleepTimerEnabled = remaining > 0
                    )
                }
            }
            pause()
        }
    }

    fun skipForward() {
        val player = exoPlayer ?: return
        val durationVal = player.duration
        val targetPos = player.currentPosition + skipIntervalSeconds * 1000
        val newPos = if (durationVal != C.TIME_UNSET && durationVal > 0) {
            targetPos.coerceIn(0, durationVal)
        } else {
            targetPos.coerceAtLeast(0)
        }
        seekTo(newPos)
    }

    fun skipBackward() {
        val player = exoPlayer ?: return
        val durationVal = player.duration
        val targetPos = player.currentPosition - skipIntervalSeconds * 1000
        val newPos = if (durationVal != C.TIME_UNSET && durationVal > 0) {
            targetPos.coerceIn(0, durationVal)
        } else {
            targetPos.coerceAtLeast(0)
        }
        seekTo(newPos)
    }

    fun setSpeed(multiplier: Float) {
        speed = multiplier
        exoPlayer?.setPlaybackParameters(PlaybackParameters(multiplier))
    }

    fun setAspectRatio(mode: Int) {
        _state.update { it.copy(aspect_ratio_mode = mode) }
    }

    fun setAudioEngine(engine: String) {
        _state.update { it.copy(audioEngine = engine) }
    }

    fun setRenderingDriver(driver: String) {
        _state.update { it.copy(renderingDriver = driver) }
    }

    fun toggleLock() {
        _state.update { it.copy(isLocked = !it.isLocked) }
    }

    fun toggleAutoRotate() {
        _state.update { it.copy(autoRotateEnabled = !it.autoRotateEnabled) }
    }

    fun toggleEqualizer() {
        val nextEnabled = !_state.value.equalizerEnabled
        equalizerManager.setEnabled(nextEnabled)
        _state.update { it.copy(equalizerEnabled = nextEnabled) }
        updateEqualizerState()
    }

    fun updateBandLevel(bandIndex: Short, level: Short) {
        equalizerManager.setBandLevel(bandIndex, level)
        updateEqualizerState()
    }

    fun setPreset(presetIndex: Short) {
        equalizerManager.usePreset(presetIndex)
        _state.update { it.copy(activePresetIndex = presetIndex.toInt()) }
        updateEqualizerState()
    }

    private fun updateEqualizerState() {
        if (!equalizerManager.isSupported()) return
        val numBands = equalizerManager.getNumberOfBands()
        val range = equalizerManager.getBandLevelRange()
        val bands = (0 until numBands).map { i ->
            val band = i.toShort()
            BandState(
                bandIndex = band,
                centerFreq = equalizerManager.getCenterFreq(band),
                level = equalizerManager.getBandLevel(band),
                minLevel = range.first,
                maxLevel = range.second
            )
        }
        val presets = equalizerManager.getPresets()
        _state.update {
            it.copy(
                equalizerBands = bands,
                equalizerPresets = presets,
                equalizerEnabled = equalizerManager.isEnabled()
            )
        }
    }

    private fun startPositionTracker() {
        playerScope.launch {
            while (isActive) {
                val player = exoPlayer
                if (player != null && player.isPlaying && System.currentTimeMillis() - lastSeekTime > 800) {
                    val pos = player.currentPosition
                    val duration = player.duration
                    
                    val lyrics = _state.value.currentItem?.lrcLines ?: emptyList()
                    val activeLyricLine = lyrics.lastOrNull { it.timestampMs <= pos }?.text ?: ""

                    _state.update {
                        it.copy(
                            currentPosition = pos,
                            duration = if (duration > 0 && duration != C.TIME_UNSET) duration else it.duration,
                            currentLyrics = activeLyricLine
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    private fun registerStatusReceivers() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val pct = if (scale > 0) (level * 100 / scale.toFloat()).toInt() else 100
                    _state.update { s -> s.copy(batteryLevel = pct) }
                }
            }
        }, filter)

        playerScope.launch {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (isActive) {
                val timeStr = sdf.format(Date())
                _state.update { s -> s.copy(currentTimeString = timeStr) }
                delay(5000)
            }
        }
    }

    fun release() {
        playerScope.cancel()
        equalizerManager.release()
        exoPlayer?.release()
        exoPlayer = null
        INSTANCE = null
    }
}
