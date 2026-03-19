package com.orch.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orch.app.AppState
import com.orch.app.R
import com.orch.app.ui.theme.*

@Composable
fun LoadingScreen(
    state: AppState,
    onRetry: () -> Unit
) {
    val isError   = state is AppState.Error
    val isLoading = state is AppState.LoadingModel
    val isDownloading = state is AppState.Downloading

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue  = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(GlassBackground, RoundedCornerShape(28.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(GlassBorder, GlassBorderSubtle)),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // Logo / error icon
                Box(
                    modifier = Modifier
                        .scale(if (!isError) pulseScale else 1f)
                        .size(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isError) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(72.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                            contentDescription = "Orch AI Logo",
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Headline
                Text(
                    text = when (state) {
                        is AppState.Error       -> "Something went wrong"
                        is AppState.LoadingModel -> "Loading model…"
                        is AppState.Downloading  -> "Downloading Orch 1.7B"
                        else                     -> "Starting up…"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = WarmOrange,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                // Sub-text
                Text(
                    text = when (state) {
                        is AppState.Error       -> state.message
                        is AppState.LoadingModel -> "Loading the reasoning model into memory.\nThis takes ~10 seconds on first launch."
                        is AppState.Downloading  -> {
                            val mb = state.bytesDownloaded / 1_048_576L
                            val total = state.totalBytes / 1_048_576L
                            "$mb MB / $total MB\nOne-time download — stored privately on device."
                        }
                        else -> "Initialising inference engine…"
                    },
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                // Progress bar (downloading)
                if (isDownloading || isLoading) {
                    val downloadState = state as? AppState.Downloading
                    val progress = downloadState?.progress ?: 100

                    Spacer(Modifier.height(28.dp))

                    val animatedProgress by animateFloatAsState(
                        targetValue = progress / 100f,
                        animationSpec = tween(300),
                        label = "progress"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(WarmOrange.copy(alpha = 0.7f), WarmOrange)
                                    )
                                )
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    if (isDownloading) {
                        Text(
                            "$progress%",
                            color = WarmOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    } else {
                        // Loading: indeterminate
                        LinearProgressIndicator(
                            color = WarmOrange,
                            trackColor = DarkSurfaceBorder,
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }

                // Error retry button
                if (isError) {
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = WarmOrange),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = OnWarmOrange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Try Again", color = OnWarmOrange, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
