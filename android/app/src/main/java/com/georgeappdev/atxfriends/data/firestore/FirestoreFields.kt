package com.georgeappdev.atxfriends.data.firestore

/**
 * Every Firestore collection and field name Android reads or writes, spelled exactly as the
 * iOS app writes them. iOS and Android share one database: never rename a value here to be
 * "more Kotlin". Source of truth: docs/android-parity-spec.md §3 and the iOS encoders.
 */
object Collections {
    const val USERS = "users"
    const val ACTIVITIES = "activities"
    const val MATCHES = "matches"
    const val MESSAGES = "messages"
    const val PLANS = "plans"
    const val TODAY_PLANS = "todayPlans"
    const val GROUP_PLANS = "groupPlans"
    const val SIMPATICO_ANSWERS = "simpaticoAnswers"
    const val SHOW_UP_REPORTS = "showUpReports"
    const val WAITLIST_SIGNUPS = "waitlistSignups"
    const val REPORTS = "reports"
}

/** `users/{uid}` — FirestoreService.userToFirestoreData / firestoreDataToUser. */
object UserFields {
    const val DISPLAY_NAME = "displayName"
    const val BIO = "bio"
    const val PHOTO_URLS = "photoURLs"
    const val ACTIVITY_IDS = "activityIDs"
    const val ACTIVITY_NAMES = "activityNames"
    const val ACTIVITY_IS_PRIMARY = "activityIsPrimary"
    const val DAY_SLOT_COMBOS = "daySlotCombos"
    const val LOCATION = "location"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val RADIUS_MILES = "radiusMiles"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val IS_PROFILE_COMPLETE = "isProfileComplete"
    const val NOTIFICATION_PREFERENCES = "notificationPreferences"
    const val BLOCKED_USERS = "blockedUsers"
    const val LOCATION_SHARING_MODE = "locationSharingMode"
    const val LOCATION_UPDATED_AT = "locationUpdatedAt"
    const val SHOW_UP_THUMBS_UP = "showUpThumbsUp"
    const val SHOW_UP_TOTAL = "showUpTotal"
    const val FCM_TOKENS = "fcmTokens"
    /** Legacy single-token field; iOS folds it into [FCM_TOKENS] once, then deletes it. */
    const val FCM_TOKEN = "fcmToken"
    const val FCM_TOKEN_UPDATED_AT = "fcmTokenUpdatedAt"
    const val UNREAD_COUNT = "unreadCount"
}

/** Keys inside `users/{uid}.notificationPreferences`. */
object NotificationPreferenceFields {
    const val NEW_MATCHES = "newMatches"
    const val NEW_MESSAGES = "newMessages"
    const val PLAN_REQUESTS = "planRequests"
    const val PLAN_CONFIRMATIONS = "planConfirmations"
    const val GROUP_UPDATES = "groupUpdates"
}

/** The `Activity` map embedded in plans, todayPlans and groupPlans (Swift Codable keys). */
object ActivityFields {
    const val ID = "id"
    const val NAME = "name"
    const val IS_USER_ADDED = "isUserAdded"
    const val CREATED_AT = "createdAt"
    const val IS_PRIMARY = "isPrimary"
}

/** `activities/{activityID}` — the shared activity catalog (FirestoreService.addActivity). */
object ActivityDocFields {
    const val NAME = "name"
    const val IS_USER_ADDED = "isUserAdded"
    const val CREATED_AT = "createdAt"
    const val CATEGORY = "category"
    const val NEEDS_REVIEW = "needsReview"
}

/** `matches/{matchID}`. */
object MatchFields {
    const val USER1_ID = "user1ID"
    const val USER2_ID = "user2ID"
    const val USER1_DECISION = "user1Decision"
    const val USER2_DECISION = "user2Decision"
    const val IS_MUTUAL_MATCH = "isMutualMatch"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val OVERLAPPING_ACTIVITY_NAMES = "overlappingActivityNames"
    const val OVERLAPPING_DAY_SLOTS = "overlappingDaySlots"
    const val OVERLAPPING_CATEGORY_NAMES = "overlappingCategoryNames"
    const val IS_BLOCKED = "isBlocked"
}

