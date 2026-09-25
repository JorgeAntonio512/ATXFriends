package com.georgeappdev.atxfriends.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import com.georgeappdev.atxfriends.R

/** Names and descriptions shown in Android settings, matching the iOS toggle titles. */
@get:StringRes
val PushChannel.nameRes: Int
    get() = when (this) {
        PushChannel.NEW_MATCHES -> R.string.push_channel_new_matches
        PushChannel.MESSAGES -> R.string.push_channel_messages
        PushChannel.PLAN_REQUESTS -> R.string.push_channel_plan_requests
        PushChannel.PLAN_CONFIRMATIONS -> R.string.push_channel_plan_confirmations
        PushChannel.OTHER -> R.string.push_channel_other
    }

@get:StringRes
val PushChannel.descriptionRes: Int
    get() = when (this) {
        PushChannel.NEW_MATCHES -> R.string.notif_new_matches_desc
        PushChannel.MESSAGES -> R.string.notif_messages_desc
        PushChannel.PLAN_REQUESTS -> R.string.notif_plan_requests_desc
        PushChannel.PLAN_CONFIRMATIONS -> R.string.notif_plan_confirmations_desc
        PushChannel.OTHER -> R.string.push_channel_other_desc
    }

object PushChannels {
    /** Creates every channel (a no-op for ones that exist, keeping the user's changes). */
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = PushChannel.entries.map { channel ->
            NotificationChannel(channel.id, context.getString(channel.nameRes), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(channel.descriptionRes)
            }
        }
        manager.createNotificationChannels(channels)
        Log.i(TAG, "push: channels ready: ${channels.joinToString { it.id }}")
    }
}
