import * as functions from "firebase-functions/v1";
import * as admin from "firebase-admin";
import { FieldPath, FieldValue } from "firebase-admin/firestore";

admin.initializeApp();

const db = admin.firestore();

// ─── Shared plan-status constants ─────────────────────────────────────────────
//
// PlanStatus.counterProposed's stored rawValue is "counter" (see Plan.swift),
// NOT "counterProposed". Existing Firestore data uses "counter" — never change
// the stored value, only compare against this constant instead of a literal.
const PLAN_STATUS_COUNTER_PROPOSED = "counter";
const PLAN_STATUS_CONFIRMED = "confirmed";

// Message.kind's stored rawValue for a plan-proposal message (see MessageKind
// in MessageModels.swift). Absent on plain-text messages.
const MESSAGE_KIND_PLAN_PROPOSAL = "planProposal";

// FCM error codes that mean "this token will never work again" — safe to drop
// from fcmTokens the moment we see them.
const DEAD_TOKEN_ERROR_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

// Android notification channel for each push type (android/.../push/PushTypes.kt). One channel
// per notification preference, so Android users can turn each kind off in system settings.
// Unknown types go to "other". iOS ignores the android block.
const ANDROID_CHANNELS: { [type: string]: string } = {
  newMatch: "new_matches",
  newMessage: "messages",
  planRequest: "plan_requests",
  planRescheduleRequested: "plan_requests",
  planConfirmed: "plan_confirmations",
  planRescheduleDeclined: "plan_confirmations",
};

// ─── Helper: get user data ────────────────────────────────────────────────────

async function getUserData(userId: string) {
  const doc = await db.collection("users").doc(userId).get();
  if (!doc.exists) return null;
  const data = doc.data()!;

  // Token migration transition: prefer the new fcmTokens array, but fold in
  // the legacy single fcmToken field (if a client hasn't relaunched since the
  // migration yet) so that user keeps getting pushes until they do.
  const tokensArray = (data.fcmTokens as string[]) ?? [];
  const legacyToken = (data.fcmToken as string) ?? null;
  const tokens = legacyToken && !tokensArray.includes(legacyToken)
    ? [...tokensArray, legacyToken]
    : tokensArray;

  return {
    fcmTokens: tokens,
    blockedUsers: (data.blockedUsers as string[]) ?? [],
    displayName: (data.displayName as string) ?? "Someone",
    prefs: (data.notificationPreferences as { [key: string]: boolean }) ?? {},
    unreadCount: (data.unreadCount as number) ?? 0,
  };
}

// ─── Helper: increment unread count and send notification to every device ─────
//
// Sends to every token in `tokens`. If FCM reports a token as dead
// (registration-token-not-registered / invalid-registration-token), it's
// removed from fcmTokens immediately. The unreadCount increment is rolled
// back only if every token failed — a partial failure (e.g. one stale device
// token alongside one live one) should still leave the badge count correct.
async function sendNotification(
  userId: string,
  tokens: string[],
  title: string,
  body: string,
  data?: { [key: string]: string }
): Promise<void> {
  if (tokens.length === 0) {
    console.log(`⚠️ Skipped: ${title} — user ${userId} has no registered device tokens`);
    return;
  }

  const userRef = db.collection("users").doc(userId);
  await userRef.update({
    unreadCount: FieldValue.increment(1),
  });

  const updatedDoc = await userRef.get();
  const newCount = (updatedDoc.data()?.unreadCount as number) ?? 1;

  const results = await Promise.allSettled(
    tokens.map((token) =>
      admin.messaging().send({
        token,
        notification: { title, body },
        data: data ?? {},
        apns: {
          payload: { aps: { sound: "default", badge: newCount } },
        },
        android: {
          priority: "high",
          notification: { channelId: ANDROID_CHANNELS[data?.type ?? ""] ?? "other" },
        },
      })
    )
  );

  let anySucceeded = false;
  const deadTokens: string[] = [];

  results.forEach((result, i) => {
    const token = tokens[i];
    if (result.status === "fulfilled") {
      anySucceeded = true;
      console.log(`✅ Sent: ${title} (badge: ${newCount})`);
      return;
    }
    const error = result.reason as { code?: string; errorInfo?: { code?: string } };
    const code = error?.errorInfo?.code ?? error?.code ?? "unknown";
    console.error(`❌ Failed to send to a token for user ${userId}: ${code}`);
    if (DEAD_TOKEN_ERROR_CODES.has(code)) {
      deadTokens.push(token);
    }
  });

  if (deadTokens.length > 0) {
    // arrayRemove only strips exact matches, so this is safe even though
    // `tokens` may also include the legacy single-token fallback folded in
    // by getUserData — removing it from fcmTokens is a harmless no-op if it
    // was never actually in that array.
    await userRef.update({
      fcmTokens: FieldValue.arrayRemove(...deadTokens),
    });
    console.log(`🧹 Removed ${deadTokens.length} dead token(s) for user ${userId}`);
  }

  if (!anySucceeded) {
    await userRef.update({
      unreadCount: FieldValue.increment(-1),
    });
  }
}

