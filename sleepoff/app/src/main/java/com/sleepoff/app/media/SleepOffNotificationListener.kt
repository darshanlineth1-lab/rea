package com.sleepoff.app.media

import android.service.notification.NotificationListenerService

/**
 * Notification-listener service. The user has to explicitly grant this
 * access in Settings > Notifications > Notification access. Once granted,
 * Android gives us access to the active MediaSession list — which we use
 * to call pause() directly on the current player.
 *
 * We don't actually need to inspect notifications; the service just needs
 * to exist and be enabled for MediaSessionManager.getActiveSessions() to
 * return results with our ComponentName.
 */
class SleepOffNotificationListener : NotificationListenerService() {
    // Intentionally empty — presence + enabled status is what matters.
}
