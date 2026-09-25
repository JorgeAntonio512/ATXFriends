package com.georgeappdev.atxfriends.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.MainActivity
import com.georgeappdev.atxfriends.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM. With the app in the background, Android shows each push itself (the payload has
 * a `notification` block), using the default channel, icon and color from the manifest, and a
 * tap opens MainActivity with the push's data as extras. With the app in the foreground the push
 * comes here instead, and is shown like iOS `willPresent`: always, except when it's about the
 * conversation already open on screen.
 */
class AtxMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.i(TAG, "push: FCM issued a new token")
        (application as AtxFriendsApp).container.onNewPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data: Map<String, String?> = message.data
        val type = data[PushKeys.TYPE]
        Log.i(TAG, "push: received in foreground, type=$type matchID=${data[PushKeys.MATCH_ID]}")

        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.i(TAG, "push: signed out — not shown")
            return
        }
        if (!shouldShowInForeground(data, OpenThreadTracker.openMatchID)) {
            Log.i(TAG, "push: about the open conversation — not shown (iOS shows badge only)")
            return
        }
        val notification = message.notification
        val title = notification?.title ?: return
        show(applicationContext, title, notification.body.orEmpty(), data, message.messageId)
    }

    companion object {
        fun show(context: Context, title: String, body: String, data: Map<String, String?>, id: String?) {
            if (!canPost(context)) {
                Log.i(TAG, "push: notifications not allowed on this device — not shown")
                return
            }
            val channel = PushType.channelFor(data[PushKeys.TYPE])
            val notification = NotificationCompat.Builder(context, channel.id)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setColor(ContextCompat.getColor(context, R.color.push_accent))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(tapIntent(context, data, id))
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id ?: title, 0, notification)
                Log.i(TAG, "push: shown in channel ${channel.id}")
            } catch (e: SecurityException) {
                Log.w(TAG, "push: permission revoked while showing", e)
            }
        }

        /** The same extras Android puts on a background push's tap, so both taps route alike. */
        private fun tapIntent(context: Context, data: Map<String, String?>, id: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            data.forEach { (key, value) -> intent.putExtra(key, value) }
            return PendingIntent.getActivity(
                context,
                (id ?: data.toString()).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun canPost(context: Context): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
    }
}
