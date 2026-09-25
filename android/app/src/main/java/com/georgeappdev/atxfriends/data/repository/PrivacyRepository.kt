package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.ActivityFields
import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.DocReader
import com.georgeappdev.atxfriends.data.firestore.MatchFields
import com.georgeappdev.atxfriends.data.firestore.MessageFields
import com.georgeappdev.atxfriends.data.firestore.PlanFields
import com.georgeappdev.atxfriends.domain.export.ExportMessage
import com.georgeappdev.atxfriends.domain.export.ExportPlan
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.time.Instant

/** The people a user can block: "Matches" (pending) and "Current Connections" (mutual). */
data class BlockCandidates(val pendingIDs: Set<String>, val mutualIDs: Set<String>)

/** One mutual match for the export. */
data class ExportMatchRow(val otherUserID: String, val createdAt: Instant?)

/** Reads behind Privacy & Safety (a seam for tests). */
interface PrivacyReader {
    suspend fun blockCandidates(uid: String, blocked: Set<String>): BlockCandidates
    suspend fun exportMatches(uid: String): List<ExportMatchRow>
    suspend fun exportMessages(uid: String): List<ExportMessage>
    suspend fun exportPlans(uid: String): List<ExportPlan>
}

/** The same queries PrivacyAndSafetyViewModel runs. All throw on network/permission failure. */
class PrivacyRepository(private val db: FirebaseFirestore) : PrivacyReader {

    override suspend fun blockCandidates(uid: String, blocked: Set<String>): BlockCandidates =
        BlockCandidateRules.classify(myMatches(uid), uid, blocked)

    override suspend fun exportMatches(uid: String): List<ExportMatchRow> =
        myMatches(uid).filter { DocReader(it).bool(MatchFields.IS_MUTUAL_MATCH) == true }.map { data ->
            val r = DocReader(data)
            val user1 = r.string(MatchFields.USER1_ID).orEmpty()
            val user2 = r.string(MatchFields.USER2_ID).orEmpty()
            ExportMatchRow(if (user1 == uid) user2 else user1, r.instant(MatchFields.CREATED_AT))
        }

    override suspend fun exportMessages(uid: String): List<ExportMessage> =
        db.collection(Collections.MESSAGES)
            .where(Filter.or(Filter.equalTo(MessageFields.SENDER_ID, uid), Filter.equalTo(MessageFields.RECEIVER_ID, uid)))
            .get().await()
            .documents.mapNotNull { doc ->
                val r = DocReader(doc.data ?: return@mapNotNull null)
                ExportMessage(
                    matchID = r.string(MessageFields.MATCH_ID) ?: return@mapNotNull null,
                    senderID = r.string(MessageFields.SENDER_ID) ?: return@mapNotNull null,
                    receiverID = r.string(MessageFields.RECEIVER_ID) ?: return@mapNotNull null,
                    text = r.string(MessageFields.TEXT) ?: return@mapNotNull null,
                    sentAt = r.instant(MessageFields.SENT_AT) ?: return@mapNotNull null,
                )
            }
            .sortedBy { it.sentAt }

    override suspend fun exportPlans(uid: String): List<ExportPlan> =
        db.collection(Collections.PLANS)
            .where(Filter.or(Filter.equalTo(PlanFields.PROPOSER_ID, uid), Filter.equalTo(PlanFields.RECEIVER_ID, uid)))
            .get().await()
            .documents.mapNotNull { doc ->
                val r = DocReader(doc.data ?: return@mapNotNull null)
                ExportPlan(
                    activityName = r.reader(PlanFields.ACTIVITY)?.string(ActivityFields.NAME),
                    location = r.string(PlanFields.LOCATION),
                    status = r.string(PlanFields.STATUS),
                    proposedDates = r.instantList(PlanFields.PROPOSED_DATES).orEmpty(),
                    confirmedDate = r.instant(PlanFields.CONFIRMED_DATE),
                )
            }

    /** Raw match data where the user is user1 or user2 (iOS uses one OR query). */
    private suspend fun myMatches(uid: String): List<Map<String, Any?>> = coroutineScope {
        val matches = db.collection(Collections.MATCHES)
        val asUser1 = async { matches.whereEqualTo(MatchFields.USER1_ID, uid).get().await() }
        val asUser2 = async { matches.whereEqualTo(MatchFields.USER2_ID, uid).get().await() }
        (asUser1.await().documents + asUser2.await().documents).distinctBy { it.id }.mapNotNull { it.data }
    }
}

/** PrivacyAndSafetyViewModel.loadSearchableContacts' sorting of match docs. */
object BlockCandidateRules {
    fun classify(matches: List<Map<String, Any?>>, uid: String, blocked: Set<String>): BlockCandidates {
        val mutual = mutableSetOf<String>()
        val pending = mutableSetOf<String>()
        for (data in matches) {
            val r = DocReader(data)
            val user1 = r.string(MatchFields.USER1_ID) ?: continue
            val user2 = r.string(MatchFields.USER2_ID) ?: continue
            val other = if (user1 == uid) user2 else user1
            if (other in blocked) continue
            when {
                r.bool(MatchFields.IS_MUTUAL_MATCH) == true -> mutual += other
                r.bool(MatchFields.IS_BLOCKED) != true -> pending += other
            }
        }
        return BlockCandidates(pendingIDs = pending, mutualIDs = mutual)
    }
}
