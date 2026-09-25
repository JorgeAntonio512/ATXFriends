package com.georgeappdev.atxfriends.data.model

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.TestDocs.T1
import com.georgeappdev.atxfriends.data.TestDocs.T2
import com.georgeappdev.atxfriends.data.TestDocs.withExtraField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * For every model: a full iOS-shaped document, one missing the optional fields, one with an
 * unknown extra field, one missing a required field, and unknown enum strings.
 */
class ModelDecodingTest {

    // ---- users ----

    @Test
    fun user_fullDocument() {
        val u = UserProfile.fromFirestore("uid", TestDocs.user())!!
        assertEquals("Sam", u.displayName)
        assertEquals("Tacos and trails", u.bio)
        assertEquals(3, u.photoURLs.size)
        assertEquals(listOf(true, true, true, false), u.activities.map { it.isPrimary })
        assertEquals("Board Games", u.activities[1].name)
        assertEquals(DayOfWeek.SATURDAY, u.daySlotCombos[1].day)
        assertEquals(TimeSlot.WAKE_UP, u.daySlotCombos[1].slot)
        assertEquals(TimeSlot.OWL_HOURS, u.daySlotCombos[2].slot)
        assertEquals(30.27, u.latitude, 0.0)
        assertEquals(10.0, u.radiusMiles, 0.0)
        assertEquals(T1, u.createdAt)
        assertEquals(T2, u.updatedAt)
        assertTrue(u.isProfileComplete)
        assertFalse(u.notificationPreferences.newMessages)
        assertFalse(u.notificationPreferences.groupUpdates)
        assertEquals(listOf("blocked-1"), u.blockedUsers)
        assertEquals(LocationSharingMode.ON_OPEN, u.locationSharingMode)
        assertEquals(T2, u.locationUpdatedAt)
        assertEquals(4, u.showUpThumbsUp)
        assertEquals(5, u.showUpTotal)
    }

    @Test
    fun user_missingOptionalFields_getIosDefaults() {
        val optional = setOf(
            "bio", "activityIDs", "activityNames", "activityIsPrimary", "daySlotCombos",
            "notificationPreferences", "blockedUsers", "locationSharingMode", "locationUpdatedAt",
            "showUpThumbsUp", "showUpTotal", "location", "fcmTokens", "unreadCount",
        )
        val u = UserProfile.fromFirestore("uid", TestDocs.user() - optional)!!
        assertEquals("", u.bio)
        assertTrue(u.activities.isEmpty())
        assertTrue(u.daySlotCombos.isEmpty())
        assertEquals(NotificationPreferences(), u.notificationPreferences)
        assertTrue(u.blockedUsers.isEmpty())
        assertEquals(LocationSharingMode.OFF, u.locationSharingMode)
        assertNull(u.locationUpdatedAt)
        assertEquals(0, u.showUpThumbsUp)
        assertEquals(0, u.showUpTotal)
    }

    @Test
    fun user_legacyDocWithoutPrimaryFlags_makesEveryActivityMain() {
        val u = UserProfile.fromFirestore("uid", TestDocs.user() - "activityIsPrimary")!!
        assertTrue(u.activities.all { it.isPrimary })
        val mismatched = TestDocs.user() + ("activityIsPrimary" to listOf(true))
        assertTrue(UserProfile.fromFirestore("uid", mismatched)!!.activities.all { it.isPrimary })
    }

    @Test
    fun user_mismatchedActivityArrays_yieldNoActivities() {
        val doc = TestDocs.user() + ("activityNames" to listOf("Only one"))
        assertTrue(UserProfile.fromFirestore("uid", doc)!!.activities.isEmpty())
    }

    @Test
    fun user_extraFieldIgnored() {
        assertNotNull(UserProfile.fromFirestore("uid", TestDocs.user().withExtraField()))
    }

    @Test
    fun user_missingRequiredField_isNullNotCrash() {
        for (key in listOf("displayName", "photoURLs", "latitude", "longitude", "radiusMiles", "createdAt", "updatedAt", "isProfileComplete")) {
            assertNull(key, UserProfile.fromFirestore("uid", TestDocs.user() - key))
        }
    }

