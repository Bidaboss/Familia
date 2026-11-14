// Lot 4 rules: events and the PRD "Droits par profil". Fictional family:
// admin1 (admin), adult1 and adult2 in h1, adult3 in h2, kid1 (child with the app, parent adult1),
// kid2 (child without the app, parents adult2 and admin1).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc, Timestamp } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const ADULT3 = { memberId: 'adult3', email: 'adult3@example.com', uid: 'uid-adult3' };
const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'households/h2'), { name: 'Foyer 2' });
    await setDoc(doc(db, `memberEmails/${ADULT3.email}`), { memberId: ADULT3.memberId });
    await setDoc(doc(db, `members/${ADULT3.memberId}`), adultDoc(ADULT3, { householdId: 'h2' }));
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'members/kid2'), adultDoc({ memberId: 'kid2' }, {
      email: null, profile: 'childWithoutApp', parentIds: ['adult2', 'admin1'],
    }));
  });
});

/** A valid new event by [author]; override the target or any field. */
function event(author, overrides = {}) {
  return {
    title: 'Repas',
    date: '2026-12-24',
    time: '19:30',
    place: null,
    note: null,
    targetType: 'family',
    targetMemberId: null,
    householdId: null,
    authorId: author.memberId,
    authorName: author.memberId,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

/** Stores an event directly, as if [author] had created it earlier. */
async function stored(id, author, overrides = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `events/${id}`), {
      ...event(author, overrides),
      createdAt: Timestamp.fromMillis(0),
      updatedAt: Timestamp.fromMillis(0),
    });
  });
}

const self = (m) => ({ targetType: 'self', targetMemberId: m.memberId });
const forChild = (id) => ({ targetType: 'child', targetMemberId: id });
const forHousehold = (id) => ({ targetType: 'household', householdId: id });

describe('reading', () => {
  test('every member reads the planning, a stranger does not', async () => {
    await stored('e1', ADULT);
    await assertSucceeds(getDoc(doc(as(env, KID), 'events/e1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'events/e1')));
  });
});

describe('creating', () => {
  test('an adult creates for themselves, their household, the family and their child', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'events/a'), event(ADULT, self(ADULT))));
    await assertSucceeds(setDoc(doc(db, 'events/b'), event(ADULT, forHousehold('h1'))));
    await assertSucceeds(setDoc(doc(db, 'events/c'), event(ADULT)));
    await assertSucceeds(setDoc(doc(db, 'events/d'), event(ADULT, forChild('kid1'))));
  });

  test('an adult cannot create for another household or for someone else\'s child', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'events/a'), event(ADULT, forHousehold('h2'))));
    await assertFails(setDoc(doc(db, 'events/b'), event(ADULT, forChild('kid2'))));
    await assertFails(setDoc(doc(db, 'events/c'), event(ADULT, forChild('adult2'))));
  });

  test('a child with the app creates for themselves only', async () => {
    const db = as(env, KID);
    await assertSucceeds(setDoc(doc(db, 'events/a'), event(KID, self(KID))));
    await assertFails(setDoc(doc(db, 'events/b'), event(KID)));
    await assertFails(setDoc(doc(db, 'events/c'), event(KID, forHousehold('h1'))));
  });

  test('an admin creates for any household or child', async () => {
    const db = as(env, ADMIN);
    await assertSucceeds(setDoc(doc(db, 'events/a'), event(ADMIN, forHousehold('h2'))));
    await assertSucceeds(setDoc(doc(db, 'events/b'), event(ADMIN, forChild('kid1'))));
  });

  test('nobody creates in someone else\'s name', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'events/a'), event(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, STRANGER), 'events/b'), event({ memberId: 'stranger' })));
  });

  test('the author name is the author\'s own first name', async () => {
    await assertFails(setDoc(doc(as(env, KID), 'events/a'), event(KID, { ...self(KID), authorName: 'Maman' })));
  });

  test('a title and a date are required, and the shape is checked', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'events/a'), event(ADULT, { title: '' })));
    await assertFails(setDoc(doc(db, 'events/b'), event(ADULT, { date: null })));
    await assertFails(setDoc(doc(db, 'events/c'), event(ADULT, { date: '24/12/2026' })));
    await assertFails(setDoc(doc(db, 'events/d'), event(ADULT, { time: '25:00' })));
    await assertFails(setDoc(doc(db, 'events/e'), event(ADULT, { extra: true })));
    await assertFails(setDoc(doc(db, 'events/f'), event(ADULT, { createdAt: Timestamp.fromMillis(0) })));
    await assertFails(setDoc(doc(db, 'events/g'), event(ADULT, { targetType: 'self', targetMemberId: 'adult2' })));
  });
});

describe('editing and deleting', () => {
  test('the author edits and deletes their event', async () => {
    await stored('e1', OTHER_ADULT);
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(updateDoc(doc(db, 'events/e1'), { title: 'Goûter', updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'events/e1')));
  });

  test('another adult cannot edit or delete it', async () => {
    await stored('e1', OTHER_ADULT);
    const db = as(env, ADULT3);
    await assertFails(updateDoc(doc(db, 'events/e1'), { title: 'Goûter', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db, 'events/e1')));
  });

  test('an admin edits and deletes any event', async () => {
    await stored('e1', OTHER_ADULT);
    const db = as(env, ADMIN);
    await assertSucceeds(updateDoc(doc(db, 'events/e1'), { title: 'Goûter', updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'events/e1')));
  });

  test('a parent manages the events their child wrote and those written for their child', async () => {
    await stored('byKid', KID, self(KID));
    await stored('forKid', ADMIN, forChild('kid1'));
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'events/byKid'), { time: null, updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'events/forKid')));
  });

  test('a child cannot touch an adult\'s event, even outside the app', async () => {
    await stored('e1', ADULT);
    const db = as(env, KID);
    await assertFails(updateDoc(doc(db, 'events/e1'), { title: 'Piscine', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db, 'events/e1')));
  });

  test('a child cannot widen their own event to the whole family', async () => {
    await stored('e1', KID, self(KID));
    await assertFails(updateDoc(doc(as(env, KID), 'events/e1'), {
      targetType: 'family', targetMemberId: null, updatedAt: serverTimestamp(),
    }));
  });

  test('the author, author name and creation time never change', async () => {
    await stored('e1', ADULT);
    const db = as(env, ADULT);
    await assertFails(updateDoc(doc(db, 'events/e1'), { authorId: 'adult2', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'events/e1'), { authorName: 'Autre', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'events/e1'), { createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'events/e1'), { title: 'Sans date de modif' }));
  });

  test('the events of a removed member stay readable, and an admin can still manage them', async () => {
    await stored('e1', OTHER_ADULT);
    await env.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore(), 'members/adult2'));
      await deleteDoc(doc(ctx.firestore(), 'memberEmails/adult2@example.com'));
    });
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'events/e1')));
    await assertFails(deleteDoc(doc(as(env, ADULT), 'events/e1')));
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'events/e1'), { note: 'Gardé', updatedAt: serverTimestamp() }));
  });
});
