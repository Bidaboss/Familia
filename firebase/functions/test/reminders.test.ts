// « Mes rappels sur un événement » (PRD rév. 248, récit 56).

import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import {
  deleteEventReminders, myReminderNotice, offsetsFor, offsetsFrom, parisInstant, reminderAt, sendMyReminders, type ReminderNotice,
} from '../src/reminders';
import type { EventData } from '../src/logic';

const iso = (d: Date | null) => d?.toISOString() ?? null;

describe('when a reminder rings, without the emulator', () => {
  test('Paris time, in winter and in summer', () => {
    assert.equal(iso(parisInstant('2026-11-21', '12:00')), '2026-11-21T11:00:00.000Z');
    assert.equal(iso(parisInstant('2026-07-14', '12:00')), '2026-07-14T10:00:00.000Z');
  });

  test('an event at 15 h', () => {
    assert.equal(iso(reminderAt('2026-11-21', '15:00', 'h1')), '2026-11-21T13:00:00.000Z');
    assert.equal(iso(reminderAt('2026-11-21', '15:00', 'h3')), '2026-11-21T11:00:00.000Z');
    assert.equal(iso(reminderAt('2026-11-21', '15:00', 'morning')), '2026-11-21T07:00:00.000Z');
    assert.equal(iso(reminderAt('2026-11-21', '15:00', 'd2')), '2026-11-19T14:00:00.000Z');
    assert.equal(iso(reminderAt('2026-11-21', '15:00', 'w1')), '2026-11-14T14:00:00.000Z');
  });

  test('a breakfast at 7 h 30 has no reminder at 8 h, and an all-day event none an hour before', () => {
    assert.equal(reminderAt('2026-11-21', '07:30', 'morning'), null);
    assert.equal(reminderAt('2026-11-21', null, 'h1'), null);
    assert.equal(reminderAt('2026-11-21', null, 'h3'), null);
    assert.equal(iso(reminderAt('2026-11-21', null, 'morning')), '2026-11-21T07:00:00.000Z');
    assert.equal(iso(reminderAt('2026-11-21', null, 'd2')), '2026-11-19T07:00:00.000Z');
  });

  test('an event of its own wins over its series, and the defaults only count for « Je viens »', () => {
    const choice = { byEvent: new Map([['e1', ['h1' as const]], ['e2', []]]), bySeries: new Map([['s1', ['d2' as const]]]), defaults: ['morning' as const] };
    assert.deepEqual(offsetsFor(choice, 'e1', 's1', true), ['h1']);
    assert.deepEqual(offsetsFor(choice, 'e2', null, true), []); // « Pas de rappel » on this one.
    assert.deepEqual(offsetsFor(choice, 'e3', 's1', true), ['d2']);
    assert.deepEqual(offsetsFor(choice, 'e4', null, true), ['morning']);
    assert.deepEqual(offsetsFor(choice, 'e4', null, false), []);
  });

  test('only the known offsets, once each, and a notice that says when', () => {
    assert.deepEqual(offsetsFrom(['w1', 'h1', 'h1', 'soon', 3]), ['h1', 'w1']);
    assert.deepEqual(offsetsFrom(null), []);
    const event: EventData = {
      title: 'Piscine', date: '2026-11-21', time: '15:00', place: 'Annecy', note: null, targetType: 'family', targetMemberId: null, householdId: null,
    };
    const notice = myReminderNotice('e1', event, 'h1');
    assert.equal(notice.title, 'Dans 1 h : Piscine');
    assert.equal(notice.body, '15 h 00 · Annecy');
    assert.equal(notice.direct, true);
    assert.equal(notice.channel, 'reminders');
  });
});

