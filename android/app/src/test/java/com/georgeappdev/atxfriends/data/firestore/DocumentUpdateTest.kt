package com.georgeappdev.atxfriends.data.firestore

import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DocumentUpdateTest {

    @Test
    fun writesOnlyTheListedFields_underIosNames_withDatesAsTimestamps() {
        val at = Instant.ofEpochSecond(1_760_000_000, 5_000)
        val update = DocumentUpdate.Builder()
            .put(PlanFields.STATUS, PlanStatus.COUNTER_PROPOSED)
            .put(PlanFields.UPDATED_AT, at)
            .delete(PlanFields.COUNTER_PROPOSED_BY)
            .build()

        assertEquals(listOf("status", "updatedAt", "counterProposedBy"), update.fields.keys.toList())
        assertEquals("counter", update.fields["status"])
        assertEquals(Timestamp(1_760_000_000, 5_000), update.fields["updatedAt"])
        assertTrue(update.fields["counterProposedBy"] is FieldValue)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToWriteUnknownEnumValues() {
        DocumentUpdate.Builder().put(PlanFields.STATUS, PlanStatus.UNKNOWN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesEmptyUpdates() {
        DocumentUpdate.Builder().build()
    }

    @Test
    fun timestampRoundTripsThroughReader() {
        val at = Instant.ofEpochSecond(1_760_000_000, 999_999_000)
        assertEquals(at, DocReader.toInstant(DocReader.toTimestamp(at)))
    }
}
