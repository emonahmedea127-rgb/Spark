package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.sociva.call.CallActivity
import com.example.sociva.call.CallSignalingApi
import com.example.sociva.ui.SocivaApp
import com.example.sociva.ui.SocivaViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val viewModel: SocivaViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    lifecycleScope.launch {
      var lastLaunchedCallId: String? = null
      while (isActive) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
          runCatching { CallSignalingApi.findIncomingCall() }.getOrNull()?.let { call ->
            if (call.id != lastLaunchedCallId) {
              lastLaunchedCallId = call.id
              startActivity(CallActivity.incomingIntent(this@MainActivity, call))
            }
          }
        }
        delay(1500)
      }
    }

    setContent {
      val isDarkOverride by viewModel.isDarkTheme.collectAsState()
      val effectiveDarkTheme = isDarkOverride ?: false

      MyApplicationTheme(
        darkTheme = effectiveDarkTheme,
        dynamicColor = false
      ) {
        SocivaApp(
          viewModel = viewModel,
          isDarkTheme = effectiveDarkTheme
        )
      }
    }
  }
}
