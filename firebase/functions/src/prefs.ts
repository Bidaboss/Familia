// Régler mes notifications (PRD, récit 6 quinquies). Each member chooses what reaches them, in
// members/{id}/settings/notifications:
// - one switch per family (`planning`, `reminders`, `gifts`, `goodNews`, `comments`,
//   `helpRequests`, `trips`, `weekly`): `false` turns it off. The three levels of the screen
//   (« Tout », « L'essentiel », « Presque rien ») only set these switches at once, so a switch
//   changed afterwards always counts;
// - `quietHours`: holds what comes between 22 h and 8 h (Paris) until 8 h, grouped. On unless
//   set to `false` (nuit du 21 : a member who never opened their settings sleeps too).
// What aims at the member directly never goes quiet: a « Rejoignez-moi », a coup de main they
// took, the Bonne nouvelle of a child they are responsible for (AVIS-12), a capsule opened for
// them, an answer to « Mes demandes », the consumption alert of the admin principal.
//
// quietQueue/{memberId} holds the notices kept for the morning (`items`); only the functions use it.
//
// The Mode présentation (récit 48) was removed on 18/11 (PRODUIT-132): config/presentation, if it
// still exists, is ignored.
//
// Résumé du soir (récit 70, DESIGN-31): while the module `eveningDigest` is on, the small news of
// the day (reactions, comments, question of the week, souvenirs) wait in eveningQueue/{memberId}
// from 8 h to 19 h and go out at 19 h as one notice. Each member may keep a kind immediate
// (`eveningReactions`, `eveningComments`, `eveningWeekly`, `eveningSouvenirs` set to `false`).
// The quiet hours come first; what aims at the member directly never waits. Only the functions use
// eveningQueue. While the module is off, nothing changes.

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
  /** A reaction (« Inès a réagi »): it waits for the résumé du soir (récit 70). */
  reaction?: boolean;
  /** Sent by the admin principal (a good news of the meal): never held back by the burst cap. */
  fromOwner?: boolean;
}

/** The kinds of small news that can wait for the résumé du soir (récit 70). */
export type EveningKind = 'reactions' | 'comments' | 'weekly' | 'souvenirs';

/** The key of each kind in the settings document: `false` keeps it immediate. */
export const EVENING_KEYS: Readonly<Record<EveningKind, string>> = {
  reactions: 'eveningReactions',
  comments: 'eveningComments',
  weekly: 'eveningWeekly',
  souvenirs: 'eveningSouvenirs',
};

const EVENING_KINDS = Object.keys(EVENING_KEYS) as EveningKind[];

export interface Prefs {
  off: ReadonlySet<Family>;
  quietHours: boolean;
  /** The kinds this member lets wait for 19 h when the résumé du soir is on: all by default. */
  evening: ReadonlySet<EveningKind>;
}

export const DEFAULT_PREFS: Prefs = { off: new Set(), quietHours: true, evening: new Set(EVENING_KINDS) };

/** The settings document as the app writes it; anything unknown keeps the default. */
export function prefsFrom(data: Record<string, unknown> | undefined): Prefs {
  if (!data) return DEFAULT_PREFS;
  return {
    off: new Set(FAMILIES.filter((f) => data[f] === false)),
    quietHours: data.quietHours !== false,
    evening: new Set(EVENING_KINDS.filter((k) => data[EVENING_KEYS[k]] !== false)),
  };
}

/** Whether [notice] may reach a member with [prefs] at all. */
export function allows(prefs: Prefs, notice: Filtered): boolean {
  const family = FAMILY_OF[notice.channel];
  if (notice.direct || family === null) return true;
  return !prefs.off.has(family);
}

function parisHour(now: Date): number {
  return Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Paris', hour: '2-digit', hourCycle: 'h23' }).format(now));
}

/** From 22 h to 8 h in Paris. */
export function isQuietHour(now: Date): boolean {
  const hour = parisHour(now);
  return hour >= 22 || hour < 8;
}

