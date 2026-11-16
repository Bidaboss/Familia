import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { Firestore, getFirestore } from 'firebase-admin/firestore';
import { queueAlbumInvitations } from '../src/albumInvites';
import { digestNotice } from '../src/digest';

describe('the invitation to yesterday\'s album, without the emulator', () => {
  const quiet = { challengePercent: null, leaves: 0 };

  test('it is a soft line of the résumé; alone, a tap opens the event', () => {
    const one = digestNotice({ souvenirs: [], challengeDone: false, weekly: null, albums: ['e1'] }, quiet, 0);
    assert.equal(one?.body, 'Tes photos d\'hier à mettre dans l\'album.');
    assert.equal(one?.eventId, 'e1');
    const two = digestNotice({ souvenirs: ['a'], challengeDone: false, weekly: null, albums: ['e1', 'e2'] }, quiet, 0);
    assert.equal(two?.body, 'Tes photos d\'hier à mettre dans les albums, nouveau souvenir.');
    assert.equal(two?.eventId, undefined);
  });
});

describe('the invitation to yesterday\'s album against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'album-invites-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  // 9 h in Paris on 24/11/2026: the event was on the 23rd.
  const morning = new Date('2026-11-24T08:00:00Z');

  beforeEach(async () => {
    for (const c of ['members', 'events', 'engagementDigests', 'albumInvites']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { digest: true, eventAlbums: true } });
    for (const [id, profile] of [['camille', 'adult'], ['noe', 'adult'], ['lou', 'adult'], ['tim', 'childWithoutApp'], ['sam', 'adult']]) {
      batch.set(db.doc(`members/${id}`), { firstName: id, profile, parentIds: [] });
    }
    batch.set(db.doc('members/sam/settings/notifications'), { weekly: false });
    batch.set(db.doc('events/e1'), { title: 'Repas', date: '2026-11-23' });
    for (const [id, status] of [['camille', 'yes'], ['noe', 'yes'], ['lou', 'maybe'], ['tim', 'yes'], ['sam', 'yes']]) {
      batch.set(db.doc(`events/e1/rsvps/${id}`), { status });
    }
    batch.set(db.doc('events/e1/photos/p1'), { authorId: 'noe', slot: 1 });
    batch.set(db.doc('events/e2'), { title: 'Plus tard', date: '2026-11-30' });
    batch.set(db.doc('events/e2/rsvps/camille'), { status: 'yes' });
    await batch.commit();
  });

  test('those who came and have not posted yet are invited, once a day', async () => {
    assert.equal(await queueAlbumInvitations(db, morning), 1);
    assert.deepEqual((await db.doc('engagementDigests/camille').get()).get('albums'), ['e1']);
    for (const id of ['noe', 'lou', 'tim', 'sam']) assert.equal((await db.doc(`engagementDigests/${id}`).get()).exists, false, id);
    assert.equal(await queueAlbumInvitations(db, new Date('2026-11-24T08:30:00Z')), 0);
  });

  test('nothing while the albums or the résumé are off', async () => {
    await db.doc('config/app').set({ features: { digest: false, eventAlbums: true } });
    assert.equal(await queueAlbumInvitations(db, morning), 0);
    assert.equal((await db.doc('albumInvites/2026-11-24').get()).exists, false);
  });

  test('a failure after the mark takes it off, so the next run tries again', async () => {
    // The events cannot be read once (quota, network): the run fails, the mark of the day goes.
    const failing = new Proxy(db, {
      get(target, key) {
        if (key === 'collection') {
          return (name: string) => (name === 'events'
            ? { where: () => ({ get: async () => { throw new Error('quota'); } }) }
            : target.collection(name));
        }
        const value = Reflect.get(target, key);
        return typeof value === 'function' ? value.bind(target) : value;
      },
    }) as Firestore;
    await assert.rejects(queueAlbumInvitations(failing, morning), /quota/);
    assert.equal((await db.doc('albumInvites/2026-11-24').get()).exists, false);
    assert.equal(await queueAlbumInvitations(db, new Date('2026-11-24T08:30:00Z')), 1);
    assert.deepEqual((await db.doc('engagementDigests/camille').get()).get('albums'), ['e1']);
  });
});
