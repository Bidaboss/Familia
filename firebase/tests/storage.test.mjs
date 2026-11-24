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

  test('a participant may send their own place again, never in the folder of someone else', async () => {
    // The rule reads only the trip (two documents at most in production, see storage.rules).
    await assertSucceeds(uploadString(ref(storageAs(NEIGHBOUR), file('adult3', '5.jpg')), 'x', 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storageAs(NEIGHBOUR), file('adult3', '5.jpg')), 'y', 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(ADULT), file('adult3', '5.jpg')), 'z', 'raw', jpeg));
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

  test('not without « je viens », not in the folder of someone else', async () => {
    // The day is checked by the record in firestore.rules: Storage may read one document only.
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

  test('the admin principal removes them too', async () => {
    await uploadString(ref(storageAs(NEIGHBOUR), file('e1', 'adult3', '3.jpg')), 'x', 'raw', jpeg);
    await assertSucceeds(deleteObject(ref(storageAs(ADMIN), file('e1', 'adult3', '3.jpg'))));
  });
});

describe('cover of an event (récit 44)', () => {
  // Each test has an event of its own, written by adult1, whose cover is the photo c1 of adult1:
  // a file left by another test never gets in the way.
  let n = 0;
  const cover = (author, name) => `events/c${n}/cover/${author}/${name}`;
  beforeEach(async () => {
    n += 1;
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `events/c${n}`), { title: 'Repas', authorId: 'adult1', coverPhoto: `events/c${n}/cover/adult1/c1` });
    });
  });

  test('a member sends a cover and its thumbnail to their own folder, and the family sees it', async () => {
    const storage = storageAs(ADULT);
    await assertSucceeds(uploadString(ref(storage, cover('adult1', 'c1.jpg')), 'x'.repeat(300 * 1024), 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, cover('adult1', 'c1-thumb.jpg')), 'x'.repeat(30 * 1024), 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(OTHER_ADULT), cover('adult1', 'c1.jpg'))));
    await assertFails(getBytes(ref(storageAs(STRANGER), cover('adult1', 'c1.jpg'))));
  });

  test('never to the folder of someone else, never replaced, only a small JPEG', async () => {
    const storage = storageAs(ADULT);
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), cover('adult1', 'c2.jpg')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(STRANGER), cover('adult1', 'c2.jpg')), 'x', 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, cover('adult1', 'c2.jpg')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, cover('adult1', 'c2.jpg')), 'y', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, cover('adult1', 'c3.jpg')), 'x'.repeat(513 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storage, cover('adult1', 'c3-thumb.jpg')), 'x'.repeat(65 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storage, cover('adult1', 'c3.png')), 'x', 'raw', { contentType: 'image/png' }));
    await assertFails(uploadString(ref(storage, cover('adult1', 'c.3.jpg')), 'x', 'raw', jpeg));
  });

  test('its sender removes it; the others only a cover the event no longer names', async () => {
    const storage = storageAs(ADULT);
    for (const name of ['c1.jpg', 'c1-thumb.jpg', 'c0.jpg', 'c0-thumb.jpg']) {
      await uploadString(ref(storage, cover('adult1', name)), 'x', 'raw', jpeg);
    }
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), cover('adult1', 'c1.jpg'))));
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), cover('adult1', 'c1-thumb.jpg'))));
    await assertSucceeds(deleteObject(ref(storageAs(OTHER_ADULT), cover('adult1', 'c0.jpg'))));
    await assertSucceeds(deleteObject(ref(storageAs(OTHER_ADULT), cover('adult1', 'c0-thumb.jpg'))));
    await assertSucceeds(deleteObject(ref(storage, cover('adult1', 'c1.jpg'))));
    await assertFails(deleteObject(ref(storageAs(STRANGER), cover('adult1', 'c1-thumb.jpg'))));
  });
});