/// Returns true if `recipientBlockedUsers` (the notification recipient's
/// blockedUsers array) contains `senderId` — i.e. the recipient has blocked
/// the person whose action would otherwise trigger this push.
function isBlocked(recipientBlockedUsers: string[], senderId: string): boolean {
  return recipientBlockedUsers.includes(senderId);
}

// ─── 1. New Mutual Match ──────────────────────────────────────────────────────

export const onNewMutualMatch = functions.firestore
  .document("matches/{matchId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();

    if (before.isMutualMatch === true || after.isMutualMatch !== true) return;

    const user1ID = after.user1ID as string;
    const user2ID = after.user2ID as string;

    for (const [userId, otherUserId] of [[user1ID, user2ID], [user2ID, user1ID]]) {
      const userData = await getUserData(userId);
      if (!userData?.fcmTokens.length) continue;
      if (userData.prefs.newMatches === false) continue;
      if (isBlocked(userData.blockedUsers, otherUserId)) continue;

      const otherData = await getUserData(otherUserId);
      await sendNotification(
        userId,
        userData.fcmTokens,
        "New Match! 🎉",
        `You and ${otherData?.displayName ?? "Someone"} both said Yay!`,
        { type: "newMatch", matchID: context.params.matchId, otherUserId }
      );
    }
  });

// ─── 2. New Message ───────────────────────────────────────────────────────────

export const onNewMessage = functions.firestore
  .document("messages/{messageId}")
  .onCreate(async (snap, context) => {
    const message = snap.data();
    const senderId = message.senderID as string;
    const receiverId = message.receiverID as string;
    const text = message.text as string;
    const matchID = (message.matchID as string) ?? "";

    // planProposal messages are the chat-thread echo of a Plan document that
    // already triggered onNewPlanRequest — skip here so the receiver isn't
    // pushed twice for the same proposal.
    if (message.kind === MESSAGE_KIND_PLAN_PROPOSAL) return;

    const receiverData = await getUserData(receiverId);
    if (!receiverData?.fcmTokens.length) return;
    if (receiverData.prefs.newMessages === false) return;
    if (isBlocked(receiverData.blockedUsers, senderId)) return;

    const senderData = await getUserData(senderId);
    const preview = text.length > 60 ? text.substring(0, 60) + "…" : text;

    await sendNotification(
      receiverId,
      receiverData.fcmTokens,
      senderData?.displayName ?? "Someone",
      preview,
      { type: "newMessage", messageId: context.params.messageId, senderId, matchID }
    );
  });

// ─── 3. New Plan Request ──────────────────────────────────────────────────────

export const onNewPlanRequest = functions.firestore
  .document("plans/{planId}")
  .onCreate(async (snap, context) => {
    const plan = snap.data();
    const proposerId = plan.proposerID as string;
    const receiverId = plan.receiverID as string;
    const matchID = (plan.matchID as string) ?? "";

    const receiverData = await getUserData(receiverId);
    if (!receiverData?.fcmTokens.length) return;
    if (receiverData.prefs.planRequests === false) return;
    if (isBlocked(receiverData.blockedUsers, proposerId)) return;

    const proposerData = await getUserData(proposerId);
    const activityName = (plan.activity?.name as string) ?? "a hangout";

    await sendNotification(
      receiverId,
      receiverData.fcmTokens,
      "New Plan Request! 📅",
      `${proposerData?.displayName ?? "Someone"} wants to ${activityName} with you!`,
      { type: "planRequest", planId: context.params.planId, proposerId, matchID }
    );
  });

// ─── 5. Apply Show-Up Report ─────────────────────────────────────────────────

