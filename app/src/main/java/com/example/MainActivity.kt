package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.sociva.call.CallActivity
import com.example.sociva.call.CallSignalingApi
import com.example.sociva.push.SparkPush
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

    if (Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 901)
    }

    SparkPush.initialize(this)
    SparkPush.refresh(this)

    lifecycleScope.launch {
      viewModel.currentUserId.collect { userId ->
        if (userId.isNotBlank()) SparkPush.refresh(this@MainActivity)
      }
    }

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

  override fun onResume() {
    super.onResume()
    SparkPush.refresh(this)
  }
}
