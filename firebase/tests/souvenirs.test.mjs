// Souvenirs rules (PRD, récit 34). Fictional family from helpers.mjs.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where } from 'firebase/firestore';
import { ADULT, OTHER_ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const souvenir = (db, member, key) => doc(db, `members/${member.memberId}/souvenirs/${key}`);

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(souvenir(db, ADULT, 'firstGoodNews'), { key: 'firstGoodNews', earnedAt: new Date(0), shown: false });
    await setDoc(souvenir(db, ADULT, 'oneYear'), { key: 'oneYear', earnedAt: new Date(0), shown: true });
    await setDoc(doc(db, `souvenirCounters/${ADULT.memberId}`), { reactions: 3 });
    await setDoc(doc(db, 'souvenirMarks/reaction-n1-adult1'), { memberId: ADULT.memberId });
  });
});

describe('souvenirs', () => {
  test('a souvenir (« Réussite ») is public: the whole family reads it, shown or not (récit 114)', async () => {
    await assertSucceeds(getDoc(souvenir(as(env, ADULT), ADULT, 'firstGoodNews')));
    await assertSucceeds(getDoc(souvenir(as(env, OTHER_ADULT), ADULT, 'firstGoodNews')));
    await assertSucceeds(getDoc(souvenir(as(env, OTHER_ADULT), ADULT, 'oneYear')));
    await assertFails(getDoc(souvenir(as(env, STRANGER), ADULT, 'oneYear')));
    await assertSucceeds(getDocs(collection(as(env, OTHER_ADULT), `members/${ADULT.memberId}/souvenirs`)));
    // An app not yet updated still asks for shown == true.
    await assertSucceeds(getDocs(query(collection(as(env, OTHER_ADULT), `members/${ADULT.memberId}/souvenirs`), where('shown', '==', true))));
  });

  test('nobody hides it any more, not even its member', async () => {
    const mine = souvenir(as(env, ADULT), ADULT, 'oneYear');
    await assertFails(updateDoc(mine, { shown: false }));
    await assertFails(updateDoc(mine, { earnedAt: new Date() }));
    await assertFails(updateDoc(souvenir(as(env, OTHER_ADULT), ADULT, 'oneYear'), { shown: false }));
  });

  test('only the server gives or takes a souvenir', async () => {
    await assertFails(setDoc(souvenir(as(env, ADULT), ADULT, 'reactions10'), { key: 'reactions10', earnedAt: new Date(), shown: false }));
    await assertFails(deleteDoc(souvenir(as(env, ADULT), ADULT, 'firstGoodNews')));
  });

  test('the counters are read by their member only, and the marks by nobody', async () => {
    await assertSucceeds(getDoc(doc(as(env, ADULT), `souvenirCounters/${ADULT.memberId}`)));
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), `souvenirCounters/${ADULT.memberId}`)));
    await assertFails(setDoc(doc(as(env, ADULT), `souvenirCounters/${ADULT.memberId}`), { reactions: 99 }));
    await assertFails(getDoc(doc(as(env, ADULT), 'souvenirMarks/reaction-n1-adult1')));
  });

  test('the « Nouveau souvenir » notice can be turned off', async () => {
    const settings = doc(as(env, ADULT), `members/${ADULT.memberId}/settings/notifications`);
    await assertSucceeds(setDoc(settings, { souvenirs: false }));
    await assertFails(setDoc(settings, { souvenirs: 'non' }));
  });
});