export const applyShowUpReport = functions.firestore
  .document("showUpReports/{reportId}")
  .onCreate(async (snap, _context) => {
    const data = snap.data();
    const { reporterID, reportedUserID, planID, didShowUp } = data;

    if (!reporterID || !reportedUserID || !planID || typeof didShowUp !== "boolean") {
      console.error("applyShowUpReport: missing or invalid fields", data);
      return;
    }

    const planRef = db.collection("todayPlans").doc(planID as string);
    const planDoc = await planRef.get();

    if (!planDoc.exists) {
      console.error(`applyShowUpReport: plan not found: ${planID}`);
      return;
    }

    const plan = planDoc.data()!;
    const creatorID = plan.creatorID as string;
    const claimerID = plan.claimerID as string | undefined;
    const status = plan.status as string;
    const scheduledTime = plan.scheduledTime as admin.firestore.Timestamp;

    if (status !== "claimed") {
      console.error(`applyShowUpReport: plan not in claimed state (status=${status})`);
      return;
    }

    if (scheduledTime.toDate() > new Date()) {
      console.error(`applyShowUpReport: plan has not yet expired`);
      return;
    }

    const isCreator = reporterID === creatorID;
    const isClaimer = reporterID === claimerID;

    if (!isCreator && !isClaimer) {
      console.error(`applyShowUpReport: reporter ${reporterID} is not a participant in plan ${planID}`);
      return;
    }

    const expectedReportedID = isCreator ? claimerID : creatorID;
    if (reportedUserID !== expectedReportedID) {
      console.error(`applyShowUpReport: wrong reportedUserID ${reportedUserID}, expected ${expectedReportedID}`);
      return;
    }

    const reportField = isCreator ? "creatorReportedClaimer" : "claimerReportedCreator";
    if (plan[reportField] !== undefined && plan[reportField] !== null) {
      console.warn(`applyShowUpReport: duplicate — ${reportField} already set, ignoring`);
      return;
    }

    const batch = db.batch();

    batch.update(planRef, {
      [reportField]: didShowUp,
      updatedAt: FieldValue.serverTimestamp(),
    });

    const userRef = db.collection("users").doc(reportedUserID as string);
    const userUpdate: Record<string, FieldValue> = {
      showUpTotal: FieldValue.increment(1),
    };
    if (didShowUp === true) {
      userUpdate.showUpThumbsUp = FieldValue.increment(1);
    }
    batch.update(userRef, userUpdate);

    await batch.commit();
    console.log(`✅ applyShowUpReport: ${didShowUp ? "👍" : "👎"} from ${reporterID} about ${reportedUserID} (plan ${planID})`);
  });

// ─── 4. Plan Confirmed ────────────────────────────────────────────────────────
//
// Fires on any change into "confirmed". Three cases:
//   - pending -> confirmed: the receiver accepted the proposal; notify the proposer.
//   - counter -> confirmed with counterProposedBy set: the other person answered a
//     reschedule request; notify the requester. If confirmedDate changed they accepted
//     ("Plan Confirmed!" at the new time), otherwise they declined and the plan stays
//     at its original time.
//   - counter -> confirmed with no counterProposedBy (legacy plans from before that
//     field existed): keep the old inference — the proposer confirmed, notify the receiver.

export const onPlanConfirmed = functions.firestore
  .document("plans/{planId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();

    if (before.status === PLAN_STATUS_CONFIRMED || after.status !== PLAN_STATUS_CONFIRMED) return;

    const proposerId = after.proposerID as string;
    const receiverId = after.receiverID as string;
    const activityName = (after.activity?.name as string) ?? "your hangout";
    const matchID = (after.matchID as string) ?? "";
    const planId = context.params.planId as string;

    const requesterId = before.status === PLAN_STATUS_COUNTER_PROPOSED
      ? (before.counterProposedBy as string | undefined)
      : undefined;

    if (requesterId === proposerId || requesterId === receiverId) {
      const responderId = requesterId === proposerId ? receiverId : proposerId;
      const originalDate = before.confirmedDate as admin.firestore.Timestamp | undefined;
      const newDate = after.confirmedDate as admin.firestore.Timestamp | undefined;
      const dateChanged = !(originalDate && newDate && originalDate.isEqual(newDate));

      const requesterData = await getUserData(requesterId);
      if (!requesterData?.fcmTokens.length) return;
      if (requesterData.prefs.planConfirmations === false) return;
      if (isBlocked(requesterData.blockedUsers, responderId)) return;

      const responderData = await getUserData(responderId);
      const responderName = responderData?.displayName ?? "Someone";

      if (dateChanged) {
        const when = newDate ? ` for ${formatPlanTime(newDate)}` : "";
        await sendNotification(
          requesterId,
          requesterData.fcmTokens,
          "Plan Confirmed! ✅",
          `${responderName} confirmed your ${activityName} plan${when}. It's on!`,
          { type: "planConfirmed", planId, matchID }
        );
      } else {
        const when = originalDate ? formatPlanTime(originalDate) : "the original time";
        await sendNotification(
          requesterId,
          requesterData.fcmTokens,
          "New Time Declined",
          `${responderName} can't make the new time — your plan stays on ${when}.`,
          { type: "planRescheduleDeclined", planId, matchID }
        );
      }
      return;
    }

    // No auth context on a Firestore trigger, so infer who just confirmed from
    // the prior status: "pending" -> receiver accepted the proposer's dates;
    // legacy PLAN_STATUS_COUNTER_PROPOSED ("counter") with no counterProposedBy ->
    // proposer accepted the receiver's counter-dates.
    const confirmerId = before.status === PLAN_STATUS_COUNTER_PROPOSED ? proposerId : receiverId;
    const notifyId = confirmerId === proposerId ? receiverId : proposerId;

    const notifyData = await getUserData(notifyId);
    if (!notifyData?.fcmTokens.length) return;
    if (notifyData.prefs.planConfirmations === false) return;
    if (isBlocked(notifyData.blockedUsers, confirmerId)) return;

    const confirmerData = await getUserData(confirmerId);

    await sendNotification(
      notifyId,
      notifyData.fcmTokens,
      "Plan Confirmed! ✅",
      `${confirmerData?.displayName ?? "Someone"} confirmed your ${activityName} plan. It's on!`,
      { type: "planConfirmed", planId, matchID }
    );
  });

