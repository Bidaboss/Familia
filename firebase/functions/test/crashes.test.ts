// « Plantages » (récit 6 sexies, rév. 220): the text of the notification (pure logic), then when the
// admin principal is told, against the Firestore emulator. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { CrashData, crashNotice, errorName, onCrashReported } from '../src/crashes';
import { Sender } from '../src/store';

const TRACE = 'java.lang.IllegalStateException\n\tat fr.familia.app.feature.home.HomeViewModel.load(HomeViewModel.kt:42)';

function crash(overrides: Partial<CrashData> = {}): CrashData {
  return { memberId: 'ines', appVersion: '1.4.0', screen: 'Home', trace: TRACE, signature: '0123456789abcdef', ...overrides };
}

describe('text', () => {
  test('the kind of error, the screen and the version, on the admin channel, opening Plantages', () => {
    const notice = crashNotice(crash());
    assert.equal(notice.title, 'Nouveau plantage');
    assert.equal(notice.body, 'IllegalStateException sur l\'écran Home, version 1.4.0. À voir dans Administration > Plantages.');
    assert.equal(notice.channel, 'admin');
    assert.equal(notice.feedback, 'crashes');
  });

  test('the name of the error, even nested or empty', () => {
    assert.equal(errorName('kotlin.KotlinNullPointerException'), 'KotlinNullPointerException');
    assert.equal(errorName('fr.familia.app.Foo$BarException\n\tat x'), 'BarException');
    assert.equal(errorName(''), 'Erreur');
  });

  test('without a screen, it says nothing of it', () => {
    assert.equal(crashNotice(crash({ screen: '' })).body, 'IllegalStateException, version 1.4.0. À voir dans Administration > Plantages.');
  });
});

describe('against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'crashes-test');
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

  async function report(id: string, data: CrashData) {
    await db.doc(`crashReports/${id}`).set(data);
  }

  const noon = new Date('2026-11-21T11:00:00Z');
  const evening = new Date('2026-11-21T17:00:00Z');
  const nextDay = new Date('2026-11-22T11:00:00Z');

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('members'));
    await db.recursiveDelete(db.collection('crashReports'));
    await db.recursiveDelete(db.collection('crashNotices'));
    for (const id of ['noe', 'ines']) {
      await db.doc(`members/${id}`).set({ firstName: id, profile: 'adult', householdId: 'h1', parentIds: [], uid: null });
      await db.doc(`members/${id}/devices/token-${id}`).set({ updatedAt: new Date() });
    }
    await db.doc('config/family').set({ ownerMemberId: 'noe' });
  });

  test('the first report of a new kind tells the admin principal, and only them', async () => {
    const { sent, send } = fakeSender();
    await report('r1', crash());
    await onCrashReported(db, send, 'r1', crash(), noon);
    assert.deepEqual(sent.map((s) => s.token), ['token-noe']);
  });

  test('the same kind again tells nobody', async () => {
    const first = fakeSender();
    await report('r1', crash());
    await onCrashReported(db, first.send, 'r1', crash(), noon);
    const second = fakeSender();
    await report('r2', crash({ memberId: 'noe' }));
    await onCrashReported(db, second.send, 'r2', crash({ memberId: 'noe' }), nextDay);
    assert.equal(second.sent.length, 0);
  });

  test('a second new kind the same day waits: one notice a day at most', async () => {
    const first = fakeSender();
    await report('r1', crash());
    await onCrashReported(db, first.send, 'r1', crash(), noon);
    const other = crash({ signature: 'fedcba9876543210' });
    const second = fakeSender();
    await report('r2', other);
    await onCrashReported(db, second.send, 'r2', other, evening);
    assert.equal(second.sent.length, 0);
    const third = crash({ signature: 'aaaaaaaaaaaaaaaa' });
    const next = fakeSender();
    await report('r3', third);
    await onCrashReported(db, next.send, 'r3', third, nextDay);
    assert.equal(next.sent.length, 1);
  });

  test('a kind settled (its reports removed) that comes back is new again', async () => {
    const first = fakeSender();
    await report('r1', crash());
    await onCrashReported(db, first.send, 'r1', crash(), noon);
    await db.doc('crashReports/r1').delete();
    const again = fakeSender();
    await report('r2', crash());
    await onCrashReported(db, again.send, 'r2', crash(), nextDay);
    assert.equal(again.sent.length, 1);
  });

  test('without an admin principal, nobody is told', async () => {
    await db.doc('config/family').delete();
    const { sent, send } = fakeSender();
    await report('r1', crash());
    await onCrashReported(db, send, 'r1', crash(), noon);
    assert.equal(sent.length, 0);
  });
});
