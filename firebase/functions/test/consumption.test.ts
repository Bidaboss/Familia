// Consommation (récit 1 sexies): the estimate and the alerts (pure logic), then the daily check
// against the Firestore emulator, with figures in place of Cloud Monitoring. Fictional data only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import {
  alertNotice, alerts, checkConsumption, checkConsumptionIfDue, dayCost, DayUse, FREE, MonitoringReader, monthCost, monthDownloads, parisMidnight, pickMetrics,
} from '../src/consumption';
import { Notice } from '../src/logic';
import { Sender } from '../src/store';

const quiet: DayUse = { day: '2026-11-20', reads: 3_000, writes: 500, deletes: 20, storageBytes: 5 * 1024 * 1024 };

describe('the estimate and the alerts', () => {
  test('within the free quotas, nothing to pay and no alert', () => {
    assert.equal(dayCost(quiet), 0);
    assert.deepEqual(alerts(quiet, 0), []);
  });

  test('beyond the quotas, the cost counts only what passes them', () => {
    const busy = { ...quiet, reads: FREE.reads + 100_000 };
    assert.ok(Math.abs(dayCost(busy) - 0.06) < 1e-9);
  });

  test('80 % of a limit or a month over 0,50 € gives an alert', () => {
    assert.deepEqual(alerts({ ...quiet, reads: 40_000 }, 0), ['reads']);
    assert.deepEqual(alerts({ ...quiet, writes: 16_000, storageBytes: 900 * 1024 * 1024 }, 0.6), ['writes', 'storage', 'cost']);
  });

  test('the month adds up only its own days', () => {
    const busy = { ...quiet, reads: FREE.reads + 100_000 };
    const cost = monthCost([busy, { ...busy, day: '2026-11-21' }, { ...busy, day: '2026-10-31' }], '2026-11');
    assert.ok(Math.abs(cost - 0.12) < 1e-9);
  });

  test('the notice says the figure in French, on the admin channel', () => {
    const notice = alertNotice('reads', { ...quiet, reads: 42_000 }, 0);
    assert.equal(notice.channel, 'admin');
    assert.match(notice.body, /42\s000 sur 50\s000 gratuites \(84 %\)/);
  });

  test('the newest metric names win, the old ones are a fallback', () => {
    const p = 'firestore.googleapis.com/';
    const picked = pickMetrics([`${p}document/read_count`, `${p}document/read_ops_count`, `${p}document/write_count`, `${p}storage/data_and_index_storage_bytes`]);
    assert.equal(picked.reads, `${p}document/read_ops_count`);
    assert.equal(picked.writes, `${p}document/write_count`);
    assert.equal(picked.deletes, undefined);
    assert.equal(picked.storage, `${p}storage/data_and_index_storage_bytes`);
  });

  test('the photos of Cloud Storage count too: their size, and the downloads of the month (démo du 21)', () => {
    // The album of the 21: 13 people, 20 photos each of about 300 Ko, thumbnails of 30 Ko; each of
    // the 13 sees every thumbnail and every photo once, the image cache does the rest.
    const album = 13 * 20 * (300_000 + 30_000);
    const day21 = { ...quiet, day: '2026-11-21', photoBytes: album, downloadBytes: 13 * album };
    assert.equal(dayCost(day21), 0);
    assert.equal(monthCost([day21], '2026-11'), 0);
    assert.deepEqual(alerts(day21, 0, monthDownloads([day21], '2026-11')), []);

    assert.deepEqual(alerts({ ...quiet, photoBytes: 4.2e9 }, 0), ['photos']);
    const heavy = [{ ...quiet, downloadBytes: 50e9 }, { ...quiet, day: '2026-11-21', downloadBytes: 60e9 }];
    assert.equal(monthDownloads(heavy, '2026-11'), 110e9);
    assert.deepEqual(alerts(heavy[1], 0, 110e9), ['downloads']);
    // 10 Go beyond the 100 free, at 0,15 € the Go.
    assert.ok(Math.abs(monthCost(heavy, '2026-11') - 1.5) < 1e-9);
    assert.match(alertNotice('downloads', heavy[1], 0, 110e9).body, /110 Go sur 100 Go gratuits \(110 %\)/);
    assert.match(alertNotice('photos', { ...quiet, photoBytes: 4.2e9 }, 0).body, /4,2 Go sur 5 Go gratuits \(84 %\)/);

    const s = 'storage.googleapis.com/';
    const picked = pickMetrics([`${s}storage/total_bytes`, `${s}storage/v2/total_bytes`, `${s}network/sent_bytes_count`]);
    assert.equal(picked.photos, `${s}storage/v2/total_bytes`);
    assert.equal(picked.downloads, `${s}network/sent_bytes_count`);
    assert.equal(pickMetrics([]).photos, undefined);
  });

  test('a Paris day starts at 23 h UTC in winter, 22 h in summer', () => {
    assert.equal(parisMidnight('2026-11-20').toISOString(), '2026-11-19T23:00:00.000Z');
    assert.equal(parisMidnight('2026-07-14').toISOString(), '2026-07-13T22:00:00.000Z');
  });
});