// ─── 6. Plan Reschedule Requested ─────────────────────────────────────────────
//
// Fires when a confirmed plan moves to "counter": someone suggested a new time.
// counterProposedBy is the requester; the other participant is notified.
// Gated by the same preference as new plan requests.

export const onPlanRescheduleRequested = functions.firestore
  .document("plans/{planId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();

    if (before.status === PLAN_STATUS_COUNTER_PROPOSED || after.status !== PLAN_STATUS_COUNTER_PROPOSED) return;

    const proposerId = after.proposerID as string;
    const receiverId = after.receiverID as string;
    const requesterId = after.counterProposedBy as string | undefined;
    if (requesterId !== proposerId && requesterId !== receiverId) {
      console.log(`⚠️ Skipped reschedule push for plan ${context.params.planId}: no valid counterProposedBy`);
      return;
    }

    const notifyId = requesterId === proposerId ? receiverId : proposerId;
    const activityName = (after.activity?.name as string) ?? "your hangout";
    const matchID = (after.matchID as string) ?? "";

    const notifyData = await getUserData(notifyId);
    if (!notifyData?.fcmTokens.length) return;
    if (notifyData.prefs.planRequests === false) return;
    if (isBlocked(notifyData.blockedUsers, requesterId)) return;

    const requesterData = await getUserData(requesterId);

    await sendNotification(
      notifyId,
      notifyData.fcmTokens,
      "New Time Suggested 🗓️",
      `${requesterData?.displayName ?? "Someone"} suggested a new time for ${activityName}.`,
      { type: "planRescheduleRequested", planId: context.params.planId, matchID }
    );
  });

// ─── Helper: format a plan time for push copy ─────────────────────────────────
//
// Cloud Functions run in UTC; the app is Austin-only at launch, so render plan
// times in Central time, e.g. "Sunday, Oct 5 at 3:00 PM".
function formatPlanTime(ts: admin.firestore.Timestamp): string {
  const date = ts.toDate();
  const day = date.toLocaleDateString("en-US", {
    timeZone: "America/Chicago", weekday: "long", month: "short", day: "numeric",
  });
  const time = date.toLocaleTimeString("en-US", {
    timeZone: "America/Chicago", hour: "numeric", minute: "2-digit",
  });
  return `${day} at ${time}`;
}

// ─── 7. Delete My Account (callable) ─────────────────────────────────────────
//
// Immediate, permanent deletion of the caller's own account. The caller is
// identified only by context.auth.uid — no user ID is accepted as input.
// Deletes everything the user created, including both sides of their message
// threads, then deletes the Firebase Auth user last. Safe to retry: every step
// tolerates data that's already gone. Returns the IDs of plans the user was
// part of so the client can clear its per-plan "added to calendar" flags.

export const deleteMyAccount = functions.https.onCall(async (_data, context) => {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "You must be signed in to delete your account.");
  }

  const summary = await deleteAccountData(uid);

  try {
    await admin.auth().deleteUser(uid);
    summary.authUser = 1;
  } catch (error) {
    const code = (error as { code?: string })?.code;
    if (code !== "auth/user-not-found") throw error;
  }

  const { planIDs, ...counts } = summary;
  console.log(`🗑️ deleteMyAccount: deleted account data ${JSON.stringify(counts)}`);
  return { planIDs };
});

type DeleteAccountSummary = {
  planIDs: string[];
  [key: string]: number | string[];
};

