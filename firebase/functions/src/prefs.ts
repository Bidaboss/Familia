// Régler mes notifications (PRD, récit 6 quinquies). Each member chooses what reaches them, in
// members/{id}/settings/notifications:
// - one switch per family (`planning`, `reminders`, `gifts`, `goodNews`, `comments`,
//   `helpRequests`, `trips`, `weekly`): `false` turns it off. The three levels of the screen
//   (« Tout », « L'essentiel », « Presque rien ») only set these switches at once, so a switch
//   changed afterwards always counts;
// - `quietHours`: `true` holds what comes between 22 h and 8 h (Paris) until 8 h, grouped.
// What aims at the member directly never goes quiet: a « Rejoignez-moi », a coup de main they
// took, the Bonne nouvelle of a child they are responsible for (AVIS-12), a capsule opened for
// them, an answer to « Mes demandes », the consumption alert of the admin principal.
//
// quietQueue/{memberId} holds the notices kept for the morning (`items`); only the functions use it.
//
// Mode présentation (récit 48): while config/presentation is on, the arrivals (bienvenue), the
// reactions, the gifts and the capsules wait in presentationQueue/{memberId}, then go out as a
// summary when it ends (the arrivals, the rest in one notice, a surprise apart): turned off in the
// app, or at the first send after its end.
// « Rejoignez-moi », the retours and the admin always go. No scheduled task of its own.

import { FieldValue, type Firestore } from 'firebase-admin/firestore';
import type { Channel } from './logic';

export type Family = 'planning' | 'reminders' | 'gifts' | 'goodNews' | 'comments' | 'helpRequests' | 'trips' | 'weekly';

export const FAMILIES: readonly Family[] = ['planning', 'reminders', 'gifts', 'goodNews', 'comments', 'helpRequests', 'trips', 'weekly'];

/** The family of each Android channel; null for a channel that only carries what aims at me. */
export const FAMILY_OF: Readonly<Record<Channel, Family | null>> = {
  events: 'planning',
  reminders: 'reminders',
  gifts: 'gifts',
  goodNews: 'goodNews',
  // La bienvenue d'un nouveau membre (récit 43) is a good news of the family.
  welcome: 'goodNews',
  comments: 'comments',
  helpRequests: 'helpRequests',
  trips: 'trips',
  // « Rendez-vous de la semaine »: souvenirs, défi, récap, question de la semaine, vœux.
  weekly: 'weekly',
  souvenirs: 'weekly',
  tree: 'weekly',
  recap: 'weekly',
  birthdays: 'weekly',
  // « Pensez à moi » (récit 43): like the vœux, the invitation of the day.
  thinkOfMe: 'weekly',
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
  /** A reaction (« Inès a réagi »): held during the mode présentation like the arrivals. */
  reaction?: boolean;
  /** Sent by the admin principal (a good news of the meal): rings even during the mode présentation. */
  fromOwner?: boolean;
}

export interface Prefs {
  off: ReadonlySet<Family>;
  quietHours: boolean;
}

export const DEFAULT_PREFS: Prefs = { off: new Set(), quietHours: false };

/** The settings document as the app writes it; anything unknown keeps the default. */
export function prefsFrom(data: Record<string, unknown> | undefined): Prefs {
  if (!data) return DEFAULT_PREFS;
  return { off: new Set(FAMILIES.filter((f) => data[f] === false)), quietHours: data.quietHours === true };
}

/** Whether [notice] may reach a member with [prefs] at all. */
export function allows(prefs: Prefs, notice: Filtered): boolean {
  const family = FAMILY_OF[notice.channel];
  if (notice.direct || family === null) return true;
  return !prefs.off.has(family);
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
  // What aims at the member directly rings even at night: a « rejoins-moi » cannot wait for 8 h.
  const quiet = isQuietHour(now) && !notice.direct && FAMILY_OF[notice.channel] !== null;
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
  return (await sendCounting(db, send, memberIds, notice)).sent;
}

/** As [sendToDevices], also counting the phones that could not be reached this time. */
async function sendCounting<N>(db: Firestore, send: AnySender<N>, memberIds: string[], notice: N): Promise<{ sent: number; failed: number }> {
  let sent = 0;
  let failed = 0;
  for (const memberId of memberIds) {
    const devices = await db.collection('members').doc(memberId).collection('devices').get();
    for (const device of devices.docs) {
      const result = await send(device.id, notice);
      if (result === 'ok') sent++;
      if (result === 'error') failed++;
      if (result === 'unregistered') await device.ref.delete();
    }
  }
  return { sent, failed };
}

