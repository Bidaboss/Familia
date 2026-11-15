// Trips (V3, récit 14): « bien arrivé » tells the followers of the trip, and a participant who
// forgot it is reminded 3 hours after the planned arrival. The family is never alerted by the
// reminder, and no position is ever collected: the only signal is the button.

import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import { Member, Notice } from './logic';
import { loadMembers, memberForUid, notify, Sender } from './store';

export interface TripData {
  title: string;
  participantIds: string[];
  readerIds: string[];
}

export interface StepData {
  place: string;
  remindAt: Timestamp | null;
  arrivedAt: Timestamp | null;
  arrivedBy: string | null;
}

/** True when this change is the moment someone said « bien arrivé ». */
export function isNewArrival(before: StepData, after: StepData): boolean {
  return before.arrivedAt == null && after.arrivedAt != null;
}

/** Members with the app among [ids], except [exceptId]. Members who left the family are skipped. */
export function withApp(members: Member[], ids: string[], exceptId: string | null): string[] {
  const wanted = new Set(ids);
  return members
    .filter((m) => wanted.has(m.id) && m.profile !== 'childWithoutApp' && m.id !== exceptId)
    .map((m) => m.id)
    .sort();
}

/** "Lou", "Lou et Sam", "Lou, Sam et Noé", in alphabetical order. */
export function joinNames(names: string[]): string {
  const sorted = [...names].sort((a, b) => a.localeCompare(b, 'fr'));
  return sorted.length <= 1 ? sorted.join('') : `${sorted.slice(0, -1).join(', ')} et ${sorted[sorted.length - 1]}`;
}

/**
 * « Arrivée à Ajaccio » / « Corse : arrivée de Lou et Sam, signalée par Lou. » One tap counts for
 * every participant of the trip. The wording avoids « arrivé » or « arrivée » after a first name,
 * since the app does not know anyone's gender.
 */
export function arrivalNotice(tripId: string, trip: TripData, step: StepData, actorName: string | null, members: Member[]): Notice {
  const byId = new Map(members.map((m) => [m.id, m.firstName]));
  const names = trip.participantIds.map((id) => byId.get(id)).filter((n): n is string => !!n);
  const who = actorName ?? 'Un participant';
  const body = names.length <= 1
    ? `${trip.title} : ${who} a signalé son arrivée.`
    : `${trip.title} : arrivée de ${joinNames(names)}, signalée par ${who}.`;
  return { channel: 'events', title: `Arrivée à ${step.place}`, body, tripId };
}

export function reminderNotice(tripId: string, trip: TripData, step: StepData): Notice {
  return {
    channel: 'reminders',
    title: 'Bien arrivé ?',
    body: `${trip.title}, ${step.place} : touche « Bien arrivé » pour rassurer la famille.`,
    tripId,
  };
}

/** Someone said « bien arrivé »: every follower with the app is told, except them. */
export async function notifyArrival(
  db: Firestore, send: Sender, tripId: string, step: StepData, actorUid: string | undefined,
): Promise<number> {
  const tripDoc = await db.collection('trips').doc(tripId).get();
  if (!tripDoc.exists) return 0;
  const trip = tripDoc.data() as TripData;
  const members = await loadMembers(db);
  // The rules make arrivedBy the caller; the UID is a fallback for a change made otherwise.
  const actor = members.find((m) => m.id === step.arrivedBy) ?? memberForUid(members, actorUid);
  const recipients = withApp(members, trip.readerIds, actor?.id ?? null);
  if (recipients.length === 0) return 0;
  return notify(db, send, recipients, arrivalNotice(tripId, trip, step, actor?.firstName ?? null, members));
}

/**
 * Every half hour: the steps whose reminder time has passed with no « bien arrivé ». The
 * participants with the app get one reminder, then remindAt is cleared so it never comes twice.
 */
export async function sendArrivalReminders(db: Firestore, send: Sender, now: Date): Promise<number> {
  const due = await db.collectionGroup('steps').where('remindAt', '<=', Timestamp.fromDate(now)).get();
  if (due.empty) return 0;
  const members = await loadMembers(db);
  let sent = 0;
  for (const stepDoc of due.docs) {
    const tripRef = stepDoc.ref.parent.parent;
    // Only the steps of a trip: another collection called « steps » would never be touched.
    if (!tripRef || tripRef.parent.id !== 'trips') continue;
    const step = stepDoc.data() as StepData;
    await stepDoc.ref.update({ remindAt: null });
    if (step.arrivedAt != null) continue;
    const tripDoc = await tripRef.get();
    if (!tripDoc.exists) continue;
    const trip = tripDoc.data() as TripData;
    const recipients = withApp(members, trip.participantIds, null);
    if (recipients.length > 0) sent += await notify(db, send, recipients, reminderNotice(tripRef.id, trip, step));
  }
  return sent;
}

/** A deleted trip takes its steps with it, so nothing stays readable or reminded. */
export async function deleteTripContents(db: Firestore, tripId: string): Promise<void> {
  await db.recursiveDelete(db.collection('trips').doc(tripId));
}
