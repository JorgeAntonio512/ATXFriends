// ATX Friends — Show-Up Report Emulator Integration Test
//
// Tests both the applyShowUpReport Cloud Function behavior and the
// showUpReports security rules against the local Firebase emulator suite.
//
// Run from the repo root:
//   npm --prefix functions run build && \
//   firebase emulators:exec --only auth,firestore,functions \
//     "node functions/test-show-up-emulator.js"
//
// Prerequisites: Java installed (required by Firestore emulator).

"use strict";

const { initializeApp } = require("firebase-admin/app");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");

// Admin SDK auto-connects to emulators when FIRESTORE_EMULATOR_HOST is set
// (firebase emulators:exec sets this automatically).
const app = initializeApp({ projectId: "avenue3-73bb4" });
const db = getFirestore(app);

const PROJECT_ID = "avenue3-73bb4";
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST || "localhost:8080";

// ─── Helpers ──────────────────────────────────────────────────────────────────

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

// The Firestore emulator does NOT verify JWT signatures — it just parses the
// payload to get auth.uid / request.auth. We construct a minimal fake JWT.
function fakeJWT(uid) {
  const b64 = (obj) => Buffer.from(JSON.stringify(obj)).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  const header  = b64({ alg: "RS256", typ: "JWT" });
  const payload = b64({
    iss: `https://securetoken.google.com/${PROJECT_ID}`,
    aud: PROJECT_ID,
    sub: uid,
    user_id: uid,
    iat: now,
    exp: now + 3600,
  });
  return `${header}.${payload}.fakesig`;
}

// Convert a plain JS object to the Firestore REST API wire format.
function toFirestoreDoc(obj) {
  function toValue(v) {
    if (typeof v === "boolean") return { booleanValue: v };
    if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
    if (typeof v === "string") return { stringValue: v };
    if (v === null) return { nullValue: null };
    return { stringValue: String(v) };
  }
  return { fields: Object.fromEntries(Object.entries(obj).map(([k, v]) => [k, toValue(v)])) };
}

// POST a document to a Firestore collection via the REST API (with optional auth).
async function firestorePost(collection, doc, authUID = null) {
  const url = `http://${FIRESTORE_HOST}/v1/projects/${PROJECT_ID}/databases/(default)/documents/${collection}`;
  const headers = { "Content-Type": "application/json" };
  if (authUID) headers["Authorization"] = `Bearer ${fakeJWT(authUID)}`;
  const res = await fetch(url, { method: "POST", headers, body: JSON.stringify(toFirestoreDoc(doc)) });
  return { status: res.status, ok: res.ok };
}

// PATCH a single field on an existing document via the REST API (with optional auth).
async function firestorePatch(collection, docId, fieldName, value, authUID = null) {
  const url = `http://${FIRESTORE_HOST}/v1/projects/${PROJECT_ID}/databases/(default)/documents/${collection}/${docId}?updateMask.fieldPaths=${fieldName}`;
  const headers = { "Content-Type": "application/json" };
  if (authUID) headers["Authorization"] = `Bearer ${fakeJWT(authUID)}`;
  const body = { fields: { [fieldName]: typeof value === "number" ? { integerValue: String(value) } : { stringValue: String(value) } } };
  const res = await fetch(url, { method: "PATCH", headers, body: JSON.stringify(body) });
  return { status: res.status, ok: res.ok };
}

// Wipe the emulator's Firestore data between runs.
async function clearFirestore() {
  await fetch(
    `http://${FIRESTORE_HOST}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`,
    { method: "DELETE" }
  );
}

// ─── Test Runner ──────────────────────────────────────────────────────────────

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

// ─── Main ─────────────────────────────────────────────────────────────────────

