// « Voir le planning dans mon agenda » (PRD, récit 49), behind the switch calendarFeed. Each adult
// member writes a secret token in calendarFeeds/{memberId} from the app (rules: only their own,
// never a child); this function serves the events of the family as an .ics feed to whoever has
// the link: Google Agenda reads it every few hours, read only. A new token cuts the old link, and
// the document goes with the member who leaves. Only the events: never a gift occasion nor a
// reservation, so no surprise can leak. Same scope as the planning of the app: every member reads
// every event (rules), until the single audience function says otherwise.

import type { Firestore } from 'firebase-admin/firestore';
import type { EventData } from './logic';
import { addDays, parisDay } from './logic';
import { featureOn } from './store';

export const CALENDAR_FEED = 'calendarFeed';

/** A token as the app writes it: 32 random bytes in base64url. */
export function isFeedToken(token: unknown): token is string {
  return typeof token === 'string' && /^[A-Za-z0-9_-]{43}$/.test(token);
}

/** The text of a property, escaped as RFC 5545 asks. */
export function icsText(text: string): string {
  return text.replace(/\\/g, '\\\\').replace(/;/g, '\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n');
}

/** Lines longer than 75 octets go on, after a space, on the next line. */
export function foldLine(line: string): string {
  const out: string[] = [];
  let current = '';
  let size = 0;
  for (const ch of line) {
    const bytes = Buffer.byteLength(ch);
    if (size + bytes > (out.length === 0 ? 75 : 74)) {
      out.push(current);
      current = '';
      size = 0;
    }
    current += ch;
    size += bytes;
  }
  out.push(current);
  return out.join('\r\n ');
}

/** The UTC instant of [day] at [time] in Paris, as 20261121T110000Z. */
export function parisToUtc(day: string, time: string): string {
  const [y, m, d] = day.split('-').map(Number);
  const [hh, mm] = time.split(':').map(Number);
  const guess = Date.UTC(y, m - 1, d, hh, mm);
  // The offset of Paris at that moment (1 h in winter, 2 h in summer).
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Paris', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(guess));
  const get = (t: string) => Number(parts.find((p) => p.type === t)?.value);
  const shown = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'));
  const utc = new Date(guess - (shown - guess));
  return utc.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
}

const compactDay = (day: string) => day.replace(/-/g, '');

/** The feed of [events]: one hour for a timed event, the whole day otherwise. */
export function icsFeed(events: { id: string; data: EventData }[], now: Date): string {
  const stamp = now.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Familia//Planning//FR',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    'X-WR-CALNAME:Familia',
    'X-WR-TIMEZONE:Europe/Paris',
    'REFRESH-INTERVAL;VALUE=DURATION:PT6H',
  ];
  for (const { id, data } of events) {
    lines.push('BEGIN:VEVENT', `UID:${id}@familia`, `DTSTAMP:${stamp}`);
    if (data.time) {
      const start = parisToUtc(data.date, data.time);
      const end = new Date(Date.parse(`${start.slice(0, 4)}-${start.slice(4, 6)}-${start.slice(6, 8)}T${start.slice(9, 11)}:${start.slice(11, 13)}:00Z`) + 3_600_000);
      lines.push(`DTSTART:${start}`, `DTEND:${end.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')}`);
    } else {
      lines.push(`DTSTART;VALUE=DATE:${compactDay(data.date)}`, `DTEND;VALUE=DATE:${compactDay(addDays(data.date, 1))}`);
    }
    lines.push(`SUMMARY:${icsText(data.title)}`);
    if (data.place) lines.push(`LOCATION:${icsText(data.place)}`);
    if (data.note) lines.push(`DESCRIPTION:${icsText(data.note)}`);
    lines.push('END:VEVENT');
  }
  lines.push('END:VCALENDAR');
  return lines.map(foldLine).join('\r\n') + '\r\n';
}

/** How far the feed looks: a month back, a year ahead. */
const DAYS_BACK = 31;
const DAYS_AHEAD = 366;

/**
 * The feed of the member who owns [token], or null when the link leads nowhere: unknown or
 * replaced token, member gone, a child, or the switch off (the admin principal keeps it while in test).
 */
export async function feedFor(db: Firestore, token: unknown, now: Date): Promise<string | null> {
  if (!isFeedToken(token)) return null;
  const found = await db.collection('calendarFeeds').where('token', '==', token).limit(1).get();
  if (found.empty) return null;
  const memberId = found.docs[0].id;
  const member = await db.collection('members').doc(memberId).get();
  if (!member.exists || member.get('profile') !== 'adult') return null;
  if (!(await featureOn(db, CALENDAR_FEED))) {
    const owner = (await db.collection('config').doc('family').get()).get('ownerMemberId');
    if (owner !== memberId) return null;
  }
  const today = parisDay(now);
  const events = await db.collection('events')
    .where('date', '>=', addDays(today, -DAYS_BACK))
    .where('date', '<=', addDays(today, DAYS_AHEAD))
    .get();
  return icsFeed(events.docs.map((doc) => ({ id: doc.id, data: doc.data() as EventData })), now);
}

/** A member who leaves takes their link with them. */
export async function deleteCalendarFeedOf(db: Firestore, memberId: string): Promise<void> {
  await db.collection('calendarFeeds').doc(memberId).delete();
}
