// Firestore security rules emulator test suite.
//
// Run with `npm test` from this directory (spins up the emulator via
// `firebase emulators:exec`, which sets FIRESTORE_EMULATOR_HOST for this process).
//
// No prior rules-unit-testing suite existed in this repo before this file — this is a
// from-scratch suite covering the existing collections (users, activities, matches, plans,
// messages, todayPlans, simpaticoAnswers, showUpReports, waitlistSignups) plus the new
// groupPlans rules.

import { readFileSync } from 'fs';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc,
  setDoc,
  updateDoc,
  deleteDoc,
  getDoc,
  getDocs,
  collection,
  query,
  where,
  serverTimestamp,
  deleteField,
} from 'firebase/firestore';

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

async function main() {
  const rules = readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8');

  const testEnv = await initializeTestEnvironment({
    projectId: 'avenue3-73bb4',
    firestore: { rules },
  });

  const alice = testEnv.authenticatedContext('alice').firestore();
  const bob = testEnv.authenticatedContext('bob').firestore();
  const carol = testEnv.authenticatedContext('carol').firestore();
  const anon = testEnv.unauthenticatedContext().firestore();

  const seed = async (fn) => testEnv.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()));

  // ============================================================
  // USERS
  // ============================================================
  const validUser = (overrides = {}) => ({
    displayName: 'Alice',
    photoURLs: [],
    activityIDs: [],
    daySlotCombos: [],
    latitude: 30.27,
    longitude: -97.74,
    radiusMiles: 10,
    isProfileComplete: false,
    ...overrides,
  });

  await check('users: owner can create own doc', () =>
    assertSucceeds(setDoc(doc(alice, 'users/alice'), validUser())));

  await check('users: cannot create a doc for someone else', () =>
    assertFails(setDoc(doc(alice, 'users/bob'), validUser())));

  await check('users: owner can read own doc', async () => {
    await seed((db) => setDoc(doc(db, 'users/alice'), validUser()));
    await assertSucceeds(getDoc(doc(alice, 'users/alice')));
  });

  await check('users: anyone signed in can read a completed profile', async () => {
    await seed((db) => setDoc(doc(db, 'users/bob'), validUser({ isProfileComplete: true })));
    await assertSucceeds(getDoc(doc(alice, 'users/bob')));
  });

  await check('users: cannot read an incomplete profile that is not your own', async () => {
    await seed((db) => setDoc(doc(db, 'users/carol'), validUser({ isProfileComplete: false })));
    await assertFails(getDoc(doc(alice, 'users/carol')));
  });

  await check('users: owner can update own doc', async () => {
    await seed((db) => setDoc(doc(db, 'users/alice'), validUser()));
    await assertSucceeds(updateDoc(doc(alice, 'users/alice'), { displayName: 'Alicia' }));
  });

  // ============================================================
  // ACTIVITIES
  // ============================================================
  await check('activities: signed-in user can create a valid activity', () =>
    assertSucceeds(setDoc(doc(alice, 'activities/hiking'), {
      name: 'Hiking', isUserAdded: true, createdAt: serverTimestamp(),
    })));

  await check('activities: cannot create with an empty name', () =>
    assertFails(setDoc(doc(alice, 'activities/bad'), {
      name: '', isUserAdded: true, createdAt: serverTimestamp(),
    })));

  await check('activities: cannot update (immutable)', async () => {
    await seed((db) => setDoc(doc(db, 'activities/tacos'), { name: 'Tacos', isUserAdded: false, createdAt: new Date() }));
    await assertFails(updateDoc(doc(alice, 'activities/tacos'), { name: 'Tacos!' }));
  });

  // ============================================================
  // MATCHES
  // ============================================================
  await check('matches: participant can read their match', async () => {
    await seed((db) => setDoc(doc(db, 'matches/m1'), {
      user1ID: 'alice', user2ID: 'bob', isMutualMatch: false, createdAt: new Date(), updatedAt: new Date(),
    }));
    await assertSucceeds(getDoc(doc(alice, 'matches/m1')));
  });

  await check('matches: non-participant cannot read', async () => {
    await seed((db) => setDoc(doc(db, 'matches/m2'), {
      user1ID: 'alice', user2ID: 'bob', isMutualMatch: false, createdAt: new Date(), updatedAt: new Date(),
    }));
    await assertFails(getDoc(doc(carol, 'matches/m2')));
  });

  await check('matches: participant can set their own decision field', async () => {
    await seed((db) => setDoc(doc(db, 'matches/m3'), {
      user1ID: 'alice', user2ID: 'bob', isMutualMatch: false, createdAt: new Date(), updatedAt: new Date(),
    }));
    await assertSucceeds(updateDoc(doc(alice, 'matches/m3'), { user1Decision: true, updatedAt: new Date() }));
  });

  await check('matches: cannot modify an unrelated field via update', async () => {
    await seed((db) => setDoc(doc(db, 'matches/m4'), {
      user1ID: 'alice', user2ID: 'bob', isMutualMatch: false, createdAt: new Date(), updatedAt: new Date(),
    }));
    await assertFails(updateDoc(doc(alice, 'matches/m4'), { user1ID: 'carol' }));
  });

  await check('matches: cannot delete', async () => {
    await seed((db) => setDoc(doc(db, 'matches/m5'), {
      user1ID: 'alice', user2ID: 'bob', isMutualMatch: false, createdAt: new Date(), updatedAt: new Date(),
    }));
    await assertFails(deleteDoc(doc(alice, 'matches/m5')));
  });

  // ============================================================
  // PLANS (1-on-1)
  // ============================================================
  const validPlan = () => ({
    matchID: 'm1', proposerID: 'alice', receiverID: 'bob',
    activity: { id: 'a1', name: 'Coffee', isUserAdded: false, createdAt: new Date() },
    proposedDates: [new Date()], status: 'pending', createdAt: new Date(), updatedAt: new Date(),
  });

  await check('plans: proposer can create', () =>
    assertSucceeds(setDoc(doc(alice, 'plans/p1'), validPlan())));

  await check('plans: cannot create on someone else\'s behalf', () =>
    assertFails(setDoc(doc(bob, 'plans/p2'), validPlan())));

  await check('plans: receiver can update (e.g. confirm)', async () => {
    await seed((db) => setDoc(doc(db, 'plans/p3'), validPlan()));
    await assertSucceeds(updateDoc(doc(bob, 'plans/p3'), { status: 'confirmed', confirmedDate: new Date() }));
  });

  // ---- Reschedule requests (counterProposedBy) ----
  const originalDate = new Date('2030-06-01T15:00:00Z');
  const newDate = new Date('2030-06-02T15:00:00Z');
  const confirmedPlan = () => ({ ...validPlan(), status: 'confirmed', confirmedDate: originalDate });
  const counterPlan = (requester = 'alice') => ({
    ...confirmedPlan(), status: 'counter', counterProposedDates: [newDate], counterProposedBy: requester,
  });

  await check('plans: participant can request a reschedule of a confirmed plan', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r1'), confirmedPlan()));
    await assertSucceeds(updateDoc(doc(alice, 'plans/r1'), {
      status: 'counter', counterProposedDates: [newDate], counterProposedBy: 'alice', updatedAt: new Date(),
    }));
  });

  await check('plans: reschedule request must name the requester as counterProposedBy', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r2'), confirmedPlan()));
    await assertFails(updateDoc(doc(alice, 'plans/r2'), {
      status: 'counter', counterProposedDates: [newDate], counterProposedBy: 'bob', updatedAt: new Date(),
    }));
  });

  await check('plans: cannot request a second reschedule while one is pending', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r3'), counterPlan('alice')));
    await assertFails(updateDoc(doc(bob, 'plans/r3'), {
      counterProposedDates: [new Date('2030-06-03T15:00:00Z')], counterProposedBy: 'bob', updatedAt: new Date(),
    }));
  });

  await check('plans: other person can accept a reschedule (confirmed at the new time)', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r4'), counterPlan('alice')));
    await assertSucceeds(updateDoc(doc(bob, 'plans/r4'), {
      status: 'confirmed', confirmedDate: newDate,
      counterProposedDates: deleteField(), counterProposedBy: deleteField(), updatedAt: new Date(),
    }));
  });

  await check('plans: other person can decline a reschedule (stays at the original time)', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r5'), counterPlan('alice')));
    await assertSucceeds(updateDoc(doc(bob, 'plans/r5'), {
      status: 'confirmed',
      counterProposedDates: deleteField(), counterProposedBy: deleteField(), updatedAt: new Date(),
    }));
  });

  await check('plans: requester cannot accept their own reschedule request', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r6'), counterPlan('alice')));
    await assertFails(updateDoc(doc(alice, 'plans/r6'), {
      status: 'confirmed', confirmedDate: newDate,
      counterProposedDates: deleteField(), counterProposedBy: deleteField(), updatedAt: new Date(),
    }));
  });

  await check('plans: requester cannot decline their own reschedule request', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r7'), counterPlan('alice')));
    await assertFails(updateDoc(doc(alice, 'plans/r7'), {
      status: 'confirmed',
      counterProposedDates: deleteField(), counterProposedBy: deleteField(), updatedAt: new Date(),
    }));
  });

  await check('plans: resolving must clear the request fields', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r8'), counterPlan('alice')));
    await assertFails(updateDoc(doc(bob, 'plans/r8'), { status: 'confirmed', updatedAt: new Date() }));
  });

  await check('plans: accept cannot set an arbitrary new date', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r9'), counterPlan('alice')));
    await assertFails(updateDoc(doc(bob, 'plans/r9'), {
      status: 'confirmed', confirmedDate: new Date('2031-01-01T00:00:00Z'),
      counterProposedDates: deleteField(), counterProposedBy: deleteField(), updatedAt: new Date(),
    }));
  });

  await check('plans: requester can still cancel a plan with a pending request', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r10'), counterPlan('alice')));
    await assertSucceeds(updateDoc(doc(alice, 'plans/r10'), { status: 'cancelled', updatedAt: new Date() }));
  });

  await check('plans: outsider cannot write a reschedule request', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r11'), confirmedPlan()));
    await assertFails(updateDoc(doc(carol, 'plans/r11'), {
      status: 'counter', counterProposedDates: [newDate], counterProposedBy: 'carol', updatedAt: new Date(),
    }));
  });

  await check('plans: outsider cannot accept a reschedule request', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r12'), counterPlan('alice')));
    await assertFails(updateDoc(doc(carol, 'plans/r12'), {
      status: 'confirmed', confirmedDate: newDate,
      counterProposedDates: deleteField(), counterProposedBy: deleteField(), updatedAt: new Date(),
    }));
  });

  await check('plans: legacy counter plan (no counterProposedBy) can be accepted by either person', async () => {
    const legacy = { ...confirmedPlan(), status: 'counter', counterProposedDates: [newDate] };
    await seed((db) => setDoc(doc(db, 'plans/r13'), legacy));
    await assertSucceeds(updateDoc(doc(alice, 'plans/r13'), {
      status: 'confirmed', confirmedDate: newDate, counterProposedDates: deleteField(), updatedAt: new Date(),
    }));
  });

  await check('plans: legacy counter plan with no confirmedDate can be declined back to the original time', async () => {
    const legacy = { ...validPlan(), status: 'counter', counterProposedDates: [newDate] };
    await seed((db) => setDoc(doc(db, 'plans/r14'), legacy));
    const proposed = legacy.proposedDates[0];
    await assertSucceeds(updateDoc(doc(bob, 'plans/r14'), {
      status: 'confirmed', confirmedDate: proposed, counterProposedDates: deleteField(), updatedAt: new Date(),
    }));
  });

  await check('plans: marking a pending-reschedule plan viewed still works', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r15'), counterPlan('alice')));
    await assertSucceeds(updateDoc(doc(bob, 'plans/r15'), { isViewed: true }));
  });

  await check('plans: cannot set counterProposedBy outside a reschedule request', async () => {
    await seed((db) => setDoc(doc(db, 'plans/r16'), confirmedPlan()));
    await assertFails(updateDoc(doc(alice, 'plans/r16'), { counterProposedBy: 'alice' }));
  });

  await check('plans: stranger cannot get a plan directly', async () => {
    await seed((db) => setDoc(doc(db, 'plans/p4'), validPlan()));
    await assertFails(getDoc(doc(carol, 'plans/p4')));
  });

  await check('plans: cannot delete', async () => {
    await seed((db) => setDoc(doc(db, 'plans/p5'), validPlan()));
    await assertFails(deleteDoc(doc(alice, 'plans/p5')));
  });

  // ============================================================
  // MESSAGES
  // ============================================================
  await check('messages: sender can create', () =>
    assertSucceeds(setDoc(doc(alice, 'messages/msg1'), {
      matchID: 'm1', senderID: 'alice', receiverID: 'bob', text: 'hey!', sentAt: new Date(), isRead: false,
    })));

  await check('messages: cannot spoof a different sender', () =>
    assertFails(setDoc(doc(alice, 'messages/msg2'), {
      matchID: 'm1', senderID: 'bob', receiverID: 'alice', text: 'hey!', sentAt: new Date(), isRead: false,
    })));

  await check('messages: cannot exceed the max length', () =>
    assertFails(setDoc(doc(alice, 'messages/msg3'), {
      matchID: 'm1', senderID: 'alice', receiverID: 'bob', text: 'x'.repeat(5001), sentAt: new Date(), isRead: false,
    })));

  await check('messages: receiver can mark as read', async () => {
    await seed((db) => setDoc(doc(db, 'messages/msg4'), {
      matchID: 'm1', senderID: 'alice', receiverID: 'bob', text: 'hey!', sentAt: new Date(), isRead: false,
    }));
    await assertSucceeds(updateDoc(doc(bob, 'messages/msg4'), { isRead: true }));
  });

  await check('messages: sender cannot mark their own message as read', async () => {
    await seed((db) => setDoc(doc(db, 'messages/msg5'), {
      matchID: 'm1', senderID: 'alice', receiverID: 'bob', text: 'hey!', sentAt: new Date(), isRead: false,
    }));
    await assertFails(updateDoc(doc(alice, 'messages/msg5'), { isRead: true }));
  });

  // ============================================================
  // TODAY PLANS
  // ============================================================
  await check('todayPlans: creator can post an open plan', () =>
    assertSucceeds(setDoc(doc(alice, 'todayPlans/t1'), {
      creatorID: 'alice', activity: { id: 'a1', name: 'Tacos', isUserAdded: false, createdAt: new Date() },
      scheduledTime: new Date(), status: 'open', createdAt: new Date(), updatedAt: new Date(),
    })));

  await check('todayPlans: non-creator can claim an open plan', async () => {
    await seed((db) => setDoc(doc(db, 'todayPlans/t2'), {
      creatorID: 'alice', activity: { id: 'a1', name: 'Tacos', isUserAdded: false, createdAt: new Date() },
      scheduledTime: new Date(), status: 'open', createdAt: new Date(), updatedAt: new Date(),
    }));
    await assertSucceeds(updateDoc(doc(bob, 'todayPlans/t2'), {
      status: 'claimed', claimerID: 'bob', updatedAt: new Date(),
    }));
  });

  await check('todayPlans: claim cannot also change unrelated fields', async () => {
    await seed((db) => setDoc(doc(db, 'todayPlans/t3'), {
      creatorID: 'alice', activity: { id: 'a1', name: 'Tacos', isUserAdded: false, createdAt: new Date() },
      scheduledTime: new Date(), status: 'open', createdAt: new Date(), updatedAt: new Date(),
    }));
    await assertFails(updateDoc(doc(bob, 'todayPlans/t3'), {
      status: 'claimed', claimerID: 'bob', updatedAt: new Date(), note: 'sneaky',
    }));
  });

  await check('todayPlans: creator can delete their own open plan', async () => {
    await seed((db) => setDoc(doc(db, 'todayPlans/t4'), {
      creatorID: 'alice', activity: { id: 'a1', name: 'Tacos', isUserAdded: false, createdAt: new Date() },
      scheduledTime: new Date(), status: 'open', createdAt: new Date(), updatedAt: new Date(),
    }));
    await assertSucceeds(deleteDoc(doc(alice, 'todayPlans/t4')));
  });

  // ============================================================
  // SIMPATICO ANSWERS
  // ============================================================
  await check('simpaticoAnswers: any signed-in user can read', async () => {
    await seed((db) => setDoc(doc(db, 'simpaticoAnswers/alice'), { userID: 'alice' }));
    await assertSucceeds(getDoc(doc(bob, 'simpaticoAnswers/alice')));
  });

  await check('simpaticoAnswers: owner can write v2Answers', () =>
    assertSucceeds(setDoc(doc(alice, 'simpaticoAnswers/alice'), {
      userID: 'alice', v2Answers: { saturday: { answer: 'outdoors', acceptable: ['outdoors'] } },
    }, { merge: true })));

  await check('simpaticoAnswers: owner can write v2CompletedAt', () =>
    assertSucceeds(setDoc(doc(alice, 'simpaticoAnswers/alice'), {
      v2CompletedAt: new Date(),
    }, { merge: true })));

  await check('simpaticoAnswers: cannot write someone else\'s questionnaire', () =>
    assertFails(setDoc(doc(alice, 'simpaticoAnswers/bob'), { userID: 'bob' })));

  // ============================================================
  // SHOW-UP REPORTS
  // ============================================================
  await check('showUpReports: reporter can create their own report', () =>
    assertSucceeds(setDoc(doc(alice, 'showUpReports/r1'), {
      reporterID: 'alice', reportedUserID: 'bob', planID: 't1', didShowUp: true, createdAt: new Date(),
    })));

  await check('showUpReports: cannot read reports', async () => {
    await seed((db) => setDoc(doc(db, 'showUpReports/r2'), {
      reporterID: 'alice', reportedUserID: 'bob', planID: 't1', didShowUp: true, createdAt: new Date(),
    }));
    await assertFails(getDoc(doc(alice, 'showUpReports/r2')));
  });

  // ============================================================
  // WAITLIST SIGNUPS
  // ============================================================
  await check('waitlistSignups: unauthenticated signup with a valid email', () =>
    assertSucceeds(setDoc(doc(anon, 'waitlistSignups/w1'), {
      email: 'friend@example.com', submittedAt: new Date(),
    })));

  await check('waitlistSignups: rejects a malformed email', () =>
    assertFails(setDoc(doc(anon, 'waitlistSignups/w2'), {
      email: 'not-an-email', submittedAt: new Date(),
    })));

  await check('waitlistSignups: rejects extra fields', () =>
    assertFails(setDoc(doc(anon, 'waitlistSignups/w3'), {
      email: 'friend@example.com', submittedAt: new Date(), phone: '5125551234',
    })));

  // ============================================================
  // GROUP PLANS (new)
  // ============================================================
  const validGroupPlan = (overrides = {}) => ({
    hostID: 'alice',
    inviteeIDs: ['bob', 'carol'],
    responses: { bob: 'invited', carol: 'invited' },
    activity: { id: 'a1', name: 'Board games', isUserAdded: false, createdAt: new Date() },
    date: new Date(Date.now() + 86400000),
    status: 'active',
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  });

  await check('groupPlans: host can create with a non-empty invitee list, all invited', () =>
    assertSucceeds(setDoc(doc(alice, 'groupPlans/g1'), validGroupPlan())));

  await check('groupPlans: cannot create on someone else\'s behalf as host', () =>
    assertFails(setDoc(doc(bob, 'groupPlans/g2'), validGroupPlan())));

  await check('groupPlans: cannot create with an empty invitee list', () =>
    assertFails(setDoc(doc(alice, 'groupPlans/g3'), validGroupPlan({ inviteeIDs: [], responses: {} }))));

  await check('groupPlans: cannot create with a response that isn\'t "invited"', () =>
    assertFails(setDoc(doc(alice, 'groupPlans/g4'), validGroupPlan({ responses: { bob: 'going', carol: 'invited' } }))));

  await check('groupPlans: host can read their own plan', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g5'), validGroupPlan()));
    await assertSucceeds(getDoc(doc(alice, 'groupPlans/g5')));
  });

  await check('groupPlans: invitee can read', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g6'), validGroupPlan()));
    await assertSucceeds(getDoc(doc(bob, 'groupPlans/g6')));
  });

  await check('groupPlans: a stranger cannot read', async () => {
    const dave = testEnv.authenticatedContext('dave').firestore();
    await seed((db) => setDoc(doc(db, 'groupPlans/g7'), validGroupPlan()));
    await assertFails(getDoc(doc(dave, 'groupPlans/g7')));
  });

  await check('groupPlans: host can update any field except hostID (e.g. reschedule)', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g8'), validGroupPlan()));
    await assertSucceeds(updateDoc(doc(alice, 'groupPlans/g8'), {
      date: new Date(Date.now() + 2 * 86400000), updatedAt: new Date(),
    }));
  });

  await check('groupPlans: host cannot change hostID', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g9'), validGroupPlan()));
    await assertFails(updateDoc(doc(alice, 'groupPlans/g9'), { hostID: 'carol' }));
  });

  await check('groupPlans: host can cancel (status field)', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g10'), validGroupPlan()));
    await assertSucceeds(updateDoc(doc(alice, 'groupPlans/g10'), { status: 'cancelled', updatedAt: new Date() }));
  });

  await check('groupPlans: invitee can set their own response to going', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g11'), validGroupPlan()));
    await assertSucceeds(updateDoc(doc(bob, 'groupPlans/g11'), {
      'responses.bob': 'going', updatedAt: new Date(),
    }));
  });

  await check('groupPlans: invitee can set their own response to cantMake', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g12'), validGroupPlan()));
    await assertSucceeds(updateDoc(doc(carol, 'groupPlans/g12'), {
      'responses.carol': 'cantMake', updatedAt: new Date(),
    }));
  });

  await check('groupPlans: invitee cannot set their response to an arbitrary value', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g13'), validGroupPlan()));
    await assertFails(updateDoc(doc(bob, 'groupPlans/g13'), {
      'responses.bob': 'maybe', updatedAt: new Date(),
    }));
  });

  await check('groupPlans: invitee cannot touch another invitee\'s response', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g14'), validGroupPlan()));
    await assertFails(updateDoc(doc(bob, 'groupPlans/g14'), {
      'responses.carol': 'going', updatedAt: new Date(),
    }));
  });

  await check('groupPlans: invitee cannot touch an unrelated field while responding', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g15'), validGroupPlan()));
    await assertFails(updateDoc(doc(bob, 'groupPlans/g15'), {
      'responses.bob': 'going', activity: { id: 'a2', name: 'Hacked', isUserAdded: false, createdAt: new Date() },
    }));
  });

  await check('groupPlans: a non-invitee, non-host cannot update at all', async () => {
    const dave = testEnv.authenticatedContext('dave').firestore();
    await seed((db) => setDoc(doc(db, 'groupPlans/g16'), validGroupPlan()));
    await assertFails(updateDoc(doc(dave, 'groupPlans/g16'), { 'responses.dave': 'going' }));
  });

  await check('groupPlans: cannot delete, even as host', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g17'), validGroupPlan()));
    await assertFails(deleteDoc(doc(alice, 'groupPlans/g17')));
  });

  await check('groupPlans: host list query (whereField hostID) returns only their plans', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g18'), validGroupPlan()));
    const q = query(collection(alice, 'groupPlans'), where('hostID', '==', 'alice'));
    await assertSucceeds(getDocs(q));
  });

  await check('groupPlans: invitee list query (arrayContains) returns their invites', async () => {
    await seed((db) => setDoc(doc(db, 'groupPlans/g19'), validGroupPlan()));
    const q = query(collection(bob, 'groupPlans'), where('inviteeIDs', 'array-contains', 'bob'));
    await assertSucceeds(getDocs(q));
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
