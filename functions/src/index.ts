import * as functions from "firebase-functions/v1";
import * as admin from "firebase-admin";
import { FieldValue } from "firebase-admin/firestore";

admin.initializeApp();

const db = admin.firestore();

// ─── Helper: get user data ────────────────────────────────────────────────────

async function getUserData(userId: string) {
  const doc = await db.collection("users").doc(userId).get();
  if (!doc.exists) return null;
  const data = doc.data()!;
  return {
    fcmToken: (data.fcmToken as string) ?? null,
    displayName: (data.displayName as string) ?? "Someone",
    prefs: (data.notificationPreferences as { [key: string]: boolean }) ?? {},
    unreadCount: (data.unreadCount as number) ?? 0,
  };
}

// ─── Helper: increment unread count and send notification ─────────────────────

async function sendNotification(
  userId: string,
  fcmToken: string,
  title: string,
  body: string,
  data?: { [key: string]: string }
): Promise<void> {
  // Increment unreadCount in Firestore atomically
  const userRef = db.collection("users").doc(userId);
  await userRef.update({
    unreadCount: FieldValue.increment(1),
  });

  // Get the new count to use as badge
  const updatedDoc = await userRef.get();
  const newCount = (updatedDoc.data()?.unreadCount as number) ?? 1;

  try {
    await admin.messaging().send({
      token: fcmToken,
      notification: { title, body },
      data: data ?? {},
      apns: {
        payload: { aps: { sound: "default", badge: newCount } },
      },
    });
    console.log(`✅ Sent: ${title} (badge: ${newCount})`);
  } catch (error) {
    console.error(`❌ Failed: ${error}`);
    // Roll back the increment if send failed
    await userRef.update({
      unreadCount: FieldValue.increment(-1),
    });
  }
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
      if (!userData?.fcmToken) continue;
      if (userData.prefs.newMatches === false) continue;

      const otherData = await getUserData(otherUserId);
      await sendNotification(
        userId,
        userData.fcmToken,
        "New Match! 🎉",
        `You and ${otherData?.displayName ?? "Someone"} both said Yay!`,
        { type: "newMatch", matchId: context.params.matchId, otherUserId }
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

    const receiverData = await getUserData(receiverId);
    if (!receiverData?.fcmToken) return;
    if (receiverData.prefs.newMessages === false) return;

    const senderData = await getUserData(senderId);
    const preview = text.length > 60 ? text.substring(0, 60) + "…" : text;

    await sendNotification(
      receiverId,
      receiverData.fcmToken,
      senderData?.displayName ?? "Someone",
      preview,
      { type: "newMessage", messageId: context.params.messageId, senderId }
    );
  });

// ─── 3. New Plan Request ──────────────────────────────────────────────────────

export const onNewPlanRequest = functions.firestore
  .document("plans/{planId}")
  .onCreate(async (snap, context) => {
    const plan = snap.data();
    const proposerId = plan.proposerID as string;
    const receiverId = plan.receiverID as string;

    const receiverData = await getUserData(receiverId);
    if (!receiverData?.fcmToken) return;
    if (receiverData.prefs.planRequests === false) return;

    const proposerData = await getUserData(proposerId);
    const activityName = (plan.activity?.name as string) ?? "a hangout";

    await sendNotification(
      receiverId,
      receiverData.fcmToken,
      "New Plan Request! 📅",
      `${proposerData?.displayName ?? "Someone"} wants to ${activityName} with you!`,
      { type: "planRequest", planId: context.params.planId, proposerId }
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

    if (before.status === "confirmed" || after.status !== "confirmed") return;

    const proposerId = after.proposerID as string;
    const receiverId = after.receiverID as string;
    const activityName = (after.activity?.name as string) ?? "your hangout";

    // No auth context on a Firestore trigger, so infer who just confirmed from
    // the prior status: "pending" -> receiver accepted the proposer's dates;
    // "counterProposed" -> proposer accepted the receiver's counter-dates.
    const confirmerId = before.status === "counterProposed" ? proposerId : receiverId;
    const notifyId = confirmerId === proposerId ? receiverId : proposerId;

    const notifyData = await getUserData(notifyId);
    if (!notifyData?.fcmToken) return;
    if (notifyData.prefs.planConfirmations === false) return;

    const confirmerData = await getUserData(confirmerId);

    await sendNotification(
      notifyId,
      notifyData.fcmToken,
      "Plan Confirmed! ✅",
      `${confirmerData?.displayName ?? "Someone"} confirmed your ${activityName} plan. It's on!`,
      { type: "planConfirmed", planId: context.params.planId }
    );
  });
