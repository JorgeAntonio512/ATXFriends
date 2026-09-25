// Emulator tests for the deleteMyAccount callable Cloud Function.
//
// Run with `npm run test:functions` from this directory (builds functions/, then spins up
// the auth, firestore, functions, and storage emulators via `firebase emulators:exec`).
//
// Seeds a user ("dana") with matches, messages, 1-on-1 plans, Today plans, a hosted group
// plan, an invited group plan, show-up reports, Simpatico answers, and profile photos,
// alongside unrelated data for two other users, then calls the real callable endpoint.

import { readFileSync } from 'fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc } from 'firebase/firestore';
import { ref, uploadBytes, listAll } from 'firebase/storage';

const PROJECT_ID = 'avenue3-73bb4';
const BUCKET = 'gs://avenue3-73bb4.firebasestorage.app';
const AUTH_HOST = `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST ?? '127.0.0.1:9099'}`;
const FUNCTIONS_HOST = 'http://127.0.0.1:5001';
const CALLABLE_URL = `${FUNCTIONS_HOST}/${PROJECT_ID}/us-central1/deleteMyAccount`;

let passed = 0;
let failed = 0;
const failures = [];

async function check(name, fn) {
  try {
    await fn();
    passed++;
    console.log(`  ok  - ${name}`);
  } catch (e) {
    failed++;
    failures.push(name);
    console.log(`FAIL  - ${name}\n        ${e.message.split('\n')[0]}`);
  }
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function signUp(email) {
  const res = await fetch(
    `${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: 'password123', returnSecureToken: true }),
    },
  );
  const body = await res.json();
  if (!body.localId) throw new Error(`signUp failed: ${JSON.stringify(body)}`);
  return { uid: body.localId, idToken: body.idToken };
}

async function authUserIDs() {
  const res = await fetch(
    `${AUTH_HOST}/identitytoolkit.googleapis.com/v1/projects/${PROJECT_ID}/accounts:batchGet?maxResults=100`,
    { headers: { Authorization: 'Bearer owner' } },
  );
  const body = await res.json();
  if (!res.ok) throw new Error(`batchGet failed: ${JSON.stringify(body)}`);
  return new Set((body.users ?? []).map((u) => u.localId));
}

async function callDelete(idToken) {
  const headers = { 'Content-Type': 'application/json' };
  if (idToken) headers.Authorization = `Bearer ${idToken}`;
  const res = await fetch(CALLABLE_URL, { method: 'POST', headers, body: JSON.stringify({ data: {} }) });
  return { status: res.status, body: await res.json() };
}

async function main() {
  const testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
    storage: { rules: readFileSync(new URL('../storage.rules', import.meta.url), 'utf8') },
  });
  // withSecurityRulesDisabled resolves to void, so capture the callback's result.
  const asAdmin = async (fn) => {
    let result;
    await testEnv.withSecurityRulesDisabled(async (ctx) => { result = await fn(ctx); });
    return result;
  };
  const exists = (path) => asAdmin(async (ctx) => (await getDoc(doc(ctx.firestore(), path))).exists());
  const read = (path) => asAdmin(async (ctx) => (await getDoc(doc(ctx.firestore(), path))).data());
  const photoNames = (uid) => asAdmin(async (ctx) =>
    (await listAll(ref(ctx.storage(BUCKET), `profile_photos/${uid}`))).items.map((i) => i.name));

  const dana = await signUp('dana@example.com'); // the account being deleted
  const eve = await signUp('eve@example.com');
  const frank = await signUp('frank@example.com');
  const D = dana.uid, E = eve.uid, F = frank.uid;

  const now = new Date();
  const activity = { id: 'a1', name: 'Coffee', isUserAdded: false, createdAt: now };
  const match = (u1, u2) => ({ user1ID: u1, user2ID: u2, isMutualMatch: true, createdAt: now, updatedAt: now });
  const message = (matchID, from, to) => ({ matchID, senderID: from, receiverID: to, text: 'hi', sentAt: now, isRead: false });
  const plan = (from, to, matchID) => ({
    matchID, proposerID: from, receiverID: to, activity, proposedDates: [now],
    status: 'confirmed', confirmedDate: now, createdAt: now, updatedAt: now,
  });
  const todayPlan = (creatorID, claimerID) => ({
    creatorID, activity, scheduledTime: now, status: claimerID ? 'claimed' : 'open',
    ...(claimerID ? { claimerID } : {}), createdAt: now, updatedAt: now,
  });
  const groupPlan = (hostID, inviteeIDs, responses) => ({
    hostID, inviteeIDs, responses, activity, date: now, status: 'active', createdAt: now, updatedAt: now,
  });

  await asAdmin(async (ctx) => {
    const db = ctx.firestore();
    const seed = {
      [`users/${D}`]: { displayName: 'Dana', fcmTokens: ['tok-d'] },
      [`users/${E}`]: { displayName: 'Eve', showUpTotal: 3, showUpThumbsUp: 2 },
      [`users/${F}`]: { displayName: 'Frank', showUpTotal: 5, showUpThumbsUp: 5 },
      'matches/mDE': match(D, E),
      'matches/mFD': match(F, D),
      'matches/mEF': match(E, F),
      'messages/msg1': message('mDE', D, E),
      'messages/msg2': message('mDE', E, D),
      'messages/msg3': message('mFD', F, D),
      'messages/msg4': message('mEF', E, F),
      'plans/pDE': plan(D, E, 'mDE'),
      'plans/pFD': plan(F, D, 'mFD'),
      'plans/pEF': plan(E, F, 'mEF'),
      'todayPlans/tD': todayPlan(D),
      'todayPlans/tED': todayPlan(E, D),
      'todayPlans/tEF': todayPlan(E, F),
      'groupPlans/gHostedByD': groupPlan(D, [E], { [E]: 'going' }),
      'groupPlans/gInvitesD': groupPlan(E, [D, F], { [D]: 'going', [F]: 'cantMake' }),
      'groupPlans/gEF': groupPlan(E, [F], { [F]: 'invited' }),
      'showUpReports/sByD': { reporterID: D, reportedUserID: E, planID: 'tED', didShowUp: true, createdAt: now },
      'showUpReports/sAboutD': { reporterID: E, reportedUserID: D, planID: 'tED', didShowUp: false, createdAt: now },
      'showUpReports/sEF': { reporterID: E, reportedUserID: F, planID: 'tEF', didShowUp: true, createdAt: now },
      [`simpaticoAnswers/${D}`]: { answers: {} },
      [`simpaticoAnswers/${E}`]: { answers: {} },
    };
    for (const [path, data] of Object.entries(seed)) await setDoc(doc(db, path), data);

    const storage = ctx.storage(BUCKET);
    const bytes = new Uint8Array([0xff, 0xd8, 0xff]);
    const meta = { contentType: 'image/jpeg' };
    for (const i of [0, 1, 2]) await uploadBytes(ref(storage, `profile_photos/${D}/${D}_photo_${i}.jpg`), bytes, meta);
    await uploadBytes(ref(storage, `profile_photos/${E}/${E}_photo_0.jpg`), bytes, meta);
  });

  // ---- Unauthenticated call ----
  await check('unauthenticated call is rejected', async () => {
    const { status, body } = await callDelete(null);
    assert(status === 401, `expected HTTP 401, got ${status}`);
    assert(body.error?.status === 'UNAUTHENTICATED', `expected UNAUTHENTICATED, got ${JSON.stringify(body)}`);
    assert(await exists(`users/${D}`), 'nothing should be deleted by an unauthenticated call');
  });

  // ---- First call ----
  let firstResult;
  await check('deleteMyAccount succeeds for the signed-in user', async () => {
    const { status, body } = await callDelete(dana.idToken);
    assert(status === 200, `expected HTTP 200, got ${status}: ${JSON.stringify(body)}`);
    firstResult = body.result;
  });

  await check('returns the plan IDs the user was part of (for clearing calendar flags)', async () => {
    const ids = new Set(firstResult?.planIDs ?? []);
    for (const id of ['pDE', 'pFD', 'gHostedByD', 'gInvitesD']) assert(ids.has(id), `missing ${id}`);
    for (const id of ['pEF', 'gEF']) assert(!ids.has(id), `unexpected ${id}`);
  });

  await check('deletes the users doc and simpaticoAnswers doc', async () => {
    assert(!(await exists(`users/${D}`)), 'users doc still exists');
    assert(!(await exists(`simpaticoAnswers/${D}`)), 'simpaticoAnswers doc still exists');
  });

  await check('deletes every match they were in (as user1 or user2)', async () => {
    assert(!(await exists('matches/mDE')), 'mDE still exists');
    assert(!(await exists('matches/mFD')), 'mFD still exists');
  });

  await check('deletes every message in their matches, from both sides', async () => {
    for (const id of ['msg1', 'msg2', 'msg3']) assert(!(await exists(`messages/${id}`)), `${id} still exists`);
  });

  await check('deletes every 1-on-1 plan they proposed or received', async () => {
    assert(!(await exists('plans/pDE')), 'pDE still exists');
    assert(!(await exists('plans/pFD')), 'pFD still exists');
  });

  await check('deletes every Today plan they created or claimed', async () => {
    assert(!(await exists('todayPlans/tD')), 'tD still exists');
    assert(!(await exists('todayPlans/tED')), 'tED still exists');
  });

  await check('deletes the group plan they host', async () => {
    assert(!(await exists('groupPlans/gHostedByD')), 'gHostedByD still exists');
  });

  await check('removes them from a group plan they were invited to, leaving it intact for others', async () => {
    const g = await read('groupPlans/gInvitesD');
    assert(g, 'gInvitesD was deleted');
    assert(JSON.stringify(g.inviteeIDs) === JSON.stringify([F]), `inviteeIDs = ${JSON.stringify(g.inviteeIDs)}`);
    assert(!(D in g.responses), 'responses still has the deleted user');
    assert(g.responses[F] === 'cantMake', 'another invitee\'s response changed');
    assert(g.hostID === E && g.status === 'active', 'other fields changed');
  });

  await check('deletes show-up reports they filed or were the subject of', async () => {
    assert(!(await exists('showUpReports/sByD')), 'sByD still exists');
    assert(!(await exists('showUpReports/sAboutD')), 'sAboutD still exists');
  });

  await check('deletes their Storage profile photos', async () => {
    const names = await photoNames(D);
    assert(names.length === 0, `still has ${names.length} photo(s)`);
  });

  await check('deletes the Firebase Auth user', async () => {
    const ids = await authUserIDs();
    assert(ids.size > 0, 'auth user listing returned nothing');
    assert(!ids.has(D), 'auth user still exists');
  });

  await check('leaves other users\' unrelated data untouched', async () => {
    for (const path of [
      `users/${E}`, `users/${F}`, 'matches/mEF', 'messages/msg4', 'plans/pEF', 'todayPlans/tEF',
      'groupPlans/gEF', 'showUpReports/sEF', `simpaticoAnswers/${E}`,
    ]) {
      assert(await exists(path), `${path} was deleted`);
    }
    const e = await read(`users/${E}`);
    assert(e.showUpTotal === 3 && e.showUpThumbsUp === 2, 'another user\'s show-up counts changed');
    assert((await photoNames(E)).length === 1, 'another user\'s photo was deleted');
    const ids = await authUserIDs();
    assert(ids.has(E) && ids.has(F), 'another auth user was deleted');
  });

  // ---- Second call (retry after success) ----
  await check('calling it again does not error', async () => {
    const { status, body } = await callDelete(dana.idToken);
    assert(status === 200, `expected HTTP 200, got ${status}: ${JSON.stringify(body)}`);
    assert(await exists('groupPlans/gInvitesD'), 'invited group plan was deleted on retry');
  });

  // ---- Retry after a partial failure ----
  // Simulates a run that died halfway: the match is already gone, but its messages,
  // a plan, the users doc, photos, and the Auth user are all still there.
  await check('finishes the job when retried after a partial failure', async () => {
    const gina = await signUp('gina@example.com');
    const G = gina.uid;
    await asAdmin(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, `users/${G}`), { displayName: 'Gina' });
      await setDoc(doc(db, 'messages/gMsg1'), message('mGoneGE', G, E));
      await setDoc(doc(db, 'messages/gMsg2'), message('mGoneGE', E, G));
      await setDoc(doc(db, 'plans/pGE'), plan(G, E, 'mGoneGE'));
      await uploadBytes(ref(ctx.storage(BUCKET), `profile_photos/${G}/${G}_photo_0.jpg`),
        new Uint8Array([0xff]), { contentType: 'image/jpeg' });
    });
    const { status, body } = await callDelete(gina.idToken);
    assert(status === 200, `expected HTTP 200, got ${status}: ${JSON.stringify(body)}`);
    for (const path of [`users/${G}`, 'messages/gMsg1', 'messages/gMsg2', 'plans/pGE']) {
      assert(!(await exists(path)), `${path} still exists`);
    }
    assert((await photoNames(G)).length === 0, 'photo still exists');
    assert(!(await authUserIDs()).has(G), 'auth user still exists');
    assert(await exists(`users/${E}`), 'another user was affected');
  });

  await testEnv.cleanup();

  console.log(`\n${passed} passed, ${failed} failed (${passed + failed} total)`);
  if (failed > 0) {
    console.log('Failed:', failures.join(', '));
    process.exitCode = 1;
  }
}

main().catch((e) => {
  console.error('Test harness crashed:', e);
  process.exitCode = 1;
});
