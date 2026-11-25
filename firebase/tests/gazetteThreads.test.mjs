// La gazette (récits 115, 116): J'aime and comments of a card, gazetteThreads/{threadId}. Fictional
// family of helpers.mjs: adult1, adult2, admin2 in h1, admin1 (admin principal), adult3 in h2; kid1
// (child with the app, parent adult1). Trip t1 is read by adult1 and adult2 only; help request r1
// is seen by adult1 and adult2 only.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, deleteField, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

const thread = (db, id) => doc(db, `gazetteThreads/${id}`);
const comment = (db, id, commentId = 'c1') => doc(db, `gazetteThreads/${id}/comments/${commentId}`);

function newComment(by, overrides = {}) {
  return { text: 'Trop bien !', authorId: by.memberId, authorName: by.memberId, createdAt: serverTimestamp(), ...overrides };
}

async function stored(id, data) {
  await env.withSecurityRulesDisabled(async (ctx) => { await setDoc(thread(ctx.firestore(), id), data); });
}

async function storedComment(id, by, commentId = 'c1') {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(comment(ctx.firestore(), id, commentId), { ...newComment(by), createdAt: Timestamp.fromMillis(0) });
  });
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'trips/t1'), { readerIds: ['adult1', 'adult2'], participantIds: ['adult1'] });
    await setDoc(doc(db, 'helpRequests/r1'), { viewerIds: ['adult1', 'adult2'], authorId: 'adult1' });
    await setDoc(doc(db, 'gazettePosts/p1'), { authorId: 'adult2', authorName: 'adult2', text: 'Coucou', photoIds: [], mentionIds: [], createdAt: Timestamp.fromMillis(0) });
  });
});

describe('who reads a thread: who sees its card', () => {
  test('a publication, an event, a recipe, a birthday: every member', async () => {
    for (const id of ['post:p1', 'event:e1', 'recipe:r1', 'birthday:adult2:2026-11-25', 'weekly:q1', 'deal:d1', 'album:events:e1:2026-11-20']) {
      await stored(id, { reactions: {} });
      await assertSucceeds(getDoc(thread(as(env, NEIGHBOUR), id)));
      await assertSucceeds(getDocs(collection(as(env, KID), `gazetteThreads/${id}/comments`)));
    }
  });

  test('the photos and the film of a trip: its readers only', async () => {
    for (const id of ['album:trips:t1:2026-11-20', 'film:t1:f1']) {
      await stored(id, { reactions: {} });
      await assertSucceeds(getDoc(thread(as(env, OTHER_ADULT), id)));
      await assertFails(getDoc(thread(as(env, NEIGHBOUR), id)));
      await assertFails(getDocs(collection(as(env, NEIGHBOUR), `gazetteThreads/${id}/comments`)));
    }
    await stored('album:trips:nope:2026-11-20', { reactions: {} });
    await assertFails(getDoc(thread(as(env, ADULT), 'album:trips:nope:2026-11-20')));
  });

  test('a coup de main: who sees it, and the admin principal', async () => {
    await stored('help:r1', { reactions: {} });
    await assertSucceeds(getDoc(thread(as(env, OTHER_ADULT), 'help:r1')));
    await assertSucceeds(getDoc(thread(as(env, ADMIN), 'help:r1')));
    await assertFails(getDoc(thread(as(env, NEIGHBOUR), 'help:r1')));
    await assertFails(getDoc(thread(as(env, ADULT), 'help:gone')));
  });

  test('the envies of a member: never that member (67 quater)', async () => {
    await stored('wishes:adult2:2026-11-25', { reactions: {} });
    await assertSucceeds(getDoc(thread(as(env, ADULT), 'wishes:adult2:2026-11-25')));
    await assertFails(getDoc(thread(as(env, OTHER_ADULT), 'wishes:adult2:2026-11-25')));
    await assertFails(getDocs(collection(as(env, OTHER_ADULT), 'gazetteThreads/wishes:adult2:2026-11-25/comments')));
  });

  test('a publication: only while it exists (récit 121)', async () => {
    await stored('post:gone', { reactions: {} });
    await assertFails(getDoc(thread(as(env, ADULT), 'post:gone')));
    await assertFails(setDoc(thread(as(env, ADULT), 'post:gone2'), { reactions: { adult1: '💛' } }));
    await assertFails(setDoc(comment(as(env, ADULT), 'post:gone2'), newComment(ADULT)));
    await assertFails(getDoc(thread(as(env, ADULT), 'post:')));
  });

  test('an unknown kind is refused', async () => {
    await stored('capsule:c1', { reactions: {} });
    await assertFails(getDoc(thread(as(env, ADULT), 'capsule:c1')));
    await assertFails(setDoc(thread(as(env, ADULT), 'capsule:c1'), { reactions: { adult1: '💛' } }));
  });
});

