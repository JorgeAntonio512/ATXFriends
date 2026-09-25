package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.ActivityFields
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.GroupPlanFields
import com.georgeappdev.atxfriends.data.firestore.MatchFields
import com.georgeappdev.atxfriends.data.firestore.MessageFields
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.firestore.PlanFields
import com.georgeappdev.atxfriends.data.firestore.TodayPlanFields
import com.georgeappdev.atxfriends.data.model.Activity
import com.georgeappdev.atxfriends.data.model.GroupPlanResponse
import com.georgeappdev.atxfriends.data.model.GroupPlanStatus
import com.georgeappdev.atxfriends.data.model.MessageKind
import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.data.model.TodayPlanStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Where a plan happens, as the "Where?" field produces it. Coordinates only come from a place pick. */
data class PlanPlace(
    /** Free text, trimmed; always non-empty once the composer lets you submit. */
    val location: String,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * Every plan-related document iOS creates or changes, field for field. Each function names the
 * Swift code it mirrors. Nothing here talks to Firestore, so the shapes are unit-tested directly.
 */
object PlanWrites {

    /**
     * The `Activity` map embedded in a plan, as `Firestore.Encoder().encode(activity)` writes it:
     * all five Codable keys, `createdAt` as a Timestamp. `isPrimary` must be present — iOS's
     * decoder requires it, and a plan without it silently disappears from iPhones.
     */
    fun activity(activity: Activity): NewDocument = NewDocument.Builder()
        .put(ActivityFields.ID, activity.id)
        .put(ActivityFields.NAME, activity.name)
        .put(ActivityFields.IS_USER_ADDED, activity.isUserAdded)
        .put(ActivityFields.CREATED_AT, activity.createdAt)
        .put(ActivityFields.IS_PRIMARY, activity.isPrimary)
        .build()

    /**
     * The throwaway Activity every composer mode builds from the typed name:
     * `Activity(id: UUID().uuidString, name:, isUserAdded: false, createdAt: Date())` (isPrimary
     * defaults to true). It's never added to the shared `activities` list.
     */
    fun typedActivity(name: String, now: Instant, id: String = newID()): Activity =
        Activity(id = id, name = name, isUserAdded = false, createdAt = now, isPrimary = true)

    /** Swift `UUID().uuidString`: upper-case. */
    fun newID(): String = UUID.randomUUID().toString().uppercase(Locale.ROOT)

    // ── .proposal ────────────────────────────────────────────────────────────────────────

    /** `plans/{id}` from MessagingViewModel.proposePlan → PlansService.planToFirestoreData. */
    fun proposal(
        matchID: String,
        proposerID: String,
        receiverID: String,
        activity: Activity,
        place: PlanPlace,
        date: Instant,
        now: Instant,
    ): NewDocument = NewDocument.Builder()
        .put(PlanFields.MATCH_ID, matchID)
        .put(PlanFields.PROPOSER_ID, proposerID)
        .put(PlanFields.RECEIVER_ID, receiverID)
        .putMap(PlanFields.ACTIVITY, activity(activity))
        .putInstants(PlanFields.PROPOSED_DATES, listOf(date))
        .put(PlanFields.STATUS, PlanStatus.PENDING)
        .put(PlanFields.CREATED_AT, now)
        .put(PlanFields.UPDATED_AT, now)
        .putPlace(PlanFields.LOCATION, PlanFields.LOCATION_NAME, PlanFields.LOCATION_LATITUDE, PlanFields.LOCATION_LONGITUDE, place)
        .build()

    /**
     * `messages/{id}` from MessagingService.sendPlanProposal → messageToFirestoreData: `kind` is
     * written because it isn't "text"; `eventID` is left out because it's nil.
     */
    fun proposalMessage(
        matchID: String,
        senderID: String,
        receiverID: String,
        planID: String,
        summary: String,
        now: Instant,
    ): NewDocument = NewDocument.Builder()
        .put(MessageFields.SENDER_ID, senderID)
        .put(MessageFields.RECEIVER_ID, receiverID)
        .put(MessageFields.TEXT, summary)
        .put(MessageFields.SENT_AT, now)
        .put(MessageFields.IS_READ, false)
        .put(MessageFields.MATCH_ID, matchID)
        .put(MessageFields.KIND, MessageKind.PLAN_PROPOSAL)
        .put(MessageFields.PLAN_ID, planID)
        .build()

    /**
     * The proposal message text: `"Proposed \(activityName) · \(date)"` with a DateFormatter at
     * `.medium` date / `.short` time, e.g. "Proposed Tacos · Sep 26, 2026 at 7:00 PM".
     */
    fun proposalSummary(activityName: String, date: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", locale)
        return "Proposed $activityName · ${formatter.format(date.atZone(zone))}"
    }

    // ── .openPost ────────────────────────────────────────────────────────────────────────

    /**
     * `todayPlans/{id}` from TodayViewModel.createPlan → TodayPlanService.encode. Status starts
     * "open" and `claimerID` is absent (both required by the create rule); the composer never
     * sets a note, so `note` is absent too.
     */
    fun todayPlan(creatorID: String, activity: Activity, scheduledTime: Instant, place: PlanPlace, now: Instant): NewDocument =
        NewDocument.Builder()
            .put(TodayPlanFields.CREATOR_ID, creatorID)
            .putMap(TodayPlanFields.ACTIVITY, activity(activity))
            .put(TodayPlanFields.SCHEDULED_TIME, scheduledTime)
            .put(TodayPlanFields.STATUS, TodayPlanStatus.OPEN)
            .put(TodayPlanFields.CREATED_AT, now)
            .put(TodayPlanFields.UPDATED_AT, now)
            .putPlace(TodayPlanFields.LOCATION, TodayPlanFields.LOCATION_NAME, TodayPlanFields.LOCATION_LATITUDE, TodayPlanFields.LOCATION_LONGITUDE, place)
            .build()

    // ── .groupInvite ─────────────────────────────────────────────────────────────────────

    /**
     * `groupPlans/{id}` from GroupPlansService.groupPlanToFirestoreData: every invitee's response
     * starts "invited", status "active".
     */
    fun groupPlan(hostID: String, inviteeIDs: List<String>, activity: Activity, date: Instant, place: PlanPlace, now: Instant): NewDocument {
        require(inviteeIDs.isNotEmpty()) { "A group plan needs at least one invitee" }
        val responses = NewDocument.Builder().apply {
            for (id in inviteeIDs) put(checkedUserID(id), GroupPlanResponse.INVITED)
        }.build()
        return NewDocument.Builder()
            .put(GroupPlanFields.HOST_ID, hostID)
            .putStrings(GroupPlanFields.INVITEE_IDS, inviteeIDs)
            .putMap(GroupPlanFields.RESPONSES, responses)
            .putMap(GroupPlanFields.ACTIVITY, activity(activity))
            .put(GroupPlanFields.DATE, date)
            .put(GroupPlanFields.STATUS, GroupPlanStatus.ACTIVE)
            .put(GroupPlanFields.CREATED_AT, now)
            .put(GroupPlanFields.UPDATED_AT, now)
            .putPlace(GroupPlanFields.LOCATION, GroupPlanFields.LOCATION_NAME, GroupPlanFields.LOCATION_LATITUDE, GroupPlanFields.LOCATION_LONGITUDE, place)
            .build()
    }

    /**
     * GroupPlansService.setResponse: only `responses.{uid}` and `updatedAt`, which is exactly
     * what the invitee update rule allows.
     */
    fun groupPlanResponse(userID: String, response: GroupPlanResponse, now: Instant): DocumentUpdate {
        require(response == GroupPlanResponse.GOING || response == GroupPlanResponse.CANT_MAKE) {
            "Invitees can only answer going or cantMake"
        }
        return DocumentUpdate.Builder()
            .put("${GroupPlanFields.RESPONSES}.${checkedUserID(userID)}", response)
            .put(GroupPlanFields.UPDATED_AT, now)
            .build()
    }

    /** GroupPlansService.cancelPlan (host only, enforced by rules). */
    fun groupPlanCancel(now: Instant): DocumentUpdate = DocumentUpdate.Builder()
        .put(GroupPlanFields.STATUS, GroupPlanStatus.CANCELLED)
        .put(GroupPlanFields.UPDATED_AT, now)
        .build()

    // ── Today claim (TodayPlanService.claimTodayPlan) ────────────────────────────────────

    /** Step 1, inside the transaction: exactly the three fields the claim rule allows. */
    fun todayPlanClaim(claimerID: String, now: Instant): DocumentUpdate = DocumentUpdate.Builder()
        .put(TodayPlanFields.STATUS, TodayPlanStatus.CLAIMED)
        .put(TodayPlanFields.CLAIMER_ID, claimerID)
        .put(TodayPlanFields.UPDATED_AT, now)
        .build()

    /** Step 3a: a match already exists for the pair — upgrade it to mutual in place. */
    fun matchUpgradeToMutual(now: Instant): DocumentUpdate = DocumentUpdate.Builder()
        .put(MatchFields.USER1_DECISION, true)
        .put(MatchFields.USER2_DECISION, true)
        .put(MatchFields.IS_MUTUAL_MATCH, true)
        .put(MatchFields.UPDATED_AT, now)
        .build()

    /** Step 3b: no match yet — a new mutual one at `{min}_{max}`. `user1ID` is the smaller UID. */
    fun newMutualMatch(user1ID: String, user2ID: String, activityName: String, now: Instant): NewDocument {
        require(user1ID < user2ID) { "user1ID must be the smaller UID" }
        return NewDocument.Builder()
            .put(MatchFields.USER1_ID, user1ID)
            .put(MatchFields.USER2_ID, user2ID)
            .put(MatchFields.USER1_DECISION, true)
            .put(MatchFields.USER2_DECISION, true)
            .put(MatchFields.IS_MUTUAL_MATCH, true)
            .putStrings(MatchFields.OVERLAPPING_ACTIVITY_NAMES, listOf(activityName))
            .putStrings(MatchFields.OVERLAPPING_DAY_SLOTS, emptyList())
            .put(MatchFields.CREATED_AT, now)
            .put(MatchFields.UPDATED_AT, now)
            .build()
    }

    /** The deterministic match ID shared with MatchingService: `"{min(uid1,uid2)}_{max(uid1,uid2)}"`. */
    fun matchID(uidA: String, uidB: String): String = "${minOf(uidA, uidB)}_${maxOf(uidA, uidB)}"

    /**
     * Step 4: the pre-confirmed Plan. The claimer is the proposer (the plans create rule needs
     * `proposerID == auth.uid`). As on iOS, the Today post's location is **not** copied over.
     */
    fun claimedPlan(matchID: String, claimerID: String, creatorID: String, activity: Activity, scheduledTime: Instant, now: Instant): NewDocument =
        NewDocument.Builder()
            .put(PlanFields.MATCH_ID, matchID)
            .put(PlanFields.PROPOSER_ID, claimerID)
            .put(PlanFields.RECEIVER_ID, creatorID)
            .putMap(PlanFields.ACTIVITY, activity(activity))
            .putInstants(PlanFields.PROPOSED_DATES, listOf(scheduledTime))
            .put(PlanFields.STATUS, PlanStatus.CONFIRMED)
            .put(PlanFields.CONFIRMED_DATE, scheduledTime)
            .put(PlanFields.CREATED_AT, now)
            .put(PlanFields.UPDATED_AT, now)
            .build()

    private fun NewDocument.Builder.putPlace(
        locationField: String,
        nameField: String,
        latitudeField: String,
        longitudeField: String,
        place: PlanPlace,
    ) = apply {
        // The composer never submits a blank location; iOS writes nil (omits it) for blank.
        putIfPresent(locationField, place.location.trim().takeIf { it.isNotEmpty() })
        putIfPresent(nameField, place.locationName)
        putIfPresent(latitudeField, place.latitude)
        putIfPresent(longitudeField, place.longitude)
    }

    /** User IDs become part of a field path (`responses.{uid}`), so only plain IDs are allowed. */
    private fun checkedUserID(id: String): String {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Bad user ID '$id'" }
        return id
    }
}
