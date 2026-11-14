// Lot 10 rules: config/app holds the latest version and the link of its APK. Every member reads
// it; nobody writes it from the app (Kévin edits it in the Firebase console).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc } from 'firebase/firestore';
import { ADMIN, ADULT, STRANGER, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const config = { latestVersionCode: 2, latestVersionName: '0.2.0', downloadUrl: 'https://example.com/familia.apk' };

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'config/app'), config));
});

describe('config/app', () => {
  test('every member reads it', async () => {
    await assertSucceeds(getDoc(doc(as(env, ADULT), 'config/app')));
  });

  test('a stranger does not', async () => {
    await assertFails(getDoc(doc(as(env, STRANGER), 'config/app')));
  });

  test('nobody writes it from the app, not even an admin', async () => {
    const db = as(env, ADMIN);
    await assertFails(setDoc(doc(db, 'config/app'), { ...config, downloadUrl: 'https://evil.example/x.apk' }));
    await assertFails(deleteDoc(doc(db, 'config/app')));
  });

  test('other config documents stay closed', async () => {
    await assertFails(getDoc(doc(as(env, ADULT), 'config/other')));
  });
});
