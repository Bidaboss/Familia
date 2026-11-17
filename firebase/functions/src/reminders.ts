// « Mes rappels sur un événement » (PRD rév. 248, récit 56), behind the switch myReminders.
//
// members/{memberId}/reminders/{id} holds what a member asked for, privately: id is the event's id
// ({ eventId, offsets }) or `series-{seriesId}` for every occurrence of a series ({ seriesId, offsets }).
// An event's own choice wins over its series' one; an empty `offsets` means « pas de rappel ».
// Without either, the member's « rappels par défaut » (settings/notifications.defaultReminders) apply
// to the events they said « Je viens » to. arrivalReminder sends them, every 30 minutes: no new task.
// remindersSent/{key} (closed to the app) keeps what went out, once per event, time and offset: a
// changed time sends again at the new time, a deleted event sends nothing.

import type { Firestore } from 'firebase-admin/firestore';
import { Timestamp } from 'firebase-admin/firestore';
import { addDays, formatTime, parisDay, type EventData } from './logic';
import { deliver, type AnySender } from './prefs';
import { featureAudience } from './store';

export const MY_REMINDERS = 'myReminders';

export type ReminderOffset = 'h1' | 'h3' | 'morning' | 'd2' | 'w1';
export const OFFSETS: readonly ReminderOffset[] = ['h1', 'h3', 'morning', 'd2', 'w1'];

/** A reminder whose time passed more than this long ago is no longer sent (a missed run). */
export const LATE_MS = 2 * 60 * 60 * 1000;

/** The instant of [day] at [time] in Paris (1 h or 2 h before UTC). */
export function parisInstant(day: string, time: string): Date {
  const [y, m, d] = day.split('-').map(Number);
  const [hh, mm] = time.split(':').map(Number);
  const guess = Date.UTC(y, m - 1, d, hh, mm);
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Paris', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(guess));
  const get = (t: string) => Number(parts.find((p) => p.type === t)?.value);
  const shown = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'));
  return new Date(guess - (shown - guess));
}

/**
 * When [offset] rings for an event on [day] at [time] (null: all day). An all-day event has no
 * « 1 h avant » nor « 3 h avant »; its other reminders ring at 8 h. A reminder that would ring at
 * the start or after (« le matin à 8 h » for a breakfast at 7 h 30) does not ring.
 */
export function reminderAt(day: string, time: string | null, offset: ReminderOffset): Date | null {
  const start = parisInstant(day, time ?? '00:00');
  let at: Date;
  switch (offset) {
    case 'h1':
    case 'h3':
      if (time == null) return null;
      at = new Date(start.getTime() - (offset === 'h1' ? 1 : 3) * 60 * 60 * 1000);
      break;
    case 'morning':
      at = parisInstant(day, '08:00');
      break;
    case 'd2':
    case 'w1': {
      const before = offset === 'd2' ? 2 : 7;
      at = time == null ? parisInstant(addDays(day, -before), '08:00') : parisInstant(addDays(day, -before), time);
      break;
    }
  }
  if (time != null && at.getTime() >= start.getTime()) return null;
  return at;
}

const LEAD: Record<ReminderOffset, string> = {
  h1: 'Dans 1 h',
  h3: 'Dans 3 h',
  morning: 'Aujourd\'hui',
  d2: 'Dans 2 jours',
  w1: 'Dans une semaine',
};

export interface ReminderNotice {
  channel: 'reminders';
  title: string;
  body: string;
  eventId: string;
  /** Asked for by the member: never muted by their families. */
  direct: true;
}

export function myReminderNotice(eventId: string, event: EventData, offset: ReminderOffset): ReminderNotice {
  const details = [event.time ? formatTime(event.time) : 'Journée', event.place].filter(Boolean).join(' · ');
  return { channel: 'reminders', title: `${LEAD[offset]} : ${event.title}`, body: details, eventId, direct: true };
}

/** Keeps only the known offsets, once each, in their order. */
export function offsetsFrom(value: unknown): ReminderOffset[] {
  if (!Array.isArray(value)) return [];
  return OFFSETS.filter((o) => value.includes(o));
}

