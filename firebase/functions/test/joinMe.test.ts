// « Rejoignez-moi » (récit 33): the text of the notification (pure logic), then who is told and the
// cleanup when a member leaves, against the Firestore emulator. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { deleteJoinMeOf, JoinMeData, joinMeNotice, onJoinMeCreated } from '../src/joinMe';
import { Sender } from '../src/store';

function share(overrides: Partial<JoinMeData> = {}): JoinMeData {
  return {
    authorId: 'camille', authorName: 'Camille', recipientIds: ['ines', 'noe'],
    name: 'Plage des Sables', address: '1 promenade de la Mer', lat: 46.49, lng: -1.79, note: null, ...overrides,
  };
}

describe('text', () => {
  test('it says who and where, then the note', () => {
    const n = joinMeNotice('j1', share({ note: 'Au poste de secours' }));
    assert.equal(n.title, 'Camille : rejoins-moi');
    assert.equal(n.body, 'Plage des Sables · 1 promenade de la Mer\nAu poste de secours');
    assert.equal(n.channel, 'joinMe');
    assert.equal(n.joinMeId, 'j1');
  });

  test('a position without name nor address still reads', () => {
    assert.equal(joinMeNotice('j1', share({ name: null, address: null })).body, 'Sa position du moment');
    assert.equal(joinMeNotice('j1', share({ name: null })).body, '1 promenade de la Mer');
  });
});

describe('against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'joinme-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('joinMe'));
    await db.recursiveDelete(db.collection('members'));
    for (const id of ['camille', 'ines', 'noe']) await db.doc(`members/${id}/devices/token-${id}`).set({ updatedAt: new Date() });
    await db.doc('config/app').set({ features: { places: true } });
    await db.doc('config/family').set({ ownerMemberId: 'ines' });
  });

  test('module on: every recipient is told, never the author', async () => {
    const { sent, send } = fakeSender();
    await onJoinMeCreated(db, send, 'j1', share({ recipientIds: ['ines', 'noe', 'camille'] }));
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-ines', 'token-noe']);
  });

  test('module off: only the admin principal, and only when ticked', async () => {
    await db.doc('config/app').set({ features: { places: false } });
    const first = fakeSender();
    await onJoinMeCreated(db, first.send, 'j1', share());
    assert.deepEqual(first.sent.map((s) => s.token), ['token-ines']);
    const second = fakeSender();
    await onJoinMeCreated(db, second.send, 'j2', share({ recipientIds: ['noe'] }));
    assert.equal(second.sent.length, 0);
  });

  test('a member removed: the places they sent go, the others stay', async () => {
    await db.doc('joinMe/a').set(share());
    await db.doc('joinMe/b').set(share({ authorId: 'noe', recipientIds: ['camille'] }));
    await deleteJoinMeOf(db, 'camille');
    assert.equal((await db.doc('joinMe/a').get()).exists, false);
    assert.equal((await db.doc('joinMe/b').get()).exists, true);
  });
});
