// Annonces de l'admin (PRD, récit 69): only the admin principal sends one, with the module on and
// never during the mode présentation; each member ticked reads it; nobody changes it.
// Fictional family: adult1 and adult2 in h1 with admin2 (chef of h1), admin1 (admin principal).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, OTHER_ADULT, SECOND_ADMIN, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'config/app'), { features: { adminAnnouncements: true } });
  });
});

const announcement = (db, id = 'a1') => doc(db, `announcements/${id}`);

function written(by, overrides = {}) {
  return {
    title: 'Repas de dimanche', text: 'On se retrouve à midi.', urgent: false,
    recipientIds: [ADULT.memberId, OTHER_ADULT.memberId], authorId: by.memberId, authorName: by.memberId,
    createdAt: serverTimestamp(), ...overrides,
  };
}

async function seedAnnouncement() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(announcement(ctx.firestore()), {
      ...written(ADMIN), recipientIds: [ADULT.memberId], createdAt: Timestamp.fromMillis(0), delivery: 'sent',
    });
  });
}

describe('sending', () => {
  test('the admin principal sends one', async () => {
    await assertSucceeds(setDoc(announcement(as(env, ADMIN)), written(ADMIN)));
  });

  test('a chef de famille or an adult cannot', async () => {
    await assertFails(setDoc(announcement(as(env, SECOND_ADMIN)), written(SECOND_ADMIN)));
    await assertFails(setDoc(announcement(as(env, ADULT)), written(ADULT)));
  });

  test('not with the module off', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'config/app'), { features: {} });
    });
    await assertFails(setDoc(announcement(as(env, ADMIN)), written(ADMIN)));
  });

  test('a config/presentation left on stops nothing any more (PRODUIT-132)', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'config/presentation'), { on: true, until: null });
    });
    await assertSucceeds(setDoc(announcement(as(env, ADMIN)), written(ADMIN)));
  });

  test('a title of 60 characters and a message of 300 at most, never empty', async () => {
    const db = as(env, ADMIN);
    await assertSucceeds(setDoc(announcement(db, 'ok'), written(ADMIN, { title: 't'.repeat(60), text: 'm'.repeat(300) })));
    await assertFails(setDoc(announcement(db, 'a2'), written(ADMIN, { title: 't'.repeat(61) })));
    await assertFails(setDoc(announcement(db, 'a3'), written(ADMIN, { text: 'm'.repeat(301) })));
    await assertFails(setDoc(announcement(db, 'a4'), written(ADMIN, { title: '' })));
    await assertFails(setDoc(announcement(db, 'a5'), written(ADMIN, { recipientIds: [] })));
    await assertFails(setDoc(announcement(db, 'a6'), written(ADMIN, { delivery: 'sent' })));
  });

  test('in their own name only', async () => {
    await assertFails(setDoc(announcement(as(env, ADMIN)), written(ADMIN, { authorId: ADULT.memberId })));
    await assertFails(setDoc(announcement(as(env, ADMIN)), written(ADMIN, { authorName: 'Quelqu\'un' })));
  });
});

describe('reading', () => {
  test('a member ticked and the admin principal read it, not the others', async () => {
    await seedAnnouncement();
    await assertSucceeds(getDoc(announcement(as(env, ADULT))));
    await assertSucceeds(getDoc(announcement(as(env, ADMIN))));
    await assertFails(getDoc(announcement(as(env, OTHER_ADULT))));
  });

  test('nobody changes or removes it', async () => {
    await seedAnnouncement();
    await assertFails(updateDoc(announcement(as(env, ADMIN)), { title: 'Autre' }));
    await assertFails(deleteDoc(announcement(as(env, ADMIN))));
  });
});
