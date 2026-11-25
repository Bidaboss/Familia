// Commentaires d'un événement (PRD, récit 28): every member reads them and writes their own text,
// kept 12 months. Its author changes the text; its author, the admin principal or a chef of the
// author's household removes it. Fictional family: adult1 and adult2 in h1 with admin2 (chef of
// h1), admin1 (admin principal), adult3 alone in h2.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, getDocs, collection, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, SECOND_ADMIN, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'events/e1'), { title: 'Repas', authorId: ADULT.memberId });
  });
});

const comment = (db, id = 'c1', eventId = 'e1') => doc(db, `events/${eventId}/comments/${id}`);
const inDays = (d) => Timestamp.fromMillis(Date.now() + d * 86_400_000);

function written(by, overrides = {}) {
  return { text: 'Je prends le dessert', authorId: by.memberId, authorName: by.memberId, createdAt: serverTimestamp(), expireAt: inDays(365), ...overrides };
}

async function seedComment(by = ADULT.memberId, id = 'c1') {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(comment(ctx.firestore(), id), {
      text: 'Bonne idée', authorId: by, authorName: by, createdAt: Timestamp.fromMillis(0), expireAt: Timestamp.fromMillis(1),
    });
  });
}

describe('writing', () => {
  test('a member comments an event in their own name', async () => {
    await assertSucceeds(setDoc(comment(as(env, OTHER_ADULT)), written(OTHER_ADULT)));
    await assertSucceeds(setDoc(comment(as(env, NEIGHBOUR), 'c2'), written(NEIGHBOUR)));
  });

  test('never in the name of another, nor from outside the family', async () => {
    await assertFails(setDoc(comment(as(env, ADULT)), written(ADULT, { authorId: OTHER_ADULT.memberId })));
    await assertFails(setDoc(comment(as(env, ADULT)), written(ADULT, { authorName: 'Quelqu\'un' })));
    await assertFails(setDoc(comment(as(env, STRANGER)), written({ memberId: 'x' })));
  });

  test('a text of 1 to 500 characters, under an event that exists', async () => {
    await assertFails(setDoc(comment(as(env, ADULT)), written(ADULT, { text: '' })));
    await assertFails(setDoc(comment(as(env, ADULT)), written(ADULT, { text: 'a'.repeat(501) })));
    await assertSucceeds(setDoc(comment(as(env, ADULT)), written(ADULT, { text: 'a'.repeat(500) })));
    await assertFails(setDoc(comment(as(env, ADULT), 'c1', 'nope'), written(ADULT)));
  });

  test('kept about 12 months, with the server time and nothing else', async () => {
    await assertFails(setDoc(comment(as(env, ADULT)), written(ADULT, { expireAt: inDays(30) })));
    await assertFails(setDoc(comment(as(env, ADULT)), written(ADULT, { expireAt: inDays(800) })));
    await assertFails(setDoc(comment(as(env, ADULT)), written(ADULT, { createdAt: Timestamp.fromMillis(0) })));
    await assertFails(setDoc(comment(as(env, ADULT)), written(ADULT, { photo: 'x.jpg' })));
  });
});

describe('« @Prénom » (l’admin, 19/11)', () => {
  test('a comment names up to 20 members, each once', async () => {
    const many = Array.from({ length: 21 }, (_, i) => `m${i}`);
    await assertSucceeds(setDoc(comment(as(env, ADULT)), written(ADULT, { text: '@Noé tu viens ?', mentionIds: [OTHER_ADULT.memberId] })));
    await assertSucceeds(setDoc(comment(as(env, ADULT), 'c2'), written(ADULT, { mentionIds: [] })));
    await assertFails(setDoc(comment(as(env, ADULT), 'c3'), written(ADULT, { mentionIds: many })));
    await assertFails(setDoc(comment(as(env, ADULT), 'c4'), written(ADULT, { mentionIds: ['a', 'a'] })));
    await assertFails(setDoc(comment(as(env, ADULT), 'c5'), written(ADULT, { mentionIds: 'a' })));
  });

  test('its author changes who is named with the text', async () => {
    await seedComment();
    await assertSucceeds(updateDoc(comment(as(env, ADULT)), { text: '@Noé merci', mentionIds: [OTHER_ADULT.memberId], editedAt: serverTimestamp() }));
    await assertFails(updateDoc(comment(as(env, ADULT)), { text: 'x', mentionIds: ['a', 'a'], editedAt: serverTimestamp() }));
    await assertFails(updateDoc(comment(as(env, OTHER_ADULT)), { text: 'x', mentionIds: [], editedAt: serverTimestamp() }));
  });
});

