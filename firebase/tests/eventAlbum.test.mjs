// Rules of the album of an event (PRD, récit 41). Fictional family: admin1 (admin principal),
// admin2 (chef de famille of h1), adult1 and adult2 in h1, adult3 in h2, kid1 a child of adult2 in h1.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDocs, serverTimestamp, setDoc, Timestamp } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, SECOND_ADMIN, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

/** e1 by ADULT, already past; e2 by ADULT, far in the future. adult2 and kid1 said « je viens » to both, adult3 « peut-être ». */
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult2'] }));
    const event = (date) => ({
      title: 'Repas', date, time: null, place: null, note: null, targetType: 'family', targetMemberId: null,
      householdId: null, authorId: 'adult1', authorName: 'adult1', createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
    await setDoc(doc(db, 'events/e1'), event('2026-01-10'));
    await setDoc(doc(db, 'events/e2'), event('2099-01-01'));
    for (const e of ['e1', 'e2']) {
      await setDoc(doc(db, `events/${e}/rsvps/adult2`), { status: 'yes', answeredBy: 'adult2', updatedAt: Timestamp.fromMillis(0) });
      await setDoc(doc(db, `events/${e}/rsvps/adult3`), { status: 'maybe', answeredBy: 'adult3', updatedAt: Timestamp.fromMillis(0) });
      await setDoc(doc(db, `events/${e}/rsvps/kid1`), { status: 'yes', answeredBy: 'adult2', updatedAt: Timestamp.fromMillis(0) });
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

  test('the author of a photo or the admin principal removes it, never another adult (récit 121)', async () => {
    for (const slot of [1, 2]) {
      await setDoc(doc(as(env, OTHER_ADULT), `events/e1/photos/adult2_${slot}`), photo(OTHER_ADULT, slot));
    }
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'events/e1/photos/adult2_1')));
    // The author of the event, a chef de famille of the household, a child: not the photo of an adult.
    await assertFails(deleteDoc(doc(as(env, ADULT), 'events/e1/photos/adult2_1')));
    await assertFails(deleteDoc(doc(as(env, SECOND_ADMIN), 'events/e1/photos/adult2_1')));
    await assertFails(deleteDoc(doc(as(env, KID), 'events/e1/photos/adult2_1')));
    await assertSucceeds(deleteDoc(doc(as(env, OTHER_ADULT), 'events/e1/photos/adult2_1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'events/e1/photos/adult2_2')));
  });

  test('the responsible adults of a child remove its photos, nobody else (récit 121)', async () => {
    for (const slot of [1, 2, 3]) {
      await setDoc(doc(as(env, KID), `events/e1/photos/kid1_${slot}`), photo(KID, slot));
    }
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'events/e1/photos/kid1_1')));
    // The author of the event is neither its parent nor its chef de famille.
    await assertFails(deleteDoc(doc(as(env, ADULT), 'events/e1/photos/kid1_1')));
    await assertSucceeds(deleteDoc(doc(as(env, OTHER_ADULT), 'events/e1/photos/kid1_1')));
    await assertSucceeds(deleteDoc(doc(as(env, SECOND_ADMIN), 'events/e1/photos/kid1_2')));
    await assertSucceeds(deleteDoc(doc(as(env, KID), 'events/e1/photos/kid1_3')));
  });
});
