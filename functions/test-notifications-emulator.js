// Avenue3 — Notifications (Round 03) Emulator Test
//
// Unit-tests the notification-sending Cloud Functions' LOGIC directly via
// firebase-functions-test's wrap(), against the local Firestore emulator for
// data reads/writes. admin.messaging() is monkey-patched to a local stub —
// this test never makes a real network call to FCM.
//
// Covers the round-03 fixes:
//   #2 onPlanConfirmed notifies the correct party in both directions
//   #3 onNewMessage skips planProposal-kind messages (no double push)
//   #4 a blocked sender's action never pushes to the person who blocked them
//   #5 a dead FCM token is removed from fcmTokens; rollback is all-or-nothing
//
// Run from the repo root:
//   npm --prefix functions run build && \
//   firebase emulators:exec --only firestore \
//     "node functions/test-notifications-emulator.js"
//
// Prerequisites: Java installed (required by Firestore emulator).

"use strict";

const admin = require("firebase-admin");
const functionsTest = require("firebase-functions-test")();

const myFunctions = require("./lib/index.js");

const db = admin.firestore();
const PROJECT_ID = "avenue3-73bb4";
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST || "localhost:8080";

// ─── admin.messaging() stub — no real network call, ever ──────────────────────

const sentMessages = []; // every {token, notification, data} actually "sent"
const tokenBehaviors = {}; // token -> "dead" | "invalid" | "fail" | undefined (succeeds)

// admin.messaging is a getter-only property on the FirebaseNamespace —
// plain assignment throws, so redefine the property descriptor instead.
Object.defineProperty(admin, "messaging", {
  configurable: true,
  value: () => ({
    send: async (message) => {
      sentMessages.push(message);
      const behavior = tokenBehaviors[message.token];
      if (behavior === "dead") {
        const err = new Error("Requested entity was not found.");
        err.code = "messaging/registration-token-not-registered";
        throw err;
      }
      if (behavior === "invalid") {
        const err = new Error("Invalid registration token.");
        err.code = "messaging/invalid-registration-token";
        throw err;
      }
      if (behavior === "fail") {
        const err = new Error("Internal server error.");
        err.code = "messaging/internal-error";
        throw err;
      }
      return `projects/fake/messages/${sentMessages.length}`;
    },
  }),
});