describe('reading', () => {
  test('every member reads the comments of an event, nobody outside the family', async () => {
    await seedComment();
    await assertSucceeds(getDocs(collection(as(env, NEIGHBOUR), 'events/e1/comments')));
    await assertFails(getDoc(comment(as(env, STRANGER))));
  });
});

describe('changing', () => {
  test('its author changes the text only', async () => {
    await seedComment();
    await assertSucceeds(updateDoc(comment(as(env, ADULT)), { text: 'Finalement le fromage', editedAt: serverTimestamp() }));
    await assertFails(updateDoc(comment(as(env, ADULT)), { text: 'Encore', editedAt: serverTimestamp(), authorName: 'x' }));
    await assertFails(updateDoc(comment(as(env, ADULT)), { text: '', editedAt: serverTimestamp() }));
    await assertFails(updateDoc(comment(as(env, ADULT)), { expireAt: inDays(5000) }));
  });

  test('nobody else changes it, not even the admin principal', async () => {
    await seedComment();
    await assertFails(updateDoc(comment(as(env, OTHER_ADULT)), { text: 'x', editedAt: serverTimestamp() }));
    await assertFails(updateDoc(comment(as(env, ADMIN)), { text: 'x', editedAt: serverTimestamp() }));
  });
});

describe('removing', () => {
  test('its author, the admin principal and a chef of the author\'s household remove it', async () => {
    await seedComment(ADULT.memberId, 'c1');
    await seedComment(ADULT.memberId, 'c2');
    await seedComment(ADULT.memberId, 'c3');
    await assertSucceeds(deleteDoc(comment(as(env, ADULT), 'c1')));
    await assertSucceeds(deleteDoc(comment(as(env, ADMIN), 'c2')));
    await assertSucceeds(deleteDoc(comment(as(env, SECOND_ADMIN), 'c3')));
  });

  test('another member, or a chef of another household, does not', async () => {
    await seedComment(NEIGHBOUR.memberId);
    await assertFails(deleteDoc(comment(as(env, OTHER_ADULT))));
    await assertFails(deleteDoc(comment(as(env, SECOND_ADMIN))));
  });
});

describe('« Ne plus être prévenu »', () => {
  const mute = (db, memberId) => doc(db, `events/e1/commentMutes/${memberId}`);

  test('a member mutes and unmutes the comments of an event for themselves only', async () => {
    await assertSucceeds(setDoc(mute(as(env, ADULT), ADULT.memberId), { mutedAt: serverTimestamp() }));
    await assertSucceeds(getDoc(mute(as(env, ADULT), ADULT.memberId)));
    await assertSucceeds(deleteDoc(mute(as(env, ADULT), ADULT.memberId)));
    await assertFails(setDoc(mute(as(env, ADULT), OTHER_ADULT.memberId), { mutedAt: serverTimestamp() }));
    await assertFails(setDoc(mute(as(env, ADULT), ADULT.memberId), { mutedAt: serverTimestamp(), x: 1 }));
  });

  test('nobody reads another member\'s choice', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(mute(ctx.firestore(), ADULT.memberId), { mutedAt: Timestamp.fromMillis(0) });
    });
    await assertFails(getDoc(mute(as(env, OTHER_ADULT), ADULT.memberId)));
    await assertFails(deleteDoc(mute(as(env, ADMIN), ADULT.memberId)));
  });
});
