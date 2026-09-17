package com.webgenius.spark.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SparkOrange = Color(0xFF0866FF)
private val Light = lightColorScheme(primary = Color(0xFF0866FF), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7F0FF), onPrimaryContainer = Color(0xFF0849AB),
    background = Color(0xFFF0F2F5), surface = Color.White, surfaceContainer = Color.White,
    onBackground=Color(0xFF18191A), onSurface = Color(0xFF18191A), onSurfaceVariant = Color(0xFF65676B), outlineVariant = Color(0xFFE4E6EB))
private val Dark = darkColorScheme(primary = Color(0xFF75ADFF), onPrimary = Color(0xFF002C6B),
    primaryContainer = Color(0xFF17365D), background = Color(0xFF18191A), surface = Color(0xFF242526),
    surfaceContainer = Color(0xFF242526), onSurface = Color(0xFFE4E6EB), onSurfaceVariant = Color(0xFFB0B3B8))
@Composable fun SparkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp)
        ), content = content)
}
