// Cloud Storage rules: closed by default. Each module that opens a folder adds its tests here.
// Fictional data only.
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteObject, getBytes, ref, uploadString } from 'firebase/storage';
import { doc, setDoc, Timestamp } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, seed, STRANGER } from './helpers.mjs';

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

describe('weeklyQuestions folder (récit 37)', () => {
  let week = 0;
  beforeEach(() => { week += 1; });
  const photo = (member, name = 'p1.jpg') => `weeklyQuestions/2026-11-${String(week).padStart(2, '0')}/${member}/${name}`;

  test('a member sends the photo of their answer and its thumbnail, and the family reads them', async () => {
    const storage = storageAs(ADULT);
    await assertSucceeds(uploadString(ref(storage, photo('adult1')), 'x'.repeat(300 * 1024), 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, photo('adult1', 'p1-thumb.jpg')), 'x'.repeat(30 * 1024), 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(OTHER_ADULT), photo('adult1'))));
    await assertFails(getBytes(ref(storageAs(STRANGER), photo('adult1'))));
  });

  test('only in one\'s own folder, a JPEG not too big, never replaced, deleted by its member', async () => {
    const storage = storageAs(ADULT);
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), photo('adult1')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1')), 'x'.repeat(513 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1', 'p1.png')), 'x', 'raw', { contentType: 'image/png' }));
    await assertFails(uploadString(ref(storage, 'weeklyQuestions/semaine/adult1/p1.jpg'), 'x', 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, photo('adult1')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1')), 'y', 'raw', jpeg));
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), photo('adult1'))));
    await assertSucceeds(deleteObject(ref(storage, photo('adult1'))));
  });
});

describe('trips folder (récit 15)', () => {
  // ADULT created the trip and takes part with adult3; adult2 does not follow it.
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'trips/t1'), {
        title: 'Corse', startDate: '2027-07-10', endDate: '2027-07-20',
        participantIds: ['adult1', 'adult3'], readerIds: ['admin1', 'adult1', 'adult3'],
        creatorId: 'adult1', creatorName: 'adult1', createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
      });
    });
  });
  const file = (author, name = '1.jpg') => `trips/t1/${author}/${name}`;
  const bytes = (n) => 'x'.repeat(n);

  test('a participant sends a photo and its thumbnail in their folder, and the followers see them', async () => {
    const storage = storageAs(NEIGHBOUR);
    await assertSucceeds(uploadString(ref(storage, file('adult3')), bytes(300 * 1024), 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, file('adult3', '1-thumb.jpg')), bytes(30 * 1024), 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, file('adult3', '30.jpg')), 'x', 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(ADULT), file('adult3'))));
  });

  test('nobody outside the followers reads them', async () => {
    await uploadString(ref(storageAs(NEIGHBOUR), file('adult3')), 'x', 'raw', jpeg);
    await assertFails(getBytes(ref(storageAs(OTHER_ADULT), file('adult3'))));
    await assertFails(getBytes(ref(storageAs(STRANGER), file('adult3'))));
    await assertFails(getBytes(ref(env.unauthenticatedContext().storage(), file('adult3'))));
  });

  test('only a participant, in their own folder, with one of the 30 places and a small JPEG', async () => {
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), file('adult2')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(NEIGHBOUR), file('adult1')), 'x', 'raw', jpeg));
    const storage = storageAs(NEIGHBOUR);
    await assertFails(uploadString(ref(storage, file('adult3', '31.jpg')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, file('adult3', '0.jpg')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, file('adult3', 'a.jpg')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, file('adult3', '1.png')), 'x', 'raw', { contentType: 'image/png' }));
    await assertFails(uploadString(ref(storage, file('adult3')), bytes(513 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storage, file('adult3', '1-thumb.jpg')), bytes(65 * 1024), 'raw', jpeg));
  });

  test('the author or the creator of the trip removes a photo, nobody else', async () => {
    await uploadString(ref(storageAs(NEIGHBOUR), file('adult3')), 'x', 'raw', jpeg);
    await uploadString(ref(storageAs(NEIGHBOUR), file('adult3', '2.jpg')), 'x', 'raw', jpeg);
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), file('adult3'))));
    await assertSucceeds(deleteObject(ref(storageAs(NEIGHBOUR), file('adult3'))));
    await assertSucceeds(deleteObject(ref(storageAs(ADULT), file('adult3', '2.jpg'))));
  });

  test('the admin principal removes them too, so no file is left behind', async () => {
    await uploadString(ref(storageAs(NEIGHBOUR), file('adult3', '3.jpg')), 'x', 'raw', jpeg);
    await assertSucceeds(deleteObject(ref(storageAs(ADMIN), file('adult3', '3.jpg'))));
  });

  test('a place whose record exists is never sent again', async () => {
    await assertSucceeds(uploadString(ref(storageAs(NEIGHBOUR), file('adult3', '4.jpg')), 'x', 'raw', jpeg));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'trips/t1/photos/adult3_4'), { authorId: 'adult3', authorName: 'adult3', slot: 4, createdAt: Timestamp.fromMillis(0) });
    });
    await assertFails(uploadString(ref(storageAs(NEIGHBOUR), file('adult3', '4.jpg')), 'y', 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(NEIGHBOUR), file('adult3', '4-thumb.jpg')), 'y', 'raw', jpeg));
    // Without a record (a send cut in the middle), the place can be sent again.
    await assertSucceeds(uploadString(ref(storageAs(NEIGHBOUR), file('adult3', '5.jpg')), 'x', 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storageAs(NEIGHBOUR), file('adult3', '5.jpg')), 'y', 'raw', jpeg));
  });
});