    @Test
    fun user_wrongTypesDoNotCrash() {
        val doc = TestDocs.user() + mapOf(
            "displayName" to 42L,
            "bio" to listOf("not a string"),
            "notificationPreferences" to "nope",
        )
        assertNull(UserProfile.fromFirestore("uid", doc))
        val odd = TestDocs.user() + mapOf("bio" to 7L, "blockedUsers" to listOf("ok", 3L), "showUpTotal" to "five")
        val u = UserProfile.fromFirestore("uid", odd)!!
        assertEquals("", u.bio)
        assertTrue(u.blockedUsers.isEmpty())
        assertEquals(0, u.showUpTotal)
    }

    @Test
    fun user_integerCoordinatesAndDoubleCounters_readLikeIos() {
        val doc = TestDocs.user() + mapOf("radiusMiles" to 25L, "showUpTotal" to 5.0)
        val u = UserProfile.fromFirestore("uid", doc)!!
        assertEquals(25.0, u.radiusMiles, 0.0)
        assertEquals(5, u.showUpTotal)
    }

    @Test
    fun user_unknownEnumsAndCombos_areKeptAsUnknown() {
        val doc = TestDocs.user() + mapOf(
            "locationSharingMode" to "always",
            "daySlotCombos" to listOf("Funday_Night", "Monday_Brunch", "garbage"),
        )
        val u = UserProfile.fromFirestore("uid", doc)!!
        assertEquals(LocationSharingMode.UNKNOWN, u.locationSharingMode)
        assertFalse(u.locationSharingMode.isSharing)
        assertEquals(listOf("Funday_Night", "Monday_Brunch", "garbage"), u.daySlotCombos.map { it.raw })
        assertEquals(DayOfWeek.UNKNOWN, u.daySlotCombos[0].day)
        assertEquals(TimeSlot.UNKNOWN, u.daySlotCombos[1].slot)
        assertFalse(u.daySlotCombos[2].isKnown)
    }

    // ---- matches ----

    @Test
    fun match_fullDocument() {
        val m = Match.fromFirestore("alice_bob", TestDocs.match())!!
        assertEquals("alice", m.user1ID)
        assertEquals(true, m.user1Decision)
        assertNull(m.user2Decision)
        assertFalse(m.isMutualMatch)
        assertEquals(listOf("outdoorAndNature"), m.overlappingCategoryNames)
    }

    @Test
    fun match_missingOptionalAndExtraFields() {
        val m = Match.fromFirestore("id", (TestDocs.match() - setOf("user1Decision", "overlappingCategoryNames")).withExtraField())!!
        assertNull(m.user1Decision)
        assertTrue(m.overlappingCategoryNames.isEmpty())
    }

    @Test
    fun match_missingRequiredField_isNull() {
        for (key in listOf("user1ID", "user2ID", "isMutualMatch", "createdAt", "updatedAt", "overlappingActivityNames", "overlappingDaySlots")) {
            assertNull(key, Match.fromFirestore("id", TestDocs.match() - key))
        }
    }

    // ---- messages ----

    @Test
    fun message_fullDocument() {
        val msg = Message.fromFirestore("m1", TestDocs.message())!!
        assertEquals(MessageKind.PLAN_PROPOSAL, msg.kind)
        assertEquals("plan-1", msg.planID)
        assertEquals(T1, msg.sentAt)
    }

    @Test
    fun message_legacyDocWithoutOptionalFields_isPlainText() {
        val msg = Message.fromFirestore("m1", (TestDocs.message() - setOf("kind", "planID", "matchID")).withExtraField())!!
        assertEquals(MessageKind.TEXT, msg.kind)
        assertEquals("", msg.matchID)
        assertNull(msg.planID)
        assertNull(msg.eventID)
    }

    @Test
    fun message_unknownKind_isUnknown() {
        val msg = Message.fromFirestore("m1", TestDocs.message() + ("kind" to "voiceNote"))!!
        assertEquals(MessageKind.UNKNOWN, msg.kind)
    }

    @Test
    fun message_missingRequiredField_isNull() {
        for (key in listOf("senderID", "receiverID", "text", "sentAt", "isRead")) {
            assertNull(key, Message.fromFirestore("m1", TestDocs.message() - key))
        }
    }

