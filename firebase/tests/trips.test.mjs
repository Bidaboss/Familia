// V3 rules: trips, their followers, their steps and their checklist (PRD, récits 11 to 14). Fictional family:
// admin1 (admin principal) and adult1, adult2, admin2 in h1, adult3 in h2, kid1 (child with the app,
// parent adult1).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, deleteField, doc, getDoc, getDocs, query, serverTimestamp, setDoc, Timestamp, updateDoc, where,
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

  test('the landscape is one of the six, and an older trip without one stays valid (récit 81)', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'trips/l1'), trip(ADULT, { landscape: 'mer' })));
    await assertSucceeds(setDoc(doc(db, 'trips/l2'), trip(ADULT)));
    await assertFails(setDoc(doc(db, 'trips/l3'), trip(ADULT, { landscape: 'lune' })));
    await assertFails(setDoc(doc(db, 'trips/l4'), trip(ADULT, { landscape: 3 })));
    await storedTrip('l5', ADULT);
    await assertSucceeds(updateDoc(doc(db, 'trips/l5'), { landscape: 'neige', updatedAt: serverTimestamp() }));
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

  test('« Monnaie » (récit 105): the creator sets an ISO code or takes it back; nothing else is a currency', async () => {
    await storedTrip('t1', ADULT);
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(doc(db, 'trips/t1'), { currency: 'THB', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db, 'trips/t1'), { currency: deleteField(), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'trips/t1'), { currency: 'thb', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'trips/t1'), { currency: 'BAHT', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, 'trips/t1'), { currency: 12, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'trips/t1'), { currency: 'THB', updatedAt: serverTimestamp() }));
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

  test('a step may carry its point on the map, both numbers in range or both null (récit 87)', async () => {
    await storedTrip('t1', ADULT);
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'trips/t1/steps/m1'), step({ lat: 41.9192, lng: 8.7386 })));
    await assertSucceeds(setDoc(doc(db, 'trips/t1/steps/m2'), step({ lat: null, lng: null })));
    await assertSucceeds(setDoc(doc(db, 'trips/t1/steps/m3'), step({ lat: -33.9, lng: -151.2 })));
    await assertFails(setDoc(doc(db, 'trips/t1/steps/m4'), step({ lat: 41.9, lng: null })));
    await assertFails(setDoc(doc(db, 'trips/t1/steps/m5'), step({ lat: 91, lng: 8.7 })));
    await assertFails(setDoc(doc(db, 'trips/t1/steps/m6'), step({ lat: 41.9, lng: 181 })));
    await assertFails(setDoc(doc(db, 'trips/t1/steps/m7'), step({ lat: '41.9', lng: '8.7' })));
  });

  test('a step with a point keeps it when someone says « bien arrivé »', async () => {
    await storedTrip('t1', ADULT);
    await storedStep('t1', 's1', { lat: 41.9192, lng: 8.7386 });
    await assertSucceeds(updateDoc(doc(as(env, NEIGHBOUR), 'trips/t1/steps/s1'), { ...arrival(NEIGHBOUR), updatedAt: serverTimestamp() }));
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

  test('a participant reads the checklist, a follower who does not go reads nothing (récit 86)', async () => {
    await setDoc(doc(as(env, ADULT), 'trips/t1/items/i1'), item());
    const mate = as(env, NEIGHBOUR);
    await assertSucceeds(getDoc(doc(mate, 'trips/t1/items/i1')));
    await assertSucceeds(getDocs(collection(mate, 'trips/t1/items')));
    const follower = as(env, KID);
    await assertFails(getDoc(doc(follower, 'trips/t1/items/i1')));
    await assertFails(getDocs(collection(follower, 'trips/t1/items')));
  });

  test('a follower cannot change the checklist', async () => {
    await setDoc(doc(as(env, ADULT), 'trips/t1/items/i1'), item());
    const db = as(env, KID);
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

  test('the admin principal, if he does not go, reads nothing, may delete a line, not add one', async () => {
    await setDoc(doc(as(env, ADULT), 'trips/t1/items/i1'), item());
    await assertFails(getDoc(doc(as(env, ADMIN), 'trips/t1/items/i1')));
    await assertFails(setDoc(doc(as(env, ADMIN), 'trips/t1/items/i2'), item()));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'trips/t1/items/i1')));
  });
});

