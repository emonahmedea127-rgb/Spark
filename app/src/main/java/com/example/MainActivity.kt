package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.sociva.ui.SocivaApp
import com.example.sociva.ui.SocivaViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private val viewModel: SocivaViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val isDarkOverride by viewModel.isDarkTheme.collectAsState()
      val effectiveDarkTheme = isDarkOverride ?: false // Default strictly to light mode as preferred

      MyApplicationTheme(
        darkTheme = effectiveDarkTheme,
        dynamicColor = false // Keep Spark's custom blue/purple visual brand
      ) {
        SocivaApp(
          viewModel = viewModel,
          isDarkTheme = effectiveDarkTheme
        )
      }
    }
  }
}
