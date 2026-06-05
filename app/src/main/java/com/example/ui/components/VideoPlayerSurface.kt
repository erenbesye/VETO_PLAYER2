package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.player.PlayerManager

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerSurface(
    playerManager: PlayerManager,
    modifier: Modifier = Modifier,
    aspectRatioMode: Int = 0
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                val p = playerManager.getPlayer()
                if (player != p) {
                    player = p
                }
                useController = false
                keepScreenOn = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                resizeMode = when (aspectRatioMode) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        },
        update = { playerView ->
            val p = playerManager.getPlayer()
            if (playerView.player != p) {
                playerView.player = p
            }
            playerView.keepScreenOn = true
            playerView.resizeMode = when (aspectRatioMode) {
                1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier
    )
}