describe('trip photos (récit 15)', () => {
  beforeEach(async () => {
    // ADULT and adult3 take part; everyone but OTHER_ADULT follows.
    await storedTrip('t1', ADULT, { readerIds: EVERYONE.filter((id) => id !== 'adult2') });
  });

  const photo = (author, slot, overrides = {}) => ({
    authorId: author.memberId, authorName: author.memberId, slot, createdAt: serverTimestamp(), ...overrides,
  });

  test('a participant adds a photo in one of their 30 places, and the followers see it', async () => {
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/photos/adult3_1'), photo(NEIGHBOUR, 1)));
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/photos/adult3_30'), photo(NEIGHBOUR, 30)));
    await assertSucceeds(getDocs(collection(as(env, KID), 'trips/t1/photos')));
    await assertFails(getDocs(collection(as(env, OTHER_ADULT), 'trips/t1/photos')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'trips/t1/photos/adult3_1')));
  });

  test('no 31st photo: the place is part of the id and goes from 1 to 30', async () => {
    const db = as(env, NEIGHBOUR);
    await assertFails(setDoc(doc(db, 'trips/t1/photos/adult3_31'), photo(NEIGHBOUR, 31)));
    await assertFails(setDoc(doc(db, 'trips/t1/photos/adult3_0'), photo(NEIGHBOUR, 0)));
    await assertFails(setDoc(doc(db, 'trips/t1/photos/adult3_x'), photo(NEIGHBOUR, 2)));
    await assertFails(setDoc(doc(db, 'trips/t1/photos/adult3_2'), photo(NEIGHBOUR, 2.5)));
  });

  test('a photo may say when it was taken, never in the future nor before 2001', async () => {
    const db = as(env, NEIGHBOUR);
    await assertSucceeds(setDoc(doc(db, 'trips/t1/photos/adult3_1'), photo(NEIGHBOUR, 1, { takenAt: Timestamp.fromDate(new Date('2025-08-04T16:30:00Z')) })));
    await assertFails(setDoc(doc(db, 'trips/t1/photos/adult3_2'), photo(NEIGHBOUR, 2, { takenAt: Timestamp.fromMillis(Date.now() + 2 * 86400000) })));
    // A phone a few minutes ahead of the server still sends the photo of the meal.
    await assertSucceeds(setDoc(doc(db, 'trips/t1/photos/adult3_5'), photo(NEIGHBOUR, 5, { takenAt: Timestamp.fromMillis(Date.now() + 5 * 60000) })));
    await assertFails(setDoc(doc(db, 'trips/t1/photos/adult3_3'), photo(NEIGHBOUR, 3, { takenAt: Timestamp.fromDate(new Date('1999-12-31T00:00:00Z')) })));
    await assertFails(setDoc(doc(db, 'trips/t1/photos/adult3_4'), photo(NEIGHBOUR, 4, { takenAt: '2025-08-04' })));
  });

  test('a photo is signed by its author, never in the name of someone else, and never edited', async () => {
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/photos/adult1_1'), photo(ADULT, 1)));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/photos/adult3_1'), photo(NEIGHBOUR, 1, { authorName: 'Lou' })));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/photos/adult3_1'), photo(NEIGHBOUR, 1, { place: 'Ajaccio' })));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/photos/adult3_1'), photo(NEIGHBOUR, 1, { createdAt: Timestamp.fromMillis(0) })));
    await setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/photos/adult3_1'), photo(NEIGHBOUR, 1));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/photos/adult3_1'), photo(NEIGHBOUR, 1)));
  });

  test('a photo may name a step of this trip that exists, never another (récit 104)', async () => {
    await storedStep('t1', 's1');
    await storedTrip('t2', ADULT, { readerIds: EVERYONE });
    await storedStep('t2', 'other');
    const db = as(env, NEIGHBOUR);
    await assertSucceeds(setDoc(doc(db, 'trips/t1/photos/adult3_1'), photo(NEIGHBOUR, 1, { stepId: 's1' })));
    await assertFails(setDoc(doc(db, 'trips/t1/photos/adult3_2'), photo(NEIGHBOUR, 2, { stepId: 'other' })));
    await assertFails(setDoc(doc(db, 'trips/t1/photos/adult3_3'), photo(NEIGHBOUR, 3, { stepId: 'nope' })));
    await assertFails(setDoc(doc(db, 'trips/t1/photos/adult3_4'), photo(NEIGHBOUR, 4, { stepId: '' })));
    await assertFails(setDoc(doc(db, 'trips/t1/photos/adult3_5'), photo(NEIGHBOUR, 5, { stepId: 7 })));
    // A path would make exists() look at a document under the step.
    await storedStep('t1', 's1/x/y');
    await assertFails(setDoc(doc(db, 'trips/t1/photos/adult3_6'), photo(NEIGHBOUR, 6, { stepId: 's1/x/y' })));
    // Nobody sees more than before: who does not follow the trip still reads nothing.
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), 'trips/t1/photos/adult3_1')));
  });

  test('a follower who does not take part adds no photo', async () => {
    await assertFails(setDoc(doc(as(env, KID), 'trips/t1/photos/kid1_1'), photo(KID, 1)));
  });

  test('the author or the creator of the trip removes a photo, nobody else', async () => {
    await setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/photos/adult3_1'), photo(NEIGHBOUR, 1));
    await setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/photos/adult3_2'), photo(NEIGHBOUR, 2));
    await setDoc(doc(as(env, ADULT), 'trips/t1/photos/adult1_1'), photo(ADULT, 1));
    await assertFails(deleteDoc(doc(as(env, KID), 'trips/t1/photos/adult3_1')));
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'trips/t1/photos/adult1_1')));
    await assertSucceeds(deleteDoc(doc(as(env, NEIGHBOUR), 'trips/t1/photos/adult3_1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'trips/t1/photos/adult3_2')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'trips/t1/photos/adult1_1')));
  });
});

