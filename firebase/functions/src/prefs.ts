// Régler mes notifications (PRD, récit 6 quinquies). Each member chooses what reaches them, in
// members/{id}/settings/notifications:
// - `level`: 'all' (default), 'essential' (the planning, the reminders, what aims at me) or
//   'minimal' (only what aims at me);
// - one switch per family (`planning`, `reminders`, `gifts`, `goodNews`, `comments`,
//   `helpRequests`, `trips`, `weekly`): `false` turns it off, whatever the level;
// - `quietHours`: `true` holds what comes between 22 h and 8 h (Paris) until 8 h, grouped.
// What aims at the member directly never goes quiet: a « Rejoignez-moi », a coup de main they
// took, the Bonne nouvelle of a child they are responsible for (AVIS-12), a capsule opened for
// them, an answer to « Mes demandes », the consumption alert of the admin principal.
//
// quietQueue/{memberId} holds the notices kept for the morning (`items`); only the functions use it.

import { FieldValue, type Firestore } from 'firebase-admin/firestore';
import type { Channel } from './logic';

export type Family = 'planning' | 'reminders' | 'gifts' | 'goodNews' | 'comments' | 'helpRequests' | 'trips' | 'weekly';
export type Level = 'all' | 'essential' | 'minimal';

export const FAMILIES: readonly Family[] = ['planning', 'reminders', 'gifts', 'goodNews', 'comments', 'helpRequests', 'trips', 'weekly'];

/** The family of each Android channel; null for a channel that only carries what aims at me. */
export const FAMILY_OF: Readonly<Record<Channel, Family | null>> = {
  events: 'planning',
  reminders: 'reminders',
  gifts: 'gifts',
  goodNews: 'goodNews',
  comments: 'comments',
  helpRequests: 'helpRequests',
  trips: 'trips',
  // « Rendez-vous de la semaine »: souvenirs, défi, récap, question de la semaine, vœux.
  weekly: 'weekly',
  souvenirs: 'weekly',
  tree: 'weekly',
  recap: 'weekly',
  birthdays: 'weekly',
  joinMe: null,
  capsules: null,
  feedback: null,
  admin: null,
};

/** What the filter needs to know of a notice. */
export interface Filtered {
  channel: Channel;
  /** Aimed at the member directly: never muted, whatever the settings. */
  direct?: boolean;
}

export interface Prefs {
  level: Level;
  off: ReadonlySet<Family>;
  quietHours: boolean;
}

export const DEFAULT_PREFS: Prefs = { level: 'all', off: new Set(), quietHours: false };

/** The settings document as the app writes it; anything unknown keeps the default. */
export function prefsFrom(data: Record<string, unknown> | undefined): Prefs {
  if (!data) return DEFAULT_PREFS;
  const level = data.level === 'essential' || data.level === 'minimal' ? data.level : 'all';
  return { level, off: new Set(FAMILIES.filter((f) => data[f] === false)), quietHours: data.quietHours === true };
}

/** Whether [notice] may reach a member with [prefs] at all. */
export function allows(prefs: Prefs, notice: Filtered): boolean {
  const family = FAMILY_OF[notice.channel];
  if (notice.direct || family === null) return true;
  if (prefs.off.has(family)) return false;
  if (prefs.level === 'minimal') return false;
  if (prefs.level === 'essential') return family === 'planning' || family === 'reminders';
  return true;
}

/** From 22 h to 8 h in Paris. */
export function isQuietHour(now: Date): boolean {
  const hour = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Paris', hour: '2-digit', hourCycle: 'h23' }).format(now));
  return hour >= 22 || hour < 8;
}

export async function loadPrefs(db: Firestore, memberIds: string[]): Promise<Map<string, Prefs>> {
  const prefs = new Map<string, Prefs>();
  if (memberIds.length === 0) return prefs;
  const docs = await db.getAll(...memberIds.map((id) => db.doc(`members/${id}/settings/notifications`)));
  docs.forEach((doc, i) => prefs.set(memberIds[i], prefsFrom(doc.data())));
  return prefs;
}

