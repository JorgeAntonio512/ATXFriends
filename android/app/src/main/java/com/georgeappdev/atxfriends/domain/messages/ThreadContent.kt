package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.data.model.Message
import com.georgeappdev.atxfriends.data.model.MessageKind
import com.georgeappdev.atxfriends.data.model.Plan
import java.time.LocalDate

/** One entry in the thread's message list. */
sealed interface ThreadItem {
    val key: String

    data class DateHeader(val day: LocalDate, val label: String) : ThreadItem {
        override val key get() = "day-$day"
    }

    data class Bubble(val message: Message) : ThreadItem {
        override val key get() = message.id
    }
}

/**
 * Messages the thread shows (MessageThreadView.visibleMessages): everything except plan
 * proposals whose plan is confirmed, since the pinned card is their home once confirmed.
 */
fun visibleMessages(messages: List<Message>, confirmedPlanIDs: Set<String>): List<Message> =
    messages.filter { m ->
        val planID = m.planID
        m.kind != MessageKind.PLAN_PROPOSAL || planID == null || planID !in confirmedPlanIDs
    }

/**
 * [messages] (oldest first) with a date header before the first message of each local day.
 * iOS shows one "Today" header on top regardless of the dates; Android fixes that.
 */
fun threadItems(messages: List<Message>, dates: MessageDates): List<ThreadItem> {
    val items = mutableListOf<ThreadItem>()
    var currentDay: LocalDate? = null
    for (message in messages) {
        val day = dates.dayOf(message.sentAt)
        if (day != currentDay) {
            items += ThreadItem.DateHeader(day, dates.dateHeader(day))
            currentDay = day
        }
        items += ThreadItem.Bubble(message)
    }
    return items
}

/** What an inline plan proposal card shows (PlanProposalCard's loading / loaded / failed). */
sealed interface ProposalCardState {
    /** Plans haven't arrived yet: the message's summary text plus a spinner. */
    data object Loading : ProposalCardState

    data class Loaded(val plan: Plan) : ProposalCardState

    /** No planID, or the plan couldn't be read: just the message's summary text. */
    data object Failed : ProposalCardState

    companion object {
        fun of(message: Message, plansByID: Map<String, Plan>, plansLoaded: Boolean): ProposalCardState {
            val planID = message.planID ?: return Failed
            if (!plansLoaded) return Loading
            return plansByID[planID]?.let(::Loaded) ?: Failed
        }
    }
}
