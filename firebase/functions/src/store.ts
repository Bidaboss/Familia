// What the functions read and write in Firestore. Each function takes the database and the
// sender as parameters, so the tests run them against the emulator with a fake sender.

import { FieldValue, GrpcStatus } from 'firebase-admin/firestore';
import type { Firestore } from 'firebase-admin/firestore';
import {
  changeNotice, changeRecipients, ChangeKind, EventData, Member, Notice, parisDay, addDays, reminders, RsvpStatus,
} from './logic';
import { CARPOOL, loadRides, rideLines } from './carpool';
import { topUpSeries } from './series';
import { deliver, noticeTime } from './prefs';

/** Sends one notification to one phone. "unregistered" means the app was uninstalled. */
export type Sender = (token: string, notice: Notice) => Promise<'ok' | 'unregistered' | 'error'>;

export async function loadMembers(db: Firestore): Promise<Member[]> {
  const snapshot = await db.collection('members').get();
  return snapshot.docs.map((doc) => ({
    id: doc.id,
    firstName: String(doc.get('firstName') ?? ''),
    profile: doc.get('profile'),
    householdId: (doc.get('householdId') as string | null | undefined) ?? null,
    parentIds: (doc.get('parentIds') as string[] | undefined) ?? [],
    uid: (doc.get('uid') as string | null | undefined) ?? null,
  }));
}

/** The member behind a Firebase UID, or null (a change made from the console, for example). */
export function memberForUid(members: Member[], uid: string | undefined): Member | null {
  if (!uid) return null;
  return members.find((m) => m.uid === uid) ?? null;
}

/**
 * Sends [notice] to every phone of [memberIds]. Phones live in members/{id}/devices/{token};
 * the token of an uninstalled app is deleted so it is not tried again.
 */
export async function notify(db: Firestore, send: Sender, memberIds: string[], notice: Notice, now: Date = noticeTime()): Promise<number> {
  // Each member's settings (récit 6 quinquies) decide: now, at 8 h, or not at all.
  return deliver(db, send, memberIds, notice, now);
}

/** Whether a module is switched on for the family in config/app (Administration > Modules). */
export async function featureOn(db: Firestore, key: string): Promise<boolean> {
  const features = (await db.collection('config').doc('app').get()).get('features') as Record<string, boolean> | undefined;
  return features?.[key] === true;
}

/**
 * Who may hear of a module (config/app.features[key]): everyone once it is switched on for the
 * family; before that, the admin principal only, the one member the app already shows it to, so a
 * try never reaches the family (démo du 21).
 */
export async function featureAudience(db: Firestore, key: string): Promise<(ids: string[]) => string[]> {
  if (await featureOn(db, key)) return (ids) => ids;
  const owner = (await db.doc('config/family').get()).get('ownerMemberId');
  return (ids) => ids.filter((id) => typeof owner === 'string' && id === owner);
}

/** Whether « Ajouter à mon agenda » (récit 30) is switched on for the family. */
async function calendarOn(db: Firestore): Promise<boolean> {
  return featureOn(db, 'calendar');
}

/**
 * PRD, récit 6: an event was added, edited or deleted by [actor]. [answered] holds the members who
 * said « Je viens » or « Peut-être »: read before a deletion removes the answers.
 */
export async function notifyChange(
  db: Firestore, send: Sender, kind: ChangeKind, eventId: string, event: EventData, actorUid: string | undefined, now: Date,
  answered: Set<string> = new Set(), loaded?: Member[], repeat = '', only: (ids: string[]) => string[] = (ids) => ids,
): Promise<number> {
  const members = loaded ?? await loadMembers(db);
  const actor = memberForUid(members, actorUid);
  const recipients = only(changeRecipients(members, actor?.id ?? null, event, parisDay(now), answered));
  if (recipients.length === 0) return 0;
  const calendarHint = kind !== 'added' && await calendarOn(db);
  return notify(db, send, recipients, changeNotice(kind, eventId, event, actor?.firstName ?? null, calendarHint, repeat));
}

