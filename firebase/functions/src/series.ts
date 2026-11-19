// « Événements qui reviennent » (PRD, récit 54), behind the switch recurringEvents. A series
// (eventSeries/{seriesId}) says what repeats and how; the server writes its occurrences as ordinary
// events, events/{seriesId}_{AAAAMMJJ}, for the 12 months to come. Each occurrence keeps its own
// answers, « Qui apporte quoi », cars and comments, and the reminder of the day before works as for
// any event. No new scheduled task: the evening reminder also tops the series up (topUpSeries).
//
// The pure part computes the days; the store part writes the occurrences. A server write carries a
// new `bySeries` mark, so onEventCreated and onEventUpdated stay quiet: one notice for the whole
// series is sent instead. An occurrence taken out is marked `cancelledBySeries` before it goes, so
// only those who were coming hear of it.

import { FieldValue, type DocumentReference, type Firestore } from 'firebase-admin/firestore';
import type { EventData } from './logic';
import { addDays } from './logic';

export const RECURRING_EVENTS = 'recurringEvents';
/** How far ahead occurrences are written: the planning shows 12 months. */
export const SERIES_DAYS_AHEAD = 365;

export interface SeriesRule {
  freq: 'weekly' | 'monthly' | 'yearly';
  interval: number;
  monthlyBy: 'date' | 'weekday' | null;
  start: string;
  until: string | null;
  count: number | null;
}

export interface SeriesData {
  title: string;
  time: string | null;
  place: string | null;
  note: string | null;
  targetType: string;
  targetMemberId: string | null;
  householdId: string | null;
  placeKind?: string | null;
  placeHouseholdId?: string | null;
  /** The members named « @Prénom » in the note (Kévin, 19/11), copied to each day. */
  mentionIds?: string[];
  authorId: string;
  authorName: string;
  rule: SeriesRule;
  exdates: string[];
  applyFrom: string | null;
}

const toDate = (day: string) => new Date(`${day}T00:00:00Z`);
const toDay = (d: Date) => d.toISOString().slice(0, 10);
const daysIn = (y: number, m: number) => new Date(Date.UTC(y, m + 1, 0)).getUTCDate();

/** The [n]th [weekday] (0 Sunday) of month [m] of [y], or null when the month has no such day (a 5th). */
function nthWeekday(y: number, m: number, weekday: number, n: number): Date | null {
  const first = new Date(Date.UTC(y, m, 1)).getUTCDay();
  const day = 1 + ((weekday - first + 7) % 7) + (n - 1) * 7;
  return day <= daysIn(y, m) ? new Date(Date.UTC(y, m, day)) : null;
}

/**
 * The days of [rule] in order, from its start, as RFC 5545 counts them: a 31 or a 29 February only
 * where it exists, a 5th Sunday only in the months that have one. [count] counts them all, even
 * those taken out ([exdates] only hides them). Stops after [to].
 */
export function ruleDays(rule: SeriesRule, to: string): string[] {
  const start = toDate(rule.start);
  const last = rule.until && rule.until < to ? rule.until : to;
  const out: string[] = [];
  const y0 = start.getUTCFullYear();
  const m0 = start.getUTCMonth();
  const d0 = start.getUTCDate();
  const weekday = start.getUTCDay();
  const nth = Math.ceil(d0 / 7);
  for (let k = 0; k < 2000; k++) {
    let next: Date | null;
    if (rule.freq === 'weekly') {
      next = new Date(start.getTime() + k * 7 * rule.interval * 86_400_000);
    } else if (rule.freq === 'monthly') {
      const y = y0 + Math.floor((m0 + k) / 12);
      const m = (m0 + k) % 12;
      next = rule.monthlyBy === 'weekday' ? nthWeekday(y, m, weekday, nth) : (d0 <= daysIn(y, m) ? new Date(Date.UTC(y, m, d0)) : null);
    } else {
      const y = y0 + k;
      next = d0 <= daysIn(y, m0) ? new Date(Date.UTC(y, m0, d0)) : null;
    }
    if (next === null) continue;
    const day = toDay(next);
    if (day > last) break;
    out.push(day);
    if (rule.count !== null && out.length >= rule.count) break;
  }
  return out;
}

/** The days to have as events between [from] and [to], without those taken out. */
export function occurrenceDays(rule: SeriesRule, exdates: string[], from: string, to: string): string[] {
  const out = new Set(exdates);
  return ruleDays(rule, to).filter((d) => d >= from && !out.has(d));
}

export const occurrenceId = (seriesId: string, day: string) => `${seriesId}_${day.replace(/-/g, '')}`;

/** « chaque semaine », « toutes les 2 semaines », « chaque mois », « chaque année »: for the notice. */
export function ruleLabel(rule: SeriesRule): string {
  if (rule.freq === 'weekly') return rule.interval === 2 ? 'toutes les 2 semaines' : 'chaque semaine';
  return rule.freq === 'monthly' ? 'chaque mois' : 'chaque année';
}

