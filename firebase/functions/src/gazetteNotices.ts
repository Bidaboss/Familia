// La gazette, les notifications (brief §6, récits 117 et 119, PR 7 du brief). Grouped, never one ring
// per comment:
// - a comment on a card tells the members it names (« t'a nommé »), the author of the publication
//   (« a commenté ta publication ») and those who already commented (« a aussi commenté »);
// - a publication tells the members it names, and an edit only those it newly names.
// Only the members who see the card are told (the same as seesThread in firestore.rules): never the
// member of a card « wishes:{him} », never a trip one does not read. Never the one who wrote it, never
// a child without the app. Each notice carries the tag of its card, so a second comment replaces the
// first on the phone; deliver (prefs.ts) holds the night from 22 h to 8 h (mentions too, récit 119),
// keeps 3 rings an hour at most and groups the rest. A tap opens the card (gazetteThreadId).

import type { Firestore } from 'firebase-admin/firestore';
import { preview } from './goodNews';
import type { Notice } from './logic';
import type { Sender } from './store';
import { mentionTitle, namedIn, newlyNamed } from './mentions';
import { deliver, noticeTime } from './prefs';

/** What the server reads of a comment of the gazette. */
export interface GazetteCommentData {
  text: string;
  authorId: string;
  authorName: string;
  mentionIds?: string[];
}

/** What the server reads of a publication. */
export interface GazettePostNoticeData {
  authorId: string;
  authorName: string;
  text?: string;
  photoIds?: string[];
  mentionIds?: string[];
  loc?: { label?: string };
}

export interface GazetteMessage {
  recipientIds: string[];
  notice: Notice;
}

/** The tag of a card's notices: a new one replaces the last on the phone, and the burst keeps the last. */
export function gazetteTag(threadId: string): string {
  return `gazette:${threadId}`;
}

function gazetteNotice(threadId: string, title: string, body: string, named: boolean): Notice {
  return { channel: 'comments', title, body, gazetteThreadId: threadId, tag: gazetteTag(threadId), ...(named ? { named: true } : {}) };
}

/**
 * Who is told of a comment of [comment] on the card [threadId]: among [viewers] (who see the card
 * and have the app), the members it names, then the author of the publication ([postAuthorId], for
 * a card « post:{id} »), then the earlier commenters. Each member once, never the commenter.
 */
export function gazetteCommentMessages(
  threadId: string,
  comment: GazetteCommentData,
  viewers: Set<string>,
  earlierIds: string[],
  post: { authorId: string; authorName: string } | null,
): GazetteMessage[] {
  const named = namedIn(comment, viewers);
  const told = new Set<string>([comment.authorId, ...named]);
  const author = post && viewers.has(post.authorId) && !told.has(post.authorId) ? post.authorId : null;
  if (author) told.add(author);
  const others = [...new Set(earlierIds)].filter((id) => viewers.has(id) && !told.has(id)).sort();
  const body = preview(comment.text);
  const messages: GazetteMessage[] = [];
  if (named.length > 0) messages.push({ recipientIds: named, notice: gazetteNotice(threadId, mentionTitle(comment.authorName), body, true) });
  if (author) messages.push({ recipientIds: [author], notice: gazetteNotice(threadId, `${comment.authorName} a commenté ta publication`, body, false) });
  if (others.length > 0) {
    const title = post && post.authorId !== comment.authorId
      ? `${comment.authorName} a aussi commenté la publication de ${post.authorName}`
      : post
        ? `${comment.authorName} a aussi commenté sa publication`
        : `${comment.authorName} a aussi commenté dans la gazette`;
    messages.push({ recipientIds: others, notice: gazetteNotice(threadId, title, body, false) });
  }
  return messages;
}

/** « Camille t'a nommé dans sa publication », and the start of its text (or of its place, or « 📷 »). */
export function gazettePostMentionMessage(postId: string, before: GazettePostNoticeData | undefined, after: GazettePostNoticeData, viewers: Set<string>): GazetteMessage | null {
  const named = newlyNamed(before, after, after.authorId, viewers);
  if (named.length === 0) return null;
  const text = preview(after.text ?? '');
  const photos = after.photoIds?.length ?? 0;
  const body = text || (photos > 0 ? (photos === 1 ? '📷 Une photo' : `📷 ${photos} photos`) : after.loc?.label ? `📍 ${after.loc.label}` : '');
  const threadId = `post:${postId}`;
  return { recipientIds: named, notice: gazetteNotice(threadId, `${after.authorName} t'a nommé dans sa publication`, body, true) };
}

