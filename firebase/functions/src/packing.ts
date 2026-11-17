// « Ma valise » (PRD, récit 62), behind the switch packingList. The day before a trip, from 18 h in
// Paris, one notification to each participant whose bag still has things not ticked, and to the
// adults (taking part) of a child without the app whose bag is not ready. A bag never opened says
// nothing: the member does not use it. members/{id}/settings/notifications.packingReminder ===
// false turns it off. Sent by arrivalReminder, every 30 minutes: no new task. remindersSent keeps
// what went out, once per trip and member.

import type { Firestore } from 'firebase-admin/firestore';
import { Timestamp } from 'firebase-admin/firestore';
import { addDays, parisDay, type Member, type Notice } from './logic';
import { deliver, type AnySender } from './prefs';
import { featureAudience, loadMembers } from './store';

export const PACKING_LIST = 'packingList';

/** From this hour in Paris, the day before the departure. */
export const PACKING_HOUR = 18;

export interface BagLine { label: string; done: boolean }

/** How many things are still to pack in a bag; 0 for a bag never opened. */
export function leftIn(items: unknown): number {
  if (!Array.isArray(items)) return 0;
  return items.filter((i) => i && typeof i === 'object' && (i as BagLine).done !== true).length;
}

export interface PackingMessage { recipientId: string; notice: Notice }

/**
 * Who hears about which bags. [left] maps each participant to the things left in their bag. A
 * member with the app hears about their own bag; the adults of a child without the app who take
 * part hear about the child's. One notice per recipient.
 */
export function packingMessages(tripId: string, title: string, participantIds: string[], left: Map<string, number>, members: Member[]): PackingMessage[] {
  const byId = new Map(members.map((m) => [m.id, m]));
  const mine = new Map<string, { own: number; children: string[] }>();
  const entry = (id: string) => mine.get(id) ?? (mine.set(id, { own: 0, children: [] }), mine.get(id)!);
  for (const id of participantIds) {
    const n = left.get(id) ?? 0;
    const m = byId.get(id);
    if (n === 0 || !m) continue;
    if (m.profile !== 'childWithoutApp') {
      entry(id).own = n;
    } else {
      for (const adult of m.parentIds ?? []) {
        if (participantIds.includes(adult) && byId.get(adult)?.profile === 'adult') entry(adult).children.push(m.firstName);
      }
    }
  }
  return [...mine.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([recipientId, { own, children }]) => {
    const parts: string[] = [];
    if (own > 0) parts.push(own === 1 ? 'il reste 1 objet dans ta valise' : `il reste ${own} objets dans ta valise`);
    for (const child of children.sort((a, b) => a.localeCompare(b, 'fr'))) parts.push(`la valise de ${child} n'est pas prête`);
    const body = `${title} : ${parts.join(', ')}.`;
    return { recipientId, notice: { channel: 'trips', title: 'Demain, on part !', body: body.charAt(0).toUpperCase() + body.slice(1), tripId } };
  });
}

/** The day before each trip that leaves tomorrow, from 18 h: the reminders not sent yet. */
export async function sendPackingReminders(db: Firestore, send: AnySender<Notice>, now: Date): Promise<number> {
  const hour = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Paris', hour: '2-digit', hourCycle: 'h23' }).format(now));
  if (hour < PACKING_HOUR) return 0;
  const tomorrow = addDays(parisDay(now), 1);
  const trips = await db.collection('trips').where('startDate', '==', tomorrow).get();
  if (trips.empty) return 0;
  const only = await featureAudience(db, PACKING_LIST);
  const members = await loadMembers(db);
  let sent = 0;
  for (const trip of trips.docs) {
    const participantIds = (trip.get('participantIds') as string[] | undefined) ?? [];
    const bags = await trip.ref.collection('bags').get();
    const left = new Map(bags.docs.map((b) => [b.id, leftIn(b.get('items'))]));
    for (const { recipientId, notice } of packingMessages(trip.id, String(trip.get('title') ?? ''), participantIds, left, members)) {
      if (only([recipientId]).length === 0) continue;
      const settings = await db.doc(`members/${recipientId}/settings/notifications`).get();
      if (settings.get('packingReminder') === false) continue;
      try {
        await db.collection('remindersSent').doc(`packing_${trip.id}_${recipientId}`).create({
          expireAt: Timestamp.fromMillis(now.getTime() + 3 * 24 * 60 * 60 * 1000),
        });
      } catch {
        continue; // Already sent by an earlier run.
      }
      sent += await deliver(db, send, [recipientId], notice, now);
    }
  }
  return sent;
}

/**
 * Who left the trip between [before] and [after]: their bag has nothing more to do there. A
 * member taken back later starts from their list again, like a new participant.
 */
export function leftTheTrip(before: unknown, after: unknown): string[] {
  const ids = (raw: unknown) => (Array.isArray(raw) ? raw.filter((id): id is string => typeof id === 'string') : []);
  const still = new Set(ids(after));
  return [...new Set(ids(before))].filter((id) => !still.has(id)).sort();
}

/** Deletes the bags of [memberIds] for [tripId]; a bag never opened is simply not there. */
export async function dropBags(db: Firestore, tripId: string, memberIds: string[]): Promise<number> {
  if (memberIds.length === 0) return 0;
  const batch = db.batch();
  for (const id of memberIds) batch.delete(db.doc(`trips/${tripId}/bags/${id}`));
  await batch.commit();
  return memberIds.length;
}
