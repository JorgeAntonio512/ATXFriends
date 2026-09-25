package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.TestDocs.ts
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.at
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.plan
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every 1-on-1 plan transition in spec §5.5, checked two ways: the update is field-for-field
 * what PlansService.swift writes, and the resulting document passes a port of the deployed
 * `plans` update rule (firestore.rules `planRescheduleUpdateIsValid`). Fixture plans have
 * proposer "me" and receiver "sam".
 */
class PlanUpdatesTest {

    private val now = at(day = 25, hour = 9)
    private val original = at(day = 26, hour = 19)
    private val suggested = at(day = 27, hour = 15)

    // region pending → confirmed / declined (PlanProposalCard)

    @Test
    fun accept_confirmsAtTheProposedTime() {
        val pending = plan(status = PlanStatus.PENDING, confirmedDate = null, proposedDate = original)
        val update = PlanUpdates.accept(pending, now)!!
        assertEquals(mapOf("status" to "confirmed", "confirmedDate" to ts(original), "updatedAt" to ts(now)), update.fields)
        assertTrue(Rules.allows(doc(pending), update, "sam"))
    }

    @Test
    fun accept_withNoProposedDate_writesNothing_likeIos() {
        val broken = plan(status = PlanStatus.PENDING, confirmedDate = null).copy(proposedDates = emptyList())
        assertNull(PlanUpdates.accept(broken, now))
    }

    @Test
    fun decline_isStatusAndUpdatedAtOnly() {
        val pending = plan(status = PlanStatus.PENDING, confirmedDate = null)
        val update = PlanUpdates.decline(now)
        assertEquals(mapOf("status" to "declined", "updatedAt" to ts(now)), update.fields)
        assertTrue(Rules.allows(doc(pending), update, "sam"))
    }

    // endregion

    // region confirmed → cancelled / counter

    @Test
    fun cancel_isStatusAndUpdatedAtOnly_fromConfirmedOrPendingReschedule() {
        val update = PlanUpdates.cancel(now)
        assertEquals(mapOf("status" to "cancelled", "updatedAt" to ts(now)), update.fields)
        assertTrue(Rules.allows(doc(plan()), update, "me"))
        assertTrue(Rules.allows(doc(plan()), update, "sam"))
        val counter = plan(status = PlanStatus.COUNTER_PROPOSED, counterDate = suggested, counterProposedBy = "sam")
        assertTrue("either person may cancel with a request pending", Rules.allows(doc(counter), update, "me"))
    }

    @Test
    fun requestReschedule_keepsTheOriginalTime_andNamesTheRequester() {
        val update = PlanUpdates.requestReschedule(suggested, "me", now)
        assertEquals(
            mapOf(
                "status" to "counter",
                "counterProposedDates" to listOf(ts(suggested)),
                "counterProposedBy" to "me",
                "updatedAt" to ts(now),
            ),
            update.fields,
        )
        assertFalse("confirmedDate stays until accepted", "confirmedDate" in update.fields)
        assertTrue(Rules.allows(doc(plan(confirmedDate = original)), update, "me"))
    }

    @Test
    fun requestReschedule_rulesRejectNamingSomeoneElse_orASecondRequest() {
        val asSam = PlanUpdates.requestReschedule(suggested, "sam", now)
        assertFalse(Rules.allows(doc(plan()), asSam, "me"))
        val pending = plan(status = PlanStatus.COUNTER_PROPOSED, counterDate = suggested, counterProposedBy = "sam")
        assertFalse("one request at a time", Rules.allows(doc(pending), PlanUpdates.requestReschedule(at(day = 28), "me", now), "me"))
    }

    // endregion

    // region counter → confirmed (reschedule answers)

    private val requestedBySam = plan(status = PlanStatus.COUNTER_PROPOSED, confirmedDate = original, counterDate = suggested, counterProposedBy = "sam")

    @Test
    fun acceptReschedule_movesToTheSuggestedTime_andClearsTheRequest() {
        val update = PlanUpdates.acceptReschedule(requestedBySam, now)!!
        assertEquals(listOf("status", "confirmedDate", "counterProposedDates", "counterProposedBy", "updatedAt"), update.fields.keys.toList())
        assertEquals("confirmed", update.fields["status"])
        assertEquals(ts(suggested), update.fields["confirmedDate"])
        assertTrue(update.fields["counterProposedDates"] is FieldValue)
        assertTrue(update.fields["counterProposedBy"] is FieldValue)
        assertEquals(ts(now), update.fields["updatedAt"])

        assertTrue("the other person can accept", Rules.allows(doc(requestedBySam), update, "me"))
        assertFalse("the requester can't accept their own request", Rules.allows(doc(requestedBySam), update, "sam"))
        assertEquals(ts(suggested), applied(requestedBySam, update)["confirmedDate"])
    }

