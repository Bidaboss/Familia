// « Plantages » (PRD, récit 6 sexies, rév. 220): crashReports/{reportId}, sent by the app at the
// launch after a crash. The admin principal is told of the first report of a new kind (same
// signature), at most once a day, through deliver(). No scheduled task. Data model: docs/data-model.md.

import { Firestore } from 'firebase-admin/firestore';
import { Notice, parisDay } from './logic';
import { deliver } from './prefs';
import { Sender } from './store';

export interface CrashData {
  memberId: string;
  appVersion: string;
  screen: string;
  trace: string;
  signature: string;
}

/** « IllegalStateException », from the first line of the trace (class names only, no message). */
export function errorName(trace: string): string {
  const first = trace.split('\n', 1)[0].trim();
  const name = first.split(/[.$]/).pop() ?? '';
  return name === '' ? 'Erreur' : name.slice(0, 60);
}

/** To the admin principal: what crashed, on which screen and version. Never a name, never a text. */
export function crashNotice(crash: CrashData): Notice {
  const where = crash.screen.trim() === '' ? '' : ` sur l'écran ${crash.screen.trim()}`;
  return {
    channel: 'admin',
    title: 'Nouveau plantage',
    body: `${errorName(crash.trace)}${where}, version ${crash.appVersion}. À voir dans Administration > Plantages.`,
    feedback: 'crashes',
  };
}

async function ownerId(db: Firestore): Promise<string | null> {
  const owner = (await db.doc('config/family').get()).get('ownerMemberId');
  return typeof owner === 'string' ? owner : null;
}

/** True when this report is the only one of its kind (the others were settled, or never came). */
async function isNewKind(db: Firestore, reportId: string, signature: string): Promise<boolean> {
  const same = await db.collection('crashReports').where('signature', '==', signature).limit(2).get();
  return same.docs.every((d) => d.id === reportId);
}

/** Takes today's notice if it is still free: one a day at most, even with two crashes at once. */
async function takeTodaysNotice(db: Firestore, now: Date): Promise<boolean> {
  const ref = db.doc('crashNotices/state');
  const today = parisDay(now);
  return db.runTransaction(async (tx) => {
    if ((await tx.get(ref)).get('lastDay') === today) return false;
    tx.set(ref, { lastDay: today });
    return true;
  });
}

export async function onCrashReported(db: Firestore, send: Sender, reportId: string, crash: CrashData, now: Date = new Date()): Promise<number> {
  if (typeof crash.signature !== 'string' || typeof crash.trace !== 'string') return 0;
  const owner = await ownerId(db);
  if (owner === null) return 0;
  if (!(await isNewKind(db, reportId, crash.signature))) return 0;
  if (!(await takeTodaysNotice(db, now))) return 0;
  return deliver(db, send, [owner], crashNotice(crash), now);
}
