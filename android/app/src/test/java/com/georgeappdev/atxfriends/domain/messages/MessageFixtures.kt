package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.data.model.Activity
import com.georgeappdev.atxfriends.data.model.Match
import com.georgeappdev.atxfriends.data.model.Message
import com.georgeappdev.atxfriends.data.model.MessageKind
import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.data.model.PlanStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Builders for the Messages domain tests. All times are in Austin. */
object MessageFixtures {
    val AUSTIN: ZoneId = ZoneId.of("America/Chicago")

    /** Thursday, Sep 24 2026. */
    fun at(year: Int = 2026, month: Int = 9, day: Int = 24, hour: Int = 12, minute: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(AUSTIN).toInstant()

    val NOON: Instant = at()

    fun dates(now: Instant = NOON) = MessageDates(now, AUSTIN, Locale.US)

    fun match(id: String = "me_sam", createdAt: Instant = at(month = 1, day = 1)) = Match(
        id = id,
        user1ID = "me",
        user2ID = "sam",
        user1Decision = true,
        user2Decision = true,
        isMutualMatch = true,
        createdAt = createdAt,
        updatedAt = createdAt,
        overlappingActivityNames = listOf("Hiking"),
        overlappingDaySlots = emptyList(),
        overlappingCategoryNames = emptyList(),
    )

    fun message(
        id: String = "m1",
        sentAt: Instant = NOON,
        senderID: String = "sam",
        kind: MessageKind = MessageKind.TEXT,
        planID: String? = null,
    ) = Message(
        id = id,
        matchID = "me_sam",
        eventID = null,
        senderID = senderID,
        receiverID = if (senderID == "me") "sam" else "me",
        text = "hi",
        sentAt = sentAt,
        isRead = false,
        kind = kind,
        planID = planID,
    )

    fun plan(
        id: String = "p1",
        activity: String = "Poker",
        status: PlanStatus = PlanStatus.CONFIRMED,
        confirmedDate: Instant? = at(day = 26, hour = 19),
        proposedDate: Instant = at(day = 26, hour = 19),
        updatedAt: Instant = at(month = 1, day = 1),
        counterDate: Instant? = null,
        counterProposedBy: String? = null,
        location: String? = null,
        locationName: String? = null,
    ) = Plan(
        id = id,
        matchID = "me_sam",
        proposerID = "me",
        receiverID = "sam",
        activity = Activity(id = "a", name = activity, isUserAdded = false, createdAt = NOON, isPrimary = true),
        location = location,
        locationName = locationName,
        locationLatitude = null,
        locationLongitude = null,
        proposedDates = listOf(proposedDate),
        status = status,
        confirmedDate = confirmedDate,
        createdAt = at(month = 1, day = 1),
        updatedAt = updatedAt,
        counterProposedDates = counterDate?.let(::listOf),
        counterProposedBy = counterProposedBy,
        isViewed = true,
    )

    fun thread(
        id: String,
        lastMessageAt: Instant? = null,
        plan: Plan? = null,
        matchCreatedAt: Instant = at(month = 1, day = 1),
    ) = MessageThread(
        id = id,
        match = match(id, matchCreatedAt),
        otherUserID = "sam",
        otherUserName = "Sam",
        otherUserPhotoURL = null,
        lastMessage = lastMessageAt?.let { message(id = "$id-last", sentAt = it) },
        upcomingPlan = plan,
    )
}
