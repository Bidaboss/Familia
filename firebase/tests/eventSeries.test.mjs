// « Événements qui reviennent » (PRD, récit 54): eventSeries/{seriesId} says what repeats and how;
// the server writes the occurrences as events with seriesId. Fictional family of helpers.mjs:
// adult1 and adult2 in h1, admin1 (admin principal), adult3 in h2, kid1 (child of adult1).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

const rule = (overrides = {}) => ({ freq: 'weekly', interval: 1, monthlyBy: null, start: '2026-12-06', until: null, count: null, ...overrides });
const series = (by, overrides = {}) => ({
  title: 'Marché', time: '10:00', place: null, note: null,
  targetType: 'family', targetMemberId: null, householdId: null,
  authorId: by.memberId, authorName: by.memberId,
  rule: rule(), exdates: [], applyFrom: null,
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
});
const occurrence = {
  title: 'Marché', date: '2026-12-13', time: '10:00', place: null, note: null,
  targetType: 'family', targetMemberId: null, householdId: null,
  authorId: 'adult1', authorName: 'adult1',
  seriesId: 's1', occurrence: '2026-12-13', bySeries: 3,
  createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
};

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'events/s1_20261213'), occurrence);
  });
});

const ref = (db, id = 's1') => doc(db, `eventSeries/${id}`);

async function seedSeries(overrides = {}) {
  await env.withSecurityRulesDisabled((ctx) => setDoc(ref(ctx.firestore()), {
    ...series(ADULT), createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0), ...overrides,
  }));
}

describe('« @Prénom » in the note of a series (l’admin, 19/11)', () => {
  test('a series names members like an event', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(ref(db, 'm1'), series(ADULT, { note: '@Noé', mentionIds: [OTHER_ADULT.memberId] })));
    await assertFails(setDoc(ref(db, 'm2'), series(ADULT, { mentionIds: ['a', 'a'] })));
  });
});

describe('eventSeries', () => {
  test('a member creates a series every week, every 2 weeks, every month or every year', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(ref(db, 'a'), series(ADULT)));
    await assertSucceeds(setDoc(ref(db, 'b'), series(ADULT, { rule: rule({ interval: 2, count: 10 }) })));
    await assertSucceeds(setDoc(ref(db, 'c'), series(ADULT, { rule: rule({ freq: 'monthly', monthlyBy: 'weekday', until: '2027-06-30' }) })));
    await assertSucceeds(setDoc(ref(db, 'd'), series(ADULT, { rule: rule({ freq: 'monthly', monthlyBy: 'date' }) })));
    await assertSucceeds(setDoc(ref(db, 'e'), series(ADULT, { rule: rule({ freq: 'yearly' }) })));
    await assertSucceeds(getDoc(ref(as(env, NEIGHBOUR), 'a')));
    await assertFails(getDoc(ref(as(env, STRANGER), 'a')));
  });

  test('an ill-formed rule is refused', async () => {
    const db = as(env, ADULT);
    for (const bad of [
      rule({ freq: 'daily' }),
      rule({ interval: 3 }),
      rule({ freq: 'monthly', interval: 2, monthlyBy: 'date' }),
      rule({ freq: 'monthly' }),
      rule({ monthlyBy: 'date' }),
      rule({ until: '2026-12-01' }),
      rule({ count: 1 }),
      rule({ count: 201 }),
      rule({ until: '2027-01-01', count: 5 }),
      { ...rule(), byDay: 'SU' },
    ]) {
      await assertFails(setDoc(ref(db, 'x'), series(ADULT, { rule: bad })));
    }
    await assertFails(setDoc(ref(db, 'x'), series(ADULT, { exdates: ['2026-12-13'] })));
    await assertFails(setDoc(ref(db, 'x'), series(ADULT, { title: '' })));
  });

  test('the same targets as an event: never for someone else, a child only for themselves', async () => {
    await assertFails(setDoc(ref(as(env, ADULT), 'x'), series(OTHER_ADULT)));
    await assertFails(setDoc(ref(as(env, NEIGHBOUR), 'x'), series(NEIGHBOUR, { targetType: 'household', householdId: 'h1' })));
    await assertFails(setDoc(ref(as(env, KID), 'x'), series(KID)));
    await assertSucceeds(setDoc(ref(as(env, KID), 'y'), series(KID, { targetType: 'self', targetMemberId: 'kid1' })));
  });

  test('those who manage it change it, take a day out, or end it', async () => {
    await seedSeries();
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(ref(db), { title: 'Marché du dimanche', applyFrom: '2026-12-20', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(ref(db), { exdates: ['2026-12-27'], updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(ref(db), { rule: rule({ until: '2027-03-01' }), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref(as(env, NEIGHBOUR)), { title: 'x', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref(db), { authorId: 'adult2', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(ref(as(env, ADMIN)), { note: 'Prends un panier', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(ref(as(env, NEIGHBOUR))));
    await assertSucceeds(deleteDoc(ref(db)));
  });

  test('a series takes the place of récit 74, an unknown household is refused', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(ref(db, 'p1'), series(ADULT, { place: 'Foyer 1', placeKind: 'household', placeHouseholdId: 'h1' })));
    await assertSucceeds(setDoc(ref(db, 'p2'), series(ADULT, { place: '3 rue des Lilas', placeKind: 'address' })));
    await assertFails(setDoc(ref(db, 'p3'), series(ADULT, { place: 'Foyer 9', placeKind: 'household', placeHouseholdId: 'h9' })));
  });
});

describe('the occurrences of a series', () => {
  const occ = (db) => doc(db, 'events/s1_20261213');

  test('nobody writes one from the app', async () => {
    const db = as(env, ADULT);
    const fresh = { ...occurrence, createdAt: serverTimestamp(), updatedAt: serverTimestamp() };
    await assertFails(setDoc(doc(db, 'events/s1_20261220'), { ...fresh, date: '2026-12-20', occurrence: '2026-12-20' }));
    const { seriesId, occurrence: o, bySeries, ...plain } = fresh;
    await assertFails(setDoc(doc(db, 'events/n1'), { ...plain, detached: true }));
    await assertSucceeds(setDoc(doc(db, 'events/n2'), plain));
  });

  test('« Cette fois seulement » changes it and marks it detached, its series kept', async () => {
    const db = as(env, ADULT);
    await assertFails(updateDoc(occ(db), { time: '11:00', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(occ(db), { time: '11:00', detached: true, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(occ(db), { seriesId: 's2', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(occ(db), { occurrence: '2026-12-14', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(occ(db), { bySeries: 4, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(occ(db), { detached: false, updatedAt: serverTimestamp() }));
  });

  test('a plain event never gains series fields', async () => {
    const { seriesId, occurrence: o, bySeries, ...plain } = occurrence;
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'events/p1'), plain));
    const db = as(env, ADULT);
    await assertFails(updateDoc(doc(db, 'events/p1'), { detached: true, updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db, 'events/p1'), { time: '12:00', updatedAt: serverTimestamp() }));
  });
});
