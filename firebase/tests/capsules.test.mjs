// Rules of the time capsule (PRD, récit 39). Fictional family: admin1 (admin principal), adult1,
// adult2 in h1, adult3 in h2.
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

/** A day [days] from now, AAAA-MM-JJ. */
function inDays(days) {
  return new Date(Date.now() + days * 24 * 3600 * 1000).toISOString().slice(0, 10);
}

/** A valid new capsule by [author] for adult2, opening in two months. */
function capsule(author, overrides = {}) {
  return {
    text: 'Pour tes 18 ans : on est fiers de toi.',
    photoCount: 1,
    recipientIds: ['adult2'],
    openAt: inDays(60),
    openedFor: [],
    authorId: author.memberId,
    authorName: author.memberId,
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

/** A capsule already opened by the server for adult2. */
async function opened(id) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `capsules/${id}`), {
      ...capsule(ADULT),
      openAt: '2026-01-01',
      openedFor: ['adult2'],
      createdAt: Timestamp.fromMillis(0),
    });
  });
}

describe('time capsules (récit 39)', () => {
  test('a member seals a capsule; only they read it until it opens', async () => {
    await assertSucceeds(setDoc(doc(as(env, ADULT), 'capsules/c1'), capsule(ADULT)));
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'capsules/c1')));
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), 'capsules/c1')));
    await assertFails(getDoc(doc(as(env, ADMIN), 'capsules/c1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'capsules/c1')));
  });

  test('from one month to five years ahead, 1 to 13 recipients, up to 3 photos', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'capsules/ok'), capsule(ADULT, { openAt: inDays(5 * 365) })));
    await assertFails(setDoc(doc(db, 'capsules/c1'), capsule(ADULT, { openAt: inDays(10) })));
    await assertFails(setDoc(doc(db, 'capsules/c2'), capsule(ADULT, { openAt: inDays(6 * 366) })));
    await assertFails(setDoc(doc(db, 'capsules/c3'), capsule(ADULT, { openAt: 'bientôt' })));
    await assertFails(setDoc(doc(db, 'capsules/c4'), capsule(ADULT, { recipientIds: [] })));
    await assertFails(setDoc(doc(db, 'capsules/c5'), capsule(ADULT, { recipientIds: ['adult2', 'adult2'] })));
    await assertFails(setDoc(doc(db, 'capsules/c6'), capsule(ADULT, { photoCount: 4 })));
    await assertFails(setDoc(doc(db, 'capsules/c7'), capsule(ADULT, { text: '' })));
  });

  test('never already open, never in the name of someone else, never edited', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(doc(db, 'capsules/c1'), capsule(ADULT, { openedFor: ['adult2'] })));
    await assertFails(setDoc(doc(db, 'capsules/c2'), capsule(OTHER_ADULT)));
    await assertSucceeds(setDoc(doc(db, 'capsules/c3'), capsule(ADULT)));
    await assertFails(updateDoc(doc(db, 'capsules/c3'), { openedFor: ['adult2'] }));
    await assertFails(updateDoc(doc(db, 'capsules/c3'), { text: 'Changé' }));
  });

  test('once opened, the recipients read it and find it with their query', async () => {
    await opened('c1');
    await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), 'capsules/c1')));
    await assertFails(getDoc(doc(as(env, NEIGHBOUR), 'capsules/c1')));
    const mine = query(collection(as(env, OTHER_ADULT), 'capsules'), where('openedFor', 'array-contains', 'adult2'));
    await assertSucceeds(getDocs(mine));
    const written = query(collection(as(env, ADULT), 'capsules'), where('authorId', '==', 'adult1'));
    await assertSucceeds(getDocs(written));
  });

  test('only the author removes it, and only while sealed', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'capsules/c2'), { ...capsule(ADULT), createdAt: Timestamp.fromMillis(0) });
    });
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'capsules/c2')));
    await assertFails(deleteDoc(doc(as(env, ADMIN), 'capsules/c2')));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'capsules/c2')));
    // Opened, it is a keepsake of its recipients (PRD rév. 187).
    await opened('c1');
    await assertFails(deleteDoc(doc(as(env, ADULT), 'capsules/c1')));
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'capsules/c1')));
  });

  test('the admin principal sees what a removed author left, and may only ask to delete it', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'capsuleLeftovers/gone'), { memberName: 'Jules', count: 2, deleteAsked: false, updatedAt: Timestamp.fromMillis(0) });
    });
    const admin = as(env, ADMIN);
    await assertSucceeds(getDoc(doc(admin, 'capsuleLeftovers/gone')));
    await assertFails(getDoc(doc(as(env, ADULT), 'capsuleLeftovers/gone')));
    await assertFails(updateDoc(doc(as(env, ADULT), 'capsuleLeftovers/gone'), { deleteAsked: true }));
    await assertFails(updateDoc(doc(admin, 'capsuleLeftovers/gone'), { count: 0 }));
    await assertFails(updateDoc(doc(admin, 'capsuleLeftovers/gone'), { deleteAsked: false }));
    await assertFails(setDoc(doc(admin, 'capsuleLeftovers/other'), { memberName: 'Lou', count: 1, deleteAsked: true }));
    await assertFails(deleteDoc(doc(admin, 'capsuleLeftovers/gone')));
    await assertSucceeds(updateDoc(doc(admin, 'capsuleLeftovers/gone'), { deleteAsked: true }));
  });
});
