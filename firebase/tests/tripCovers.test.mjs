// Rules of « Autre photo » (demande de l'admin du 25/11 soir): the photo chosen for the cover of a
// destination, shared with the whole family. Fictional family: adult1 creates the trip, adult3
// takes part, adult2 only follows it.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'trips/t1'), {
      title: 'Corse',
      startDate: '2027-07-10',
      endDate: '2027-07-20',
      participantIds: [ADULT.memberId, NEIGHBOUR.memberId],
      readerIds: ['admin1', 'adult1', 'adult2', 'adult3'],
      creatorId: ADULT.memberId,
      creatorName: ADULT.memberId,
      createdAt: Timestamp.fromMillis(0),
      updatedAt: Timestamp.fromMillis(0),
    });
    await setDoc(doc(db, 'trips/t1/steps/s1'), { type: 'lodging', place: 'Ajaccio', departDate: '2027-07-10' });
  });
});

/** A valid chosen photo, signed by [who]. */
function cover(who, overrides = {}) {
  return {
    url: 'https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Port.jpg/960px-Port.jpg',
    credit: 'Camille, CC BY-SA 4.0, Wikimedia Commons',
    byId: who.memberId,
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

async function storedCover() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'trips/t1/covers/s1'), { ...cover(ADULT), updatedAt: Timestamp.fromMillis(0) });
  });
}

describe('chosen cover photo of a destination', () => {
  test('each participant chooses another photo, signed with their own id', async () => {
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/covers/s1'), cover(NEIGHBOUR)));
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'trips/t1/covers/s1'), cover(ADULT)));
    await assertFails(setDoc(doc(as(env, ADULT), 'trips/t1/covers/s1'), cover(NEIGHBOUR)));
  });

  test('the followers read it; a follower who does not take part chooses nothing', async () => {
    await storedCover();
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(getDoc(doc(db, 'trips/t1/covers/s1')));
    await assertFails(setDoc(doc(db, 'trips/t1/covers/s1'), cover(OTHER_ADULT)));
    await assertFails(deleteDoc(doc(db, 'trips/t1/covers/s1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'trips/t1/covers/s1')));
  });

  test('only a photo of Wikimedia Commons, with a credit, under an existing destination', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'trips/t1/covers/s1'), cover(ADULT, { url: 'https://exemple.fr/photo.jpg' })));
    await assertFails(setDoc(doc(db, 'trips/t1/covers/s1'), cover(ADULT, { credit: '' })));
    await assertFails(setDoc(doc(db, 'trips/t1/covers/s1'), cover(ADULT, { note: 'en plus' })));
    await assertFails(setDoc(doc(db, 'trips/t1/covers/s1'), cover(ADULT, { updatedAt: Timestamp.fromMillis(0) })));
    await assertFails(setDoc(doc(db, 'trips/t1/covers/nowhere'), cover(ADULT)));
  });

  test('the photo of the place named by the trip goes under « _trip », never under another missing destination (récit 124)', async () => {
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/covers/_trip'), cover(NEIGHBOUR)));
    await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), 'trips/t1/covers/_trip')));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'trips/t1/covers/_trip'), cover(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, ADULT), 'trips/t1/covers/trip'), cover(ADULT)));
    await assertFails(setDoc(doc(as(env, ADULT), 'trips/t1/covers/_trip'), cover(ADULT, { url: 'https://example.com/a.jpg' })));
  });

  test('a participant or the admin principal removes it', async () => {
    await storedCover();
    await assertSucceeds(deleteDoc(doc(as(env, NEIGHBOUR), 'trips/t1/covers/s1')));
    await storedCover();
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'trips/t1/covers/s1')));
  });
});