describe('photos of the gift lists (récit 44)', () => {
  // Each test has its own lists, so a file left by another test never gets in the way.
  let n = 0;
  const file = (list, folder, author, name = 'p1.jpg') => `giftLists/${list}${n}/${folder}/${author}/${name}`;
  beforeEach(async () => {
    n += 1;
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      const list = { beneficiaryId: 'adult1', viewerIds: [], revealed: false };
      await setDoc(doc(db, `giftLists/wishes${n}`), { ...list, kind: 'wishes' });
      await setDoc(doc(db, `giftLists/ideas${n}`), { ...list, kind: 'ideas' });
      await setDoc(doc(db, `giftLists/surprise${n}`), { ...list, kind: 'surprise', viewerIds: ['adult2', 'adult3'] });
    });
  });

  test('a wish: its author sends the photo, the family and the person read it', async () => {
    await assertSucceeds(uploadString(ref(storageAs(ADULT), file('wishes', 'shared', 'adult1')), 'x'.repeat(300 * 1024), 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storageAs(ADULT), file('wishes', 'shared', 'adult1', 'p1-thumb.jpg')), 'x', 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(ADULT), file('wishes', 'shared', 'adult1'))));
    await assertSucceeds(getBytes(ref(storageAs(OTHER_ADULT), file('wishes', 'shared', 'adult1'))));
    await assertFails(getBytes(ref(storageAs(STRANGER), file('wishes', 'shared', 'adult1'))));
  });

  test('« Idées de la famille »: the person, signed in, can neither read nor download the photo', async () => {
    await assertSucceeds(uploadString(ref(storageAs(NEIGHBOUR), file('ideas', 'hidden', 'adult3')), 'x', 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(OTHER_ADULT), file('ideas', 'hidden', 'adult3'))));
    await assertFails(getBytes(ref(storageAs(ADULT), file('ideas', 'hidden', 'adult3'))));
    // Not even by sending one there themselves.
    await assertFails(uploadString(ref(storageAs(ADULT), file('ideas', 'hidden', 'adult1')), 'x', 'raw', jpeg));
  });

  test('a hidden idea on a wish list stays hidden from the person until the reveal', async () => {
    await assertSucceeds(uploadString(ref(storageAs(OTHER_ADULT), file('wishes', 'hidden', 'adult2')), 'x', 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(NEIGHBOUR), file('wishes', 'hidden', 'adult2'))));
    await assertFails(getBytes(ref(storageAs(ADULT), file('wishes', 'hidden', 'adult2'))));
  });

  test('a surprise list: only its viewers send and read', async () => {
    await assertSucceeds(uploadString(ref(storageAs(OTHER_ADULT), file('surprise', 'hidden', 'adult2')), 'x', 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(NEIGHBOUR), file('surprise', 'hidden', 'adult2'))));
    await assertFails(getBytes(ref(storageAs(ADULT), file('surprise', 'hidden', 'adult2'))));
  });

  test('« On m\'a offert »: the photo copied by the server is read by the family, written by nobody (rév. 237)', async () => {
    const received = `giftLists/wishes${n}/received/book/photo.jpg`;
    await env.withSecurityRulesDisabled(async (ctx) => {
      await uploadString(ref(ctx.storage(), received), 'x', 'raw', jpeg);
    });
    await assertSucceeds(getBytes(ref(storageAs(ADULT), received)));
    await assertSucceeds(getBytes(ref(storageAs(OTHER_ADULT), received)));
    await assertFails(getBytes(ref(storageAs(STRANGER), received)));
    await assertFails(uploadString(ref(storageAs(ADULT), `giftLists/wishes${n}/received/lamp/photo.jpg`), 'x', 'raw', jpeg));
    await assertFails(deleteObject(ref(storageAs(ADULT), received)));
    // Only under « Mes envies ».
    await assertFails(getBytes(ref(storageAs(OTHER_ADULT), `giftLists/ideas${n}/received/book/photo.jpg`)));
  });

  test('only in my folder, a known folder, a small JPEG, never replaced; only its author removes it', async () => {
    const storage = storageAs(ADULT);
    await assertFails(uploadString(ref(storage, file('wishes', 'shared', 'adult2')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, file('wishes', 'other', 'adult1')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, file('wishes', 'shared', 'adult1')), 'x'.repeat(513 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storage, `giftLists/none${n}/shared/adult1/p1.jpg`), 'x', 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, file('wishes', 'shared', 'adult1')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, file('wishes', 'shared', 'adult1')), 'y', 'raw', jpeg));
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), file('wishes', 'shared', 'adult1'))));
    await assertSucceeds(deleteObject(ref(storage, file('wishes', 'shared', 'adult1'))));
  });
});

