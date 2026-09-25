package com.georgeappdev.atxfriends.domain.matching

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.model.Match
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MatchDecisionTest {

    private val now = Instant.ofEpochSecond(1_760_000_000, 250_000_000)

    private fun match(user1: Boolean? = null, user2: Boolean? = null, mutual: Boolean = false): Match =
        Match.fromFirestore("alice_bob", TestDocs.match())!!.copy(
            user1ID = "alice", user2ID = "bob",
            user1Decision = user1, user2Decision = user2, isMutualMatch = mutual,
        )

    @Test
    fun user1SaysYay_writesOnlyUser1Decision() {
        val w = MatchDecision.write(match(), "alice", yay = true, now)
        assertEquals(mapOf("user1Decision" to true, "isMutualMatch" to false, "updatedAt" to Timestamp(1_760_000_000, 250_000_000)), w.update.fields)
        assertFalse(w.completesMutualMatch)
    }

    @Test
    fun user2SaysYay_writesOnlyUser2Decision() {
        val w = MatchDecision.write(match(), "bob", yay = true, now)
        assertEquals(setOf("user2Decision", "isMutualMatch", "updatedAt"), w.update.fields.keys)
        assertEquals(true, w.update.fields["user2Decision"])
        assertEquals(false, w.update.fields["isMutualMatch"])
    }

    @Test
    fun yayCompletingAMutualMatch() {
        val w = MatchDecision.write(match(user1 = true), "bob", yay = true, now)
        assertEquals(true, w.update.fields["isMutualMatch"])
        assertTrue(w.isMutualAfter)
        assertTrue("isMutualMatch goes false → true, which fires onNewMutualMatch", w.completesMutualMatch)
    }

    @Test
    fun yayOnAnAlreadyMutualDoc_doesNotCountAsNew() {
        val w = MatchDecision.write(match(user1 = true, user2 = true, mutual = true), "alice", yay = true, now)
        assertTrue(w.isMutualAfter)
        assertFalse(w.completesMutualMatch)
    }

    @Test
    fun nay_recordsFalse_neverMutual() {
        for (other in listOf(null, true, false)) {
            val w = MatchDecision.write(match(user2 = other), "alice", yay = false, now)
            assertEquals(false, w.update.fields["user1Decision"])
            assertEquals(false, w.update.fields["isMutualMatch"])
            assertFalse(w.completesMutualMatch)
        }
    }

    @Test
    fun yayAfterTheOtherSaidNay_isNotMutual() {
        val w = MatchDecision.write(match(user1 = false), "bob", yay = true, now)
        assertEquals(false, w.update.fields["isMutualMatch"])
    }

    @Test
    fun writtenFieldsAreAlwaysWithinWhatRulesAllow() {
        for (me in listOf("alice", "bob")) for (yay in listOf(true, false)) for (u1 in listOf(null, true, false)) for (u2 in listOf(null, true, false)) {
            val w = MatchDecision.write(match(u1, u2), me, yay, now)
            assertTrue(w.update.fields.keys.toString(), MatchDecision.ALLOWED_FIELDS.containsAll(w.update.fields.keys))
            assertEquals(3, w.update.fields.size)
        }
        assertEquals(setOf("user1Decision", "user2Decision", "isMutualMatch", "updatedAt"), MatchDecision.ALLOWED_FIELDS)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToDecideOnSomeoneElsesMatch() {
        MatchDecision.write(match(), "mallory", yay = true, now)
    }
}
