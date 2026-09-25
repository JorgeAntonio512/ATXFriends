package com.georgeappdev.atxfriends.ui.settings

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.BlockCandidateRules
import com.georgeappdev.atxfriends.data.repository.BlockCandidates
import com.georgeappdev.atxfriends.data.repository.ExportMatchRow
import com.georgeappdev.atxfriends.data.repository.PrivacyReader
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.export.DataExport
import com.georgeappdev.atxfriends.domain.export.ExportInput
import com.georgeappdev.atxfriends.domain.export.ExportMatch
import com.georgeappdev.atxfriends.domain.export.ExportMessage
import com.georgeappdev.atxfriends.domain.export.ExportPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyTest {

    private val session = TestSession()
    private val writer = FakeProfileWriter()
    private val edits = ProfileEdits(session.manager, writer) { FIXED_NOW }

    private fun person(id: String, name: String): UserProfile = sampleProfile().copy(id = id, displayName = name, blockedUsers = emptyList())

    private val people = mutableMapOf(
        "uid-sam" to session.profile,
        "alice" to person("alice", "Alice"),
        "bob" to person("bob", "Bob"),
        "cara" to person("cara", "Cara"),
    )
    private val fetchUser: suspend (String) -> UserDoc = { id -> people[id]?.let { UserDoc.Found(it) } ?: UserDoc.Missing }

    private class FakePrivacy : PrivacyReader {
        var candidates = BlockCandidates(pendingIDs = setOf("bob", "ghost"), mutualIDs = setOf("cara", "alice"))
        var failCandidates = false
        var failMessages = false
        override suspend fun blockCandidates(uid: String, blocked: Set<String>): BlockCandidates {
            if (failCandidates) throw IOException("offline")
            return BlockCandidates(candidates.pendingIDs - blocked, candidates.mutualIDs - blocked)
        }
        override suspend fun exportMatches(uid: String) = listOf(ExportMatchRow("alice", TestDocs.T1), ExportMatchRow("ghost", TestDocs.T1))
        override suspend fun exportMessages(uid: String): List<ExportMessage> {
            if (failMessages) throw IOException("offline")
            return listOf(
                ExportMessage("m1", "alice", "uid-sam", "Hike Saturday?", TestDocs.T1),
                ExportMessage("m1", "uid-sam", "alice", "Yes!", TestDocs.T2),
            )
        }
        override suspend fun exportPlans(uid: String) = listOf(ExportPlan("Hiking", "Barton Creek", "confirmed", listOf(TestDocs.T1), TestDocs.T2))
    }

    private val privacy = FakePrivacy()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    // Who can be blocked.

    @Test
    fun candidates_mutualAreConnections_pendingAreMatches_blockedAndIsBlockedAreSkipped() {
        fun m(u1: String, u2: String, mutual: Boolean, blocked: Boolean? = null) =
            buildMap<String, Any?> {
                put("user1ID", u1); put("user2ID", u2); put("isMutualMatch", mutual)
                if (blocked != null) put("isBlocked", blocked)
            }
        val result = BlockCandidateRules.classify(
            listOf(
                m("me", "alice", mutual = true),
                m("bob", "me", mutual = false),
                m("me", "cara", mutual = false, blocked = true),
                m("me", "dan", mutual = true),
                mapOf("user1ID" to "me"),
            ),
            uid = "me",
            blocked = setOf("dan"),
        )
        assertEquals(setOf("alice"), result.mutualIDs)
        assertEquals(setOf("bob"), result.pendingIDs)
    }

    // Block a User.

    @Test
    fun blockList_loadsSortedPeople_skippingUnreadableProfiles() {
        val s = BlockUserViewModel(edits, privacy, fetchUser).state.value
        assertEquals(listOf("Bob"), s.pending.map { it.name })
        assertEquals(listOf("Alice", "Cara"), s.connections.map { it.name })
    }

    @Test
    fun block_writesOnlyBlockedUsersArrayUnion_andUpdatesTheSession() {
        val vm = BlockUserViewModel(edits, privacy, fetchUser)
        vm.block(PersonRow("bob", "Bob", null))
        assertEquals(setOf("blockedUsers"), writer.lastFields.keys)
        assertEquals(listOf("bob"), fieldValueElements(writer.lastFields["blockedUsers"]))
        assertTrue("bob" in session.profile.blockedUsers)
        assertEquals("Bob", vm.state.value.blockedName)
        assertTrue(vm.state.value.pending.isEmpty())
    }

    @Test
    fun block_failure_isVisible_andNothingChanges() {
        writer.failAll = true
        val vm = BlockUserViewModel(edits, privacy, fetchUser)
        vm.block(PersonRow("bob", "Bob", null))
        assertTrue(vm.state.value.blockFailed)
        assertFalse(vm.state.value.isBlocking)
        assertEquals(listOf("Bob"), vm.state.value.pending.map { it.name })
        assertFalse("bob" in session.profile.blockedUsers)
    }

    @Test
    fun blockList_loadFailure_isVisible() {
        privacy.failCandidates = true
        assertTrue(BlockUserViewModel(edits, privacy, fetchUser).state.value.loadFailed)
    }

    // Blocked Users.

    @Test
    fun blockedList_showsBlockedUsers_andUnblockIsArrayRemove() {
        people["uid-sam"] = session.profile.copy(blockedUsers = listOf("cara", "alice"))
        session.manager.profileChanged(people["uid-sam"]!!)
        val vm = BlockedUsersViewModel(edits, fetchUser)
        assertEquals(listOf("Alice", "Cara"), vm.state.value.blocked.map { it.name })
        vm.unblock(PersonRow("cara", "Cara", null))
        assertEquals(setOf("blockedUsers"), writer.lastFields.keys)
        assertTrue(writer.lastFields["blockedUsers"]!!.javaClass.simpleName.contains("Remove"))
        assertEquals(listOf("cara"), fieldValueElements(writer.lastFields["blockedUsers"]))
        assertEquals(listOf("alice"), session.profile.blockedUsers)
        assertEquals(listOf("Alice"), vm.state.value.blocked.map { it.name })
    }

    @Test
    fun unblock_failure_isVisible() {
        people["uid-sam"] = session.profile.copy(blockedUsers = listOf("cara"))
        writer.failAll = true
        val vm = BlockedUsersViewModel(edits, fetchUser)
        vm.unblock(PersonRow("cara", "Cara", null))
        assertTrue(vm.state.value.unblockFailed)
        assertEquals(1, vm.state.value.blocked.size)
    }

    // Export My Data.

    private val zone = ZoneId.of("America/Chicago")

    @Test
    fun exportText_matchesTheIosLayout() {
        val text = DataExport.build(
            ExportInput(
                uid = "uid-sam",
                profile = session.profile,
                matches = listOf(ExportMatch("Alice", TestDocs.T1), ExportMatch(null, TestDocs.T1)),
                messages = listOf(
                    ExportMessage("m1", "uid-sam", "alice", "Yes!", TestDocs.T2),
                    ExportMessage("m1", "alice", "uid-sam", "Hike Saturday?", TestDocs.T1),
                ),
                plans = listOf(ExportPlan(null, null, null, emptyList(), null)),
                names = mapOf("alice" to "Alice"),
            ),
            now = Instant.parse("2026-09-25T20:04:00Z"),
            zone = zone,
            locale = Locale.US,
        )
        val expected = """
            ATX FRIENDS DATA EXPORT
            =======================
            Export Date: 9/25/2026, 3:04 PM

            PROFILE INFORMATION
            -------------------
            Name: Sam
            Account Created: 10/9/2025, 3:53 AM

            PREFERENCES
            -----------
            Search Radius: 10.0 miles

            ACTIVITIES
            ----------
            - Hiking
            - Board Games
            - Tacos
            - Yoga

            AVAILABILITY
            ------------
            - Monday Night
            - Saturday Wake Up
            - Sunday Owl Hours

            PHOTOS
            ------
            Photo 1: https://a/0.jpg
            Photo 2: https://a/1.jpg
            Photo 3: https://a/2.jpg

            MATCHES
            -------
            Match with Alice on 10/9/2025, 3:53 AM

            MESSAGES
            --------

            Conversation with Alice (Match ID: m1):
            [10/9/2025, 3:53 AM] Alice: Hike Saturday?
            [10/10/2025, 3:53 AM] You: Yes!

            PLANS
            -----
            Activity: Unknown Activity
            Proposed Dates:
            Location: No location
            Status: unknown
            Confirmed Date: Not confirmed


        """.trimIndent().replace("Proposed Dates:\n", "Proposed Dates: \n") // iOS joins an empty list after "Dates: "
        assertEquals(expected, text)
    }

    @Test
    fun exportText_emptyAndErrorSections() {
        val text = DataExport.build(
            ExportInput("uid-sam", session.profile.copy(activities = emptyList(), photoURLs = emptyList()), emptyList(), null, emptyList(), emptyMap()),
            Instant.EPOCH, zone, Locale.US,
        )
        assertTrue(text.contains("No activities added\n\n"))
        assertTrue(text.contains("No photos uploaded\n\n"))
        assertTrue(text.contains("No mutual matches yet\n\n"))
        assertTrue(text.contains("Error loading messages\n\n"))
        assertTrue(text.endsWith("No plans yet\n\n"))
    }

    @Test
    fun exportViewModel_buildsTheText_andAFailedSectionDoesntFailTheExport() {
        privacy.failMessages = true
        val vm = ExportDataViewModel({ "uid-sam" }, privacy, fetchUser, { FIXED_NOW }, { zone })
        vm.export()
        val text = vm.state.value.readyText!!
        assertTrue(text.contains("Match with Alice on"))
        assertFalse("unreadable other profile skipped", text.contains("ghost"))
        assertTrue(text.contains("Error loading messages"))
        assertTrue(text.contains("Activity: Hiking\n"))
        assertTrue(text.contains("Location: Barton Creek\n"))
        vm.shareHandled()
        assertNull(vm.state.value.readyText)
        assertTrue("export makes no writes", writer.writes.isEmpty())
    }

    @Test
    fun exportViewModel_unreadableProfile_showsTheError() {
        people.remove("uid-sam")
        val vm = ExportDataViewModel({ "uid-sam" }, privacy, fetchUser, { FIXED_NOW }, { zone })
        vm.export()
        assertTrue(vm.state.value.failed)
        assertNull(vm.state.value.readyText)
    }

    @Test
    fun exportViewModel_blocksDoubleTaps() {
        Dispatchers.setMain(StandardTestDispatcher())
        val vm = ExportDataViewModel({ "uid-sam" }, privacy, fetchUser, { FIXED_NOW }, { zone })
        vm.export()
        assertTrue(vm.state.value.isExporting)
        vm.export()
        assertTrue(vm.state.value.isExporting)
    }
}
