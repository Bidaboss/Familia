// La gazette, les publications (récits 117, 119 et 121): gazettePosts/{postId}. Fictional family
// of helpers.mjs: adult1, adult2 and admin2 (chef) in h1, admin1 (admin principal), adult3 in h2;
// kid1 (child with the app, parent adult1).
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, deleteField, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore';
import { ADMIN, ADULT, NEIGHBOUR, OTHER_ADULT, SECOND_ADMIN, STRANGER, adultDoc, as, createEnv, seed } from './helpers.mjs';

let env;
before(async () => { env = await createEnv(); });
after(async () => { await env.cleanup(); });

const KID = { memberId: 'kid1', email: 'kid1@example.com', uid: 'uid-kid1' };

const ref = (db, id = 'p1') => doc(db, `gazettePosts/${id}`);

/** A valid new publication by [by]. */
function post(by, overrides = {}) {
  return {
    authorId: by.memberId, authorName: by.memberId, text: 'Belle journée au parc', photoIds: ['a1', 'a2'],
    mentionIds: [], createdAt: serverTimestamp(), ...overrides,
  };
}

/** Writes a publication without the rules, as if it was published earlier. */
async function stored(data, id = 'p1') {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(ref(ctx.firestore(), id), { ...data, createdAt: Timestamp.fromMillis(0) });
  });
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(env);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `memberEmails/${KID.email}`), { memberId: KID.memberId });
    await setDoc(doc(db, `members/${KID.memberId}`), adultDoc(KID, { profile: 'childWithApp', parentIds: ['adult1'] }));
  });
});

describe('publish (récit 117)', () => {
  test('a member publishes a word, photos, or both, for the whole family', async () => {
    await assertSucceeds(setDoc(ref(as(env, ADULT), 'p1'), post(ADULT)));
    await assertSucceeds(setDoc(ref(as(env, ADULT), 'p2'), post(ADULT, { photoIds: [] })));
    await assertSucceeds(setDoc(ref(as(env, ADULT), 'p3'), post(ADULT, { text: '', photoIds: ['a1', 'a2', 'a3', 'a4', 'a5', 'a6'] })));
    await assertSucceeds(setDoc(ref(as(env, KID), 'p4'), post(KID, { mentionIds: ['adult1', 'adult2'] })));
    await assertSucceeds(getDoc(ref(as(env, NEIGHBOUR), 'p1')));
    await assertSucceeds(getDocs(collection(as(env, KID), 'gazettePosts')));
  });

  test('never empty, nor too long, nor more than 6 photos (PRD 119 b)', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(ref(db), post(ADULT, { text: '  ', photoIds: [] })));
    await assertSucceeds(setDoc(ref(db, 'long'), post(ADULT, { text: 'x'.repeat(1000) })));
    await assertFails(setDoc(ref(db), post(ADULT, { text: 'x'.repeat(1001) })));
    await assertFails(setDoc(ref(db), post(ADULT, { photoIds: ['a1', 'a2', 'a3', 'a4', 'a5', 'a6', 'a7'] })));
    await assertFails(setDoc(ref(db), post(ADULT, { photoIds: ['../x'] })));
    await assertFails(setDoc(ref(db), post(ADULT, { mentionIds: ['adult2', 'adult2'] })));
    await assertFails(setDoc(ref(db), post(ADULT, { mentionIds: ['adult1'] })));
    await assertFails(setDoc(ref(db), post(ADULT, { loc: 'Lyon' })));
    await assertFails(setDoc(ref(db), post(ADULT, { editedAt: serverTimestamp() })));
  });

  test('only as oneself, at the time of the server', async () => {
    await assertFails(setDoc(ref(as(env, ADULT)), post(OTHER_ADULT)));
    await assertFails(setDoc(ref(as(env, ADULT)), post(ADULT, { authorName: 'someone' })));
    await assertFails(setDoc(ref(as(env, ADULT)), post(ADULT, { createdAt: Timestamp.fromMillis(0) })));
    await assertFails(setDoc(ref(as(env, STRANGER)), post(ADULT)));
    await assertFails(getDoc(ref(as(env, STRANGER), 'p1')));
  });
});