// « Ma valise » (récit 62): a private list per participant, ticked by them or by their adults.
describe('trips/{tripId}/bags', () => {
  const bag = (by, items = [{ label: 'Chargeur', done: false }]) => ({ items, updatedAt: serverTimestamp(), updatedBy: by.memberId });

  beforeEach(async () => {
    await storedTrip('t1', ADULT, { participantIds: ['adult1', 'adult3', 'kid1'] });
  });

  test('a participant fills and ticks their own bag, and reads it back', async () => {
    const db = as(env, NEIGHBOUR);
    await assertSucceeds(setDoc(doc(db, 'trips/t1/bags/adult3'), bag(NEIGHBOUR)));
    await assertSucceeds(setDoc(doc(db, 'trips/t1/bags/adult3'), bag(NEIGHBOUR, [{ label: 'Chargeur', done: true }])));
    await assertSucceeds(getDoc(doc(db, 'trips/t1/bags/adult3')));
    await assertSucceeds(deleteDoc(doc(db, 'trips/t1/bags/adult3')));
  });

  test('a responsible adult ticks the bag of their child, nobody else reads it', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'trips/t1/bags/kid1'), bag(ADULT)));
    await assertSucceeds(getDoc(doc(as(env, KID), 'trips/t1/bags/kid1')));
    await assertFails(getDoc(doc(as(env, NEIGHBOUR), 'trips/t1/bags/kid1')));
    await assertFails(getDoc(doc(as(env, ADMIN), 'trips/t1/bags/kid1')));
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/bags/adult1'), bag(NEIGHBOUR)));
    await assertFails(getDoc(doc(as(env, STRANGER), 'trips/t1/bags/adult3')));
  });

  test('only the participants have a bag, of the right shape', async () => {
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'trips/t1/bags/adult2'), bag(OTHER_ADULT)));
    const db = as(env, NEIGHBOUR);
    await assertFails(setDoc(doc(db, 'trips/t1/bags/adult3'), { ...bag(NEIGHBOUR), updatedBy: 'adult1' }));
    await assertFails(setDoc(doc(db, 'trips/t1/bags/adult3'), { ...bag(NEIGHBOUR), extra: 1 }));
    await assertFails(setDoc(doc(db, 'trips/t1/bags/adult3'), bag(NEIGHBOUR, 'Chargeur')));
    await assertFails(setDoc(doc(db, 'trips/t1/bags/adult3'), bag(NEIGHBOUR, Array.from({ length: 81 }, (_, i) => ({ label: `o${i}`, done: false })))));
  });
});

