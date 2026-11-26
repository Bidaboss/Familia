// Rules of the places programmed under a destination (récit 103): an accommodation, a restaurant
// or a spot, read by the followers of the trip and written by its participants. Fictional family:
// adult1 creates the trip, adult3 takes part, adult2 and admin1 (admin principal) only follow it.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
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
    await setDoc(doc(db, 'trips/t1/steps/s2'), { type: 'lodging', place: 'Bastia', departDate: '2027-07-15' });
  });
});

/** A valid place under the destination s1. */
function place(overrides = {}) {
  return {
    stepId: 's1',
    kind: 'restaurant',
    name: 'Le Bistrot du Port',
    address: 'Quai Napoléon, Ajaccio',
    lat: 41.9192,
    lng: 8.7386,
    arriveTime: '20:00',
    note: 'Table au nom de Camille',
    cost: 80,
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

async function storedPlace(id, overrides = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `trips/t1/places/${id}`), { ...place(overrides), updatedAt: Timestamp.fromMillis(0) });
  });
}

describe('places of a destination (récit 103)', () => {
  test('each participant adds, changes and deletes a place', async () => {
    const db = as(env, NEIGHBOUR);
    await assertSucceeds(setDoc(doc(db, 'trips/t1/places/p1'), place()));
    await assertSucceeds(setDoc(doc(db, 'trips/t1/places/p1'), place({ name: 'Chez Paulo', arriveTime: null, note: null, cost: null })));
    await assertSucceeds(deleteDoc(doc(db, 'trips/t1/places/p1')));
  });

  test('the followers read them; a follower who does not take part writes nothing', async () => {
    await storedPlace('p1');
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(getDoc(doc(db, 'trips/t1/places/p1')));
    await assertFails(setDoc(doc(db, 'trips/t1/places/p2'), place()));
    await assertFails(updateDoc(doc(db, 'trips/t1/places/p1'), { note: 'Rien', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db, 'trips/t1/places/p1')));
  });

  test('someone who does not follow the trip reads nothing', async () => {
    await storedPlace('p1');
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'trips/t1'), { readerIds: ['adult1', 'adult3'] });
    });
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), 'trips/t1/places/p1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'trips/t1/places/p1')));
  });

  test('the admin principal can remove a place', async () => {
    await storedPlace('p1');
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'trips/t1/places/p1')));
  });

  test('a place is under an existing destination, and stays under it', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'trips/t1/places/p1'), place({ stepId: 'nowhere' })));
    await storedPlace('p2');
    await assertFails(setDoc(doc(db, 'trips/t1/places/p2'), place({ stepId: 's2' })));
  });

  test('the shape is checked', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'trips/t1/places/ok1'), place({ kind: 'lodging', address: null, lat: null, lng: null })));
    await assertSucceeds(setDoc(doc(db, 'trips/t1/places/ok2'), place({ kind: 'spot', note: 'a'.repeat(600), cost: 0 })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/a'), place({ kind: 'museum' })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/b'), place({ name: '' })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/c'), place({ name: 'a'.repeat(121) })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/d'), place({ arriveTime: '20h' })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/e'), place({ note: 'a'.repeat(601) })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/f'), place({ cost: -1 })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/g'), place({ cost: '80' })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/h'), place({ lat: 41.9, lng: null })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/i'), place({ lat: 91 })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/j'), place({ rating: 5 })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/k'), place({ updatedAt: Timestamp.fromMillis(0) })));
    const { note, ...noNote } = place();
    await assertFails(setDoc(doc(db, 'trips/t1/places/l'), noNote));
  });
});

describe('the day of a place and the nights of an accommodation (récit 122)', () => {
  test('a restaurant or a spot takes a day, or none (« À caser »)', async () => {
    const db = as(env, NEIGHBOUR);
    await assertSucceeds(setDoc(doc(db, 'trips/t1/places/p1'), place({ day: '2027-07-12' })));
    await assertSucceeds(setDoc(doc(db, 'trips/t1/places/p2'), place({ kind: 'spot' })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/p3'), place({ day: '12/07/2027' })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/p4'), place({ day: null })));
  });

  test('an accommodation takes its nights, never a day', async () => {
    const db = as(env, NEIGHBOUR);
    await assertSucceeds(setDoc(doc(db, 'trips/t1/places/h1'), place({ kind: 'lodging', checkIn: '2027-07-10', checkOut: '2027-07-13' })));
    await assertSucceeds(setDoc(doc(db, 'trips/t1/places/h2'), place({ kind: 'lodging' })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/h3'), place({ kind: 'lodging', checkIn: '2027-07-13', checkOut: '2027-07-13' })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/h4'), place({ kind: 'lodging', day: '2027-07-12' })));
    await assertFails(setDoc(doc(db, 'trips/t1/places/p5'), place({ checkIn: '2027-07-10' })));
  });

  test('a follower still cannot set a day', async () => {
    await storedPlace('p1');
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'trips/t1/places/p1'), { day: '2027-07-12', updatedAt: serverTimestamp() }));
  });
});
