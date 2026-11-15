// Bonnes nouvelles (PRD, récit 32, carte AVIS-12): who is told, and the photos of a removed good
// news. The pure part decides the recipients and the French texts; the store part reads
// Firestore, sends and deletes in Storage.

import type { Firestore } from 'firebase-admin/firestore';

export interface GoodNewsData {
  text: string;
  photoIds: string[];
  authorId: string;
  authorName: string;
  forMemberId: string;
  mentionIds: string[];
}

export interface GoodNewsMember {
  id: string;
  firstName: string;
  profile: string;
  parentIds: string[];
}

export interface GoodNewsNotice {
  channel: 'goodNews';
  title: string;
  body: string;
  goodNewsId: string;
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
  id: string, n: GoodNewsData, members: GoodNewsMember[], mutedIds: Set<string>,
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
      notice: { channel: 'goodNews', title: `${n.authorName} a publié une Bonne nouvelle`, body: preview(n.text), goodNewsId: id },
    });
  }
  return messages;
}

/** The two Storage files of each photo: the photo and its thumbnail. */
export function photoPaths(postId: string, authorId: string, photoIds: string[]): string[] {
  return photoIds.flatMap((p) => [`goodNews/${authorId}/${postId}/${p}.jpg`, `goodNews/${authorId}/${postId}/${p}-thumb.jpg`]);
}

// --- Firestore and Storage ----------------------------------------------------------------------

export type GoodNewsSender = (token: string, notice: GoodNewsNotice) => Promise<'ok' | 'unregistered' | 'error'>;

/** Deletes files in Storage; a file already gone is not an error. */
export type FileRemover = (paths: string[]) => Promise<void>;

async function loadMembers(db: Firestore): Promise<GoodNewsMember[]> {
  const snapshot = await db.collection('members').get();
  return snapshot.docs.map((doc) => ({
    id: doc.id,
    firstName: String(doc.get('firstName') ?? ''),
    profile: String(doc.get('profile') ?? ''),
    parentIds: (doc.get('parentIds') as string[] | undefined) ?? [],
  }));
}

/** Members who turned the good news off: members/{id}/settings/notifications.goodNews. */
async function mutedMembers(db: Firestore, memberIds: string[]): Promise<Set<string>> {
  if (memberIds.length === 0) return new Set();
  const refs = memberIds.map((mid) => db.collection('members').doc(mid).collection('settings').doc('notifications'));
  const docs = await db.getAll(...refs);
  return new Set(docs.filter((d) => d.exists && d.get('goodNews') === false).map((d) => d.ref.parent.parent!.id));
}

export async function onGoodNewsPublished(db: Firestore, send: GoodNewsSender, id: string, n: GoodNewsData): Promise<number> {
  const members = await loadMembers(db);
  const muted = await mutedMembers(db, members.map((m) => m.id));
  let sent = 0;
  for (const { recipientIds, notice } of goodNewsMessages(id, n, members, muted)) {
    for (const memberId of recipientIds) {
      const devices = await db.collection('members').doc(memberId).collection('devices').get();
      for (const device of devices.docs) {
        const result = await send(device.id, notice);
        if (result === 'ok') sent++;
        if (result === 'unregistered') await device.ref.delete();
      }
    }
  }
  return sent;
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