// « Le mot de l'étape » (récit 88): trips/t1/words/s1. adult1 and adult3 take part, admin1 and adult2
// follow, a trip hidden from adult2 has no word for them.
describe('« le mot de l\'étape » (récit 88)', () => {
  const word = (m, overrides = {}) => ({
    text: 'Bien arrivés à Ajaccio, il fait beau.',
    photoIds: ['adult1_1', 'adult1_2'],
    authorId: m.memberId,
    authorName: m.memberId,
    hearts: {},
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  });

  async function setup(tripOverrides = {}) {
    await storedTrip('t1', ADULT, tripOverrides);
    await storedStep('t1', 's1');
  }

  async function storedWord(m, overrides = {}) {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'trips/t1/words/s1'), {
        ...word(m, overrides), createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
      });
    });
  }

  test('a participant writes a word, signed, for a step that exists', async () => {
    await setup();
    await storedStep('t1', 's2');
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'trips/t1/words/s1'), word(ADULT)));
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/words/s2'), word(NEIGHBOUR, { photoIds: [] })));
    await assertFails(setDoc(doc(as(env, ADULT), 'trips/t1/words/nope'), word(ADULT)));
  });

  test('a follower who does not take part writes nothing, and nobody writes in another name', async () => {
    await setup();
    await assertFails(setDoc(doc(as(env, ADMIN), 'trips/t1/words/s1'), word(ADMIN)));
    await assertFails(setDoc(doc(as(env, ADULT), 'trips/t1/words/s1'), word(NEIGHBOUR)));
    await assertFails(setDoc(doc(as(env, ADULT), 'trips/t1/words/s1'), word(ADULT, { authorName: 'Maman' })));
  });

  test('the shape is checked: 280 characters, 4 photos, no hearts yet', async () => {
    await setup();
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'trips/t1/words/s1'), word(ADULT, { text: 'a'.repeat(280), photoIds: ['a_1', 'a_2', 'a_3', 'a_4'] })));
    await assertFails(setDoc(doc(db, 'trips/t1/words/s1'), word(ADULT, { text: 'a'.repeat(281) })));
    await assertFails(setDoc(doc(db, 'trips/t1/words/s1'), word(ADULT, { text: '' })));
    await assertFails(setDoc(doc(db, 'trips/t1/words/s1'), word(ADULT, { photoIds: ['a_1', 'a_2', 'a_3', 'a_4', 'a_5'] })));
    await assertFails(setDoc(doc(db, 'trips/t1/words/s1'), word(ADULT, { photoIds: ['a_1', 'a_1'] })));
    await assertFails(setDoc(doc(db, 'trips/t1/words/s1'), word(ADULT, { hearts: { admin1: true } })));
    await assertFails(setDoc(doc(db, 'trips/t1/words/s1'), word(ADULT, { hearts: [] })));
    await assertFails(setDoc(doc(db, 'trips/t1/words/s1'), word(ADULT, { hearts: '' })));
    await assertFails(setDoc(doc(db, 'trips/t1/words/s1'), word(ADULT, { extra: 1 })));
  });

  test('followers read the word, an unticked member does not', async () => {
    await setup({ readerIds: ['admin1', 'adult1', 'adult3'] });
    await storedWord(ADULT);
    await assertSucceeds(getDoc(doc(as(env, ADMIN), 'trips/t1/words/s1')));
    await assertSucceeds(getDoc(doc(as(env, NEIGHBOUR), 'trips/t1/words/s1')));
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), 'trips/t1/words/s1')));
  });

  test('only its author changes the text and the photos, and takes the word back', async () => {
    await setup();
    await storedWord(ADULT);
    const change = { text: 'Changé', photoIds: ['adult1_3'], updatedAt: serverTimestamp() };
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'trips/t1/words/s1'), change));
    await assertFails(updateDoc(doc(as(env, ADMIN), 'trips/t1/words/s1'), change));
    await assertFails(updateDoc(doc(as(env, ADULT), 'trips/t1/words/s1'), { ...change, authorId: 'adult3' }));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'trips/t1/words/s1'), change));
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'trips/t1/words/s1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'trips/t1/words/s1')));
  });

  test('the admin principal takes a word back', async () => {
    await setup();
    await storedWord(ADULT);
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'trips/t1/words/s1')));
  });

  test('a follower puts and takes back their own heart, and nobody else\'s', async () => {
    await setup();
    await storedWord(ADULT);
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(updateDoc(doc(db, 'trips/t1/words/s1'), { 'hearts.adult2': true }));
    await assertFails(updateDoc(doc(db, 'trips/t1/words/s1'), { 'hearts.admin1': true }));
    await assertFails(updateDoc(doc(db, 'trips/t1/words/s1'), { 'hearts.adult2': 'yes' }));
    await assertFails(updateDoc(doc(db, 'trips/t1/words/s1'), { text: 'Changé', 'hearts.adult2': true }));
    await assertSucceeds(updateDoc(doc(as(env, ADULT), 'trips/t1/words/s1'), { 'hearts.adult1': true }));
    await assertSucceeds(updateDoc(doc(db, 'trips/t1/words/s1'), { 'hearts.adult2': deleteField() }));
  });

  test('an unticked member puts no heart and no comment', async () => {
    await setup({ readerIds: ['admin1', 'adult1', 'adult3'] });
    await storedWord(ADULT);
    const db = as(env, OTHER_ADULT);
    await assertFails(updateDoc(doc(db, 'trips/t1/words/s1'), { 'hearts.adult2': true }));
    await assertFails(setDoc(doc(db, 'trips/t1/words/s1/comments/c1'), comment(OTHER_ADULT)));
  });

  const comment = (m, overrides = {}) => ({
    text: 'Quelle belle photo !', authorId: m.memberId, authorName: m.memberId, createdAt: serverTimestamp(), ...overrides,
  });

  test('a follower comments with a mention, and changes or removes their own comment', async () => {
    await setup();
    await storedWord(ADULT);
    const db = as(env, ADMIN);
    await assertSucceeds(setDoc(doc(db, 'trips/t1/words/s1/comments/c1'), comment(ADMIN, { mentionIds: ['adult1'] })));
    await assertFails(setDoc(doc(db, 'trips/t1/words/s1/comments/c2'), comment(ADMIN, { text: 'a'.repeat(501) })));
    await assertFails(setDoc(doc(db, 'trips/t1/words/s1/comments/c3'), comment(OTHER_ADULT)));
    await assertSucceeds(updateDoc(doc(db, 'trips/t1/words/s1/comments/c1'), { text: 'Merci', editedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'trips/t1/words/s1/comments/c1'), { text: 'X', editedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, 'trips/t1/words/s1/comments/c1')));
  });

  test('the author of the word removes a comment under it, and no comment without a word', async () => {
    await setup();
    await storedWord(ADULT);
    await setDoc(doc(as(env, ADMIN), 'trips/t1/words/s1/comments/c1'), comment(ADMIN));
    await assertFails(deleteDoc(doc(as(env, NEIGHBOUR), 'trips/t1/words/s1/comments/c1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'trips/t1/words/s1/comments/c1')));
    await assertFails(setDoc(doc(as(env, ADMIN), 'trips/t1/words/s2/comments/c1'), comment(ADMIN)));
  });
});

describe('« Prévenir ma famille » (récit 105, règle 43)', () => {
  const alert = (m) => ({ authorId: m.memberId, authorName: m.memberId, createdAt: serverTimestamp() });

  async function storedAlert(tripId, m, millis) {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `trips/${tripId}/alerts/${m.memberId}`), {
        authorId: m.memberId, authorName: m.memberId, createdAt: Timestamp.fromMillis(millis),
      });
    });
  }

  test('a participant sends one, signed with their own name and the server time', async () => {
    await storedTrip('t1', ADULT);
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/alerts/adult3'), alert(NEIGHBOUR)));
  });

  test('nobody sends one for someone else, with another time or with more in it', async () => {
    await storedTrip('t1', ADULT);
    const db = as(env, NEIGHBOUR);
    await assertFails(setDoc(doc(db, 'trips/t1/alerts/adult1'), alert(ADULT)));
    await assertFails(setDoc(doc(db, 'trips/t1/alerts/adult3'), { ...alert(NEIGHBOUR), createdAt: Timestamp.fromMillis(0) }));
    await assertFails(setDoc(doc(db, 'trips/t1/alerts/adult3'), { ...alert(NEIGHBOUR), authorName: 'Maman' }));
    await assertFails(setDoc(doc(db, 'trips/t1/alerts/adult3'), { ...alert(NEIGHBOUR), lat: 41.9, lng: 8.7 }));
  });

  test('a follower who does not go cannot send one, and nobody reads them', async () => {
    await storedTrip('t1', ADULT);
    await assertFails(setDoc(doc(as(env, KID), 'trips/t1/alerts/kid1'), alert(KID)));
    await storedAlert('t1', NEIGHBOUR, 0);
    await assertFails(getDoc(doc(as(env, NEIGHBOUR), 'trips/t1/alerts/adult3')));
    await assertFails(getDoc(doc(as(env, ADULT), 'trips/t1/alerts/adult3')));
  });

  test('a new one only 10 minutes after the last', async () => {
    await storedTrip('t1', ADULT);
    await storedAlert('t1', NEIGHBOUR, Date.now() - 60 * 1000);
    await assertFails(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/alerts/adult3'), alert(NEIGHBOUR)));
    await storedAlert('t1', NEIGHBOUR, Date.now() - 11 * 60 * 1000);
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/alerts/adult3'), alert(NEIGHBOUR)));
  });
});

