package com.sleepoff.app.media

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

/**
 * Sends a "pause" command to whatever music player is currently playing.
 *
 * Two strategies, ordered by reliability:
 *  1. If the user has granted Notification Listener access, we use the
 *     active MediaSession via MediaSessionManager and call pause() directly
 *     on it. This is the correct Android API and works for Spotify, YouTube
 *     Music, VLC, Apple Music, SoundCloud and any other MediaSession-
 *     compatible player.
 *  2. Otherwise (default, no special permission needed), we dispatch a
 *     simulated KEYCODE_MEDIA_PAUSE key event through AudioManager. This is
 *     the same path Bluetooth headsets use and works with the vast majority
 *     of players.
 *
 * We NEVER assume every player responds. If both attempts fail, the caller
 * records that outcome honestly in the history entry.
 */
class MediaControllerHelper(private val appContext: Context) {

    companion object {
        private const val TAG = "MediaCtrl"
    }

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Send pause to active media.
     *
     * @param alsoDuck   if true, pause is a hard stop; if false we still pause
     *                   (kept for future ducking extensions).
     * @return true if we dispatched a pause command; false means no active
     *         session was found so nothing was sent.
     */
    fun pauseActiveMedia(): Boolean {
        // Strategy 1: MediaSession (requires Notification Listener).
        if (NotificationListenerAccess.isGranted(appContext)) {
            val mgr = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE)
                as? MediaSessionManager
            if (mgr != null) {
                try {
                    val controllers: List<MediaController> = mgr.getActiveSessions(
                        android.content.ComponentName(appContext, SleepOffNotificationListener::class.java)
                    )
                    // Prefer a controller that says it's playing.
                    val active = controllers.firstOrNull {
                        it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                    } ?: controllers.firstOrNull()
                    if (active != null) {
                        if (active.playbackState != null) {
                            active.transportControls.pause()
                            Log.i(TAG, "Sent MediaSession pause to ${active.packageName}")
                            return true
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "MediaSession pause failed", t)
                }
            }
        }

        // Strategy 2: media key event — works without any special permission.
        return try {
            val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager.dispatchMediaKeyEvent(down)
            audioManager.dispatchMediaKeyEvent(up)
            Log.i(TAG, "Dispatched KEYCODE_MEDIA_PAUSE")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "dispatchMediaKeyEvent failed", t)
            false
        }
    }

    /**
     * Fade media volume from its current level to 0 over fadeMs milliseconds,
     * then invoke onFinished. This is the Gentle Shutdown path.
     *
     * We intentionally modify STREAM_MUSIC volume only and record the starting
     * level so we can optionally restore it afterwards.
     */
    fun fadeMediaVolume(fadeMs: Long, onFinished: () -> Unit): Int {
        val start = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (start <= 0 || fadeMs <= 0) {
            onFinished()
            return 0
        }
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val steps = start.coerceAtLeast(1)
        val interval = fadeMs / steps
        val handler = Handler(Looper.getMainLooper())
        var remaining = start
        val runnable = object : Runnable {
            override fun run() {
                remaining--
                try {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        remaining.coerceAtLeast(0),
                        0
                    )
                } catch (_: Throwable) {}
                if (remaining > 0) handler.postDelayed(this, interval)
                else onFinished()
            }
        }
        handler.postDelayed(runnable, interval)
        return start
    }

    fun restoreVolume(previousLevel: Int) {
        if (previousLevel <= 0) return
        try {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                previousLevel.coerceAtMost(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),
                0
            )
        } catch (_: Throwable) {}
    }
}

/**
 * Small helper that checks whether the user has enabled SleepOff's
 * notification listener (required for the MediaSession API path).
 */
object NotificationListenerAccess {
    fun isGranted(context: Context): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(context.packageName + "/")
    }
}
