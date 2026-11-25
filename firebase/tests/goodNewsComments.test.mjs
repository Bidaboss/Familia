// Commentaires des bonnes nouvelles (PRD, récit 67). Fictional family of helpers.mjs: adult1, adult2
// and admin2 (chef) in h1, admin1 (admin principal), adult3 in h2; kid1 (child with the app, parent
// adult1) in h1. The news n1 is by adult2, for adult2, household h1.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, SECOND_ADMIN, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

const comment = (db, id = 'c1', post = 'n1') => doc(db, `goodNews/${post}/comments/${id}`);
const mute = (db, memberId, post = 'n1') => doc(db, `goodNews/${post}/commentMutes/${memberId}`);

function newComment(by, overrides = {}) {
  return { text: 'Bravo !', authorId: by.memberId, authorName: by.memberId, createdAt: serverTimestamp(), ...overrides };
}

/** A comment written earlier by [by], without the rules. */
async function stored(by, id = 'c1') {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(comment(ctx.firestore(), id), { ...newComment(by), createdAt: Timestamp.fromMillis(0) });
  });
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'goodNews/n1'), {
      text: 'Premier tour de vélo', photoIds: ['p1'], authorId: 'adult2', authorName: 'adult2', forMemberId: 'adult2',
      householdId: 'h1', mentionIds: [], reactions: {}, hiddenByIds: [], commentCount: 0,
      createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
    // « Défi réussi », published by Familia: no author, no household.
    await setDoc(doc(db, 'goodNews/n2'), {
      text: 'Défi réussi', photoIds: [], challengeMonth: '2026-11', createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
  });
});

describe('write', () => {
  test('every member comments in their own name, a child with the app too (PRODUIT-102)', async () => {
    await assertSucceeds(setDoc(comment(as(env, ADULT)), newComment(ADULT)));
    await assertSucceeds(setDoc(comment(as(env, KID), 'c2'), newComment(KID)));
    await assertSucceeds(setDoc(comment(as(env, NEIGHBOUR), 'c3'), newComment(NEIGHBOUR)));
  });

  test('1 to 500 characters, nothing else, never in the name of someone else', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(comment(db), newComment(ADULT, { text: '' })));
    await assertFails(setDoc(comment(db), newComment(ADULT, { text: 'a'.repeat(501) })));
    await assertSucceeds(setDoc(comment(db), newComment(ADULT, { text: 'a'.repeat(500) })));
    await assertFails(setDoc(comment(db, 'c2'), newComment(ADULT, { photo: 'x' })));
    await assertFails(setDoc(comment(db, 'c3'), newComment(ADULT, { authorId: 'adult2' })));
    await assertFails(setDoc(comment(db, 'c4'), newComment(ADULT, { authorName: 'Quelqu’un' })));
    await assertFails(setDoc(comment(db, 'c5'), newComment(ADULT, { createdAt: Timestamp.fromMillis(0) })));
  });

  test('not under a news that does not exist, not from outside the family', async () => {
    await assertFails(setDoc(comment(as(env, ADULT), 'c1', 'gone'), newComment(ADULT)));
    await assertFails(setDoc(comment(as(env, STRANGER)), newComment({ memberId: 'stranger' })));
  });

  test('every member reads them', async () => {
    await stored(ADULT);
    await assertSucceeds(getDoc(comment(as(env, NEIGHBOUR))));
    await assertSucceeds(getDoc(comment(as(env, KID))));
    await assertFails(getDoc(comment(as(env, STRANGER))));
  });
});

describe('« @Prénom » (l’admin, 19/11)', () => {
  test('a comment names up to 20 members, each once, and its author changes them with the text', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(comment(db), newComment(ADULT, { text: '@Noé bravo', mentionIds: ['adult2'] })));
    await assertFails(setDoc(comment(db, 'c2'), newComment(ADULT, { mentionIds: Array.from({ length: 21 }, (_, i) => `m${i}`) })));
    await assertFails(setDoc(comment(db, 'c3'), newComment(ADULT, { mentionIds: ['a', 'a'] })));
    await assertSucceeds(updateDoc(comment(db), { text: 'Bravo', mentionIds: [], editedAt: serverTimestamp() }));
    await assertFails(updateDoc(comment(as(env, NEIGHBOUR)), { mentionIds: ['adult3'], editedAt: serverTimestamp() }));
  });
});

describe('change and remove', () => {
  test('its author alone changes the text', async () => {
    await stored(ADULT);
    await assertSucceeds(updateDoc(comment(as(env, ADULT)), { text: 'Bravo, champion !', editedAt: serverTimestamp() }));
    await assertFails(updateDoc(comment(as(env, ADULT)), { authorId: 'adult2' }));
    await assertFails(updateDoc(comment(as(env, OTHER_ADULT)), { text: 'Non', editedAt: serverTimestamp() }));
    await assertFails(updateDoc(comment(as(env, ADMIN)), { text: 'Non', editedAt: serverTimestamp() }));
  });

  test('its author, the author of the news, a responsible adult, a chef there and the admin principal remove it', async () => {
    for (const who of [KID, OTHER_ADULT, ADULT, SECOND_ADMIN, ADMIN]) {
      await stored(KID);
      await assertSucceeds(deleteDoc(comment(as(env, who))));
    }
  });

  test('another member, or a chef of another household, does not', async () => {
    await stored(ADULT);
    await assertFails(deleteDoc(comment(as(env, NEIGHBOUR))));
    await assertFails(deleteDoc(comment(as(env, KID))));
  });

  test('under a « Défi réussi » of Familia, the author and the admin principal remove it', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(comment(ctx.firestore(), 'c1', 'n2'), { ...newComment(ADULT), createdAt: Timestamp.fromMillis(0) });
    });
    await assertFails(deleteDoc(comment(as(env, NEIGHBOUR), 'c1', 'n2')));
    await assertSucceeds(deleteDoc(comment(as(env, ADMIN), 'c1', 'n2')));
  });
});

describe('the news and its count', () => {
  test('nobody writes commentCount: not on a new news, not on an edit', async () => {
    await assertFails(setDoc(doc(as(env, ADULT), 'goodNews/n3'), {
      text: 'Une bonne nouvelle', photoIds: ['p1'], authorId: 'adult1', authorName: 'adult1', forMemberId: 'adult1',
      householdId: 'h1', mentionIds: [], reactions: {}, hiddenByIds: [], commentCount: 3,
      createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(as(env, OTHER_ADULT), 'goodNews/n1'), { commentCount: 9 }));
  });

  test('its author still edits a news that has a count', async () => {
    await assertSucceeds(updateDoc(doc(as(env, OTHER_ADULT), 'goodNews/n1'), { text: 'Premier tour sans roulettes', updatedAt: serverTimestamp() }));
  });
});

describe('Ne plus être prévenu', () => {
  test('a member mutes and unmutes for themselves only', async () => {
    await assertSucceeds(setDoc(mute(as(env, ADULT), 'adult1'), { mutedAt: serverTimestamp() }));
    await assertSucceeds(getDoc(mute(as(env, ADULT), 'adult1')));
    await assertFails(getDoc(mute(as(env, OTHER_ADULT), 'adult1')));
    await assertFails(setDoc(mute(as(env, ADULT), 'adult2'), { mutedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(mute(as(env, ADULT), 'adult1')));
  });
});
