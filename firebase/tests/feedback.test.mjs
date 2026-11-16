// « Une idée ? Un souci ? » (PRD, récit 6 sexies): any member sends an idea or a problem in their
// own name, with the context of the phone; only its author and the admin principal read it; the
// admin principal gives it a state and a word.
// Fictional family: adult1 and adult2 in h1 with admin2 (chef of h1), admin1 (admin principal).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, Timestamp, updateDoc, where } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, SECOND_ADMIN, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
});

const feedback = (db, id = 'f1') => doc(db, `feedback/${id}`);

function sent(by, overrides = {}) {
  return {
    kind: 'idea', text: 'Un bouton pour les anniversaires', authorId: by.memberId, authorName: by.memberId,
    appVersion: '1.4.0', android: '14', device: 'Google Pixel 8', screen: 'family',
    createdAt: serverTimestamp(), status: 'new', note: null, statusAt: null, ...overrides,
  };
}

async function seedFeedback(by = ADULT.memberId, id = 'f1') {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(feedback(ctx.firestore(), id), {
      kind: 'problem', text: 'Le planning ne charge pas', authorId: by, authorName: by,
      appVersion: '1.4.0', android: '14', device: 'Google Pixel 8', screen: 'help',
      createdAt: Timestamp.fromMillis(0), status: 'new', note: null, statusAt: null,
    });
  });
}

describe('sending', () => {
  test('a member sends an idea or a problem in their own name', async () => {
    await assertSucceeds(setDoc(feedback(as(env, ADULT)), sent(ADULT)));
    await assertSucceeds(setDoc(feedback(as(env, OTHER_ADULT), 'f2'), sent(OTHER_ADULT, { kind: 'problem' })));
  });

  test('a screenshot is named only at the place of this request', async () => {
    await assertSucceeds(setDoc(feedback(as(env, ADULT), 's1'), sent(ADULT, { screenshot: 'feedback/adult1/s1.jpg' })));
    await assertFails(setDoc(feedback(as(env, ADULT), 's2'), sent(ADULT, { screenshot: 'feedback/adult1/s1.jpg' })));
    await assertFails(setDoc(feedback(as(env, ADULT), 's3'), sent(ADULT, { screenshot: 'feedback/adult2/s3.jpg' })));
    await assertFails(setDoc(feedback(as(env, ADULT), 's4'), sent(ADULT, { screenshot: 'goodNews/adult1/x/s4.jpg' })));
  });

  test('never in the name of another, nor from outside the family', async () => {
    await assertFails(setDoc(feedback(as(env, ADULT)), sent(ADULT, { authorId: OTHER_ADULT.memberId })));
    await assertFails(setDoc(feedback(as(env, ADULT)), sent(ADULT, { authorName: 'Quelqu\'un' })));
    await assertFails(setDoc(feedback(as(env, STRANGER)), sent({ memberId: 'x' })));
  });

  test('a text of 1 to 1000 characters, a known kind, unseen, at the server time, nothing else', async () => {
    await assertFails(setDoc(feedback(as(env, ADULT)), sent(ADULT, { text: '' })));
    await assertFails(setDoc(feedback(as(env, ADULT)), sent(ADULT, { text: 'a'.repeat(1001) })));
    await assertSucceeds(setDoc(feedback(as(env, ADULT)), sent(ADULT, { text: 'a'.repeat(1000) })));
    await assertFails(setDoc(feedback(as(env, ADULT), 'f2'), sent(ADULT, { kind: 'bug' })));
    await assertFails(setDoc(feedback(as(env, ADULT), 'f2'), sent(ADULT, { status: 'done' })));
    await assertFails(setDoc(feedback(as(env, ADULT), 'f2'), sent(ADULT, { note: 'Déjà fait' })));
    await assertFails(setDoc(feedback(as(env, ADULT), 'f2'), sent(ADULT, { screen: 'planning' })));
    await assertFails(setDoc(feedback(as(env, ADULT), 'f2'), sent(ADULT, { device: 'd'.repeat(81) })));
    await assertFails(setDoc(feedback(as(env, ADULT), 'f2'), sent(ADULT, { createdAt: Timestamp.fromMillis(0) })));
    await assertFails(setDoc(feedback(as(env, ADULT), 'f2'), sent(ADULT, { photo: 'x.jpg' })));
    await assertFails(setDoc(feedback(as(env, ADULT), 'f2'), sent(ADULT, { appVersion: 'v'.repeat(41) })));
  });
});

describe('reading', () => {
  test('its author and the admin principal, nobody else', async () => {
    await seedFeedback();
    await assertSucceeds(getDoc(feedback(as(env, ADULT))));
    await assertSucceeds(getDoc(feedback(as(env, ADMIN))));
    await assertFails(getDoc(feedback(as(env, OTHER_ADULT))));
    await assertFails(getDoc(feedback(as(env, SECOND_ADMIN))));
    await assertFails(getDoc(feedback(as(env, STRANGER))));
  });

  test('the admin principal lists them all, a member only their own', async () => {
    await seedFeedback();
    await seedFeedback(OTHER_ADULT.memberId, 'f2');
    await assertSucceeds(getDocs(collection(as(env, ADMIN), 'feedback')));
    await assertFails(getDocs(collection(as(env, ADULT), 'feedback')));
    await assertSucceeds(getDocs(query(collection(as(env, ADULT), 'feedback'), where('authorId', '==', ADULT.memberId))));
  });
});

describe('after sending', () => {
  test('only the admin principal gives it a state and a word, and changes nothing else', async () => {
    await seedFeedback();
    const answer = (status, note = null) => ({ status, note, statusAt: serverTimestamp() });
    await assertFails(updateDoc(feedback(as(env, ADULT)), answer('done')));
    await assertFails(updateDoc(feedback(as(env, SECOND_ADMIN)), answer('done')));
    await assertFails(updateDoc(feedback(as(env, ADMIN)), { text: 'Autre chose' }));
    await assertFails(updateDoc(feedback(as(env, ADMIN)), { ...answer('done'), text: 'Autre chose' }));
    await assertFails(updateDoc(feedback(as(env, ADMIN)), answer('maybe')));
    await assertFails(updateDoc(feedback(as(env, ADMIN)), answer('done', 'm'.repeat(301))));
    await assertFails(updateDoc(feedback(as(env, ADMIN)), { status: 'done', note: null, statusAt: Timestamp.fromMillis(0) }));
    await assertSucceeds(updateDoc(feedback(as(env, ADMIN)), answer('planned', 'Pour la prochaine version')));
    await assertSucceeds(updateDoc(feedback(as(env, ADMIN)), answer('done')));
    await assertSucceeds(updateDoc(feedback(as(env, ADMIN)), answer('new')));
  });

  test('its author or the admin principal deletes it', async () => {
    await seedFeedback();
    await seedFeedback(ADULT.memberId, 'f2');
    await assertFails(deleteDoc(feedback(as(env, OTHER_ADULT))));
    await assertFails(deleteDoc(feedback(as(env, SECOND_ADMIN))));
    await assertSucceeds(deleteDoc(feedback(as(env, ADULT))));
    await assertSucceeds(deleteDoc(feedback(as(env, ADMIN), 'f2')));
  });
});
