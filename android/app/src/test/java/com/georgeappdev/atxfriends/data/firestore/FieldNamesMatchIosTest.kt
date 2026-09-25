package com.georgeappdev.atxfriends.data.firestore

import com.georgeappdev.atxfriends.data.model.DayOfWeek
import com.georgeappdev.atxfriends.data.model.GroupPlanResponse
import com.georgeappdev.atxfriends.data.model.GroupPlanStatus
import com.georgeappdev.atxfriends.data.model.LocationSharingMode
import com.georgeappdev.atxfriends.data.model.MessageKind
import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.data.model.SimpaticoImportance
import com.georgeappdev.atxfriends.data.model.TimeSlot
import com.georgeappdev.atxfriends.data.model.TodayPlanStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins every Firestore key and enum raw value to the literal strings in the iOS encoders
 * (FirestoreService, PlansService, MessagingService, TodayPlanService, GroupPlansService,
 * SimpaticoService). If one of these fails, a rename would have broken iPhone users.
 */
class FieldNamesMatchIosTest {

    private fun assertKeys(expected: List<String>, holder: Any) {
        val actual = holder::class.java.declaredFields
            .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.get(null) as String }
        assertEquals(holder::class.simpleName, expected.sorted(), actual.sorted())
    }

    @Test
    fun collections() = assertKeys(
        listOf("users", "matches", "messages", "plans", "todayPlans", "groupPlans", "simpaticoAnswers", "showUpReports"),
        Collections,
    )

    @Test
    fun users() = assertKeys(
        listOf(
            "displayName", "bio", "photoURLs", "activityIDs", "activityNames", "activityIsPrimary",
            "daySlotCombos", "location", "latitude", "longitude", "radiusMiles", "createdAt", "updatedAt",
            "isProfileComplete", "notificationPreferences", "blockedUsers", "locationSharingMode",
            "locationUpdatedAt", "showUpThumbsUp", "showUpTotal", "fcmTokens", "unreadCount",
        ),
        UserFields,
    )

    @Test
    fun notificationPreferences() = assertKeys(
        listOf("newMatches", "newMessages", "planRequests", "planConfirmations", "groupUpdates"),
        NotificationPreferenceFields,
    )

    @Test
    fun embeddedActivity() = assertKeys(listOf("id", "name", "isUserAdded", "createdAt", "isPrimary"), ActivityFields)

    @Test
    fun matches() = assertKeys(
        listOf(
            "user1ID", "user2ID", "user1Decision", "user2Decision", "isMutualMatch", "createdAt", "updatedAt",
            "overlappingActivityNames", "overlappingDaySlots", "overlappingCategoryNames",
        ),
        MatchFields,
    )

    @Test
    fun messages() = assertKeys(
        listOf("matchID", "eventID", "senderID", "receiverID", "text", "sentAt", "isRead", "kind", "planID"),
        MessageFields,
    )

    @Test
    fun plans() = assertKeys(
        listOf(
            "matchID", "proposerID", "receiverID", "activity", "location", "locationName", "locationLatitude",
            "locationLongitude", "proposedDates", "status", "confirmedDate", "createdAt", "updatedAt",
            "counterProposedDates", "counterProposedBy", "isViewed",
        ),
        PlanFields,
    )

    @Test
    fun todayPlans() = assertKeys(
        listOf(
            "creatorID", "activity", "scheduledTime", "note", "location", "locationName", "locationLatitude",
            "locationLongitude", "status", "claimerID", "createdAt", "updatedAt", "creatorReportedClaimer",
            "claimerReportedCreator",
        ),
        TodayPlanFields,
    )

    @Test
    fun groupPlans() = assertKeys(
        listOf(
            "hostID", "inviteeIDs", "responses", "activity", "location", "locationName", "locationLatitude",
            "locationLongitude", "date", "status", "createdAt", "updatedAt",
        ),
        GroupPlanFields,
    )

    @Test
    fun simpaticoAnswers() = assertKeys(
        listOf("userID", "v2Answers", "v2CompletedAt", "answers", "answer", "acceptable", "importance"),
        SimpaticoFields,
    )

    @Test
    fun showUpReports() = assertKeys(
        listOf("reporterID", "reportedUserID", "planID", "didShowUp", "createdAt"),
        ShowUpReportFields,
    )

    @Test
    fun enumRawValues() {
        assertEquals(listOf("pending", "counter", "confirmed", "declined", "cancelled", null), PlanStatus.entries.map { it.raw })
        assertEquals(listOf("text", "planProposal", null), MessageKind.entries.map { it.raw })
        assertEquals(listOf("open", "claimed", null), TodayPlanStatus.entries.map { it.raw })
        assertEquals(listOf("invited", "going", "cantMake", null), GroupPlanResponse.entries.map { it.raw })
        assertEquals(listOf("active", "cancelled", null), GroupPlanStatus.entries.map { it.raw })
        assertEquals(listOf("off", "once", "onOpen", null), LocationSharingMode.entries.map { it.raw })
        assertEquals(listOf("little", "somewhat", "very", null), SimpaticoImportance.entries.map { it.raw })
        assertEquals(listOf(1, 10, 50, 0), SimpaticoImportance.entries.map { it.weight })
        assertEquals(
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", null),
            DayOfWeek.entries.map { it.raw },
        )
        assertEquals(listOf("Wake Up", "Afternoon", "Evening", "Night", "Owl Hours", null), TimeSlot.entries.map { it.raw })
    }
}
