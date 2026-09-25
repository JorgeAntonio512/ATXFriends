package com.georgeappdev.atxfriends.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import java.time.Instant

/** Sample Firestore documents shaped like what iOS writes (parity spec §3). */
object TestDocs {
    val T1: Instant = Instant.ofEpochSecond(1_760_000_000, 123_000_000)
    val T2: Instant = Instant.ofEpochSecond(1_760_086_400)

    fun ts(instant: Instant) = Timestamp(instant.epochSecond, instant.nano)

    fun activity(id: String = "act-1", name: String = "Hiking") = mapOf(
        "id" to id,
        "name" to name,
        "isUserAdded" to false,
        "createdAt" to ts(T1),
        "isPrimary" to true,
    )

    fun user() = mapOf(
        "displayName" to "Sam",
        "bio" to "Tacos and trails",
        "photoURLs" to listOf("https://a/0.jpg", "https://a/1.jpg", "https://a/2.jpg"),
        "activityIDs" to listOf("a1", "a2", "a3", "a4"),
        "activityNames" to listOf("Hiking", "Board Games", "Tacos", "Yoga"),
        "activityIsPrimary" to listOf(true, true, true, false),
        "daySlotCombos" to listOf("Monday_Night", "Saturday_Wake Up", "Sunday_Owl Hours"),
        "location" to GeoPoint(30.27, -97.74),
        "latitude" to 30.27,
        "longitude" to -97.74,
        "radiusMiles" to 10.0,
        "createdAt" to ts(T1),
        "updatedAt" to ts(T2),
        "isProfileComplete" to true,
        "notificationPreferences" to mapOf(
            "newMatches" to true,
            "newMessages" to false,
            "planRequests" to true,
            "planConfirmations" to true,
            "groupUpdates" to false,
        ),
        "blockedUsers" to listOf("blocked-1"),
        "locationSharingMode" to "onOpen",
        "locationUpdatedAt" to ts(T2),
        "showUpThumbsUp" to 4L,
        "showUpTotal" to 5L,
        "fcmTokens" to listOf("token"),
        "unreadCount" to 2L,
    )

    fun match() = mapOf(
        "user1ID" to "alice",
        "user2ID" to "bob",
        "user1Decision" to true,
        "isMutualMatch" to false,
        "createdAt" to ts(T1),
        "updatedAt" to ts(T2),
        "overlappingActivityNames" to listOf("Hiking"),
        "overlappingDaySlots" to listOf("Monday_Night"),
        "overlappingCategoryNames" to listOf("outdoorAndNature"),
    )

    fun message() = mapOf(
        "matchID" to "alice_bob",
        "senderID" to "alice",
        "receiverID" to "bob",
        "text" to "Want to hike Saturday?",
        "sentAt" to ts(T1),
        "isRead" to false,
        "kind" to "planProposal",
        "planID" to "plan-1",
    )

    fun plan() = mapOf(
        "matchID" to "alice_bob",
        "proposerID" to "alice",
        "receiverID" to "bob",
        "activity" to activity(),
        "location" to "Zilker Park",
        "locationName" to "Zilker Park",
        "locationLatitude" to 30.2669,
        "locationLongitude" to -97.7729,
        "proposedDates" to listOf(ts(T2)),
        "status" to "counter",
        "confirmedDate" to ts(T2),
        "createdAt" to ts(T1),
        "updatedAt" to ts(T2),
        "counterProposedDates" to listOf(ts(T2)),
        "counterProposedBy" to "bob",
        "isViewed" to true,
    )

    fun todayPlan() = mapOf(
        "creatorID" to "alice",
        "activity" to activity(),
        "scheduledTime" to ts(T2),
        "note" to "Bring water",
        "location" to "Barton Springs",
        "locationName" to "Barton Springs",
        "locationLatitude" to 30.264,
        "locationLongitude" to -97.771,
        "status" to "claimed",
        "claimerID" to "bob",
        "createdAt" to ts(T1),
        "updatedAt" to ts(T2),
        "creatorReportedClaimer" to true,
        "claimerReportedCreator" to false,
    )

    fun groupPlan() = mapOf(
        "hostID" to "alice",
        "inviteeIDs" to listOf("bob", "cara"),
        "responses" to mapOf("bob" to "going", "cara" to "cantMake"),
        "activity" to activity(),
        "location" to "Mueller Park",
        "date" to ts(T2),
        "status" to "active",
        "createdAt" to ts(T1),
        "updatedAt" to ts(T2),
    )

    fun simpatico() = mapOf(
        "userID" to "alice",
        "v2Answers" to mapOf(
            "q1" to mapOf("answer" to "a", "acceptable" to listOf("a", "b"), "importance" to "very"),
            "q2" to mapOf("answer" to "c", "acceptable" to listOf("a", "b", "c")),
        ),
        "v2CompletedAt" to ts(T2),
        "answers" to mapOf("legacyQ" to "legacyA"),
    )

    fun showUpReport() = mapOf(
        "reporterID" to "alice",
        "reportedUserID" to "bob",
        "planID" to "today-1",
        "didShowUp" to true,
        "createdAt" to ts(T1),
    )

    /** Adds a field no model knows about — must be ignored, never crash. */
    fun <V> Map<String, V>.withExtraField(): Map<String, Any?> = this + ("someFutureIosField" to mapOf("x" to 1L))
}
