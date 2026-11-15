// What the functions read and write in Firestore. Each function takes the database and the
// sender as parameters, so the tests run them against the emulator with a fake sender.

import type { Firestore } from 'firebase-admin/firestore';
import {
  changeNotice, changeRecipients, ChangeKind, EventData, Member, Notice, parisDay, addDays, reminders, RsvpStatus,
} from './logic';

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
export async function notify(db: Firestore, send: Sender, memberIds: string[], notice: Notice): Promise<number> {
  let sent = 0;
  for (const memberId of memberIds) {
    const devices = await db.collection('members').doc(memberId).collection('devices').get();
    for (const device of devices.docs) {
      const result = await send(device.id, notice);
      if (result === 'ok') sent++;
      if (result === 'unregistered') await device.ref.delete();
    }
  }
  return sent;
}

/**
 * PRD, récit 6: an event was added, edited or deleted by [actor]. [answered] holds the members who
 * said « Je viens » or « Peut-être »: read before a deletion removes the answers.
 */
export async function notifyChange(
  db: Firestore, send: Sender, kind: ChangeKind, eventId: string, event: EventData, actorUid: string | undefined, now: Date,
  answered: Set<string> = new Set(), loaded?: Member[],
): Promise<number> {
  const members = loaded ?? await loadMembers(db);
  const actor = memberForUid(members, actorUid);
  const recipients = changeRecipients(members, actor?.id ?? null, event, parisDay(now), answered);
  if (recipients.length === 0) return 0;
  return notify(db, send, recipients, changeNotice(kind, eventId, event, actor?.firstName ?? null));
}

/**
 * Who said « Je viens » or « Peut-être » to [eventId]. A child without the app answers through
 * their adults, who are then told instead.
 */
export async function comingMembers(db: Firestore, eventId: string, members?: Member[]): Promise<Set<string>> {
  const rsvps = await db.collection('events').doc(eventId).collection('rsvps').get();
  const all = members ?? await loadMembers(db);
  const byId = new Map(all.map((m) => [m.id, m]));
  const coming = new Set<string>();
  for (const r of rsvps.docs) {
    if (r.get('status') === 'no') continue;
    const member = byId.get(r.id);
    if (!member) continue;
    if (member.profile === 'childWithoutApp') member.parentIds.forEach((id) => coming.add(id));
    else coming.add(member.id);
  }
  return coming;
}

/** The answers of a deleted event are useless and would stay readable: they go with it. */
export async function deleteEventAnswers(db: Firestore, eventId: string): Promise<void> {
  await db.recursiveDelete(db.collection('events').doc(eventId).collection('rsvps'));
}

/**
 * A member was removed (a separation, for example): their answers, phones and settings go too.
 * The family has a few hundred events at most, so going through them all stays cheap.
 */
export async function deleteMemberData(db: Firestore, memberId: string): Promise<void> {
  const events = await db.collection('events').select().get();
  const writer = db.bulkWriter();
  for (const event of events.docs) {
    writer.delete(event.ref.collection('rsvps').doc(memberId));
  }
  await writer.close();
  const member = db.collection('members').doc(memberId);
  await db.recursiveDelete(member.collection('devices'));
  await db.recursiveDelete(member.collection('settings'));
}

/** PRD, récit 6 bis: at 18 h, the members coming tomorrow are reminded. */
export async function sendDayBeforeReminders(db: Firestore, send: Sender, now: Date): Promise<number> {
  const tomorrow = addDays(parisDay(now), 1);
  const events = await db.collection('events').where('date', '==', tomorrow).get();
  if (events.empty) return 0;
  const members = await loadMembers(db);
  let sent = 0;
  for (const event of events.docs) {
    const answers = new Map<string, RsvpStatus>();
    const rsvps = await event.ref.collection('rsvps').get();
    rsvps.docs.forEach((r) => answers.set(r.id, r.get('status')));
    const all = reminders(members, event.id, event.data() as EventData, answers, new Set());
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