describe('J\'aime', () => {
  test('each member sets, changes and removes only their own', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(thread(db, 'post:p1'), { reactions: { adult1: '💛' } }));
    await assertSucceeds(updateDoc(thread(as(env, KID), 'post:p1'), { 'reactions.kid1': '🎉' }));
    await assertSucceeds(updateDoc(thread(db, 'post:p1'), { 'reactions.adult1': '🔥' }));
    await assertSucceeds(updateDoc(thread(db, 'post:p1'), { 'reactions.adult1': deleteField() }));
    await assertFails(updateDoc(thread(db, 'post:p1'), { 'reactions.kid1': deleteField() }));
    await assertFails(updateDoc(thread(db, 'post:p1'), { 'reactions.adult2': '💛' }));
  });

  test('only the four reactions, never a count or a comment written by the phone', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(thread(db, 'post:p1'), { reactions: { adult1: '😡' } }));
    await assertFails(setDoc(thread(db, 'post:p1'), { reactions: { adult2: '💛' } }));
    await assertFails(setDoc(thread(db, 'post:p1'), { reactions: { adult1: '💛' }, commentCount: 3 }));
    await stored('post:p1', { reactions: {}, commentCount: 1, last: [] });
    await assertFails(updateDoc(thread(db, 'post:p1'), { commentCount: 0 }));
    await assertFails(updateDoc(thread(db, 'post:p1'), { last: ['x'] }));
    await assertSucceeds(updateDoc(thread(db, 'post:p1'), { 'reactions.adult1': '👏' }));
  });

  test('no J\'aime on a card one does not see', async () => {
    await assertFails(setDoc(thread(as(env, NEIGHBOUR), 'help:r1'), { reactions: { adult3: '💛' } }));
    await assertFails(setDoc(thread(as(env, OTHER_ADULT), 'wishes:adult2:2026-11-25'), { reactions: { adult2: '💛' } }));
    await assertFails(deleteDoc(thread(as(env, ADMIN), 'post:p1')));
  });
});

describe('comments', () => {
  test('every member comments in their own name, a child with the app too', async () => {
    await assertSucceeds(setDoc(comment(as(env, ADULT), 'post:p1'), newComment(ADULT)));
    await assertSucceeds(setDoc(comment(as(env, KID), 'post:p1', 'c2'), newComment(KID, { mentionIds: ['adult1'] })));
    await assertFails(setDoc(comment(as(env, ADULT), 'post:p1', 'c3'), newComment(OTHER_ADULT)));
    await assertFails(setDoc(comment(as(env, ADULT), 'post:p1', 'c4'), newComment(ADULT, { authorName: 'someone' })));
  });

  test('1 to 500 characters, nothing else', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(comment(db, 'post:p1'), newComment(ADULT, { text: '' })));
    await assertFails(setDoc(comment(db, 'post:p1'), newComment(ADULT, { text: 'a'.repeat(501) })));
    await assertFails(setDoc(comment(db, 'post:p1'), newComment(ADULT, { photo: 'x' })));
    await assertSucceeds(setDoc(comment(db, 'post:p1'), newComment(ADULT, { text: 'a'.repeat(500) })));
  });

  test('events and recipes keep their own comments', async () => {
    await assertFails(setDoc(comment(as(env, ADULT), 'event:e1'), newComment(ADULT)));
    await assertFails(setDoc(comment(as(env, ADULT), 'recipe:r1'), newComment(ADULT)));
  });

  test('never on a card one does not see', async () => {
    await assertFails(setDoc(comment(as(env, NEIGHBOUR), 'help:r1'), newComment(NEIGHBOUR)));
    await assertFails(setDoc(comment(as(env, NEIGHBOUR), 'film:t1:f1'), newComment(NEIGHBOUR)));
    await assertFails(setDoc(comment(as(env, OTHER_ADULT), 'wishes:adult2:2026-11-25'), newComment(OTHER_ADULT)));
    await assertSucceeds(setDoc(comment(as(env, ADULT), 'wishes:adult2:2026-11-25'), newComment(ADULT)));
  });

  test('its author changes the text only', async () => {
    await storedComment('post:p1', ADULT);
    await assertSucceeds(updateDoc(comment(as(env, ADULT), 'post:p1'), { text: 'Superbe', editedAt: serverTimestamp() }));
    await assertFails(updateDoc(comment(as(env, ADULT), 'post:p1'), { authorId: 'adult2', editedAt: serverTimestamp() }));
    await assertFails(updateDoc(comment(as(env, OTHER_ADULT), 'post:p1'), { text: 'Non', editedAt: serverTimestamp() }));
  });

  test('its author, a parent of a child and the admin principal remove it, nobody else', async () => {
    await storedComment('post:p1', KID);
    await assertFails(deleteDoc(comment(as(env, OTHER_ADULT), 'post:p1')));
    await assertSucceeds(deleteDoc(comment(as(env, ADULT), 'post:p1')));
    await storedComment('post:p1', OTHER_ADULT, 'c2');
    await assertFails(deleteDoc(comment(as(env, ADULT), 'post:p1', 'c2')));
    await assertSucceeds(deleteDoc(comment(as(env, ADMIN), 'post:p1', 'c2')));
    await storedComment('post:p1', OTHER_ADULT, 'c3');
    await assertSucceeds(deleteDoc(comment(as(env, OTHER_ADULT), 'post:p1', 'c3')));
  });
});