describe('recipes folder (récit 38)', () => {
  // r1 is written by adult1.
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'recipes/r1'), { title: 'Gâteau', authorId: 'adult1' });
    });
  });
  const file = (author, name = '1.jpg') => `recipes/r1/${author}/${name}`;

  test('the author sends up to 3 photos, and the family sees them', async () => {
    await assertSucceeds(uploadString(ref(storageAs(ADULT), file('adult1')), 'x', 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storageAs(ADULT), file('adult1', '3-thumb.jpg')), 'x', 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(NEIGHBOUR), file('adult1'))));
    await assertFails(getBytes(ref(storageAs(STRANGER), file('adult1'))));
    await assertFails(uploadString(ref(storageAs(ADULT), file('adult1', '4.jpg')), 'x', 'raw', jpeg));
  });

  test('nobody else sends photos, but the admin principal may, and removes them', async () => {
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), file('adult2')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), file('adult1')), 'x', 'raw', jpeg));
    await uploadString(ref(storageAs(ADULT), file('adult1', '2.jpg')), 'x', 'raw', jpeg);
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), file('adult1', '2.jpg'))));
    await assertSucceeds(deleteObject(ref(storageAs(ADMIN), file('adult1', '2.jpg'))));
    await assertSucceeds(uploadString(ref(storageAs(ADMIN), file('admin1')), 'x', 'raw', jpeg));
  });
});

describe('« Qui est-ce ? » folder (récit 43)', () => {
  const file = (author, post, name = 'photo.jpg') => `whoIsIt/${author}/${post}/${name}`;

  test('the author sends the photo and its thumbnail before the post, the family reads them', async () => {
    const storage = storageAs(ADULT);
    await assertSucceeds(uploadString(ref(storage, file('adult1', 'w1')), 'x'.repeat(300 * 1024), 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, file('adult1', 'w1', 'photo-thumb.jpg')), 'x'.repeat(30 * 1024), 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(NEIGHBOUR), file('adult1', 'w1'))));
    await assertFails(uploadString(ref(storage, file('adult1', 'w2', 'other.jpg')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, file('adult1', 'w3')), 'x'.repeat(600 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), file('adult1', 'w4')), 'x', 'raw', jpeg));
  });

  test('never into the post of someone else, and only its author or the admin removes', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'whoIsIt/w1'), { authorId: 'adult2' });
    });
    await assertFails(uploadString(ref(storageAs(ADULT), file('adult1', 'w1')), 'x', 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storageAs(OTHER_ADULT), file('adult2', 'w1')), 'x', 'raw', jpeg));
    await assertFails(deleteObject(ref(storageAs(ADULT), file('adult2', 'w1'))));
    await assertSucceeds(deleteObject(ref(storageAs(ADMIN), file('adult2', 'w1'))));
  });
});

