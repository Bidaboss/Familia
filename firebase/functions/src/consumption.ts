// Récit 1 sexies: once a day, the use of the Firestore database is read from Cloud Monitoring,
// kept in consumption/{day} for the « Consommation » card of Administration, and the admin
// principal is warned at 80 % of a free limit or when the estimated cost of the month passes
// 0,50 €. The Google budget alert at 1 € by e-mail stays as the safety net.

import type { Firestore } from 'firebase-admin/firestore';
import { addDays, Notice } from './logic';
import { notify, Sender } from './store';

export type Limit = 'reads' | 'writes' | 'deletes' | 'storage';

/** The free quotas of Firestore: per day for the operations, stored for the size. */
export const FREE: Record<Limit, number> = {
  reads: 50_000,
  writes: 20_000,
  deletes: 20_000,
  storage: 1024 * 1024 * 1024,
};

/** PRD: 40 000 reads, 16 000 writes or deletes a day, or 800 Mo of database. */
export const ALERT_AT: Record<Limit, number> = {
  reads: 40_000,
  writes: 16_000,
  deletes: 16_000,
  storage: 800 * 1024 * 1024,
};

export const COST_ALERT_EUROS = 0.5;

/**
 * Prices beyond the free quotas, taken high on purpose (multi-region prices, a dollar counted as
 * a euro), so the estimate errs on the safe side: per 100 000 operations, and per Gio a month.
 */
export const PRICE = { reads: 0.06, writes: 0.18, deletes: 0.02, storagePerGiBMonth: 0.18 };

export interface DayUse {
  day: string;
  reads: number;
  writes: number;
  deletes: number;
  /** Null when Monitoring has no figure for the size. */
  storageBytes: number | null;
}

/** What the day costs beyond the free quotas, with its share of the month for the size. */
export function dayCost(use: DayUse): number {
  const over = (n: number, free: number) => Math.max(0, n - free);
  const storageGiB = over(use.storageBytes ?? 0, FREE.storage) / FREE.storage;
  return (
    (over(use.reads, FREE.reads) / 100_000) * PRICE.reads +
    (over(use.writes, FREE.writes) / 100_000) * PRICE.writes +
    (over(use.deletes, FREE.deletes) / 100_000) * PRICE.deletes +
    (storageGiB * PRICE.storagePerGiBMonth) / 30
  );
}

/** The days of [month] (AAAA-MM) among [days], added up. */
export function monthCost(days: DayUse[], month: string): number {
  return days.filter((d) => d.day.startsWith(month)).reduce((sum, d) => sum + dayCost(d), 0);
}

/** The limits reached by [use] at 80 %, and « cost » when the month passes 0,50 €. */
export function alerts(use: DayUse, cost: number): (Limit | 'cost')[] {
  const found: (Limit | 'cost')[] = [];
  if (use.reads >= ALERT_AT.reads) found.push('reads');
  if (use.writes >= ALERT_AT.writes) found.push('writes');
  if (use.deletes >= ALERT_AT.deletes) found.push('deletes');
  if ((use.storageBytes ?? 0) >= ALERT_AT.storage) found.push('storage');
  if (cost > COST_ALERT_EUROS) found.push('cost');
  return found;
}

const number = (n: number) => new Intl.NumberFormat('fr-FR').format(Math.round(n));
const euros = (n: number) => new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(n);
const megabytes = (n: number) => `${number(n / (1024 * 1024))} Mo`;

/** One notification per limit, in the words of the admin principal's phone. */
export function alertNotice(alert: Limit | 'cost', use: DayUse, cost: number): Notice {
  const share = (n: number, free: number) => `${Math.round((100 * n) / free)} %`;
  const title = 'Familia approche d\'une limite gratuite';
  switch (alert) {
    case 'reads':
      return { channel: 'admin', title, body: `Lectures hier : ${number(use.reads)} sur ${number(FREE.reads)} gratuites (${share(use.reads, FREE.reads)}).` };
    case 'writes':
      return { channel: 'admin', title, body: `Écritures hier : ${number(use.writes)} sur ${number(FREE.writes)} gratuites (${share(use.writes, FREE.writes)}).` };
    case 'deletes':
      return { channel: 'admin', title, body: `Suppressions hier : ${number(use.deletes)} sur ${number(FREE.deletes)} gratuites (${share(use.deletes, FREE.deletes)}).` };
    case 'storage': {
      const bytes = use.storageBytes ?? 0;
      return { channel: 'admin', title, body: `Taille de la base : ${megabytes(bytes)} sur 1 Go gratuit (${share(bytes, FREE.storage)}).` };
    }
    case 'cost':
      return { channel: 'admin', title: 'Familia commence à coûter', body: `Coût estimé du mois : ${euros(cost)}. L'alerte de Google arrive à 1 €.` };
  }
}

