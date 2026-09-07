package com.example.sociva.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.SocivaIndigo
import com.example.ui.theme.SocivaPurple

@Composable
fun SplashScreen(
  modifier: Modifier = Modifier
) {
  // Gentle pulse animation for the Spark brand logo
  val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.96f,
    targetValue = 1.04f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_scale"
  )

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .testTag("spark_splash_screen"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp)
    ) {
      // Centered Spark Logo with gradient boundary
      Box(
        modifier = Modifier
          .scale(pulseScale)
          .size(92.dp)
          .clip(RoundedCornerShape(26.dp))
          .background(
            Brush.linearGradient(
              colors = listOf(SocivaIndigo, SocivaPurple)
            )
          )
          .padding(2.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
        ) {
          Image(
            painter = painterResource(id = R.drawable.spark_app_logo_1788636063462),
            contentDescription = "Spark Logo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Spark Brand Wordmark
      Text(
        text = "Spark",
        fontSize = 36.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1).sp,
        color = MaterialTheme.colorScheme.onBackground
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Ignite connections. Share moments.",
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    // Bottom Loading Indicator and Brand Line
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 44.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(26.dp),
        strokeWidth = 2.5.dp,
        color = MaterialTheme.colorScheme.primary
      )

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "from",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
          text = "SPARK",
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.8.sp,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}
