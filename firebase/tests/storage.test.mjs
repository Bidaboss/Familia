// Cloud Storage rules: closed by default. Each module that opens a folder adds its tests here.
// Fictional data only.
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteObject, getBytes, ref, uploadString } from 'firebase/storage';
import { ADULT, OTHER_ADULT, seed, STRANGER } from './helpers.mjs';

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

describe('goodNews folder (récit 32)', () => {
  // Each test sends to a folder of its own, so a file left by another test never gets in the way.
  let post = 0;
  beforeEach(() => { post += 1; });
  const photo = (author, name = 'p1.jpg') => `goodNews/${author}/n${post}/${name}`;
  const bytes = (n) => 'x'.repeat(n);

  test('a member sends the photo and its thumbnail in their own folder, and the family reads them', async () => {
    const storage = storageAs(ADULT);
    await assertSucceeds(uploadString(ref(storage, photo('adult1')), bytes(300 * 1024), 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, photo('adult1', 'p1-thumb.jpg')), bytes(30 * 1024), 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(OTHER_ADULT), photo('adult1'))));
  });

  test('nobody sends in the folder of someone else', async () => {
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), photo('adult1')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(STRANGER), photo('adult1')), 'x', 'raw', jpeg));
  });

  test('only a compressed JPEG with a photo name, never too big', async () => {
    const storage = storageAs(ADULT);
    await assertFails(uploadString(ref(storage, photo('adult1')), bytes(513 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1', 'p1-thumb.jpg')), bytes(65 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1', 'p1.png')), 'x', 'raw', { contentType: 'image/png' }));
    await assertFails(uploadString(ref(storage, photo('adult1')), 'x', 'raw', { contentType: 'image/png' }));
    await assertFails(uploadString(ref(storage, photo('adult1', 'p1.jpg.exe')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, 'goodNews/adult1/n.1/p1.jpg'), 'x', 'raw', jpeg));
  });

  test('a photo is never replaced, and only its author deletes it', async () => {
    const storage = storageAs(ADULT);
    await assertSucceeds(uploadString(ref(storage, photo('adult1')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1')), 'y', 'raw', jpeg));
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), photo('adult1'))));
    await assertSucceeds(deleteObject(ref(storage, photo('adult1'))));
  });

  test('someone outside the family does not read them', async () => {
    await assertSucceeds(uploadString(ref(storageAs(ADULT), photo('adult1')), 'x', 'raw', jpeg));
    await assertFails(getBytes(ref(storageAs(STRANGER), photo('adult1'))));
    await assertFails(getBytes(ref(env.unauthenticatedContext().storage(), photo('adult1'))));
  });
});
