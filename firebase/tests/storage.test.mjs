// Cloud Storage rules: closed by default. Each module that opens a folder adds its tests here.
// Fictional data only.
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { ref, uploadString, getBytes } from 'firebase/storage';
import { ADULT, seed, STRANGER } from './helpers.mjs';

const rules = (name) => readFileSync(new URL(`../${name}`, import.meta.url), 'utf8');
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-familia',
    firestore: { rules: rules('firestore.rules') },
    storage: { rules: rules('storage.rules') },
  });
});
after(() => env.cleanup());
beforeEach(async () => {
  await env.clearFirestore();
  await env.clearStorage();
  await seed(env);
});

function storageAs(account) {
  return env.authenticatedContext(account.uid, { email: account.email, email_verified: true }).storage();
}

const jpeg = { contentType: 'image/jpeg' };

describe('storage closed by default', () => {
  test('a member can neither send nor read a file outside a module folder', async () => {
    const storage = storageAs(ADULT);
    await assertFails(uploadString(ref(storage, 'misc/photo.jpg'), 'x', 'raw', jpeg));
    await assertFails(getBytes(ref(storage, 'misc/photo.jpg')));
  });

  test('someone outside the family and a signed-out visitor are refused', async () => {
    await assertFails(uploadString(ref(storageAs(STRANGER), 'misc/photo.jpg'), 'x', 'raw', jpeg));
    await assertFails(getBytes(ref(env.unauthenticatedContext().storage(), 'misc/photo.jpg')));
  });
});