describe('« Documents » (récit 105, règle 41)', () => {
  const document = (m, overrides = {}) => ({
    ownerId: m.memberId, ownerName: m.memberId, title: 'Billet aller', kind: 'ticket',
    file: 'd1.pdf', contentType: 'application/pdf', size: 1024, createdAt: serverTimestamp(), ...overrides,
  });

  async function storedDocument(tripId, id, m) {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `trips/${tripId}/documents/${id}`), {
        ...document(m, { file: `${id}.pdf` }), createdAt: Timestamp.fromMillis(0),
      });
    });
  }

  test('a participant adds one, signed with their name, and only those who go read it', async () => {
    await storedTrip('t1', ADULT);
    await assertSucceeds(setDoc(doc(as(env, NEIGHBOUR), 'trips/t1/documents/d1'), document(NEIGHBOUR)));
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'trips/t1/documents/d1')));
    // KID follows the trip (EVERYONE) without going: no document for them.
    await assertFails(getDoc(doc(as(env, KID), 'trips/t1/documents/d1')));
    await assertFails(getDocs(collection(as(env, KID), 'trips/t1/documents')));
  });

  test('the shape is checked: owner, kind, file named after the document, type, size, time', async () => {
    await storedTrip('t1', ADULT);
    const db = as(env, NEIGHBOUR);
    await assertFails(setDoc(doc(db, 'trips/t1/documents/d1'), document(ADULT)));
    await assertFails(setDoc(doc(db, 'trips/t1/documents/d1'), document(NEIGHBOUR, { kind: 'photo' })));
    await assertFails(setDoc(doc(db, 'trips/t1/documents/d1'), document(NEIGHBOUR, { file: 'd2.pdf' })));
    await assertFails(setDoc(doc(db, 'trips/t1/documents/d1'), document(NEIGHBOUR, { contentType: 'image/jpeg' })));
    await assertFails(setDoc(doc(db, 'trips/t1/documents/d1'), document(NEIGHBOUR, { size: 6 * 1024 * 1024 })));
    await assertFails(setDoc(doc(db, 'trips/t1/documents/d1'), document(NEIGHBOUR, { title: '' })));
    await assertFails(setDoc(doc(db, 'trips/t1/documents/d1'), document(NEIGHBOUR, { createdAt: Timestamp.fromMillis(0) })));
    await assertFails(setDoc(doc(db, 'trips/t1/documents/d1'), document(NEIGHBOUR, { lat: 1 })));
    await assertSucceeds(setDoc(doc(db, 'trips/t1/documents/d3'), document(NEIGHBOUR, { file: 'd3.png', contentType: 'image/png' })));
  });

  test('a follower who does not go adds none; nothing is edited', async () => {
    await storedTrip('t1', ADULT);
    await assertFails(setDoc(doc(as(env, KID), 'trips/t1/documents/d1'), document(KID)));
    await storedDocument('t1', 'd2', NEIGHBOUR);
    await assertFails(updateDoc(doc(as(env, NEIGHBOUR), 'trips/t1/documents/d2'), { title: 'Autre' }));
  });

  test('the owner, the creator of the trip or the admin principal removes one, nobody else', async () => {
    await storedTrip('t1', ADULT, { participantIds: ['adult1', 'adult3', 'adult2'], readerIds: EVERYONE });
    await storedDocument('t1', 'd1', NEIGHBOUR);
    await storedDocument('t1', 'd2', NEIGHBOUR);
    await storedDocument('t1', 'd3', NEIGHBOUR);
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'trips/t1/documents/d1')));
    await assertSucceeds(deleteDoc(doc(as(env, NEIGHBOUR), 'trips/t1/documents/d1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'trips/t1/documents/d2')));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'trips/t1/documents/d3')));
  });
});
