// On se prête (récit 51): the texts of an ask, its answer and the reminder, then the notices and
// the reminder against the Firestore emulator. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import {
  acceptNotice, askNotice, declineNotice, lendDates, onLendItemUpdated, reminderNotice, sendLendReminders,
  type LendItemData,
} from '../src/lendItems';
import { Notice } from '../src/logic';
import { Sender } from '../src/store';

const DAY = 24 * 60 * 60 * 1000;
const at = (iso: string) => Timestamp.fromDate(new Date(iso));

function item(overrides: Partial<LendItemData> = {}): LendItemData {
  return {
    name: 'l\'appareil à raclette', ownerId: 'camille', ownerName: 'Camille', status: 'available',
    borrowerId: null, borrowerName: null, from: null, until: null, remindedAt: null, ...overrides,
  };
}

const asked = (overrides: Partial<LendItemData> = {}) =>
  item({ status: 'asked', borrowerId: 'ines', borrowerName: 'Inès', ...overrides });

describe('texts of a loan', () => {
  test('the dates of an ask, whole, half or none', () => {
    assert.equal(lendDates(null, null), '');
    assert.equal(lendDates(at('2026-12-12T10:00:00Z'), at('2026-12-15T10:00:00Z')), ' du samedi 12 décembre au mardi 15 décembre');
    assert.equal(lendDates(at('2026-12-12T10:00:00Z'), null), ' à partir du samedi 12 décembre');
    assert.equal(lendDates(null, at('2026-12-15T10:00:00Z')), ' jusqu\'au mardi 15 décembre');
  });

  test('the ask goes to the owner directly, and opens the thing', () => {
    const n = askNotice('i1', asked({ until: at('2026-12-15T10:00:00Z') }));
    assert.equal(n.body, 'Inès voudrait emprunter l\'appareil à raclette jusqu\'au mardi 15 décembre.');
    assert.equal(n.direct, true);
    assert.equal(n.lendItemId, 'i1');
    assert.equal(n.channel, 'helpRequests');
  });

  test('the answers, and the reminder that is never direct', () => {
    assert.equal(acceptNotice('i1', asked()).body, 'Camille te prête l\'appareil à raclette.');
    assert.equal(declineNotice('i1', asked()).body, 'Camille ne peut pas te prêter l\'appareil à raclette cette fois.');
    const r = reminderNotice('i1', asked({ status: 'lent', until: at('2026-12-15T10:00:00Z') }));
    assert.equal(r.title, 'Petit rappel');
    assert.equal(r.body, 'l\'appareil à raclette de Camille devait rentrer le mardi 15 décembre. Tu le rendras quand tu pourras.');
    assert.equal(r.direct, false);
  });
});

describe('against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'lend-items-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const now = new Date('2026-12-20T17:00:00Z');

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('lendItems'));
    await db.recursiveDelete(db.collection('members'));
    for (const id of ['camille', 'ines', 'noe']) {
      await db.doc(`members/${id}`).set({ firstName: id, profile: 'adult', uid: `uid-${id}` });
      await db.doc(`members/${id}/devices/token-${id}`).set({ updatedAt: new Date() });
    }
  });

  test('an ask tells the owner, « D\'accord » the one who asked', async () => {
    const { sent, send } = fakeSender();
    await onLendItemUpdated(db, send, 'i1', item(), asked(), 'uid-ines', now);
    await onLendItemUpdated(db, send, 'i1', asked(), asked({ status: 'lent' }), 'uid-camille', now);
    assert.deepEqual(sent.map((s) => [s.token, s.notice.title]), [
      ['token-camille', 'On voudrait t\'emprunter quelque chose'],
      ['token-ines', 'C\'est d\'accord'],
    ]);
  });

  test('« Pas cette fois » tells the one who asked; a dropped ask and « Rendu » tell nobody', async () => {
    const { sent, send } = fakeSender();
    await onLendItemUpdated(db, send, 'i1', asked(), item(), 'uid-camille', now);
    await onLendItemUpdated(db, send, 'i2', asked(), item(), 'uid-ines', now);
    await onLendItemUpdated(db, send, 'i3', asked({ status: 'lent' }), item(), 'uid-ines', now);
    await onLendItemUpdated(db, send, 'i4', item(), item({ name: 'la remorque' }), 'uid-camille', now);
    assert.deepEqual(sent.map((s) => [s.token, s.notice.title]), [['token-ines', 'Pas cette fois']]);
  });

  test('one reminder, 3 days after the return date, then never again', async () => {
    const lent = (until: number, extra: Record<string, unknown> = {}) => ({
      ...asked({ status: 'lent', until: Timestamp.fromMillis(now.getTime() - until * DAY) }), ...extra,
    });
    await db.doc('lendItems/late').set(lent(4));
    await db.doc('lendItems/soon').set(lent(2));
    await db.doc('lendItems/done').set(lent(10, { remindedAt: Timestamp.fromMillis(0) }));
    await db.doc('lendItems/open').set(asked({ status: 'lent' }));
    await db.doc('lendItems/free').set(item({ until: Timestamp.fromMillis(now.getTime() - 10 * DAY) }));
    const { sent, send } = fakeSender();
    assert.equal(await sendLendReminders(db, send, now), 1);
    assert.deepEqual(sent.map((s) => [s.token, s.notice.lendItemId]), [['token-ines', 'late']]);
    assert.ok((await db.doc('lendItems/late').get()).get('remindedAt'));
    assert.equal(await sendLendReminders(db, send, now), 0);
  });
});