/// Deletes all Firestore and Storage data belonging to `uid`. Throws if any
/// write failed, so the caller can retry; already-deleted data is skipped.
async function deleteAccountData(uid: string): Promise<DeleteAccountSummary> {
  const idsWhere = async (collection: string, field: string, op: FirebaseFirestore.WhereFilterOp = "==") =>
    (await db.collection(collection).where(field, op, uid).select().get()).docs.map((d) => d.ref);

  // Matches, and every message in them (plus any message they sent or received)
  const matchRefs = [
    ...(await idsWhere("matches", "user1ID")),
    ...(await idsWhere("matches", "user2ID")),
  ];
  const messageRefs = new Map<string, FirebaseFirestore.DocumentReference>();
  for (let i = 0; i < matchRefs.length; i += 30) {
    const ids = matchRefs.slice(i, i + 30).map((ref) => ref.id);
    const snap = await db.collection("messages").where("matchID", "in", ids).select().get();
    snap.docs.forEach((d) => messageRefs.set(d.ref.path, d.ref));
  }
  for (const ref of [
    ...(await idsWhere("messages", "senderID")),
    ...(await idsWhere("messages", "receiverID")),
  ]) {
    messageRefs.set(ref.path, ref);
  }

  const planRefs = uniqueRefs([
    ...(await idsWhere("plans", "proposerID")),
    ...(await idsWhere("plans", "receiverID")),
  ]);
  const todayPlanRefs = uniqueRefs([
    ...(await idsWhere("todayPlans", "creatorID")),
    ...(await idsWhere("todayPlans", "claimerID")),
  ]);
  const hostedGroupPlanRefs = await idsWhere("groupPlans", "hostID");
  const hostedPaths = new Set(hostedGroupPlanRefs.map((ref) => ref.path));
  const invitedGroupPlanRefs = (await idsWhere("groupPlans", "inviteeIDs", "array-contains"))
    .filter((ref) => !hostedPaths.has(ref.path));
  const showUpReportRefs = uniqueRefs([
    ...(await idsWhere("showUpReports", "reporterID")),
    ...(await idsWhere("showUpReports", "reportedUserID")),
  ]);

  // Messages first, so a partial failure can still find the rest via the matches.
  await runBulkWrites((writer) => [...messageRefs.values()].map((ref) => writer.delete(ref)));

  await runBulkWrites((writer) => [
    ...[...planRefs, ...todayPlanRefs, ...hostedGroupPlanRefs, ...showUpReportRefs, ...matchRefs]
      .map((ref) => writer.delete(ref)),
    // Invited group plans stay intact for everyone else; only this user is removed.
    ...invitedGroupPlanRefs.map((ref) => writer.update(
      ref,
      "inviteeIDs", FieldValue.arrayRemove(uid),
      new FieldPath("responses", uid), FieldValue.delete(),
    )),
    writer.delete(db.collection("simpaticoAnswers").doc(uid)),
    writer.delete(db.collection("users").doc(uid)),
  ]);

  const [photoFiles] = await admin.storage().bucket().getFiles({ prefix: `profile_photos/${uid}/` });
  await Promise.all(photoFiles.map((file) => file.delete({ ignoreNotFound: true })));

  return {
    planIDs: [...planRefs, ...hostedGroupPlanRefs, ...invitedGroupPlanRefs].map((ref) => ref.id),
    messages: messageRefs.size,
    matches: matchRefs.length,
    plans: planRefs.length,
    todayPlans: todayPlanRefs.length,
    hostedGroupPlans: hostedGroupPlanRefs.length,
    invitedGroupPlans: invitedGroupPlanRefs.length,
    showUpReports: showUpReportRefs.length,
    profilePhotos: photoFiles.length,
    authUser: 0,
  };
}

/// Runs a batch of writes through a BulkWriter (which handles Firestore's batch
/// limits and retries) and throws if any individual write ultimately failed.
async function runBulkWrites(
  enqueue: (writer: FirebaseFirestore.BulkWriter) => Promise<unknown>[]
): Promise<void> {
  const writer = db.bulkWriter();
  const results = Promise.allSettled(enqueue(writer));
  await writer.close();
  const failures = (await results).filter((r) => r.status === "rejected");
  if (failures.length > 0) {
    throw new functions.https.HttpsError("internal", `${failures.length} write(s) failed; please retry.`);
  }
}

function uniqueRefs(refs: FirebaseFirestore.DocumentReference[]): FirebaseFirestore.DocumentReference[] {
  return [...new Map(refs.map((ref) => [ref.path, ref])).values()];
}