/** Who gets [notice] now, and who gets it at 8 h; the others turned it off. */
export async function audience(db: Firestore, memberIds: string[], notice: Filtered, now: Date): Promise<{ now: string[]; later: string[] }> {
  const prefs = await loadPrefs(db, memberIds);
  const quiet = isQuietHour(now);
  const result = { now: [] as string[], later: [] as string[] };
  for (const id of memberIds) {
    const p = prefs.get(id) ?? DEFAULT_PREFS;
    if (!allows(p, notice)) continue;
    (quiet && p.quietHours ? result.later : result.now).push(id);
  }
  return result;
}

export type AnySender<N> = (token: string, notice: N) => Promise<'ok' | 'unregistered' | 'error'>;

/** Sends [notice] to every phone of [memberIds], forgetting the phones that are gone. */
export async function sendToDevices<N>(db: Firestore, send: AnySender<N>, memberIds: string[], notice: N): Promise<number> {
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

/** The single way out for a notice: filtered by each member's settings, held during quiet hours. */
export async function deliver<N extends Filtered>(db: Firestore, send: AnySender<N>, memberIds: string[], notice: N, now: Date = new Date()): Promise<number> {
  const { now: ready, later } = await audience(db, memberIds, notice, now);
  if (later.length > 0) {
    // Firestore refuses undefined fields: the notice is kept as plain JSON.
    const item = { ...JSON.parse(JSON.stringify(notice)), heldAt: now.toISOString() };
    const batch = db.batch();
    for (const id of later) batch.set(db.collection('quietQueue').doc(id), { items: FieldValue.arrayUnion(item) }, { merge: true });
    await batch.commit();
  }
  return sendToDevices(db, send, ready, notice);
}

const MAX_BODY = 180;

/**
 * The notices of the night, one per channel so that a surprise keeps its private channel: a
 * single one as it came, several as « Cette nuit dans Familia » with their titles.
 */
export function morningNotices<N extends Filtered & { title: string; body: string }>(items: N[]): N[] {
  const byChannel = new Map<Channel, N[]>();
  for (const item of items) byChannel.set(item.channel, [...(byChannel.get(item.channel) ?? []), item]);
  return [...byChannel.values()].map((group) => {
    if (group.length === 1) return group[0];
    const titles = [...new Set(group.map((n) => n.title))].join(' · ');
    const body = `${group.length} notifications : ${titles}`;
    return { channel: group[0].channel, title: 'Cette nuit dans Familia', body: body.length > MAX_BODY ? `${body.slice(0, MAX_BODY - 1)}…` : body } as N;
  });
}

/** From 8 h, what was held during the night goes out, once. Returns how many phones got something. */
export async function sendHeldNotices<N extends Filtered & { title: string; body: string }>(db: Firestore, send: AnySender<N>, now: Date): Promise<number> {
  if (isQuietHour(now)) return 0;
  const queues = await db.collection('quietQueue').get();
  let sent = 0;
  for (const queue of queues.docs) {
    // Taken and emptied at once, so two runs never send the same night twice.
    const items = await db.runTransaction(async (tx) => {
      const doc = await tx.get(queue.ref);
      tx.delete(queue.ref);
      return ((doc.get('items') as (N & { heldAt?: string })[] | undefined) ?? []);
    });
    const notices = morningNotices(items.map(({ heldAt: _heldAt, ...n }) => n as unknown as N));
    for (const notice of notices) sent += await sendToDevices(db, send, [queue.id], notice);
  }
  return sent;
}

/** A member who leaves takes their held notices with them. */
export async function deleteQuietQueueOf(db: Firestore, memberId: string): Promise<void> {
  await db.collection('quietQueue').doc(memberId).delete();
}
