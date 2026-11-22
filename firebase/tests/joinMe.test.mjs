// « Rejoignez-moi » (PRD, récit 33): a member sends a place to the members they ticked. Only the
// author and the recipients read it; nobody changes it; its author takes it back. Fictional family:
// adult1 and adult2 in h1, admin1 (admin principal), adult3 in another household.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, Timestamp, updateDoc, where } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
});

const share = (db, id = 's1') => doc(db, `joinMe/${id}`);
const inHours = (h) => Timestamp.fromMillis(Date.now() + h * 3_600_000);

/** What the app writes: expireAt is « now + 2 h » from the phone's clock. */
function joinMe(by, recipientIds = [OTHER_ADULT.memberId], overrides = {}) {
  return {
    authorId: by.memberId, authorName: by.memberId, recipientIds,
    name: 'Plage des Sables', address: '1 promenade de la Mer, 85100 Les Sables', lat: 46.49, lng: -1.79,
    note: 'On est au poste de secours', createdAt: serverTimestamp(), expireAt: inHours(2), ...overrides,
  };
}

async function seedShare(by = ADULT.memberId, recipientIds = [OTHER_ADULT.memberId]) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(share(ctx.firestore()), {
      authorId: by, authorName: by, recipientIds, name: null, address: null, lat: 46.49, lng: -1.79, note: null,
      createdAt: Timestamp.fromMillis(0), expireAt: Timestamp.fromMillis(86_400_000),
    });
  });
}

describe('sending', () => {
  test('a member sends a place to the members they ticked, with or without name, address and note', async () => {
    await assertSucceeds(setDoc(share(as(env, ADULT)), joinMe(ADULT)));
    await assertSucceeds(setDoc(share(as(env, ADULT), 's2'), joinMe(ADULT, [OTHER_ADULT.memberId, NEIGHBOUR.memberId], { name: null, address: null, note: null })));
  });

  test('nobody ticked, or themselves among the recipients, is refused', async () => {
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, [])));
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, [ADULT.memberId, OTHER_ADULT.memberId])));
  });

  test('it is sent in their own name only', async () => {
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, undefined, { authorId: OTHER_ADULT.memberId })));
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, undefined, { authorName: 'Quelqu\'un' })));
    await assertFails(setDoc(share(as(env, STRANGER)), joinMe({ memberId: 'x' })));
  });

  test('it lasts about 2 h, never longer', async () => {
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, undefined, { expireAt: inHours(24) })));
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, undefined, { expireAt: inHours(0.5) })));
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, undefined, { expireAt: null })));
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, undefined, { createdAt: Timestamp.fromMillis(0) })));
  });

  test('a position out of range, a long text or an extra field is refused', async () => {
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, undefined, { lat: 91 })));
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, undefined, { lng: '2.35' })));
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, undefined, { name: 'x'.repeat(61) })));
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, undefined, { note: 'x'.repeat(201) })));
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, undefined, { name: '' })));
    await assertFails(setDoc(share(as(env, ADULT)), joinMe(ADULT, undefined, { accuracy: 12 })));
    const { note, ...missing } = joinMe(ADULT);
    await assertFails(setDoc(share(as(env, ADULT)), missing));
  });
});

describe('reading', () => {
  test('its author and its recipients read it, nobody else, not even the admin principal', async () => {
    await seedShare();
    await assertSucceeds(getDoc(share(as(env, ADULT))));
    await assertSucceeds(getDoc(share(as(env, OTHER_ADULT))));
    await assertFails(getDoc(share(as(env, NEIGHBOUR))));
    await assertFails(getDoc(share(as(env, ADMIN))));
    await assertFails(getDoc(share(as(env, STRANGER))));
  });

  test('the app lists what it was sent and what it sent', async () => {
    await seedShare();
    await assertSucceeds(getDocs(query(collection(as(env, OTHER_ADULT), 'joinMe'), where('recipientIds', 'array-contains', OTHER_ADULT.memberId))));
    await assertSucceeds(getDocs(query(collection(as(env, ADULT), 'joinMe'), where('authorId', '==', ADULT.memberId))));
    await assertFails(getDocs(collection(as(env, OTHER_ADULT), 'joinMe')));
  });
});

describe('changing and taking back', () => {
  test('nobody changes it', async () => {
    await seedShare();
    await assertFails(updateDoc(share(as(env, ADULT)), { note: 'Changement' }));
    await assertFails(updateDoc(share(as(env, OTHER_ADULT)), { recipientIds: [OTHER_ADULT.memberId] }));
  });

  test('only its author takes it back', async () => {
    await seedShare();
    await assertFails(deleteDoc(share(as(env, OTHER_ADULT))));
    await assertFails(deleteDoc(share(as(env, ADMIN))));
    await assertSucceeds(deleteDoc(share(as(env, ADULT))));
  });
});