/**
 * Who said « Je viens » or « Peut-être » to [eventId]. A child without the app answers through
 * their adults, who are then told instead.
 */
export async function comingMembers(db: Firestore, eventId: string, members?: Member[], skip: readonly string[] = []): Promise<Set<string>> {
  const rsvps = await db.collection('events').doc(eventId).collection('rsvps').get();
  const all = members ?? await loadMembers(db);
  const byId = new Map(all.map((m) => [m.id, m]));
  const coming = new Set<string>();
  for (const r of rsvps.docs) {
    if (r.get('status') === 'no' || skip.includes(r.id)) continue;
    const member = byId.get(r.id);
    if (!member) continue;
    if (member.profile === 'childWithoutApp') member.parentIds.forEach((id) => coming.add(id));
    else coming.add(member.id);
  }
  return coming;
}

/** The answers and the « qui apporte quoi » list of a deleted event are useless and would stay readable: they go with it. */
export async function deleteEventAnswers(db: Firestore, eventId: string): Promise<void> {
  await db.recursiveDelete(db.collection('events').doc(eventId).collection('rsvps'));
  await db.recursiveDelete(db.collection('events').doc(eventId).collection('bring'));
  // « On y va ensemble » (récit 52): its cars and requests for a seat.
  await db.recursiveDelete(db.collection('events').doc(eventId).collection('rides'));
  await db.recursiveDelete(db.collection('events').doc(eventId).collection('rideNeeds'));
}

/** « Qui apporte quoi » (récit 4 bis): what a member who left had taken is free again. */
export async function releaseBringOf(db: Firestore, memberId: string): Promise<void> {
  const taken = await db.collectionGroup('bring').where('takerId', '==', memberId).get();
  const writer = db.bulkWriter();
  for (const item of taken.docs) {
    writer.update(item.ref, { takerId: null, takerName: null, updatedAt: FieldValue.serverTimestamp() });
  }
  await writer.close();
}

/**
 * A member was removed (a separation, for example): their answers, bags, alerts, documents and voice notes
 * of trips, phones, settings and days of use (PRD, récit 1 nonies) go too.
 * The family has a few hundred events at most, so going through them all stays cheap.
 * Their trip films stay, like their photos: they belong to the trip and its followers (relecture #559).
 * Returns the Storage folders of their trip files, for the caller to empty: a file sent without its
 * document or note (the app cut off in between) is in none of them, and a passport must not stay.
 */
export async function deleteMemberData(db: Firestore, memberId: string): Promise<string[]> {
  const events = await db.collection('events').select().get();
  const writer = db.bulkWriter();
  // A burst sent in between is gone (NOT_FOUND): nothing left to remove. Any other failure is
  // retried, then logged, as close() does not throw for a single write.
  writer.onWriteError((error) => {
    if (error.code === GrpcStatus.NOT_FOUND) return false;
    if (error.failedAttempts < 3) return true;
    console.error('deleteMemberData', error.documentRef.path, error.message);
    return false;
  });
  for (const event of events.docs) {
    writer.delete(event.ref.collection('rsvps').doc(memberId));
  }
  // « À emporter » (récit 62): their bags, which nobody else could read, go too.
  const trips = await db.collection('trips').select().get();
  const folders: string[] = [];
  for (const trip of trips.docs) {
    folders.push(`trips/${trip.id}/documents/${memberId}/`, `trips/${trip.id}/voice/${memberId}/`);
    writer.delete(trip.ref.collection('bags').doc(memberId));
    // « Prévenir ma famille » (récit 105): their last alert.
    writer.delete(trip.ref.collection('alerts').doc(memberId));
    // « Documents » (règle 41): their tickets and papers (a passport may be one); each file goes
    // with its document (onTripDocumentDeleted).
    const documents = await trip.ref.collection('documents').where('ownerId', '==', memberId).select().get();
    documents.docs.forEach((d) => writer.delete(d.ref));
    // « Ajout rapide » (récit 104, règle 35): their voice notes; each file goes with its note (onTripVoiceNoteDeleted).
    const notes = await trip.ref.collection('voiceNotes').where('authorId', '==', memberId).select().get();
    notes.docs.forEach((n) => writer.delete(n.ref));
  }
  // « Photos groupées » (récit 104, règle 34): their part of a burst not sent yet.
  const bursts = await db.collection('tripPhotoBursts').select('counts').get();
  for (const burst of bursts.docs) {
    const counts = burst.get('counts');
    if (counts && typeof counts === 'object' && memberId in counts) writer.update(burst.ref, { [`counts.${memberId}`]: FieldValue.delete() });
  }
  await writer.close();
  const member = db.collection('members').doc(memberId);
  await db.recursiveDelete(member.collection('devices'));
  await db.recursiveDelete(member.collection('settings'));
  // « Me le rappeler » (récit 56): private reminders go with the member.
  await db.recursiveDelete(member.collection('reminders'));
  // « Carnet de baroudeur » (récit 107): the places added by hand are private too.
  await db.recursiveDelete(member.collection('travelPlaces'));
  await db.recursiveDelete(db.collection('usage').doc(memberId).collection('days'));
  return folders;
}

