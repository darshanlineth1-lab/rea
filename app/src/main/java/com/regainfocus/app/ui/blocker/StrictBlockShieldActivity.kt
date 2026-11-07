package com.regainfocus.app.ui.blocker

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.regainfocus.app.MainActivity
import com.regainfocus.app.RegainFocusApplication
import com.regainfocus.app.data.preferences.ActiveStrictSessionStore
import com.regainfocus.app.domain.engine.StrictRemainingTimeCalculator
import com.regainfocus.app.domain.model.PersistedTimerSessionState
import com.regainfocus.app.domain.model.StrictEnforcementLevel
import com.regainfocus.app.service.FocusTimerService
import com.regainfocus.app.ui.theme.AuroraCyan
import com.regainfocus.app.ui.theme.AuroraEmerald
import com.regainfocus.app.ui.theme.MidnightBg
import com.regainfocus.app.ui.theme.RegainFocusTheme
import com.regainfocus.app.ui.theme.StrictCrimson
import com.regainfocus.app.util.TimeFormatters
import kotlinx.coroutines.delay

/**
 * Dedicated Non-Bypassable Focus Interception Screen.
 *
 * STRICT MODE ENFORCEMENT INVARIANTS:
 * - Zero unlock buttons, zero skip buttons, zero snooze buttons, zero instant disable buttons.
 * - Hardware/gesture Back navigation routes exclusively to the Android Home Launcher (never back to the blocked app).
 * - Returning to the launcher leaves blocking 100% active; re-opening any blocked app immediately intercepts again.
 * - Automatically dismisses itself and restores access when the session timer completes (`remainingMillis <= 0`).
 */
class StrictBlockShieldActivity : ComponentActivity() {

