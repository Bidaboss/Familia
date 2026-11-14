// Lot 7, security audit: edge cases found while re-reading all the rules (docs/security-audit.md).
// Fictional family: admin1 and admin2 (admins), adult1 and adult2, all in h1.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collectionGroup, doc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc, writeBatch,
} from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, SECOND_ADMIN, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); await seed(env); });

const child = (parentIds, overrides = {}) =>
  adultDoc({ memberId: 'kid1' }, { email: null, profile: 'childWithoutApp', parentIds, ...overrides });

function event(author, overrides = {}) {
  return {
    title: 'Repas', date: '2026-12-24', time: null, place: null, note: null,
    targetType: 'family', targetMemberId: null, householdId: null,
    authorId: author.memberId, authorName: author.memberId,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}

describe('responsible adults', () => {
  test('an adult cannot be given responsible adults, who would then manage their events', async () => {
    const db = as(env, ADMIN);
    await assertFails(updateDoc(doc(db, 'members/adult1'), { parentIds: ['adult2'] }));
    await assertFails(setDoc(doc(db, 'members/new1'), adultDoc({ memberId: 'new1', email: null }, { parentIds: ['adult2'] })));
  });

  test('a child\'s responsible adults must be existing adults', async () => {
    const db = as(env, ADMIN);
    await assertFails(setDoc(doc(db, 'members/kid1'), child(['ghost'])));
    await assertSucceeds(setDoc(doc(db, 'members/kid1'), child(['adult1'])));
    await assertFails(setDoc(doc(db, 'members/kid2'), adultDoc({ memberId: 'kid2' }, {
      email: null, profile: 'childWithoutApp', parentIds: ['kid1'],
    })));
  });

  test('four responsible adults at most', async () => {
    const db = as(env, ADMIN);
    await assertSucceeds(setDoc(doc(db, 'members/kid1'), child(['adult1', 'adult2', 'admin1', 'admin2'])));
    await assertFails(setDoc(doc(db, 'members/kid1'), child(['adult1', 'adult2', 'admin1', 'admin2', 'adult1'])));
  });

  test('a new parent and their child can be added in the same batch', async () => {
    const db = as(env, ADMIN);
    const batch = writeBatch(db);
    const parent = { memberId: 'new1', email: 'new1@example.com' };
    batch.set(doc(db, 'members/new1'), adultDoc(parent));
    batch.set(doc(db, 'memberEmails/new1@example.com'), { memberId: 'new1' });
    batch.set(doc(db, 'members/kid1'), child(['new1']));
    await assertSucceeds(batch.commit());
  });
});

describe('dates', () => {
  test('an event on a day that does not exist is refused', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'events/e1'), event(ADULT, { date: '2026-13-01' })));
    await assertFails(setDoc(doc(db, 'events/e2'), event(ADULT, { date: '2026-12-32' })));
    await assertSucceeds(setDoc(doc(db, 'events/e3'), event(ADULT, { date: '2026-12-31' })));
  });

  test('a birth date on a day that does not exist is refused', async () => {
    const db = as(env, ADMIN);
    await assertFails(updateDoc(doc(db, 'members/adult1'), { birthDate: '1985-00-10' }));
    await assertSucceeds(updateDoc(doc(db, 'members/adult1'), { birthDate: '1985-01-10' }));
  });
});

describe('admin rights follow the family as it is now', () => {
  test('an admin removed from the role loses admin rights at once', async () => {
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'members/admin2'), { isAdmin: false }));
    await assertFails(updateDoc(doc(as(env, SECOND_ADMIN), 'members/adult1'), { birthDate: '1985-01-10' }));
  });
});

describe('answers of all events at once', () => {
  test('a member reads every answer with one query, a stranger does not', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'events/e1'), { ...event(OTHER_ADULT), createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0) });
      await setDoc(doc(db, 'events/e1/rsvps/adult2'), { status: 'yes', answeredBy: 'adult2', updatedAt: Timestamp.fromMillis(0) });
    });
    const snap = await assertSucceeds(getDocs(collectionGroup(as(env, ADULT), 'rsvps')));
    if (snap.size !== 1) throw new Error(`expected 1 answer, got ${snap.size}`);
    await assertFails(getDocs(collectionGroup(as(env, STRANGER), 'rsvps')));
  });

  test('the collection group opens reading only, never writing', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'other/x/rsvps/adult1'), { status: 'yes', answeredBy: 'adult1', updatedAt: serverTimestamp() }));
  });
});