/** The members with the app: a child without it is never told. */
async function membersWithApp(db: Firestore): Promise<string[]> {
  const members = await db.collection('members').select('profile').get();
  return members.docs.filter((d) => d.get('profile') !== 'childWithoutApp').map((d) => d.id);
}

/**
 * Who sees the card [threadId], among [withApp]: the server side of seesThread (firestore.rules).
 * A card of an unknown kind is seen by nobody, so it tells nobody.
 */
export async function gazetteThreadViewers(db: Firestore, threadId: string, withApp: string[]): Promise<Set<string>> {
  const [kind, first = '', second = ''] = threadId.split(':');
  const all = new Set(withApp);
  const among = (ids: unknown) => new Set((Array.isArray(ids) ? ids : []).filter((id): id is string => typeof id === 'string' && all.has(id)));
  switch (kind) {
    // An event and a recipe keep their comments on themselves today (takesComments), but are seen by all.
    case 'event': case 'recipe': case 'birthday': case 'weekly': case 'deal':
      return all;
    case 'post':
      return first !== '' && (await db.collection('gazettePosts').doc(first).get()).exists ? all : new Set();
    case 'album':
      if (first === 'events') return all;
      if (first === 'trips' && second !== '') return among((await db.collection('trips').doc(second).get()).get('readerIds'));
      return new Set();
    case 'film':
      return first === '' ? new Set() : among((await db.collection('trips').doc(first).get()).get('readerIds'));
    case 'wishes':
      return first === '' ? new Set() : new Set([...all].filter((id) => id !== first));
    case 'help': {
      if (first === '') return new Set();
      const request = await db.collection('helpRequests').doc(first).get();
      if (!request.exists) return new Set();
      const owner = (await db.doc('config/family').get()).get('ownerMemberId');
      const viewers = among(request.get('viewerIds'));
      if (typeof owner === 'string' && all.has(owner)) viewers.add(owner);
      return viewers;
    }
    default:
      return new Set();
  }
}

/** A new comment on a card of the gazette: the people concerned are told, grouped. */
export async function notifyGazetteComment(
  db: Firestore, send: Sender, threadId: string, commentId: string, comment: GazetteCommentData, now: Date = noticeTime(),
): Promise<number> {
  if (typeof comment?.authorId !== 'string' || typeof comment.text !== 'string') return 0;
  const viewers = await gazetteThreadViewers(db, threadId, await membersWithApp(db));
  if (viewers.size === 0) return 0;
  const thread = db.collection('gazetteThreads').doc(threadId);
  const earlier = await thread.collection('comments').select('authorId').get();
  const earlierIds = earlier.docs.filter((d) => d.id !== commentId).map((d) => d.get('authorId')).filter((id): id is string => typeof id === 'string');
  let post: { authorId: string; authorName: string } | null = null;
  if (threadId.startsWith('post:')) {
    const doc = await db.collection('gazettePosts').doc(threadId.slice('post:'.length)).get();
    const authorId = doc.get('authorId');
    if (typeof authorId === 'string') post = { authorId, authorName: String(doc.get('authorName') ?? '') };
  }
  let sent = 0;
  for (const { recipientIds, notice } of gazetteCommentMessages(threadId, comment, viewers, earlierIds, post)) {
    sent += await deliver(db, send, recipientIds, notice, now);
  }
  return sent;
}

/** A publication written or edited: the members it newly names are told. */
export async function notifyGazettePostMentions(
  db: Firestore, send: Sender, postId: string, before: GazettePostNoticeData | undefined, after: GazettePostNoticeData | undefined, now: Date = noticeTime(),
): Promise<number> {
  if (!after || typeof after.authorId !== 'string') return 0;
  const message = gazettePostMentionMessage(postId, before, after, new Set(await membersWithApp(db)));
  return message ? deliver(db, send, message.recipientIds, message.notice, now) : 0;
}
