package com.regainfocus.app.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.regainfocus.app.ui.theme.AuroraCyan
import com.regainfocus.app.ui.theme.AuroraEmerald
import com.regainfocus.app.ui.theme.AuroraIndigo

@Composable
fun AppIconBadge(
    drawable: Drawable?,
    label: String,
    size: Dp = 42.dp
) {
    val bitmap = remember(drawable) {
        runCatching { drawable?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = label,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Surface(
            modifier = Modifier.size(size),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}

@Composable
fun AuroraGoalRing(
    progress: Float,
    centerTitle: String,
    centerSubtitle: String,
    modifier: Modifier = Modifier,
    ringSize: Dp = 160.dp,
    strokeWidth: Dp = 14.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "goal_ring_anim"
    )
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(ringSize)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (animatedProgress > 0.005f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(AuroraEmerald, AuroraCyan, AuroraIndigo, AuroraEmerald)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = centerSubtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class BarChartPoint(
    val label: String,
    val primaryMinutes: Int,
    val secondaryMinutes: Int = 0
)

@Composable
fun WeeklyBarChart(
    points: List<BarChartPoint>,
    primaryColor: Color = AuroraEmerald,
    secondaryColor: Color? = null,
    goalLineMinutes: Int? = null,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val maxVal = maxOf(
        points.maxOf { maxOf(it.primaryMinutes, it.secondaryMinutes) },
        goalLineMinutes ?: 0,
        30
    ).toFloat()

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val goalColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            val count = points.size
            val slotWidth = size.width / count.toFloat()
            val barWidth = if (secondaryColor != null) {
                (slotWidth * 0.28f).coerceAtMost(16.dp.toPx())
            } else {
                (slotWidth * 0.48f).coerceAtMost(26.dp.toPx())
            }

            // Optional horizontal goal line
            if (goalLineMinutes != null && goalLineMinutes > 0) {
                val goalRatio = (goalLineMinutes / maxVal).coerceIn(0f, 1f)
                val y = size.height * (1f - goalRatio)
                drawLine(
                    color = goalColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            points.forEachIndexed { idx, pt ->
                val centerX = idx * slotWidth + slotWidth / 2f
                val pRatio = (pt.primaryMinutes / maxVal).coerceIn(0f, 1f)
                val pHeight = (size.height * pRatio).coerceAtLeast(if (pt.primaryMinutes > 0) 4.dp.toPx() else 0f)

                if (secondaryColor == null) {
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(centerX - barWidth / 2f, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                    if (pHeight > 0f) {
                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset(centerX - barWidth / 2f, size.height - pHeight),
                            size = Size(barWidth, pHeight),
                            cornerRadius = CornerRadius(8.dp.toPx())
                        )
                    }
                } else {
                    val gap = 3.dp.toPx()
                    val sRatio = (pt.secondaryMinutes / maxVal).coerceIn(0f, 1f)
                    val sHeight = (size.height * sRatio).coerceAtLeast(if (pt.secondaryMinutes > 0) 4.dp.toPx() else 0f)

                    if (pHeight > 0f) {
                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset(centerX - barWidth - gap / 2f, size.height - pHeight),
                            size = Size(barWidth, pHeight),
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                    }
                    if (sHeight > 0f) {
                        drawRoundRect(
                            color = secondaryColor,
                            topLeft = Offset(centerX + gap / 2f, size.height - sHeight),
                            size = Size(barWidth, sHeight),
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SmoothTrendLineChart(
    values: List<Int>,
    lineColor: Color = AuroraCyan,
    modifier: Modifier = Modifier
) {
    if (values.size < 2) return
    val maxVal = maxOf(values.maxOrNull() ?: 1, 30).toFloat()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
    ) {
        val stepX = size.width / (values.size - 1).toFloat()
        val linePath = Path()
        val fillPath = Path()

        values.forEachIndexed { idx, v ->
            val x = idx * stepX
            val y = size.height * (1f - (v / maxVal).coerceIn(0f, 1f))
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.30f), lineColor.copy(alpha = 0.0f))
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun HourlySparkBars(
    hourlyMinutes: List<Int>,
    barColor: Color = AuroraCyan,
    modifier: Modifier = Modifier
) {
    val buckets = if (hourlyMinutes.size == 24) hourlyMinutes else List(24) { 0 }
    val maxVal = maxOf(buckets.maxOrNull() ?: 1, 20).toFloat()
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
        ) {
            val slot = size.width / 24f
            val w = (slot * 0.62f).coerceAtLeast(3f)
            buckets.forEachIndexed { h, mins ->
                val x = h * slot + (slot - w) / 2f
                drawRoundRect(
                    color = track,
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                if (mins > 0) {
                    val barH = (size.height * (mins / maxVal).coerceIn(0f, 1f)).coerceAtLeast(4f)
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - barH),
                        size = Size(w, barH),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("12a", "6a", "12p", "6p", "11p").forEach { lbl ->
                Text(
                    text = lbl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StreakHeatmapGrid(
    activeEpochDays: Set<Long>,
    goalEpochDays: Set<Long>,
    todayEpochDay: Long,
    daysToShow: Int = 28
) {
    val days = remember(todayEpochDay, daysToShow) {
        ((daysToShow - 1) downTo 0).map { offset -> todayEpochDay - offset }
    }
    val rows = days.chunked(7)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { weekRow ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                weekRow.forEach { day ->
                    val isGoal = day in goalEpochDays
                    val isActive = day in activeEpochDays
                    val color = when {
                        isGoal -> AuroraEmerald
                        isActive -> AuroraEmerald.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}
