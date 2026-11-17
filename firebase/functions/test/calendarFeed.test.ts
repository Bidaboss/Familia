import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { feedEvents, feedFor, foldLine, icsFeed, icsText, isFeedToken, parisToUtc } from '../src/calendarFeed';
import type { Member } from '../src/logic';

const TOKEN = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO_-';
const event = (title: string, date: string, time: string | null, place: string | null = null) => ({
  title, date, time, place, note: null, targetType: 'family', targetMemberId: null, householdId: null,
});

describe('the calendar feed, without the emulator', () => {
  test('a token is 43 characters of base64url, nothing else', () => {
    assert.equal(isFeedToken(TOKEN.slice(0, 43)), true);
    assert.equal(isFeedToken('court'), false);
    assert.equal(isFeedToken(`${TOKEN.slice(0, 42)}/`), false);
    assert.equal(isFeedToken(undefined), false);
  });

  test('texts are escaped and long lines folded', () => {
    assert.equal(icsText('Repas; chez Inès, 19 h\nApporter du pain'), 'Repas\\; chez Inès\\, 19 h\\nApporter du pain');
    const folded = foldLine(`SUMMARY:${'é'.repeat(60)}`);
    for (const line of folded.split('\r\n')) assert.ok(Buffer.byteLength(line) <= 75);
    assert.equal(folded.replace(/\r\n /g, ''), `SUMMARY:${'é'.repeat(60)}`);
  });

  test('Paris time becomes UTC, winter and summer', () => {
    assert.equal(parisToUtc('2026-11-21', '12:30'), '20261121T113000Z');
    assert.equal(parisToUtc('2027-07-14', '22:00'), '20270714T200000Z');
  });

  test('a timed event lasts an hour, an all-day event the whole day', () => {
    const feed = icsFeed([
      { id: 'e1', data: event('Galette', '2026-12-24', '19:00', 'Chez Camille') },
      { id: 'e2', data: event('Vacances', '2026-12-31', null) },
    ], new Date('2026-11-21T10:00:00Z'));
    assert.ok(feed.startsWith('BEGIN:VCALENDAR\r\n'));
    assert.ok(feed.includes('UID:e1@familia\r\n'));
    assert.ok(feed.includes('DTSTART:20261224T180000Z\r\nDTEND:20261224T190000Z\r\n'));
    assert.ok(feed.includes('LOCATION:Chez Camille\r\n'));
    assert.ok(feed.includes('DTSTART;VALUE=DATE:20261231\r\nDTEND;VALUE=DATE:20270101\r\n'));
    assert.ok(feed.endsWith('END:VCALENDAR\r\n'));
  });
});

describe('what a link may carry of the children (PRD rév. 267)', () => {
  const adult = (id: string, householdId = 'h1'): Member => ({ id, firstName: id, profile: 'adult', householdId, parentIds: [], uid: null });
  const camille = adult('camille');
  const ines = adult('ines'); // same household, not responsible for the child
  const jules = adult('jules', 'h2'); // responsible, from another household
  const noe: Member = { id: 'noe', firstName: 'Noé', profile: 'childWithApp', householdId: 'h1', parentIds: ['camille', 'jules'], uid: null };
  const members = [camille, ines, jules, noe];
  const judo = { id: 'judo', data: { ...event('Judo de Noé', '2026-12-04', '17:00', 'Dojo du centre'), note: 'Ceinture jaune', targetType: 'child', targetMemberId: 'noe' } };
  const mine = { id: 'mine', data: { ...event('Rendez-vous de Noé', '2026-12-05', '09:00', 'Cabinet'), targetType: 'self', targetMemberId: 'noe' } };

  test('a responsible adult gets the title and the time, never the place nor the note', () => {
    for (const parent of [camille, jules]) {
      const [kept] = feedEvents([judo], parent, members);
      assert.equal(kept.data.title, 'Judo de Noé');
      assert.equal(kept.data.time, '17:00');
      assert.equal(kept.data.place, null);
      assert.equal(kept.data.note, null);
    }
    const feed = icsFeed(feedEvents([judo, mine], camille, members), new Date('2026-11-21T10:00:00Z'));
    assert.ok(feed.includes('SUMMARY:Judo de Noé'));
    assert.equal(feed.includes('LOCATION'), false);
    assert.equal(feed.includes('DESCRIPTION'), false);
  });

  test('another adult of the household, not responsible, gets nothing of the child', () => {
    assert.deepEqual(feedEvents([judo, mine], ines, members), []);
  });

  test('the events of the family keep their place and their note', () => {
    const galette = { id: 'g', data: { ...event('Galette', '2026-12-24', '19:00', 'Chez Camille'), note: 'Apporter du cidre' } };
    assert.deepEqual(feedEvents([galette], ines, members), [galette]);
  });
});