    // ---- plans ----

    @Test
    fun plan_fullDocument() {
        val p = Plan.fromFirestore("plan-1", TestDocs.plan())!!
        assertEquals(PlanStatus.COUNTER_PROPOSED, p.status)
        assertEquals("Hiking", p.activity.name)
        assertEquals(T1, p.activity.createdAt)
        assertEquals(listOf(T2), p.proposedDates)
        assertEquals(T2, p.confirmedDate)
        assertEquals(listOf(T2), p.counterProposedDates)
        assertEquals("bob", p.counterProposedBy)
        assertEquals("Zilker Park", p.location)
        assertTrue(p.isViewed)
    }

    @Test
    fun plan_missingOptionalAndExtraFields() {
        val optional = setOf(
            "location", "locationName", "locationLatitude", "locationLongitude", "confirmedDate",
            "counterProposedDates", "counterProposedBy", "isViewed",
        )
        val p = Plan.fromFirestore("p", (TestDocs.plan() - optional).withExtraField())!!
        assertNull(p.location)
        assertNull(p.locationLatitude)
        assertNull(p.confirmedDate)
        assertNull(p.counterProposedDates)
        assertNull(p.counterProposedBy)
        assertFalse(p.isViewed)
    }

    @Test
    fun plan_everyIosStatusDecodes_andNewOnesAreUnknown() {
        val expected = mapOf(
            "pending" to PlanStatus.PENDING,
            "counter" to PlanStatus.COUNTER_PROPOSED,
            "confirmed" to PlanStatus.CONFIRMED,
            "declined" to PlanStatus.DECLINED,
            "cancelled" to PlanStatus.CANCELLED,
            "counterProposed" to PlanStatus.UNKNOWN, // the Swift case name, never the stored value
            "rescheduled" to PlanStatus.UNKNOWN,
        )
        for ((raw, status) in expected) {
            assertEquals(raw, status, Plan.fromFirestore("p", TestDocs.plan() + ("status" to raw))!!.status)
        }
    }

    @Test
    fun plan_missingRequiredOrBrokenActivity_isNull() {
        for (key in listOf("matchID", "proposerID", "receiverID", "activity", "proposedDates", "status", "createdAt", "updatedAt")) {
            assertNull(key, Plan.fromFirestore("p", TestDocs.plan() - key))
        }
        val brokenActivity = TestDocs.plan() + ("activity" to (TestDocs.activity() - "isPrimary"))
        assertNull(Plan.fromFirestore("p", brokenActivity))
        val badDates = TestDocs.plan() + ("proposedDates" to listOf("tomorrow"))
        assertNull(Plan.fromFirestore("p", badDates))
    }

    // ---- todayPlans ----

    @Test
    fun todayPlan_fullDocument() {
        val t = TodayPlan.fromFirestore("t1", TestDocs.todayPlan())!!
        assertEquals(TodayPlanStatus.CLAIMED, t.status)
        assertEquals("bob", t.claimerID)
        assertEquals(true, t.creatorReportedClaimer)
        assertEquals(false, t.claimerReportedCreator)
        assertEquals(T2, t.scheduledTime)
    }

    @Test
    fun todayPlan_missingOptionalAndExtraFields() {
        val optional = setOf(
            "note", "location", "locationName", "locationLatitude", "locationLongitude",
            "claimerID", "creatorReportedClaimer", "claimerReportedCreator",
        )
        val t = TodayPlan.fromFirestore("t1", (TestDocs.todayPlan() - optional + ("status" to "open")).withExtraField())!!
        assertEquals(TodayPlanStatus.OPEN, t.status)
        assertNull(t.note)
        assertNull(t.claimerID)
        assertNull(t.creatorReportedClaimer)
        assertNull(t.claimerReportedCreator)
    }

    @Test
    fun todayPlan_unknownStatusAndMissingRequired() {
        assertEquals(TodayPlanStatus.UNKNOWN, TodayPlan.fromFirestore("t", TestDocs.todayPlan() + ("status" to "expired"))!!.status)
        for (key in listOf("creatorID", "activity", "scheduledTime", "status", "createdAt", "updatedAt")) {
            assertNull(key, TodayPlan.fromFirestore("t", TestDocs.todayPlan() - key))
        }
    }