/** config/presentation, written by the admin principal from Administration. */
export const PRESENTATION_DOC = 'config/presentation';

/** The mode présentation: [until] null means « jusqu'à ce que je l'éteigne ». */
export interface Presentation {
  on: boolean;
  until: Date | null;
}

export function presentationFrom(data: Record<string, unknown> | undefined): Presentation {
  const until = data?.until as { toDate?: () => Date } | null | undefined;
  return { on: data?.on === true, until: typeof until?.toDate === 'function' ? until.toDate() : null };
}

export function isPresenting(p: Presentation, now: Date): boolean {
  return p.on && (p.until === null || p.until.getTime() > now.getTime());
}

/** Channels that wait during the mode présentation; joinMe, feedback and admin never wait. */
// While the whole family tries the app at the same table, what everyone posts waits for the summary
// (coups de main, comments, good news). Only the admin principal's good news rings at once.
const HELD_WHILE_PRESENTING: ReadonlySet<Channel> = new Set<Channel>(['welcome', 'gifts', 'capsules', 'helpRequests', 'comments', 'goodNews']);

export function heldByPresentation(notice: Filtered): boolean {
  if (notice.channel === 'joinMe' || notice.channel === 'feedback' || notice.channel === 'admin') return false;
  if (notice.channel === 'goodNews' && notice.fromOwner === true) return false;
  return HELD_WHILE_PRESENTING.has(notice.channel) || notice.reaction === true;
}

/**
 * Turns off a mode présentation whose end has passed, once (a transaction): the trigger on
 * config/presentation then sends the summary. Returns whether the mode is on at [now].
 */
export async function presentingNow(db: Firestore, now: Date): Promise<boolean> {
  const ref = db.doc(PRESENTATION_DOC);
  const p = presentationFrom((await ref.get()).data());
  if (isPresenting(p, now)) return true;
  if (p.on) {
    await db.runTransaction(async (tx) => {
      const again = presentationFrom((await tx.get(ref)).data());
      if (again.on && !isPresenting(again, now)) tx.set(ref, { on: false }, { merge: true });
    });
  }
  return false;
}

/** The single way out for a notice: filtered by each member's settings, held during quiet hours. */
export async function deliver<N extends Filtered>(db: Firestore, send: AnySender<N>, memberIds: string[], notice: N, now: Date = new Date()): Promise<number> {
  const { now: ready, later } = await audience(db, memberIds, notice, now);
  // Every send looks at the mode présentation, so its end is seen at the first send that follows.
  // A failed read of config/presentation never stops a notice: it then goes as usual.
  let presenting = false;
  try {
    presenting = await presentingNow(db, now);
  } catch (e) {
    console.error('presentation: read failed', e);
  }
  if (presenting && heldByPresentation(notice) && ready.length + later.length > 0) {
    const item = { ...JSON.parse(JSON.stringify(notice)), heldAt: now.toISOString() };
    const batch = db.batch();
    for (const id of [...ready, ...later]) batch.set(db.collection('presentationQueue').doc(id), { items: FieldValue.arrayUnion(item) }, { merge: true });
    await batch.commit();
    return 0;
  }
  if (later.length > 0) {
    // Firestore refuses undefined fields: the notice is kept as plain JSON.
    const item = { ...JSON.parse(JSON.stringify(notice)), heldAt: now.toISOString() };
    const batch = db.batch();
    for (const id of later) batch.set(db.collection('quietQueue').doc(id), { items: FieldValue.arrayUnion(item) }, { merge: true });
    await batch.commit();
  }
  // What was held above (night, mode présentation) is not counted: it was not sent.
  const { go, wait } = burstCap && cappable(notice) ? await splitBurst(db, ready, now) : { go: ready, wait: [] };
  if (wait.length > 0) {
    const item = { ...JSON.parse(JSON.stringify(notice)), heldAt: now.toISOString() };
    const batch = db.batch();
    for (const id of wait) batch.set(db.collection(BURST_QUEUE).doc(id), { items: FieldValue.arrayUnion(item) }, { merge: true });
    await batch.commit();
  }
  return sendToDevices(db, send, go, notice);
}

