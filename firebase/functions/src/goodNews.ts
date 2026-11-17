// Bonnes nouvelles (PRD, récit 32, carte AVIS-12): who is told, and the photos of a removed good
// news. The pure part decides the recipients and the French texts; the store part reads
// Firestore, sends and deletes in Storage.

import { FieldValue, Timestamp, type Firestore } from 'firebase-admin/firestore';
import { deliver } from './prefs';
import { familyMembers } from './audience';

export interface GoodNewsData {
  text: string;
  photoIds: string[];
  authorId: string;
  authorName: string;
  forMemberId: string;
  mentionIds: string[];
  /** memberId → reaction name (heart, clap…). */
  reactions?: Record<string, string>;
}

export interface GoodNewsMember {
  id: string;
  firstName: string;
  profile: string;
  parentIds: string[];
}

export interface GoodNewsNotice {
  /** `goodNews` for a new one; `comments` for the grouped reactions (PRD rév. 174). */
  channel: 'goodNews' | 'comments';
  title: string;
  body: string;
  goodNewsId: string;
  /** The post of a child, to their responsible adults (AVIS-12): never muted (récit 6 quinquies). */
  direct?: boolean;
}

export interface GoodNewsMessage {
  recipientIds: string[];
  notice: GoodNewsNotice;
}

const PREVIEW_LENGTH = 100;

/** The text on one line, cut at about 100 characters. */
export function preview(text: string): string {
  const line = text.replace(/\s+/g, ' ').trim();
  return line.length <= PREVIEW_LENGTH ? line : `${line.slice(0, PREVIEW_LENGTH - 1).trimEnd()}…`;
}

/**
 * Who sees the good news. Today the whole family who has the app (récit 32). When the circles
 * (PRD rév. 124) arrive, this is the one place to plug the audience function of logic.ts.
 */
export function goodNewsAudience(members: GoodNewsMember[]): GoodNewsMember[] {
  return members.filter((m) => m.profile !== 'childWithoutApp');
}

/**
 * What a new good news tells whom. Everyone in the audience but the author, except those who
 * turned these notifications off ([mutedIds]). When a child publishes, their responsible adults
 * get a notice of their own, even if they turned the others off (carte AVIS-12).
 */
export function goodNewsMessages(
  id: string, n: GoodNewsData, members: GoodNewsMember[], mutedIds: Set<string> = new Set(),
): GoodNewsMessage[] {
  const author = members.find((m) => m.id === n.authorId);
  const forName = n.forMemberId !== n.authorId ? members.find((m) => m.id === n.forMemberId)?.firstName : undefined;
  const childParents = author && author.profile !== 'adult' ? author.parentIds : [];

  const audience = goodNewsAudience(members).map((m) => m.id);
  const parents = [...new Set(childParents)].filter((pid) => audience.includes(pid) && pid !== n.authorId).sort();
  const others = audience.filter((mid) => mid !== n.authorId && !parents.includes(mid) && !mutedIds.has(mid)).sort();

  const messages: GoodNewsMessage[] = [];
  if (others.length > 0) {
    const title = forName ? `Bonne nouvelle pour ${forName}` : `Bonne nouvelle de ${n.authorName}`;
    const body = forName ? `${n.authorName} : ${preview(n.text)}` : preview(n.text);
    messages.push({ recipientIds: others, notice: { channel: 'goodNews', title, body, goodNewsId: id } });
  }
  if (parents.length > 0) {
    messages.push({
      recipientIds: parents,
      notice: { channel: 'goodNews', title: `${n.authorName} a publié une Bonne nouvelle`, body: preview(n.text), goodNewsId: id, direct: true },
    });
  }
  return messages;
}

// --- Grouped reactions (PRD rév. 174) ------------------------------------------------------------
//
// The author gets one notice per good news, an hour after the first reaction, for everyone who
// reacted since the last one. goodNewsDigests/{postId} keeps when it is due and who was already
// named; only the Cloud Functions read and write it (no rule opens it to the app).

/** One hour after the first reaction. */
export const DIGEST_DELAY_MS = 60 * 60 * 1000;

/** Members who reacted between [before] and [after], the author left out. */
export function newReactorIds(before: GoodNewsData, after: GoodNewsData): string[] {
  const had = new Set(Object.keys(before.reactions ?? {}));
  return Object.keys(after.reactions ?? {}).filter((id) => !had.has(id) && id !== after.authorId).sort();
}

