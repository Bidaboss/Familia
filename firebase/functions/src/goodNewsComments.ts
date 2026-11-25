// Commentaires des bonnes nouvelles (PRD, récit 67): goodNews/{postId}/comments/{commentId}. A new
// comment tells the author of the news, the member it is for and those who already commented, never
// the one who wrote it, nor those who chose « Ne plus être prévenu »
// (goodNews/{postId}/commentMutes/{memberId}) or hid the news for themselves. Sent by deliver on the
// channel comments, like the reactions. Until the
// fil d'actualité is switched on for the family (config/app.features.newsFeed), only the admin
// principal is told. The news keeps its count of comments (commentCount) for the cards of the fil.

import type { Firestore } from 'firebase-admin/firestore';
import { GoodNewsNotice, GoodNewsSender, preview } from './goodNews';
import { mentionTitle, namedIn } from './mentions';
import { deliver, noticeTime } from './prefs';
import { featureAudience } from './store';

export interface GoodNewsCommentData {
  text: string;
  authorId: string;
  authorName: string;
  /** The members named « @Prénom » (l'admin, 19/11). */
  mentionIds?: string[];
}

/** What the server reads of the news to choose whom to tell. */
export interface CommentedNews {
  authorId?: string;
  forMemberId?: string;
  hiddenByIds?: string[];
}

/** One message per wording: to whom the news belongs, then to the others who commented. */
export interface GoodNewsCommentMessage {
  recipientIds: string[];
  notice: GoodNewsNotice;
}

/**
 * Who is told of a comment of [commenterId]: the author of the news, the member it is for and the
 * earlier commenters, among the members with the app ([withApp]). Never the commenter, nor who
 * muted the comments or hid the news.
 */
export function goodNewsCommentMessages(
  postId: string,
  news: CommentedNews,
  comment: GoodNewsCommentData,
  earlierIds: string[],
  mutedIds: Set<string>,
  withApp: Set<string>,
): GoodNewsCommentMessage[] {
  const hidden = new Set(news.hiddenByIds ?? []);
  // Who is named hears it once, as « t'a nommé », even with the comments muted; never once the
  // news is hidden for them.
  const named = namedIn(comment, withApp).filter((id) => !hidden.has(id));
  const keep = (id: string | undefined): id is string =>
    typeof id === 'string' && id !== comment.authorId && withApp.has(id) && !mutedIds.has(id) && !hidden.has(id) && !named.includes(id);
  const owners = [...new Set([news.authorId, news.forMemberId].filter(keep))].sort();
  const others = [...new Set(earlierIds.filter(keep))].filter((id) => !owners.includes(id)).sort();
  const body = preview(comment.text);
  const messages: GoodNewsCommentMessage[] = [];
  if (named.length > 0) {
    messages.push({
      recipientIds: named,
      notice: { channel: 'comments', title: mentionTitle(comment.authorName), body, goodNewsId: postId, direct: true },
    });
  }
  if (owners.length > 0) {
    messages.push({
      recipientIds: owners,
      notice: { channel: 'comments', title: `${comment.authorName} a commenté ton actualité`, body, goodNewsId: postId },
    });
  }
  if (others.length > 0) {
    messages.push({
      recipientIds: others,
      notice: { channel: 'comments', title: `${comment.authorName} a aussi commenté l'actualité`, body, goodNewsId: postId },
    });
  }
  return messages;
}

/** A new comment: the count of the news goes up, and the people concerned are told. */
export async function onGoodNewsCommentCreated(
  db: Firestore, send: GoodNewsSender, postId: string, commentId: string, comment: GoodNewsCommentData, now: Date = noticeTime(),
): Promise<number> {
  const newsRef = db.collection('goodNews').doc(postId);
  const news = await newsRef.get();
  if (!news.exists) return 0;
  await recount(db, postId);
  const earlier = await newsRef.collection('comments').select('authorId').get();
  const earlierIds = earlier.docs.filter((d) => d.id !== commentId).map((d) => d.get('authorId')).filter((id): id is string => typeof id === 'string');
  const muted = new Set((await newsRef.collection('commentMutes').select().get()).docs.map((d) => d.id));
  const members = await db.collection('members').select('profile').get();
  const withApp = new Set(members.docs.filter((d) => d.get('profile') !== 'childWithoutApp').map((d) => d.id));
  const audience = await featureAudience(db, 'newsFeed');
  let sent = 0;
  for (const { recipientIds, notice } of goodNewsCommentMessages(postId, news.data() as CommentedNews, comment, earlierIds, muted, withApp)) {
    const to = audience(recipientIds);
    if (to.length > 0) sent += await deliver(db, send, to, notice, now);
  }
  return sent;
}

/** A removed comment: the count goes down, unless the news itself is gone. */
export async function onGoodNewsCommentDeleted(db: Firestore, postId: string): Promise<void> {
  await recount(db, postId);
}

/** A removed good news: its comments and its « Ne plus être prévenu » go with it. */
export async function deleteGoodNewsComments(db: Firestore, postId: string): Promise<void> {
  const news = db.collection('goodNews').doc(postId);
  await db.recursiveDelete(news.collection('comments'));
  await db.recursiveDelete(news.collection('commentMutes'));
}

/**
 * The count is the number of comments under the news, counted again each time: a trigger that runs
 * twice for the same comment (Cloud Functions may replay an event) leaves it right, where adding 1
 * would count the comment twice. Never writes a news that no longer exists.
 */
async function recount(db: Firestore, postId: string): Promise<void> {
  const ref = db.collection('goodNews').doc(postId);
  await db.runTransaction(async (tx) => {
    const news = await tx.get(ref);
    if (!news.exists) return;
    const count = (await tx.get(ref.collection('comments').count())).data().count;
    if (news.get('commentCount') !== count) tx.update(ref, { commentCount: count });
  });
}
