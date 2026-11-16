// Question de la semaine (PRD, récit 37): each Monday at 9 h, the server publishes one question
// from the bank. The pure part picks it; the store part writes it, tells the family, keeps the
// number of answers on the question (the home card shows « 6 réponses », never who, carte
// EXPERT-3) and removes the answers of a member who leaves.

import type { Firestore } from 'firebase-admin/firestore';
import { addDays, Notice, parisDay } from './logic';
import { BankQuestion, QUESTION_BANK } from './questionBank';
import { digestOn, queueDigest } from './digest';
import { notify, Sender } from './store';

/** The first question comes out on Monday 30 November 2026 (PRD rév. 174). */
export const FIRST_DAY = '2026-11-30';
/** And it is the first of the bank, the one the family will remember. */
export const FIRST_QUESTION = 'q01';
/** Everyone reads the answers 48 hours after the question, even without answering. */
export const REVEAL_AFTER_MS = 48 * 3600 * 1000;
/** A question of the bank comes back once it has not been asked for this long. */
const REUSE_AFTER_DAYS = 330;
/** The gRPC code Firestore gives when create() meets an existing document. */
const ALREADY_EXISTS = 6;

/** A small stable hash, so a given Monday always draws the same question (the tests rely on it). */
export function daySeed(day: string): number {
  let h = 2166136261;
  for (const c of day) h = Math.imul(h ^ c.charCodeAt(0), 16777619) >>> 0;
  return h;
}

/** True when a seasonal question may come out on [day] (AAAA-MM-JJ); always true otherwise. */
export function inSeason(q: BankQuestion, day: string): boolean {
  if (!q.season) return true;
  const [, m, d] = day.split('-').map(Number);
  return q.season.months.includes(m) && d >= (q.season.fromDay ?? 1) && d <= (q.season.toDay ?? 31);
}

/**
 * The question of [day]. A seasonal question in season goes first; otherwise a question drawn
 * among those not asked lately ([recent] holds their bank ids). Null once everything was asked.
 */
export function pickQuestion(bank: BankQuestion[], recent: Set<string>, day: string): BankQuestion | null {
  const fresh = bank.filter((q) => !recent.has(q.id) && inSeason(q, day));
  if (fresh.length === 0) return null;
  if (recent.size === 0) {
    const first = fresh.find((q) => q.id === FIRST_QUESTION);
    if (first) return first;
  }
  const seasonal = fresh.filter((q) => q.season);
  const pool = seasonal.length > 0 ? seasonal : fresh;
  return pool[daySeed(day) % pool.length];
}

export function questionNotice(weekId: string, text: string): Notice {
  return { channel: 'weekly', title: 'La question de la semaine', body: text, weeklyQuestionId: weekId };
}

// --- Firestore -----------------------------------------------------------------------------------

/** Modules switched on in Administration (config/app.features). */
async function isModuleOn(db: Firestore): Promise<boolean> {
  const config = await db.doc('config/app').get();
  return config.get('features.weeklyQuestion') === true;
}

/** Who is told: every member with the app, except those who turned it off. */
async function audience(db: Firestore): Promise<string[]> {
  const members = await db.collection('members').get();
  const withApp = members.docs.filter((m) => m.get('profile') !== 'childWithoutApp').map((m) => m.id);
  if (withApp.length === 0) return [];
  const settings = await db.getAll(...withApp.map((id) => db.doc(`members/${id}/settings/notifications`)));
  const muted = new Set(settings.filter((s) => s.exists && s.get('weekly') === false).map((s) => s.ref.parent.parent!.id));
  return withApp.filter((id) => !muted.has(id)).sort();
}

/** The weekday (0 for Sunday) and the hour in Paris. */
export function parisWeekdayHour(now: Date): { weekday: number; hour: number } {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Paris', weekday: 'short', hour: '2-digit', hourCycle: 'h23' })
    .formatToParts(now);
  const weekday = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].indexOf(parts.find((p) => p.type === 'weekday')!.value);
  return { weekday, hour: Number(parts.find((p) => p.type === 'hour')!.value) };
}

