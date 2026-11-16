// Commentaires d'un événement (PRD, récit 28): events/{eventId}/comments/{commentId}. A new comment
// tells the author of the event, those who already commented and those who said « Je viens » or
// « Peut-être », never the one who wrote it nor those who chose « Ne plus être prévenu »
// (events/{eventId}/commentMutes/{memberId}). Until the module is switched on for the family
// (config/app.features.comments), only the admin principal is told, so a try never reaches the
// family. Data model: docs/data-model.md.

import { Firestore } from 'firebase-admin/firestore';
import { Notice } from './logic';
import { comingMembers, featureOn, loadMembers, notify, Sender } from './store';

export interface CommentData {
  text: string;
  authorId: string;
  authorName: string;
}

const PREVIEW = 120;

/** « Camille a commenté « Pique-nique » » and the start of the text. */
export function commentNotice(eventId: string, eventTitle: string, comment: CommentData): Notice {
  const text = comment.text.trim().replace(/\s+/g, ' ');
  const body = text.length > PREVIEW ? `${text.slice(0, PREVIEW - 1)}…` : text;
  return { channel: 'comments', title: `${comment.authorName} a commenté « ${eventTitle} »`, body, eventId };
}

/** Who is told of [comment] on the event [eventId], before the module switch is applied. */
export async function commentRecipients(db: Firestore, eventId: string, commentId: string, comment: CommentData): Promise<string[]> {
  const eventRef = db.collection('events').doc(eventId);
  const event = await eventRef.get();
  if (!event.exists) return [];
  const members = await loadMembers(db);
  const known = new Set(members.map((m) => m.id));
  const to = new Set<string>();
  const author = event.get('authorId');
  if (typeof author === 'string') to.add(author);
  const earlier = await eventRef.collection('comments').select('authorId').get();
  earlier.docs.filter((d) => d.id !== commentId).forEach((d) => {
    const id = d.get('authorId');
    if (typeof id === 'string') to.add(id);
  });
  (await comingMembers(db, eventId, members)).forEach((id) => to.add(id));
  to.delete(comment.authorId);
  const muted = await eventRef.collection('commentMutes').select().get();
  muted.docs.forEach((d) => to.delete(d.id));
  return [...to].filter((id) => known.has(id)).sort();
}

export async function onCommentCreated(
  db: Firestore, send: Sender, eventId: string, commentId: string, comment: CommentData,
): Promise<number> {
  let to = await commentRecipients(db, eventId, commentId, comment);
  if (!(await featureOn(db, 'comments'))) {
    const owner = (await db.doc('config/family').get()).get('ownerMemberId');
    to = to.filter((id) => typeof owner === 'string' && id === owner);
  }
  if (to.length === 0) return 0;
  const title = String((await db.collection('events').doc(eventId).get()).get('title') ?? '');
  return notify(db, send, to, commentNotice(eventId, title, comment));
}

/** A deleted event: its comments and the « Ne plus être prévenu » go with it. */
export async function deleteEventComments(db: Firestore, eventId: string): Promise<void> {
  const event = db.collection('events').doc(eventId);
  await db.recursiveDelete(event.collection('comments'));
  await db.recursiveDelete(event.collection('commentMutes'));
}

/**
 * A member removed from the family: their « Ne plus être prévenu » go. Their comments stay, signed
 * with the name they had (like their events), until they expire after 12 months.
 */
export async function deleteCommentMutesOf(db: Firestore, memberId: string): Promise<void> {
  const events = await db.collection('events').select().get();
  const writer = db.bulkWriter();
  events.docs.forEach((e) => writer.delete(e.ref.collection('commentMutes').doc(memberId)));
  await writer.close();
}