// --- Avalanche de notifications (démo du 21) -----------------------------------------------------
//
// When a dozen people arrive, react and post in the same hour, nobody's phone should ring more than
// BURST_MAX times an hour. notifyLog/{memberId} keeps when the last notices went out; past the cap,
// a notice waits in burstQueue/{memberId}, and arrivalReminder (every 30 min) sends what waited as
// one notice. What cannot wait is never counted nor held: « Rejoins-moi », the reminders of an
// event, the retours, the admin and the good news of the admin principal. Only the functions read and write these two collections.

/** At most this many notices an hour on a member's phone, the grouped ones included. */
export const BURST_MAX = 3;
export const BURST_WINDOW_MS = 60 * 60 * 1000;
const BURST_QUEUE = 'burstQueue';
const NOTIFY_LOG = 'notifyLog';

/** Channels whose notifications may hold a surprise (gifts, time capsules). Same list in NotificationChannels.kt. */
export const PRIVATE_CHANNELS: ReadonlySet<Channel> = new Set<Channel>(['gifts', 'capsules']);

/** What always rings at once, whatever came before. */
const NEVER_CAPPED: ReadonlySet<Channel> = new Set<Channel>(['joinMe', 'reminders', 'feedback', 'admin']);

// Turned on once by index.ts, so the sends of the other tests are never counted.
let burstCap = false;

/** Counts the notices of each member and groups what goes past [BURST_MAX] an hour. */
export function capBursts(on = true): void {
  burstCap = on;
}

export function cappable(notice: Filtered): boolean {
  // The good news of the admin principal (the photo of the meal) rings at once, as in the mode présentation.
  return !NEVER_CAPPED.has(notice.channel) && notice.fromOwner !== true;
}

/** Takes one of [memberId]'s places of the hour, if one is left (a transaction, so two sends never take the same). */
async function takeSlot(db: Firestore, memberId: string, now: Date): Promise<boolean> {
  const ref = db.collection(NOTIFY_LOG).doc(memberId);
  return db.runTransaction(async (tx) => {
    const since = now.getTime() - BURST_WINDOW_MS;
    const at = ((await tx.get(ref)).get('at') as string[] | undefined ?? []).filter((t) => Date.parse(t) > since);
    if (at.length >= BURST_MAX) return false;
    tx.set(ref, { at: [...at, now.toISOString()] });
    return true;
  });
}

async function splitBurst(db: Firestore, memberIds: string[], now: Date): Promise<{ go: string[]; wait: string[] }> {
  const result = { go: [] as string[], wait: [] as string[] };
  for (const id of memberIds) {
    // A failed count never stops a notice: it then goes as usual.
    let free = true;
    try {
      free = await takeSlot(db, id, now);
    } catch (e) {
      console.error('notifyLog: count failed', id, e);
    }
    (free ? result.go : result.wait).push(id);
  }
  return result;
}

/**
 * What waited past the cap, as few notices as possible: the notices of a same tag (the cards of the
 * day) keep the last one, a surprise keeps its private channel, and the rest is one notice.
 */
export function burstNotices<N extends Filtered & { title: string; body: string; tag?: string }>(items: N[], heading = 'Dans Familia'): N[] {
  const byTag = new Map<string, number>();
  items.forEach((n, i) => { if (n.tag) byTag.set(n.tag, i); });
  const kept = items.filter((n, i) => !n.tag || byTag.get(n.tag) === i);
  const shared = kept.filter((n) => !PRIVATE_CHANNELS.has(n.channel));
  const surprises = morningNotices(kept.filter((n) => PRIVATE_CHANNELS.has(n.channel)), heading);
  if (shared.length <= 1) return [...shared, ...surprises];
  const counts = new Map<Channel, number>();
  for (const n of shared) counts.set(n.channel, (counts.get(n.channel) ?? 0) + 1);
  const channel = [...counts.entries()].sort((a, b) => b[1] - a[1])[0][0];
  const titles = [...new Set(shared.map((n) => n.title))].join(' · ');
  const body = `${shared.length} notifications : ${titles}`;
  const one = { channel, title: heading, body: body.length > MAX_BODY ? `${body.slice(0, MAX_BODY - 1)}…` : body } as N;
  return [one, ...surprises];
}

