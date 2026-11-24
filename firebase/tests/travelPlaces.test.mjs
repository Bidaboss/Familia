// Rules of the destinations added by hand to « Carnet de baroudeur » (récit 107, règle 38): private
// to their member, a past day, no kilometre. Fictional family: adult1 keeps a carnet, adult2 and
// admin1 (admin principal) are other members.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { addDoc, collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
});

/** A valid destination of adult1's carnet. */
function place(overrides = {}) {
  return {
    name: 'Lisbonne',
    country: 'Portugal',
    countryCode: 'PT',
    lat: 38.72,
    lng: -9.14,
    date: '2019-05-12',
    days: 5,
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

const PATH = `members/${ADULT.memberId}/travelPlaces`;

async function storedPlace(id) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `${PATH}/${id}`), { ...place(), createdAt: Timestamp.fromMillis(0) });
  });
}

/** A day of the next year, « yyyy-MM-dd ». */
function nextYear() {
  return `${new Date().getUTCFullYear() + 1}-01-15`;
}

describe('hand-added destinations of the carnet (récit 107)', () => {
  test('the member adds, reads and removes their own', async () => {
    const db = as(env, ADULT);
    const ref = await assertSucceeds(addDoc(collection(db, PATH), place()));
    await assertSucceeds(getDoc(ref));
    await assertSucceeds(getDocs(collection(db, PATH)));
    await assertSucceeds(addDoc(collection(db, PATH), place({ country: null, countryCode: null, days: 1 })));
    await assertSucceeds(deleteDoc(ref));
  });

  test('nobody else reads, adds or removes them, not even the admin principal', async () => {
    await storedPlace('p1');
    for (const who of [OTHER_ADULT, ADMIN, STRANGER]) {
      const db = as(env, who);
      await assertFails(getDoc(doc(db, `${PATH}/p1`)));
      await assertFails(getDocs(collection(db, PATH)));
      await assertFails(setDoc(doc(db, `${PATH}/p2`), place()));
      await assertFails(deleteDoc(doc(db, `${PATH}/p1`)));
    }
  });

  test('a destination is never edited', async () => {
    await storedPlace('p1');
    await assertFails(updateDoc(doc(as(env, ADULT), `${PATH}/p1`), { days: 6 }));
  });

  test('the day is a past day, never in the future', async () => {
    const db = as(env, ADULT);
    await assertFails(addDoc(collection(db, PATH), place({ date: nextYear() })));
    await assertFails(addDoc(collection(db, PATH), place({ date: '1850-01-01' })));
    await assertFails(addDoc(collection(db, PATH), place({ date: '12/05/2019' })));
    await assertFails(addDoc(collection(db, PATH), place({ date: '2019-13-01' })));
  });

  test('a shape the app does not write is refused', async () => {
    const db = as(env, ADULT);
    await assertFails(addDoc(collection(db, PATH), place({ name: '' })));
    await assertFails(addDoc(collection(db, PATH), place({ name: 'x'.repeat(81) })));
    await assertFails(addDoc(collection(db, PATH), place({ countryCode: 'pt' })));
    await assertFails(addDoc(collection(db, PATH), place({ lat: 91 })));
    await assertFails(addDoc(collection(db, PATH), place({ lat: null })));
    await assertFails(addDoc(collection(db, PATH), place({ days: 0 })));
    await assertFails(addDoc(collection(db, PATH), place({ days: 366 })));
    await assertFails(addDoc(collection(db, PATH), place({ days: 2.5 })));
    await assertFails(addDoc(collection(db, PATH), place({ km: 2000 })));
    await assertFails(addDoc(collection(db, PATH), place({ createdAt: Timestamp.fromMillis(0) })));
  });
});
