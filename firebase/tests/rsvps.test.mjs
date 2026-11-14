// Lot 6 rules: "qui vient". Each member answers for themselves, each adult also for their
// children (PRD, récit 4). Fictional family: adult1 and adult2 in h1, admin1 (admin),
// kid1 (child with the app, parent adult1), kid2 (child without the app, parents adult2 and admin1).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'members/kid2'), adultDoc({ memberId: 'kid2' }, {
      email: null, profile: 'childWithoutApp', parentIds: ['adult2', 'admin1'],
    }));
    await setDoc(doc(db, 'events/e1'), {
      title: 'Repas', date: '2026-12-24', time: null, place: null, note: null,
      targetType: 'family', targetMemberId: null, householdId: null,
      authorId: 'adult2', authorName: 'adult2',
      createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
  });
});

/** A valid answer written by [by]. */
function answer(by, status = 'yes', overrides = {}) {
  return { status, answeredBy: by.memberId, updatedAt: serverTimestamp(), ...overrides };
}

const rsvp = (db, memberId, eventId = 'e1') => doc(db, `events/${eventId}/rsvps/${memberId}`);

describe('answering for oneself', () => {
  test('a member answers « Je viens », then changes their mind', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(rsvp(db, 'adult1'), answer(ADULT, 'yes')));
    await assertSucceeds(setDoc(rsvp(db, 'adult1'), answer(ADULT, 'maybe')));
    await assertSucceeds(setDoc(rsvp(db, 'adult1'), answer(ADULT, 'no')));
  });

  test('a child with the app answers for themselves', async () => {
    await assertSucceeds(setDoc(rsvp(as(env, KID), 'kid1'), answer(KID)));
  });

  test('a member withdraws their answer', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(rsvp(db, 'adult1'), answer(ADULT)));
    await assertSucceeds(deleteDoc(rsvp(db, 'adult1')));
  });

  test('every member reads the answers, a stranger does not', async () => {
    await assertSucceeds(setDoc(rsvp(as(env, ADULT), 'adult1'), answer(ADULT)));
    await assertSucceeds(getDoc(rsvp(as(env, KID), 'adult1')));
    await assertFails(getDoc(rsvp(as(env, STRANGER), 'adult1')));
  });
});

describe('answering for someone else', () => {
  test('an adult answers for their child, with or without the app', async () => {
    await assertSucceeds(setDoc(rsvp(as(env, ADULT), 'kid1'), answer(ADULT)));
    await assertSucceeds(setDoc(rsvp(as(env, OTHER_ADULT), 'kid2'), answer(OTHER_ADULT, 'maybe')));
  });

  test('a second responsible adult answers too, and may withdraw the answer', async () => {
    await assertSucceeds(setDoc(rsvp(as(env, OTHER_ADULT), 'kid2'), answer(OTHER_ADULT)));
    await assertSucceeds(deleteDoc(rsvp(as(env, ADMIN), 'kid2')));
  });

  test('an adult cannot answer for another adult, nor for a child who is not theirs', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(rsvp(db, 'adult2'), answer(ADULT)));
    await assertFails(setDoc(rsvp(db, 'kid2'), answer(ADULT)));
  });

  test('an admin does not answer for others either', async () => {
    await assertFails(setDoc(rsvp(as(env, ADMIN), 'adult1'), answer(ADMIN)));
  });

  test('a child cannot answer for a parent', async () => {
    await assertFails(setDoc(rsvp(as(env, KID), 'adult1'), answer(KID)));
  });

  test('nobody removes the answer of someone they do not answer for', async () => {
    await assertSucceeds(setDoc(rsvp(as(env, OTHER_ADULT), 'adult2'), answer(OTHER_ADULT)));
    await assertFails(deleteDoc(rsvp(as(env, ADULT), 'adult2')));
  });
});

describe('shape of an answer', () => {
  test('the answer names who wrote it, truthfully', async () => {
    await assertFails(setDoc(rsvp(as(env, ADULT), 'kid1'), answer(ADULT, 'yes', { answeredBy: 'kid1' })));
  });

  test('only the three answers of the PRD are accepted', async () => {
    await assertFails(setDoc(rsvp(as(env, ADULT), 'adult1'), answer(ADULT, 'perhaps')));
  });

  test('no other field, and the time is the server time', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(rsvp(db, 'adult1'), answer(ADULT, 'yes', { guests: 3 })));
    await assertFails(setDoc(rsvp(db, 'adult1'), answer(ADULT, 'yes', { updatedAt: Timestamp.fromMillis(0) })));
  });

  test('no answer for an event or a member that does not exist', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(rsvp(db, 'adult1', 'nope'), answer(ADULT)));
    await assertFails(setDoc(rsvp(db, 'ghost'), answer(ADULT)));
  });
});