describe('the daily check', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'consumption-test');
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

  const p = 'firestore.googleapis.com/';
  function monitoring(figures: Record<string, number>): MonitoringReader {
    return {
      metricTypes: async () => Object.keys(figures).map((k) => p + k),
      total: async (type) => figures[type.slice(p.length)] ?? null,
    };
  }

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('consumption'));
    await db.recursiveDelete(db.collection('members'));
    await db.doc('config/family').set({ ownerMemberId: 'camille' });
    await db.doc('members/camille/devices/token-camille').set({ updatedAt: new Date() });
    await db.doc('members/noe/devices/token-noe').set({ updatedAt: new Date() });
  });

  test('a quiet day is kept, and nobody is warned', async () => {
    const { sent, send } = fakeSender();
    const found = await checkConsumption(db, monitoring({ 'document/read_ops_count': 3_000, 'document/write_ops_count': 400 }), send, '2026-11-20');
    assert.deepEqual(found, []);
    assert.equal(sent.length, 0);
    const doc = (await db.doc('consumption/2026-11-20').get()).data()!;
    assert.equal(doc.reads, 3_000);
    assert.equal(doc.writes, 400);
    assert.equal(doc.deletes, 0);
    assert.equal(doc.storageBytes, null);
    assert.equal(doc.monthCost, 0);
  });

  test('a busy day warns the admin principal only, once', async () => {
    const { sent, send } = fakeSender();
    const reader = monitoring({ 'document/read_ops_count': 45_000 });
    assert.deepEqual(await checkConsumption(db, reader, send, '2026-11-20'), ['reads']);
    await checkConsumption(db, reader, send, '2026-11-20');
    assert.deepEqual(sent.map((s) => s.token), ['token-camille']);
  });

  test('the cost of the month counts the days already kept', async () => {
    await db.doc('consumption/2026-11-19').set({ day: '2026-11-19', reads: FREE.reads + 1_000_000, writes: 0, deletes: 0, storageBytes: null });
    await db.doc('consumption/2026-10-31').set({ day: '2026-10-31', reads: FREE.reads + 9_000_000, writes: 0, deletes: 0, storageBytes: null });
    const { sent, send } = fakeSender();
    const found = await checkConsumption(db, monitoring({ 'document/read_ops_count': 1_000 }), send, '2026-11-20');
    assert.deepEqual(found, ['cost']);
    assert.ok(Math.abs((await db.doc('consumption/2026-11-20').get()).get('monthCost') - 0.6) < 1e-9);
    assert.equal(sent[0].notice.title, 'Familia commence à coûter');
  });

  test('the photos are kept with the day, and their downloads add up over the month', async () => {
    const s = 'storage.googleapis.com/';
    const figures: Record<string, number> = { [`${s}storage/v2/total_bytes`]: 90e6, [`${s}network/sent_bytes_count`]: 1.2e9 };
    const reader: MonitoringReader = { metricTypes: async () => Object.keys(figures), total: async (type) => figures[type] ?? null };
    await db.doc('consumption/2026-11-20').set({ day: '2026-11-20', reads: 0, writes: 0, deletes: 0, storageBytes: null, downloadBytes: 0.8e9 });
    const { sent, send } = fakeSender();
    assert.deepEqual(await checkConsumption(db, reader, send, '2026-11-21'), []);
    const doc = (await db.doc('consumption/2026-11-21').get()).data()!;
    assert.equal(doc.photoBytes, 90e6);
    assert.equal(doc.downloadBytes, 1.2e9);
    assert.equal(doc.monthDownloadBytes, 2e9);
    assert.equal(doc.monthCost, 0);
    assert.equal(sent.length, 0);
  });

  test('without the Monitoring role, the day is kept with an error', async () => {
    const { sent, send } = fakeSender();
    const refused: MonitoringReader = { metricTypes: async () => { throw new Error('Monitoring 403'); }, total: async () => null };
    assert.deepEqual(await checkConsumption(db, refused, send, '2026-11-20'), []);
    assert.equal((await db.doc('consumption/2026-11-20').get()).get('error'), 'monitoring');
    assert.equal(sent.length, 0);
  });

  test('the 30-minute task reads the day before once, from 8 h in Paris', async () => {
    const { send } = fakeSender();
    let reads = 0;
    const counting: MonitoringReader = {
      metricTypes: async () => { reads++; return []; },
      total: async () => null,
    };
    // 7 h 30 in Paris (6 h 30 UTC in winter): too early.
    assert.equal(await checkConsumptionIfDue(db, counting, send, new Date('2026-11-21T06:30:00Z')), null);
    // 8 h, then 8 h 30: one reading only, of the 20th.
    assert.equal(await checkConsumptionIfDue(db, counting, send, new Date('2026-11-21T07:00:00Z')), '2026-11-20');
    assert.equal(await checkConsumptionIfDue(db, counting, send, new Date('2026-11-21T07:30:00Z')), null);
    assert.equal(reads, 1);
    assert.ok((await db.doc('consumption/2026-11-20').get()).exists);
  });
});
