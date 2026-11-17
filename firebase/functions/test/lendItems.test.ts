// On se prête (récit 51): the texts of an ask, its answer and the reminder, then the notices and
// the reminder against the Firestore emulator. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import {
  acceptNotice, askNotice, declineNotice, deleteLoanOf, lendDates, onLendLoanWritten, releaseLendItemsOf, reminderNotice, sendLendReminders,
  type LendItemData, type LendLoanData, type LendThingData,
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

/** The thing as everyone reads it: no name, no date of the loan. */
function thing(overrides: Partial<LendThingData> = {}): LendThingData {
  return { name: 'l\'appareil à raclette', ownerId: 'camille', ownerName: 'Camille', lent: false, ...overrides };
}

/** The ask, or the loan, apart. */
function loan(overrides: Partial<LendLoanData> = {}): LendLoanData {
  return {
    ownerId: 'camille', parties: ['camille', 'ines'], status: 'asked', borrowerId: 'ines', borrowerName: 'Inès',
    from: null, until: null, remindedAt: null, ...overrides,
  };
}

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
    await db.recursiveDelete(db.collection('lendLoans'));
    await db.recursiveDelete(db.collection('members'));
    for (const id of ['camille', 'ines', 'noe']) {
      await db.doc(`members/${id}`).set({ firstName: id, profile: 'adult', uid: `uid-${id}` });
      await db.doc(`members/${id}/devices/token-${id}`).set({ updatedAt: new Date() });
    }
    for (const id of ['i1', 'i2', 'i3', 'i4']) await db.doc(`lendItems/${id}`).set(thing({ lent: true }));
  });

  test('an ask tells the owner, « D\'accord » the one who asked', async () => {
    const { sent, send } = fakeSender();
    await onLendLoanWritten(db, send, 'i1', undefined, loan({ until: at('2026-12-15T10:00:00Z') }), 'uid-ines', now);
    await onLendLoanWritten(db, send, 'i1', loan(), loan({ status: 'lent' }), 'uid-camille', now);
    assert.deepEqual(sent.map((s) => [s.token, s.notice.title]), [
      ['token-camille', 'On voudrait t\'emprunter quelque chose'],
      ['token-ines', 'C\'est d\'accord'],
    ]);
    assert.equal(sent[0].notice.body, 'Inès voudrait emprunter l\'appareil à raclette jusqu\'au mardi 15 décembre.');
  });

  test('« Pas cette fois » tells the one who asked; a dropped ask, « Rendu » and a loan gone with its thing tell nobody', async () => {
    const { sent, send } = fakeSender();
    await onLendLoanWritten(db, send, 'i1', loan(), undefined, 'uid-camille', now);
    await onLendLoanWritten(db, send, 'i2', loan(), undefined, 'uid-ines', now);
    await onLendLoanWritten(db, send, 'i3', loan({ status: 'lent' }), undefined, 'uid-ines', now);
    // The thing removed: the server takes the ask away, nobody is told.
    await onLendLoanWritten(db, send, 'gone', loan(), undefined, undefined, now);
    await onLendLoanWritten(db, send, 'i4', loan(), undefined, undefined, now);
    assert.deepEqual(sent.map((s) => [s.token, s.notice.title]), [['token-ines', 'Pas cette fois']]);
  });

  test('one reminder, 3 days after the return date, then never again', async () => {
    const lent = (until: number, extra: Partial<LendLoanData> = {}) =>
      loan({ status: 'lent', until: Timestamp.fromMillis(now.getTime() - until * DAY), ...extra });
    for (const id of ['late', 'soon', 'done', 'open']) await db.doc(`lendItems/${id}`).set(thing({ lent: true }));
    await db.doc('lendLoans/late').set(lent(4));
    await db.doc('lendLoans/soon').set(lent(2));
    await db.doc('lendLoans/done').set(lent(10, { remindedAt: Timestamp.fromMillis(0) }));
    await db.doc('lendLoans/open').set(loan({ status: 'lent' }));
    await db.doc('lendLoans/asked').set(loan({ until: Timestamp.fromMillis(now.getTime() - 10 * DAY) }));
    const { sent, send } = fakeSender();
    assert.equal(await sendLendReminders(db, send, now), 1);
    assert.deepEqual(sent.map((s) => [s.token, s.notice.lendItemId]), [['token-ines', 'late']]);
    assert.match(sent[0].notice.body, /^l'appareil à raclette de Camille devait rentrer le /);
    assert.ok((await db.doc('lendLoans/late').get()).get('remindedAt'));
    assert.equal(await sendLendReminders(db, send, now), 0);
  });

  test('a thing removed takes its loan with it', async () => {
    await db.doc('lendLoans/i1').set(loan());
    await deleteLoanOf(db, 'i1');
    assert.equal((await db.doc('lendLoans/i1').get()).exists, false);
    await deleteLoanOf(db, 'none');
  });

  test('a member removed: their things go, with their loans', async () => {
    await db.doc('lendItems/mine').set(thing({ ownerId: 'ines', lent: true }));
    await db.doc('lendLoans/mine').set(loan({ ownerId: 'ines', parties: ['ines', 'noe'], borrowerId: 'noe', borrowerName: 'Noé' }));
    await db.doc('lendItems/other').set(thing());
    assert.equal(await releaseLendItemsOf(db, 'ines'), 1);
    assert.equal((await db.doc('lendItems/mine').get()).exists, false);
    assert.equal((await db.doc('lendLoans/mine').get()).exists, false);
    assert.equal((await db.doc('lendItems/other').get()).exists, true);
  });

  test('a member removed: what they asked for or had is free again, entirely', async () => {
    await db.doc('lendItems/asked').set(thing({ lent: true }));
    await db.doc('lendLoans/asked').set(loan());
    await db.doc('lendItems/lent').set(thing({ lent: true }));
    await db.doc('lendLoans/lent').set(loan({ status: 'lent', until: Timestamp.fromMillis(now.getTime()) }));
    await db.doc('lendItems/noe').set(thing({ lent: true }));
    await db.doc('lendLoans/noe').set(loan({ parties: ['camille', 'noe'], borrowerId: 'noe', borrowerName: 'Noé' }));
    assert.equal(await releaseLendItemsOf(db, 'ines'), 2);
    for (const id of ['asked', 'lent']) {
      assert.equal((await db.doc(`lendLoans/${id}`).get()).exists, false);
      const doc = (await db.doc(`lendItems/${id}`).get()).data();
      assert.equal(doc?.lent, false);
      assert.equal(doc?.ownerId, 'camille');
    }
    assert.equal((await db.doc('lendLoans/noe').get()).get('borrowerId'), 'noe');
    assert.equal((await db.doc('lendItems/noe').get()).get('lent'), true);
  });
});
