// « On y va ensemble » (PRD, récit 52): an adult who comes offers seats in their car; each member
// gets in or out for themselves or a child they answer for, never beyond the seats. Fictional
// family: adult1 and adult2 in h1, admin1 (admin principal), kid1 (child with the app, parent
// adult1), adult3 in h2. The event e1 is written by adult2; adult2 drives.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };
const DRIVER = OTHER_ADULT;

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'events/e1'), {
      title: 'Repas', date: '2026-12-24', time: null, place: null, note: null,
      targetType: 'family', targetMemberId: null, householdId: null,
      authorId: 'adult2', authorName: 'adult2',
      createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
    for (const id of ['adult2', 'adult1']) {
      await setDoc(doc(db, `events/e1/rsvps/${id}`), { status: 'yes', answeredBy: id, updatedAt: Timestamp.fromMillis(0) });
    }
  });
});

const rideRef = (db, driverId = DRIVER.memberId) => doc(db, `events/e1/rides/${driverId}`);
const offer = (overrides = {}) => ({
  seats: 2, from: 'Lyon', leaveAt: '10:30', passengers: [],
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
});

/** The car of adult2, already there with [passengers]. */
async function seedRide(passengers = [], seats = 2) {
  await env.withSecurityRulesDisabled((ctx) => setDoc(rideRef(ctx.firestore()), {
    seats, from: null, leaveAt: null, passengers, createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
  }));
}

const passengers = (list) => ({ passengers: list, updatedAt: serverTimestamp() });

describe('rides', () => {
  test('an adult who comes offers seats, with a town and a time if they like', async () => {
    await assertSucceeds(setDoc(rideRef(as(env, DRIVER)), offer()));
    await assertSucceeds(setDoc(rideRef(as(env, ADULT), ADULT.memberId), offer({ from: null, leaveAt: null })));
  });

  test('an offer is refused when not coming, for someone else, a child, or ill-formed', async () => {
    await assertFails(setDoc(rideRef(as(env, ADMIN), ADMIN.memberId), offer()));
    await assertFails(setDoc(rideRef(as(env, ADULT), DRIVER.memberId), offer()));
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'events/e1/rsvps/kid1'), { status: 'yes', answeredBy: 'kid1', updatedAt: Timestamp.fromMillis(0) }));
    await assertFails(setDoc(rideRef(as(env, KID), KID.memberId), offer()));
    const db = as(env, DRIVER);
    await assertFails(setDoc(rideRef(db), offer({ seats: 0 })));
    await assertFails(setDoc(rideRef(db), offer({ seats: 9 })));
    await assertFails(setDoc(rideRef(db), offer({ leaveAt: '25:00' })));
    await assertFails(setDoc(rideRef(db), offer({ from: 'x'.repeat(41) })));
    await assertFails(setDoc(rideRef(db), offer({ passengers: ['adult1'] })));
    await assertFails(setDoc(rideRef(db), offer({ lat: 45.7 })));
  });

  test('a member gets in for themselves or a child, and out again', async () => {
    await seedRide();
    await assertSucceeds(updateDoc(rideRef(as(env, ADULT)), passengers(['adult1'])));
    await assertSucceeds(updateDoc(rideRef(as(env, ADULT)), passengers(['adult1', 'kid1'])));
    await assertSucceeds(updateDoc(rideRef(as(env, KID)), passengers(['adult1'])));
    await assertSucceeds(updateDoc(rideRef(as(env, ADULT)), passengers([])));
  });

  test('never beyond the seats, never two at once, never for someone else', async () => {
    await seedRide(['adult1'], 1);
    await assertFails(updateDoc(rideRef(as(env, ADMIN)), passengers(['adult1', 'admin1'])));
    await seedRide([], 2);
    await assertFails(updateDoc(rideRef(as(env, ADULT)), passengers(['adult1', 'kid1'])));
    await assertFails(updateDoc(rideRef(as(env, NEIGHBOUR)), passengers(['adult1'])));
    await assertFails(updateDoc(rideRef(as(env, ADULT)), passengers(['adult2'])));
    await assertFails(updateDoc(rideRef(as(env, ADULT)), passengers(['ghost'])));
    await assertFails(updateDoc(rideRef(as(env, ADULT)), { seats: 8, updatedAt: serverTimestamp() }));
    await seedRide(['admin1'], 2);
    await assertFails(updateDoc(rideRef(as(env, ADULT)), passengers([])));
  });

  test('the driver changes the car and lets anyone out, never below the passengers', async () => {
    await seedRide(['adult1', 'admin1'], 3);
    const db = as(env, DRIVER);
    await assertSucceeds(updateDoc(rideRef(db), { seats: 2, from: 'Annecy', leaveAt: '09:00', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(rideRef(db), { seats: 1, updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(rideRef(db), passengers(['admin1'])));
    await assertSucceeds(deleteDoc(rideRef(db)));
  });

  test('the passenger, or a stranger, does not withdraw the offer; the author of the event does', async () => {
    await seedRide(['adult1']);
    await assertFails(deleteDoc(rideRef(as(env, ADULT))));
    await assertFails(deleteDoc(rideRef(as(env, STRANGER))));
    await assertSucceeds(deleteDoc(rideRef(as(env, ADMIN))));
  });
});

describe('rideNeeds', () => {
  const need = (db, id = 'adult1') => doc(db, `events/e1/rideNeeds/${id}`);
  const ask = (by) => ({ askedBy: by.memberId, updatedAt: serverTimestamp() });

  test('a member asks for a seat, for themselves or a child, and takes it back', async () => {
    await assertSucceeds(setDoc(need(as(env, ADULT)), ask(ADULT)));
    await assertSucceeds(setDoc(need(as(env, ADULT), 'kid1'), ask(ADULT)));
    await assertSucceeds(deleteDoc(need(as(env, ADULT), 'kid1')));
    await assertSucceeds(deleteDoc(need(as(env, ADULT))));
  });

  test('not for someone else, nor with another field', async () => {
    await assertFails(setDoc(need(as(env, NEIGHBOUR)), ask(NEIGHBOUR)));
    await assertFails(setDoc(need(as(env, ADULT)), ask(NEIGHBOUR)));
    await assertFails(setDoc(need(as(env, ADULT)), { ...ask(ADULT), note: 'x' }));
    await assertFails(setDoc(need(as(env, STRANGER)), ask(ADULT)));
  });
});
