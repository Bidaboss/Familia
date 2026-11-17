import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { changeNotice } from '../src/logic';
import {
  toldBefore,
  occurrenceDays, occurrenceId, ruleDays, ruleLabel, syncSeries, writtenBySeries, type SeriesData, type SeriesRule,
} from '../src/series';

const rule = (o: Partial<SeriesRule> = {}): SeriesRule => ({ freq: 'weekly', interval: 1, monthlyBy: null, start: '2026-12-06', until: null, count: null, ...o });

test('occurrences taken out together tell each member once, at the first one', () => {
  assert.deepEqual(toldBefore([['a', 'b'], ['a', 'c'], ['b']]), [[], ['a', 'b'], ['a', 'b', 'c']]);
  assert.deepEqual(toldBefore([]), []);
});

describe('the days of a series, without the emulator', () => {
  test('every week and every 2 weeks', () => {
    assert.deepEqual(ruleDays(rule(), '2026-12-27'), ['2026-12-06', '2026-12-13', '2026-12-20', '2026-12-27']);
    assert.deepEqual(ruleDays(rule({ interval: 2 }), '2027-01-03'), ['2026-12-06', '2026-12-20', '2027-01-03']);
  });

  test('every month on the same date: a 31 only where it exists', () => {
    assert.deepEqual(ruleDays(rule({ freq: 'monthly', monthlyBy: 'date', start: '2027-01-31' }), '2027-05-31'),
      ['2027-01-31', '2027-03-31', '2027-05-31']);
  });

  test('every month on the same weekday: the 1st Sunday, a 5th only in the months that have one', () => {
    assert.deepEqual(ruleDays(rule({ freq: 'monthly', monthlyBy: 'weekday', start: '2026-12-06' }), '2027-03-31'),
      ['2026-12-06', '2027-01-03', '2027-02-07', '2027-03-07']);
    assert.deepEqual(ruleDays(rule({ freq: 'monthly', monthlyBy: 'weekday', start: '2027-01-31' }), '2027-06-30'),
      ['2027-01-31', '2027-05-30']);
  });

  test('every year: a 29 February only in a leap year', () => {
    assert.deepEqual(ruleDays(rule({ freq: 'yearly', start: '2028-02-29' }), '2033-01-01'), ['2028-02-29', '2032-02-29']);
  });

  test('it stops at a day, or after N times, taken-out days counted', () => {
    assert.deepEqual(ruleDays(rule({ until: '2026-12-20' }), '2027-12-31'), ['2026-12-06', '2026-12-13', '2026-12-20']);
    assert.deepEqual(ruleDays(rule({ count: 3 }), '2027-12-31'), ['2026-12-06', '2026-12-13', '2026-12-20']);
    assert.deepEqual(occurrenceDays(rule({ count: 3 }), ['2026-12-13'], '2026-12-01', '2027-12-31'), ['2026-12-06', '2026-12-20']);
    assert.deepEqual(occurrenceDays(rule(), [], '2026-12-14', '2026-12-27'), ['2026-12-20', '2026-12-27']);
  });

  test('the words of the notice, and what the server wrote', () => {
    assert.equal(ruleLabel(rule()), 'chaque semaine');
    assert.equal(ruleLabel(rule({ interval: 2 })), 'toutes les 2 semaines');
    assert.equal(ruleLabel(rule({ freq: 'yearly' })), 'chaque année');
    assert.equal(occurrenceId('s1', '2026-12-06'), 's1_20261206');
    const ev = { title: 'Marché', date: '2026-12-06', time: null, place: null, note: null, targetType: 'family', targetMemberId: null, householdId: null };
    assert.match(changeNotice('added', 'e1', ev, 'Camille', false, ' Puis chaque semaine.').body, /Puis chaque semaine\.$/);
    assert.equal(writtenBySeries(undefined, { seriesId: 's1', bySeries: 1 }), true);
    assert.equal(writtenBySeries({ seriesId: 's1', bySeries: 1 }, { seriesId: 's1', bySeries: 2 }), true);
    assert.equal(writtenBySeries({ seriesId: 's1', bySeries: 1 }, { seriesId: 's1', bySeries: 1, detached: true }), false);
    assert.equal(writtenBySeries(undefined, { title: 'x' }), false);
  });
});