/** What an occurrence of [series] holds on [day], besides its marks. */
export function occurrenceContent(series: SeriesData, day: string): EventData {
  return {
    title: series.title, date: day, time: series.time, place: series.place, note: series.note,
    targetType: series.targetType, targetMemberId: series.targetMemberId, householdId: series.householdId,
    placeKind: series.placeKind ?? null, placeHouseholdId: series.placeHouseholdId ?? null,
    mentionIds: series.mentionIds ?? [],
  };
}

const CONTENT: (keyof SeriesData & keyof EventData)[] = ['title', 'time', 'place', 'note', 'targetType', 'targetMemberId', 'householdId',
  'placeKind', 'placeHouseholdId'];

/** True when an occurrence no longer says what its series says. */
export function differs(series: SeriesData, event: Record<string, unknown>): boolean {
  const named = (ids: unknown) => JSON.stringify(Array.isArray(ids) ? [...ids].sort() : []);
  return CONTENT.some((k) => (series[k] ?? null) !== (event[k] ?? null)) || named(series.mentionIds) !== named(event.mentionIds);
}

export interface SyncResult {
  created: string[];
  updated: string[];
  cancelled: string[];
}

/**
 * Brings the occurrences of [seriesId] in line with it from [today] for 12 months: writes the
 * missing days, rewrites those that changed (from [applyFrom], never one changed by hand), and takes
 * out those no longer wanted. A series gone takes all its occurrences to come with it.
 */
export async function syncSeries(db: Firestore, seriesId: string, today: string): Promise<SyncResult> {
  const result: SyncResult = { created: [], updated: [], cancelled: [] };
  const snap = await db.collection('eventSeries').doc(seriesId).get();
  const series = snap.exists ? (snap.data() as SeriesData) : null;
  const existing = await db.collection('events').where('seriesId', '==', seriesId).get();
  const coming = existing.docs.filter((d) => String(d.get('occurrence') ?? d.get('date')) >= today);
  const wanted = series ? occurrenceDays(series.rule, series.exdates ?? [], today, addDays(today, SERIES_DAYS_AHEAD)) : [];
  const wantedSet = new Set(wanted);
  const mark = Date.now();
  const batch = db.batch();
  const toDelete: DocumentReference[] = [];
  const ordered = [...coming].sort((a, b) => String(a.get('occurrence')).localeCompare(String(b.get('occurrence'))));
  const out = ordered.filter((d) => !wantedSet.has(String(d.get('occurrence'))));
  const answered = await Promise.all(out.map(async (d) =>
    (await d.ref.collection('rsvps').get()).docs.filter((r) => r.get('status') !== 'no').map((r) => r.id)));
  const told = toldBefore(answered);
  for (const d of ordered) {
    const day = String(d.get('occurrence'));
    if (!wantedSet.has(day)) {
      batch.update(d.ref, { cancelledBySeries: true, bySeries: mark, seriesTold: told[out.indexOf(d)] });
      toDelete.push(d.ref);
      result.cancelled.push(d.id);
    } else if (series && d.get('detached') !== true && (!series.applyFrom || day >= series.applyFrom) && differs(series, d.data())) {
      batch.update(d.ref, { ...occurrenceContent(series, String(d.get('date'))), date: d.get('date'), bySeries: mark, updatedAt: FieldValue.serverTimestamp() });
      result.updated.push(d.id);
    }
  }
  // A series whose author was removed keeps what it has, but writes no new day (C de la relecture de #244).
  const authorHere = series ? (await db.collection('members').doc(series.authorId).get()).exists : false;
  if (series && authorHere) {
    const have = new Set(coming.map((d) => String(d.get('occurrence'))));
    for (const day of wanted) {
      if (have.has(day)) continue;
      const id = occurrenceId(seriesId, day);
      batch.set(db.collection('events').doc(id), {
        ...occurrenceContent(series, day),
        authorId: series.authorId, authorName: series.authorName,
        seriesId, occurrence: day, bySeries: mark,
        createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
      });
      result.created.push(id);
    }
  }
  await batch.commit();
  // Taken out after the mark, so onEventDeleted sees it and only tells those who were coming.
  await Promise.all(toDelete.map((ref) => ref.delete()));
  return result;
}

/**
 * For occurrences taken out together, in order, with who answered each: who already hears of an
 * earlier one, and so is not told again (one notice each, C de la relecture de #244).
 */
export function toldBefore(answered: string[][]): string[][] {
  const seen = new Set<string>();
  return answered.map((ids) => {
    const before = [...seen];
    ids.forEach((id) => seen.add(id));
    return before;
  });
}

/** Every series, from the evening reminder: the 12 months move on by a day. */
export async function topUpSeries(db: Firestore, today: string): Promise<number> {
  const all = await db.collection('eventSeries').get();
  let created = 0;
  for (const s of all.docs) {
    // Each series on its own: one that fails leaves the others and the reminders be.
    try {
      created += (await syncSeries(db, s.id, today)).created.length;
    } catch (e) {
      console.error('syncSeries', s.id, e);
    }
  }
  return created;
}

/** A server write of a series: no notice of its own (bySeries changed, or a new occurrence). */
export function writtenBySeries(before: Record<string, unknown> | undefined, after: Record<string, unknown> | undefined): boolean {
  if (!after?.seriesId) return false;
  return !before || before.bySeries !== after.bySeries;
}