describe('capsules folder (récit 39)', () => {
  const file = (author, capsule, name = '1.jpg') => `capsules/${author}/${capsule}/${name}`;

  test('the author sends up to 3 photos and reads them; nobody else before the opening', async () => {
    await assertSucceeds(uploadString(ref(storageAs(ADULT), file('adult1', 'c1')), 'x', 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storageAs(ADULT), file('adult1', 'c1', '3-thumb.jpg')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(ADULT), file('adult1', 'c1', '4.jpg')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), file('adult1', 'c1', '2.jpg')), 'x', 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(ADULT), file('adult1', 'c1'))));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'capsules/c1'), { authorId: 'adult1', openedFor: [] });
    });
    await assertFails(getBytes(ref(storageAs(OTHER_ADULT), file('adult1', 'c1'))));
  });

  test('once opened, the recipients see the photos; the author removes them', async () => {
    await uploadString(ref(storageAs(ADULT), file('adult1', 'c2')), 'x', 'raw', jpeg);
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'capsules/c2'), { authorId: 'adult1', openedFor: ['adult2'] });
    });
    await assertSucceeds(getBytes(ref(storageAs(OTHER_ADULT), file('adult1', 'c2'))));
    await assertFails(getBytes(ref(storageAs(NEIGHBOUR), file('adult1', 'c2'))));
    // A folder of another author never borrows the opening of capsule c2.
    await assertSucceeds(uploadString(ref(storageAs(NEIGHBOUR), file(NEIGHBOUR.memberId, 'c2')), 'x', 'raw', jpeg));
    await assertFails(getBytes(ref(storageAs(OTHER_ADULT), file(NEIGHBOUR.memberId, 'c2'))));
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), file('adult1', 'c2'))));
    await assertSucceeds(deleteObject(ref(storageAs(ADULT), file('adult1', 'c2'))));
  });
});

describe('feedback folder (récit 6 sexies)', () => {
  const file = (author, name = 'f1.jpg') => `feedback/${author}/${name}`;
  const bytes = (n) => 'x'.repeat(n);

  test('the author sends one screenshot of at most 150 KB in their own folder', async () => {
    await assertSucceeds(uploadString(ref(storageAs(ADULT), file('adult1')), bytes(150 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(ADULT), file('adult1', 'f2.jpg')), bytes(150 * 1024 + 1), 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(ADULT), file('adult1', 'f3.png')), 'x', 'raw', { contentType: 'image/png' }));
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), file('adult1', 'f4.jpg')), 'x', 'raw', jpeg));
    // Never replaced.
    await assertFails(uploadString(ref(storageAs(ADULT), file('adult1')), 'y', 'raw', jpeg));
  });

  test('only the author and the admin principal see it, and either removes it', async () => {
    await uploadString(ref(storageAs(ADULT), file('adult1', 'g1.jpg')), 'x', 'raw', jpeg);
    await uploadString(ref(storageAs(ADULT), file('adult1', 'g2.jpg')), 'x', 'raw', jpeg);
    await assertSucceeds(getBytes(ref(storageAs(ADULT), file('adult1', 'g1.jpg'))));
    await assertSucceeds(getBytes(ref(storageAs(ADMIN), file('adult1', 'g1.jpg'))));
    await assertFails(getBytes(ref(storageAs(OTHER_ADULT), file('adult1', 'g1.jpg'))));
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), file('adult1', 'g1.jpg'))));
    await assertSucceeds(deleteObject(ref(storageAs(ADMIN), file('adult1', 'g1.jpg'))));
    await assertSucceeds(deleteObject(ref(storageAs(ADULT), file('adult1', 'g2.jpg'))));
  });
});