/**
 * From arrivalReminder: for each member who has a free place again, what waited goes out grouped,
 * taking one place. The night waits for 8 h and the mode présentation for its summary, as in
 * [deliver]; what reached none of the phones waits for the next run, for a day at most.
 */
export async function sendBurstHeld<N extends Filtered & { title: string; body: string; tag?: string }>(db: Firestore, send: AnySender<N>, now: Date): Promise<number> {
  const queues = await db.collection(BURST_QUEUE).get();
  if (queues.empty) return 0;
  let presenting = false;
  try {
    presenting = await presentingNow(db, now);
  } catch (e) {
    console.error('presentation: read failed', e);
  }
  let sent = 0;
  for (const queue of queues.docs) {
    if (!(await takeSlot(db, queue.id, now))) continue; // Still at the cap: the next run.
    const items = await db.runTransaction(async (tx) => {
      const doc = await tx.get(queue.ref);
      tx.delete(queue.ref);
      return ((doc.get('items') as (N & { heldAt?: string })[] | undefined) ?? []);
    });
    const fresh = items.filter((n) => !n.heldAt || now.getTime() - Date.parse(n.heldAt) < MAX_HELD_MS);
    const prefs = (await loadPrefs(db, [queue.id])).get(queue.id) ?? DEFAULT_PREFS;
    const again: unknown[] = [];
    for (const notice of burstNotices(fresh.map(({ heldAt: _heldAt, ...n }) => n as unknown as N))) {
      const item = { ...JSON.parse(JSON.stringify(notice)), heldAt: now.toISOString() };
      if (presenting && heldByPresentation(notice)) {
        await db.collection('presentationQueue').doc(queue.id).set({ items: FieldValue.arrayUnion(item) }, { merge: true });
        continue;
      }
      if (isQuietHour(now) && prefs.quietHours && !notice.direct && FAMILY_OF[notice.channel] !== null) {
        await db.collection('quietQueue').doc(queue.id).set({ items: FieldValue.arrayUnion(item) }, { merge: true });
        continue;
      }
      let result = { sent: 0, failed: 0 };
      try {
        result = await sendCounting(db, send, [queue.id], notice);
      } catch (e) {
        console.error('burstQueue: send failed', queue.id, e);
        result = { sent: 0, failed: 1 };
      }
      sent += result.sent;
      if (result.sent === 0 && result.failed > 0) again.push(item);
    }
    if (again.length > 0) await queue.ref.set({ items: FieldValue.arrayUnion(...again) }, { merge: true });
  }
  return sent;
}

const MAX_BODY = 180;

/**
 * The notices of the night, one per channel so that a surprise keeps its private channel: a
 * single one as it came, several as « Cette nuit dans Familia » with their titles.
 */
export function morningNotices<N extends Filtered & { title: string; body: string }>(items: N[], heading = 'Cette nuit dans Familia'): N[] {
  const byChannel = new Map<Channel, N[]>();
  for (const item of items) byChannel.set(item.channel, [...(byChannel.get(item.channel) ?? []), item]);
  return [...byChannel.values()].map((group) => {
    if (group.length === 1) return group[0];
    const titles = [...new Set(group.map((n) => n.title))].join(' · ');
    const body = `${group.length} notifications : ${titles}`;
    return { channel: group[0].channel, title: heading, body: body.length > MAX_BODY ? `${body.slice(0, MAX_BODY - 1)}…` : body } as N;
  });
}

/** A notice of the night that still cannot go out after a day is dropped: it is no longer news. */
const MAX_HELD_MS = 24 * 60 * 60 * 1000;

/**
 * From 8 h, what was held during the night goes out, once. Returns how many phones got something.
 * A channel that reached none of the member's phones (an error, the network) goes back to the
 * queue and is tried again by the next run, for a day at most.
 */
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
    const byChannel = new Map<Channel, (N & { heldAt?: string })[]>();
    for (const item of items) byChannel.set(item.channel, [...(byChannel.get(item.channel) ?? []), item]);
    const again: (N & { heldAt?: string })[] = [];
    for (const group of byChannel.values()) {
      const [notice] = morningNotices(group.map(({ heldAt: _heldAt, ...n }) => n as unknown as N));
      let result = { sent: 0, failed: 0 };
      try {
        result = await sendCounting(db, send, [queue.id], notice);
      } catch (e) {
        console.error('quietQueue: send failed', queue.id, e);
        result = { sent: 0, failed: 1 };
      }
      sent += result.sent;
      if (result.sent === 0 && result.failed > 0) {
        again.push(...group.filter((n) => !n.heldAt || now.getTime() - Date.parse(n.heldAt) < MAX_HELD_MS));
      }
    }
    if (again.length > 0) await queue.ref.set({ items: FieldValue.arrayUnion(...again) }, { merge: true });
  }
  return sent;
}

