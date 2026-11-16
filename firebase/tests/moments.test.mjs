// « Pensez à moi » rules (PRD, récit 43). Fictional family from helpers.mjs, plus kid1 (8 years
// old) and teen1 (16, with the app), both children of adult1.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  Timestamp, collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, updateDoc, where,
} from 'firebase/firestore';
import { ADULT, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const TEEN = { memberId: 'teen1', email: 'teen1@example.com', uid: 'uid-teen1' };

function yearsAgo(years) {
  const d = new Date();
  d.setFullYear(d.getFullYear() - years);
  return d.toISOString().slice(0, 10);
}

function moment(by, overrides = {}) {
  return {
    forMemberId: by.memberId, authorId: by.memberId, authorName: by.memberId, title: 'Examen de piano',
    day: '2026-12-12', status: 'accepted', createdAt: serverTimestamp(), ...overrides,
  };
}

function cheer(by, momentId = 'm1', forMemberId = ADULT.memberId) {
  return { momentId, forMemberId, authorId: by.memberId, authorName: by.memberId, text: 'Tu vas y arriver !', createdAt: serverTimestamp() };
}

async function stored(data, id = 'm1') {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `moments/${id}`), { ...data, createdAt: Timestamp.fromMillis(0) });
  });
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${TEEN.email}`), { memberId: TEEN.memberId });
    await setDoc(doc(db, `members/${TEEN.memberId}`), adultDoc(TEEN, { profile: 'childWithApp', parentIds: ['adult1'], birthDate: yearsAgo(16) }));
    await setDoc(doc(db, 'members/kid1'), adultDoc({ memberId: 'kid1' }, {
      email: null, profile: 'childWithoutApp', parentIds: ['adult1'], birthDate: yearsAgo(8),
    }));
  });
});

describe('moments', () => {
  test('a member notes their own moment, signed with their name', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'moments/m1'), moment(ADULT)));
    await assertFails(setDoc(doc(db, 'moments/m2'), moment(ADULT, { authorName: 'Quelqu’un' })));
    await assertFails(setDoc(doc(db, 'moments/m3'), moment(ADULT, { status: 'pending' })));
    await assertFails(setDoc(doc(db, 'moments/m4'), moment(ADULT, { title: '' })));
    await assertFails(setDoc(doc(db, 'moments/m5'), moment(ADULT, { day: '12/12' })));
    await assertFails(setDoc(doc(db, 'moments/m6'), moment(ADULT, { startedAt: serverTimestamp() })));
    await assertFails(setDoc(doc(as(env, STRANGER), 'moments/m7'), moment({ memberId: 'stranger' })));
  });

  test('a parent notes it for a young child; for a teenager, it waits for them', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'moments/m1'), moment(ADULT, { forMemberId: 'kid1' })));
    await assertFails(setDoc(doc(db, 'moments/m2'), moment(ADULT, { forMemberId: 'kid1', status: 'pending' })));
    await assertFails(setDoc(doc(db, 'moments/m3'), moment(ADULT, { forMemberId: TEEN.memberId })));
    await assertSucceeds(setDoc(doc(db, 'moments/m4'), moment(ADULT, { forMemberId: TEEN.memberId, status: 'pending' })));
    await assertFails(setDoc(doc(as(env, OTHER_ADULT), 'moments/m5'), moment(OTHER_ADULT, { forMemberId: 'kid1' })));
  });

  test('the teenager accepts it or takes it back; nobody else accepts it', async () => {
    await stored(moment(ADULT, { forMemberId: TEEN.memberId, status: 'pending' }));
    await assertFails(updateDoc(doc(as(env, ADULT), 'moments/m1'), { status: 'accepted' }));
    const teen = as(env, TEEN);
    await assertFails(updateDoc(doc(teen, 'moments/m1'), { status: 'accepted', title: 'Autre' }));
    await assertSucceeds(updateDoc(doc(teen, 'moments/m1'), { status: 'accepted' }));
    await assertFails(updateDoc(doc(teen, 'moments/m1'), { status: 'pending' }));
    await assertSucceeds(deleteDoc(doc(teen, 'moments/m1')));
  });

  test('a moment waiting for the teenager stays between them, its author and their parents', async () => {
    await stored(moment(ADULT, { forMemberId: TEEN.memberId, status: 'pending' }), 'm1');
    await stored(moment(TEEN), 'm2');
    await assertFails(getDoc(doc(as(env, OTHER_ADULT), 'moments/m1')));
    await assertSucceeds(getDoc(doc(as(env, TEEN), 'moments/m1')));
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'moments/m1')));
    const moments = collection(as(env, OTHER_ADULT), 'moments');
    await assertFails(getDocs(query(moments, where('forMemberId', '==', TEEN.memberId))));
    await assertSucceeds(getDocs(query(moments, where('forMemberId', '==', TEEN.memberId), where('status', '==', 'accepted'))));
    await assertSucceeds(getDocs(query(collection(as(env, ADULT), 'moments'), where('forMemberId', '==', TEEN.memberId))));
  });

  test('born a 29 February: 15 years old on 1 March', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'members/kid1'), adultDoc({ memberId: 'kid1' }, {
        email: null, profile: 'childWithoutApp', parentIds: ['adult1'], birthDate: '2008-02-29',
      }));
    });
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, 'moments/m1'), moment(ADULT, { forMemberId: 'kid1', status: 'pending' })));
  });

  test('the family reads them; the author or the person removes one, nobody else', async () => {
    await stored(moment(ADULT));
    await assertSucceeds(getDoc(doc(as(env, OTHER_ADULT), 'moments/m1')));
    await assertFails(deleteDoc(doc(as(env, OTHER_ADULT), 'moments/m1')));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), 'moments/m1')));
  });
});

describe('encouragements', () => {
  test('on the day, a member writes one to the person, signed with their name', async () => {
    await stored({ ...moment(ADULT), startedAt: Timestamp.fromMillis(Date.now() - 3600 * 1000) });
    const db = as(env, OTHER_ADULT);
    await assertSucceeds(setDoc(doc(db, 'momentCheers/m1-adult2'), cheer(OTHER_ADULT)));
    await assertFails(setDoc(doc(db, 'momentCheers/m1-other'), cheer(OTHER_ADULT)));
    await assertFails(setDoc(doc(as(env, ADULT), 'momentCheers/m1-adult1'), cheer(ADULT)));
    await assertFails(setDoc(doc(as(env, TEEN), 'momentCheers/m1-teen1'), cheer(TEEN, 'm1', 'kid1')));
    await assertSucceeds(deleteDoc(doc(db, 'momentCheers/m1-adult2')));
  });

  test('not before the day, not after 24 h, not on a moment waiting for the teenager', async () => {
    await stored(moment(ADULT), 'm1');
    await stored({ ...moment(ADULT), startedAt: Timestamp.fromMillis(Date.now() - 25 * 3600 * 1000) }, 'm2');
    await stored({ ...moment(ADULT, { forMemberId: TEEN.memberId, status: 'pending' }), startedAt: Timestamp.now() }, 'm3');
    const db = as(env, OTHER_ADULT);
    await assertFails(setDoc(doc(db, 'momentCheers/m1-adult2'), cheer(OTHER_ADULT, 'm1')));
    await assertFails(setDoc(doc(db, 'momentCheers/m2-adult2'), cheer(OTHER_ADULT, 'm2')));
    await assertFails(setDoc(doc(db, 'momentCheers/m3-adult2'), cheer(OTHER_ADULT, 'm3', TEEN.memberId)));
    await assertFails(setDoc(doc(db, 'momentCheers/m9-adult2'), cheer(OTHER_ADULT, 'm9')));
  });
});