describe('events folder (récit 41)', () => {
  // e1 is past, e2 far in the future; adult3 said « je viens » to both, adult2 « peut-être ».
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      for (const [id, date] of [['e1', '2026-01-10'], ['e2', '2099-01-01']]) {
        await setDoc(doc(db, `events/${id}`), { title: 'Repas', date, authorId: 'adult1' });
        await setDoc(doc(db, `events/${id}/rsvps/adult3`), { status: 'yes' });
        await setDoc(doc(db, `events/${id}/rsvps/adult2`), { status: 'maybe' });
      }
    });
  });
  const file = (event, author, name = '1.jpg') => `events/${event}/${author}/${name}`;

  test('a member who said « je viens » sends photos from the day of the event, and the family sees them', async () => {
    await assertSucceeds(uploadString(ref(storageAs(NEIGHBOUR), file('e1', 'adult3')), 'x', 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storageAs(NEIGHBOUR), file('e1', 'adult3', '1-thumb.jpg')), 'x', 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(OTHER_ADULT), file('e1', 'adult3'))));
    await assertFails(getBytes(ref(storageAs(STRANGER), file('e1', 'adult3'))));
  });

  test('not before the day, not without « je viens », not in the folder of someone else', async () => {
    await assertFails(uploadString(ref(storageAs(NEIGHBOUR), file('e2', 'adult3')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), file('e1', 'adult2')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(NEIGHBOUR), file('e1', 'adult2')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(NEIGHBOUR), file('e1', 'adult3', '31.jpg')), 'x', 'raw', jpeg));
  });

  test('the author or the author of the event removes a photo', async () => {
    await uploadString(ref(storageAs(NEIGHBOUR), file('e1', 'adult3')), 'x', 'raw', jpeg);
    await uploadString(ref(storageAs(NEIGHBOUR), file('e1', 'adult3', '2.jpg')), 'x', 'raw', jpeg);
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), file('e1', 'adult3'))));
    await assertSucceeds(deleteObject(ref(storageAs(NEIGHBOUR), file('e1', 'adult3'))));
    await assertSucceeds(deleteObject(ref(storageAs(ADULT), file('e1', 'adult3', '2.jpg'))));
  });

  test('the admin principal removes them too, and a recorded place is never sent again', async () => {
    await uploadString(ref(storageAs(NEIGHBOUR), file('e1', 'adult3', '3.jpg')), 'x', 'raw', jpeg);
    await assertSucceeds(deleteObject(ref(storageAs(ADMIN), file('e1', 'adult3', '3.jpg'))));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'events/e1/photos/adult3_4'), { authorId: 'adult3', slot: 4 });
    });
    await assertFails(uploadString(ref(storageAs(NEIGHBOUR), file('e1', 'adult3', '4.jpg')), 'x', 'raw', jpeg));
  });
});
