package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.player.*
import com.example.ui.components.player.ErenCryptEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import org.json.JSONObject

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isMediaFile: Boolean,
    val isVideo: Boolean = false,
    val size: Long = 0,
    val lastModified: Long = 0
)

enum class MediaSortMode {
    NAME_ASC,       // A'dan Z'ye Sırala
    NAME_DESC,      // Z'den A'ya Sırala
    DATE_DESC,      // En Yeni (Tarih Sonrası)
    DATE_ASC,       // En Eski (Tarih Öncesi)
    SIZE_ASC,       // Küçükten Büyüğe Sırala
    SIZE_DESC,      // Büyükten Küçüğe Sırala
    DURATION_ASC,   // Kısadan Uzuna Sırala
    DURATION_DESC,  // Uzundan Kısa Sırala
    CUSTOM          // Kullanıcı Özelleştrilmiş Sıralama
}

enum class AppTheme {
    SYSTEM, BLACK, BLUE, RED, PINK, MAROON, DARK_BLUE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val _appTheme = MutableStateFlow(AppTheme.valueOf(prefs.getString("app_theme", AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name))
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()
    
    fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
        prefs.edit().putString("app_theme", theme.name).apply()
    }

    fun backupSettings() {
        val backupPrefs = context.getSharedPreferences("app_settings_backup", Context.MODE_PRIVATE)
        val editor = backupPrefs.edit()
        prefs.all.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
            }
        }
        editor.apply()

        try {
            val jsonString = exportSettingsToJsonString()
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            val backupFile = File(downloadDir, "media_player_backup.json")
            backupFile.writeText(jsonString, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreSettings() {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupFile = File(downloadDir, "media_player_backup.json")
            if (backupFile.exists()) {
                val jsonString = backupFile.readText(Charsets.UTF_8)
                importSettingsFromJsonString(
                    jsonStr = jsonString,
                    onSuccess = {},
                    onError = {
                        restoreFromPrivateBackup()
                    }
                )
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        restoreFromPrivateBackup()
    }

    private fun restoreFromPrivateBackup() {
        val backupPrefs = context.getSharedPreferences("app_settings_backup", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        backupPrefs.all.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
            }
        }
        editor.apply()
        
        // Refresh values
        _appTheme.value = AppTheme.valueOf(prefs.getString("app_theme", AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name)
        val renamedStr = prefs.getString("renamed_items", "") ?: ""
        if (renamedStr.isNotBlank()) {
            val map = renamedStr.split(";").associate { 
                val parts = it.split("::")
                if (parts.size == 2) parts[0] to parts[1] else "" to ""
            }.filter { it.key.isNotEmpty() }
            _renamedItems.value = map
        }
        val deletedStr = prefs.getString("deleted_items", "") ?: ""
        if (deletedStr.isNotBlank()) {
            _deletedItems.value = deletedStr.split(";").toSet()
        }
        val customOrderStr = prefs.getString("custom_order", "") ?: ""
        _customOrderList.value = if (customOrderStr.isBlank()) emptyList() else customOrderStr.split(",")
        
        scanMediaFiles()
    }

    fun exportSettingsToJsonString(): String {
        val root = JSONObject()
        try {
            root.put("version", 1)
            root.put("app_theme", prefs.getString("app_theme", AppTheme.SYSTEM.name))
            root.put("renamed_items", prefs.getString("renamed_items", ""))
            root.put("deleted_items", prefs.getString("deleted_items", ""))
            root.put("custom_order", prefs.getString("custom_order", ""))
            
            val currentSettings = settings.value
            root.put("skipIntervalSeconds", currentSettings.skipIntervalSeconds)
            root.put("useDynamicColors", currentSettings.useDynamicColors)
            root.put("fileSortMode", currentSettings.fileSortMode)
            root.put("musicSortMode", currentSettings.musicSortMode)
            root.put("videoSortMode", currentSettings.videoSortMode)
            root.put("filterUnder60s", currentSettings.filterUnder60s)

            val favoritesList = favorites.value
            val favsArray = org.json.JSONArray()
            favoritesList.forEach { fav ->
                val favObj = JSONObject()
                favObj.put("uri", fav.uri)
                favObj.put("title", fav.title)
                favObj.put("artist", fav.artist ?: "")
                favObj.put("duration", fav.duration)
                favObj.put("isVideo", fav.isVideo)
                favObj.put("dateAdded", fav.dateAdded)
                favsArray.put(favObj)
            }
            root.put("favorites", favsArray)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return root.toString(4)
    }

    fun importSettingsFromJsonString(jsonStr: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject(jsonStr)
                val editor = prefs.edit()
                
                if (root.has("app_theme")) editor.putString("app_theme", root.getString("app_theme"))
                if (root.has("renamed_items")) editor.putString("renamed_items", root.getString("renamed_items"))
                if (root.has("deleted_items")) editor.putString("deleted_items", root.getString("deleted_items"))
                if (root.has("custom_order")) editor.putString("custom_order", root.getString("custom_order"))
                editor.apply()

                _appTheme.value = AppTheme.valueOf(prefs.getString("app_theme", AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name)
                val renamedStr = prefs.getString("renamed_items", "") ?: ""
                val renamedMap = if (renamedStr.isNotBlank()) {
                    renamedStr.split(";").associate { 
                        val parts = it.split("::")
                        if (parts.size == 2) parts[0] to parts[1] else "" to ""
                    }.filter { it.key.isNotEmpty() }
                } else emptyMap()
                _renamedItems.value = renamedMap

                val deletedStr = prefs.getString("deleted_items", "") ?: ""
                _deletedItems.value = if (deletedStr.isNotBlank()) deletedStr.split(";").toSet() else emptySet()

                val customOrderStr = prefs.getString("custom_order", "") ?: ""
                _customOrderList.value = if (customOrderStr.isBlank()) emptyList() else customOrderStr.split(",")

                val currentSettings = repository.getSettingsDirect()
                var updatedSettings = currentSettings
                if (root.has("skipIntervalSeconds")) {
                    updatedSettings = updatedSettings.copy(skipIntervalSeconds = root.getInt("skipIntervalSeconds"))
                }
                if (root.has("useDynamicColors")) {
                    updatedSettings = updatedSettings.copy(useDynamicColors = root.getBoolean("useDynamicColors"))
                }
                if (root.has("fileSortMode")) {
                    updatedSettings = updatedSettings.copy(fileSortMode = root.getString("fileSortMode"))
                }
                if (root.has("musicSortMode")) {
                    updatedSettings = updatedSettings.copy(musicSortMode = root.getString("musicSortMode"))
                }
                if (root.has("videoSortMode")) {
                    updatedSettings = updatedSettings.copy(videoSortMode = root.getString("videoSortMode"))
                }
                if (root.has("filterUnder60s")) {
                    updatedSettings = updatedSettings.copy(filterUnder60s = root.getBoolean("filterUnder60s"))
                }
                
                repository.saveSettings(updatedSettings)

                _musicSortMode.value = runCatching { MediaSortMode.valueOf(updatedSettings.musicSortMode) }.getOrDefault(MediaSortMode.NAME_ASC)
                _videoSortMode.value = runCatching { MediaSortMode.valueOf(updatedSettings.videoSortMode) }.getOrDefault(MediaSortMode.NAME_ASC)
                _fileSortMode.value = runCatching { MediaSortMode.valueOf(updatedSettings.fileSortMode) }.getOrDefault(MediaSortMode.NAME_ASC)
                _filterUnder60s.value = updatedSettings.filterUnder60s

                if (root.has("favorites")) {
                    val favsArray = root.getJSONArray("favorites")
                    for (i in 0 until favsArray.length()) {
                        val favObj = favsArray.getJSONObject(i)
                        val fav = FavoriteMedia(
                            uri = favObj.getString("uri"),
                            title = favObj.getString("title"),
                            artist = if (favObj.isNull("artist") || favObj.getString("artist").isEmpty()) null else favObj.getString("artist"),
                            duration = favObj.getLong("duration"),
                            isVideo = favObj.getBoolean("isVideo"),
                            dateAdded = if (favObj.has("dateAdded")) favObj.getLong("dateAdded") else System.currentTimeMillis()
                        )
                        repository.addFavorite(fav)
                    }
                }
                
                scanMediaFiles()
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.localizedMessage ?: "Geçersiz yedek formatı")
            }
        }
    }

    private val _renamedItems = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _deletedItems = MutableStateFlow<Set<String>>(emptySet())

    init {
        // Load renamed and deleted items mapping
        val renamedStr = prefs.getString("renamed_items", "") ?: ""
        if (renamedStr.isNotBlank()) {
            val map = renamedStr.split(";").associate { 
                val parts = it.split("::")
                if (parts.size == 2) parts[0] to parts[1] else "" to ""
            }.filter { it.key.isNotEmpty() }
            _renamedItems.value = map
        }
        val deletedStr = prefs.getString("deleted_items", "") ?: ""
        if (deletedStr.isNotBlank()) {
            _deletedItems.value = deletedStr.split(";").toSet()
        }
    }

    fun renameItem(item: PlayableItem, newName: String) {
        val uriStr = item.uri.toString()
        val curr = _renamedItems.value.toMutableMap()
        curr[uriStr] = newName
        _renamedItems.value = curr
        prefs.edit().putString("renamed_items", curr.entries.joinToString(";") { "${it.key}::${it.value}" }).apply()
        
        // update memory
        val all = _scannedMedia.value.map { if (it.uri.toString() == uriStr) it.copy(title = newName) else it }
        _scannedMedia.value = all
    }

    fun deleteItem(item: PlayableItem) {
        val uriStr = item.uri.toString()
        val curr = _deletedItems.value.toMutableSet()
        curr.add(uriStr)
        _deletedItems.value = curr
        prefs.edit().putString("deleted_items", curr.joinToString(";")).apply()

        // update memory
        val all = _scannedMedia.value.filter { it.uri.toString() != uriStr }
        _scannedMedia.value = all
    }

    private val database = MediaDatabase.getDatabase(context)
    private val repository = MediaRepository(database.mediaDao())
    val playerManager = PlayerManager.getInstance(context)

    private val _musicSortMode = MutableStateFlow(MediaSortMode.NAME_ASC)
    val musicSortMode: StateFlow<MediaSortMode> = _musicSortMode.asStateFlow()

    private val _videoSortMode = MutableStateFlow(MediaSortMode.NAME_ASC)
    val videoSortMode: StateFlow<MediaSortMode> = _videoSortMode.asStateFlow()

    private val _filterUnder60s = MutableStateFlow(true)
    val filterUnder60s: StateFlow<Boolean> = _filterUnder60s.asStateFlow()

    private val _currentPath = MutableStateFlow<String>(
        Environment.getExternalStorageDirectory()?.absolutePath ?: "/"
    )
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _filesList = MutableStateFlow<List<FileEntry>>(emptyList())
    val filesList: StateFlow<List<FileEntry>> = _filesList.asStateFlow()

    private val _scannedMedia = MutableStateFlow<List<PlayableItem>>(emptyList())
    val scannedMedia: StateFlow<List<PlayableItem>> = _scannedMedia.asStateFlow()

    val favorites: StateFlow<List<FavoriteMedia>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val secureItems: StateFlow<List<SecureItem>> = repository.secureItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertSecureItem(title: String, rawContent: String, userKey: String) {
        viewModelScope.launch {
            val encryptedText = ErenCryptEngine.encrypt(rawContent, userKey)
            val digitalSignature = ErenCryptEngine.generateFileSignature(rawContent, userKey)
            val item = SecureItem(
                title = title,
                encryptedContent = encryptedText,
                signature = digitalSignature,
                timestamp = System.currentTimeMillis()
            )
            repository.insertSecureItem(item)
        }
    }

    fun deleteSecureItem(id: Long) {
        viewModelScope.launch {
            repository.deleteSecureItem(id)
        }
    }

    private val _customOrderList = MutableStateFlow(getCustomOrder())
    
    private fun getCustomOrder(): List<String> {
        val s = prefs.getString("custom_order", "") ?: ""
        return if (s.isBlank()) emptyList() else s.split(",")
    }

    private fun saveCustomOrder(order: List<String>) {
        val str = order.joinToString(",")
        prefs.edit().putString("custom_order", str).apply()
        _customOrderList.value = order
    }

    fun moveItemUp(item: PlayableItem) {
        val order = _customOrderList.value.toMutableList()
        val uriStr = item.uri.toString()
        if (!order.contains(uriStr)) {
            val currentList = (if (item.isVideo) scannedVideo.value else scannedMusic.value).map { it.uri.toString() }
            order.clear()
            order.addAll(currentList)
        }
        val idx = order.indexOf(uriStr)
        if (idx > 0) {
            order.removeAt(idx)
            order.add(idx - 1, uriStr)
            saveCustomOrder(order)
            if (item.isVideo) setVideoSortMode(MediaSortMode.CUSTOM) else setMusicSortMode(MediaSortMode.CUSTOM)
        }
    }

    fun moveItemDown(item: PlayableItem) {
        val order = _customOrderList.value.toMutableList()
        val uriStr = item.uri.toString()
        if (!order.contains(uriStr)) {
            val currentList = (if (item.isVideo) scannedVideo.value else scannedMusic.value).map { it.uri.toString() }
            order.clear()
            order.addAll(currentList)
        }
        val idx = order.indexOf(uriStr)
        if (idx >= 0 && idx < order.size - 1) {
            order.removeAt(idx)
            order.add(idx + 1, uriStr)
            saveCustomOrder(order)
            if (item.isVideo) setVideoSortMode(MediaSortMode.CUSTOM) else setMusicSortMode(MediaSortMode.CUSTOM)
        }
    }

    val scannedMusic: StateFlow<List<PlayableItem>> = combine(_scannedMedia, _musicSortMode, _filterUnder60s, _customOrderList) { media, sortMode, filterUnder60s, customOrder ->
        val music = media.filter { 
            !it.isVideo && (
                !filterUnder60s || 
                it.duration >= 60000L || 
                (it.duration <= 0L && (it.size >= 200 * 1024L || it.size <= 0L))
            )
        }
        when (sortMode) {
            MediaSortMode.NAME_ASC -> music.sortedBy { it.title.lowercase() }
            MediaSortMode.NAME_DESC -> music.sortedByDescending { it.title.lowercase() }
            MediaSortMode.DATE_DESC -> music.sortedByDescending { it.dateModified }
            MediaSortMode.DATE_ASC -> music.sortedBy { it.dateModified }
            MediaSortMode.SIZE_ASC -> music.sortedBy { it.size }
            MediaSortMode.SIZE_DESC -> music.sortedByDescending { it.size }
            MediaSortMode.DURATION_ASC -> music.sortedBy { it.duration }
            MediaSortMode.DURATION_DESC -> music.sortedByDescending { it.duration }
            MediaSortMode.CUSTOM -> music.sortedBy { 
                val idx = customOrder.indexOf(it.uri.toString())
                if (idx == -1) Int.MAX_VALUE else idx 
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scannedVideo: StateFlow<List<PlayableItem>> = combine(_scannedMedia, _videoSortMode, _customOrderList) { media, sortMode, customOrder ->
        val videos = media.filter { it.isVideo }
        when (sortMode) {
            MediaSortMode.NAME_ASC -> videos.sortedBy { it.title.lowercase() }
            MediaSortMode.NAME_DESC -> videos.sortedByDescending { it.title.lowercase() }
            MediaSortMode.DATE_DESC -> videos.sortedByDescending { it.dateModified }
            MediaSortMode.DATE_ASC -> videos.sortedBy { it.dateModified }
            MediaSortMode.SIZE_ASC -> videos.sortedBy { it.size }
            MediaSortMode.SIZE_DESC -> videos.sortedByDescending { it.size }
            MediaSortMode.DURATION_ASC -> videos.sortedBy { it.duration }
            MediaSortMode.DURATION_DESC -> videos.sortedByDescending { it.duration }
            MediaSortMode.CUSTOM -> videos.sortedBy { 
                val idx = customOrder.indexOf(it.uri.toString())
                if (idx == -1) Int.MAX_VALUE else idx 
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteMusic: StateFlow<List<FavoriteMedia>> = combine(favorites, _musicSortMode, _customOrderList) { favs, sortMode, customOrder ->
        val musicFavs = favs.filter { !it.isVideo }
        when (sortMode) {
            MediaSortMode.NAME_ASC -> musicFavs.sortedBy { it.title.lowercase() }
            MediaSortMode.NAME_DESC -> musicFavs.sortedByDescending { it.title.lowercase() }
            MediaSortMode.DATE_DESC -> musicFavs.sortedByDescending { it.dateAdded }
            MediaSortMode.DATE_ASC -> musicFavs.sortedBy { it.dateAdded }
            MediaSortMode.SIZE_ASC -> musicFavs.sortedBy { it.duration }
            MediaSortMode.SIZE_DESC -> musicFavs.sortedByDescending { it.duration }
            MediaSortMode.DURATION_ASC -> musicFavs.sortedBy { it.duration }
            MediaSortMode.DURATION_DESC -> musicFavs.sortedByDescending { it.duration }
            MediaSortMode.CUSTOM -> musicFavs.sortedBy { 
                val idx = customOrder.indexOf(it.uri.toString())
                if (idx == -1) Int.MAX_VALUE else idx 
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteVideo: StateFlow<List<FavoriteMedia>> = combine(favorites, _videoSortMode, _customOrderList) { favs, sortMode, customOrder ->
        val videoFavs = favs.filter { it.isVideo }
        when (sortMode) {
            MediaSortMode.NAME_ASC -> videoFavs.sortedBy { it.title.lowercase() }
            MediaSortMode.NAME_DESC -> videoFavs.sortedByDescending { it.title.lowercase() }
            MediaSortMode.DATE_DESC -> videoFavs.sortedByDescending { it.dateAdded }
            MediaSortMode.DATE_ASC -> videoFavs.sortedBy { it.dateAdded }
            MediaSortMode.SIZE_ASC -> videoFavs.sortedBy { it.duration }
            MediaSortMode.SIZE_DESC -> videoFavs.sortedByDescending { it.duration }
            MediaSortMode.DURATION_ASC -> videoFavs.sortedBy { it.duration }
            MediaSortMode.DURATION_DESC -> videoFavs.sortedByDescending { it.duration }
            MediaSortMode.CUSTOM -> videoFavs.sortedBy { 
                val idx = customOrder.indexOf(it.uri.toString())
                if (idx == -1) Int.MAX_VALUE else idx 
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMusicSortMode(mode: MediaSortMode) {
        _musicSortMode.value = mode
        viewModelScope.launch {
            val s = repository.getSettingsDirect()
            repository.saveSettings(s.copy(musicSortMode = mode.name))
        }
    }

    fun setVideoSortMode(mode: MediaSortMode) {
        _videoSortMode.value = mode
        viewModelScope.launch {
            val s = repository.getSettingsDirect()
            repository.saveSettings(s.copy(videoSortMode = mode.name))
        }
    }

    fun setFilterUnder60s(filter: Boolean) {
        _filterUnder60s.value = filter
        viewModelScope.launch {
            val s = repository.getSettingsDirect()
            repository.saveSettings(s.copy(filterUnder60s = filter))
        }
    }

    private val _fileSortMode = MutableStateFlow(MediaSortMode.NAME_ASC)
    val fileSortMode: StateFlow<MediaSortMode> = _fileSortMode.asStateFlow()

    fun setFileSortMode(mode: MediaSortMode) {
        _fileSortMode.value = mode
        loadDirectoryFiles()
        viewModelScope.launch {
            val s = repository.getSettingsDirect()
            repository.saveSettings(s.copy(fileSortMode = mode.name))
        }
    }

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    init {
        viewModelScope.launch {
            val s = repository.getSettingsDirect()
            _musicSortMode.value = runCatching { MediaSortMode.valueOf(s.musicSortMode) }.getOrDefault(MediaSortMode.NAME_ASC)
            _videoSortMode.value = runCatching { MediaSortMode.valueOf(s.videoSortMode) }.getOrDefault(MediaSortMode.NAME_ASC)
            _fileSortMode.value = runCatching { MediaSortMode.valueOf(s.fileSortMode) }.getOrDefault(MediaSortMode.NAME_ASC)
            _filterUnder60s.value = s.filterUnder60s

            loadDirectoryFiles()

            settings.collectLatest { settingsItem ->
                playerManager.skipIntervalSeconds = settingsItem.skipIntervalSeconds
            }
        }
    }

    fun navigateToDirectory(path: String) {
        _currentPath.value = path
        loadDirectoryFiles()
    }

    fun navigateUp(): Boolean {
        val current = File(_currentPath.value)
        val parent = current.parentFile
        if (parent != null && current.absolutePath != "/") {
            _currentPath.value = parent.absolutePath
            loadDirectoryFiles()
            return true
        }
        return false
    }

    fun loadDirectoryFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(_currentPath.value)
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles() ?: emptyArray()
                    val mapped = files.map { file ->
                        val ext = file.extension.lowercase()
                        val isVideo = ext in listOf("mp4", "mkv", "webm", "avi", "ts", "3gp", "mov", "flv", "wmv", "m4v", "mpeg")
                        val isAudio = ext in listOf("mp3", "wav", "flac", "ogg", "aac", "m4a", "amr", "wma", "opus", "mid", "midi", "alac")
                        FileEntry(
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = file.isDirectory,
                            isMediaFile = isVideo || isAudio,
                            isVideo = isVideo,
                            size = file.length(),
                            lastModified = file.lastModified()
                        )
                    }
                    val sorted = when (_fileSortMode.value) {
                        MediaSortMode.NAME_ASC -> mapped.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        MediaSortMode.NAME_DESC -> mapped.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenByDescending { it.name.lowercase() })
                        MediaSortMode.DATE_ASC -> mapped.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified }))
                        MediaSortMode.DATE_DESC -> mapped.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenByDescending { it.lastModified })
                        MediaSortMode.SIZE_ASC -> mapped.sortedWith(compareBy({ !it.isDirectory }, { it.size }))
                        MediaSortMode.SIZE_DESC -> mapped.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenByDescending { it.size })
                        else -> mapped.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    }
                    _filesList.value = sorted
                } else {
                    _filesList.value = emptyList()
                }
            } catch (e: Exception) {
                _filesList.value = emptyList()
            }
        }
    }

    fun deleteMediaFiles(items: List<PlayableItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            items.forEach { item ->
                try {
                    val deletedRows = context.contentResolver.delete(item.uri, null, null)
                    if (deletedRows == 0) {
                        // Fallback to java File delete if it's a file uri
                        if (item.uri.scheme == "file") {
                            item.uri.path?.let { java.io.File(it).delete() }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            scanMediaFiles() // Refresh list after deletion
        }
    }

    fun scanMediaFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = mutableListOf<PlayableItem>()
            
            try {
                val audioUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val audioProjection = arrayOf(
                    android.provider.MediaStore.Audio.Media._ID,
                    android.provider.MediaStore.Audio.Media.TITLE,
                    android.provider.MediaStore.Audio.Media.ARTIST,
                    android.provider.MediaStore.Audio.Media.DURATION,
                    android.provider.MediaStore.Audio.Media.DATA,
                    android.provider.MediaStore.Audio.Media.DATE_ADDED,
                    android.provider.MediaStore.Audio.Media.DATE_MODIFIED,
                    android.provider.MediaStore.MediaColumns.SIZE
                )
                context.contentResolver.query(audioUri, audioProjection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                    val durationCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                    val dateAddedCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATE_ADDED)
                    val dateModifiedCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATE_MODIFIED)
                    val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.SIZE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol)
                        val artist = cursor.getString(artistCol)
                        var duration = cursor.getLong(durationCol)
                        val data = cursor.getString(dataCol)
                        val dateAdded = cursor.getLong(dateAddedCol) * 1000L
                        val dateModified = if (data != null && File(data).exists()) File(data).lastModified() else cursor.getLong(dateModifiedCol) * 1000L
                        val size = cursor.getLong(sizeCol)
                        val uri = android.content.ContentUris.withAppendedId(audioUri, id)
                        
                        if (duration <= 0) {
                            var retriever: android.media.MediaMetadataRetriever? = null
                            try {
                                retriever = android.media.MediaMetadataRetriever()
                                retriever.setDataSource(context, uri)
                                val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                if (durStr != null) {
                                    duration = durStr.toLong()
                                }
                            } catch (e: Exception) {
                                // Fallback
                            } finally {
                                try {
                                    retriever?.release()
                                } catch (ignored: Exception) {}
                            }
                        }

                        items.add(
                            PlayableItem(
                                uri = uri,
                                title = title,
                                artist = artist ?: "Bilinmeyen Sanatçı",
                                duration = duration,
                                isVideo = false,
                                lrcLines = LyricsParser.generateMockLyrics(duration, title),
                                dateAdded = dateAdded,
                                dateModified = dateModified,
                                size = size
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val videoUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val videoProjection = arrayOf(
                    android.provider.MediaStore.Video.Media._ID,
                    android.provider.MediaStore.Video.Media.TITLE,
                    android.provider.MediaStore.Video.Media.DATA,
                    android.provider.MediaStore.Video.Media.DURATION,
                    android.provider.MediaStore.Video.Media.DATE_ADDED,
                    android.provider.MediaStore.Video.Media.DATE_MODIFIED,
                    android.provider.MediaStore.MediaColumns.SIZE
                )
                context.contentResolver.query(videoUri, videoProjection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.TITLE)
                    val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
                    val durationCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
                    val dateAddedCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATE_ADDED)
                    val dateModifiedCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATE_MODIFIED)
                    val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.SIZE)
                    while (cursor.moveToNext()) {
                        val data = cursor.getString(dataCol) ?: ""
                        val ext = data.substringAfterLast('.', "").lowercase()
                        val isActuallyAudio = ext in listOf("mp3", "wav", "flac", "ogg", "aac", "m4a", "amr", "wma", "opus", "mid", "midi", "alac")
                        if (isActuallyAudio) continue
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol)
                        var duration = cursor.getLong(durationCol)
                        val dateAdded = cursor.getLong(dateAddedCol) * 1000L
                        val dateModified = if (File(data).exists()) File(data).lastModified() else cursor.getLong(dateModifiedCol) * 1000L
                        val size = cursor.getLong(sizeCol)
                        val uri = android.content.ContentUris.withAppendedId(videoUri, id)
                        
                        if (duration <= 0) {
                            var retriever: android.media.MediaMetadataRetriever? = null
                            try {
                                retriever = android.media.MediaMetadataRetriever()
                                retriever.setDataSource(context, uri)
                                val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                if (durStr != null) {
                                    duration = durStr.toLong()
                                }
                            } catch (e: Exception) {
                                // Fallback
                            } finally {
                                try {
                                    retriever?.release()
                                } catch (ignored: Exception) {}
                            }
                        }

                        val fileName = if (data.isNotEmpty()) File(data).name else "Yerel Video"
                        val displayTitle = if (title.isNullOrBlank() || title == "Yerel Video" || title.contains("Yerel Video", ignoreCase = true)) fileName else title
                        items.add(
                            PlayableItem(
                                uri = uri,
                                title = displayTitle,
                                artist = fileName,
                                duration = duration,
                                isVideo = true,
                                dateAdded = dateAdded,
                                dateModified = dateModified,
                                size = size
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val deleted = _deletedItems.value
            val renamed = _renamedItems.value
            val combined = items.distinctBy { it.title }.filter {
                !deleted.contains(it.uri.toString())
            }.map { 
                val uriStr = it.uri.toString()
                if (renamed.containsKey(uriStr)) it.copy(title = renamed[uriStr]!!) else it
            }
            _scannedMedia.value = combined
        }
    }

    fun playFile(entry: FileEntry) {
        val uri = Uri.fromFile(File(entry.path))
        val playable = PlayableItem(
            uri = uri,
            title = entry.name,
            artist = if (entry.isVideo) File(entry.path).name else "Ses Dosyası",
            isVideo = entry.isVideo,
            lrcLines = LyricsParser.generateMockLyrics(180000L, entry.name)
        )
        val currentDirFiles = _filesList.value.filter { it.isMediaFile }
        val playlist = currentDirFiles.map { f ->
            PlayableItem(
                uri = Uri.fromFile(File(f.path)),
                title = f.name,
                artist = if (f.isVideo) File(f.path).name else "Ses Dosyası",
                isVideo = f.isVideo,
                lrcLines = LyricsParser.generateMockLyrics(180000L, f.name)
            )
        }
        playerManager.play(playable, playlist)
    }

    fun toggleFavorite(item: PlayableItem) {
        viewModelScope.launch {
            val isFav = repository.isFavorite(item.uri.toString())
            if (isFav) {
                repository.deleteFavorite(item.uri.toString())
            } else {
                repository.addFavorite(
                    FavoriteMedia(
                        uri = item.uri.toString(),
                        title = item.title,
                        artist = item.artist,
                        duration = item.duration,
                        isVideo = item.isVideo
                    )
                )
            }
        }
    }

    fun isFavorite(uri: String): StateFlow<Boolean> {
        val flow = MutableStateFlow(false)
        viewModelScope.launch {
            flow.value = repository.isFavorite(uri)
        }
        return flow
    }

    fun updateSkipInterval(seconds: Int) {
        viewModelScope.launch {
            val currentSettings = settings.value
            repository.saveSettings(currentSettings.copy(skipIntervalSeconds = seconds))
        }
    }

    fun updateThemePreference(useDynamic: Boolean) {
        viewModelScope.launch {
            val currentSettings = settings.value
            repository.saveSettings(currentSettings.copy(useDynamicColors = useDynamic))
        }
    }

    fun getStreamSamples(): List<PlayableItem> {
        return listOf(
            PlayableItem(
                uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
                title = "Symphony No. 5 (MP3 Classic)",
                artist = "Symphony Orchestrator",
                duration = 372000L,
                isVideo = false,
                lrcLines = LyricsParser.generateMockLyrics(372000L, "Symphony No. 5")
            ),
            PlayableItem(
                uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
                title = "Ambient Forest Flute (WAV Audio)",
                artist = "Nature Meditations",
                duration = 423000L,
                isVideo = false,
                lrcLines = LyricsParser.generateMockLyrics(423000L, "Ambient Forest Flute")
            ),
            PlayableItem(
                uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"),
                title = "Acoustic Horizon (Lossless FLAC)",
                artist = "Guitar Masters",
                duration = 302000L,
                isVideo = false,
                lrcLines = LyricsParser.generateMockLyrics(302000L, "Acoustic Horizon")
            ),
            PlayableItem(
                uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3"),
                title = "Retro Beats & Frequency Sync",
                artist = "Synth Wave Station",
                duration = 318000L,
                isVideo = false,
                lrcLines = LyricsParser.generateMockLyrics(318000L, "Retro Beats")
            ),
            PlayableItem(
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                title = "Big Buck Bunny (MP4 Video)",
                artist = "Blender Open Creative",
                duration = 596000L,
                isVideo = true
            ),
            PlayableItem(
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"),
                title = "Sintel Cinematic Movie",
                artist = "Durian Open Science Foundation",
                duration = 848000L,
                isVideo = true
            ),
            PlayableItem(
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"),
                title = "Tears of Steel (HD VFX Test)",
                artist = "Mango Creative Open",
                duration = 734000L,
                isVideo = true
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
