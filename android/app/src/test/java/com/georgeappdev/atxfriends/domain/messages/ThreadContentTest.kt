package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.data.model.MessageKind
import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.NOON
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.at
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.dates
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.message
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ThreadContentTest {

    private val d = dates()

    // Date header labels (fixes iOS always showing "Today", spec §11.2)

    @Test
    fun dateHeader_labels() {
        assertEquals("Today", d.dateHeader(LocalDate.of(2026, 9, 24)))
        assertEquals("Yesterday", d.dateHeader(LocalDate.of(2026, 9, 23)))
        assertEquals("Tuesday", d.dateHeader(LocalDate.of(2026, 9, 22)))
        assertEquals("Friday", d.dateHeader(LocalDate.of(2026, 9, 18)))
        assertEquals("Thu, Sep 17", d.dateHeader(LocalDate.of(2026, 9, 17)))
        assertEquals("Mon, Jan 5", d.dateHeader(LocalDate.of(2026, 1, 5)))
        assertEquals("Dec 31, 2025", d.dateHeader(LocalDate.of(2025, 12, 31)))
    }

    @Test
    fun threadItems_oneHeaderPerDay_beforeThatDaysFirstMessage() {
        val messages = listOf(
            message("a", at(day = 22, hour = 9)),
            message("b", at(day = 22, hour = 21)),
            message("c", at(day = 24, hour = 8)),
            message("d", at(day = 24, hour = 11)),
        )
        val labels = threadItems(messages, d).map {
            when (it) {
                is ThreadItem.DateHeader -> "[${it.label}]"
                is ThreadItem.Bubble -> it.message.id
            }
        }
        assertEquals(listOf("[Tuesday]", "a", "b", "[Today]", "c", "d"), labels)
    }

    @Test
    fun threadItems_splitDaysByLocalMidnight_notUtc() {
        // 11:30pm and 12:30am Austin time are different local days (but the same UTC day).
        val messages = listOf(message("late", at(day = 23, hour = 23, minute = 30)), message("early", at(day = 24, hour = 0, minute = 30)))
        val headers = threadItems(messages, d).filterIsInstance<ThreadItem.DateHeader>().map { it.label }
        assertEquals(listOf("Yesterday", "Today"), headers)
    }

    @Test
    fun threadItems_noMessages_noHeader() {
        assertTrue(threadItems(emptyList(), d).isEmpty())
    }

    @Test
    fun threadItems_keysAreUnique() {
        val messages = listOf(message("a", at(day = 20)), message("b", at(day = 21)), message("c", at(day = 21, hour = 13)))
        val keys = threadItems(messages, d).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    // Visible messages (MessageThreadView.visibleMessages)

    @Test
    fun visibleMessages_hidesProposalsForConfirmedPlans() {
        val text = message("text")
        val confirmedProposal = message("confirmed", kind = MessageKind.PLAN_PROPOSAL, planID = "p1")
        val pendingProposal = message("pending", kind = MessageKind.PLAN_PROPOSAL, planID = "p2")
        val visible = visibleMessages(listOf(text, confirmedProposal, pendingProposal), confirmedPlanIDs = setOf("p1"))
        assertEquals(listOf("text", "pending"), visible.map { it.id })
    }

    // Plans listener results (PlansService.listenToConfirmedPlan)

    @Test
    fun confirmedPlans_upcomingSoonestFirst_idsIncludePast() {
        val plans = listOf(
            plan(id = "later", confirmedDate = at(day = 30)),
            plan(id = "past", confirmedDate = at(day = 20)),
            plan(id = "reschedule", status = PlanStatus.COUNTER_PROPOSED, confirmedDate = at(day = 26), counterDate = at(day = 27)),
            plan(id = "pending", status = PlanStatus.PENDING, confirmedDate = null),
            plan(id = "declined", status = PlanStatus.DECLINED, confirmedDate = null),
        )
        val result = ConfirmedPlans.from(plans, NOON)
        assertEquals(listOf("reschedule", "later"), result.upcoming.map { it.id })
        assertEquals(setOf("later", "past", "reschedule"), result.allIDs)
    }

    @Test
    fun rescheduleStaysUpcoming_whenOnlyTheSuggestedTimeIsAhead() {
        val p = plan(status = PlanStatus.COUNTER_PROPOSED, confirmedDate = at(day = 23), counterDate = at(day = 27))
        assertTrue(p.isUpcoming(NOON))
        assertFalse(p.copy(counterProposedDates = null).isUpcoming(NOON))
    }

    @Test
    fun canRespondToReschedule_onlyTheOtherPerson() {
        val p = plan(status = PlanStatus.COUNTER_PROPOSED, counterProposedBy = "sam")
        assertTrue(p.canRespondToReschedule("me"))
        assertFalse(p.canRespondToReschedule("sam"))
        assertTrue("legacy requests: either person", p.copy(counterProposedBy = null).canRespondToReschedule("sam"))
        assertFalse("not a participant", p.canRespondToReschedule("stranger"))
        assertFalse("not a reschedule", p.copy(status = PlanStatus.CONFIRMED).canRespondToReschedule("me"))
    }

    // Inline proposal card state

    @Test
    fun proposalCardState() {
        val proposal = message(kind = MessageKind.PLAN_PROPOSAL, planID = "p1")
        val p1 = plan(id = "p1", status = PlanStatus.PENDING)
        assertEquals(ProposalCardState.Loading, ProposalCardState.of(proposal, emptyMap(), plansLoaded = false))
        assertEquals(ProposalCardState.Loaded(p1), ProposalCardState.of(proposal, mapOf("p1" to p1), plansLoaded = true))
        assertEquals(ProposalCardState.Failed, ProposalCardState.of(proposal, emptyMap(), plansLoaded = true))
        assertEquals(ProposalCardState.Failed, ProposalCardState.of(proposal.copy(planID = null), mapOf("p1" to p1), plansLoaded = false))
    }
}