describe('the occurrences against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'series-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const today = '2026-12-01';
  const series = (o: Partial<SeriesData> = {}): SeriesData => ({
    title: 'Marché', time: '10:00', place: null, note: null,
    targetType: 'family', targetMemberId: null, householdId: null,
    authorId: 'camille', authorName: 'Camille',
    rule: rule({ until: '2027-01-03' }), exdates: [], applyFrom: null, ...o,
  });
  const days = async () => (await db.collection('events').where('seriesId', '==', 's1').get()).docs.map((d) => d.id).sort();

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('events'));
    await db.recursiveDelete(db.collection('eventSeries'));
    await db.doc('members/camille').set({ firstName: 'Camille', profile: 'adult', householdId: 'h1', parentIds: [] });
  });

  test('a series whose author was removed writes no new day, and keeps the others', async () => {
    await db.doc('eventSeries/s1').set(series({ rule: rule({ until: '2026-12-13' }) }));
    await syncSeries(db, 's1', today);
    await db.doc('members/camille').delete();
    await db.doc('eventSeries/s1').update({ rule: rule({ until: '2027-01-03' }) });
    const r = await syncSeries(db, 's1', today);
    assert.deepEqual(r.created, []);
    assert.deepEqual(await days(), ['s1_20261206', 's1_20261213']);
  });

  test('a new series writes its days to come', async () => {
    await db.doc('eventSeries/s1').set(series());
    const r = await syncSeries(db, 's1', today);
    assert.equal(r.created.length, 5);
    assert.deepEqual(await days(), ['s1_20261206', 's1_20261213', 's1_20261220', 's1_20261227', 's1_20270103']);
    const first = (await db.doc('events/s1_20261206').get()).data()!;
    assert.equal(first.title, 'Marché');
    assert.equal(first.occurrence, '2026-12-06');
    assert.equal(typeof first.bySeries, 'number');
    assert.deepEqual(await syncSeries(db, 's1', today), { created: [], updated: [], cancelled: [] });
  });

  test('« celle-ci et les suivantes » rewrites from that day, never one changed by hand', async () => {
    await db.doc('eventSeries/s1').set(series());
    await syncSeries(db, 's1', today);
    await db.doc('events/s1_20261227').update({ time: '11:00', detached: true });
    await db.doc('eventSeries/s1').update({ title: 'Marché bio', applyFrom: '2026-12-20' });
    const r = await syncSeries(db, 's1', today);
    assert.deepEqual(r.updated.sort(), ['s1_20261220', 's1_20270103']);
    assert.equal((await db.doc('events/s1_20261213').get()).get('title'), 'Marché');
    assert.equal((await db.doc('events/s1_20261227').get()).get('title'), 'Marché');
    assert.equal((await db.doc('events/s1_20270103').get()).get('title'), 'Marché bio');
  });

  test('a day taken out, an earlier end, or the series gone take the occurrences out', async () => {
    await db.doc('eventSeries/s1').set(series());
    await syncSeries(db, 's1', today);
    await db.doc('events/s1_20261213/rsvps/ines').set({ status: 'yes' });
    await db.doc('eventSeries/s1').update({ exdates: ['2026-12-13'] });
    assert.deepEqual((await syncSeries(db, 's1', today)).cancelled, ['s1_20261213']);
    await db.doc('eventSeries/s1').update({ rule: rule({ until: '2026-12-20' }) });
    assert.deepEqual((await syncSeries(db, 's1', today)).cancelled.sort(), ['s1_20261227', 's1_20270103']);
    assert.deepEqual(await days(), ['s1_20261206', 's1_20261220']);
    await db.doc('eventSeries/s1').delete();
    await syncSeries(db, 's1', today);
    assert.deepEqual(await days(), []);
  });

  test('past occurrences stay, with their answers', async () => {
    await db.doc('eventSeries/s1').set(series());
    await syncSeries(db, 's1', today);
    await db.doc('eventSeries/s1').delete();
    await syncSeries(db, 's1', '2026-12-14');
    assert.deepEqual(await days(), ['s1_20261206', 's1_20261213']);
  });
});