describe('modify (fiche R4)', () => {
  beforeEach(async () => { await stored(post(KID)); });

  test('its author changes the text and the members named, « modifié »', async () => {
    await assertSucceeds(updateDoc(ref(as(env, KID)), { text: 'Une autre phrase', mentionIds: ['adult2'], editedAt: serverTimestamp() }));
  });

  test('never the photos, never without editedAt, never someone else', async () => {
    await assertFails(updateDoc(ref(as(env, KID)), { photoIds: ['a1'], editedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref(as(env, KID)), { text: 'Une autre phrase' }));
    await assertFails(updateDoc(ref(as(env, ADULT)), { text: 'Par un parent', editedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref(as(env, ADMIN)), { text: 'Par l\'admin', editedAt: serverTimestamp() }));
  });
});

describe('remove (récit 121)', () => {
  beforeEach(async () => { await stored(post(KID)); });

  test('its author', async () => {
    await assertSucceeds(deleteDoc(ref(as(env, KID))));
  });

  test('a responsible adult of its author', async () => {
    await assertSucceeds(deleteDoc(ref(as(env, ADULT))));
  });

  test('the admin principal', async () => {
    await assertSucceeds(deleteDoc(ref(as(env, ADMIN))));
  });

  test('the chef de famille of its household too, as for the photos of an album (règle du 25/11)', async () => {
    await assertSucceeds(deleteDoc(ref(as(env, SECOND_ADMIN))));
  });

  test('nobody else: another adult, a chef of another household', async () => {
    await assertFails(deleteDoc(ref(as(env, OTHER_ADULT))));
    await assertFails(deleteDoc(ref(as(env, NEIGHBOUR))));
  });

  test('a chef never removes the publication of another adult', async () => {
    await stored(post(OTHER_ADULT), 'p2');
    await assertFails(deleteDoc(ref(as(env, SECOND_ADMIN), 'p2')));
    await assertFails(deleteDoc(ref(as(env, ADULT), 'p2')));
  });
});

const HERE = { label: 'Annecy · Haute-Savoie', lat: 45.9, lng: 6.12 };

describe('« Dire où je suis » (récit 117)', () => {
  test('a position alone, or with a word, shared with its point', async () => {
    const db = as(env, KID);
    await assertSucceeds(setDoc(ref(db, 'p1'), post(KID, { text: '', photoIds: [], loc: HERE, locLive: true })));
    await assertSucceeds(setDoc(ref(db, 'p2'), post(KID, { loc: HERE, locLive: true })));
  });

  test('never a point without locLive, a wrong point, nor a long or empty name', async () => {
    const db = as(env, ADULT);
    await assertFails(setDoc(ref(db), post(ADULT, { loc: HERE })));
    await assertFails(setDoc(ref(db), post(ADULT, { locLive: true })));
    await assertFails(setDoc(ref(db), post(ADULT, { loc: HERE, locLive: false })));
    await assertFails(setDoc(ref(db), post(ADULT, { loc: { ...HERE, lat: 91 }, locLive: true })));
    await assertFails(setDoc(ref(db), post(ADULT, { loc: { ...HERE, lng: '6' }, locLive: true })));
    await assertFails(setDoc(ref(db), post(ADULT, { loc: { ...HERE, label: 'x'.repeat(81) }, locLive: true })));
    await assertFails(setDoc(ref(db), post(ADULT, { loc: { ...HERE, label: '' }, locLive: true })));
    await assertFails(setDoc(ref(db), post(ADULT, { loc: { ...HERE, alt: 3 }, locLive: true })));
    await assertFails(setDoc(ref(db), post(ADULT, { text: '', photoIds: [], loc: { label: 'Annecy' } })));
  });

  describe('« Arrêter »: the point goes, the name stays', () => {
    beforeEach(async () => { await stored(post(KID, { loc: HERE, locLive: true })); });
    const stop = (by) => updateDoc(ref(as(env, by)), { loc: { label: HERE.label }, locLive: deleteField() });

    test('its author, a responsible adult, the chef de famille, the admin principal', async () => {
      await assertSucceeds(stop(KID));
      await stored(post(KID, { loc: HERE, locLive: true }));
      await assertSucceeds(stop(ADULT));
      await stored(post(KID, { loc: HERE, locLive: true }));
      await assertSucceeds(stop(SECOND_ADMIN));
      await stored(post(KID, { loc: HERE, locLive: true }));
      await assertSucceeds(stop(ADMIN));
    });

    test('nobody else, and never a new name, a new point or another field', async () => {
      await assertFails(stop(OTHER_ADULT));
      await assertFails(stop(NEIGHBOUR));
      await assertFails(updateDoc(ref(as(env, KID)), { loc: { label: 'Ailleurs' }, locLive: deleteField() }));
      await assertFails(updateDoc(ref(as(env, KID)), { loc: { ...HERE, lat: 10 }, locLive: deleteField() }));
      await assertFails(updateDoc(ref(as(env, KID)), { loc: { label: HERE.label } }));
      await assertFails(updateDoc(ref(as(env, ADULT)), { loc: { label: HERE.label }, locLive: deleteField(), text: 'x' }));
    });

    test('the author changes the text, the position stays as it was', async () => {
      await assertSucceeds(updateDoc(ref(as(env, KID)), { text: 'En route', editedAt: serverTimestamp() }));
      await assertFails(updateDoc(ref(as(env, KID)), { text: 'En route', loc: { label: HERE.label }, editedAt: serverTimestamp() }));
    });
  });
});