    private var blockedPkgState by mutableStateOf("")
    private var reasonState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extractExtras(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToHomeLauncher()
            }
        })

        val app = application as RegainFocusApplication
        val container = app.container

        setContent {
            val settings by container.productivityRepository.settingsFlow.collectAsState(
                initial = com.regainfocus.app.domain.model.UserSettings()
            )
            val sessionState by container.activeSessionStore.sessionStateFlow.collectAsState()

            val bootId = remember { ActiveStrictSessionStore.currentBootCountId(this) }
            var remainingMillis by remember { mutableLongStateOf(computeRemaining(sessionState, bootId)) }

            LaunchedEffect(sessionState) {
                while (true) {
                    val rem = computeRemaining(sessionState, bootId)
                    remainingMillis = rem
                    if (sessionState.isActive && sessionState.isStrictActive && rem <= 0L) {
                        FocusTimerService.triggerExpiryCheck(this@StrictBlockShieldActivity)
                        finish()
                        break
                    }
                    if (!sessionState.isActive && reasonState.contains("Focus", ignoreCase = true)) {
                        finish()
                        break
                    }
                    delay(250L)
                }
            }

            val appLabel = remember(blockedPkgState) {
                if (blockedPkgState.isBlank()) "Distracting App"
                else container.usageRepository.getAppLabel(blockedPkgState)
            }
            val appIcon = remember(blockedPkgState) {
                if (blockedPkgState.isBlank()) null
                else container.usageRepository.getAppIcon(blockedPkgState)
            }

            RegainFocusTheme(themeMode = settings.themeMode) {
                StrictFocusShieldScreen(
                    blockedAppLabel = appLabel,
                    blockedAppIcon = appIcon,
                    reasonLabel = reasonState.ifBlank { "Blocked during active Focus Session" },
                    sessionState = sessionState,
                    remainingMillis = remainingMillis,
                    onReturnToLauncher = { navigateToHomeLauncher() },
                    onOpenFocusTimer = {
                        startActivity(
                            Intent(this@StrictBlockShieldActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra(MainActivity.EXTRA_OPEN_TAB, "focus")
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractExtras(intent)
    }

    private fun extractExtras(intent: Intent?) {
        blockedPkgState = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
        reasonState = intent?.getStringExtra(EXTRA_REASON_LABEL).orEmpty()
    }

    private fun computeRemaining(state: PersistedTimerSessionState, bootId: String): Long {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        return if (state.isStrictActive) {
            StrictRemainingTimeCalculator.computeStrictTotalRemainingMillis(state, nowWall, nowElapsed, bootId)
        } else {
            StrictRemainingTimeCalculator.computePhaseRemainingMillis(state, nowWall, nowElapsed, bootId)
        }
    }

    private fun navigateToHomeLauncher() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        private const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        private const val EXTRA_REASON_LABEL = "extra_reason_label"

        fun launch(context: Context, blockedPackage: String, reasonLabel: String) {
            val intent = Intent(context, StrictBlockShieldActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                putExtra(EXTRA_BLOCKED_PACKAGE, blockedPackage)
                putExtra(EXTRA_REASON_LABEL, reasonLabel)
            }
            runCatching { context.startActivity(intent) }
        }
    }
}

@Composable
fun StrictFocusShieldScreen(
    blockedAppLabel: String,
    blockedAppIcon: Drawable?,
    reasonLabel: String,
    sessionState: PersistedTimerSessionState,
    remainingMillis: Long,
    onReturnToLauncher: () -> Unit,
    onOpenFocusTimer: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "shield_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val totalDuration = (sessionState.strictHardEndWallMillis - sessionState.sessionStartWallMillis)
        .coerceAtLeast(sessionState.phaseDurationMillis)
        .coerceAtLeast(1L)
    val progress = if (sessionState.isActive) {
        (1f - (remainingMillis.toFloat() / totalDuration.toFloat())).coerceIn(0.02f, 1f)
    } else 1f

    val iconBitmap = remember(blockedAppIcon) {
        runCatching { blockedAppIcon?.toBitmap(128, 128)?.asImageBitmap() }.getOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A161F),
                        MidnightBg,
                        Color(0xFF140D18)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Enforcement Badge
            Surface(
                shape = RoundedCornerShape(50),
                color = StrictCrimson.copy(alpha = 0.16f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = StrictCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (sessionState.strictLevel != StrictEnforcementLevel.NONE) {
                            "${sessionState.strictLevel.badge} · Locked Until Timer Ends"
                        } else {
                            "Focus Shield Active"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFFFB4C2)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Circular Countdown & Blocked App Emblem
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize().scale(pulseScale)) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(AuroraEmerald, AuroraCyan, StrictCrimson, AuroraEmerald)
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = blockedAppLabel,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = AuroraEmerald,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (sessionState.isActive) {
                        Text(
                            text = TimeFormatters.formatMillisClock(remainingMillis),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "REMAINING",
                            style = MaterialTheme.typography.labelSmall,
                            color = AuroraEmerald
                        )
                    } else {
                        Text(
                            text = "LIMIT REACHED",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = StrictCrimson
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = "$blockedAppLabel is Restricted",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = reasonLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB3C7D6),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val tagLine = if (sessionState.isActive) {
                        "Current Goal: ${sessionState.sessionTag}" +
                            (sessionState.blockingGroupName?.let { " · $it" } ?: "")
                    } else {
                        "Your daily budget for $blockedAppLabel has been used for today."
                    }
                    Text(
                        text = tagLine,
                        style = MaterialTheme.typography.titleSmall,
                        color = AuroraEmerald,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "No early unlock or skip option is available in Strict Mode. Access restores automatically when the timer reaches 00:00.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9BB0C1),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onOpenFocusTimer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuroraEmerald,
                    contentColor = Color(0xFF042619)
                )
            ) {
                Icon(Icons.Default.Timer, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Return to Focus Timer", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onReturnToLauncher,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Go to Home Screen (Keep Shield On)", color = Color.White)
            }
        }
    }
}
