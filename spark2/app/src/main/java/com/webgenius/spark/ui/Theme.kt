package com.webgenius.spark.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SparkOrange = Color(0xFFEF6548)
private val Light = lightColorScheme(primary = Color(0xFFB83C25), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD0), onPrimaryContainer = Color(0xFF59190A),
    background = Color(0xFFFAF9F6), surface = Color(0xFFFAF9F6), surfaceContainer = Color.White,
    onSurface = Color(0xFF202321), onSurfaceVariant = Color(0xFF656660), outlineVariant = Color(0xFFE7E7E0))
private val Dark = darkColorScheme(primary = Color(0xFFFFAC97), onPrimary = Color(0xFF5C1C0C),
    primaryContainer = Color(0xFF71301F), background = Color(0xFF141716), surface = Color(0xFF141716),
    surfaceContainer = Color(0xFF202422), onSurface = Color(0xFFF1F1E9), onSurfaceVariant = Color(0xFFBFC2B9))
@Composable fun SparkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 43.sp, letterSpacing = (-1.5).sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.7).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp)
        ), content = content)
}