/** `messages/{messageID}`. */
object MessageFields {
    const val MATCH_ID = "matchID"
    const val EVENT_ID = "eventID"
    const val SENDER_ID = "senderID"
    const val RECEIVER_ID = "receiverID"
    const val TEXT = "text"
    const val SENT_AT = "sentAt"
    const val IS_READ = "isRead"
    const val KIND = "kind"
    const val PLAN_ID = "planID"
}

/** `plans/{planID}` — the 1-on-1 plan proposal. */
object PlanFields {
    const val MATCH_ID = "matchID"
    const val PROPOSER_ID = "proposerID"
    const val RECEIVER_ID = "receiverID"
    const val ACTIVITY = "activity"
    const val LOCATION = "location"
    const val LOCATION_NAME = "locationName"
    const val LOCATION_LATITUDE = "locationLatitude"
    const val LOCATION_LONGITUDE = "locationLongitude"
    const val PROPOSED_DATES = "proposedDates"
    const val STATUS = "status"
    const val CONFIRMED_DATE = "confirmedDate"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val COUNTER_PROPOSED_DATES = "counterProposedDates"
    const val COUNTER_PROPOSED_BY = "counterProposedBy"
    const val IS_VIEWED = "isViewed"
}

/** `todayPlans/{planID}`. */
object TodayPlanFields {
    const val CREATOR_ID = "creatorID"
    const val ACTIVITY = "activity"
    const val SCHEDULED_TIME = "scheduledTime"
    const val NOTE = "note"
    const val LOCATION = "location"
    const val LOCATION_NAME = "locationName"
    const val LOCATION_LATITUDE = "locationLatitude"
    const val LOCATION_LONGITUDE = "locationLongitude"
    const val STATUS = "status"
    const val CLAIMER_ID = "claimerID"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val CREATOR_REPORTED_CLAIMER = "creatorReportedClaimer"
    const val CLAIMER_REPORTED_CREATOR = "claimerReportedCreator"
}

/** `groupPlans/{planID}` — the multi-invitee plan shown on Upcoming. */
object GroupPlanFields {
    const val HOST_ID = "hostID"
    const val INVITEE_IDS = "inviteeIDs"
    const val RESPONSES = "responses"
    const val ACTIVITY = "activity"
    const val LOCATION = "location"
    const val LOCATION_NAME = "locationName"
    const val LOCATION_LATITUDE = "locationLatitude"
    const val LOCATION_LONGITUDE = "locationLongitude"
    const val DATE = "date"
    const val STATUS = "status"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
}

/**
 * `simpaticoAnswers/{uid}` — as SimpaticoService.swift actually writes it. (Spec §3 lists
 * `answers`/`completedAt`; the live code uses `v2Answers`/`v2CompletedAt`, and `answers` is
 * the legacy pre-v2 field that must never be touched.)
 */
object SimpaticoFields {
    const val USER_ID = "userID"
    const val V2_ANSWERS = "v2Answers"
    const val V2_COMPLETED_AT = "v2CompletedAt"
    const val LEGACY_ANSWERS = "answers"

    // Keys inside each v2Answers.{questionID} map.
    const val ANSWER = "answer"
    const val ACCEPTABLE = "acceptable"
    const val IMPORTANCE = "importance"
}

/** `showUpReports/{auto-id}` — write-once queue for the applyShowUpReport Cloud Function. */
object ShowUpReportFields {
    const val REPORTER_ID = "reporterID"
    const val REPORTED_USER_ID = "reportedUserID"
    const val PLAN_ID = "planID"
    const val DID_SHOW_UP = "didShowUp"
    const val CREATED_AT = "createdAt"
}

/**
 * `reports/{auto-id}` — PrivacyAndSafetyViewModel.submitReport. Note the lowercase "Id"
 * (unlike `reportedUserID` in `showUpReports`); the rules accept exactly these five keys.
 */
object ReportFields {
    const val REPORTED_USER_ID = "reportedUserId"
    const val REPORTING_USER_ID = "reportingUserId"
    const val REASON = "reason"
    const val COMMENTS = "comments"
    const val TIMESTAMP = "timestamp"
}

/** `waitlistSignups/{auto-id}` — WaitlistView.submit. Rules accept exactly these two keys. */
object WaitlistFields {
    const val EMAIL = "email"
    const val SUBMITTED_AT = "submittedAt"
}