/** The résumé du soir leaves from this hour in Paris (récit 70). */
export const EVENING_HOUR = 19;

/** From 8 h to 19 h in Paris: the small news then wait for the résumé du soir. */
export function isEveningWait(now: Date): boolean {
  const hour = parisHour(now);
  return hour >= 8 && hour < EVENING_HOUR;
}

/**
 * The kind of small news [notice] is, or null when it always goes at once: a planning event, a
 * coup de main, a Bonne nouvelle, and whatever aims at the member directly.
 */
export function eveningKind(notice: Filtered): EveningKind | null {
  if (notice.direct) return null;
  if (notice.reaction) return 'reactions';
  switch (notice.channel) {
    case 'comments': return 'comments';
    case 'weekly': return 'weekly';
    // The souvenirs and the récaps of the week or of the month.
    case 'souvenirs': case 'recap': return 'souvenirs';
    default: return null;
  }
}

/** Whether the module « Résumé du soir » is on (Administration > Modules). */
export async function eveningDigestOn(db: Firestore): Promise<boolean> {
  const features = (await db.collection('config').doc('app').get()).get('features') as Record<string, boolean> | undefined;
  return features?.eveningDigest === true;
}

export async function loadPrefs(db: Firestore, memberIds: string[]): Promise<Map<string, Prefs>> {
  const prefs = new Map<string, Prefs>();
  if (memberIds.length === 0) return prefs;
  const docs = await db.getAll(...memberIds.map((id) => db.doc(`members/${id}/settings/notifications`)));
  docs.forEach((doc, i) => prefs.set(memberIds[i], prefsFrom(doc.data())));
  return prefs;
}

/**
 * Who gets [notice] now, who gets it at 8 h, and who at 19 h ([evening]: the résumé du soir is on
 * and it is its time to wait); the others turned it off.
 */
