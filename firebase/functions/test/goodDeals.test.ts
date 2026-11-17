// Bons plans (récit 45): the summary of the day (pure texts, then against the Firestore emulator)
// and the cleanup of the ended ones. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { deleteEndedGoodDeals, goodDealsNotice, sendGoodDealsSummary } from '../src/goodDeals';
import { Notice } from '../src/logic';
import { Sender } from '../src/store';

const DAY = 24 * 60 * 60 * 1000;

describe('texts of the summary', () => {
  test('one bon plan is named with its author, several are counted', () => {
    assert.equal(goodDealsNotice([]), null);
    const one = goodDealsNotice([{ title: 'Pneus à -30 %', authorId: 'camille', authorName: 'Camille' }]);
    assert.equal(one?.title, 'Nouveau bon plan');
    assert.equal(one?.body, 'Camille : Pneus à -30 %');
    assert.equal(one?.goodDeals, 'list');
    const two = goodDealsNotice([
      { title: 'Pneus à -30 %', authorId: 'camille', authorName: 'Camille' },
      { title: 'Fraises à cueillir', authorId: 'ines', authorName: 'Inès' },
    ]);
    assert.equal(two?.title, '2 nouveaux bons plans');
    assert.equal(two?.body, 'Pneus à -30 % · Fraises à cueillir');
  });

  test('a long list is cut', () => {
    const many = Array.from({ length: 12 }, (_, i) => ({ title: `Bon plan numéro ${i} assez long`, authorId: 'camille', authorName: 'Camille' }));
    const notice = goodDealsNotice(many);
    assert.ok((notice?.body.length ?? 0) <= 180);
    assert.ok(notice?.body.endsWith('…'));
  });
});

describe('against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'good-deals-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const now = new Date('2026-12-01T17:00:00Z');

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  async function deal(id: string, authorId: string, overrides: Record<string, unknown> = {}) {
    await db.doc(`goodDeals/${id}`).set({
      kind: 'deal', title: `Bon plan ${id}`, authorId, authorName: authorId,
      endsAt: Timestamp.fromMillis(now.getTime() + 7 * DAY),
      createdAt: Timestamp.fromMillis(now.getTime() - 2 * 60 * 60 * 1000), ...overrides,
    });
  }

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('goodDeals'));
    await db.recursiveDelete(db.collection('members'));
    for (const id of ['camille', 'ines', 'noe']) {
      await db.doc(`members/${id}`).set({ firstName: id, profile: 'adult' });
      await db.doc(`members/${id}/devices/token-${id}`).set({ updatedAt: new Date() });
    }
    await db.doc('members/lou').set({ firstName: 'lou', profile: 'childWithoutApp' });
    await db.doc('config/app').set({ features: { goodDeals: true } });
    await db.doc('config/family').set({ ownerMemberId: 'ines' });
  });

  test('each member hears of the new bons plans of the others, once, in a summary', async () => {
    await deal('d1', 'camille');
    await deal('d2', 'ines');
    const { sent, send } = fakeSender();
    assert.equal(await sendGoodDealsSummary(db, send, now), 3);
    const byToken = new Map(sent.map((s) => [s.token, s.notice]));
    assert.equal(byToken.get('token-noe')?.title, '2 nouveaux bons plans');
    assert.equal(byToken.get('token-camille')?.body, 'ines : Bon plan d2');
    assert.equal(byToken.get('token-ines')?.body, 'camille : Bon plan d1');
  });

  test('no old, ended or bonne adresse in the summary', async () => {
    await deal('d1', 'camille', { createdAt: Timestamp.fromMillis(now.getTime() - 2 * DAY) });
    await deal('d2', 'camille', { endsAt: Timestamp.fromMillis(now.getTime() - 60 * 1000) });
    await deal('a1', 'camille', { kind: 'address', endsAt: null });
    const { sent, send } = fakeSender();
    assert.equal(await sendGoodDealsSummary(db, send, now), 0);
    assert.equal(sent.length, 0);
  });

  test('module off: only the admin principal hears of it', async () => {
    await db.doc('config/app').set({ features: { goodDeals: false } });
    await deal('d1', 'camille');
    const { sent, send } = fakeSender();
    await sendGoodDealsSummary(db, send, now);
    assert.deepEqual(sent.map((s) => s.token), ['token-ines']);
  });

  test('a bon plan ended more than 30 days ago is deleted, the others stay', async () => {
    await deal('old', 'camille', { endsAt: Timestamp.fromMillis(now.getTime() - 31 * DAY) });
    await deal('recent', 'camille', { endsAt: Timestamp.fromMillis(now.getTime() - 29 * DAY) });
    await deal('a1', 'camille', { kind: 'address', endsAt: null });
    assert.equal(await deleteEndedGoodDeals(db, now), 1);
    assert.equal((await db.doc('goodDeals/old').get()).exists, false);
    assert.equal((await db.doc('goodDeals/recent').get()).exists, true);
    assert.equal((await db.doc('goodDeals/a1').get()).exists, true);
  });
});
