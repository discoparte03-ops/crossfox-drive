package com.crossfox.drive

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

data class NotifItem(val key: String, val appLabel: String, val title: String, val text: String)
data class NowPlaying(val title: String?, val artist: String?, val playing: Boolean, val pkg: String)

/** Estado en memoria (nunca se guarda ni se envía fuera del dispositivo). */
object Bridge {
    val notifications = MutableStateFlow<List<NotifItem>>(emptyList())
    val nowPlaying = MutableStateFlow<NowPlaying?>(null)
    @Volatile var controller: MediaController? = null
}

class CrossfoxListenerService : NotificationListenerService() {
    private var msm: MediaSessionManager? = null
    private var current: MediaController? = null

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
    }
    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { updateSessions(it) }

    override fun onListenerConnected() {
        val cn = ComponentName(this, CrossfoxListenerService::class.java)
        try {
            msm = getSystemService(MediaSessionManager::class.java)
            msm?.addOnActiveSessionsChangedListener(sessionListener, cn)
            updateSessions(msm?.getActiveSessions(cn))
        } catch (_: SecurityException) { }
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionListener) } catch (_: Exception) { }
        current?.unregisterCallback(cb)
        current = null
        Bridge.controller = null
        Bridge.nowPlaying.value = null
        Bridge.notifications.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refreshNotifications()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refreshNotifications()

    private fun updateSessions(list: List<MediaController>?) {
        current?.unregisterCallback(cb)
        current = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        current?.registerCallback(cb)
        Bridge.controller = current
        publish()
    }

    private fun publish() {
        val c = current
        if (c == null) { Bridge.nowPlaying.value = null; return }
        val md = c.metadata
        Bridge.nowPlaying.value = NowPlaying(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            pkg = c.packageName,
        )
    }

    private fun refreshNotifications() {
        val active = try { activeNotifications?.toList().orEmpty() } catch (_: Exception) { emptyList() }
        Bridge.notifications.value = active
            .filter { !it.isOngoing && it.packageName != packageName }
            .map { sbn ->
                val ex = sbn.notification.extras
                val label = try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
                } catch (_: Exception) { sbn.packageName }
                NotifItem(
                    key = sbn.key,
                    appLabel = label,
                    title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                )
            }
    }
}