export async function audience(db: Firestore, memberIds: string[], notice: Filtered, now: Date, evening = false): Promise<{ now: string[]; later: string[]; tonight: string[] }> {
  const prefs = await loadPrefs(db, memberIds);
  // What aims at the member directly rings even at night: a « rejoins-moi » cannot wait for 8 h.
  const quiet = isQuietHour(now) && !notice.direct && FAMILY_OF[notice.channel] !== null;
  const kind = evening ? eveningKind(notice) : null;
  const result = { now: [] as string[], later: [] as string[], tonight: [] as string[] };
  for (const id of memberIds) {
    const p = prefs.get(id) ?? DEFAULT_PREFS;
    if (!allows(p, notice)) continue;
    if (quiet && p.quietHours) result.later.push(id);
    else if (kind !== null && p.evening.has(kind)) result.tonight.push(id);
    else result.now.push(id);
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

/**
 * The clock of [deliver] when no `now` is given: the real time in production. The tests fix it to
 * the afternoon (test/setup.ts), so a suite run at night is not held by « Calme la nuit ».
 */
let clock: () => Date = () => new Date();

/** The time a notice takes when it is not given one: see [setClockForTests]. */
export function noticeTime(): Date {
  return clock();
}

/** Tests only: the time [deliver] takes when it is not given one. */
export function setClockForTests(next: () => Date): void {
  clock = next;
}

/** The single way out for a notice: filtered by each member's settings, held during quiet hours. */
export async function deliver<N extends Filtered>(db: Firestore, send: AnySender<N>, memberIds: string[], notice: N, now: Date = noticeTime()): Promise<number> {
  // The module is read only for a small news in the day, so the other notices cost nothing more.
  // A failed read never stops a notice: it then goes as usual.
  let evening = false;
  if (eveningKind(notice) !== null && isEveningWait(now)) {
    try {
      evening = await eveningDigestOn(db);
    } catch (e) {
      console.error('eveningDigest: read failed', e);
    }
  }
  const { now: ready, later, tonight } = await audience(db, memberIds, notice, now, evening);
  if (later.length > 0) {
    // Firestore refuses undefined fields: the notice is kept as plain JSON.
    const item = { ...JSON.parse(JSON.stringify(notice)), heldAt: now.toISOString() };
    const batch = db.batch();
    for (const id of later) batch.set(db.collection('quietQueue').doc(id), { items: FieldValue.arrayUnion(item) }, { merge: true });
    await batch.commit();
  }
  if (tonight.length > 0) {
    const item = { ...JSON.parse(JSON.stringify(notice)), heldAt: now.toISOString() };
    const batch = db.batch();
    for (const id of tonight) batch.set(db.collection(EVENING_QUEUE).doc(id), { items: FieldValue.arrayUnion(item) }, { merge: true });
    await batch.commit();
  }
  // What was held above (night, résumé du soir) is not counted: it was not sent.
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
  // The good news of the admin principal (the photo of the meal) rings at once.
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

/** Aimed at me and waiting for my answer: the Planning (a car) and Entraide (a turn, a loan, a coup de main). */
export function asksAnswer(notice: Filtered): boolean {
  return notice.direct === true && (notice.channel === 'events' || notice.channel === 'helpRequests');
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
  // A notice that asks me something (« C'est ton tour », a passenger, a loan) is never lost in the
  // group: it comes first, and the group says that an answer is waiting.
  const asks = shared.filter(asksAnswer);
  const titles = [...new Set([...asks, ...shared.filter((n) => !asksAnswer(n))].map((n) => n.title))].join(' · ');
  const body = `${asks.length > 0 ? 'Une demande attend ta réponse. ' : ''}${shared.length} notifications : ${titles}`;
  // With a demand inside, the group is aimed at me too: it rings at night as the demand alone would.
  const one = { channel, title: heading, body: body.length > MAX_BODY ? `${body.slice(0, MAX_BODY - 1)}…` : body, ...(asks.length > 0 ? { direct: true } : {}) } as N;
  return [one, ...surprises];
}

/**
 * From arrivalReminder: for each member who has a free place again, what waited goes out grouped,
 * taking one place. The night waits for 8 h, as in [deliver]; what reached none of the phones waits for the next run, for a day at most.
 */
export async function sendBurstHeld<N extends Filtered & { title: string; body: string; tag?: string }>(db: Firestore, send: AnySender<N>, now: Date): Promise<number> {
  const queues = await db.collection(BURST_QUEUE).get();
  if (queues.empty) return 0;
  let sent = 0;
  for (const queue of queues.docs) {
    const prefs = (await loadPrefs(db, [queue.id])).get(queue.id) ?? DEFAULT_PREFS;
    const waits = (notice: N): 'quietQueue' | null => {
      if (isQuietHour(now) && prefs.quietHours && !notice.direct && FAMILY_OF[notice.channel] !== null) return 'quietQueue';
      return null;
    };
    const group = (items: (N & { heldAt?: string })[]) => burstNotices(items
      .filter((n) => !n.heldAt || now.getTime() - Date.parse(n.heldAt) < MAX_HELD_MS)
      .map(({ heldAt: _heldAt, ...n }) => n as unknown as N));
    // A place of the hour is taken only when something rings now (note C1 de #295); still at the
    // cap, everything waits for the next run.
    const ringing = group((queue.get('items') as (N & { heldAt?: string })[] | undefined) ?? []).some((n) => waits(n) === null);
    if (ringing && !(await takeSlot(db, queue.id, now))) continue;
    const items = await db.runTransaction(async (tx) => {
      const doc = await tx.get(queue.ref);
      tx.delete(queue.ref);
      return ((doc.get('items') as (N & { heldAt?: string })[] | undefined) ?? []);
    });
    const again: unknown[] = [];
    for (const notice of group(items)) {
      const item = { ...JSON.parse(JSON.stringify(notice)), heldAt: now.toISOString() };
      const queueName = waits(notice);
      if (queueName !== null) {
        await db.collection(queueName).doc(queue.id).set({ items: FieldValue.arrayUnion(item) }, { merge: true });
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
export async function sendHeldNotices<N extends Filtered & { title: string; body: string; tag?: string }>(db: Firestore, send: AnySender<N>, now: Date): Promise<number> {
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
    // One notice for the whole night, a surprise apart on its own channel (no avalanche at 8 h):
    // the shared items go together, each private channel by itself.
    // A message of the admin principal (récit 69) goes alone too, whole, so its tap opens it.
    const alone = (n: N) => n.channel === 'admin';
    const shared = items.filter((n) => !PRIVATE_CHANNELS.has(n.channel) && !alone(n));
    const groups: (N & { heldAt?: string })[][] = shared.length > 0 ? [shared] : [];
    for (const channel of new Set(items.map((n) => n.channel).filter((c) => PRIVATE_CHANNELS.has(c)))) {
      groups.push(items.filter((n) => n.channel === channel));
    }
    for (const n of items.filter(alone)) groups.push([n]);
    const again: (N & { heldAt?: string })[] = [];
    for (const group of groups) {
      const [notice] = burstNotices(group.map(({ heldAt: _heldAt, ...n }) => n as unknown as N), 'Cette nuit dans Familia');
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

const EVENING_QUEUE = 'eveningQueue';

/**
 * From 19 h, the small news that waited go out as one notice per member, « Ce soir dans Familia »
 * (récit 70). Run by the task of every 30 minutes. What is still there at night (a failed send)
 * waits for 8 h with the night, for a member who keeps « Calme la nuit ». A notice that reached
 * none of the member's phones is tried again by the next run, for a day at most. Returns how many
 * phones got something.
 */
export async function sendEveningHeld<N extends Filtered & { title: string; body: string; tag?: string }>(db: Firestore, send: AnySender<N>, now: Date): Promise<number> {
  if (parisHour(now) < EVENING_HOUR && !isQuietHour(now)) return 0;
  const queues = await db.collection(EVENING_QUEUE).get();
  if (queues.empty) return 0;
  let sent = 0;
  for (const queue of queues.docs) {
    // Taken and emptied at once, so two runs never send the same evening twice.
    const items = await db.runTransaction(async (tx) => {
      const doc = await tx.get(queue.ref);
      tx.delete(queue.ref);
      return ((doc.get('items') as (N & { heldAt?: string })[] | undefined) ?? []);
    });
    const fresh = items.filter((n) => !n.heldAt || now.getTime() - Date.parse(n.heldAt) < MAX_HELD_MS);
    if (fresh.length === 0) continue;
    const prefs = (await loadPrefs(db, [queue.id])).get(queue.id) ?? DEFAULT_PREFS;
    if (isQuietHour(now) && prefs.quietHours) {
      await db.collection('quietQueue').doc(queue.id).set({ items: FieldValue.arrayUnion(...fresh) }, { merge: true });
      continue;
    }
    let failed = false;
    for (const notice of burstNotices(fresh.map(({ heldAt: _heldAt, ...n }) => n as unknown as N), 'Ce soir dans Familia')) {
      let result = { sent: 0, failed: 0 };
      try {
        result = await sendCounting(db, send, [queue.id], notice);
      } catch (e) {
        console.error('eveningQueue: send failed', queue.id, e);
        result = { sent: 0, failed: 1 };
      }
      sent += result.sent;
      if (result.sent === 0 && result.failed > 0) failed = true;
    }
    if (failed) await queue.ref.set({ items: FieldValue.arrayUnion(...fresh) }, { merge: true });
  }
  return sent;
}

/** A member who leaves takes their held notices with them. */
export async function deleteQuietQueueOf(db: Firestore, memberId: string): Promise<void> {
  await db.collection('quietQueue').doc(memberId).delete();
  await db.collection(EVENING_QUEUE).doc(memberId).delete();
  // What the Mode présentation (removed on 18/11) may have left behind.
  await db.collection('presentationQueue').doc(memberId).delete();
  await db.collection(BURST_QUEUE).doc(memberId).delete();
  await db.collection(NOTIFY_LOG).doc(memberId).delete();
}
