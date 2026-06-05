@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.window.Dialog
import com.example.player.PlayableItem
import com.example.player.PlayerState
import com.example.player.PlaybackMode
import com.example.player.LyricsParser
import com.example.data.AppSettings
import com.example.data.FavoriteMedia
import com.example.data.SecureItem
import com.example.ui.components.player.ErenCryptEngine
import androidx.compose.ui.text.TextStyle
import com.example.ui.FileEntry
import com.example.ui.MainViewModel
import com.example.ui.MediaSortMode
import com.example.ui.components.VideoPlayerSurface
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandEmerald
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

@Composable
fun MediaAppMainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val playerState by viewModel.playerManager.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val path by viewModel.currentPath.collectAsState()
    val filesList by viewModel.filesList.collectAsState()
    val scannedMusicList by viewModel.scannedMusic.collectAsState()
    val scannedVideoList by viewModel.scannedVideo.collectAsState()
    val favoriteMusicList by viewModel.favoriteMusic.collectAsState()
    val favoriteVideoList by viewModel.favoriteVideo.collectAsState()
    val musicSortMode by viewModel.musicSortMode.collectAsState()
    val videoSortMode by viewModel.videoSortMode.collectAsState()
    val favoritesList by viewModel.favorites.collectAsState()

    var activeTab by remember { mutableStateOf("muzik") } // "muzik", "video", "oynatma_listeleri"
    var activeSubTab by remember { mutableStateOf("muzikler") } // muzikler, videolar, favoriler, gezgini, ornekler
    var videoFolderView by remember { mutableStateOf("videos") } // videos, dizinler

    var musicSearchQuery by remember { mutableStateOf("") }
    var videoSearchQuery by remember { mutableStateOf("") }
    var isGlobalSearching by remember { mutableStateOf(false) }
    var globalSearchQuery by remember { mutableStateOf("") }

    var musicSubTab by remember { mutableStateOf("ŞARKILAR") }
    var isReorderMode by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    // State of expanding the full bottom sheet player for music
    var isMusicExpanded by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1200)
        showSplash = false
    }

    if (showSplash) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition()
            val spinningAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.PlayCircleFilled,
                    contentDescription = "Logo",
                    tint = BrandEmerald,
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer { rotationZ = spinningAngle }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "veto_player",
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        return
    }

    // Request permissions for storage and notification
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.READ_MEDIA_AUDIO] == true &&
            results[Manifest.permission.READ_MEDIA_VIDEO] == true
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (storageGranted) {
            viewModel.loadDirectoryFiles()
            viewModel.scanMediaFiles()
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissionsToRequest)
        viewModel.loadDirectoryFiles()
        viewModel.scanMediaFiles()
    }

    // Handle back button directory traversal
    androidx.activity.compose.BackHandler(enabled = true) {
        if (playerState.currentItem != null && playerState.isVideo) {
            // Exit video player
            viewModel.playerManager.stop()
            setActivityOrientation(context, false)
        } else if (isMusicExpanded) {
            isMusicExpanded = false
        } else if (videoFolderView != "folders_list") {
            videoFolderView = "folders_list"
        } else if (activeTab == "ekolayzir" || activeTab == "ayarlar") {
            activeTab = "muzik"
        } else {
            val wentUp = viewModel.navigateUp()
            if (!wentUp && activeTab != "muzik") {
                activeTab = "muzik"
            }
        }
    }

    // Video Player overlay state
    if (playerState.currentItem != null && playerState.isVideo) {
        VideoPlayerFullscreen(
            playerState = playerState,
            viewModel = viewModel,
            onClose = {
                viewModel.playerManager.stop()
                setActivityOrientation(context, false)
            }
        )
    } else {
        Scaffold(
            topBar = {
                if (activeTab != "ekolayzir" && activeTab != "ayarlar") {
                    // Left aligned Top App Bar representing "Premium" from screenshots
                    TopAppBar(
                        title = {
                            if (isGlobalSearching) {
                                TextField(
                                    value = globalSearchQuery,
                                    onValueChange = { globalSearchQuery = it },
                                    placeholder = { Text("Müzik veya video ara...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    )
                                )
                            } else {
                                Text(
                                    text = "veto_player",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 23.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { 
                                isGlobalSearching = !isGlobalSearching 
                                if (!isGlobalSearching) globalSearchQuery = ""
                            }) {
                                Icon(
                                    imageVector = if (isGlobalSearching) Icons.Filled.Close else Icons.Filled.Search, 
                                    contentDescription = "Ara", 
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            var showMenuDropdown by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenuDropdown = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Seçenekler", tint = MaterialTheme.colorScheme.onBackground)
                            }
                            DropdownMenu(
                                expanded = showMenuDropdown,
                                onDismissRequest = { showMenuDropdown = false }
                            ) {
                                if (activeTab == "muzik" && musicSubTab == "ŞARKILAR") {
                                    val filterUnder60s by viewModel.filterUnder60s.collectAsState()
                                    
                                    DropdownMenuItem(
                                        text = { Text("60 Saniye Altını Filtrele") },
                                        leadingIcon = { 
                                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = if (filterUnder60s) BrandEmerald else Color.Gray) 
                                        },
                                        onClick = {
                                            viewModel.setFilterUnder60s(!filterUnder60s)
                                            showMenuDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sırala") },
                                        leadingIcon = { Icon(Icons.Filled.Sort, contentDescription = null, tint = BrandBlue) },
                                        onClick = {
                                            showMenuDropdown = false
                                            showSortDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isReorderMode) "Sıralamayı Kaydet" else "Düzenle") },
                                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = BrandAmber) },
                                        onClick = {
                                            showMenuDropdown = false
                                            isReorderMode = !isReorderMode
                                        }
                                    )
                                    androidx.compose.material3.HorizontalDivider()
                                }

                                DropdownMenuItem(
                                    text = { Text("Ekolayzır") },
                                    leadingIcon = { Icon(Icons.Filled.Equalizer, contentDescription = null, tint = BrandEmerald) },
                                    onClick = {
                                        showMenuDropdown = false
                                        activeTab = "ekolayzir"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ayarlar") },
                                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = BrandBlue) },
                                    onClick = {
                                        showMenuDropdown = false
                                        activeTab = "ayarlar"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Medya Ara/Tara") },
                                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = BrandAmber) },
                                    onClick = {
                                        showMenuDropdown = false
                                        viewModel.scanMediaFiles()
                                    }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier.testTag("app_bar")
                    )
                } else {
                    // Header for equalizer or settings screen
                    TopAppBar(
                        title = {
                            Text(
                                text = if (activeTab == "ekolayzir") "Ekolayzır" else "Ayarlar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { activeTab = "muzik" }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Geri Dön")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            bottomBar = {
                Column {
                    // Floating player strip when music is active in background from screenshot 1 & 2
                    if (playerState.currentItem != null && !playerState.isVideo) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { isMusicExpanded = true },
                            color = MaterialTheme.colorScheme.surfaceVariant, // Changed to use theme color instead of hardcoded grey
                            tonalElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Close button
                                IconButton(
                                    onClick = { viewModel.playerManager.stop() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Kapat",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(4.dp))

                                // 2. Square note box icon in grey
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color(0xFFE0E0E0), shape = RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = "Nota",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // 3. Title & Duration elapsed text
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = (playerState.currentItem?.title ?: "Çalınan Parça").uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${formatTime(playerState.currentPosition)}/${formatTime(playerState.duration)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                // 4. Play/Pause
                                IconButton(
                                    onClick = {
                                        if (playerState.isPlaying) viewModel.playerManager.pause()
                                        else viewModel.playerManager.resume()
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Oynat Durdur",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                // 5. Menu / queue list
                                IconButton(
                                    onClick = { isMusicExpanded = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = "Sıra Listesi",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Navigation Bar matching Screenshot 1 & 2 perfectly
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = activeTab == "video",
                            onClick = { 
                                activeTab = "video"
                                videoFolderView = "videos"
                            },
                            icon = { Icon(Icons.Filled.VideoLibrary, "Video") },
                            label = { Text("Video") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandEmerald,
                                selectedTextColor = BrandEmerald,
                                indicatorColor = Color.Transparent, // No pill container!
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                        NavigationBarItem(
                            selected = activeTab == "muzik",
                            onClick = { activeTab = "muzik" },
                            icon = { Icon(Icons.Filled.MusicNote, "Müzik") },
                            label = { Text("Müzik") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandEmerald,
                                selectedTextColor = BrandEmerald,
                                indicatorColor = Color.Transparent, // No pill container!
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                        NavigationBarItem(
                            selected = activeTab == "oynatma_listeleri",
                            onClick = { activeTab = "oynatma_listeleri" },
                            icon = { Icon(Icons.Filled.QueueMusic, "Oynatma listeleri") },
                            label = { Text("Oynatma listeleri") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandEmerald,
                                selectedTextColor = BrandEmerald,
                                indicatorColor = Color.Transparent, // No pill container!
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (activeTab) {
                    "muzik" -> {
                        MusicTabContent(
                            allMusicList = scannedMusicList,
                            favoritesList = favoritesList,
                            playerState = playerState,
                            viewModel = viewModel,
                            searchQuery = if (isGlobalSearching) globalSearchQuery else "",
                            musicSubTab = musicSubTab,
                            onMusicSubTabChange = { musicSubTab = it },
                            isReorderMode = isReorderMode,
                            onToggleReorder = { isReorderMode = it },
                            showSortDialog = showSortDialog,
                            onDismissSortDialog = { showSortDialog = false }
                        )
                    }

                    "video" -> {
                        VideoTabContent(
                            allVideoList = scannedVideoList,
                            favoritesList = favoritesList,
                            filesList = filesList,
                            path = path,
                            videoFolderView = videoFolderView,
                            videoSortMode = videoSortMode,
                            onVideoFolderViewChange = { videoFolderView = it },
                            viewModel = viewModel,
                            searchQuery = if (isGlobalSearching) globalSearchQuery else ""
                        )
                    }

                    "oynatma_listeleri" -> {
                        PlaylistsTabContent(
                            favoriteMusicList = favoriteMusicList,
                            favoriteVideoList = favoriteVideoList,
                            viewModel = viewModel
                        )
                    }

                    "ayarlar" -> {
                        SettingsPanel(
                            settings = settings,
                            viewModel = viewModel
                        )
                    }

                    "medyalar" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            val tabs = listOf(
                                Triple("muzikler", "Müzikler", Icons.Filled.MusicNote),
                                Triple("videolar", "Videolar", Icons.Filled.VideoLibrary),
                                Triple("favoriler", "Favoriler", Icons.Filled.Favorite),
                                Triple("gezgini", "Dosya Gezgini", Icons.Filled.Folder),
                                Triple("ornekler", "Örnekler", Icons.Filled.PlayArrow)
                            )
                            val selectedIndex = tabs.indexOfFirst { it.first == activeSubTab }.coerceAtLeast(0)

                            ScrollableTabRow(
                                selectedTabIndex = selectedIndex,
                                edgePadding = 16.dp,
                                containerColor = MaterialTheme.colorScheme.background
                            ) {
                                tabs.forEach { tab ->
                                    val isSelected = activeSubTab == tab.first
                                    Tab(
                                        selected = isSelected,
                                        onClick = { activeSubTab = tab.first },
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                                                Icon(
                                                    imageVector = tab.third, 
                                                    contentDescription = tab.second,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(text = tab.second, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                            }
                                        }
                                    )
                                }
                            }

                            when (activeSubTab) {
                                "muzikler" -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = musicSearchQuery,
                                            onValueChange = { musicSearchQuery = it },
                                            placeholder = { Text("Şarkı veya sanatçı ara...") },
                                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            item {
                                                FilterChip(
                                                    selected = musicSortMode == MediaSortMode.NAME_ASC,
                                                    onClick = { viewModel.setMusicSortMode(MediaSortMode.NAME_ASC) },
                                                    label = { Text("A - Z") },
                                                    leadingIcon = { Icon(Icons.Filled.SortByAlpha, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                )
                                            }
                                            item {
                                                FilterChip(
                                                    selected = musicSortMode == MediaSortMode.NAME_DESC,
                                                    onClick = { viewModel.setMusicSortMode(MediaSortMode.NAME_DESC) },
                                                    label = { Text("Z - A") },
                                                    leadingIcon = { Icon(Icons.Filled.SortByAlpha, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                )
                                            }
                                            item {
                                                FilterChip(
                                                    selected = musicSortMode == MediaSortMode.DATE_DESC,
                                                    onClick = { viewModel.setMusicSortMode(MediaSortMode.DATE_DESC) },
                                                    label = { Text("Tarih (En Yeni)") },
                                                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                )
                                            }
                                            item {
                                                FilterChip(
                                                    selected = musicSortMode == MediaSortMode.DATE_ASC,
                                                    onClick = { viewModel.setMusicSortMode(MediaSortMode.DATE_ASC) },
                                                    label = { Text("Tarih (En Eski)") },
                                                    leadingIcon = { Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        val filteredMusic = scannedMusicList.filter {
                                            it.title.contains(musicSearchQuery, ignoreCase = true) ||
                                            it.artist.contains(musicSearchQuery, ignoreCase = true)
                                        }

                                        if (filteredMusic.isEmpty()) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "Müzik Dosyası Bulunamadı",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(onClick = { viewModel.scanMediaFiles() }) {
                                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Cihazı Şimdi Tara")
                                                }
                                            }
                                        } else {
                                            LazyColumn(
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                items(filteredMusic) { item ->
                                                    val isFav = favoritesList.any { it.uri == item.uri.toString() }
                                                    MediaItemRow(
                                                        item = item,
                                                        isFavorite = isFav,
                                                        onPlay = { viewModel.playerManager.play(item, filteredMusic) },
                                                        onToggleFavorite = { viewModel.toggleFavorite(item) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                "videolar" -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = videoSearchQuery,
                                            onValueChange = { videoSearchQuery = it },
                                            placeholder = { Text("Video ara...") },
                                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            item {
                                                FilterChip(
                                                    selected = videoSortMode == MediaSortMode.NAME_ASC,
                                                    onClick = { viewModel.setVideoSortMode(MediaSortMode.NAME_ASC) },
                                                    label = { Text("A - Z") },
                                                    leadingIcon = { Icon(Icons.Filled.SortByAlpha, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                )
                                            }
                                            item {
                                                FilterChip(
                                                    selected = videoSortMode == MediaSortMode.NAME_DESC,
                                                    onClick = { viewModel.setVideoSortMode(MediaSortMode.NAME_DESC) },
                                                    label = { Text("Z - A") },
                                                    leadingIcon = { Icon(Icons.Filled.SortByAlpha, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                )
                                            }
                                            item {
                                                FilterChip(
                                                    selected = videoSortMode == MediaSortMode.DATE_DESC,
                                                    onClick = { viewModel.setVideoSortMode(MediaSortMode.DATE_DESC) },
                                                    label = { Text("Tarih (En Yeni)") },
                                                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                )
                                            }
                                            item {
                                                FilterChip(
                                                    selected = videoSortMode == MediaSortMode.DATE_ASC,
                                                    onClick = { viewModel.setVideoSortMode(MediaSortMode.DATE_ASC) },
                                                    label = { Text("Tarih (En Eski)") },
                                                    leadingIcon = { Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        val filteredVideo = scannedVideoList.filter {
                                            it.title.contains(videoSearchQuery, ignoreCase = true)
                                        }

                                        if (filteredVideo.isEmpty()) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "Video Dosyası Bulunamadı",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(onClick = { viewModel.scanMediaFiles() }) {
                                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Cihazı Şimdi Tara")
                                                }
                                            }
                                        } else {
                                            LazyColumn(
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                items(filteredVideo) { item ->
                                                    val isFav = favoritesList.any { it.uri == item.uri.toString() }
                                                    MediaItemRow(
                                                        item = item,
                                                        isFavorite = isFav,
                                                        onPlay = { viewModel.playerManager.play(item, filteredVideo) },
                                                        onToggleFavorite = { viewModel.toggleFavorite(item) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                "favoriler" -> {
                                    if (favoriteMusicList.isEmpty() && favoriteVideoList.isEmpty()) {
                                        EmptyStateView(
                                            title = "Henüz Favori Eklenmemiş",
                                            subtitle = "Beğendiğiniz müzikleri veya videoları kalp simgesiyle favorilere ekleyebilirsiniz."
                                        )
                                    } else {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(16.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            if (favoriteMusicList.isNotEmpty()) {
                                                item {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(vertical = 8.dp)
                                                    ) {
                                                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(20.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Müzik Favorileri (${favoriteMusicList.size})",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 16.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                items(favoriteMusicList) { fav ->
                                                    val playable = PlayableItem(
                                                        uri = Uri.parse(fav.uri),
                                                        title = fav.title,
                                                        artist = fav.artist ?: "Bilinmeyen Sanatçı",
                                                        duration = fav.duration,
                                                        isVideo = fav.isVideo,
                                                        lrcLines = LyricsParser.generateMockLyrics(fav.duration, fav.title)
                                                    )
                                                    val musicFavsPlaylist = favoriteMusicList.map { f ->
                                                        PlayableItem(
                                                            uri = Uri.parse(f.uri),
                                                            title = f.title,
                                                            artist = f.artist ?: "Bilinmeyen Sanatçı",
                                                            duration = f.duration,
                                                            isVideo = f.isVideo,
                                                            lrcLines = LyricsParser.generateMockLyrics(f.duration, f.title)
                                                        )
                                                    }
                                                    MediaItemRow(
                                                        item = playable,
                                                        isFavorite = true,
                                                        onPlay = { viewModel.playerManager.play(playable, musicFavsPlaylist) },
                                                        onToggleFavorite = { viewModel.toggleFavorite(playable) }
                                                    )
                                                }
                                            }

                                            if (favoriteVideoList.isNotEmpty()) {
                                                item {
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(vertical = 8.dp)
                                                    ) {
                                                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = BrandAmber, modifier = Modifier.size(20.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Video Favorileri (${favoriteVideoList.size})",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 16.sp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                items(favoriteVideoList) { fav ->
                                                    val playable = PlayableItem(
                                                        uri = Uri.parse(fav.uri),
                                                        title = fav.title,
                                                        artist = fav.artist ?: "Yerel Video",
                                                        duration = fav.duration,
                                                        isVideo = fav.isVideo
                                                    )
                                                    val videoFavsPlaylist = favoriteVideoList.map { f ->
                                                        PlayableItem(
                                                            uri = Uri.parse(f.uri),
                                                            title = f.title,
                                                            artist = f.artist ?: "Yerel Video",
                                                            duration = f.duration,
                                                            isVideo = f.isVideo
                                                        )
                                                    }
                                                    MediaItemRow(
                                                        item = playable,
                                                        isFavorite = true,
                                                        onPlay = { viewModel.playerManager.play(playable, videoFavsPlaylist) },
                                                        onToggleFavorite = { viewModel.toggleFavorite(playable) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                "gezgini" -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                    ) {
                                        // Current browsing path with a back-track indicator button
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.navigateUp() },
                                                enabled = path != "/"
                                            ) {
                                                Icon(Icons.Filled.ArrowBack, "Yukarı Git")
                                            }
                                            Text(
                                                text = path,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        if (filesList.isEmpty()) {
                                            EmptyStateView(
                                                title = "Dosya Bulunamadı",
                                                subtitle = "Klasör boş veya gerekli depolama izinleri yok. Sol üst yön tuşuyla diğer dizinlere geçebilirsiniz."
                                            )
                                        } else {
                                            LazyColumn(
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                items(filesList) { file ->
                                                    FileRow(
                                                        file = file,
                                                        onClick = {
                                                            if (file.isDirectory) {
                                                                viewModel.navigateToDirectory(file.path)
                                                            } else if (file.isMediaFile) {
                                                                viewModel.playFile(file)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                "ornekler" -> {
                                    val streams = viewModel.getStreamSamples()
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(16.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(streams) { stream ->
                                            MediaStreamRow(
                                                item = stream,
                                                onPlay = { viewModel.playerManager.play(stream, streams) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "ekolayzir" -> {
                        EqualizerControlPanel(
                            playerState = playerState,
                            viewModel = viewModel
                        )
                    }

                    "ayarlar" -> {
                        SettingsPanel(
                            settings = settings,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = "Zamanlayıcı",
                        tint = BrandBlue,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Uyku Zamanlayıcı", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Belirlenen süre sonunda oynatıcı duracaktır.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    val options = listOf(
                        "Kapalı" to 0,
                        "10 Dakika" to 10,
                        "15 Dakika" to 15,
                        "30 Dakika" to 30,
                        "45 Dakika" to 45,
                        "60 Dakika" to 60
                    )
                    
                    options.forEach { (label, minutes) ->
                        val isSelected = if (minutes == 0) !playerState.sleepTimerEnabled else playerState.sleepTimerEnabled && ((playerState.sleepTimerRemainingSeconds + 59) / 60 == minutes)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.playerManager.setSleepTimer(minutes)
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label, 
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) BrandBlue else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = "Seçili", tint = BrandBlue)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    // Full screen expandable sheet player for standard music listening
    if (isMusicExpanded && playerState.currentItem != null) {
        Dialog(
            onDismissRequest = { isMusicExpanded = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF303030)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Close Sheet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isMusicExpanded = false }) {
                            Icon(Icons.Filled.ArrowBack, "Kapat", tint = Color.White)
                        }
                        Row {
                            IconButton(onClick = { activeTab = "ekolayzir"; isMusicExpanded = false }) {
                                Icon(Icons.Filled.Tune, "Ayarlar", tint = Color.White)
                            }
                            IconButton(onClick = { /* More options */ }) {
                                Icon(Icons.Filled.MoreVert, "Seçenekler", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Hardware accelerated Disc Spin Animation
                    val rotationAngle = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(playerState.isPlaying) {
                        if (playerState.isPlaying) {
                            rotationAngle.animateTo(
                                targetValue = rotationAngle.value + 360f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(12000, easing = androidx.compose.animation.core.LinearEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                                )
                            )
                        } else {
                            rotationAngle.stop()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .graphicsLayer { rotationZ = rotationAngle.value }
                            .background(Color(0xFF555555), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .background(Color(0xFF444444), shape = CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .background(Color(0xFF333333), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = "Nota",
                                modifier = Modifier.size(36.dp),
                                tint = Color(0xFF555555)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Title and Icons Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showSleepTimerDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.AccessAlarm,
                                contentDescription = "Zamanlayıcı",
                                tint = if (playerState.sleepTimerEnabled) BrandEmerald else Color.LightGray
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = playerState.currentItem?.title ?: "Bilinmeyen Başlık",
                                fontSize = 16.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = playerState.currentItem?.artist ?: "Bilinmeyen Sanatçı",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val activeAudioDevice = rememberActiveAudioDeviceName(LocalContext.current)
                            val isBtDevice = activeAudioDevice != "Bu telefondan çıkıyor ses"
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isBtDevice) Color(0xFF1E88E5).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isBtDevice) Icons.Filled.Bluetooth else Icons.Filled.PhoneAndroid,
                                    contentDescription = "Ses Çıkışı",
                                    tint = if (isBtDevice) Color(0xFF2196F3) else Color.LightGray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activeAudioDevice,
                                    fontSize = 11.sp,
                                    color = if (isBtDevice) Color(0xFF90CAF9) else Color.LightGray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleFavorite(playerState.currentItem!!) }
                        ) {
                            val isFavState = viewModel.isFavorite(playerState.currentItem!!.uri.toString()).collectAsState()
                            Icon(
                                imageVector = if (isFavState.value) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Yıldızla",
                                tint = if (isFavState.value) Color.Red else Color.LightGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Progress indicator
                    val currentPos = playerState.currentPosition
                    val totalDuration = playerState.duration
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPos),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        
                        var isDragging by remember { mutableStateOf(false) }
                        var dragPosition by remember { mutableStateOf(0f) }
                        val sliderValue = if (isDragging) dragPosition else (if (totalDuration > 0) currentPos.toFloat() / totalDuration else 0f)

                        Slider(
                            value = sliderValue,
                            onValueChange = { percent ->
                                isDragging = true
                                dragPosition = percent
                            },
                            onValueChangeFinished = {
                                viewModel.playerManager.seekTo((dragPosition * totalDuration).toLong())
                                isDragging = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4CAF50), // Green thumb
                                activeTrackColor = Color(0xFF4CAF50), // Green active
                                inactiveTrackColor = Color.Gray
                            )
                        )
                        
                        Text(
                            text = formatTime(totalDuration),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Main Controls panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentMode = playerState.playbackMode
                        val modeIcon = when (currentMode) {
                            PlaybackMode.SEQUENTIAL -> Icons.Filled.Repeat
                            PlaybackMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                            PlaybackMode.STOP_ON_END -> Icons.Filled.DoNotDisturbOn
                        }

                        IconButton(onClick = {
                            val nextMode = when (currentMode) {
                                PlaybackMode.SEQUENTIAL -> PlaybackMode.REPEAT_ONE
                                PlaybackMode.REPEAT_ONE -> PlaybackMode.STOP_ON_END
                                PlaybackMode.STOP_ON_END -> PlaybackMode.SEQUENTIAL
                            }
                            viewModel.playerManager.setPlaybackMode(nextMode)
                        }) {
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = "Döngü Modu",
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(onClick = { viewModel.playerManager.playPrevious() }) {
                            Icon(Icons.Filled.SkipPrevious, "Önceki", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        // Play/Pause button
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clickable {
                                    if (playerState.isPlaying) viewModel.playerManager.pause()
                                    else viewModel.playerManager.resume()
                                }
                                .background(Color.Transparent, CircleShape)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Oynat Durdur",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(onClick = { viewModel.playerManager.playNext() }) {
                            Icon(Icons.Filled.SkipNext, "Sonraki", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        IconButton(onClick = { isMusicExpanded = false }) {
                            Icon(Icons.Filled.Menu, "Liste", tint = Color.Gray, modifier = Modifier.size(28.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Bottom extra controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.playerManager.skipBackward() }) {
                            Icon(Icons.Filled.Replay10, "-10 Sn", tint = Color.Gray, modifier = Modifier.size(28.dp))
                        }

                        // Speed Indicator Box
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Transparent)
                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                .clickable {
                                    val newSpeed = if (playerState.speed >= 2.0f) 0.5f else playerState.speed + 0.25f
                                    viewModel.playerManager.setSpeed(newSpeed)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${playerState.speed}X",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(onClick = { viewModel.playerManager.skipForward() }) {
                            Icon(Icons.Filled.Forward10, "+10 Sn", tint = Color.Gray, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

// Fullscreen Video Player Screen wrapping the ExoPlayer Controller overlays and orientation controls
@Composable
fun VideoPlayerFullscreen(
    playerState: PlayerState,
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }

    val currentPositionState by androidx.compose.runtime.rememberUpdatedState(playerState.currentPosition)
    val durationState by androidx.compose.runtime.rememberUpdatedState(playerState.duration)

    var brightnessOverlayVal by remember { mutableStateOf<Float?>(null) }
    var volumeOverlayVal by remember { mutableStateOf<Int?>(null) }
    var seekOverlayVal by remember { mutableStateOf<Long?>(null) }
    var seekOverlayDiff by remember { mutableStateOf<Long?>(null) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartY by remember { mutableStateOf(0f) }
    var originalVolume by remember { mutableStateOf(0) }
    var originalBrightness by remember { mutableStateOf(0.5f) }
    var originalPosition by remember { mutableStateOf(0L) }
    var detectType by remember { mutableStateOf(0) } // 0: untouched, 1: horizontal, 2: vertical left, 3: vertical right

    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(playerState.videoWidth, playerState.videoHeight) {
        val w = playerState.videoWidth
        val h = playerState.videoHeight
        if (w > 0 && h > 0) {
            val activity = context as? Activity
            if (activity != null) {
                if (w > h) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
            if (!playerState.isLocked) {
                showControls = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(playerState.isLocked) {
                if (playerState.isLocked) {
                    detectTapGestures(
                        onTap = { showControls = !showControls }
                    )
                } else {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 2f) {
                                viewModel.playerManager.skipBackward()
                            } else {
                                viewModel.playerManager.skipForward()
                            }
                        }
                    )
                }
            }
            .pointerInput(playerState.isLocked) {
                if (playerState.isLocked) return@pointerInput
                
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStartX = offset.x
                        dragStartY = offset.y
                        detectType = 0
                        
                        // Load starting values
                        originalVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                        val activity = context as? Activity
                        val screenBright = activity?.window?.attributes?.screenBrightness ?: -1f
                        originalBrightness = if (screenBright < 0f) 0.5f else screenBright
                        originalPosition = currentPositionState
                    },
                    onDragEnd = {
                        if (detectType == 1 && seekOverlayVal != null) {
                            viewModel.playerManager.seekTo(seekOverlayVal!!)
                        }
                        // Reset overlays
                        brightnessOverlayVal = null
                        volumeOverlayVal = null
                        seekOverlayVal = null
                        seekOverlayDiff = null
                        detectType = 0
                    },
                    onDragCancel = {
                        brightnessOverlayVal = null
                        volumeOverlayVal = null
                        seekOverlayVal = null
                        seekOverlayDiff = null
                        detectType = 0
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val width = size.width
                        val height = size.height
                        
                        val dx = change.position.x - dragStartX
                        val dy = change.position.y - dragStartY
                        
                        if (detectType == 0) {
                            if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > 30f) {
                                detectType = 1 // horizontal seek
                            } else if (kotlin.math.abs(dy) > kotlin.math.abs(dx) && kotlin.math.abs(dy) > 30f) {
                                if (dragStartX < width / 2f) {
                                    detectType = 2 // vertical left (brightness)
                                } else {
                                    detectType = 3 // vertical right (volume)
                                }
                            }
                        }
                        
                        if (detectType == 1) { // Seek
                            val seekRatio = dx / width.toFloat()
                            val totalDur = durationState
                            if (totalDur > 0) {
                                val deltaMs = (seekRatio.toDouble() * 120000.0).toLong() // drag fully yields +2 mins
                                val targetPosition = (originalPosition + deltaMs).coerceIn(0L, totalDur)
                                seekOverlayVal = targetPosition
                                seekOverlayDiff = deltaMs
                            }
                        } else if (detectType == 2) { // Brightness
                            val activity = context as? Activity
                            val ratio = -dy / height.toFloat() // swipe up is negative dy
                            val targetBrightness = (originalBrightness + ratio).coerceIn(0f, 1f)
                            activity?.runOnUiThread {
                                val lp = activity.window.attributes
                                lp.screenBrightness = targetBrightness
                                activity.window.attributes = lp
                            }
                            brightnessOverlayVal = targetBrightness
                        } else if (detectType == 3) { // Volume
                            val ratio = -dy / height.toFloat()
                            val changeVol = (ratio.toDouble() * maxVolume.toDouble() * 1.5).roundToInt()
                            val targetVolume = (originalVolume + changeVol).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
                            volumeOverlayVal = targetVolume
                        }
                    }
                )
            }
    ) {
        // Safe drawing of media video surface
        VideoPlayerSurface(
            playerManager = viewModel.playerManager,
            aspectRatioMode = playerState.aspect_ratio_mode,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Controllers
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top controls strip (Clock, battery, back button, lock tracker)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.ArrowBack, "Kapat", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = playerState.currentItem?.title ?: "Video Oynatıcı",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Clock and Battery trackers requested
                            Text(
                                text = "Saat: ${playerState.currentTimeString} | Şarj: %${playerState.batteryLevel}" +
                                        if (playerState.sleepTimerEnabled) " | ⏱️ ${formatTime(playerState.sleepTimerRemainingSeconds * 1000L)}" else "",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Right top buttons: Lock icon, aspect scale index button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showSleepTimerDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccessTime,
                                tint = if (playerState.sleepTimerEnabled) BrandEmerald else Color.White,
                                contentDescription = "Zamanlayıcı"
                            )
                        }

                        IconButton(
                            onClick = { viewModel.playerManager.toggleLock() }
                        ) {
                            Icon(
                                imageVector = if (playerState.isLocked) Icons.Filled.Lock else Icons.Default.Lock,
                                tint = if (playerState.isLocked) BrandEmerald else Color.White,
                                contentDescription = "Kilitle"
                            )
                        }

                        if (!playerState.isLocked) {
                            IconButton(
                                onClick = {
                                    val nextMode = (playerState.aspect_ratio_mode + 1) % 3
                                    viewModel.playerManager.setAspectRatio(nextMode)
                                }
                            ) {
                                val ratioLabel = when (playerState.aspect_ratio_mode) {
                                    1 -> "Uzatarak"
                                    2 -> "Yakınlaş"
                                    else -> "Sığdır"
                                }
                                Text(text = ratioLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            IconButton(
                                onClick = {
                                    val act = context as? android.app.Activity
                                    if (act != null) {
                                        val isCurrentLandscape = act.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                        setActivityOrientation(context, !isCurrentLandscape)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ScreenRotation,
                                    tint = Color.White,
                                    contentDescription = "Ekranı Döndür"
                                )
                            }
                        }
                    }
                }

                // If locked, we intercept and hide other controls
                if (!playerState.isLocked) {
                    // Middle Buttons (Reverse seek, pause, play, advance seek)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentMode = playerState.playbackMode
                        val modeIcon = when (currentMode) {
                            PlaybackMode.SEQUENTIAL -> Icons.Filled.Repeat
                            PlaybackMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                            PlaybackMode.STOP_ON_END -> Icons.Filled.DoNotDisturbOn
                        }
                        val modeColor = if (currentMode == PlaybackMode.STOP_ON_END) Color.White.copy(alpha = 0.5f) else BrandEmerald

                        IconButton(
                            onClick = {
                                val nextMode = when (currentMode) {
                                    PlaybackMode.SEQUENTIAL -> PlaybackMode.REPEAT_ONE
                                    PlaybackMode.REPEAT_ONE -> PlaybackMode.STOP_ON_END
                                    PlaybackMode.STOP_ON_END -> PlaybackMode.SEQUENTIAL
                                }
                                viewModel.playerManager.setPlaybackMode(nextMode)
                            }
                        ) {
                            Icon(imageVector = modeIcon, contentDescription = "Mod", tint = modeColor, modifier = Modifier.size(36.dp))
                        }

                        IconButton(
                            onClick = { viewModel.playerManager.playPrevious() }
                        ) {
                            Icon(Icons.Filled.SkipPrevious, "Önceki", tint = Color.White, modifier = Modifier.size(40.dp))
                        }

                        IconButton(
                            onClick = { viewModel.playerManager.skipBackward() }
                        ) {
                            Icon(Icons.Filled.Replay10, "10 Sn Geri", tint = Color.White, modifier = Modifier.size(44.dp))
                        }

                        IconButton(
                            onClick = {
                                if (playerState.isPlaying) viewModel.playerManager.pause()
                                else viewModel.playerManager.resume()
                            }
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                tint = Color.White,
                                modifier = Modifier.size(56.dp),
                                contentDescription = "Durdur"
                            )
                        }

                        IconButton(
                            onClick = { viewModel.playerManager.skipForward() }
                        ) {
                            Icon(Icons.Filled.Forward10, "10 Sn İleri", tint = Color.White, modifier = Modifier.size(44.dp))
                        }

                        IconButton(
                            onClick = { viewModel.playerManager.playNext() }
                        ) {
                            Icon(Icons.Filled.SkipNext, "Sonraki", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                    }

                    // Bottom controls block (Rotation control, Speed selector 2x, seek timeline)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rotation toggle requested
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        viewModel.playerManager.toggleAutoRotate()
                                        // Request activity rotation landscape
                                        val isLandscape = playerState.autoRotateEnabled
                                        setActivityOrientation(context, isLandscape)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        tint = if (playerState.autoRotateEnabled) BrandEmerald else Color.White,
                                        contentDescription = "Otomatik Döndür"
                                    )
                                }
                                Text(
                                    text = if (playerState.autoRotateEnabled) "Yatay Kilit" else "Dikey",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }

                            // Speed indicator control (Supports 2x control)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                listOf(1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                                    val isSelected = playerState.speed == s
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .background(
                                                if (isSelected) BrandBlue else Color.White.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.playerManager.setSpeed(s) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = "${s}x", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val pos = playerState.currentPosition
                        val duration = playerState.duration

                        var isDraggingVideo by remember { mutableStateOf(false) }
                        var dragPositionVideo by remember { mutableStateOf(0f) }

                        val videoSliderValue = if (isDraggingVideo) dragPositionVideo else (if (duration > 0) pos.toFloat() / duration else 0f)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val activePosValue = if (isDraggingVideo) (dragPositionVideo * duration).toLong() else pos
                            Text(text = formatTime(activePosValue), color = Color.White, fontSize = 11.sp)
                            Slider(
                                value = videoSliderValue,
                                onValueChange = { percent ->
                                    isDraggingVideo = true
                                    dragPositionVideo = percent
                                },
                                onValueChangeFinished = {
                                    viewModel.playerManager.seekTo((dragPositionVideo * duration).toLong())
                                    isDraggingVideo = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = BrandBlue,
                                    activeTrackColor = BrandBlue,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            Text(text = formatTime(duration), color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Gesture indicators HUD overlay
        if (brightnessOverlayVal != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 48.dp)
                    .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = "Parlaklık",
                        tint = Color.Yellow,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "%" + (brightnessOverlayVal!! * 100).roundToInt(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        if (volumeOverlayVal != null) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val ratio = if (maxVolume > 0) volumeOverlayVal!!.toFloat() / maxVolume else 0f
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp)
                    .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (volumeOverlayVal!! == 0) Icons.Filled.VolumeMute else if (ratio < 0.5f) Icons.Filled.VolumeDown else Icons.Filled.VolumeUp,
                        contentDescription = "Ses",
                        tint = BrandBlue,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "%" + (ratio * 100).roundToInt(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        if (seekOverlayVal != null) {
            val diffSeconds = seekOverlayDiff ?: 0L
            val sign = if (diffSeconds >= 0) "+" else ""
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (diffSeconds >= 0) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                        contentDescription = "Sar",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatTime(seekOverlayVal!!),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "$sign${diffSeconds / 1000} sn",
                        color = if (diffSeconds >= 0) BrandEmerald else Color.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Sleep Timer Dialogue inside Fullscreen View
        if (showSleepTimerDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = "Zamanlayıcı",
                            tint = BrandBlue,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Uyku Zamanlayıcı", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Belirlenen süre sonunda oynatıcı duracaktır.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        val options = listOf(
                            "Kapalı" to 0,
                            "10 Dakika" to 10,
                            "15 Dakika" to 15,
                            "30 Dakika" to 30,
                            "45 Dakika" to 45,
                            "60 Dakika" to 60
                        )
                        
                        options.forEach { (label, minutes) ->
                            val isSelected = if (minutes == 0) !playerState.sleepTimerEnabled else playerState.sleepTimerEnabled && ((playerState.sleepTimerRemainingSeconds + 59) / 60 == minutes)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.playerManager.setSleepTimer(minutes)
                                        showSleepTimerDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label, 
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected) BrandBlue else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Filled.Check, contentDescription = "Seçili", tint = BrandBlue)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text("Kapat")
                    }
                }
            )
        }
    }
}

// Sound Equalizer board control mapping native bands
@Composable
fun EqualizerControlPanel(
    playerState: PlayerState,
    viewModel: MainViewModel
) {
    var showPresets by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Gelişmiş Ekolayzır Paneli",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Kayıpsız ses formatları için donanımsal frekans seviyeleri dengeleyin.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            androidx.compose.material3.Switch(
                checked = playerState.equalizerEnabled,
                onCheckedChange = {
                    viewModel.playerManager.toggleEqualizer()
                },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BrandEmerald,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        // --- GELİŞMİŞ SES VE FİZİKSEL KULAKLIK MOTORU PANELİ ---
        androidx.compose.material3.Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Headset,
                            contentDescription = "Kulaklık Motoru",
                            tint = BrandEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gelişmiş Kulaklık & Ses Motoru",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    val activeColor = if (playerState.audioEngine.contains("AAudio")) BrandEmerald else (if (playerState.audioEngine.contains("AudioTrack")) BrandAmber else BrandBlue)
                    Box(
                        modifier = Modifier
                            .background(activeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, activeColor, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = playerState.audioEngine.substringBefore(" (").uppercase(),
                            color = activeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Kulaklığın sürücü ses motoru nedir?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = BrandEmerald
                )
                Text(
                    text = "Android işletim sistemi, kulaklık ve hoparlörlere ses iletirken gecikmeyi sıfırlamak için AAudio veya kararlılık için AudioTrack kullanır. AAudio kablosuz (Bluetooth) kulaklıklarda gecikmeyi kaldırarak en yüksek performansı verir. Bu motor değiştirilebilir mi? Evet, dilediğiniz sürücü motorunu seçerek kulaklık çıkışını özelleştirebilirsiniz:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Selectors for changing audioEngine
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val engines = listOf(
                        "AAudio (Düşük Gecikme)" to BrandEmerald,
                        "AudioTrack (Yüksek Uyum)" to BrandAmber,
                        "OpenSL ES (Saf Native C)" to BrandBlue
                    )

                    engines.forEach { (engine, color) ->
                        val isSelected = playerState.audioEngine == engine
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) color else Color.Gray.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.playerManager.setAudioEngine(engine)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = engine.substringBefore(" "),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                // --- 4K VIDEO DONANIMSAL KOD ÇÖZÜCÜ VE VULKAN SÜRÜCÜSÜ PANELİ ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = "Video Çözücü",
                        tint = BrandAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Aktif Çözücü Motoru:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "4K Ultra-HD Donanımsal Hızlandırma (HEVC/VP9/AV1)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                // --- VULKAN GRAFİK SÜRÜCÜSÜ VE RENDER SİSTEMİ ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.FlashOn,
                            contentDescription = "Grafik Hızlandırıcı",
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Grafik Sürücüsü (Renderer Pipeline)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Active badge
                    val driverBadgeColor = when {
                        playerState.renderingDriver.contains("Vulkan") -> Color(0xFFFF5722)
                        playerState.renderingDriver.contains("OpenGL") -> BrandBlue
                        else -> Color.Gray
                    }
                    Box(
                        modifier = Modifier
                            .background(driverBadgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, driverBadgeColor, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = playerState.renderingDriver.substringBefore(" (").uppercase(),
                            color = driverBadgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Vulkan Donanımsal Sürücüsü, modern Android 10+ cihazlarda 4K 60FPS videoları sıfır takılma, sıfır ısınma ve en düşük GPU güç tüketimiyle doğrudan GPU işlemcisiyle çizer. Grafik motorunu değiştirebilirsiniz:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Selectors for changing rendering Driver
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val drivers = listOf(
                        "Vulkan GPU (Ultra Hızlı)" to Color(0xFFFF5722),
                        "OpenGL ES 3.2 (Klasik)" to BrandBlue,
                        "Yazılımsal (Enerji Tasarrufu)" to Color.Gray
                    )

                    drivers.forEach { (drv, color) ->
                        val isSelected = playerState.renderingDriver == drv
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) color else Color.Gray.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.playerManager.setRenderingDriver(drv)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = drv.substringBefore(" "),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        if (playerState.equalizerBands.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Bilgilendirme",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ekolayzır Aktif Değil",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ekolayzır motoru ses dalgası çalarken kilitlenir. Lütfen parça çalmaya başlayın.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else if (!playerState.equalizerEnabled) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.VolumeMute,
                        contentDescription = "Ekolayzır Kapalı",
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ekolayzır Kapalı",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ekolayzır sistemi devre dışı bırakıldı. Aktif hale getirip frekans seviyelerini düzenlemek için lütfen sağ üstteki anahtarı açın.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            // Preset selectors
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hazır Preset Ses Modları",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Button(onClick = { showPresets = !showPresets }) {
                            Text(text = if (showPresets) "Kapat" else "Seç")
                        }
                    }

                    if (showPresets) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(playerState.equalizerPresets) { idx, name ->
                                val isSelected = idx == playerState.activePresetIndex
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) BrandEmerald else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            viewModel.playerManager.setPreset(idx.toShort())
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = name,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bands Sliders
            Text(
                text = "Frekans Bantları",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(playerState.equalizerBands) { band ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val freqKhz = band.centerFreq / 1000000.0
                            val freqString = if (freqKhz >= 1.0) {
                                String.format("%.1f kHz", freqKhz)
                            } else {
                                String.format("%d Hz", band.centerFreq / 1000)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = freqString, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                val currentDb = band.level / 100.0
                                Text(text = String.format("%+.1f dB", currentDb), fontSize = 12.sp, color = BrandBlue)
                            }

                            val levelRange = (band.maxLevel - band.minLevel).toFloat()
                            val progressValue = (band.level - band.minLevel) / levelRange

                            Slider(
                                value = progressValue,
                                onValueChange = { scale ->
                                    val newLevel = (band.minLevel + (scale * levelRange)).roundToInt().toShort()
                                    viewModel.playerManager.updateBandLevel(band.bandIndex, newLevel)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = BrandBlue,
                                    activeTrackColor = BrandBlue
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// User Configuration settings dashboard
@Composable
fun SettingsPanel(
    settings: AppSettings,
    viewModel: MainViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Eşleşmiş Ayarlar",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Video atlama süreleri ve temalandırma biçimleri kişiselleştirin.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Custom Jump seconds requested: "video icin 10,15,20,30 saniye ileri atlama olsun"
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "İleri / Geri Atlama Süresi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Video veya müzik atlama tuşlarına basıldığında geçilecek saniye miktarı.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(10, 15, 20, 30).forEach { sec ->
                        val isSelected = settings.skipIntervalSeconds == sec
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.updateSkipInterval(sec) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$sec Sn",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic System theme selector required: "güzel temalar ekle sistem rengi olsun diye ekstra tuş ekle kapalı ise ise varsayılan rengi kullansın"
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dinamik Sistem Rengi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Açık ise Android sistem(dynamic) duvar kağıdı uymaya çalışır. Kapalıysa uygulama teması uygulanır.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = settings.useDynamicColors,
                    onCheckedChange = { viewModel.updateThemePreference(it) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Custom App Themes
        val appTheme by viewModel.appTheme.collectAsState()
        Text(
            text = "Uygulama Teması",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val themes = listOf(
                com.example.ui.AppTheme.SYSTEM to "Sistem",
                com.example.ui.AppTheme.BLACK to "Siyah",
                com.example.ui.AppTheme.BLUE to "Mavi",
                com.example.ui.AppTheme.RED to "Kırmızı",
                com.example.ui.AppTheme.PINK to "Pembe",
                com.example.ui.AppTheme.MAROON to "Bordo",
                com.example.ui.AppTheme.DARK_BLUE to "Lacivert"
            )
            themes.forEach { (themeEnum, name) ->
                val isSelected = appTheme == themeEnum
                Box(
                    modifier = Modifier
                        .clickable { viewModel.setAppTheme(themeEnum); viewModel.updateThemePreference(false) } // disables dynamic mode if customized
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = name, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                }
            }
        }

        var showExportDialog by remember { mutableStateOf(false) }
        var showImportDialog by remember { mutableStateOf(false) }
        var backupText by remember { mutableStateOf("") }
        var importText by remember { mutableStateOf("") }
        val currentContext = LocalContext.current

        Spacer(modifier = Modifier.height(16.dp))

        // Eren's Cryptographic Safe
        ErenCryptographyCard(viewModel = viewModel)

        Spacer(modifier = Modifier.height(16.dp))

        Spacer(modifier = Modifier.height(32.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Yedekleme ve Geri Yükleme",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Card(
           shape = RoundedCornerShape(16.dp),
           modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
           colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Yedekleriniz telefonun 'Download' klasörüne 'media_player_backup.json' adıyla kaydedilir. Uygulamayı veya telefonu sıfırlasanız dahi bu dosya silinmez.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                // Local Backup & Restore Row
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = { 
                        viewModel.backupSettings()
                        Toast.makeText(currentContext, "Download/media_player_backup.json dosyasına yedeklendi!", Toast.LENGTH_LONG).show()
                    }) {
                        Text("Cihaza Yedekle", fontSize = 12.sp)
                    }
                    Button(modifier = Modifier.weight(1f), onClick = { 
                        viewModel.restoreSettings()
                        Toast.makeText(currentContext, "Cihaz yedek dosyasından geri yüklendi!", Toast.LENGTH_LONG).show()
                    }) {
                        Text("Cihazdan Yükle", fontSize = 12.sp)
                    }
                }
                
                // Export & Import Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        onClick = { 
                            backupText = viewModel.exportSettingsToJsonString()
                            showExportDialog = true
                        }
                    ) {
                        Text("Dışa Aktar (Paylaş)", fontSize = 12.sp)
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        onClick = { 
                            importText = ""
                            showImportDialog = true
                        }
                    ) {
                        Text("İçeri Aktar (Kod)", fontSize = 12.sp)
                    }
                }
            }
        }

        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = BrandBlue, modifier = Modifier.padding(end = 8.dp))
                        Text("Yedek Kodu Dışa Aktar")
                    }
                },
                text = {
                    Column {
                        Text("Aşağıdaki yedek kodunu kopyalayabilir veya diğer cihazlarla paylaşabilirsiniz:", fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = backupText,
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val clipboard = currentContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Medya Oynatıcı Yedek", backupText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(currentContext, "Yedek kodu panoya kopyalandı!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Kopyala")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, backupText)
                                    type = "text/plain"
                                }
                                currentContext.startActivity(android.content.Intent.createChooser(sendIntent, "Yedek Kodu Paylaş"))
                            }
                        ) {
                            Text("Paylaş")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { showExportDialog = false }) {
                            Text("Kapat")
                        }
                    }
                }
            )
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = BrandEmerald, modifier = Modifier.padding(end = 8.dp))
                        Text("Yedek Kodu İçeri Aktar")
                    }
                },
                text = {
                    Column {
                        Text("Lütfen kopyaladığınız yedek kodunu buraya yapıştırın:", fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it },
                            label = { Text("Yedek Kodu") },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (importText.isBlank()) {
                                Toast.makeText(currentContext, "Yedek kodu boş olamaz!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.importSettingsFromJsonString(
                                    jsonStr = importText,
                                    onSuccess = {
                                        Toast.makeText(currentContext, "Tüm ayarlar başarıyla aktarıldı!", Toast.LENGTH_SHORT).show()
                                        showImportDialog = false
                                    },
                                    onError = { err ->
                                        Toast.makeText(currentContext, "Hata: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    ) {
                        Text("Yükle")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("İptal")
                    }
                }
            )
        }
    }
}

@Composable
fun FileRow(
    file: FileEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when {
                file.isDirectory -> Icons.Filled.Folder
                file.isVideo -> Icons.Filled.VideoLibrary
                else -> Icons.Filled.MusicNote
            }
            val tint = when {
                file.isDirectory -> BrandBlue
                file.isVideo -> BrandAmber
                else -> BrandEmerald
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tint.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = file.name, tint = tint)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusText = if (file.isDirectory) {
                    "Klasör"
                } else {
                    val sizeKb = file.size / 1024.0
                    if (sizeKb >= 1024.0) {
                        String.format("%.1f MB", sizeKb / 1024.0)
                    } else {
                        String.format("%.0f KB", sizeKb)
                    }
                }
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun MediaStreamRow(
    item: PlayableItem,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = if (item.isVideo) Icons.Filled.VideoLibrary else Icons.Filled.MusicNote
            val tint = if (item.isVideo) BrandAmber else BrandEmerald

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(tint.copy(alpha = 0.12f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = item.title, tint = tint)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artist,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Oynat",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaItemRow(
    item: PlayableItem,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    isPlaying: Boolean = false,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    reorderMode: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    viewModel: com.example.ui.MainViewModel? = null
) {
    var expandedMenu by remember { mutableStateOf(false) }

    if (!item.isVideo) {
        // High fidelity minimal design from screenshot 1
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else onPlay() },
                    onLongClick = { onToggleSelect() }
                )
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .padding(vertical = 12.dp, horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left text block (aligned far-left, no icon!)
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isPlaying) BrandEmerald else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.artist.ifEmpty { "Bilinmeyen" },
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right actions block
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (reorderMode) {
                        IconButton(onClick = onMoveUp) { Icon(Icons.Filled.ArrowUpward, "Yukarı taşı") }
                        IconButton(onClick = onMoveDown) { Icon(Icons.Filled.ArrowDownward, "Aşağı taşı") }
                    } else if (selectionMode) {
                        androidx.compose.material3.Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() }
                        )
                    } else {
                        // Playing visualizer signal
                        if (isPlaying) {
                            Icon(
                                imageVector = Icons.Filled.BarChart, // Equalizer/soundwave visual
                                contentDescription = "Oynatılıyor",
                                tint = BrandEmerald,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // More options with "5-30" tag below
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Seçenekler",
                                    tint = Color.Gray,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { expandedMenu = true }
                                )
                                var showRenameDialog by remember { mutableStateOf(false) }
                                if (showRenameDialog) {
                                    var newName by remember { mutableStateOf(item.title) }
                                    androidx.compose.material3.AlertDialog(
                                        onDismissRequest = { showRenameDialog = false },
                                        title = { Text("Yeniden Adlandır") },
                                        text = {
                                            androidx.compose.material3.OutlinedTextField(
                                                value = newName,
                                                onValueChange = { newName = it },
                                                label = { Text("Yeni İsim") }
                                            )
                                        },
                                        confirmButton = {
                                            androidx.compose.material3.TextButton(
                                                onClick = {
                                                    viewModel?.renameItem(item, newName)
                                                    showRenameDialog = false
                                                }
                                            ) { Text("Tamam") }
                                        },
                                        dismissButton = {
                                            androidx.compose.material3.TextButton(onClick = { showRenameDialog = false }) { Text("İptal") }
                                        }
                                    )
                                }

                                DropdownMenu(expanded = expandedMenu, onDismissRequest = { expandedMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (isFavorite) "Favorilerden Çıkar" else "Favorilere Ekle") },
                                        leadingIcon = { Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = BrandEmerald) },
                                        onClick = { expandedMenu = false; onToggleFavorite() }
                                    )
                                    if (viewModel != null) {
                                        DropdownMenuItem(
                                            text = { Text("Yukarı Taşı") },
                                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) },
                                            onClick = { expandedMenu = false; viewModel.moveItemUp(item) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Aşağı Taşı") },
                                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                                            onClick = { expandedMenu = false; viewModel.moveItemDown(item) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Yeniden Adlandır") },
                                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                            onClick = { expandedMenu = false; showRenameDialog = true }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = { expandedMenu = false; viewModel.deleteItem(item) }
                                        )
                                    }
                                }
                            }
                            val dateStr = java.text.SimpleDateFormat("d.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(item.dateAdded))
                            Text(
                                text = dateStr,
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    } else {
        // High fidelity elegant display format for Video Items
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else onPlay() },
                    onLongClick = { onToggleSelect() }
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(BrandAmber.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Filled.VideoLibrary, contentDescription = item.title, tint = BrandAmber)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isPlaying) BrandEmerald else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.artist.ifBlank { "Yerel Video" },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (selectionMode) {
                    androidx.compose.material3.Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() }
                    )
                } else {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Kayıt",
                            tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Klasör",
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0 || ms > 100 * 60 * 60 * 1000) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

fun setActivityOrientation(context: Context, isLandscape: Boolean) {
    val activity = (context as? Activity) ?: return
    activity.requestedOrientation = if (isLandscape) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

// ----------------------------------------------------
// HELPER TAB COMPOSABLES FOR HIGH FIDELITY XPLAYER DESIGNS
// ----------------------------------------------------

@Composable
fun MusicTabContent(
    allMusicList: List<PlayableItem>,
    favoritesList: List<FavoriteMedia>,
    playerState: PlayerState,
    viewModel: MainViewModel,
    searchQuery: String,
    musicSubTab: String,
    onMusicSubTabChange: (String) -> Unit,
    isReorderMode: Boolean,
    onToggleReorder: (Boolean) -> Unit,
    showSortDialog: Boolean,
    onDismissSortDialog: () -> Unit
) {
    val scannedMusicList = if (searchQuery.isBlank()) {
        allMusicList
    } else {
        allMusicList.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
    }
    val tabsList = listOf("ŞARKILAR", "SANATÇILAR")
    
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        // Upper SubTabs: ŞARKILAR, KLASÖRLER, ALBÜMLER, SANATÇILAR
        ScrollableTabRow(
            selectedTabIndex = tabsList.indexOf(musicSubTab),
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.background,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[tabsList.indexOf(musicSubTab)]),
                    color = MaterialTheme.colorScheme.onBackground // Adapts to theme
                )
            },
            divider = {}
        ) {
            tabsList.forEach { title ->
                val isSelected = musicSubTab == title
                Tab(
                    selected = isSelected,
                    onClick = { onMusicSubTabChange(title) },
                    text = {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Gray,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (musicSubTab) {
            "ŞARKILAR" -> {
                val filterUnder60s by viewModel.filterUnder60s.collectAsState()
                val musicSortMode by viewModel.musicSortMode.collectAsState()

                if (showSortDialog) {
                    SortDialog(
                        currentSortMode = musicSortMode,
                        onDismiss = onDismissSortDialog,
                        onConfirm = { viewModel.setMusicSortMode(it) }
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Play all row "Oynat 84"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (scannedMusicList.isNotEmpty()) {
                                    viewModel.playerManager.play(scannedMusicList.first(), scannedMusicList)
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        // Play icon in green circle
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(BrandEmerald, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Oyna",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text("Oynat", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${scannedMusicList.size}",
                            fontSize = 15.sp,
                            color = Color.LightGray
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Simple list of songs
                    if (scannedMusicList.isEmpty()) {
                        EmptyStateView(
                            title = "Sıfır Medya Bulundu",
                            subtitle = "Lütfen ses/müzik dosyalarınızı tık tık ederek tazeleyiniz."
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(scannedMusicList) { item ->
                                val isThisPlaying = playerState.isPlaying && playerState.currentItem?.title == item.title
                                val isFav = favoritesList.any { it.uri == item.uri.toString() }
                                MediaItemRow(
                                    item = item,
                                    viewModel = viewModel,
                                    isFavorite = isFav,
                                    onPlay = { viewModel.playerManager.play(item, scannedMusicList) },
                                    onToggleFavorite = { viewModel.toggleFavorite(item) },
                                    isPlaying = isThisPlaying,
                                    reorderMode = isReorderMode,
                                    onMoveUp = { viewModel.moveItemUp(item) },
                                    onMoveDown = { viewModel.moveItemDown(item) }
                                )
                            }
                        }
                    }
                }
            }
            "KLASÖRLER" -> {
                val groupedMusic = scannedMusicList.groupBy { "Müzik Klasörü" }
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupedMusic.forEach { (folderName, tracks) ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onMusicSubTabChange("ŞARKILAR") },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Folder, contentDescription = folderName, tint = BrandBlue, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(folderName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("${tracks.size} Dosya", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "ALBÜMLER" -> {
                val groupedAlbums = scannedMusicList.groupBy { "Bilinmeyen Albüm" }
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupedAlbums.forEach { (albumName, tracks) ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onMusicSubTabChange("ŞARKILAR") },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Album, contentDescription = albumName, tint = BrandAmber, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(albumName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("${tracks.size} Parça", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "SANATÇILAR" -> {
                val groupedArtists = scannedMusicList.groupBy { it.artist.ifEmpty { "Bilinmeyen Sanatçı" } }
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupedArtists.forEach { (artistName, tracks) ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onMusicSubTabChange("ŞARKILAR") },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Person, contentDescription = artistName, tint = BrandEmerald, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(artistName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("${tracks.size} Şarkı", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoTabContent(
    allVideoList: List<PlayableItem>,
    favoritesList: List<FavoriteMedia>,
    filesList: List<FileEntry>,
    path: String,
    videoFolderView: String,
    videoSortMode: MediaSortMode,
    onVideoFolderViewChange: (String) -> Unit,
    viewModel: MainViewModel,
    searchQuery: String
) {
    val scannedVideoList = if (searchQuery.isBlank()) {
        allVideoList
    } else {
        allVideoList.filter {
            it.title.contains(searchQuery, ignoreCase = true)
        }
    }
    var showVideoSortDialog by remember { mutableStateOf(false) }
    var showFileSortDialog by remember { mutableStateOf(false) }
    val fileSortMode by viewModel.fileSortMode.collectAsState()
    val context = LocalContext.current

    var selectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<PlayableItem>()) }

    if (showVideoSortDialog) {
        SortDialog(
            currentSortMode = videoSortMode,
            onDismiss = { showVideoSortDialog = false },
            onConfirm = { viewModel.setVideoSortMode(it) }
        )
    }

    if (showFileSortDialog) {
        SortDialog(
            currentSortMode = fileSortMode,
            onDismiss = { showFileSortDialog = false },
            onConfirm = { viewModel.setFileSortMode(it) }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Text(
                    text = "${selectedItems.size} seçildi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BrandEmerald
                )
                Row {
                    androidx.compose.material3.TextButton(onClick = {
                        selectionMode = false
                        selectedItems = emptySet()
                    }) {
                        Text("İptal")
                    }
                    if (selectedItems.isNotEmpty()) {
                        androidx.compose.material3.TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.data = Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                                android.widget.Toast.makeText(context, "Lütfen dosya silmek için 'Tüm dosyalara erişim' izni verin", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.deleteMediaFiles(selectedItems.toList())
                                selectionMode = false
                                selectedItems = emptySet()
                            }
                        }) {
                            Text("Sil", color = Color.Red)
                        }
                    }
                }
            } else {
                Text(
                    text = "${scannedVideoList.size} Video",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                androidx.compose.material3.FilterChip(
                    selected = false,
                    onClick = { showVideoSortDialog = true },
                    label = { Text("Sırala", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (scannedVideoList.isEmpty()) {
                EmptyStateView(
                    title = "Video Bulunamadı",
                    subtitle = "Cihazınızda taranmış video bulunmamaktadır."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(scannedVideoList) { item ->
                        val isFav = favoritesList.any { it.uri == item.uri.toString() }
                        val isSelected = selectedItems.contains(item)
                        MediaItemRow(
                            item = item,
                            isFavorite = isFav,
                            onPlay = { viewModel.playerManager.play(item, scannedVideoList) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                            selectionMode = selectionMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedItems = setOf(item)
                                } else {
                                    if (isSelected) {
                                        selectedItems -= item
                                        if (selectedItems.isEmpty()) selectionMode = false
                                    } else {
                                        selectedItems += item
                                    }
                                }
                            }
                        )
                    }
                }
            }
        // Removed extra bracket
    }
}

@Composable
fun PlaylistsTabContent(
    favoriteMusicList: List<FavoriteMedia>,
    favoriteVideoList: List<FavoriteMedia>,
    viewModel: MainViewModel
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Favori Listelerim", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(14.dp))
        
        if (favoriteMusicList.isEmpty() && favoriteVideoList.isEmpty()) {
            EmptyStateView(
                title = "Sıfır Favori",
                subtitle = "Lütfen seve seve dinlemek istediğiniz yapıtları beğenip buraya toplayınız."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (favoriteMusicList.isNotEmpty()) {
                    item {
                        Text("Müzik Favorileri (${favoriteMusicList.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandEmerald)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(favoriteMusicList) { fav ->
                        val item = PlayableItem(
                            uri = Uri.parse(fav.uri),
                            title = fav.title,
                            artist = fav.artist ?: "Bilinmeyen Sanatçı",
                            duration = fav.duration,
                            isVideo = fav.isVideo,
                            lrcLines = LyricsParser.generateMockLyrics(fav.duration, fav.title)
                        )
                        MediaItemRow(
                            item = item,
                            isFavorite = true,
                            onPlay = { viewModel.playerManager.play(item, listOf(item)) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) }
                        )
                    }
                }

                if (favoriteVideoList.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Video Favorileri (${favoriteVideoList.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandAmber)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    items(favoriteVideoList) { fav ->
                        val item = PlayableItem(
                            uri = Uri.parse(fav.uri),
                            title = fav.title,
                            artist = fav.artist ?: "Yerel Video",
                            duration = fav.duration,
                            isVideo = fav.isVideo
                        )
                        MediaItemRow(
                            item = item,
                            isFavorite = true,
                            onPlay = { viewModel.playerManager.play(item, listOf(item)) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SortDialog(
    currentSortMode: MediaSortMode,
    onDismiss: () -> Unit,
    onConfirm: (MediaSortMode) -> Unit
) {
    var selectedType by remember {
        mutableStateOf(
            when (currentSortMode) {
                MediaSortMode.NAME_ASC, MediaSortMode.NAME_DESC -> "isim"
                MediaSortMode.DATE_ASC, MediaSortMode.DATE_DESC -> "tarih"
                MediaSortMode.SIZE_ASC, MediaSortMode.SIZE_DESC -> "boyut"
                MediaSortMode.DURATION_ASC, MediaSortMode.DURATION_DESC -> "uzunluk"
                MediaSortMode.CUSTOM -> "ozel"
            }
        )
    }
    var selectedDirection by remember {
        mutableStateOf(
            when (currentSortMode) {
                MediaSortMode.NAME_ASC, MediaSortMode.DATE_ASC, MediaSortMode.SIZE_ASC, MediaSortMode.DURATION_ASC -> "asc"
                MediaSortMode.NAME_DESC, MediaSortMode.DATE_DESC, MediaSortMode.SIZE_DESC, MediaSortMode.DURATION_DESC -> "desc"
                MediaSortMode.CUSTOM -> "asc"
            }
        )
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(320.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Sırala:",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Types Radio List
                val types = listOf(
                    Pair("isim", "İsim"),
                    Pair("tarih", "Tarih (Gerçek Dosya)"),
                    Pair("boyut", "Boyut"),
                    Pair("uzunluk", "Uzunluk")
                )

                types.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = key }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = (selectedType == key),
                            onClick = { selectedType = key },
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                selectedColor = BrandEmerald,
                                unselectedColor = Color.Gray
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color.LightGray.copy(alpha = 0.5f)
                )

                // Directions Radio List
                val directions = listOf(
                    Pair("desc", "Yeniden eskiye"),
                    Pair("asc", "Eskiden yeniye")
                )

                directions.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDirection = key }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = (selectedDirection == key),
                            onClick = { selectedDirection = key },
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                selectedColor = BrandEmerald,
                                unselectedColor = Color.Gray
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("İPTAL", color = BrandEmerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(
                        onClick = {
                            val mode = when (selectedType) {
                                "isim" -> if (selectedDirection == "desc") MediaSortMode.NAME_DESC else MediaSortMode.NAME_ASC
                                "tarih" -> if (selectedDirection == "desc") MediaSortMode.DATE_DESC else MediaSortMode.DATE_ASC
                                "boyut" -> if (selectedDirection == "desc") MediaSortMode.SIZE_DESC else MediaSortMode.SIZE_ASC
                                else -> if (selectedDirection == "desc") MediaSortMode.DURATION_DESC else MediaSortMode.DURATION_ASC
                            }
                            onConfirm(mode)
                            onDismiss()
                        }
                    ) {
                        Text("TAMAM", color = BrandEmerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun rememberActiveAudioDeviceName(context: Context): String {
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    var deviceName by remember { mutableStateOf(getDeviceName(audioManager)) }

    DisposableEffect(context) {
        val callback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                deviceName = getDeviceName(audioManager)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                deviceName = getDeviceName(audioManager)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    return deviceName
}

private fun getDeviceName(audioManager: android.media.AudioManager): String {
    val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
    for (device in devices) {
        if (device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            device.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
            device.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER) {
            val name = device.productName?.toString()?.trim() ?: ""
            if (name.isNotEmpty()) {
                return name
            }
            return "Bluetooth Kulaklık"
        }
    }
    return "Bu telefondan çıkıyor ses"
}

@Composable
fun ErenCryptographyCard(viewModel: MainViewModel) {
    val currentContext = LocalContext.current
    val secureItems by viewModel.secureItems.collectAsState()
    
    var cardExpanded by remember { mutableStateOf(false) }
    
    // Encrypt State
    var textToEncrypt by remember { mutableStateOf("") }
    var encryptionKey by remember { mutableStateOf("Eren_Secure_2026") }
    var encryptedOutput by remember { mutableStateOf("") }
    var signatureOutput by remember { mutableStateOf("") }
    var cryptTitle by remember { mutableStateOf("Eren Dosya Bilgisi") }
    
    // Decrypt State
    var decryptionInput by remember { mutableStateOf("") }
    var decryptKey by remember { mutableStateOf("Eren_Secure_2026") }
    var decryptedOutput by remember { mutableStateOf("") }
    
    // Verify State
    var verifyContentInput by remember { mutableStateOf("") }
    var verifyKeyInput by remember { mutableStateOf("Eren_Secure_2026") }
    var verifySignatureInput by remember { mutableStateOf("") }
    var verificationResult by remember { mutableStateOf<Boolean?>(null) }
    
    var selectedCryptoTab by remember { mutableStateOf("encrypt") } // "encrypt" or "decrypt" or "verify" or "list"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { cardExpanded = !cardExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "Eren Kripto",
                        tint = BrandEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Eren Özel Kriptografik Güvenlik Merkezi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Askeri düzeyde AES şifreleme ve kişiselleştirilmiş SHA-256 dijital imza motoru.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    imageVector = if (cardExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Genişlet",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (cardExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                // Sub-tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        "encrypt" to "Şifrele & İmza",
                        "decrypt" to "Şifre Çöz",
                        "verify" to "İmza Doğrula",
                        "list" to "Kasaya Kayıtlılar (${secureItems.size})"
                    )
                    tabs.forEach { (tabId, tabName) ->
                        val isSelected = selectedCryptoTab == tabId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) BrandEmerald.copy(alpha = 0.15f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) BrandEmerald else Color.Gray.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedCryptoTab = tabId }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabName,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) BrandEmerald else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedCryptoTab) {
                    "encrypt" -> {
                        Text(
                            text = "Şifrelenecek Ham Veri / Dosya İçeriği",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = textToEncrypt,
                            onValueChange = { textToEncrypt = it },
                            placeholder = { Text("Güvenliğe alınacak hassas metin, dosya içeriği veya şifre girin...") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 12.sp),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Güvenlik Anahtarı (Salt)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = encryptionKey,
                                    onValueChange = { encryptionKey = it },
                                    placeholder = { Text("Şifre") },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Yedek Etiketi",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = cryptTitle,
                                    onValueChange = { cryptTitle = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 12.sp),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (textToEncrypt.isBlank()) {
                                    Toast.makeText(currentContext, "Lütfen şifrelenecek bir içerik girin!", Toast.LENGTH_SHORT).show()
                                } else {
                                    try {
                                        encryptedOutput = ErenCryptEngine.encrypt(textToEncrypt, encryptionKey)
                                        signatureOutput = ErenCryptEngine.generateFileSignature(textToEncrypt, encryptionKey)
                                    } catch (e: Exception) {
                                        Toast.makeText(currentContext, "Şifreleme hatası: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Eren Motoru ile Şifrele & Dijital İmza Oluştur")
                        }

                        if (encryptedOutput.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Kriptolanmış İçerik (Eren-Secure Base64)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandEmerald
                                        )
                                        IconButton(
                                            onClick = {
                                                val clipboard = currentContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("Eren Şifreli Veri", encryptedOutput)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(currentContext, "Şifreli veri panoya kopyalandı!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, "Kopyala", modifier = Modifier.size(14.dp), tint = BrandEmerald)
                                        }
                                    }
                                    Text(
                                        text = encryptedOutput,
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Dosya Benzersiz Dijital İmzası (SHA-256)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandBlue
                                        )
                                        IconButton(
                                            onClick = {
                                                val clipboard = currentContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("Eren Dijital İmza", signatureOutput)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(currentContext, "Dijital imza panoya kopyalandı!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, "Kopyala", modifier = Modifier.size(14.dp), tint = BrandBlue)
                                        }
                                    }
                                    Text(
                                        text = signatureOutput,
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            viewModel.insertSecureItem(cryptTitle, textToEncrypt, encryptionKey)
                                            Toast.makeText(currentContext, "Kriptolu Güvenli Kasaya kaydedildi!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Kriptolu Kasaya Güvenle Kaydet / Yedekle", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    "decrypt" -> {
                        Text(
                            text = "Deşifre Edilecek Şifreli Metin (EREN_SECURE_V1[...])",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = decryptionInput,
                            onValueChange = { decryptionInput = it },
                            placeholder = { Text("Buraya kopyaladığınız EREN_SECURE_V1[...] formatındaki şifreli yedeği yapıştırın...") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 12.sp),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Eren Güvenlik Çözücü Anahtarı (Decryption Key)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = decryptKey,
                            onValueChange = { decryptKey = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (decryptionInput.isBlank()) {
                                    Toast.makeText(currentContext, "Lütfen deşifre edilecek şifreli içeriği girin!", Toast.LENGTH_SHORT).show()
                                } else {
                                    try {
                                        decryptedOutput = ErenCryptEngine.decrypt(decryptionInput, decryptKey)
                                        Toast.makeText(currentContext, "Şifre başarıyla çözüldü!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        decryptedOutput = ""
                                        Toast.makeText(currentContext, "Hatalı şifre veya bozulmuş dosya formatı!", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAmber),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Eren Çözücü ile Şifreyi Kır / Oku")
                        }

                        if (decryptedOutput.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Geri Çözülen Orijinal Veri / Metin",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandAmber
                                        )
                                        IconButton(
                                            onClick = {
                                                val clipboard = currentContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("Orijinal Veri", decryptedOutput)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(currentContext, "Çözülen veri panoya kopyalandı!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, "Kopyala", modifier = Modifier.size(14.dp), tint = BrandAmber)
                                        }
                                    }
                                    Text(
                                        text = decryptedOutput,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    "verify" -> {
                        Text(
                            text = "Doğrulanacak Dosya/Metin İçeriği",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = verifyContentInput,
                            onValueChange = { verifyContentInput = it },
                            placeholder = { Text("İmzayı türeten ham metin içeriğini buraya girin...") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 12.sp),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(
                                    text = "Doğrulanacak İmza (EREN-SIG-...)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = verifySignatureInput,
                                    onValueChange = { verifySignatureInput = it },
                                    placeholder = { Text("EREN-SIG-...") },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Eren İmza Anahtarı",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = verifyKeyInput,
                                    onValueChange = { verifyKeyInput = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (verifyContentInput.isBlank() || verifySignatureInput.isBlank()) {
                                    Toast.makeText(currentContext, "Lütfen gerekli tüm alanları doldurun!", Toast.LENGTH_SHORT).show()
                                } else {
                                    verificationResult = ErenCryptEngine.verifyFileSignature(
                                        verifyContentInput, verifyKeyInput, verifySignatureInput
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("İmza Doğruluğunu Test Et")
                        }

                        verificationResult?.let { isValid ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isValid) BrandEmerald.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isValid) BrandEmerald else Color.Red,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isValid) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                        contentDescription = null,
                                        tint = if (isValid) BrandEmerald else Color.Red,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isValid) "ÖZGÜN DİJİTAL İMZA DOĞRULANDI" else "GEÇERSİZ VEYA BOZULMUŞ İMZA",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isValid) BrandEmerald else Color.Red
                                        )
                                        Text(
                                            text = if (isValid) "Bu dosya / metin içeriği orijinaldir, Eren'in özel şifreleme anahtarı ve salti ile imzasıyla %100 uyuşmaktadır." 
                                                   else "İmza geçersiz veya veri değiştirilmiş! Lütfen verinin, anahtarın ve imzanın doğru olduğundan emin olun.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    "list" -> {
                        if (secureItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Kasada Kayıtlı Veri Yok", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
                                    Text("Şifrele ve İmza sekmesinden kasaya ilk secure kodunu kaydet.", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        } else {
                            Text(
                                text = "Kriptolu Güvenli Kasa Arşivi",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandEmerald,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                secureItems.forEach { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.title,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    val formattedDate = remember(item.timestamp) {
                                                        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault())
                                                        sdf.format(java.util.Date(item.timestamp))
                                                    }
                                                    Text(
                                                        text = formattedDate,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                    )
                                                }
                                                // Actions Row
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    // Copy Encrypted Text
                                                    IconButton(
                                                        onClick = {
                                                            val clipboard = currentContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                            val clip = android.content.ClipData.newPlainText("Eren Kasa Şifreli", item.encryptedContent)
                                                            clipboard.setPrimaryClip(clip)
                                                            Toast.makeText(currentContext, "Şifreli içerik kopyalandı!", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Filled.ContentCopy, "Şifreliyi Kopyala", tint = BrandEmerald, modifier = Modifier.size(16.dp))
                                                    }
                                                    // Copy Signature
                                                    IconButton(
                                                        onClick = {
                                                            val clipboard = currentContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                            val clip = android.content.ClipData.newPlainText("Eren Kasa İmza", item.signature)
                                                            clipboard.setPrimaryClip(clip)
                                                            Toast.makeText(currentContext, "Dijital imza kopyalandı!", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Filled.VerifiedUser, "İmzayı Kopyala", tint = BrandBlue, modifier = Modifier.size(16.dp))
                                                    }
                                                    // Delete
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.deleteSecureItem(item.id)
                                                            Toast.makeText(currentContext, "Güvenli kaydı silindi!", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Filled.Delete, "Sil", tint = Color.Red, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = "Veri: " + item.encryptedContent.take(50) + "...",
                                                fontSize = 11.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                            )
                                            Text(
                                                text = "İmza: " + item.signature,
                                                fontSize = 11.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                color = BrandBlue
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