    // ---- groupPlans ----

    @Test
    fun groupPlan_fullDocument() {
        val g = GroupPlan.fromFirestore("g1", TestDocs.groupPlan())!!
        assertEquals(GroupPlanStatus.ACTIVE, g.status)
        assertEquals(GroupPlanResponse.GOING, g.responses["bob"])
        assertEquals(GroupPlanResponse.CANT_MAKE, g.responses["cara"])
        assertEquals("Mueller Park", g.location)
        assertNull(g.locationName)
    }

    @Test
    fun groupPlan_extraFieldAndUnknownValues() {
        val doc = TestDocs.groupPlan().withExtraField() + mapOf(
            "status" to "archived",
            "responses" to mapOf("bob" to "maybe", "cara" to 3L),
        )
        val g = GroupPlan.fromFirestore("g1", doc)!!
        assertEquals(GroupPlanStatus.UNKNOWN, g.status)
        assertEquals(GroupPlanResponse.UNKNOWN, g.responses["bob"])
        assertEquals(GroupPlanResponse.UNKNOWN, g.responses["cara"])
    }

    @Test
    fun groupPlan_missingRequiredField_isNull() {
        for (key in listOf("hostID", "inviteeIDs", "responses", "activity", "date", "status", "createdAt", "updatedAt")) {
            assertNull(key, GroupPlan.fromFirestore("g", TestDocs.groupPlan() - key))
        }
    }

    // ---- simpaticoAnswers ----

    @Test
    fun simpatico_fullDocument() {
        val s = SimpaticoState.fromFirestore("alice", TestDocs.simpatico())
        assertEquals(SimpaticoImportance.VERY, s.answers["q1"]!!.importance)
        assertNull(s.answers["q2"]!!.importance)
        assertEquals(listOf("a", "b", "c"), s.answers["q2"]!!.acceptable)
        assertEquals(T2, s.completedAt)
        assertTrue(s.hasLegacyAnswers)
    }

    @Test
    fun simpatico_missingDocAndOptionalFields() {
        val empty = SimpaticoState.fromFirestore("alice", null)
        assertTrue(empty.answers.isEmpty())
        assertNull(empty.completedAt)
        assertFalse(empty.hasLegacyAnswers)

        val partial = SimpaticoState.fromFirestore("alice", (TestDocs.simpatico() - setOf("v2CompletedAt", "answers")).withExtraField())
        assertEquals(2, partial.answers.size)
        assertNull(partial.completedAt)
        assertFalse(partial.hasLegacyAnswers)
    }

    @Test
    fun simpatico_brokenAnswersSkipped_unknownImportanceKept() {
        val doc = TestDocs.simpatico() + ("v2Answers" to mapOf(
            "ok" to mapOf("answer" to "a", "acceptable" to listOf("a"), "importance" to "extremely"),
            "noAcceptable" to mapOf("answer" to "a"),
            "notAMap" to "x",
        ))
        val s = SimpaticoState.fromFirestore("alice", doc)
        assertEquals(setOf("ok"), s.answers.keys)
        assertEquals(SimpaticoImportance.UNKNOWN, s.answers["ok"]!!.importance)
    }

    // ---- showUpReports ----

    @Test
    fun showUpReport_decodes() {
        val r = ShowUpReport.fromFirestore("r1", TestDocs.showUpReport().withExtraField())!!
        assertEquals("bob", r.reportedUserID)
        assertTrue(r.didShowUp)
        for (key in listOf("reporterID", "reportedUserID", "planID", "didShowUp", "createdAt")) {
            assertNull(key, ShowUpReport.fromFirestore("r1", TestDocs.showUpReport() - key))
        }
    }

    // ---- embedded activity ----

    @Test
    fun activity_requiresEveryCodableKey() {
        assertNotNull(Activity.fromFirestore(TestDocs.activity().withExtraField()))
        assertNull(Activity.fromFirestore(null))
        for (key in listOf("id", "name", "isUserAdded", "createdAt", "isPrimary")) {
            assertNull(key, Activity.fromFirestore(TestDocs.activity() - key))
        }
    }
}
