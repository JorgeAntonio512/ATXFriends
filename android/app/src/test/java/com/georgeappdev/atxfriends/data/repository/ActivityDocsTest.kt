package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.domain.matching.ActivityCategory
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ActivityDocsTest {

    @Test
    fun newCustomActivity_hasExactlyTheFieldsIosWrites() {
        val doc = ActivityDocs.newCustom("Rock Climbing", ActivityCategory.OUTDOOR_AND_NATURE, Instant.ofEpochSecond(1_770_000_000))
        assertEquals(
            mapOf(
                "name" to "Rock Climbing",
                "isUserAdded" to true,
                "createdAt" to Timestamp(1_770_000_000, 0),
                "category" to "outdoorAndNature",
                "needsReview" to true,
            ),
            doc,
        )
    }

    @Test
    fun decode_needsNameIsUserAddedAndCreatedAt() {
        val good = mapOf("name" to "Hiking", "isUserAdded" to false, "createdAt" to TestDocs.ts(TestDocs.T1))
        assertEquals(CatalogActivity("id1", "Hiking"), ActivityDocs.decode("id1", good))
        assertNull(ActivityDocs.decode("id1", good - "isUserAdded"))
        assertNull(ActivityDocs.decode("id1", good - "createdAt"))
        assertNull(ActivityDocs.decode("id1", good - "name"))
    }
}