describe('image of a coup de main (Kévin 16/11)', () => {
  // Each test has a request of its own by adult1, seen by adult2 only, naming the image h1 of adult1.
  let n = 0;
  const image = (author, name) => `helpRequests/h${n}/${author}/${name}`;
  beforeEach(async () => {
    n += 1;
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `helpRequests/h${n}`), {
        title: 'Colis', authorId: 'adult1', notifyIds: ['adult2'], viewerIds: ['adult1', 'adult2'], photo: `helpRequests/h${n}/adult1/h1`,
      });
    });
  });

  test('the sender sends it to their own folder; only those who see the request read it', async () => {
    const storage = storageAs(ADULT);
    await assertSucceeds(uploadString(ref(storage, image('adult1', 'h1.jpg')), 'x'.repeat(300 * 1024), 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, image('adult1', 'h1-thumb.jpg')), 'x'.repeat(30 * 1024), 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storage, image('adult1', 'h1.jpg'))));
    await assertSucceeds(getBytes(ref(storageAs(OTHER_ADULT), image('adult1', 'h1.jpg'))));
    await assertFails(getBytes(ref(storageAs(NEIGHBOUR), image('adult1', 'h1.jpg'))));
    await assertFails(getBytes(ref(storageAs(STRANGER), image('adult1', 'h1.jpg'))));
  });

  test('never into another folder, never replaced, a JPEG of the right size only', async () => {
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), image('adult1', 'h2.jpg')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storageAs(STRANGER), image('stranger', 'h2.jpg')), 'x', 'raw', jpeg));
    const storage = storageAs(ADULT);
    await assertSucceeds(uploadString(ref(storage, image('adult1', 'h2.jpg')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, image('adult1', 'h2.jpg')), 'y', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, image('adult1', 'h3.jpg')), 'x'.repeat(513 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storage, image('adult1', 'h3-thumb.jpg')), 'x'.repeat(65 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storage, image('adult1', 'h3.png')), 'x', 'raw', { contentType: 'image/png' }));
  });

  test('its sender removes it; the others only an image the request no longer names', async () => {
    const storage = storageAs(ADULT);
    for (const name of ['h1.jpg', 'h1-thumb.jpg', 'old.jpg']) await uploadString(ref(storage, image('adult1', name)), 'x', 'raw', jpeg);
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), image('adult1', 'h1.jpg'))));
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), image('adult1', 'h1-thumb.jpg'))));
    await assertSucceeds(deleteObject(ref(storageAs(OTHER_ADULT), image('adult1', 'old.jpg'))));
    await assertSucceeds(deleteObject(ref(storage, image('adult1', 'h1.jpg'))));
  });
});

describe('photo of a bon plan (récit 45)', () => {
  // Each test has a deal of its own, written by adult1, whose photo is p1 of adult1.
  let n = 0;
  const photo = (author, name) => `goodDeals/d${n}/${author}/${name}`;
  beforeEach(async () => {
    n += 1;
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `goodDeals/d${n}`), { kind: 'deal', authorId: 'adult1', photo: `goodDeals/d${n}/adult1/p1` });
    });
  });

  test('a member sends a photo and its thumbnail to their own folder, and the family sees it', async () => {
    const storage = storageAs(ADULT);
    await assertSucceeds(uploadString(ref(storage, photo('adult1', 'p1.jpg')), 'x'.repeat(300 * 1024), 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, photo('adult1', 'p1-thumb.jpg')), 'x'.repeat(30 * 1024), 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(NEIGHBOUR), photo('adult1', 'p1.jpg'))));
    await assertFails(getBytes(ref(storageAs(STRANGER), photo('adult1', 'p1.jpg'))));
  });

  test('never to the folder of someone else, never replaced, only a small JPEG', async () => {
    const storage = storageAs(ADULT);
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), photo('adult1', 'p2.jpg')), 'x', 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, photo('adult1', 'p2.jpg')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1', 'p2.jpg')), 'y', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1', 'p3.jpg')), 'x'.repeat(513 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1', 'p3-thumb.jpg')), 'x'.repeat(65 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1', 'p3.png')), 'x', 'raw', { contentType: 'image/png' }));
  });

  test('its sender removes it; the others only a photo the deal no longer names', async () => {
    const storage = storageAs(ADULT);
    for (const name of ['p1.jpg', 'p1-thumb.jpg', 'p0.jpg']) {
      await uploadString(ref(storage, photo('adult1', name)), 'x', 'raw', jpeg);
    }
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), photo('adult1', 'p1.jpg'))));
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), photo('adult1', 'p1-thumb.jpg'))));
    await assertSucceeds(deleteObject(ref(storageAs(OTHER_ADULT), photo('adult1', 'p0.jpg'))));
    await assertSucceeds(deleteObject(ref(storage, photo('adult1', 'p1.jpg'))));
  });
});

