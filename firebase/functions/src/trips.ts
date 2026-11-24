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
  return { channel: 'trips', title: `Arrivée à ${step.place}`, body, tripId };
}

export function reminderNotice(tripId: string, trip: TripData, step: StepData): Notice {
  return {
    channel: 'trips',
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

/** « Prévenir ma famille » (récit 105, règle 43): trips/{tripId}/alerts/{memberId}. */
export interface AlertData {
  authorId: string;
  authorName: string;
  createdAt: Timestamp;
}

/** The time between two alerts of the same participant, as in the rules. */
export const ALERT_GAP_MS = 10 * 60 * 1000;

/**
 * « Lou demande à être appelé » would guess a gender: the title names the trip, the body asks
 * for a call. No position: the family calls, the app never says where someone is.
 */
export function alertNotice(tripId: string, trip: TripData, authorName: string): Notice {
  return {
    channel: 'trips',
    title: `${trip.title} : ${authorName} prévient la famille`,
    body: `${authorName} a touché « Prévenir ma famille ». Appelle dès que possible.`,
    tripId,
    direct: true,
  };
}

/** Whether this write is a new alert: a first one, or one at least [ALERT_GAP_MS] after the last. */
export function isNewAlert(before: AlertData | undefined, after: AlertData | undefined): boolean {
  if (!after?.createdAt) return false;
  if (!before?.createdAt) return true;
  return after.createdAt.toMillis() - before.createdAt.toMillis() >= ALERT_GAP_MS;
}

/**
 * Everyone with the app who follows the trip is told at once, even at night (it aims at them
 * directly), except its author. Only a participant of the trip rings the family: the rules say
 * so, and the server checks again.
 */
export async function notifyAlert(db: Firestore, send: Sender, tripId: string, memberId: string, alert: AlertData): Promise<number> {
  if (alert.authorId !== memberId) return 0;
  const tripDoc = await db.collection('trips').doc(tripId).get();
  if (!tripDoc.exists) return 0;
  const trip = tripDoc.data() as TripData;
  if (!trip.participantIds.includes(memberId)) return 0;
  const members = await loadMembers(db);
  const author = members.find((m) => m.id === memberId);
  const recipients = withApp(members, trip.readerIds, memberId);
  if (recipients.length === 0) return 0;
  return notify(db, send, recipients, alertNotice(tripId, trip, author?.firstName ?? alert.authorName));
}

/** A deleted trip takes its steps with it, so nothing stays readable or reminded. */
export async function deleteTripContents(db: Firestore, tripId: string): Promise<void> {
  await db.recursiveDelete(db.collection('trips').doc(tripId));
  // Its photos waiting to be told (récit 104): nothing goes for a trip that is gone.
  await db.collection(PHOTO_BURSTS).doc(tripId).delete();
}

/** The word of a deleted step (récit 88) goes with its hearts and its comments. */
export async function deleteStepWord(db: Firestore, tripId: string, stepId: string): Promise<void> {
  await db.recursiveDelete(db.collection('trips').doc(tripId).collection('words').doc(stepId));
}

/** The voice notes of a deleted step (récit 104, règle 35) go with it; their files follow (onTripVoiceNoteDeleted). */
export async function deleteStepVoiceNotes(db: Firestore, tripId: string, stepId: string): Promise<number> {
  const notes = await db.collection('trips').doc(tripId).collection('voiceNotes').where('stepId', '==', stepId).get();
  if (notes.empty) return 0;
  const batch = db.batch();
  notes.docs.forEach((d) => batch.delete(d.ref));
  await batch.commit();
  return notes.size;
}

/** The file of a removed voice note, at its own place only, named after the record. */
export function voiceNoteFile(tripId: string, noteId: string, data: { authorId?: unknown } | undefined): string | null {
  if (!data || typeof data.authorId !== 'string' || !/^[A-Za-z0-9_-]{1,64}$/.test(data.authorId)) return null;
  if (!/^[A-Za-z0-9]{1,40}$/.test(noteId)) return null;
  return `trips/${tripId}/voice/${data.authorId}/${noteId}.m4a`;
}

/** A document of a trip (récit 105, règle 41), as the rules write it. */
export interface TripDocumentData {
  ownerId?: unknown;
  file?: unknown;
}

/**
 * The file of a removed document of a trip, in Storage: only the one at its own place, named after
 * the document. The admin principal may remove the record but not the file (the two reads of the
 * Storage rules), and a removal cut halfway would leave it: the server takes it away.
 */
export function tripDocumentFile(tripId: string, docId: string, data: TripDocumentData | undefined): string | null {
  if (!data || typeof data.ownerId !== 'string' || typeof data.file !== 'string') return null;
  if (!/^[A-Za-z0-9]{1,40}$/.test(data.ownerId)) return null;
  if (!['pdf', 'jpg', 'png'].some((ext) => data.file === `${docId}.${ext}`)) return null;
  return `trips/${tripId}/documents/${data.ownerId}/${data.file}`;
}

// --- Photos groupées (récit 104, règle 34, DESIGN-76-notifs A) -----------------------------------
//
// No notice per photo. Each new photo of a trip adds to tripPhotoBursts/{tripId} (counts by author,
// firstAt, dueAt) and pushes dueAt to 10 min after it, never past 45 min after the first photo of
// the burst, so a long send still goes. The digest of the good news (every 15 min, no new
// schedule) then tells the followers of the trip once per burst, through deliver() and its cap.
// Only the functions read and write this collection (firestore.rules).

export const PHOTO_BURSTS = 'tripPhotoBursts';
export const PHOTO_CALM_MS = 10 * 60 * 1000;
export const PHOTO_BURST_MAX_MS = 45 * 60 * 1000;

type Counts = Record<string, number>;

function countsOf(value: unknown): Counts {
  const result: Counts = {};
  if (value && typeof value === 'object') {
    for (const [id, n] of Object.entries(value as Record<string, unknown>)) {
      if (typeof n === 'number' && n > 0) result[id] = n;
    }
  }
  return result;
}

const total = (counts: Counts) => Object.values(counts).reduce((a, b) => a + b, 0);

/** When a burst goes: 10 min of calm after [now], at most 45 min after its first photo. */
export function photoBurstDue(firstAt: Date, now: Date): Date {
  return new Date(Math.min(now.getTime() + PHOTO_CALM_MS, firstAt.getTime() + PHOTO_BURST_MAX_MS));
}

/** A photo was added to the trip: it waits in the burst, nothing is sent now. */
export async function addTripPhotoToBurst(db: Firestore, tripId: string, authorId: unknown, now: Date): Promise<void> {
  if (typeof authorId !== 'string' || !/^[A-Za-z0-9_-]{1,64}$/.test(authorId)) return;
  const ref = db.collection(PHOTO_BURSTS).doc(tripId);
  await db.runTransaction(async (tx) => {
    const doc = await tx.get(ref);
    const counts = doc.exists ? countsOf(doc.get('counts')) : {};
    const firstAt = doc.exists && doc.get('firstAt') instanceof Timestamp ? (doc.get('firstAt') as Timestamp).toDate() : now;
    counts[authorId] = (counts[authorId] ?? 0) + 1;
    tx.set(ref, { counts, firstAt: Timestamp.fromDate(firstAt), dueAt: Timestamp.fromDate(photoBurstDue(firstAt, now)) });
  });
}

/** « Corse : 5 nouvelles photos », « Par Lou et Sam. » */
export function photoBurstNotice(tripId: string, trip: TripData, count: number, authorNames: string[]): Notice {
  const title = count === 1 ? `${trip.title} : une nouvelle photo` : `${trip.title} : ${count} nouvelles photos`;
  const body = authorNames.length === 0 ? 'Touche pour les voir.' : `Par ${joinNames(authorNames)}.`;
  return { channel: 'trips', title, body, tripId };
}

/**
 * Every 15 min (with the digest of the good news): the bursts whose time has come. The followers
 * of the trip with the app are told once (readerIds only, never every member); the author is left
 * out when they are the only one. A photo that came while sending is never lost: the burst goes
 * only if it did not change, else what came stays for the next time.
 */
export async function sendTripPhotoDigests(db: Firestore, send: Sender, now: Date): Promise<number> {
  const due = await db.collection(PHOTO_BURSTS).where('dueAt', '<=', Timestamp.fromDate(now)).get();
  if (due.empty) return 0;
  const members = await loadMembers(db);
  let sent = 0;
  for (const burst of due.docs) {
    const told = countsOf(burst.get('counts'));
    const tripDoc = await db.collection('trips').doc(burst.id).get();
    if (tripDoc.exists && total(told) > 0) {
      const trip = tripDoc.data() as TripData;
      const authors = Object.keys(told).sort();
      const recipients = withApp(members, trip.readerIds ?? [], authors.length === 1 ? authors[0] : null);
      const byId = new Map(members.map((m) => [m.id, m.firstName]));
      const names = authors.map((id) => byId.get(id)).filter((n): n is string => !!n);
      if (recipients.length > 0) sent += await notify(db, send, recipients, photoBurstNotice(burst.id, trip, total(told), names), now);
    }
    await db.runTransaction(async (tx) => {
      const current = await tx.get(burst.ref);
      if (!current.exists) return;
      const left = countsOf(current.get('counts'));
      for (const [id, n] of Object.entries(told)) {
        left[id] = (left[id] ?? 0) - n;
        if (left[id] <= 0) delete left[id];
      }
      if (!tripDoc.exists || total(left) === 0) {
        tx.delete(burst.ref);
      } else {
        // What came while sending: a new burst, from now.
        tx.set(burst.ref, { counts: left, firstAt: Timestamp.fromDate(now), dueAt: Timestamp.fromDate(photoBurstDue(now, now)) });
      }
    });
  }
  return sent;
}
