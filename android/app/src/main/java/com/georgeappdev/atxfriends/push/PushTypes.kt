package com.georgeappdev.atxfriends.push

/**
 * Notification channels, one per iOS notification-preference toggle (NotificationSettingsView),
 * so each can be switched off in Android settings. [OTHER] is the default channel for pushes
 * that arrive without a channel (see [PushType]).
 */
enum class PushChannel(val id: String) {
    NEW_MATCHES("new_matches"),
    MESSAGES("messages"),
    PLAN_REQUESTS("plan_requests"),
    PLAN_CONFIRMATIONS("plan_confirmations"),
    OTHER("other"),
}

/**
 * Every push the Cloud Functions send (`data.type`, functions/src/index.ts). [channel] follows
 * the preference each function checks before sending: reschedule requests are gated by
 * `planRequests`, a declined new time by `planConfirmations`.
 */
enum class PushType(val raw: String, val channel: PushChannel) {
    NEW_MATCH("newMatch", PushChannel.NEW_MATCHES),
    NEW_MESSAGE("newMessage", PushChannel.MESSAGES),
    PLAN_REQUEST("planRequest", PushChannel.PLAN_REQUESTS),
    PLAN_RESCHEDULE_REQUESTED("planRescheduleRequested", PushChannel.PLAN_REQUESTS),
    PLAN_CONFIRMED("planConfirmed", PushChannel.PLAN_CONFIRMATIONS),
    PLAN_RESCHEDULE_DECLINED("planRescheduleDeclined", PushChannel.PLAN_CONFIRMATIONS);

    companion object {
        fun fromRaw(raw: String?): PushType? = entries.firstOrNull { it.raw == raw }

        /** Unknown or missing types still show, in the catch-all channel. */
        fun channelFor(raw: String?): PushChannel = fromRaw(raw)?.channel ?: PushChannel.OTHER
    }
}

/** Payload keys the Cloud Functions put in `data`. */
object PushKeys {
    const val TYPE = "type"
    const val MATCH_ID = "matchID"
}

/**
 * Where tapping a push goes (iOS PendingThreadRoute): the conversation for [matchID]. For a new
 * match only, if that conversation isn't in the loaded list, fall back to the Matches tab.
 */
data class PushRoute(val matchID: String, val fallbackToMatchesTab: Boolean) {
    companion object {
        /** iOS NotificationManager.handleNotificationTap. Null when there's nowhere to go. */
        fun fromData(data: Map<String, String?>): PushRoute? {
            val type = PushType.fromRaw(data[PushKeys.TYPE]) ?: return null
            val matchID = data[PushKeys.MATCH_ID]?.takeIf { it.isNotBlank() } ?: return null
            return PushRoute(matchID, fallbackToMatchesTab = type == PushType.NEW_MATCH)
        }
    }
}

/**
 * iOS `willPresent`: a push about the conversation already open on screen isn't shown (its
 * message or plan arrives live in that thread); every other push is.
 */
fun shouldShowInForeground(data: Map<String, String?>, openMatchID: String?): Boolean {
    val matchID = data[PushKeys.MATCH_ID]
    return matchID == null || matchID != openMatchID
}
