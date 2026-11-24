// Rules of the album of an event (PRD, récit 41). Fictional family: admin1 (admin principal),
// adult1 and adult2 in h1, adult3 in h2.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDocs, serverTimestamp, setDoc, Timestamp } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

/** e1 by ADULT, already past; e2 by ADULT, far in the future. adult2 and adult3 said « je viens » to both. */
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const event = (date) => ({
      title: 'Repas', date, time: null, place: null, note: null, targetType: 'family', targetMemberId: null,
      householdId: null, authorId: 'adult1', authorName: 'adult1', createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
    await setDoc(doc(db, 'events/e1'), event('2026-01-10'));
    await setDoc(doc(db, 'events/e2'), event('2099-01-01'));
    for (const e of ['e1', 'e2']) {
      await setDoc(doc(db, `events/${e}/rsvps/adult2`), { status: 'yes', answeredBy: 'adult2', updatedAt: Timestamp.fromMillis(0) });
      await setDoc(doc(db, `events/${e}/rsvps/adult3`), { status: 'maybe', answeredBy: 'adult3', updatedAt: Timestamp.fromMillis(0) });
    }
  });
});

const photo = (author, slot, overrides = {}) => ({
  authorId: author.memberId, authorName: author.memberId, slot, createdAt: serverTimestamp(), ...overrides,
});

describe('event album (récit 41)', () => {
  test('a member who said « je viens » adds photos from the day of the event, and the family sees them', async () => {
    await assertSucceeds(setDoc(doc(as(env, OTHER_ADULT), 'events/e1/photos/adult2_1'), photo(OTHER_ADULT, 1)));
    await assertSucceeds(getDocs(collection(as(env, NEIGHBOUR), 'events/e1/photos')));
    await assertFails(getDocs(collection(as(env, STRANGER), 'events/e1/photos')));
  });

  test('not before the day of the event', async () => {
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'events/e2/photos/adult2_1'), photo(OTHER_ADULT, 1)));
  });

  test('only those who said « je viens », in their own name, with one of the 30 places', async () => {
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'events/e1/photos/adult3_1'), photo(NEIGHBOUR, 1)));
    await assertFails(setDoc(doc(as(env, ADULT), 'events/e1/photos/adult1_1'), photo(ADULT, 1)));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'events/e1/photos/adult2_31'), photo(OTHER_ADULT, 31)));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'events/e1/photos/adult1_1'), photo(ADULT, 1)));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'events/e1/photos/adult2_1'), photo(OTHER_ADULT, 1, { place: 'Ajaccio' })));
    // stepId is for the photos of a trip only (récit 104).
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'events/e1/photos/adult2_1'), photo(OTHER_ADULT, 1, { stepId: 's1' })));
  });

  test('the author of the photo, the author of the event or the admin principal removes it', async () => {
    for (const slot of [1, 2, 3]) {
      await setDoc(doc(as(env, OTHER_ADULT), `events/e1/photos/adult2_${slot}`), photo(OTHER_ADULT, slot));
    }
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'events/e1/photos/adult2_1')));
    await assertSucceeds(deleteDoc(doc(as(env, OTHER_ADULT), 'events/e1/photos/adult2_1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'events/e1/photos/adult2_2')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'events/e1/photos/adult2_3')));
  });
});
