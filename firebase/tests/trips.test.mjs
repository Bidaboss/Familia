// V3 rules: trips, their followers, their steps and their checklist (PRD, récits 11 to 14). Fictional family:
// admin1 (admin principal) and adult1, adult2, admin2 in h1, adult3 in h2, kid1 (child with the app,
// parent adult1).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, Timestamp, updateDoc, where,
} from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

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
  });
});

const EVERYONE = ['admin1', 'admin2', 'adult1', 'adult2', 'adult3', 'kid1'];

/** A valid new trip by [creator], with adult3 taking part and everyone following it. */
function trip(creator, overrides = {}) {
  return {
    title: 'Corse',
    startDate: '2027-07-10',
    endDate: '2027-07-20',
    participantIds: [creator.memberId, 'adult3'],
    readerIds: EVERYONE,
    creatorId: creator.memberId,
    creatorName: creator.memberId,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

/** Stores a trip directly, as if [creator] had created it earlier. */
async function storedTrip(id, creator, overrides = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `trips/${id}`), {
      ...trip(creator, overrides),
      createdAt: Timestamp.fromMillis(0),
      updatedAt: Timestamp.fromMillis(0),
    });
  });
}

/** A valid step, not arrived yet. */
function step(overrides = {}) {
  return {
    type: 'flight',
    place: 'Ajaccio',
    departDate: '2027-07-10',
    departTime: '08:15',
    arriveDate: '2027-07-10',
    arriveTime: '09:40',
    note: null,
    remindAt: Timestamp.fromMillis(Date.UTC(2027, 6, 10, 10, 40)),
    arrivedAt: null,
    arrivedBy: null,
    arrivedByName: null,
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

async function storedStep(tripId, id, overrides = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `trips/${tripId}/steps/${id}`), { ...step(overrides), updatedAt: Timestamp.fromMillis(0) });
  });
}

const arrival = (m) => ({ arrivedAt: serverTimestamp(), arrivedBy: m.memberId, arrivedByName: m.memberId, remindAt: null });

describe('creating a trip', () => {
  test('an adult creates a trip with participants of any household', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'trips/t1'), trip(ADULT)));
  });

  test('a child never creates a trip, but takes part and says bien arrive once added', async () => {
    await assertFails(setDoc(doc(as(env, KID), 'trips/t2'), trip(KID)));
    await storedTrip('t3', ADULT, { participantIds: ['adult1', 'kid1'] });
    const db = as(env, KID);
    await assertSucceeds(setDoc(doc(db, 'trips/t3/steps/s1'), step()));
    await assertSucceeds(updateDoc(doc(db, 'trips/t3/steps/s1'), { ...arrival(KID), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'trips/t3'), { readerIds: ['adult1', 'kid1'], updatedAt: serverTimestamp() }));
  });

  test('nobody creates a trip in someone else\'s name, and a stranger creates nothing', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'trips/t1'), trip(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, ADULT), 'trips/t2'), trip(ADULT, { creatorName: 'Maman' })));
    await assertFails(setDoc(doc(as(env, STRANGER), 'trips/t3'), trip({ memberId: 'stranger' })));
  });

  test('the creator takes part, and every participant follows the trip', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'trips/t1'), trip(ADULT, { participantIds: ['adult3'] })));
    await assertFails(setDoc(doc(db, 'trips/t2'), trip(ADULT, { readerIds: ['adult1', 'admin1'] })));
  });

  test('the shape is checked', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'trips/a'), trip(ADULT, { title: '' })));
    await assertFails(setDoc(doc(db, 'trips/b'), trip(ADULT, { endDate: '2027-07-01' })));
    await assertFails(setDoc(doc(db, 'trips/c'), trip(ADULT, { startDate: '10/07/2027' })));
    await assertFails(setDoc(doc(db, 'trips/d'), trip(ADULT, { participantIds: ['adult1', 'adult1'] })));
    await assertFails(setDoc(doc(db, 'trips/e'), trip(ADULT, { extra: true })));
    await assertFails(setDoc(doc(db, 'trips/f'), trip(ADULT, { createdAt: Timestamp.fromMillis(0) })));
    await assertFails(setDoc(doc(db, 'trips/g'), trip(ADULT, { budget: 1200 })));
  });
});

