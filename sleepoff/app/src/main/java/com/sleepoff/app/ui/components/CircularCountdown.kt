package com.sleepoff.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/**
 * Large circular countdown ring used on the Home screen.
 *
 * @param progress  0f..1f where 1 is full time remaining and 0 is done
 * @param remainingMs ms remaining (formatted in the center)
 * @param label    "Ready to sleep" / "Music stops in" / "Paused"
 */
@Composable
fun CircularCountdown(
    progress: Float,
    remainingMs: Long,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    stroke: Dp = 14.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = if (progress == 0f) 400 else 700),
        label = "countdownProgress"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        val grad = Brush.sweepGradient(
            0f to MaterialTheme.colorScheme.primary,
            0.65f to MaterialTheme.colorScheme.secondary,
            1f to MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.toPx() - inset * 2, size.toPx() - inset * 2)
            // Track
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            // Progress arc
            drawArc(
                brush = grad,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        // Center text
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatDuration(remainingMs),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 48.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> String.format("%d:%02d:%02d", h, m, s)
        else -> String.format("%d:%02d", m, s)
    }
}

// Helper used by the notification builder and the UI.
fun floorDiv(a: Long, b: Long): Long = floor(a.toDouble() / b.toDouble()).toLong()
