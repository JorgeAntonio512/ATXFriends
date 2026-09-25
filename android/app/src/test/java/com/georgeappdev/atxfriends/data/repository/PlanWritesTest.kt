package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.model.Activity
import com.georgeappdev.atxfriends.data.model.GroupPlan
import com.georgeappdev.atxfriends.data.model.GroupPlanResponse
import com.georgeappdev.atxfriends.data.model.GroupPlanStatus
import com.georgeappdev.atxfriends.data.model.Match
import com.georgeappdev.atxfriends.data.model.Message
import com.georgeappdev.atxfriends.data.model.MessageKind
import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.data.model.TodayPlanStatus
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Every plan write Android makes, pinned to the exact field set, value types and enum strings
 * the iOS encoders produce — and decoded back through the same models iOS-shaped docs use.
 */
class PlanWritesTest {

    private val now = Instant.ofEpochSecond(1_790_000_000, 123_000_000)
    private val date = Instant.ofEpochSecond(1_790_050_000)
    private val activity = Activity("ACT-UUID", "Tacos", false, now, true)
    private fun ts(i: Instant) = Timestamp(i.epochSecond, i.nano)

    private val activityMap = mapOf(
        "id" to "ACT-UUID", "name" to "Tacos", "isUserAdded" to false, "createdAt" to ts(now), "isPrimary" to true,
    )

    @Test
    fun embeddedActivity_hasAllFiveCodableKeys_includingIsPrimary() {
        assertEquals(activityMap, PlanWrites.activity(activity).fields)
    }

    @Test
    fun typedActivity_isLikeIos_uppercaseUuid_notUserAdded_primary() {
        val a = PlanWrites.typedActivity("Board games", now)
        assertEquals("Board games", a.name)
        assertFalse(a.isUserAdded)
        assertEquals(true, a.isPrimary)
        assertEquals(now, a.createdAt)
        assert(a.id.matches(Regex("[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[0-9A-F]{4}-[0-9A-F]{12}"))) { a.id }
    }

    @Test
    fun proposal_freeTextPlace_matchesPlanToFirestoreData() {
        val doc = PlanWrites.proposal("m1", "me", "them", activity, PlanPlace("Veracruz on Cesar Chavez"), date, now)
        assertEquals(
            mapOf(
                "matchID" to "m1", "proposerID" to "me", "receiverID" to "them", "activity" to activityMap,
                "proposedDates" to listOf(ts(date)), "status" to "pending", "createdAt" to ts(now), "updatedAt" to ts(now),
                "location" to "Veracruz on Cesar Chavez",
            ),
            doc.fields,
        )
        // An iPhone decodes it (through the same model Android reads iOS docs with).
        val plan = Plan.fromFirestore("p1", doc.fields)!!
        assertEquals(PlanStatus.PENDING, plan.status)
        assertEquals(listOf(date), plan.proposedDates)
    }

    @Test
    fun proposal_pickedPlace_addsNameAndCoordinates() {
        val doc = PlanWrites.proposal("m1", "me", "them", activity, PlanPlace("Zilker Park", "Zilker Park", 30.26, -97.77), date, now)
        assertEquals("Zilker Park", doc.fields["locationName"])
        assertEquals(30.26, doc.fields["locationLatitude"])
        assertEquals(-97.77, doc.fields["locationLongitude"])
    }

    @Test
    fun proposalMessage_isAPlanProposalWithPlanID_noEventID() {
        val doc = PlanWrites.proposalMessage("m1", "me", "them", "PLAN-1", "Proposed Tacos · Sep 26, 2026 at 7:00 PM", now)
        assertEquals(
            mapOf(
                "senderID" to "me", "receiverID" to "them", "text" to "Proposed Tacos · Sep 26, 2026 at 7:00 PM",
                "sentAt" to ts(now), "isRead" to false, "matchID" to "m1", "kind" to "planProposal", "planID" to "PLAN-1",
            ),
            doc.fields,
        )
        assertEquals(MessageKind.PLAN_PROPOSAL, Message.fromFirestore("x", doc.fields)!!.kind)
    }

    @Test
    fun proposalSummary_usesIosMediumDateShortTime() {
        val zone = ZoneId.of("America/Chicago")
        val at = LocalDateTime.of(2026, 9, 26, 19, 0).atZone(zone).toInstant()
        assertEquals("Proposed Tacos · Sep 26, 2026 at 7:00 PM", PlanWrites.proposalSummary("Tacos", at, zone, Locale.US))
    }

    @Test
    fun todayPlan_isOpen_withNoClaimerOrNote() {
        val doc = PlanWrites.todayPlan("me", activity, date, PlanPlace("Barton Springs"), now)
        assertEquals(
            mapOf(
                "creatorID" to "me", "activity" to activityMap, "scheduledTime" to ts(date), "status" to "open",
                "createdAt" to ts(now), "updatedAt" to ts(now), "location" to "Barton Springs",
            ),
            doc.fields,
        )
        assertEquals(TodayPlanStatus.OPEN, TodayPlan.fromFirestore("t1", doc.fields)!!.status)
    }

