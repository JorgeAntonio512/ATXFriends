package com.georgeappdev.atxfriends.domain.today

import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.model.Activity
import com.google.firebase.Timestamp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** TodayPlanService.claimTodayPlan's steps, order, shapes — and two people claiming at once. */
class TodayClaimTest {

    private val now = Instant.ofEpochSecond(1_790_000_000)
    private val start = Instant.ofEpochSecond(1_790_020_000)
    private val activity = Activity("ACT", "Tacos", false, Instant.ofEpochSecond(1_789_000_000), true)
    private fun ts(i: Instant) = Timestamp(i.epochSecond, i.nano)

    /**
     * An in-memory Firestore for one Today plan and the matches/plans collections. Transactions
     * behave like Firestore's optimistic ones: read (remembering the version), then commit only
     * if nobody changed the doc meanwhile — otherwise retry from the read.
     */
    private class FakeStore(var status: String? = "open") : TodayClaimStore {
        var claimerID: String? = null
        private var version = 0
        private val commitLock = Mutex()
        val calls = mutableListOf<String>()
        val matches = mutableMapOf<String, Map<String, Any>>()
        val matchUpdates = mutableListOf<Pair<String, Map<String, Any?>>>()
        val plans = mutableListOf<Map<String, Any>>()
        var failCommit = false

        override suspend fun claimInTransaction(planID: String, check: (String?) -> Unit, claim: DocumentUpdate) {
            calls += "transaction:$planID"
            while (true) {
                val readStatus = status
                val readVersion = version
                yield() // let a concurrent claimer interleave between read and commit
                check(readStatus)
                val committed = commitLock.withLock {
                    if (version != readVersion) return@withLock false
                    status = claim.fields["status"] as String
                    claimerID = claim.fields["claimerID"] as String
                    version++
                    true
                }
                if (committed) return
                calls += "retry:$planID"
            }
        }

        override suspend fun findMatchID(user1ID: String, user2ID: String): String? {
            calls += "findMatch:$user1ID,$user2ID"
            return matches.entries.firstOrNull { it.value["user1ID"] == user1ID && it.value["user2ID"] == user2ID }?.key
        }

        override suspend fun commitMatchAndPlan(match: MatchUpsert, plan: NewDocument) {
            calls += "commit:${match::class.simpleName}:${match.matchID}"
            if (failCommit) throw IOException("offline")
            when (match) {
                is MatchUpsert.Upgrade -> matchUpdates += match.matchID to match.update.fields
                is MatchUpsert.Create -> matches[match.matchID] = match.doc.fields
            }
            plans += plan.fields
        }
    }

    private suspend fun claim(store: FakeStore, claimer: String, creator: String = "creatorZ") =
        TodayClaim.claim(store, "TP1", claimer, creator, activity, start, clock = { now })

    @Test
    fun noExistingMatch_claims_createsMinMaxMatch_writesConfirmedPlan_inIosOrder() = runTest {
        val store = FakeStore()
        val matchID = claim(store, claimer = "claimerA")

        assertEquals("claimerA_creatorZ", matchID)
        assertEquals(
            listOf("transaction:TP1", "findMatch:claimerA,creatorZ", "commit:Create:claimerA_creatorZ"),
            store.calls,
        )
        assertEquals("claimed", store.status)
        assertEquals("claimerA", store.claimerID)
        assertEquals(
            mapOf(
                "user1ID" to "claimerA", "user2ID" to "creatorZ", "user1Decision" to true, "user2Decision" to true,
                "isMutualMatch" to true, "overlappingActivityNames" to listOf("Tacos"), "overlappingDaySlots" to emptyList<String>(),
                "createdAt" to ts(now), "updatedAt" to ts(now),
            ),
            store.matches["claimerA_creatorZ"],
        )
        val plan = store.plans.single()
        assertEquals("claimerA_creatorZ", plan["matchID"])
        assertEquals("claimerA", plan["proposerID"])
        assertEquals("creatorZ", plan["receiverID"])
        assertEquals("confirmed", plan["status"])
        assertEquals(ts(start), plan["confirmedDate"])
        assertEquals(listOf(ts(start)), plan["proposedDates"])
    }

    @Test
    fun user1IsAlwaysTheSmallerUid_whicheverSideClaims() = runTest {
        val store = FakeStore()
        assertEquals("aaa_zzz", claim(store, claimer = "zzz", creator = "aaa"))
        assertTrue(store.calls.contains("findMatch:aaa,zzz"))
        assertEquals("zzz", store.plans.single()["proposerID"])
    }

    @Test
    fun existingMatch_evenALegacyUuidDoc_isUpgradedInPlace() = runTest {
        val store = FakeStore()
        store.matches["LEGACY-UUID"] = mapOf("user1ID" to "claimerA", "user2ID" to "creatorZ")

        val matchID = claim(store, claimer = "claimerA")

        assertEquals("LEGACY-UUID", matchID)
        assertEquals(
            listOf("LEGACY-UUID" to mapOf("user1Decision" to true, "user2Decision" to true, "isMutualMatch" to true, "updatedAt" to ts(now))),
            store.matchUpdates,
        )
        assertEquals("LEGACY-UUID", store.plans.single()["matchID"])
    }

    @Test
    fun alreadyClaimed_failsWithIosMessage_andWritesNothingElse() = runTest {
        for (status in listOf("claimed", null, "somethingNew")) {
            val store = FakeStore(status)
            try {
                claim(store, claimer = "claimerA")
                fail("expected a conflict for status=$status")
            } catch (e: PlanAlreadyClaimedException) {
                assertEquals("This plan is no longer available — someone got there first!", e.message)
            }
            assertEquals(listOf("transaction:TP1"), store.calls)
            assertTrue(store.plans.isEmpty())
        }
    }

    @Test
    fun twoPeopleClaimAtOnce_exactlyOneWins_theOtherGetsTheConflict() = runTest {
        val store = FakeStore()
        val results = listOf("alice", "bob").map { claimer ->
            async { runCatching { claim(store, claimer = claimer, creator = "zed") } }
        }.awaitAll()

        val winners = results.filter { it.isSuccess }
        val losers = results.filter { it.isFailure }
        assertEquals(1, winners.size)
        assertEquals(1, losers.size)
        assertTrue(losers.single().exceptionOrNull() is PlanAlreadyClaimedException)

        // The loser's transaction retried, saw "claimed", and stopped before any other write.
        assertTrue(store.calls.any { it.startsWith("retry:") })
        val winner = store.claimerID!!
        assertEquals("${winner}_zed", winners.single().getOrThrow())
        assertEquals(1, store.plans.size)
        assertEquals(winner, store.plans.single()["proposerID"])
        assertEquals(1, store.matches.size)
    }

    @Test
    fun failureAfterTheClaim_surfacesTheError() = runTest {
        val store = FakeStore().apply { failCommit = true }
        try {
            claim(store, claimer = "claimerA")
            fail("expected the commit error")
        } catch (e: IOException) {
            assertEquals("offline", e.message)
        }
        // As on iOS the claim itself stands; match + plan are all-or-nothing on Android.
        assertEquals("claimed", store.status)
        assertTrue(store.plans.isEmpty())
        assertTrue(store.matches.isEmpty())
    }
}
