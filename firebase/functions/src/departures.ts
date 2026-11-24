// « Rappel du départ » (récit 112, règle 46): for a flight, a train or a bus, the participants with
// the app are reminded the day before from 18 h in Paris, and on the morning of the day from 7 h.
// The morning one is skipped when the departure is before 9 h (the evening one was enough, nobody is
// woken up) or already past. Sent by arrivalReminder, every 30 minutes: no new task. remindersSent
// keeps what went out, once per step and per kind. deliver() applies the settings of each one, the
// quiet hours and the cap of notices per hour.
import type { Firestore } from 'firebase-admin/firestore';
import { Timestamp } from 'firebase-admin/firestore';
import { addDays, formatTime, parisDay, type Notice } from './logic';
import { deliver, type AnySender } from './prefs';
import { loadMembers } from './store';
import { withApp, type TripData } from './trips';

/** The steps that leave: a flight, a train or a bus. */
export const DEPARTURE_TYPES = ['flight', 'train', 'bus'] as const;
/** From this hour in Paris, the day before. */
export const EVE_HOUR = 18;
/** From this hour in Paris, on the day. */
export const MORNING_HOUR = 7;
/** A departure before this time has only the reminder of the day before. */
export const MORNING_EARLIEST = '09:00';

export type DepartureKind = 'eve' | 'morning';

export interface DepartureStep {
  type: string;
  place: string;
  departDate: string;
  departTime: string | null;
}

const WORDS: Record<string, string> = { flight: 'Vol', train: 'Train', bus: 'Bus' };

/** « Corse : départ demain », « Vol pour Ajaccio à 8 h 15. » */
export function departureNotice(tripId: string, trip: Pick<TripData, 'title'>, step: DepartureStep, kind: DepartureKind): Notice {
  const when = kind === 'eve' ? 'demain' : "aujourd'hui";
  const at = step.departTime ? ` à ${formatTime(step.departTime)}` : '';
  return {
    channel: 'trips',
    title: `${trip.title} : départ ${when}`,
    body: `${WORDS[step.type] ?? 'Départ'} pour ${step.place}${at}.`,
    tripId,
  };
}

/**
 * Whether [step] has a reminder of [kind] due at [hhmm] (Paris time, « 07:30 »): a flight, a train
 * or a bus, and for the morning one, a departure from 9 h that has not left yet.
 */
export function departureDue(step: DepartureStep, kind: DepartureKind, hhmm: string): boolean {
  if (!(DEPARTURE_TYPES as readonly string[]).includes(step.type)) return false;
  if (kind === 'eve') return true;
  if (step.departTime == null) return true;
  return step.departTime >= MORNING_EARLIEST && step.departTime > hhmm;
}

/** The kind of reminder to look for at [now], with the day of the departures: null outside the hours. */
export function departureWindow(now: Date): { kind: DepartureKind; day: string; hhmm: string }[] {
  const hhmm = new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Paris', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(now);
  const hour = Number(hhmm.slice(0, 2));
  const today = parisDay(now);
  const windows: { kind: DepartureKind; day: string; hhmm: string }[] = [];
  if (hour >= MORNING_HOUR) windows.push({ kind: 'morning', day: today, hhmm });
  if (hour >= EVE_HOUR) windows.push({ kind: 'eve', day: addDays(today, 1), hhmm });
  return windows;
}

/** The reminders of departure due at [now] that no earlier run sent. */
export async function sendDepartureReminders(db: Firestore, send: AnySender<Notice>, now: Date): Promise<number> {
  let sent = 0;
  let members: Awaited<ReturnType<typeof loadMembers>> | null = null;
  for (const { kind, day, hhmm } of departureWindow(now)) {
    const steps = await db.collectionGroup('steps').where('departDate', '==', day).get();
    for (const stepDoc of steps.docs) {
      const tripRef = stepDoc.ref.parent.parent;
      // Only the steps of a trip: another collection called « steps » would never be touched.
      if (!tripRef || tripRef.parent.id !== 'trips') continue;
      const step = stepDoc.data() as DepartureStep;
      if (!departureDue(step, kind, hhmm)) continue;
      const tripDoc = await tripRef.get();
      if (!tripDoc.exists) continue;
      const trip = tripDoc.data() as TripData;
      members ??= await loadMembers(db);
      const recipients = withApp(members, trip.participantIds ?? [], null);
      if (recipients.length === 0) continue;
      try {
        await db.collection('remindersSent').doc(`depart_${kind}_${tripRef.id}_${stepDoc.id}`).create({
          expireAt: Timestamp.fromMillis(now.getTime() + 3 * 24 * 60 * 60 * 1000),
        });
      } catch {
        continue; // Already sent by an earlier run.
      }
      sent += await deliver(db, send, recipients, departureNotice(tripRef.id, trip, step, kind), now);
    }
  }
  return sent;
}