    @Test
    fun groupPlan_everyInviteeStartsInvited_statusActive() {
        val doc = PlanWrites.groupPlan("host", listOf("b", "c"), activity, date, PlanPlace("Mozart's"), now)
        assertEquals(
            mapOf(
                "hostID" to "host", "inviteeIDs" to listOf("b", "c"), "responses" to mapOf("b" to "invited", "c" to "invited"),
                "activity" to activityMap, "date" to ts(date), "status" to "active", "createdAt" to ts(now), "updatedAt" to ts(now),
                "location" to "Mozart's",
            ),
            doc.fields,
        )
        val decoded = GroupPlan.fromFirestore("g1", doc.fields)!!
        assertEquals(GroupPlanStatus.ACTIVE, decoded.status)
        assertEquals(mapOf("b" to GroupPlanResponse.INVITED, "c" to GroupPlanResponse.INVITED), decoded.responses)
    }

    @Test(expected = IllegalArgumentException::class)
    fun groupPlan_needsAnInvitee() {
        PlanWrites.groupPlan("host", emptyList(), activity, date, PlanPlace("x"), now)
    }

    @Test
    fun groupPlanResponse_writesOnlyMyKeyAndUpdatedAt() {
        assertEquals(
            mapOf("responses.uidB" to "going", "updatedAt" to ts(now)),
            PlanWrites.groupPlanResponse("uidB", GroupPlanResponse.GOING, now).fields,
        )
        assertEquals("cantMake", PlanWrites.groupPlanResponse("uidB", GroupPlanResponse.CANT_MAKE, now).fields["responses.uidB"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun groupPlanResponse_neverWritesInvitedBack() {
        PlanWrites.groupPlanResponse("uidB", GroupPlanResponse.INVITED, now)
    }

    @Test(expected = IllegalArgumentException::class)
    fun groupPlanResponse_refusesIdsThatWouldBreakTheFieldPath() {
        PlanWrites.groupPlanResponse("a.b", GroupPlanResponse.GOING, now)
    }

    @Test
    fun groupPlanCancel_isStatusAndUpdatedAt() {
        assertEquals(mapOf("status" to "cancelled", "updatedAt" to ts(now)), PlanWrites.groupPlanCancel(now).fields)
    }

    @Test
    fun claim_updatesExactlyTheThreeFieldsTheRuleAllows() {
        assertEquals(
            mapOf("status" to "claimed", "claimerID" to "claimer", "updatedAt" to ts(now)),
            PlanWrites.todayPlanClaim("claimer", now).fields,
        )
    }

    @Test
    fun matchUpgrade_isTheFourFieldsTheMatchesRuleAllows() {
        assertEquals(
            mapOf("user1Decision" to true, "user2Decision" to true, "isMutualMatch" to true, "updatedAt" to ts(now)),
            PlanWrites.matchUpgradeToMutual(now).fields,
        )
    }

    @Test
    fun newMutualMatch_matchesClaimTodayPlanSetData() {
        val doc = PlanWrites.newMutualMatch("aaa", "zzz", "Tacos", now)
        assertEquals(
            mapOf(
                "user1ID" to "aaa", "user2ID" to "zzz", "user1Decision" to true, "user2Decision" to true, "isMutualMatch" to true,
                "overlappingActivityNames" to listOf("Tacos"), "overlappingDaySlots" to emptyList<String>(),
                "createdAt" to ts(now), "updatedAt" to ts(now),
            ),
            doc.fields,
        )
        assertNotNull(Match.fromFirestore("aaa_zzz", doc.fields))
    }

    @Test
    fun matchID_isMinUnderscoreMax_eitherOrder() {
        assertEquals("aaa_zzz", PlanWrites.matchID("zzz", "aaa"))
        assertEquals("aaa_zzz", PlanWrites.matchID("aaa", "zzz"))
    }

    @Test
    fun claimedPlan_isPreConfirmed_claimerProposes_noLocationCopied() {
        val doc = PlanWrites.claimedPlan("aaa_zzz", "claimer", "creator", activity, date, now)
        assertEquals(
            mapOf(
                "matchID" to "aaa_zzz", "proposerID" to "claimer", "receiverID" to "creator", "activity" to activityMap,
                "proposedDates" to listOf(ts(date)), "status" to "confirmed", "confirmedDate" to ts(date),
                "createdAt" to ts(now), "updatedAt" to ts(now),
            ),
            doc.fields,
        )
        assertEquals(date, Plan.fromFirestore("p", doc.fields)!!.confirmedDate)
    }
}
