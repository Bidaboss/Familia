// Récit 49 rules: calendarFeeds/{memberId} holds the secret token of an adult's calendar link.
// Only that adult reads or writes it (a fresh token cuts the old link); the admin principal can
// cut anyone's link; a child never has one.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, SECOND_ADMIN, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };
const TOKEN = 'A'.repeat(42) + '_';
const OTHER_TOKEN = 'b'.repeat(42) + '-';
const feed = (token = TOKEN) => ({ token, updatedAt: serverTimestamp() });

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: [ADULT.memberId] }));
    await setDoc(doc(db, `calendarFeeds/${OTHER_ADULT.memberId}`), { token: OTHER_TOKEN, updatedAt: new Date() });
  });
});

describe('calendarFeeds', () => {
  test('an adult creates, reads and renews their own link', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(doc(db, `calendarFeeds/${ADULT.memberId}`), feed()));
    await assertSucceeds(getDoc(doc(db, `calendarFeeds/${ADULT.memberId}`)));
    await assertSucceeds(setDoc(doc(db, `calendarFeeds/${ADULT.memberId}`), feed(OTHER_TOKEN)));
    await assertSucceeds(deleteDoc(doc(db, `calendarFeeds/${ADULT.memberId}`)));
  });

  test('nobody reads or writes the link of another member', async () => {
    const db = as(env, ADULT);
    await assertFails(getDoc(doc(db, `calendarFeeds/${OTHER_ADULT.memberId}`)));
    await assertFails(setDoc(doc(db, `calendarFeeds/${OTHER_ADULT.memberId}`), feed()));
    await assertFails(deleteDoc(doc(db, `calendarFeeds/${OTHER_ADULT.memberId}`)));
    await assertFails(getDoc(doc(as(env, ADMIN), `calendarFeeds/${OTHER_ADULT.memberId}`)));
    await assertFails(deleteDoc(doc(as(env, SECOND_ADMIN), `calendarFeeds/${OTHER_ADULT.memberId}`)));
  });

  test('the admin principal cuts the link of anyone', async () => {
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), `calendarFeeds/${OTHER_ADULT.memberId}`)));
  });

  test('a child has no link', async () => {
    await assertFails(setDoc(doc(as(env, KID), `calendarFeeds/${KID.memberId}`), feed()));
  });

  test('the token is 43 url-safe characters and the document holds nothing else', async () => {
    const ref = doc(as(env, ADULT), `calendarFeeds/${ADULT.memberId}`);
    await assertFails(setDoc(ref, feed('court')));
    await assertFails(setDoc(ref, feed('A'.repeat(42) + '=')));
    await assertFails(setDoc(ref, feed('A'.repeat(44))));
    await assertFails(setDoc(ref, { ...feed(), memberId: ADULT.memberId }));
    await assertFails(setDoc(ref, { token: TOKEN, updatedAt: new Date(2020, 0, 1) }));
  });

  test('a stranger does nothing', async () => {
    const db = as(env, STRANGER);
    await assertFails(getDoc(doc(db, `calendarFeeds/${OTHER_ADULT.memberId}`)));
    await assertFails(setDoc(doc(db, `calendarFeeds/${OTHER_ADULT.memberId}`), feed()));
  });
});