describe('photo of a thing to lend (récit 51)', () => {
  // Each test has an item of its own, owned by adult1, whose photo is p1 of adult1.
  let n = 0;
  const photo = (author, name) => `lendItems/i${n}/${author}/${name}`;
  beforeEach(async () => {
    n += 1;
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `lendItems/i${n}`), { ownerId: 'adult1', photo: `lendItems/i${n}/adult1/p1` });
    });
  });

  test('a member sends a photo and its thumbnail to their own folder, and the family sees it', async () => {
    const storage = storageAs(ADULT);
    await assertSucceeds(uploadString(ref(storage, photo('adult1', 'p1.jpg')), 'x'.repeat(300 * 1024), 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, photo('adult1', 'p1-thumb.jpg')), 'x'.repeat(30 * 1024), 'raw', jpeg));
    await assertSucceeds(getBytes(ref(storageAs(NEIGHBOUR), photo('adult1', 'p1.jpg'))));
    await assertFails(getBytes(ref(storageAs(STRANGER), photo('adult1', 'p1.jpg'))));
  });

  test('never to the folder of someone else, never replaced, only a small JPEG', async () => {
    const storage = storageAs(ADULT);
    await assertFails(uploadString(ref(storageAs(OTHER_ADULT), photo('adult1', 'p2.jpg')), 'x', 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, photo('adult1', 'p2.jpg')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1', 'p2.jpg')), 'y', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1', 'p3.jpg')), 'x'.repeat(513 * 1024), 'raw', jpeg));
    await assertFails(uploadString(ref(storage, photo('adult1', 'p3.png')), 'x', 'raw', { contentType: 'image/png' }));
  });

  test('its sender removes it; the others only a photo the item no longer names', async () => {
    const storage = storageAs(ADULT);
    for (const name of ['p1.jpg', 'p1-thumb.jpg', 'p0.jpg']) {
      await uploadString(ref(storage, photo('adult1', name)), 'x', 'raw', jpeg);
    }
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), photo('adult1', 'p1.jpg'))));
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), photo('adult1', 'p1-thumb.jpg'))));
    await assertSucceeds(deleteObject(ref(storageAs(OTHER_ADULT), photo('adult1', 'p0.jpg'))));
    await assertSucceeds(deleteObject(ref(storage, photo('adult1', 'p1.jpg'))));
  });
});

describe('trip documents folder (récit 105, règle 41)', () => {
  // ADULT created the trip and takes part with adult3; admin1 follows it without going.
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'trips/t1'), {
        title: 'Corse', startDate: '2027-07-10', endDate: '2027-07-20',
        participantIds: ['adult1', 'adult3'], readerIds: ['admin1', 'adult1', 'adult3'],
        creatorId: 'adult1', creatorName: 'adult1', createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
      });
    });
  });
  const file = (owner, name = 'abc123.pdf') => `trips/t1/documents/${owner}/${name}`;
  const pdf = { contentType: 'application/pdf' };

  test('a participant sends a PDF, a JPEG or a PNG in their folder, and only those who go read it', async () => {
    const storage = storageAs(NEIGHBOUR);
    await assertSucceeds(uploadString(ref(storage, file('adult3')), 'x'.repeat(1024 * 1024), 'raw', pdf));
    await assertSucceeds(uploadString(ref(storage, file('adult3', 'abc124.jpg')), 'x', 'raw', jpeg));
    await assertSucceeds(uploadString(ref(storage, file('adult3', 'abc125.png')), 'x', 'raw', { contentType: 'image/png' }));
    await assertSucceeds(getBytes(ref(storageAs(ADULT), file('adult3'))));
    await assertFails(getBytes(ref(storageAs(ADMIN), file('adult3'))));
    await assertFails(getBytes(ref(storageAs(OTHER_ADULT), file('adult3'))));
  });

  test('never in someone else\'s folder, never by a follower, never too big, never another type', async () => {
    await assertFails(uploadString(ref(storageAs(NEIGHBOUR), file('adult1')), 'x', 'raw', pdf));
    await assertFails(uploadString(ref(storageAs(ADMIN), file('admin1')), 'x', 'raw', pdf));
    const storage = storageAs(NEIGHBOUR);
    await assertFails(uploadString(ref(storage, file('adult3', 'big.pdf')), 'x'.repeat(5 * 1024 * 1024 + 1), 'raw', pdf));
    await assertFails(uploadString(ref(storage, file('adult3', 'abc.pdf')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, file('adult3', 'abc.exe')), 'x', 'raw', { contentType: 'application/octet-stream' }));
    await assertFails(uploadString(ref(storage, file('adult3', 'a-b.pdf')), 'x', 'raw', pdf));
  });

  test('a file is never replaced; its owner or the creator of the trip removes it', async () => {
    // Names of their own: the emulator may keep a file of an earlier test.
    const storage = storageAs(NEIGHBOUR);
    await uploadString(ref(storage, file('adult3', 'abc200.pdf')), 'x', 'raw', pdf);
    await assertFails(uploadString(ref(storage, file('adult3', 'abc200.pdf')), 'y', 'raw', pdf));
    await uploadString(ref(storage, file('adult3', 'abc201.pdf')), 'x', 'raw', pdf);
    await assertFails(deleteObject(ref(storageAs(OTHER_ADULT), file('adult3', 'abc200.pdf'))));
    await assertSucceeds(deleteObject(ref(storage, file('adult3', 'abc200.pdf'))));
    await assertSucceeds(deleteObject(ref(storageAs(ADULT), file('adult3', 'abc201.pdf'))));
  });
});

