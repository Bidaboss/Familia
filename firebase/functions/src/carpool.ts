// « On y va ensemble » (PRD, récit 52), behind the switch carpool: the cars of an event. The pure
// part decides who is told and the French texts; the store part reads Firestore and sends. Every
// notice aims at someone directly (direct: true): the driver when a member gets in or out, the
// passengers when the car is withdrawn or the driver lets them out. No new scheduled task: the
// reminder of the day before (sendDayBeforeReminders) only gains a line.

import { FieldValue, type Firestore } from 'firebase-admin/firestore';
import type { Member, Notice } from './logic';

export const CARPOOL = 'carpool';

export interface RideData {
  seats: number;
  from: string | null;
  leaveAt: string | null;
  passengers: string[];
}

export interface RideMessage {
  recipientIds: string[];
  notice: Notice;
}

function joinNames(names: string[]): string {
  return names.length <= 1 ? names.join('') : `${names.slice(0, -1).join(', ')} et ${names[names.length - 1]}`;
}

function firstNames(ids: string[], byId: Map<string, Member>): string[] {
  return ids.map((id) => byId.get(id)?.firstName).filter((n): n is string => !!n);
}

/** Who reads a notice for [memberId]: the member, or the responsible adults of a child without the app. */
function readersOf(memberId: string, byId: Map<string, Member>): string[] {
  const m = byId.get(memberId);
  if (!m) return [];
  if (m.profile !== 'childWithoutApp') return [m.id];
  return m.parentIds.filter((id) => byId.has(id));
}

const seatsLeft = (ride: RideData) => {
  const n = ride.seats - ride.passengers.length;
  return n <= 0 ? 'C\'est complet.' : n === 1 ? 'Il reste 1 place.' : `Il reste ${n} places.`;
};

/**
 * The notices for a change of the car of [driverId] at [eventTitle], made by [actorId] (null for the
 * server, after a « Je ne viens plus »). Nobody is told of their own change.
 */
export function rideMessages(
  eventId: string, eventTitle: string, driverId: string,
  before: RideData | null, after: RideData | null, actorId: string | null, members: Member[],
): RideMessage[] {
  const byId = new Map(members.map((m) => [m.id, m]));
  const driverName = byId.get(driverId)?.firstName ?? 'Le conducteur';
  const out: RideMessage[] = [];
  const notice = (title: string, body: string): Notice => ({ channel: 'events', title, body, eventId, direct: true });
  const others = (ids: string[]) => [...new Set(ids)].filter((id) => id !== actorId);
  const old = before?.passengers ?? [];
  if (!after) {
    const readers = others(old.flatMap((id) => readersOf(id, byId)));
    if (readers.length > 0) {
      out.push({ recipientIds: readers, notice: notice(`${driverName} ne prend plus sa voiture`, `Pour ${eventTitle}. Regarde sur la fiche si une autre voiture a de la place.`) });
    }
    return out;
  }
  const now = after.passengers;
  const joined = now.filter((id) => !old.includes(id));
  const left = old.filter((id) => !now.includes(id));
  const fromDriver = actorId === driverId;
  if (!fromDriver && joined.length > 0 && byId.has(driverId)) {
    const names = joinNames(firstNames(joined, byId));
    const verb = joined.length > 1 ? 'montent' : 'monte';
    out.push({ recipientIds: others([driverId]), notice: notice(`${names} ${verb} avec toi`, `${eventTitle}. ${seatsLeft(after)}`) });
  }
  if (!fromDriver && left.length > 0 && byId.has(driverId)) {
    const names = joinNames(firstNames(left, byId));
    const verb = left.length > 1 ? 'ne montent plus' : 'ne monte plus';
    out.push({ recipientIds: others([driverId]), notice: notice(`${names} ${verb} avec toi`, `${eventTitle}. ${seatsLeft(after)}`) });
  }
  if (fromDriver && left.length > 0) {
    const readers = others(left.flatMap((id) => readersOf(id, byId)));
    if (readers.length > 0) {
      out.push({ recipientIds: readers, notice: notice(`${driverName} ne peut plus t'emmener`, `Pour ${eventTitle}. Regarde sur la fiche si une autre voiture a de la place.`) });
    }
  }
  return out.filter((m) => m.recipientIds.length > 0);
}

