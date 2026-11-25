// « Mes notes » (récit 114, lot A4): a private notepad about another member. Fictional family from
// helpers.mjs: admin1 (admin principal), adult1, adult2 in h1, adult3 in h2.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
});

const note = (db, author, aboutId) => doc(db, `members/${author.memberId}/notes/${aboutId}`);
const text = (t = 'Aime le thé vert, pointure 38.') => ({ text: t, updatedAt: serverTimestamp() });

describe('« Mes notes » (récit 114)', () => {
  test('its author writes, reads and removes a note about another member', async () => {
    const db = as(env, ADULT);
    await assertSucceeds(setDoc(note(db, ADULT, 'adult3'), text()));
    await assertSucceeds(getDoc(note(db, ADULT, 'adult3')));
    await assertSucceeds(getDocs(collection(db, `members/${ADULT.memberId}/notes`)));
    await assertSucceeds(setDoc(note(db, ADULT, 'adult3'), text('')));
    await assertSucceeds(deleteDoc(note(db, ADULT, 'adult3')));
  });

  test('never read by the person it is about, nor the admin principal, nor anyone else', async () => {
    await assertSucceeds(setDoc(note(as(env, ADULT), ADULT, 'adult3'), text()));
    await assertFails(getDoc(note(as(env, NEIGHBOUR), ADULT, 'adult3')));
    await assertFails(getDoc(note(as(env, ADMIN), ADULT, 'adult3')));
    await assertFails(getDocs(collection(as(env, ADMIN), `members/${ADULT.memberId}/notes`)));
    await assertFails(getDoc(note(as(env, OTHER_ADULT), ADULT, 'adult3')));
    await assertFails(getDoc(note(as(env, STRANGER), ADULT, 'adult3')));
    await assertFails(deleteDoc(note(as(env, ADMIN), ADULT, 'adult3')));
  });

  test('not in the notepad of someone else, not about oneself or someone unknown, 2000 characters at most', async () => {
    await assertFails(setDoc(note(as(env, ADMIN), ADULT, 'adult3'), text()));
    const db = as(env, ADULT);
    await assertFails(setDoc(note(db, ADULT, 'adult1'), text()));
    await assertFails(setDoc(note(db, ADULT, 'nobody'), text()));
    await assertFails(setDoc(note(db, ADULT, 'adult3'), text('x'.repeat(2001))));
    await assertSucceeds(setDoc(note(db, ADULT, 'adult2'), text('x'.repeat(2000))));
    await assertFails(setDoc(note(db, ADULT, 'adult3'), { ...text(), shared: true }));
    await assertFails(setDoc(note(db, ADULT, 'adult3'), { text: 42, updatedAt: serverTimestamp() }));
    await assertFails(setDoc(note(db, ADULT, 'adult3'), { text: 'x', updatedAt: new Date(0) }));
  });
});