describe('trip voice notes folder (récit 104, règle 35)', () => {
  // ADULT created the trip and takes part with adult3; admin1 follows it without going.
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'trips/t1'), {
        title: 'Corse', startDate: '2027-07-10', endDate: '2027-07-20',
        participantIds: ['adult1', 'adult3'], readerIds: ['admin1', 'adult1', 'adult3'],
        creatorId: 'adult1', creatorName: 'adult1', createdAt: Timestamp.fromMillis(0), updatedAt: Timestamp.fromMillis(0),
      });
    });
  });
  const file = (author, name) => `trips/t1/voice/${author}/${name}`;
  const m4a = { contentType: 'audio/mp4' };

  test('a participant sends a note of 2 MB at most in their folder, and the followers listen', async () => {
    const storage = storageAs(NEIGHBOUR);
    await assertSucceeds(uploadString(ref(storage, file('adult3', 'v100.m4a')), 'x'.repeat(2 * 1024 * 1024), 'raw', m4a));
    await assertSucceeds(getBytes(ref(storageAs(ADMIN), file('adult3', 'v100.m4a'))));
    await assertFails(getBytes(ref(storageAs(OTHER_ADULT), file('adult3', 'v100.m4a'))));
  });

  test('never too big, never another type or name, never for someone else, never by a follower, never replaced', async () => {
    const storage = storageAs(NEIGHBOUR);
    await assertFails(uploadString(ref(storage, file('adult3', 'v101.m4a')), 'x'.repeat(2 * 1024 * 1024 + 1), 'raw', m4a));
    await assertFails(uploadString(ref(storage, file('adult3', 'v102.m4a')), 'x', 'raw', jpeg));
    await assertFails(uploadString(ref(storage, file('adult3', 'v-103.m4a')), 'x', 'raw', m4a));
    await assertFails(uploadString(ref(storage, file('adult3', 'v104.mp3')), 'x', 'raw', m4a));
    await assertFails(uploadString(ref(storage, file('adult1', 'v105.m4a')), 'x', 'raw', m4a));
    await assertFails(uploadString(ref(storageAs(ADMIN), file('admin1', 'v106.m4a')), 'x', 'raw', m4a));
    await uploadString(ref(storage, file('adult3', 'v107.m4a')), 'x', 'raw', m4a);
    await assertFails(uploadString(ref(storage, file('adult3', 'v107.m4a')), 'y', 'raw', m4a));
    await assertFails(deleteObject(ref(storageAs(ADULT), file('adult3', 'v107.m4a'))));
    await assertSucceeds(deleteObject(ref(storage, file('adult3', 'v107.m4a'))));
  });
});