describe('who follows a trip (récit 12)', () => {
  test('a follower reads the trip and its steps, an unticked member reads neither', async () => {
    await storedTrip('t1', ADULT, { readerIds: ['adult1', 'adult3', 'admin1'] });
    await storedStep('t1', 's1');
    await assertSucceeds(getDoc(doc(as(env, ADMIN), 'trips/t1')));
    await assertSucceeds(getDoc(doc(as(env, ADMIN), 'trips/t1/steps/s1')));
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), 'trips/t1')));
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), 'trips/t1/steps/s1')));
    await assertFails(getDocs(collection(as(env, OTHER_ADULT), 'trips/t1/steps')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'trips/t1')));
  });

  test('the planning lists the trips a member follows, and only those', async () => {
    await storedTrip('t1', ADULT);
    await storedTrip('t2', ADULT, { readerIds: ['adult1', 'adult3'] });
    const db = as(env, KID);
    const mine = await assertSucceeds(getDocs(query(collection(db, 'trips'), where('readerIds', 'array-contains', 'kid1'))));
    if (mine.size !== 1 || mine.docs[0].id !== 't1') throw new Error(`expected t1 only, got ${mine.docs.map((d) => d.id)}`);
    await assertFails(getDocs(collection(db, 'trips')));
    await assertFails(getDocs(query(collection(db, 'trips'), where('readerIds', 'array-contains', 'adult3'))));
  });
});

describe('editing a trip', () => {
  test('the creator and the admin principal edit and delete it', async () => {
    await storedTrip('t1', ADULT);
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'trips/t1'), { title: 'Corse du Sud', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, ADMIN), 'trips/t1'), { endDate: '2027-07-21', updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'trips/t1')));
  });

  test('a participant or a follower who did not create it cannot edit or delete it', async () => {
    await storedTrip('t1', ADULT);
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'trips/t1'), { title: 'Sardaigne', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'trips/t1')));
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'trips/t1')));
  });

  test('the creator, their name and the creation time never change', async () => {
    await storedTrip('t1', ADULT);
    const db = as(env, ADULT);
    await assertFails(updateDoc(doc(db, 'trips/t1'), { creatorId: 'adult3', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'trips/t1'), { creatorName: 'Autre', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'trips/t1'), { createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'trips/t1'), { title: 'Sans date de modif' }));
  });

  test('a participant cannot be unticked from the followers', async () => {
    await storedTrip('t1', ADULT);
    await assertFails(updateDoc(doc(as(env, ADULT), 'trips/t1'), { readerIds: ['adult1'], updatedAt: serverTimestamp() }));
  });
});

describe('steps (récit 11)', () => {
  test('each participant adds, edits and deletes steps', async () => {
    await storedTrip('t1', ADULT);
    const db = as(env, NEIGHBOUR);
    await assertSucceeds(setDoc(doc(db, 'trips/t1/steps/s1'), step()));
    await assertSucceeds(updateDoc(doc(db, 'trips/t1/steps/s1'), { place: 'Bastia', updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'trips/t1/steps/s1')));
  });

  test('a follower who does not take part only reads them', async () => {
    await storedTrip('t1', ADULT);
    await storedStep('t1', 's1');
    const db = as(env, OTHER_ADULT);
    await assertFails(setDoc(doc(db, 'trips/t1/steps/s2'), step()));
    await assertFails(updateDoc(doc(db, 'trips/t1/steps/s1'), { place: 'Bastia', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db, 'trips/t1/steps/s1')));
  });

  test('the admin principal can remove a step', async () => {
    await storedTrip('t1', ADULT);
    await storedStep('t1', 's1');
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'trips/t1/steps/s1')));
  });

  test('the shape is checked, and nothing locates anyone', async () => {
    await storedTrip('t1', ADULT);
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'trips/t1/steps/a'), step({ type: 'boat' })));
    await assertFails(setDoc(doc(db, 'trips/t1/steps/b'), step({ place: '' })));
    await assertFails(setDoc(doc(db, 'trips/t1/steps/c'), step({ arriveDate: '2027-07-09' })));
    await assertFails(setDoc(doc(db, 'trips/t1/steps/d'), step({ departTime: '8h15' })));
    await assertFails(setDoc(doc(db, 'trips/t1/steps/e'), step({ latitude: 41.9, longitude: 8.7 })));
    await assertFails(setDoc(doc(db, 'trips/t1/steps/f'), step({ cost: 120 })));
  });

  test('no step without its trip', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'trips/missing/steps/s1'), step()));
  });
});