describe('the calendar feed against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'calendar-feed-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const now = new Date('2026-11-21T10:00:00Z');
  const token = TOKEN.slice(0, 43);

  beforeEach(async () => {
    for (const c of ['members', 'events', 'calendarFeeds', 'config']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    batch.set(db.doc('members/camille'), { firstName: 'Camille', profile: 'adult', parentIds: [] });
    batch.set(db.doc('members/noe'), { firstName: 'Noé', profile: 'childWithApp', parentIds: ['camille'] });
    batch.set(db.doc('members/admin'), { firstName: 'Sam', profile: 'adult', parentIds: [] });
    batch.set(db.doc('config/family'), { ownerMemberId: 'admin' });
    batch.set(db.doc('config/app'), { features: { calendarFeed: true } });
    batch.set(db.doc('calendarFeeds/camille'), { token });
    batch.set(db.doc('events/soon'), event('Galette', '2026-12-24', '19:00'));
    batch.set(db.doc('events/old'), event('Pique-nique', '2026-06-01', null));
    await batch.commit();
  });

  test('the link of a member gives the events of the month past and the year ahead', async () => {
    const feed = await feedFor(db, token, now);
    assert.ok(feed?.includes('SUMMARY:Galette'));
    assert.equal(feed?.includes('Pique-nique'), false);
  });

  test('only what concerns me, as on my Accueil', async () => {
    await db.doc('members/camille').set({ firstName: 'Camille', profile: 'adult', parentIds: [], householdId: 'h1' });
    await db.doc('events/theirs').set({ ...event('Courses du foyer 2', '2026-12-02', null), targetType: 'household', householdId: 'h2' });
    await db.doc('events/ours').set({ ...event('Ménage du foyer 1', '2026-12-03', null), targetType: 'household', householdId: 'h1' });
    await db.doc('events/kid').set({ ...event('Judo de Noé', '2026-12-04', '17:00', 'Dojo du centre'), targetType: 'child', targetMemberId: 'noe' });
    const feed = await feedFor(db, token, now);
    assert.ok(feed?.includes('SUMMARY:Galette'));
    assert.ok(feed?.includes('Ménage du foyer 1'));
    assert.ok(feed?.includes('Judo de Noé'));
    assert.equal(feed?.includes('Courses du foyer 2'), false);
    // The child's event has no place in the feed, even from Firestore.
    assert.equal(feed?.includes('LOCATION'), false);
  });

  test('a wrong, replaced or child link leads nowhere', async () => {
    assert.equal(await feedFor(db, `${token.slice(0, 42)}x`, now), null);
    await db.doc('calendarFeeds/camille').set({ token: `${token.slice(0, 42)}y` });
    assert.equal(await feedFor(db, token, now), null);
    await db.doc('calendarFeeds/noe').set({ token });
    assert.equal(await feedFor(db, token, now), null);
  });

  test('switched off, only the admin principal keeps a feed; a member gone loses theirs', async () => {
    await db.doc('config/app').set({ features: { calendarFeed: false } });
    assert.equal(await feedFor(db, token, now), null);
    await db.doc('calendarFeeds/admin').set({ token: `${token.slice(0, 42)}z` });
    assert.ok(await feedFor(db, `${token.slice(0, 42)}z`, now));
    await db.doc('config/app').set({ features: { calendarFeed: true } });
    await db.doc('members/camille').delete();
    assert.equal(await feedFor(db, token, now), null);
  });
});