async function run() {
  console.log("\n🧪  ATX Friends — Show-Up Report Emulator Test\n");
  console.log(`    Firestore emulator: ${FIRESTORE_HOST}`);
  console.log(`    Project:            ${PROJECT_ID}\n`);

  await clearFirestore();

  const GEORGE = "test_george";
  const SALLY  = "test_sally";
  const PLAN_A = "test_plan_expired";
  const PLAN_B = "test_plan_future";
  const PLAN_C = "test_plan_thumbsdown";

  const twoHoursAgo = Timestamp.fromDate(new Date(Date.now() - 2 * 60 * 60 * 1000));
  const inOneHour   = Timestamp.fromDate(new Date(Date.now() + 1 * 60 * 60 * 1000));

  const userBase = {
    isProfileComplete: true,
    friendshipMode: "individual",
    photoURLs: [],
    activityIDs: [],
    daySlotCombos: [],
    latitude: 30.27,
    longitude: -97.74,
    radiusMiles: 10,
  };

  // Seed via admin SDK (bypasses security rules)
  await db.collection("users").doc(GEORGE).set({ ...userBase, displayName: "George", showUpTotal: 0, showUpThumbsUp: 0 });
  await db.collection("users").doc(SALLY).set({ ...userBase, displayName: "Sally", showUpTotal: 0, showUpThumbsUp: 0 });

  await db.collection("todayPlans").doc(PLAN_A).set({
    creatorID: GEORGE, claimerID: SALLY,
    activity: { name: "Coffee", id: "act_coffee" },
    scheduledTime: twoHoursAgo, status: "claimed",
    createdAt: twoHoursAgo, updatedAt: twoHoursAgo,
  });
  await db.collection("todayPlans").doc(PLAN_B).set({
    creatorID: GEORGE, claimerID: SALLY,
    activity: { name: "Hiking", id: "act_hiking" },
    scheduledTime: inOneHour, status: "claimed",
    createdAt: twoHoursAgo, updatedAt: twoHoursAgo,
  });
  await db.collection("todayPlans").doc(PLAN_C).set({
    creatorID: GEORGE, claimerID: SALLY,
    activity: { name: "Tacos", id: "act_tacos" },
    scheduledTime: twoHoursAgo, status: "claimed",
    createdAt: twoHoursAgo, updatedAt: twoHoursAgo,
  });

  console.log("📦  Seeded: 2 users + 3 plans (2 expired, 1 future)\n");

  // ── SECTION 1: Security Rules ────────────────────────────────────────────────
  console.log("━━━  Security Rules  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

  console.log("A. Valid create — reporter matches auth uid");
  const r1 = await firestorePost("showUpReports", {
    reporterID: GEORGE, reportedUserID: SALLY, planID: PLAN_A, didShowUp: true, createdAt: "now",
  }, GEORGE);
  assert(r1.ok, `Auth uid == reporterID → HTTP ${r1.status} (want 200)`);

  console.log("\nB. Rejected — auth uid ≠ reporterID (George's uid, but Sally's auth)");
  const r2 = await firestorePost("showUpReports", {
    reporterID: GEORGE, reportedUserID: SALLY, planID: PLAN_A, didShowUp: true, createdAt: "now",
  }, SALLY); // mismatch
  assert(!r2.ok, `Mismatched uid → HTTP ${r2.status} (want 403)`);

  console.log("\nC. Rejected — missing required field (planID absent)");
  const r3 = await firestorePost("showUpReports", {
    reporterID: GEORGE, reportedUserID: SALLY, didShowUp: true, createdAt: "now",
    // planID omitted
  }, GEORGE);
  assert(!r3.ok, `Missing planID → HTTP ${r3.status} (want 403)`);

  console.log("\nD. Rejected — unauthenticated (no auth header)");
  const r4 = await firestorePost("showUpReports", {
    reporterID: GEORGE, reportedUserID: SALLY, planID: PLAN_A, didShowUp: true, createdAt: "now",
  }, null);
  assert(!r4.ok, `No auth → HTTP ${r4.status} (want 403)`);

  console.log("\nE. Rejected — direct cross-user showUpTotal write (old rule removed)");
  const r5 = await firestorePatch("users", SALLY, "showUpTotal", 999, GEORGE);
  assert(!r5.ok, `George patching Sally's showUpTotal → HTTP ${r5.status} (want 403)`);

  // ── SECTION 1b: Waitlist Signup Rules ────────────────────────────────────────
  console.log("\n━━━  Waitlist Signup Rules  ━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

  console.log("W1. Valid create — unauthenticated, valid email shape");
  const w1 = await firestorePost("waitlistSignups", {
    email: "test@example.com",
    submittedAt: "2026-09-09",
  }, null); // no auth token
  assert(w1.ok, `Valid email, no auth → HTTP ${w1.status} (want 200)`);

  console.log("\nW2. Rejected — invalid email (no @ symbol)");
  const w2 = await firestorePost("waitlistSignups", {
    email: "notanemail",
    submittedAt: "2026-09-09",
  }, null);
  assert(!w2.ok, `Bad email → HTTP ${w2.status} (want 403)`);

  console.log("\nW3. Rejected — extra field in payload (keys().size() > 2)");
  const w3 = await firestorePost("waitlistSignups", {
    email: "other@example.com",
    submittedAt: "2026-09-09",
    extraField: "bad",
  }, null);
  assert(!w3.ok, `Extra field → HTTP ${w3.status} (want 403)`);

  console.log("\nW4. Rejected — missing required field (no submittedAt)");
  const w4 = await firestorePost("waitlistSignups", {
    email: "another@example.com",
  }, null);
  assert(!w4.ok, `Missing submittedAt → HTTP ${w4.status} (want 403)`);

  // Note: allow read/update/delete: if false is implicit in the rules.
  // The rules test framework (emulator security rules) enforces these —
  // a GET on a specific doc returns 403 when the authenticated rule denies it,
  // consistent with the deny-all pattern used for showUpReports above.

  // ── SECTION 2: Cloud Function ────────────────────────────────────────────────
  console.log("\n━━━  Cloud Function: applyShowUpReport  ━━━━━━━━━━━━━━\n");

  // Reset counters cleanly before function tests
  await db.collection("users").doc(SALLY).update({ showUpTotal: 0, showUpThumbsUp: 0 });

  console.log("1. Thumbs up → plan field set, showUpTotal+1, showUpThumbsUp+1");
  await db.collection("showUpReports").add({
    reporterID: GEORGE, reportedUserID: SALLY, planID: PLAN_A,
    didShowUp: true, createdAt: Timestamp.now(),
  });
  await sleep(8000); // wait for emulator to process the trigger

  const planA  = (await db.collection("todayPlans").doc(PLAN_A).get()).data();
  const sally1 = (await db.collection("users").doc(SALLY).get()).data();
  assert(planA?.creatorReportedClaimer === true, `planA.creatorReportedClaimer = true (got ${planA?.creatorReportedClaimer})`);
  assert(sally1?.showUpTotal === 1,     `Sally showUpTotal = 1 (got ${sally1?.showUpTotal})`);
  assert(sally1?.showUpThumbsUp === 1,  `Sally showUpThumbsUp = 1 (got ${sally1?.showUpThumbsUp})`);

  console.log("\n2. Duplicate report → idempotent, counters stay at 1");
  await db.collection("showUpReports").add({
    reporterID: GEORGE, reportedUserID: SALLY, planID: PLAN_A,
    didShowUp: true, createdAt: Timestamp.now(),
  });
  await sleep(8000);

  const sally2 = (await db.collection("users").doc(SALLY).get()).data();
  assert(sally2?.showUpTotal === 1, `showUpTotal still 1 after duplicate (got ${sally2?.showUpTotal})`);

  console.log("\n3. Thumbs down → showUpTotal+1 but showUpThumbsUp unchanged");
  await db.collection("showUpReports").add({
    reporterID: GEORGE, reportedUserID: SALLY, planID: PLAN_C,
    didShowUp: false, createdAt: Timestamp.now(),
  });
  await sleep(8000);

  const planC  = (await db.collection("todayPlans").doc(PLAN_C).get()).data();
  const sally3 = (await db.collection("users").doc(SALLY).get()).data();
  assert(planC?.creatorReportedClaimer === false, `planC.creatorReportedClaimer = false (got ${planC?.creatorReportedClaimer})`);
  assert(sally3?.showUpTotal === 2,    `showUpTotal = 2 after thumbs down (got ${sally3?.showUpTotal})`);
  assert(sally3?.showUpThumbsUp === 1, `showUpThumbsUp still 1 (got ${sally3?.showUpThumbsUp})`);

  console.log("\n4. Non-participant reporter → rejected, counters unchanged");
  await db.collection("showUpReports").add({
    reporterID: "user_random_stranger", reportedUserID: SALLY, planID: PLAN_A,
    didShowUp: true, createdAt: Timestamp.now(),
  });
  await sleep(8000);

  const sally4 = (await db.collection("users").doc(SALLY).get()).data();
  assert(sally4?.showUpTotal === 2, `showUpTotal still 2 after non-participant report (got ${sally4?.showUpTotal})`);

  console.log("\n5. Future plan (not expired) → rejected, counters unchanged");
  await db.collection("showUpReports").add({
    reporterID: GEORGE, reportedUserID: SALLY, planID: PLAN_B,
    didShowUp: true, createdAt: Timestamp.now(),
  });
  await sleep(8000);

  const sally5 = (await db.collection("users").doc(SALLY).get()).data();
  assert(sally5?.showUpTotal === 2, `showUpTotal still 2 after future-plan report (got ${sally5?.showUpTotal})`);

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