/** A member who leaves takes their held notices with them. */
export async function deleteQuietQueueOf(db: Firestore, memberId: string): Promise<void> {
  await db.collection('quietQueue').doc(memberId).delete();
  await db.collection('presentationQueue').doc(memberId).delete();
  await db.collection(BURST_QUEUE).doc(memberId).delete();
  await db.collection(NOTIFY_LOG).doc(memberId).delete();
}

/** « Bienvenue » titles end with this (welcome.ts): the summary names those who arrived. */
const ARRIVED = " vient d'arriver";

/**
 * The summary of the mode présentation for one member: the arrivals as « 9 personnes ont rejoint
 * Familia », the rest as one notice, and a surprise on its private channel.
 */
export function presentationNotices<N extends Filtered & { title: string; body: string }>(items: N[]): N[] {
  const welcome = items.filter((n) => n.channel === 'welcome');
  // The rest as one notice (avalanche du 21), a surprise keeping its private channel.
  const others = burstNotices(items.filter((n) => n.channel !== 'welcome'), 'Pendant la présentation');
  if (welcome.length <= 1) return [...welcome, ...others];
  // A grouped bienvenue (welcome.ts) carries its names.
  const names = [...new Set(welcome.flatMap((n) => {
    const grouped = (n as { welcomeNames?: unknown }).welcomeNames;
    if (Array.isArray(grouped)) return grouped.map(String);
    return [n.title.endsWith(ARRIVED) ? n.title.slice(0, -ARRIVED.length) : n.title];
  }))];
  const shown = names.length <= 3
    ? `${names.slice(0, -1).join(', ')} et ${names[names.length - 1]}`
    : `${names.slice(0, 3).join(', ')} et ${names.length - 3} autres`;
  const body = `${shown}. Dis-leur bonjour !`;
  const summary = { channel: 'welcome' as Channel, title: `${names.length} personnes ont rejoint Familia`, body: body.length > MAX_BODY ? `${body.slice(0, MAX_BODY - 1)}…` : body } as N;
  return [summary, ...others];
}

/**
 * Once the mode présentation is off, what it held goes out: the summary of each member,
 * held again until 8 h for a member with quiet hours at night. Returns how many phones got something.
 */
export async function sendPresentationHeld<N extends Filtered & { title: string; body: string }>(db: Firestore, send: AnySender<N>, now: Date): Promise<number> {
  const queues = await db.collection('presentationQueue').get();
  let sent = 0;
  for (const queue of queues.docs) {
    // Taken and emptied at once, so two triggers never send the same summary twice.
    const items = await db.runTransaction(async (tx) => {
      const doc = await tx.get(queue.ref);
      tx.delete(queue.ref);
      return ((doc.get('items') as (N & { heldAt?: string })[] | undefined) ?? []);
    });
    const notices = presentationNotices(items.map(({ heldAt: _heldAt, ...n }) => n as unknown as N));
    const prefs = (await loadPrefs(db, [queue.id])).get(queue.id) ?? DEFAULT_PREFS;
    for (const notice of notices) {
      // The night waits for 8 h; a summary that reached none of the phones is tried again by the
      // morning queue (every 30 min, for a day at most), so it is never lost.
      const quiet = isQuietHour(now) && prefs.quietHours && !notice.direct && FAMILY_OF[notice.channel] !== null;
      let result = { sent: 0, failed: 0 };
      if (!quiet) {
        try {
          result = await sendCounting(db, send, [queue.id], notice);
        } catch (e) {
          console.error('presentationQueue: send failed', queue.id, e);
          result = { sent: 0, failed: 1 };
        }
      }
      sent += result.sent;
      if (quiet || (result.sent === 0 && result.failed > 0)) {
        const item = { ...JSON.parse(JSON.stringify(notice)), heldAt: now.toISOString() };
        await db.collection('quietQueue').doc(queue.id).set({ items: FieldValue.arrayUnion(item) }, { merge: true });
      }
    }
  }
  return sent;
}
