// « Qui apporte quoi » (PRD, récit 4 bis): every member with the app adds a line or takes one; a line
// taken shows who took it, who can let it go. Fictional family: adult1 and adult2 in h1, admin1
// (admin principal), kid1 (child with the app, parent adult1). The event e1 is written by adult2.
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
    await setDoc(doc(db, 'events/e1'), {
      title: 'Repas', date: '2026-12-24', time: null, place: null, note: null,
      targetType: 'family', targetMemberId: null, householdId: null,
      authorId: 'adult2', authorName: 'adult2',
      createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
  });
});

const item = (db, id = 'b1', eventId = 'e1') => doc(db, `events/${eventId}/bring/${id}`);

/** A new line written by [by], taken by them when [take] is set. */
function line(by, label = 'Le pain', take = false, overrides = {}) {
  return {
    label, authorId: by.memberId,
    takerId: take ? by.memberId : null, takerName: take ? by.memberId : null,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  };
}

async function seedLine(by, takerId = null) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(item(ctx.firestore()), {
      label: 'Le dessert', authorId: by, takerId, takerName: takerId,
      createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
    });
  });
}

const take = (who) => ({ takerId: who.memberId, takerName: who.memberId, updatedAt: serverTimestamp() });
const release = () => ({ takerId: null, takerName: null, updatedAt: serverTimestamp() });

describe('adding a line', () => {
  test('a line may join a recipe of the book, a short id only, and never anything else', async () => {
    await assertSucceeds(setDoc(item(as(env, ADULT), 'r1'), line(ADULT, 'La tarte', true, { recipeId: 'recipe1' })));
    await assertFails(setDoc(item(as(env, ADULT), 'r2'), line(ADULT, 'La tarte', true, { recipeId: '' })));
    await assertFails(setDoc(item(as(env, ADULT), 'r3'), line(ADULT, 'La tarte', true, { recipeId: 'x'.repeat(65) })));
    await assertFails(setDoc(item(as(env, ADULT), 'r4'), line(ADULT, 'La tarte', true, { recipeId: 42 })));
    await assertFails(setDoc(item(as(env, ADULT), 'r5'), line(ADULT, 'La tarte', true, { note: 'non' })));
  });

  test('the recipe of a line does not change when someone takes it', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(item(ctx.firestore()), {
        label: 'La tarte', authorId: 'adult2', takerId: null, takerName: null, recipeId: 'recipe1',
        createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
      });
    });
    await assertSucceeds(updateDoc(item(as(env, ADULT)), take(ADULT)));
    await assertFails(updateDoc(item(as(env, ADULT)), { ...release(), recipeId: 'recipe2' }));
  });

  test('a member adds a free line, or one they take at once; a child with the app too', async () => {
    await assertSucceeds(setDoc(item(as(env, ADULT), 'b1'), line(ADULT)));
    await assertSucceeds(setDoc(item(as(env, ADULT), 'b2'), line(ADULT, 'Le fromage', true)));
    await assertSucceeds(setDoc(item(as(env, KID), 'b3'), line(KID, 'Les boissons', true)));
  });

  test('a line for someone else, an empty or too long label, or a missing event is refused', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(item(db), line(ADULT, 'Le pain', false, { takerId: 'adult2', takerName: 'adult2' })));
    await assertFails(setDoc(item(db), line(ADULT, 'Le pain', false, { authorId: 'adult2' })));
    await assertFails(setDoc(item(db), line(ADULT, '   ')));
    await assertFails(setDoc(item(db), line(ADULT, 'x'.repeat(61))));
    await assertFails(setDoc(item(db, 'b1', 'nope'), line(ADULT)));
    await assertFails(setDoc(item(db), line(ADULT, 'Le pain', false, { extra: 1 })));
  });

  test('every member reads the list, a stranger does not', async () => {
    await seedLine('adult2');
    await assertSucceeds(getDoc(item(as(env, KID))));
    await assertFails(getDoc(item(as(env, STRANGER))));
  });
});

describe('taking and letting go', () => {
  test('a member takes a free line, then lets it go', async () => {
    await seedLine('adult2');
    const db = as(env, ADULT);
    await assertSucceeds(updateDoc(item(db), take(ADULT)));
    await assertSucceeds(updateDoc(item(db), release()));
  });

  test('nobody takes a line already taken, nor in someone else\'s name', async () => {
    await seedLine('adult2', 'adult1');
    await assertFails(updateDoc(item(as(env, KID)), take(KID)));
    await seedLine('adult2');
    await assertFails(updateDoc(item(as(env, KID)), { takerId: 'adult1', takerName: 'adult1', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(item(as(env, KID)), { label: 'Autre chose', updatedAt: serverTimestamp() }));
  });

  test('the line\'s author and those who manage the event free a line; another member does not', async () => {
    await seedLine('adult1', 'adult1');
    await assertFails(updateDoc(item(as(env, KID)), release()));
    await assertSucceeds(updateDoc(item(as(env, OTHER_ADULT)), release()));
    await seedLine('adult1', 'adult1');
    await assertSucceeds(updateDoc(item(as(env, ADMIN)), release()));
    await seedLine('kid1', 'adult1');
    await assertSucceeds(updateDoc(item(as(env, KID)), release()));
  });
});

describe('deleting a line', () => {
  test('its author or those who manage the event delete it; another member does not', async () => {
    await seedLine('adult1');
    await assertFails(deleteDoc(item(as(env, KID))));
    await assertSucceeds(deleteDoc(item(as(env, ADULT))));
    await seedLine('adult1');
    await assertSucceeds(deleteDoc(item(as(env, OTHER_ADULT))));
  });
});
