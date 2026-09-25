package com.georgeappdev.atxfriends.ui.messages

import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.data.repository.LiveMessages
import com.georgeappdev.atxfriends.data.repository.MessageStore
import com.georgeappdev.atxfriends.data.repository.PlanStore
import com.georgeappdev.atxfriends.data.repository.ShowUpStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** In-memory `messages`: records every write, can fail or hold writes on demand. */
class FakeMessageStore : MessageStore {
    val live = MutableStateFlow(LiveMessages(emptyList()))
    val sent = mutableListOf<NewDocument>()
    val markedRead = mutableListOf<String>()
    var sendError: Exception? = null
    var markReadError: Exception? = null
    /** When set, writes wait for it before finishing. */
    var gate: CompletableDeferred<Unit>? = null

    override fun messagesFor(matchID: String): Flow<LiveMessages> = live

    override suspend fun send(message: NewDocument): String {
        gate?.await()
        sendError?.let { throw it }
        sent += message
        return "new-${sent.size}"
    }

    override suspend fun markRead(messageID: String) {
        gate?.await()
        markReadError?.let { throw it }
        markedRead += messageID
    }
}

/** In-memory `plans`: records every update. */
class FakePlanStore : PlanStore {
    val plans = MutableStateFlow<List<Plan>>(emptyList())
    val updates = mutableListOf<Pair<String, DocumentUpdate>>()
    var updateError: Exception? = null
    var gate: CompletableDeferred<Unit>? = null

    override fun plansFor(matchID: String): Flow<List<Plan>> = plans

    override suspend fun update(planID: String, update: DocumentUpdate) {
        gate?.await()
        updateError?.let { throw it }
        updates += planID to update
    }
}

/** In-memory "Added to Calendar" flags. */
class FakeCalendarStore(val added: MutableSet<String> = mutableSetOf()) : AddedToCalendarStore {
    override fun isAdded(planID: String) = planID in added
    override fun markAdded(planID: String) { added += planID }
    override fun clear(planIDs: Collection<String>) { added -= planIDs.toSet() }
}

/** In-memory show-up reports: a pending plan to return, and every report submitted. */
class FakeShowUpStore(var pending: TodayPlan? = null) : ShowUpStore {
    val submitted = mutableListOf<NewDocument>()
    var submitError: Exception? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun fetchPendingReport(myID: String, otherUserID: String, now: Instant) = pending

    override suspend fun submit(report: NewDocument) {
        gate?.await()
        submitError?.let { throw it }
        submitted += report
    }
}