interface Choice {
  byEvent: Map<string, ReminderOffset[]>;
  bySeries: Map<string, ReminderOffset[]>;
  defaults: ReminderOffset[];
}

/**
 * What [memberId] is reminded of for an event: its own choice, else its series' one, else the
 * defaults when the member said « Je viens ».
 */
export function offsetsFor(choice: Choice, eventId: string, seriesId: string | null, coming: boolean): ReminderOffset[] {
  const own = choice.byEvent.get(eventId);
  if (own) return own;
  const series = seriesId ? choice.bySeries.get(seriesId) : undefined;
  if (series) return series;
  return coming ? choice.defaults : [];
}

/** Every reminder due at [now], sent once. Returns how many phones got one. */
export async function sendMyReminders(db: Firestore, send: AnySender<ReminderNotice>, now: Date): Promise<number> {
  const today = parisDay(now);
  const events = await db.collection('events').where('date', '>=', today).where('date', '<=', addDays(today, 8)).get();
  if (events.empty) return 0;

  const members = await db.collection('members').get();
  const choices = new Map<string, Choice>();
  for (const m of members.docs) {
    const defaults = offsetsFrom((await m.ref.collection('settings').doc('notifications').get()).get('defaultReminders'));
    choices.set(m.id, { byEvent: new Map(), bySeries: new Map(), defaults });
  }
  const asked = await db.collectionGroup('reminders').get();
  for (const doc of asked.docs) {
    const memberRef = doc.ref.parent.parent;
    // Only members/{id}/reminders: another collection called « reminders » is never read.
    if (!memberRef || memberRef.parent.id !== 'members') continue;
    const choice = choices.get(memberRef.id);
    if (!choice) continue;
    const offsets = offsetsFrom(doc.get('offsets'));
    const eventId = doc.get('eventId');
    const seriesId = doc.get('seriesId');
    if (typeof eventId === 'string' && doc.id === eventId) choice.byEvent.set(eventId, offsets);
    else if (typeof seriesId === 'string' && doc.id === `series-${seriesId}`) choice.bySeries.set(seriesId, offsets);
  }
  const anyDefaults = [...choices.values()].some((c) => c.defaults.length > 0);
  const only = await featureAudience(db, MY_REMINDERS);

  let sent = 0;
  for (const eventDoc of events.docs) {
    const event = eventDoc.data() as EventData & { seriesId?: string; cancelledBySeries?: boolean };
    if (event.cancelledBySeries) continue;
    const start = parisInstant(event.date, event.time ?? '00:00');
    const coming = new Set<string>();
    if (anyDefaults) {
      const rsvps = await eventDoc.ref.collection('rsvps').where('status', '==', 'yes').get();
      rsvps.docs.forEach((r) => coming.add(r.id));
    }
    for (const [memberId, choice] of choices) {
      if (only([memberId]).length === 0) continue;
      for (const offset of offsetsFor(choice, eventDoc.id, event.seriesId ?? null, coming.has(memberId))) {
        const at = reminderAt(event.date, event.time ?? null, offset);
        if (!at || at.getTime() > now.getTime() || now.getTime() - at.getTime() > LATE_MS) continue;
        if (event.time != null && start.getTime() <= now.getTime()) continue;
        // The time is in the key: a new time is a new reminder, an old one is never sent twice.
        const key = `${memberId}_${eventDoc.id}_${offset}_${event.date}T${event.time ?? 'jour'}`;
        try {
          await db.collection('remindersSent').doc(key.replace(/[/:]/g, '-')).create({
            expireAt: Timestamp.fromMillis(start.getTime() + 2 * 24 * 60 * 60 * 1000),
          });
        } catch {
          continue; // Already sent by an earlier run.
        }
        sent += await deliver(db, send, [memberId], myReminderNotice(eventDoc.id, event, offset), now);
      }
    }
  }
  return sent;
}

/** A deleted event takes its reminders with it, in every member's list. */
export async function deleteEventReminders(db: Firestore, eventId: string): Promise<void> {
  const asked = await db.collectionGroup('reminders').where('eventId', '==', eventId).get();
  await Promise.all(asked.docs.filter((d) => d.ref.parent.parent?.parent.id === 'members').map((d) => d.ref.delete()));
}