    @Test
    fun declineReschedule_keepsTheOriginalTime_andNeverCancels() {
        val update = PlanUpdates.declineReschedule(requestedBySam, now)
        assertEquals(listOf("status", "counterProposedDates", "counterProposedBy", "updatedAt"), update.fields.keys.toList())
        assertEquals("confirmed", update.fields["status"])
        assertTrue(Rules.allows(doc(requestedBySam), update, "me"))
        assertFalse(Rules.allows(doc(requestedBySam), update, "sam"))
        val after = applied(requestedBySam, update)
        assertEquals(ts(original), after["confirmedDate"])
        assertFalse("counterProposedDates" in after)
        assertFalse("counterProposedBy" in after)
    }

    @Test
    fun legacyRequest_withNoRequester_canBeAnsweredByEitherPerson() {
        val legacy = requestedBySam.copy(counterProposedBy = null)
        assertTrue(Rules.allows(doc(legacy), PlanUpdates.acceptReschedule(legacy, now)!!, "sam"))
        assertTrue(Rules.allows(doc(legacy), PlanUpdates.declineReschedule(legacy, now), "me"))
    }

    @Test
    fun legacyDecline_withNoConfirmedDate_restoresTheOriginalProposedTime() {
        val legacy = plan(status = PlanStatus.COUNTER_PROPOSED, confirmedDate = null, proposedDate = original, counterDate = suggested)
        val update = PlanUpdates.declineReschedule(legacy, now)
        assertEquals(ts(original), update.fields["confirmedDate"])
        assertTrue(Rules.allows(doc(legacy), update, "sam"))
    }

    @Test
    fun acceptReschedule_withNoSuggestedTime_writesNothing_likeIos() {
        assertNull(PlanUpdates.acceptReschedule(requestedBySam.copy(counterProposedDates = null), now))
        assertNull("not a pending request", PlanUpdates.acceptReschedule(plan(), now))
    }

    // endregion

    @Test
    fun nonParticipants_canNeverUpdate() {
        assertFalse(Rules.allows(doc(plan()), PlanUpdates.cancel(now), "stranger"))
    }

    // region Firestore semantics + rules port

    /** A plan document as iOS writes it. */
    private fun doc(p: Plan): Map<String, Any?> = buildMap {
        put("matchID", p.matchID)
        put("proposerID", p.proposerID)
        put("receiverID", p.receiverID)
        put("status", p.status.raw)
        put("proposedDates", p.proposedDates.map(::ts))
        put("createdAt", ts(p.createdAt))
        put("updatedAt", ts(p.updatedAt))
        p.confirmedDate?.let { put("confirmedDate", ts(it)) }
        p.counterProposedDates?.let { put("counterProposedDates", it.map(::ts)) }
        p.counterProposedBy?.let { put("counterProposedBy", it) }
    }

    private fun applied(p: Plan, update: DocumentUpdate) = Rules.apply(doc(p), update)

    /** Port of the `plans` `allow update` rule in firestore.rules. */
    private object Rules {
        fun apply(before: Map<String, Any?>, update: DocumentUpdate): Map<String, Any?> {
            val after = before.toMutableMap()
            update.fields.forEach { (k, v) -> if (v is FieldValue) after.remove(k) else after[k] = v }
            return after
        }

        fun allows(before: Map<String, Any?>, update: DocumentUpdate, uid: String): Boolean {
            val after = apply(before, update)
            val participant = before["proposerID"] == uid || before["receiverID"] == uid
            return participant && rescheduleUpdateIsValid(before, after, uid)
        }

        private fun changed(before: Map<String, Any?>, after: Map<String, Any?>, keys: List<String>) =
            keys.any { before[it] != after[it] || (it in before) != (it in after) }

        private fun rescheduleUpdateIsValid(before: Map<String, Any?>, after: Map<String, Any?>, uid: String): Boolean {
            val wasCounter = before["status"] == "counter"
            val isCounter = after["status"] == "counter"
            return (!wasCounter && !isCounter && !changed(before, after, listOf("counterProposedBy"))) ||
                (!wasCounter && isCounter && requestIsValid(before, after, uid)) ||
                (wasCounter && isCounter && !changed(before, after, listOf("counterProposedBy", "counterProposedDates", "confirmedDate"))) ||
                (wasCounter && after["status"] == "cancelled") ||
                (wasCounter && after["status"] == "confirmed" && resolutionIsValid(before, after, uid))
        }

        private fun requestIsValid(before: Map<String, Any?>, after: Map<String, Any?>, uid: String): Boolean {
            val dates = after["counterProposedDates"]
            return before["status"] == "confirmed" &&
                after["counterProposedBy"] == uid &&
                dates is List<*> && dates.size == 1 &&
                after["confirmedDate"] == before["confirmedDate"]
        }

        private fun resolutionIsValid(before: Map<String, Any?>, after: Map<String, Any?>, uid: String): Boolean {
            val requester = before["counterProposedBy"]
            val newDate = after["confirmedDate"]
            val originalDate = before["confirmedDate"]
            return (requester == null || requester != uid) &&
                "counterProposedBy" !in after && "counterProposedDates" !in after &&
                newDate != null &&
                (newDate == originalDate ||
                    newDate == (before["counterProposedDates"] as? List<*>)?.firstOrNull() ||
                    (originalDate == null && newDate == (before["proposedDates"] as List<*>).first()))
        }
    }

    // endregion
}