/** PRD, récit 6 bis: at 18 h, the members coming tomorrow are reminded. */
export async function sendDayBeforeReminders(db: Firestore, send: Sender, now: Date): Promise<number> {
  // « Événements qui reviennent » (récit 54): the 12 months move on by a day, before tomorrow is read.
  // Never in the way of the reminders: a series that fails is only logged.
  try {
    await topUpSeries(db, parisDay(now));
  } catch (e) {
    console.error('topUpSeries', e);
  }
  const tomorrow = addDays(parisDay(now), 1);
  const events = await db.collection('events').where('date', '==', tomorrow).get();
  if (events.empty) return 0;
  const members = await loadMembers(db);
  // « Qui apporte quoi » (récit 4 bis): the reminder says what each one brings.
  const bringOn = await featureOn(db, 'bring');
  const carpoolOn = await featureOn(db, CARPOOL);
  let sent = 0;
  for (const event of events.docs) {
    const answers = new Map<string, RsvpStatus>();
    const rsvps = await event.ref.collection('rsvps').get();
    rsvps.docs.forEach((r) => answers.set(r.id, r.get('status')));
    const bringing = new Map<string, string[]>();
    if (bringOn) {
      const items = await event.ref.collection('bring').get();
      items.docs
        .filter((i) => typeof i.get('takerId') === 'string')
        .sort((a, b) => String(a.get('label')).localeCompare(String(b.get('label')), 'fr'))
        .forEach((i) => bringing.set(i.get('takerId'), [...(bringing.get(i.get('takerId')) ?? []), i.get('label')]));
    }
    // « On y va ensemble » (récit 52): who each one takes, or goes with.
    const rides = carpoolOn ? rideLines(await loadRides(db, event.id), members) : new Map<string, string[]>();
    const all = reminders(members, event.id, event.data() as EventData, answers, new Set(), bringing, rides);
    const off = await remindersOff(db, all.map((r) => r.recipientId));
    for (const reminder of all) {
      if (off.has(reminder.recipientId)) continue;
      sent += await notify(db, send, [reminder.recipientId], reminder.notice);
    }
  }
  return sent;
}

/** Members who turned the reminder off: members/{id}/settings/notifications.dayBeforeReminder. */
async function remindersOff(db: Firestore, memberIds: string[]): Promise<Set<string>> {
  if (memberIds.length === 0) return new Set();
  const refs = memberIds.map((id) => db.collection('members').doc(id).collection('settings').doc('notifications'));
  const docs = await db.getAll(...refs);
  return new Set(docs.filter((d) => d.exists && d.get('dayBeforeReminder') === false).map((d) => d.ref.parent.parent!.id));
}
