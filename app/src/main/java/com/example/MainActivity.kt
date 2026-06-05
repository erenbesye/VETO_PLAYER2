package com.example

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.ui.MainViewModel
import com.example.ui.screens.MediaAppMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {
  private var controllerFuture: ListenableFuture<MediaController>? = null
  private var mediaController: MediaController? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    try {
      val sessionToken = SessionToken(
        this,
        ComponentName(this, com.example.player.PlaybackService::class.java)
      )
      controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
      controllerFuture?.addListener({
        try {
          mediaController = controllerFuture?.get()
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }, MoreExecutors.directExecutor())
    } catch (e: Exception) {
      e.printStackTrace()
    }

    setContent {
      val mainViewModel: MainViewModel = viewModel()
      val settings by mainViewModel.settings.collectAsState()
      val appTheme by mainViewModel.appTheme.collectAsState()

      MyApplicationTheme(dynamicColor = settings.useDynamicColors, appTheme = appTheme) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          MediaAppMainScreen(viewModel = mainViewModel)
        }
      }
    }
  }

  override fun onDestroy() {
    controllerFuture?.let {
      MediaController.releaseFuture(it)
    }
    super.onDestroy()
  }
}