// --- Cloud Monitoring ------------------------------------------------------------------------------

/** The metric types used, picked among those Monitoring lists for the project. */
export interface Metrics {
  reads?: string;
  writes?: string;
  deletes?: string;
  storage?: string;
}

/**
 * Firestore has renamed its metrics over time (document/read_count, then document/read_ops_count):
 * the newest name present wins, so the function keeps working whichever the project has.
 */
export function pickMetrics(types: string[]): Metrics {
  const has = new Set(types);
  const first = (...names: string[]) => names.map((n) => `firestore.googleapis.com/${n}`).find((n) => has.has(n));
  return {
    reads: first('document/read_ops_count', 'document/read_count'),
    writes: first('document/write_ops_count', 'document/write_count'),
    deletes: first('document/delete_ops_count', 'document/delete_count'),
    storage: types.find((t) => t.startsWith('firestore.googleapis.com/') && /storage/.test(t) && /bytes/.test(t)),
  };
}

/** Reads Cloud Monitoring; the tests replace it with figures. */
export interface MonitoringReader {
  metricTypes(): Promise<string[]>;
  /** The sum of a counter between [start] and [end], or the largest value of a gauge. */
  total(metricType: string, start: Date, end: Date, gauge: boolean): Promise<number | null>;
}

/** The instant a Paris day starts: midnight is 23 h or 22 h UTC the day before. */
export function parisMidnight(day: string): Date {
  const [y, m, d] = day.split('-').map(Number);
  for (const hours of [1, 2, 0]) {
    const candidate = new Date(Date.UTC(y, m - 1, d) - hours * 3_600_000);
    const hour = new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Paris', hour: '2-digit', hourCycle: 'h23' }).format(candidate);
    if (hour === '00') return candidate;
  }
  return new Date(Date.UTC(y, m - 1, d));
}

/**
 * Reads the use of [day] (the day before, normally), keeps it in consumption/{day} with the
 * estimated cost of its month, and warns the admin principal. Without the « Lecteur Monitoring »
 * role, the day is kept with error: 'monitoring', which the card explains.
 */
export async function checkConsumption(db: Firestore, monitoring: MonitoringReader, send: Sender, day: string): Promise<(Limit | 'cost')[]> {
  const ref = db.collection('consumption').doc(day);
  // A retried run does not warn twice for the same day.
  const already = new Set<string>(((await ref.get()).get('alerts') as string[] | undefined) ?? []);
  let use: DayUse;
  try {
    const metrics = pickMetrics(await monitoring.metricTypes());
    const start = parisMidnight(day);
    const end = parisMidnight(addDays(day, 1));
    const read = async (type: string | undefined, gauge = false) => (type ? monitoring.total(type, start, end, gauge) : null);
    use = {
      day,
      reads: (await read(metrics.reads)) ?? 0,
      writes: (await read(metrics.writes)) ?? 0,
      deletes: (await read(metrics.deletes)) ?? 0,
      storageBytes: await read(metrics.storage, true),
    };
  } catch {
    await ref.set({ day, error: 'monitoring', checkedAt: new Date() });
    return [];
  }
  const month = day.slice(0, 7);
  const earlier = await db.collection('consumption').where('day', '>=', `${month}-01`).where('day', '<', day).get();
  const days: DayUse[] = earlier.docs
    .filter((doc) => !doc.get('error'))
    .map((doc) => ({
      day: doc.get('day'),
      reads: doc.get('reads') ?? 0,
      writes: doc.get('writes') ?? 0,
      deletes: doc.get('deletes') ?? 0,
      storageBytes: doc.get('storageBytes') ?? null,
    }));
  const cost = monthCost([...days, use], month);
  const found = alerts(use, cost);
  await ref.set({ ...use, monthCost: cost, alerts: found, checkedAt: new Date() });
  const ownerId = (await db.collection('config').doc('family').get()).get('ownerMemberId') as string | undefined;
  if (ownerId) {
    for (const alert of found.filter((a) => !already.has(a))) await notify(db, send, [ownerId], alertNotice(alert, use, cost));
  }
  return found;
}
