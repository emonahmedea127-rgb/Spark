package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val SparkDarkColorScheme = darkColorScheme(
  primary = SparkBlueLight,
  onPrimary = Color(0xFF0F172A),
  primaryContainer = Color(0xFF312E81),
  onPrimaryContainer = Color(0xFFE0E7FF),
  secondary = SparkPurpleLight,
  onSecondary = Color(0xFF0F172A),
  secondaryContainer = Color(0xFF581C87),
  onSecondaryContainer = Color(0xFFF3E8FF),
  tertiary = SparkPink,
  background = DarkBg,
  onBackground = DarkOnSurface,
  surface = DarkSurface,
  onSurface = DarkOnSurface,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = DarkOnSurfaceVariant,
  outline = DarkOutline,
  error = SparkRed
)

private val SparkLightColorScheme = lightColorScheme(
  primary = SparkIndigo,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFEEF2FF),
  onPrimaryContainer = Color(0xFF3730A3),
  secondary = SparkPurple,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFFAF5FF),
  onSecondaryContainer = Color(0xFF6B21A8),
  tertiary = SparkPink,
  background = LightBg,
  onBackground = LightOnSurface,
  surface = LightSurface,
  onSurface = LightOnSurface,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = LightOnSurfaceVariant,
  outline = LightOutline,
  error = SparkRed
)

private val SocivaDarkColorScheme = SparkDarkColorScheme
private val SocivaLightColorScheme = SparkLightColorScheme

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Use Spark custom branding by default for high visual identity
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> SparkDarkColorScheme
    else -> SparkLightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
