// Profile photos (PRD, récit 3 bis): the member themself, an adult of the same household for a
// child, or the admin principal changes a photo; only the family sees them.
import { after, afterEach, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Bytes, deleteDoc, doc, getDoc, serverTimestamp, setDoc, writeBatch } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
afterEach(async () => { await env.clearFirestore(); });

// A child with the app in h1 and a child without the app in h2.
const CHILD = { memberId: 'child1', email: 'child1@example.com', uid: 'uid-child1' };
const SMALL = { memberId: 'child2' };

beforeEach(async () => {
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${CHILD.email}`), { memberId: CHILD.memberId });
    await setDoc(doc(db, `members/${CHILD.memberId}`), adultDoc(CHILD, { profile: 'childWithApp', parentIds: [ADULT.memberId] }));
    await setDoc(doc(db, `members/${SMALL.memberId}`), adultDoc(SMALL, {
      email: null, profile: 'childWithoutApp', householdId: 'h2', parentIds: [NEIGHBOUR.memberId],
    }));
    await setDoc(doc(db, 'memberPhotos/adult2'), { jpeg: jpeg(), updatedAt: new Date() });
  });
});

function jpeg(size = 1000) {
  return Bytes.fromUint8Array(new Uint8Array(size).fill(7));
}

function photo(db, memberId, data = { jpeg: jpeg(), updatedAt: serverTimestamp() }) {
  return setDoc(doc(db, `memberPhotos/${memberId}`), data);
}

describe('memberPhotos', () => {
  test('a member changes and removes their own photo', async () => {
    await assertSucceeds(photo(as(env, ADULT), ADULT.memberId));
    await assertSucceeds(deleteDoc(doc(as(env, ADULT), `memberPhotos/${ADULT.memberId}`)));
  });

  test('a child with the app changes their own photo', async () => {
    await assertSucceeds(photo(as(env, CHILD), CHILD.memberId));
  });

  test('any adult of the household changes the photo of a child, not of another adult', async () => {
    await assertSucceeds(photo(as(env, OTHER_ADULT), CHILD.memberId));
    await assertFails(photo(as(env, ADULT), OTHER_ADULT.memberId));
    await assertFails(deleteDoc(doc(as(env, ADULT), 'memberPhotos/adult2')));
  });

  test("an adult of another household never changes a child's photo", async () => {
    await assertFails(photo(as(env, ADULT), SMALL.memberId));
    await assertSucceeds(photo(as(env, NEIGHBOUR), SMALL.memberId));
  });

  test("a child never changes another member's photo", async () => {
    await assertFails(photo(as(env, CHILD), ADULT.memberId));
  });

  test('the admin principal changes anyone', async () => {
    await assertSucceeds(photo(as(env, ADMIN), NEIGHBOUR.memberId));
    await assertSucceeds(deleteDoc(doc(as(env, ADMIN), 'memberPhotos/adult2')));
  });

  test('only the family reads the photos', async () => {
    await assertSucceeds(getDoc(doc(as(env, CHILD), 'memberPhotos/adult2')));
    await assertFails(getDoc(doc(as(env, STRANGER), 'memberPhotos/adult2')));
    await assertFails(getDoc(doc(as(env, ADULT, { verified: false }), 'memberPhotos/adult2')));
  });

  test('a photo is a small JPEG with the server time, and nothing else', async () => {
    const db = as(env, ADULT);
    await assertFails(photo(db, ADULT.memberId, { jpeg: jpeg(150001), updatedAt: serverTimestamp() }));
    await assertFails(photo(db, ADULT.memberId, { jpeg: jpeg(0), updatedAt: serverTimestamp() }));
    await assertFails(photo(db, ADULT.memberId, { jpeg: 'not bytes', updatedAt: serverTimestamp() }));
    await assertFails(photo(db, ADULT.memberId, { jpeg: jpeg(), updatedAt: new Date(2020, 0, 1) }));
    await assertFails(photo(db, ADULT.memberId, { jpeg: jpeg(), updatedAt: serverTimestamp(), extra: true }));
    await assertSucceeds(photo(db, ADULT.memberId, { jpeg: jpeg(150000), updatedAt: serverTimestamp() }));
  });

  test('no photo for a member who does not exist', async () => {
    await assertFails(photo(as(env, ADMIN), 'ghost'));
  });

  test('removing a member removes their photo in the same batch', async () => {
    const db = as(env, ADMIN);
    const batch = writeBatch(db);
    batch.delete(doc(db, 'members/adult2'));
    batch.delete(doc(db, `memberEmails/${OTHER_ADULT.email}`));
    batch.delete(doc(db, 'memberPhotos/adult2'));
    await assertSucceeds(batch.commit());
  });

  test("the admin principal removes a parent, updates the child and drops the photo in one batch", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `members/${CHILD.memberId}`), adultDoc(CHILD, {
        profile: 'childWithApp', parentIds: [ADULT.memberId, OTHER_ADULT.memberId],
      }));
      await setDoc(doc(ctx.firestore(), `memberPhotos/${ADULT.memberId}`), { jpeg: jpeg(), updatedAt: new Date() });
    });
    const db = as(env, ADMIN);
    const batch = writeBatch(db);
    batch.delete(doc(db, `members/${ADULT.memberId}`));
    batch.delete(doc(db, `memberEmails/${ADULT.email}`));
    batch.set(doc(db, `members/${CHILD.memberId}`), adultDoc(CHILD, { profile: 'childWithApp', parentIds: [OTHER_ADULT.memberId] }));
    batch.delete(doc(db, `memberPhotos/${ADULT.memberId}`));
    await assertSucceeds(batch.commit());
  });
});