describe('« bien arrivé » (récit 14)', () => {
  test('a participant says it, signed with their own name and the server time', async () => {
    await storedTrip('t1', ADULT);
    await storedStep('t1', 's1');
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'trips/t1/steps/s1'), { ...arrival(NEIGHBOUR), updatedAt: serverTimestamp() }));
  });

  test('nobody says it for someone else, or at another time', async () => {
    await storedTrip('t1', ADULT);
    await storedStep('t1', 's1');
    const db = as(env, NEIGHBOUR);
    await assertFails(updateDoc(doc(db, 'trips/t1/steps/s1'), { ...arrival(ADULT), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'trips/t1/steps/s1'), {
      ...arrival(NEIGHBOUR), arrivedAt: Timestamp.fromMillis(0), updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(db, 'trips/t1/steps/s1'), { ...arrival(NEIGHBOUR), arrivedByName: 'Maman', updatedAt: serverTimestamp() }));
  });

  test('once arrived, the reminder is cleared', async () => {
    await storedTrip('t1', ADULT);
    await storedStep('t1', 's1');
    await assertFails(updateDoc(doc(as(env, ADULT), 'trips/t1/steps/s1'), {
      ...arrival(ADULT), remindAt: Timestamp.fromMillis(0), updatedAt: serverTimestamp(),
    }));
  });

  test('a follower who does not take part cannot say it', async () => {
    await storedTrip('t1', ADULT);
    await storedStep('t1', 's1');
    await assertFails(updateDoc(doc(as(env, KID), 'trips/t1/steps/s1'), { ...arrival(KID), updatedAt: serverTimestamp() }));
  });

  test('another participant edits the step without touching the arrival, or takes it back', async () => {
    await storedTrip('t1', ADULT);
    await storedStep('t1', 's1', { arrivedAt: Timestamp.fromMillis(1000), arrivedBy: 'adult3', arrivedByName: 'adult3', remindAt: null });
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'trips/t1/steps/s1'), { note: 'Vol en retard', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'trips/t1/steps/s1'), { arrivedBy: 'adult1', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db, 'trips/t1/steps/s1'), {
      arrivedAt: null, arrivedBy: null, arrivedByName: null, updatedAt: serverTimestamp(),
    }));
  });
});

/** A valid line of the checklist (récit 13). */
function item(overrides = {}) {
  return { label: 'Crème solaire', assigneeId: null, assigneeName: null, done: false, updatedAt: serverTimestamp(), ...overrides };
}

describe('trip checklist', () => {
  beforeEach(async () => {
    // ADULT and adult3 take part; everyone but OTHER_ADULT follows.
    await storedTrip('t1', ADULT, { readerIds: EVERYONE.filter((id) => id !== 'adult2') });
  });

  test('a participant adds a line, gives it to another participant and ticks it', async () => {
    const ref = doc(as(env, ADULT), 'trips/t1/items/i1');
    await assertSucceeds(setDoc(ref, item()));
    await assertSucceeds(updateDoc(ref, { assigneeId: 'adult3', assigneeName: 'adult3', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'trips/t1/items/i1'), { done: true, updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(as(env, NEIGHBOUR), 'trips/t1/items/i1')));
  });

  test('a follower reads the checklist but cannot change it', async () => {
    await setDoc(doc(as(env, ADULT), 'trips/t1/items/i1'), item());
    const db = as(env, KID);
    await assertSucceeds(getDoc(doc(db, 'trips/t1/items/i1')));
    await assertSucceeds(getDocs(collection(db, 'trips/t1/items')));
    await assertFails(setDoc(doc(db, 'trips/t1/items/i2'), item()));
    await assertFails(updateDoc(doc(db, 'trips/t1/items/i1'), { done: true, updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db, 'trips/t1/items/i1')));
  });

  test('an unticked member and someone outside the family read nothing', async () => {
    await setDoc(doc(as(env, ADULT), 'trips/t1/items/i1'), item());
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), 'trips/t1/items/i1')));
    await assertFails(getDocs(collection(as(env, OTHER_ADULT), 'trips/t1/items')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'trips/t1/items/i1')));
  });

  test('only a participant can be in charge, and the shape is checked', async () => {
    const ref = doc(as(env, ADULT), 'trips/t1/items/i1');
    await assertFails(setDoc(ref, item({ assigneeId: 'kid1', assigneeName: 'kid1' })));
    await assertFails(setDoc(ref, item({ assigneeId: 'adult3', assigneeName: null })));
    await assertFails(setDoc(ref, item({ label: '' })));
    await assertFails(setDoc(ref, item({ label: 'x'.repeat(81) })));
    await assertFails(setDoc(ref, item({ done: 'yes' })));
    await assertFails(setDoc(ref, item({ position: '41.9,8.7' })));
    await assertFails(setDoc(ref, item({ updatedAt: Timestamp.fromMillis(0) })));
  });

  test('the admin principal may delete a line, not add one', async () => {
    await setDoc(doc(as(env, ADULT), 'trips/t1/items/i1'), item());
    await assertFails(setDoc(doc(as(env, ADMIN), 'trips/t1/items/i2'), item()));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'trips/t1/items/i1')));
  });
});
