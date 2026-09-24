import * as functions from "firebase-functions/v1";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

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

    // No auth context on a Firestore trigger, so infer who just confirmed from
    // the prior status: "pending" -> receiver accepted the proposer's dates;
    // PLAN_STATUS_COUNTER_PROPOSED ("counter") -> proposer accepted the
    // receiver's counter-dates.
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
      { type: "planConfirmed", planId: context.params.planId, matchID }
    );
  });