/**
 * The line each one reads in the reminder of the day before: « Tu emmènes A et B », « Tu pars
 * avec C », and for a child without the app, « Noé part avec C » to their responsible adults.
 */
export function rideLines(rides: { driverId: string; ride: RideData }[], members: Member[]): Map<string, string[]> {
  const byId = new Map(members.map((m) => [m.id, m]));
  const lines = new Map<string, string[]>();
  const add = (id: string, line: string) => lines.set(id, [...(lines.get(id) ?? []), line]);
  for (const { driverId, ride } of rides) {
    const driver = byId.get(driverId);
    if (!driver) continue;
    const names = firstNames(ride.passengers, byId);
    if (names.length > 0) add(driverId, `Tu emmènes ${joinNames(names)}`);
    for (const id of ride.passengers) {
      const p = byId.get(id);
      if (!p) continue;
      if (p.profile === 'childWithoutApp') {
        for (const parentId of readersOf(id, byId)) if (parentId !== driverId) add(parentId, `${p.firstName} part avec ${driver.firstName}`);
      } else {
        add(id, `Tu pars avec ${driver.firstName}`);
      }
    }
  }
  return lines;
}

const ridesOf = (db: Firestore, eventId: string) => db.collection('events').doc(eventId).collection('rides');

/** Someone got in: their « J'ai besoin d'une place » is answered. */
export async function clearNeedsOf(db: Firestore, eventId: string, memberIds: string[]): Promise<void> {
  const needs = db.collection('events').doc(eventId).collection('rideNeeds');
  await Promise.all(memberIds.map((id) => needs.doc(id).delete()));
}

/**
 * « Je ne viens plus » (or a member who leaves): their offer goes, they leave the car they were in,
 * and their request for a seat goes. Each change then tells whoever it concerns (onRideWritten).
 */
export async function clearRidesOf(db: Firestore, eventId: string, memberId: string): Promise<void> {
  const rides = ridesOf(db, eventId);
  await rides.doc(memberId).delete();
  const inCars = await rides.where('passengers', 'array-contains', memberId).get();
  await Promise.all(inCars.docs.map((d) => d.ref.update({ passengers: FieldValue.arrayRemove(memberId), updatedAt: FieldValue.serverTimestamp() })));
  await db.collection('events').doc(eventId).collection('rideNeeds').doc(memberId).delete();
}

/** The cars of an event, for the reminder of the day before. */
export async function loadRides(db: Firestore, eventId: string): Promise<{ driverId: string; ride: RideData }[]> {
  const snap = await ridesOf(db, eventId).get();
  return snap.docs.map((d) => ({ driverId: d.id, ride: { ...(d.data() as RideData), passengers: (d.get('passengers') as string[] | undefined) ?? [] } }));
}

/** Tells whoever a change of a car concerns, and answers the requests of those who got in. */
export async function onRideChanged(
  db: Firestore, send: (ids: string[], notice: Notice) => Promise<number>,
  eventId: string, driverId: string, before: RideData | null, after: RideData | null, actorId: string | null, members: Member[],
): Promise<number> {
  const joined = (after?.passengers ?? []).filter((id) => !(before?.passengers ?? []).includes(id));
  if (joined.length > 0) await clearNeedsOf(db, eventId, joined);
  const event = await db.collection('events').doc(eventId).get();
  // The event itself is gone: its deletion already told those who were coming.
  if (!event.exists) return 0;
  let sent = 0;
  for (const m of rideMessages(eventId, String(event.get('title') ?? ''), driverId, before, after, actorId, members)) {
    sent += await send(m.recipientIds, m.notice);
  }
  return sent;
}