/**
 * Monday from 9 h (the task of every 30 minutes calls it, to keep the scheduled tasks few):
 * publishes weeklyQuestions/{AAAA-MM-JJ} and tells the family. Does nothing before
 * [FIRST_DAY], while the module is off, or when this Monday already has its question. Returns
 * the id of the question published, or null.
 */
export async function publishWeeklyQuestion(db: Firestore, send: Sender, now: Date): Promise<string | null> {
  const { weekday, hour } = parisWeekdayHour(now);
  if (weekday !== 1 || hour < 9) return null;
  const weekId = parisDay(now);
  if (weekId < FIRST_DAY || !(await isModuleOn(db))) return null;
  const ref = db.collection('weeklyQuestions').doc(weekId);
  if ((await ref.get()).exists) return null;

  const since = addDays(weekId, -REUSE_AFTER_DAYS);
  const past = await db.collection('weeklyQuestions').where('weekId', '>=', since).get();
  const recent = new Set(past.docs.map((d) => String(d.get('bankId') ?? '')));
  const question = pickQuestion(QUESTION_BANK, recent, weekId);
  if (!question) return null;

  // create() fails if the question exists: two close runs never tell the family twice.
  const created = await ref.create({
    weekId,
    text: question.text,
    bankId: question.id,
    proposedByName: null,
    publishedAt: now,
    revealAt: new Date(now.getTime() + REVEAL_AFTER_MS),
    answerCount: 0,
  }).then(() => true, (e: { code?: number }) => {
    if (e.code === ALREADY_EXISTS) return false;
    throw e;
  });
  if (!created) return null;
  const told = await audience(db);
  // With the résumé of the day (AVIS-14), the question waits for it; otherwise it is told at once.
  if (await digestOn(db)) await queueDigest(db, told, { kind: 'weekly', weekId }, now);
  else await notify(db, send, told, questionNotice(weekId, question.text));
  return weekId;
}

/** An answer was written or removed: the question keeps the number of real answers (not « Passer »). */
export async function countAnswers(db: Firestore, weekId: string): Promise<number> {
  const ref = db.collection('weeklyQuestions').doc(weekId);
  const answers = await ref.collection('answers').where('skipped', '==', false).count().get();
  const n = answers.data().count;
  if ((await ref.get()).exists) await ref.update({ answerCount: n });
  return n;
}

/** A member removed from the family takes their answers with them (carte EXPERT-1). */
export async function deleteAnswersOf(db: Firestore, memberId: string): Promise<number> {
  const questions = await db.collection('weeklyQuestions').get();
  if (questions.empty) return 0;
  const answers = await db.getAll(...questions.docs.map((q) => q.ref.collection('answers').doc(memberId)));
  const mine = answers.filter((a) => a.exists);
  const batch = db.batch();
  mine.forEach((a) => batch.delete(a.ref));
  await batch.commit();
  return mine.length;
}

type AnswerPhoto = { photoId?: string | null } | undefined;

/** The photo of an answer: weeklyQuestions/{weekId}/{memberId}/{photoId}.jpg and its thumbnail. */
export function answerPhotoPaths(weekId: string, memberId: string, photoId: string): string[] {
  const folder = `weeklyQuestions/${weekId}/${memberId}`;
  return [`${folder}/${photoId}.jpg`, `${folder}/${photoId}-thumb.jpg`];
}

/**
 * An answer dropped or changed its photo, or was removed (also when its member leaves): the old
 * photo goes, so Storage keeps only the photos that an answer names.
 */
export async function removeOldAnswerPhoto(
  remove: (paths: string[]) => Promise<void>, weekId: string, memberId: string, before: AnswerPhoto, after: AnswerPhoto,
): Promise<boolean> {
  const old = before?.photoId;
  if (!old || old === after?.photoId) return false;
  await remove(answerPhotoPaths(weekId, memberId, old));
  return true;
}