function sentTo(token) {
  return sentMessages.filter((m) => m.token === token);
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

async function clearFirestore() {
  await fetch(
    `http://${FIRESTORE_HOST}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`,
    { method: "DELETE" }
  );
}

let passed = 0;
let failed = 0;
function assert(condition, message) {
  if (condition) {
    console.log(`  ✅ ${message}`);
    passed++;
  } else {
    console.log(`  ❌ ${message}`);
    failed++;
  }
}

const BASE_USER = {
  isProfileComplete: true,
  friendshipMode: "individual",
  photoURLs: [],
  activityIDs: [],
  daySlotCombos: [],
  latitude: 30.27,
  longitude: -97.74,
  radiusMiles: 10,
};

async function seedUser(uid, overrides = {}) {
  await db.collection("users").doc(uid).set({
    ...BASE_USER,
    displayName: uid,
    unreadCount: 0,
    ...overrides,
  });
}

// ─── Main ─────────────────────────────────────────────────────────────────────

async function run() {
  console.log("\n🧪  Avenue3 — Notifications (Round 03) Emulator Test\n");
  console.log(`    Firestore emulator: ${FIRESTORE_HOST}`);
  console.log(`    Project:            ${PROJECT_ID}\n`);

  await clearFirestore();

  const onPlanConfirmed = functionsTest.wrap(myFunctions.onPlanConfirmed);
  const onNewMessage = functionsTest.wrap(myFunctions.onNewMessage);

  const GEORGE = `george_${Date.now()}`;
  const CARLOS = `carlos_${Date.now()}`;
  await seedUser(GEORGE, { fcmTokens: ["token_george"] });
  await seedUser(CARLOS, { fcmTokens: ["token_carlos"] });

  // ── #2: onPlanConfirmed notifies the right person, both directions ─────────
  console.log("━━━  #2  onPlanConfirmed — correct recipient  ━━━━━━━━━━━━━━\n");

  console.log("A. Receiver accepts the original proposal → notify the proposer");
  sentMessages.length = 0;
  const planA = {
    matchID: "match_1", proposerID: GEORGE, receiverID: CARLOS,
    activity: { name: "Hiking" }, status: "confirmed",
  };
  await onPlanConfirmed(
    functionsTest.makeChange(
      functionsTest.firestore.makeDocumentSnapshot({ ...planA, status: "pending" }, "plans/planA"),
      functionsTest.firestore.makeDocumentSnapshot(planA, "plans/planA")
    ),
    { params: { planId: "planA" } }
  );
  await sleep(200);
  assert(sentTo("token_george").length === 1, "Proposer (George) received the confirmation push");
  assert(sentTo("token_carlos").length === 0, "Receiver (Carlos), who just confirmed, got nothing — no self-notify");

  console.log("\nB. Proposer accepts a counter-proposal → notify the receiver");
  sentMessages.length = 0;
  const planB = {
    matchID: "match_2", proposerID: GEORGE, receiverID: CARLOS,
    activity: { name: "Tacos" }, status: "confirmed",
  };
  await onPlanConfirmed(
    functionsTest.makeChange(
      // Real stored value for "counter-proposed" is "counter", not
      // "counterProposed" — this is the exact bug fix under test.
      functionsTest.firestore.makeDocumentSnapshot({ ...planB, status: "counter" }, "plans/planB"),
      functionsTest.firestore.makeDocumentSnapshot(planB, "plans/planB")
    ),
    { params: { planId: "planB" } }
  );
  await sleep(200);
  assert(sentTo("token_carlos").length === 1, "Receiver (Carlos) received the confirmation push");
  assert(sentTo("token_george").length === 0, "Proposer (George), who just confirmed the counter, got nothing — no self-notify");

  // ── #3: no double push for planProposal messages ────────────────────────────
  console.log("\n━━━  #3  onNewMessage — skips planProposal kind  ━━━━━━━━━━━\n");

  sentMessages.length = 0;
  await onNewMessage(
    functionsTest.firestore.makeDocumentSnapshot({
      senderID: GEORGE, receiverID: CARLOS, text: "Proposed Hiking · Sat 7pm",
      matchID: "match_1", kind: "planProposal",
    }, "messages/msgPlan"),
    { params: { messageId: "msgPlan" } }
  );
  await sleep(200);
  assert(sentMessages.length === 0, "planProposal message sent zero pushes (onNewPlanRequest already covers it)");

  console.log("\nRegular text message still notifies exactly as before");
  sentMessages.length = 0;
  await onNewMessage(
    functionsTest.firestore.makeDocumentSnapshot({
      senderID: GEORGE, receiverID: CARLOS, text: "hey!", matchID: "match_1",
    }, "messages/msgText"),
    { params: { messageId: "msgText" } }
  );
  await sleep(200);
  assert(sentTo("token_carlos").length === 1, "Plain text message still pushes to the receiver");

  // ── #4: blocked sender never triggers a push ────────────────────────────────
  console.log("\n━━━  #4  Blocked users never trigger a push  ━━━━━━━━━━━━━━━\n");

  await db.collection("users").doc(CARLOS).update({ blockedUsers: [GEORGE] });

  sentMessages.length = 0;
  await onNewMessage(
    functionsTest.firestore.makeDocumentSnapshot({
      senderID: GEORGE, receiverID: CARLOS, text: "hey again", matchID: "match_1",
    }, "messages/msgBlocked"),
    { params: { messageId: "msgBlocked" } }
  );
  await sleep(200);
  assert(sentMessages.length === 0, "Carlos blocked George — George's message triggered no push to Carlos");

  await db.collection("users").doc(CARLOS).update({ blockedUsers: [] });

  // ── #5: dead token removed from fcmTokens; rollback is all-or-nothing ───────
  console.log("\n━━━  #5  Dead FCM token cleanup + partial-failure rollback  ━\n");

  const SALLY = `sally_${Date.now()}`;
  await seedUser(SALLY, { fcmTokens: ["token_sally_dead", "token_sally_live"] });
  tokenBehaviors["token_sally_dead"] = "dead";

  sentMessages.length = 0;
  await onNewMessage(
    functionsTest.firestore.makeDocumentSnapshot({
      senderID: GEORGE, receiverID: SALLY, text: "hi sally", matchID: "match_3",
    }, "messages/msgSally"),
    { params: { messageId: "msgSally" } }
  );
  await sleep(200);

  const sallyDoc = (await db.collection("users").doc(SALLY).get()).data();
  assert(!sallyDoc.fcmTokens.includes("token_sally_dead"), "Dead token removed from fcmTokens");
  assert(sallyDoc.fcmTokens.includes("token_sally_live"), "Live token untouched");
  assert(sallyDoc.unreadCount === 1, "unreadCount incremented — the live token succeeded, so no rollback");

  console.log("\nRollback only when EVERY token fails");
  const WALTER = `walter_${Date.now()}`;
  await seedUser(WALTER, { fcmTokens: ["token_walter_dead"] });
  tokenBehaviors["token_walter_dead"] = "dead";
  await onNewMessage(
    functionsTest.firestore.makeDocumentSnapshot({
      senderID: GEORGE, receiverID: WALTER, text: "hi walter", matchID: "match_4",
    }, "messages/msgWalter"),
    { params: { messageId: "msgWalter" } }
  );
  await sleep(200);
  const walterDoc = (await db.collection("users").doc(WALTER).get()).data();
  assert(walterDoc.unreadCount === 0, "unreadCount rolled back — every token failed");
  assert(!walterDoc.fcmTokens.includes("token_walter_dead"), "The one dead token was still removed");

  // ── Legacy token fallback (getUserData folds fcmToken into fcmTokens) ───────
  console.log("\n━━━  Legacy fcmToken fallback still delivers  ━━━━━━━━━━━━━━\n");
  const LEGACY = `legacy_${Date.now()}`;
  await db.collection("users").doc(LEGACY).set({
    ...BASE_USER, displayName: LEGACY, unreadCount: 0, fcmToken: "token_legacy",
  });
  sentMessages.length = 0;
  await onNewMessage(
    functionsTest.firestore.makeDocumentSnapshot({
      senderID: GEORGE, receiverID: LEGACY, text: "hi legacy", matchID: "match_5",
    }, "messages/msgLegacy"),
    { params: { messageId: "msgLegacy" } }
  );
  await sleep(200);
  assert(sentTo("token_legacy").length === 1, "User who hasn't migrated yet (only has legacy fcmToken) still gets pushed");

  // ── Summary ────────────────────────────────────────────────────────────────
  console.log(`\n${"━".repeat(55)}`);
  console.log(`🏁  Results: ${passed} passed, ${failed} failed\n`);
  if (failed > 0) {
    console.log("Fix failing tests before deploying to production.\n");
    process.exit(1);
  } else {
    console.log("All tests passed — safe to deploy. 🚀\n");
  }
}

run().catch((err) => {
  console.error("❌  Unhandled error:", err);
  process.exit(1);
});
