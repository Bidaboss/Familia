// Question de la semaine rules (PRD, récit 37). Fictional family from helpers.mjs, plus kid1 (child
// with the app, parent adult1). The question of week w1 is revealed in the future, w0 in the past.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADULT, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

function answer(by, overrides = {}) {
  return { memberId: by.memberId, memberName: by.memberId, text: 'La pêche avec Papi', skipped: false, updatedAt: serverTimestamp(), ...overrides };
}

const answerRef = (db, who, week = 'w1') => doc(db, `weeklyQuestions/${week}/answers/${who.memberId ?? who}`);

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    const question = { text: 'Ton petit-déjeuner idéal ?', bankId: 'q14', proposedByName: null, answerCount: 1 };
    await setDoc(doc(db, 'weeklyQuestions/w1'), { ...question, weekId: 'w1', revealAt: Timestamp.fromMillis(Date.now() + 86_400_000) });
    await setDoc(doc(db, 'weeklyQuestions/w0'), { ...question, weekId: 'w0', revealAt: Timestamp.fromMillis(Date.now() - 86_400_000) });
    for (const week of ['w0', 'w1']) {
      await setDoc(answerRef(db, OTHER_ADULT, week), { ...answer(OTHER_ADULT), updatedAt: Timestamp.fromMillis(0) });
    }
  });
});

describe('the question', () => {
  test('every member reads it, nobody writes it from the app', async () => {
    await assertSucceeds(getDoc(doc(as(env, KID), 'weeklyQuestions/w1')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'weeklyQuestions/w1')));
    await assertFails(setDoc(doc(as(env, ADULT), 'weeklyQuestions/w2'), { text: 'Ma question', revealAt: Timestamp.now() }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'weeklyQuestions/w1'), { answerCount: 12 }));
  });
});

describe('answering', () => {
  test('a member answers, a child with the app too, each for themselves only', async () => {
    await assertSucceeds(setDoc(answerRef(as(env, ADULT), ADULT), answer(ADULT)));
    await assertSucceeds(setDoc(answerRef(as(env, KID), KID), answer(KID)));
    await assertFails(setDoc(answerRef(as(env, ADULT), KID), answer(KID)));
    await assertFails(setDoc(answerRef(as(env, ADULT), ADULT), answer(ADULT, { memberName: 'Quelqu’un' })));
    await assertFails(setDoc(answerRef(as(env, STRANGER), 'stranger'), answer({ memberId: 'stranger' })));
  });

  test('« Passer » has no text, an answer has a short one', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(answerRef(db, ADULT), answer(ADULT, { skipped: true, text: null })));
    await assertFails(setDoc(answerRef(db, ADULT), answer(ADULT, { skipped: true })));
    await assertFails(setDoc(answerRef(db, ADULT), answer(ADULT, { text: '   ' })));
    await assertFails(setDoc(answerRef(db, ADULT), answer(ADULT, { text: 'x'.repeat(501) })));
    await assertFails(setDoc(answerRef(db, ADULT), answer(ADULT, { photo: 'p1' })));
  });

  test('an answer may name one photo, « Passer » never does', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(answerRef(db, ADULT), answer(ADULT, { photoId: 'Ab12' })));
    await assertSucceeds(setDoc(answerRef(db, ADULT), answer(ADULT, { photoId: null })));
    await assertFails(setDoc(answerRef(db, ADULT), answer(ADULT, { skipped: true, text: null, photoId: 'Ab12' })));
    await assertFails(setDoc(answerRef(db, ADULT), answer(ADULT, { photoId: '../x' })));
    await assertFails(setDoc(answerRef(db, ADULT), answer(ADULT, { photoId: 12 })));
    await assertFails(setDoc(answerRef(db, ADULT), answer(ADULT, { updatedAt: Timestamp.fromMillis(0) })));
  });

  test('only a question the server published', async () => {
    await assertFails(setDoc(answerRef(as(env, ADULT), ADULT, 'w9'), answer(ADULT)));
  });

  test('one changes or removes one\'s own answer, not someone else\'s', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(answerRef(db, ADULT), answer(ADULT)));
    await assertSucceeds(setDoc(answerRef(db, ADULT), answer(ADULT, { text: 'Des crêpes' })));
    await assertSucceeds(deleteDoc(answerRef(db, ADULT)));
    await assertFails(deleteDoc(answerRef(db, OTHER_ADULT)));
    await assertFails(setDoc(answerRef(db, OTHER_ADULT), answer(OTHER_ADULT)));
  });
});

describe('reading the answers', () => {
  test('before answering, only one\'s own; the others stay hidden until 48 h', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(getDoc(answerRef(db, ADULT)));
    await assertFails(getDoc(answerRef(db, OTHER_ADULT)));
    await assertFails(getDocs(collection(db, 'weeklyQuestions/w1/answers')));
  });

  test('once answered, or passed, one reads everyone\'s', async () => {
    const db = as(env, ADULT);
    await setDoc(answerRef(db, ADULT), answer(ADULT, { skipped: true, text: null }));
    await assertSucceeds(getDocs(collection(db, 'weeklyQuestions/w1/answers')));
    await assertSucceeds(getDoc(answerRef(db, OTHER_ADULT)));
  });

  test('48 h after the question, everyone reads the answers', async () => {
    await assertSucceeds(getDocs(collection(as(env, KID), 'weeklyQuestions/w0/answers')));
    await assertFails(getDocs(collection(as(env, STRANGER), 'weeklyQuestions/w0/answers')));
  });
});
