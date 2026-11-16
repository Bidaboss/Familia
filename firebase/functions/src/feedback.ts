// « Une idée ? Un souci ? » (PRD, récit 6 sexies): feedback/{feedbackId}. Each new one tells the
// admin principal, unless they wrote it themselves; each new state tells its author (« Prévue »,
// « Faite », « Pas retenue »), unless the author is the admin principal. No module switch: it is
// there for the 21/11. Data model: docs/data-model.md.

import { Firestore } from 'firebase-admin/firestore';
import { Notice } from './logic';
import { notify, Sender } from './store';

export type FeedbackStatus = 'new' | 'planned' | 'done' | 'declined';

export interface FeedbackData {
  kind: 'idea' | 'problem';
  text: string;
  authorId: string;
  authorName: string;
  status?: FeedbackStatus;
  note?: string | null;
}

const PREVIEW = 120;

function preview(text: string): string {
  const line = text.trim().replace(/\s+/g, ' ');
  return line.length > PREVIEW ? `${line.slice(0, PREVIEW - 1)}…` : line;
}

/** To the admin principal: « Idée de Camille » or « Souci signalé par Camille », then the text. */
export function feedbackNotice(feedback: FeedbackData): Notice {
  const title = feedback.kind === 'problem' ? `Souci signalé par ${feedback.authorName}` : `Idée de ${feedback.authorName}`;
  return { channel: 'admin', title, body: preview(feedback.text), feedback: 'list' };
}

const STATUS_TITLE: Record<Exclude<FeedbackStatus, 'new'>, string> = {
  planned: 'Ta demande est prévue',
  done: 'Ta demande est faite',
  declined: 'Ta demande n\'est pas retenue',
};

/** To the author, when the state changes: the word of the admin principal, or the request itself. */
export function statusNotice(feedback: FeedbackData): Notice | null {
  const status = feedback.status ?? 'new';
  if (status === 'new') return null;
  const note = typeof feedback.note === 'string' && feedback.note.trim() !== '' ? feedback.note : null;
  return { channel: 'feedback', title: STATUS_TITLE[status], body: preview(note ?? feedback.text), feedback: 'mine' };
}

async function ownerId(db: Firestore): Promise<string | null> {
  const owner = (await db.doc('config/family').get()).get('ownerMemberId');
  return typeof owner === 'string' ? owner : null;
}

export async function onFeedbackCreated(db: Firestore, send: Sender, feedback: FeedbackData): Promise<number> {
  const owner = await ownerId(db);
  if (owner === null || owner === feedback.authorId) return 0;
  return notify(db, send, [owner], feedbackNotice(feedback));
}

export async function onFeedbackChanged(db: Firestore, send: Sender, before: FeedbackData, after: FeedbackData): Promise<number> {
  if ((before.status ?? 'new') === (after.status ?? 'new')) return 0;
  const notice = statusNotice(after);
  if (notice === null) return 0;
  if ((await ownerId(db)) === after.authorId) return 0;
  return notify(db, send, [after.authorId], notice);
}