describe('sending, against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'reminders');
  const db = getFirestore(app);
  after(async () => { await deleteApp(app); });

  const piscine = (overrides: Partial<EventData & { seriesId: string }> = {}) => ({
    title: 'Piscine', date: '2026-11-21', time: '15:00', place: null, note: null, targetType: 'family', targetMemberId: null, householdId: null, ...overrides,
  });
  // 14 h in Paris, the hour before the swim.
  const at14 = new Date('2026-11-21T13:00:00Z');

  beforeEach(async () => {
    for (const c of ['members', 'events', 'remindersSent', 'eventSeries', 'presentationQueue']) await db.recursiveDelete(db.collection(c));
    await db.doc('config/app').set({ features: { myReminders: true } });
    await db.doc('config/presentation').delete();
    for (const id of ['camille', 'ines', 'sam']) {
      await db.doc(`members/${id}`).set({ firstName: id });
      await db.doc(`members/${id}/devices/phone-${id}`).set({});
    }
  });

  const collect = () => {
    const sent: [string, ReminderNotice][] = [];
    const send = async (token: string, notice: ReminderNotice) => { sent.push([token, notice]); return 'ok' as const; };
    return { sent, send };
  };

  test('what each one asked for, once, and nothing for the others', async () => {
    await db.doc('events/e1').set(piscine());
    await db.doc('members/camille/reminders/e1').set({ eventId: 'e1', offsets: ['h1', 'h3'] });
    const { sent, send } = collect();
    assert.equal(await sendMyReminders(db, send, at14), 2); // 1 h and 3 h before: both are due within 2 h.
    assert.deepEqual(sent.map(([t, n]) => [t, n.title]), [['phone-camille', 'Dans 1 h : Piscine'], ['phone-camille', 'Dans 3 h : Piscine']]);
    assert.equal(await sendMyReminders(db, send, new Date('2026-11-21T13:30:00Z')), 0);
  });

  test('a changed time rings again at the new time, a deleted event never', async () => {
    await db.doc('events/e1').set(piscine());
    await db.doc('members/camille/reminders/e1').set({ eventId: 'e1', offsets: ['h1'] });
    const { sent, send } = collect();
    await sendMyReminders(db, send, at14);
    await db.doc('events/e1').update({ time: '17:00' });
    assert.equal(await sendMyReminders(db, send, at14), 0); // 16 h now.
    assert.equal(await sendMyReminders(db, send, new Date('2026-11-21T15:00:00Z')), 1);
    await deleteEventReminders(db, 'e1');
    assert.equal((await db.doc('members/camille/reminders/e1').get()).exists, false);
    assert.equal(sent.length, 2);
  });

  test('a series counts for each occurrence, an occurrence of its own wins, and the defaults need « Je viens »', async () => {
    await db.doc('events/s1_20261121').set(piscine({ seriesId: 's1' }));
    await db.doc('events/s1_20261121b').set(piscine({ seriesId: 's1', title: 'Natation' }));
    await db.doc('members/camille/reminders/series-s1').set({ seriesId: 's1', offsets: ['h1'] });
    await db.doc('members/camille/reminders/s1_20261121b').set({ eventId: 's1_20261121b', offsets: [] });
    await db.doc('members/ines/settings/notifications').set({ defaultReminders: ['h1'] });
    await db.doc('members/sam/settings/notifications').set({ defaultReminders: ['h1'] });
    await db.doc('events/s1_20261121/rsvps/ines').set({ status: 'yes' });
    await db.doc('events/s1_20261121/rsvps/sam').set({ status: 'maybe' });
    const { sent, send } = collect();
    await sendMyReminders(db, send, at14);
    assert.deepEqual(sent.map(([t, n]) => [t, n.title]).sort(), [['phone-camille', 'Dans 1 h : Piscine'], ['phone-ines', 'Dans 1 h : Piscine']]);
  });

  test('switch off, only the admin principal, who tries it, is reminded', async () => {
    await db.doc('config/app').set({ features: { myReminders: false } });
    await db.doc('config/family').set({ ownerMemberId: 'ines' }, { merge: true });
    await db.doc('events/e1').set(piscine());
    await db.doc('members/camille/reminders/e1').set({ eventId: 'e1', offsets: ['h1'] });
    await db.doc('members/ines/reminders/e1').set({ eventId: 'e1', offsets: ['h1'] });
    const { sent, send } = collect();
    await sendMyReminders(db, send, at14);
    assert.deepEqual(sent.map(([t]) => t), ['phone-ines']);
    await db.doc('config/app').set({ features: {} });
  });
});