/** « Inès a réagi », « Inès et Jules ont réagi », « Inès et 3 autres ont réagi » à ta Bonne nouvelle. */
export function reactionsNotice(id: string, n: GoodNewsData, names: string[]): GoodNewsNotice | null {
  if (names.length === 0) return null;
  const who = names.length === 1 ? `${names[0]} a réagi`
    : names.length === 2 ? `${names[0]} et ${names[1]} ont réagi`
      : `${names[0]} et ${names.length - 1} autres ont réagi`;
  return { channel: 'comments', title: `${who} à ta Bonne nouvelle`, body: preview(n.text), goodNewsId: id };
}

/** The two Storage files of each photo: the photo and its thumbnail. */
export function photoPaths(postId: string, authorId: string, photoIds: string[]): string[] {
  return photoIds.flatMap((p) => [`goodNews/${authorId}/${postId}/${p}.jpg`, `goodNews/${authorId}/${postId}/${p}-thumb.jpg`]);
}

// --- Firestore and Storage ----------------------------------------------------------------------

export type GoodNewsSender = (token: string, notice: GoodNewsNotice) => Promise<'ok' | 'unregistered' | 'error'>;

/** Deletes files in Storage; a file already gone is not an error. */
export type FileRemover = (paths: string[]) => Promise<void>;

const DIGESTS = 'goodNewsDigests';

async function loadMembers(db: Firestore): Promise<GoodNewsMember[]> {
  const snapshot = await familyMembers(db);
  return snapshot.map((doc) => ({
    id: doc.id,
    firstName: String(doc.get('firstName') ?? ''),
    profile: String(doc.get('profile') ?? ''),
    parentIds: (doc.get('parentIds') as string[] | undefined) ?? [],
  }));
}

export async function onGoodNewsPublished(db: Firestore, send: GoodNewsSender, id: string, n: GoodNewsData, now: Date = new Date()): Promise<number> {
  const members = await loadMembers(db);
  let sent = 0;
  // The switch « Bonnes nouvelles » is read by deliver (récit 6 quinquies), like every family.
  for (const { recipientIds, notice } of goodNewsMessages(id, n, members)) sent += await deliver(db, send, recipientIds, notice, now);
  return sent;
}

/** A new reaction: the grouped notice is due in an hour, unless one is already waiting. */
export async function onGoodNewsReacted(db: Firestore, id: string, before: GoodNewsData, after: GoodNewsData, now: Date): Promise<void> {
  if (newReactorIds(before, after).length === 0) return;
  const ref = db.collection(DIGESTS).doc(id);
  await db.runTransaction(async (tx) => {
    const digest = await tx.get(ref);
    if (digest.exists && digest.get('dueAt')) return;
    tx.set(ref, { authorId: after.authorId, dueAt: Timestamp.fromMillis(now.getTime() + DIGEST_DELAY_MS) }, { merge: true });
  });
}

/** Every 15 minutes: the grouped notices that are due go to their authors. */
export async function sendReactionDigests(db: Firestore, send: GoodNewsSender, now: Date): Promise<number> {
  const due = await db.collection(DIGESTS).where('dueAt', '<=', Timestamp.fromDate(now)).get();
  let sent = 0;
  for (const digest of due.docs) {
    const post = await db.collection('goodNews').doc(digest.id).get();
    if (!post.exists) {
      await digest.ref.delete();
      continue;
    }
    const n = post.data() as GoodNewsData;
    const named = new Set((digest.get('notifiedIds') as string[] | undefined) ?? []);
    const reactors = Object.keys(n.reactions ?? {}).filter((mid) => mid !== n.authorId && !named.has(mid)).sort();
    const memberDocs = reactors.length === 0 ? [] : await db.getAll(...reactors.map((mid) => db.collection('members').doc(mid)));
    const names = memberDocs.filter((d) => d.exists).map((d) => String(d.get('firstName') ?? '')).filter((name) => name);
    const notice = reactionsNotice(digest.id, n, names);
    if (notice) sent += await deliver(db, send, [n.authorId], notice, now);
    await digest.ref.update({ dueAt: FieldValue.delete(), notifiedIds: [...named, ...reactors].sort() });
  }
  return sent;
}

/** A removed good news takes its grouped notice with it. */
export async function deleteReactionDigest(db: Firestore, id: string): Promise<void> {
  await db.collection(DIGESTS).doc(id).delete();
}

/** The author took photos out: their files go. */
export async function onGoodNewsEdited(remove: FileRemover, id: string, before: GoodNewsData, after: GoodNewsData): Promise<void> {
  const gone = before.photoIds.filter((p) => !after.photoIds.includes(p));
  if (gone.length > 0) await remove(photoPaths(id, before.authorId, gone));
}

/** A good news removed by anyone (author, parent, chef, admin): its photos go with it. */
export async function onGoodNewsRemoved(remove: FileRemover, id: string, n: GoodNewsData): Promise<void> {
  await remove(photoPaths(id, n.authorId, n.photoIds));
}
