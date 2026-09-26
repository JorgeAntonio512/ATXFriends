package com.georgeappdev.atxfriends.ui.settings

import com.georgeappdev.atxfriends.data.firestore.DocReader
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.model.ReportReason
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.BlockCandidates
import com.georgeappdev.atxfriends.data.repository.ExportMatchRow
import com.georgeappdev.atxfriends.data.repository.PrivacyReader
import com.georgeappdev.atxfriends.data.repository.ReportSubmitter
import com.georgeappdev.atxfriends.data.repository.ReportWrites
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.export.ExportMessage
import com.georgeappdev.atxfriends.domain.export.ExportPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ReportUserTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private var me: UserProfile = sampleProfile().copy(blockedUsers = listOf("dan"))

    private fun person(id: String, name: String): UserProfile = sampleProfile().copy(id = id, displayName = name, blockedUsers = emptyList())

    private val people = mapOf(
        "alice" to person("alice", "Alice"),
        "bob" to person("bob", "Bob"),
        "dan" to person("dan", "Dan"),
    )
    private val fetchUser: suspend (String) -> UserDoc = { id ->
        if (id == me.id) UserDoc.Found(me) else people[id]?.let { UserDoc.Found(it) } ?: UserDoc.Missing
    }

    private class FakePrivacy : PrivacyReader {
        var failCandidates = false
        val askedToSkip = mutableListOf<Set<String>>()
        override suspend fun blockCandidates(uid: String, blocked: Set<String>): BlockCandidates {
            if (failCandidates) throw IOException("offline")
            askedToSkip += blocked
            return BlockCandidates(pendingIDs = setOf("bob", "dan") - blocked, mutualIDs = setOf("alice", "ghost") - blocked)
        }
        override suspend fun exportMatches(uid: String) = emptyList<ExportMatchRow>()
        override suspend fun exportMessages(uid: String) = emptyList<ExportMessage>()
        override suspend fun exportPlans(uid: String) = emptyList<ExportPlan>()
    }

    private class FakeReports : ReportSubmitter {
        val sent = mutableListOf<NewDocument>()
        var fail = false
        override suspend fun submitReport(report: NewDocument) {
            if (fail) throw IOException("offline")
            sent += report
        }
    }

    private val privacy = FakePrivacy()
    private val reports = FakeReports()

    private fun viewModel() = ReportUserViewModel({ me }, privacy, reports, fetchUser) { FIXED_NOW }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    // The write.

    @Test
    fun write_isExactlyTheFiveIosFields() {
        val f = ReportWrites.report("uid-sam", "bob", ReportReason.FAKE_PROFILE, "  Not a real person \n", FIXED_NOW).fields
        assertEquals(listOf("reportedUserId", "reportingUserId", "reason", "comments", "timestamp"), f.keys.toList())
        assertEquals("bob", f["reportedUserId"])
        assertEquals("uid-sam", f["reportingUserId"])
        assertEquals("Fake profile", f["reason"])
        assertEquals("Not a real person", f["comments"])
        assertEquals(DocReader.toTimestamp(FIXED_NOW), f["timestamp"])
    }

    @Test
    fun write_withNoComments_sendsAnEmptyString_likeIos() {
        assertEquals("", ReportWrites.report("uid-sam", "bob", ReportReason.SPAM, "", FIXED_NOW).fields["comments"])
    }

    @Test
    fun write_refusesUnknownReason() {
        assertThrows(IllegalArgumentException::class.java) {
            ReportWrites.report("uid-sam", "bob", ReportReason.UNKNOWN, "", FIXED_NOW)
        }
    }

    @Test
    fun choices_areTheFiveIosReasons_inOrder() {
        assertEquals(
            listOf("Inappropriate behavior", "Harassment", "Fake profile", "Spam", "Other"),
            ReportReason.choices.map { it.raw },
        )
    }

    // Who can be reported.

    @Test
    fun list_hasMatchesAndConnections_plusBlockedPeopleInTheirOwnSection() {
        val s = viewModel().state.value
        assertEquals(listOf("Bob"), s.pending.map { it.name })
        assertEquals(listOf("Alice"), s.connections.map { it.name })
        assertEquals(listOf("Dan"), s.blocked.map { it.name })
        assertEquals(listOf(setOf("dan")), privacy.askedToSkip)
    }

    @Test
    fun list_loadFailure_isVisible() {
        privacy.failCandidates = true
        assertTrue(viewModel().state.value.loadFailed)
    }

    // Reason and submit.

    @Test
    fun submit_needsAReason() {
        val vm = viewModel()
        vm.select(PersonRow("bob", "Bob", null))
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        assertTrue(reports.sent.isEmpty())
    }

    @Test
    fun submit_sendsOneReport_showsSuccess_thenLeaves() {
        val vm = viewModel()
        vm.select(PersonRow("bob", "Bob", null))
        vm.setReason(ReportReason.HARASSMENT)
        vm.setComments("Kept messaging after I said no")
        vm.submit()
        vm.submit() // A second tap while the overlay is up sends nothing more.

        assertEquals(1, reports.sent.size)
        val f = reports.sent.single().fields
        assertEquals("bob", f["reportedUserId"])
        assertEquals(me.id, f["reportingUserId"])
        assertEquals("Harassment", f["reason"])
        assertEquals("Kept messaging after I said no", f["comments"])
        assertTrue(vm.state.value.submitted)
        assertFalse(vm.state.value.done)

        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.done)
    }

    @Test
    fun submit_failure_isVisible_andKeepsWhatWasTyped() {
        reports.fail = true
        val vm = viewModel()
        vm.select(PersonRow("bob", "Bob", null))
        vm.setReason(ReportReason.SPAM)
        vm.setComments("Links everywhere")
        vm.submit()
        val s = vm.state.value
        assertTrue(s.submitFailed)
        assertFalse(s.isSubmitting)
        assertFalse(s.submitted)
        assertEquals(ReportReason.SPAM, s.reason)
        assertEquals("Links everywhere", s.comments)
        assertTrue(s.canSubmit)
    }

    @Test
    fun comments_areCappedAtTheRulesLimit() {
        val vm = viewModel()
        vm.select(PersonRow("bob", "Bob", null))
        vm.setComments("x".repeat(ReportWrites.MAX_COMMENT_CHARS + 50))
        assertEquals(ReportWrites.MAX_COMMENT_CHARS, vm.state.value.comments.length)
    }

    @Test
    fun backToPeople_clearsTheSelection_andPickingSomeoneElseStartsFresh() {
        val vm = viewModel()
        vm.select(PersonRow("bob", "Bob", null))
        vm.setReason(ReportReason.SPAM)
        vm.backToPeople()
        assertNull(vm.state.value.selected)
        vm.select(PersonRow("alice", "Alice", null))
        assertNull(vm.state.value.reason)
        assertEquals("", vm.state.value.comments)
    }
}
