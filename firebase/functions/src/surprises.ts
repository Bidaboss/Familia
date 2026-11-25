// Cadeaux et Surprise (brief design/cadeaux/brief-surprise-final.md, plan « Cadeaux et Surprise »):
// what the server keeps for a surprise. The beneficiary never reads any of it, admin included.
//
// surprises/{sid}/proposals/{pid}/ratings/{memberId}  { stars: 1 to 5 }, read only by its author.
//   → ratingSum and ratingCount on the proposal, so the average is shown without telling who gave what.
// surprises/{sid}/takes/{pid}/shares/{memberId}       { amount: whole euros }
//   → sharesTotal and sharesCount on the take (a joint gift).

import type { DocumentReference, DocumentSnapshot, Firestore } from 'firebase-admin/firestore';
import { FieldValue } from 'firebase-admin/firestore';
import { giftAudience } from './gifts';
import { Notice, parisDay } from './logic';
import { notify, Sender } from './store';

/** A star rating counts only from 1 to 5, whole stars: anything else is left out. */
export function validStars(value: unknown): number | null {
  const n = Number(value);
  return Number.isInteger(n) && n >= 1 && n <= 5 ? n : null;
}

/** A part counts only in whole euros above 0 (brief: « parts en euros entiers »). */
export function validAmount(value: unknown): number | null {
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : null;
}

/** The average shows from 3 ratings (anonymat des notes). */
export const MIN_RATINGS = 3;

/**
 * The sum and the number of the ratings of a proposal. Only the server reads every rating, so the
 * members see the average and never who rated what (the app shows it from 3 ratings).
 */
export async function updateRatingAverage(db: Firestore, surpriseId: string, proposalId: string): Promise<void> {
  const proposal = db.collection('surprises').doc(surpriseId).collection('proposals').doc(proposalId);
  // In a transaction: two ratings in the same second must not write an older count over a newer one.
  await db.runTransaction(async (tx) => {
    const [snapshot, ratings] = await Promise.all([tx.get(proposal), tx.get(proposal.collection('ratings'))]);
    if (!snapshot.exists) return;
    const stars = ratings.docs.map((r) => validStars(r.get('stars'))).filter((n): n is number => n !== null);
    // Below 3 ratings, no sum: with one rating the sum would tell it (C1 de #619), the app shows the
    // average from 3 ratings only.
    const sum = stars.length >= MIN_RATINGS ? stars.reduce((total, n) => total + n, 0) : null;
    if (snapshot.get('ratingSum') === sum && snapshot.get('ratingCount') === stars.length) return;
    tx.update(proposal, { ratingSum: sum, ratingCount: stars.length });
  });
}

/** The total and the number of the parts of a joint gift. Over the goal is allowed: the app only warns. */
export async function updateTakeTotal(db: Firestore, surpriseId: string, proposalId: string): Promise<void> {
  const take = db.collection('surprises').doc(surpriseId).collection('takes').doc(proposalId);
  await db.runTransaction(async (tx) => {
    const [snapshot, shares] = await Promise.all([tx.get(take), tx.get(take.collection('shares'))]);
    if (!snapshot.exists) return;
    const amounts = shares.docs.map((s) => validAmount(s.get('amount'))).filter((n): n is number => n !== null);
    const total = amounts.reduce((sum, n) => sum + n, 0);
    if (snapshot.get('sharesTotal') === total && snapshot.get('sharesCount') === amounts.length) return;
    tx.update(take, { sharesTotal: total, sharesCount: amounts.length });
  });
}

/** A removed proposal takes its ratings with it: nobody could read them any more. */
export async function deleteProposalRatings(db: Firestore, surpriseId: string, proposalId: string): Promise<void> {
  await db.recursiveDelete(db.collection('surprises').doc(surpriseId).collection('proposals').doc(proposalId).collection('ratings'));
}

/** A released gift (« Je le prends » undone, or the joint gift dropped) takes its parts with it. */
export async function deleteTakeShares(db: Firestore, surpriseId: string, proposalId: string): Promise<void> {
  await db.recursiveDelete(db.collection('surprises').doc(surpriseId).collection('takes').doc(proposalId).collection('shares'));
}

/**
 * openSurprises/{sid} tells the family (never the beneficiary) that a surprise is in preparation for
 * someone, so nobody starts a second one. Offered or removed, it goes.
 */
export async function closeOpenSurprise(db: Firestore, surpriseId: string): Promise<void> {
  await db.collection('openSurprises').doc(surpriseId).delete();
}

/** A surprise removed by its creator leaves nothing behind (members, proposals, ratings, takes, parts). */
export async function deleteSurpriseContent(db: Firestore, surpriseId: string): Promise<void> {
  await closeOpenSurprise(db, surpriseId);
  await db.collection('offeredSurprises').doc(surpriseId).delete();
  const surprise = db.collection('surprises').doc(surpriseId);
  const children = await surprise.listCollections();
  await Promise.all(children.map((c) => db.recursiveDelete(c)));
}

// --- Mon carnet (récit 9 bis): what I took in a surprise, and my part of a joint gift -----------------
//
// One entry per member and proposal, notebooks/{memberId}/entries/surprise_{sid}_{pid}, written by the
// server like the reservations of the lists: source 'claim' alone, 'share' in a joint gift. No listId:
// the app has no list to open, surpriseId tells where it comes from.

export function surpriseEntryId(surpriseId: string, proposalId: string): string {
  return `surprise_${surpriseId}_${proposalId}`;
}

/** The year of a surprise date « AAAA-MM-JJ », or null. */
function yearOfDate(date: unknown): number | null {
  return typeof date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(date) ? Number(date.slice(0, 4)) : null;
}

/** Who has this gift in their notebook, with their price: the taker alone, or the owner and every part. */
export function notebookHolders(
  take: { kind?: unknown; ownerId?: unknown } | undefined, proposalPrice: number | null, shares: { id: string; amount: unknown }[],
): Map<string, { source: 'claim' | 'share'; price: number | null }> {
  const holders = new Map<string, { source: 'claim' | 'share'; price: number | null }>();
  if (!take || typeof take.ownerId !== 'string' || take.ownerId === '') return holders;
  if (take.kind !== 'shared') {
    holders.set(take.ownerId, { source: 'claim', price: proposalPrice });
    return holders;
  }
  // The one who opened the joint gift, without a part yet: in the notebook, nothing spent.
  holders.set(take.ownerId, { source: 'share', price: null });
  for (const s of shares) {
    const amount = validAmount(s.amount);
    if (amount !== null) holders.set(s.id, { source: 'share', price: amount });
  }
  return holders;
}

/** Title, link and price of a proposal: a wish is a live link to « Mes envies », an idea a copy. */
async function giftDetails(db: Firestore, beneficiaryId: string, proposal: Record<string, unknown> | undefined): Promise<{ title: string; url: string | null; price: number | null }> {
  let source: Record<string, unknown> | undefined = proposal;
  if (proposal?.kind === 'wish' && typeof proposal.wishItemId === 'string' && beneficiaryId) {
    const wish = await db.collection('giftLists').doc(`wishes-${beneficiaryId}`).collection('items').doc(proposal.wishItemId).get();
    if (wish.exists) source = { ...proposal, ...wish.data() };
  }
  return {
    title: String(source?.title ?? ''),
    url: typeof source?.url === 'string' ? source.url : null,
    price: typeof source?.price === 'number' ? source.price : null,
  };
}

/**
 * Who holds a notebook entry of this gift, kept by the server in surprises/{sid}/notebook/{pid}
 * (nobody else reads it): a released gift then knows whose entries to take back.
 */
function holdersRef(db: Firestore, surpriseId: string, proposalId: string): DocumentReference {
  return db.collection('surprises').doc(surpriseId).collection('notebook').doc(proposalId);
}

/** Brings every notebook entry of a gift taken in a surprise in line with the take and its parts. */
export async function syncSurpriseNotebook(db: Firestore, surpriseId: string, proposalId: string): Promise<void> {
  const surpriseRef = db.collection('surprises').doc(surpriseId);
  const takeRef = surpriseRef.collection('takes').doc(proposalId);
  const [surprise, proposal, take, shares, registry] = await Promise.all([
    surpriseRef.get(), surpriseRef.collection('proposals').doc(proposalId).get(), takeRef.get(), takeRef.collection('shares').get(),
    holdersRef(db, surpriseId, proposalId).get(),
  ]);
  // Offered, or purged after: the entries stay as they are, « Offert ».
  if (!surprise.exists || surprise.get('status') === 'offered') return;
  const entryId = surpriseEntryId(surpriseId, proposalId);
  const beneficiaryId = String(surprise.get('beneficiaryId') ?? '');
  const details = await giftDetails(db, beneficiaryId, proposal.data());
  const holders = proposal.exists && take.exists
    ? notebookHolders(take.data(), details.price, shares.docs.map((s) => ({ id: s.id, amount: s.get('amount') })))
    : new Map<string, { source: 'claim' | 'share'; price: number | null }>();
  const before = ((registry.get('memberIds') as string[] | undefined) ?? []).filter((id) => typeof id === 'string');
  const beneficiary = await db.collection('members').doc(beneficiaryId).get();
  const entry = (memberId: string) => db.collection('notebooks').doc(memberId).collection('entries').doc(entryId);
  const batch = db.batch();
  for (const id of before) if (!holders.has(id)) batch.delete(entry(id));
  for (const [memberId, h] of holders) {
    const ref = entry(memberId);
    const current = await ref.get();
    const fields = {
      source: h.source,
      surpriseId,
      proposalId,
      listId: null,
      itemId: null,
      personId: beneficiaryId,
      personName: String(beneficiary.get('firstName') ?? ''),
      occasion: (surprise.get('occasion') as string | null) ?? null,
      year: yearOfDate(surprise.get('date')),
      title: details.title,
      url: details.url,
      price: h.price,
    };
    if (current.exists) {
      const unchanged = Object.entries(fields).every(([key, value]) => current.get(key) === value);
      if (!unchanged) batch.update(ref, { ...fields, updatedAt: FieldValue.serverTimestamp() });
    } else {
      batch.set(ref, { ...fields, note: null, status: 'idea', createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
    }
  }
  const ids = [...holders.keys()].sort();
  if (ids.length > 0) batch.set(holdersRef(db, surpriseId, proposalId), { memberIds: ids });
  else if (registry.exists) batch.delete(holdersRef(db, surpriseId, proposalId));
  await batch.commit();
}

// --- « C'est offert » (brief § 5, décision de l'admin du 25/11) ----------------------------------
//
// One member writes status 'offered', offeredAt and offeredBy on the surprise. The server then, once:
// - writes « Déjà offert à X », giftHistory/{beneficiaryId}/items/{sid}_{pid}, kept without limit;
// - takes each offered wish out of « Mes envies », and each offered idea out of its author's notebook,
//   with a copy in surprises/{sid}/offered/{pid} so the admin principal can cancel within 30 days;
// - marks « Offert » the notebook entries of the takers and of every part;
// - tells the other members, never the beneficiary.

export const PURGE_DAYS = 30;

export interface OfferedGift {
  proposalId: string;
  kind: 'wish' | 'idea';
  title: string;
  wishItemId: string | null;
  authorId: string | null;
  notebookEntryId: string | null;
}

/** What a proposal tells about where its gift lives, or null when it cannot be offered. */
export function offeredGift(proposalId: string, p: Record<string, unknown> | undefined): OfferedGift | null {
  if (!p || p.removed === true) return null;
  const kind = p.kind === 'idea' ? 'idea' : p.kind === 'wish' ? 'wish' : null;
  if (!kind) return null;
  return {
    proposalId,
    kind,
    title: String(p.title ?? ''),
    wishItemId: kind === 'wish' && typeof p.wishItemId === 'string' ? p.wishItemId : null,
    authorId: typeof p.authorId === 'string' ? p.authorId : null,
    notebookEntryId: kind === 'idea' && typeof p.notebookEntryId === 'string' ? p.notebookEntryId : null,
  };
}

export function offeredNotice(surpriseId: string, byName: string, beneficiaryName: string): Notice {
  return {
    channel: 'gifts',
    title: 'Surprise offerte',
    body: `${byName} a confirmé : les cadeaux de ${beneficiaryName} sont offerts.`,
    surpriseId,
  };
}

/** Whether this change of a surprise is the « C'est offert » that the server must follow up. */
export function isNewlyOffered(before: Record<string, unknown> | undefined, after: Record<string, unknown> | undefined): boolean {
  return before?.status !== 'offered' && after?.status === 'offered';
}

export async function onSurpriseOffered(db: Firestore, send: Sender, surpriseId: string): Promise<number> {
  const surpriseRef = db.collection('surprises').doc(surpriseId);
  // Once only, a retry of the trigger included: the first run marks the surprise as handled.
  const mine = await db.runTransaction(async (tx) => {
    const current = await tx.get(surpriseRef);
    if (current.get('status') !== 'offered' || current.get('offeredHandledAt')) return false;
    tx.update(surpriseRef, { offeredHandledAt: FieldValue.serverTimestamp() });
    return true;
  });
  if (!mine) return 0;
  await closeOpenSurprise(db, surpriseId);
  const surprise = await surpriseRef.get();
  const beneficiaryId = String(surprise.get('beneficiaryId') ?? '');
  const year = yearOfDate(surprise.get('date'));
  const occasion = (surprise.get('occasion') as string | null) ?? null;
  const takes = await surpriseRef.collection('takes').get();
  let offered = 0;
  for (const take of takes.docs) {
    const proposal = await surpriseRef.collection('proposals').doc(take.id).get();
    const gift = offeredGift(take.id, proposal.data());
    if (!gift) continue;
    gift.title = (await giftDetails(db, beneficiaryId, proposal.data())).title || gift.title;
    offered += 1;
    const batch = db.batch();
    batch.set(db.collection('giftHistory').doc(beneficiaryId).collection('items').doc(`${surpriseId}_${take.id}`), {
      title: gift.title, year, occasion, surpriseId, offeredAt: FieldValue.serverTimestamp(),
    });
    // The wish or the idea, kept for an admin cancel; then out of « Mes envies » or « Mes idées ».
    let source: DocumentReference | null = null;
    if (gift.kind === 'wish' && gift.wishItemId) {
      source = db.collection('giftLists').doc(`wishes-${beneficiaryId}`).collection('items').doc(gift.wishItemId);
    } else if (gift.kind === 'idea' && gift.authorId && gift.notebookEntryId) {
      source = db.collection('notebooks').doc(gift.authorId).collection('entries').doc(gift.notebookEntryId);
    }
    if (source) {
      const original = await source.get();
      if (original.exists) {
        batch.set(surpriseRef.collection('offered').doc(take.id), { path: source.path, data: original.data() });
        // Marked first: the trigger of a removed wish then keeps its photo for a cancel (index.ts),
        // and the purge removes it with the copy.
        if (gift.kind === 'wish') await source.update({ offeredIn: surpriseId });
        batch.delete(source);
      }
    }
    await batch.commit();
    // Every notebook entry of this gift: « Offert », its status before kept for an admin cancel.
    const registry = holdersRef(db, surpriseId, take.id);
    const holders = ((await registry.get()).get('memberIds') as string[] | undefined) ?? [];
    const statusBefore: Record<string, string> = {};
    for (const id of holders) {
      const ref = db.collection('notebooks').doc(id).collection('entries').doc(surpriseEntryId(surpriseId, take.id));
      const current = await ref.get();
      if (!current.exists) continue;
      statusBefore[id] = String(current.get('status') ?? 'idea');
      await ref.update({ status: 'given', updatedAt: FieldValue.serverTimestamp() });
    }
    if (holders.length > 0) await registry.set({ statusBefore }, { merge: true });
  }
  // Administration > Surprises offertes récemment: the title, the person and the date, nothing inside.
  const beneficiaryDoc = await db.collection('members').doc(beneficiaryId).get();
  await db.collection('offeredSurprises').doc(surpriseId).set({
    title: String(surprise.get('title') ?? ''),
    date: (surprise.get('date') as string | null) ?? null,
    beneficiaryId,
    beneficiaryName: String(beneficiaryDoc.get('firstName') ?? ''),
    offeredAt: FieldValue.serverTimestamp(),
    cancelAsked: false,
  });
  const byId = String(surprise.get('offeredBy') ?? '');
  const [by, beneficiary] = await Promise.all([
    byId ? db.collection('members').doc(byId).get() : Promise.resolve(null),
    db.collection('members').doc(beneficiaryId).get(),
  ]);
  const others = (await activeMemberIds(db, surpriseId, surprise.get('memberIds'))).filter((id) => id !== byId && id !== beneficiaryId);
  const audience = await giftAudience(db);
  const recipients = audience(others);
  if (recipients.length > 0) {
    await notify(db, send, recipients, offeredNotice(surpriseId, String(by?.get('firstName') ?? 'Quelqu\'un'), String(beneficiary.get('firstName') ?? '')));
  }
  return offered;
}

// --- « Annuler C'est offert » (brief § 9): the admin principal alone, within the 30 days ----------------
//
// offeredSurprises/{sid} lists the surprises offered recently (title, person, date), never their
// content, and never to the admin when they are the person. The admin writes cancelAsked; the server
// puts everything back as it was: the surprise « À confirmer » again, the wishes and ideas in their
// lists, « Déjà offert à X » without them, the notebooks as before, the members told.

export function cancelNotice(surpriseId: string, title: string): Notice {
  return {
    channel: 'gifts',
    title: `« ${title} » : à confirmer de nouveau`,
    body: 'L\'administrateur a annulé « C\'est offert ». Confirme-le une fois les cadeaux donnés.',
    surpriseId,
    tag: `surprise-${surpriseId}`,
  };
}

/** Whether this change of offeredSurprises/{sid} is the admin's ask to cancel, once. */
export function isCancelAsked(before: Record<string, unknown> | undefined, after: Record<string, unknown> | undefined): boolean {
  return before?.cancelAsked !== true && after?.cancelAsked === true;
}

export async function cancelSurpriseOffered(db: Firestore, send: Sender, surpriseId: string): Promise<boolean> {
  const surpriseRef = db.collection('surprises').doc(surpriseId);
  const listed = db.collection('offeredSurprises').doc(surpriseId);
  // The surprise goes back to « preparing » once, in a transaction. A run cut after it (its line still
  // asked) does the steps again, which are all safe twice, without telling anyone a second time (C2 de #631).
  const pass = await db.runTransaction(async (tx) => {
    const [current, line] = await Promise.all([tx.get(surpriseRef), tx.get(listed)]);
    if (current.exists && current.get('status') === 'offered') {
      tx.update(surpriseRef, {
        status: 'preparing', offeredAt: FieldValue.delete(), offeredBy: FieldValue.delete(),
        offeredHandledAt: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp(),
      });
      return 'first';
    }
    if (current.exists && line.get('cancelAsked') === true) return 'again';
    return null;
  });
  if (!pass) {
    await listed.delete();
    return false;
  }
  const surprise = await surpriseRef.get();
  const beneficiaryId = String(surprise.get('beneficiaryId') ?? '');
  // The wishes and ideas, back where they were (unless put back since).
  const copies = await surpriseRef.collection('offered').get();
  for (const c of copies.docs) {
    const path = c.get('path');
    const data = c.get('data') as Record<string, unknown> | undefined;
    if (typeof path === 'string' && data) {
      const original = db.doc(path);
      if (!(await original.get()).exists) await original.set(data);
    }
    await c.ref.delete();
  }
  // « Déjà offert à X » without this surprise.
  const history = await db.collection('giftHistory').doc(beneficiaryId).collection('items').where('surpriseId', '==', surpriseId).get();
  for (const h of history.docs) await h.ref.delete();
  // The notebooks as before « Offert ».
  const registries = await surpriseRef.collection('notebook').get();
  for (const r of registries.docs) {
    const before = (r.get('statusBefore') as Record<string, string> | undefined) ?? {};
    for (const id of (r.get('memberIds') as string[] | undefined) ?? []) {
      const ref = db.collection('notebooks').doc(id).collection('entries').doc(surpriseEntryId(surpriseId, r.id));
      const entry = await ref.get();
      if (entry.exists && entry.get('status') === 'given') {
        await ref.update({ status: before[id] ?? 'bought', updatedAt: FieldValue.serverTimestamp() });
      }
    }
    if (r.get('statusBefore') !== undefined) await r.ref.update({ statusBefore: FieldValue.delete() });
  }
  // Back on « Pour qui » and on the tile of Cadeaux.
  await db.collection('openSurprises').doc(surpriseId).set({
    beneficiaryId, title: String(surprise.get('title') ?? ''), creatorName: String(surprise.get('creatorName') ?? ''),
    updatedAt: FieldValue.serverTimestamp(),
  });
  await listed.delete();
  if (pass === 'again') return true;
  const owner = (await db.doc('config/family').get()).get('ownerMemberId');
  const others = (await activeMemberIds(db, surpriseId, surprise.get('memberIds'))).filter((id) => id !== owner && id !== beneficiaryId);
  const recipients = (await giftAudience(db))(others);
  if (recipients.length > 0) await notify(db, send, recipients, cancelNotice(surpriseId, String(surprise.get('title') ?? '')));
  return true;
}

// --- Les autres nouvelles d'une surprise (brief § 7, audit de fidélité point 2) -----------------------
//
// A member who withdraws, a wish of the person changed, the date moved: the other members who take
// part learn it, never the beneficiary nor the author of the change. The reminders read the date each
// morning, so a new date recalculates them by itself.

export function withdrawNotice(surpriseId: string, title: string, name: string): Notice {
  return { channel: 'gifts', title: `« ${title} »`, body: `${name} ne participe pas.`, surpriseId, tag: `surprise-${surpriseId}-members` };
}

export function wishChangedNotice(surpriseId: string, title: string, wishTitle: string, itemId = ''): Notice {
  return {
    channel: 'gifts', title: `« ${title} »`, body: `Une envie a changé : « ${wishTitle} ».`, surpriseId,
    tag: `surprise-${surpriseId}-wish${itemId ? `-${itemId}` : ''}`,
  };
}

/** At most one « envie modifiée » a surprise every 10 minutes: a wish edited in several steps rings once. */
export const WISH_NOTICE_GAP_MS = 10 * 60 * 1000;

async function wishNoticeDue(db: Firestore, surpriseId: string, now: Date): Promise<boolean> {
  const ref = db.collection('surprises').doc(surpriseId).collection('server').doc('notices');
  return db.runTransaction(async (tx) => {
    const last = (await tx.get(ref)).get('wishAt') as { toMillis(): number } | undefined;
    if (last && now.getTime() - last.toMillis() < WISH_NOTICE_GAP_MS) return false;
    tx.set(ref, { wishAt: now }, { merge: true });
    return true;
  });
}

export function dateChangedNotice(surpriseId: string, title: string, date: string): Notice {
  const [y, m, d] = date.split('-');
  return { channel: 'gifts', title: `« ${title} »`, body: `Nouvelle date : le ${d}/${m}/${y}.`, surpriseId, tag: `surprise-${surpriseId}-date` };
}

async function tellOthers(db: Firestore, send: Sender, surprise: DocumentSnapshot, skip: string[], notice: Notice): Promise<number> {
  const beneficiaryId = surprise.get('beneficiaryId');
  const ids = (await activeMemberIds(db, surprise.id, surprise.get('memberIds')))
    .filter((id) => id !== beneficiaryId && !skip.includes(id));
  const recipients = (await giftAudience(db))(ids);
  return recipients.length > 0 ? notify(db, send, recipients, notice) : 0;
}

/** « Je ne participe pas » (declined false → true on surprises/{sid}/members/{memberId}). */
export async function onSurpriseMemberDeclined(
  db: Firestore, send: Sender, surpriseId: string, memberId: string,
  before: Record<string, unknown> | undefined, after: Record<string, unknown> | undefined,
): Promise<number> {
  if (before?.declined === true || after?.declined !== true) return 0;
  const surprise = await db.collection('surprises').doc(surpriseId).get();
  if (!surprise.exists || surprise.get('status') !== 'preparing') return 0;
  const member = await db.collection('members').doc(memberId).get();
  return tellOthers(db, send, surprise, [memberId],
    withdrawNotice(surpriseId, String(surprise.get('title') ?? ''), String(member.get('firstName') ?? 'Quelqu\'un')));
}

/** The date of a surprise in preparation moved (its creator alone changes it). */
export async function onSurpriseDateChanged(
  db: Firestore, send: Sender, surpriseId: string, before: Record<string, unknown> | undefined, after: Record<string, unknown> | undefined,
): Promise<number> {
  if (!before || !after || after.status !== 'preparing' || before.date === after.date || typeof after.date !== 'string') return 0;
  const surprise = await db.collection('surprises').doc(surpriseId).get();
  return tellOthers(db, send, surprise, [String(after.creatorId ?? '')], dateChangedNotice(surpriseId, String(after.title ?? ''), after.date));
}

/**
 * A wish of « Mes envies » changed (title, link, price, note): each surprise in preparation for its
 * author that proposes it follows it (the proposal takes the live title, price and link, the notebooks
 * too) and its members learn it.
 */
export async function onWishChangedForSurprises(
  db: Firestore, send: Sender, listId: string, itemId: string,
  before: Record<string, unknown> | undefined, after: Record<string, unknown> | undefined, now: Date = new Date(),
): Promise<number> {
  if (!listId.startsWith('wishes-') || !before || !after) return 0;
  const keys = ['title', 'url', 'price', 'note'];
  if (!keys.some((k) => (before[k] ?? null) !== (after[k] ?? null))) return 0;
  const beneficiaryId = listId.slice('wishes-'.length);
  const surprises = await db.collection('surprises').where('beneficiaryId', '==', beneficiaryId).where('status', '==', 'preparing').get();
  let sent = 0;
  for (const s of surprises.docs) {
    const proposal = s.ref.collection('proposals').doc(`wish-${itemId}`);
    if (!(await proposal.get()).exists) continue;
    await proposal.update({
      title: String(after.title ?? ''), price: typeof after.price === 'number' ? after.price : null,
      url: typeof after.url === 'string' ? after.url : null,
    });
    await syncSurpriseNotebook(db, s.id, `wish-${itemId}`);
    // The proposal always follows; the members are told once every 10 minutes (C1 de #639).
    if (!(await wishNoticeDue(db, s.id, now))) continue;
    sent += await tellOthers(db, send, s, [], wishChangedNotice(s.id, String(s.get('title') ?? ''), String(after.title ?? ''), itemId));
  }
  return sent;
}

/** The members of a surprise who still take part: not « Je ne participe pas », the beneficiary never. */
async function activeMemberIds(db: Firestore, surpriseId: string, memberIds: unknown, skipMuted = false): Promise<string[]> {
  const ids = Array.isArray(memberIds) ? memberIds.filter((id): id is string => typeof id === 'string') : [];
  const rows = await db.collection('surprises').doc(surpriseId).collection('members').get();
  const row = new Map(rows.docs.map((d) => [d.id, d]));
  return ids.filter((id) => row.get(id)?.get('declined') !== true && !(skipMuted && row.get(id)?.get('muted') === true));
}

// --- Rappels (brief § 7 et § 9): at 10 h in the existing task, one a day at most, « Calme la nuit » kept ---
//
// Before the day: D-14, D-7, D-3 and the day before, to the members who have not validated yet. From the
// day itself: every day until « C'est offert », to every member. Muted (⚙ of the surprise): nothing.

/** Days from [today] to [date], both « AAAA-MM-JJ »: negative once the date has passed. */
export function daysUntil(date: string, today: string): number {
  return Math.round((Date.parse(`${date}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / 86_400_000);
}

export type ReminderKind = 'before' | 'due';

export function reminderKind(daysLeft: number): ReminderKind | null {
  if (daysLeft <= 0) return 'due';
  return [14, 7, 3, 1].includes(daysLeft) ? 'before' : null;
}

/** The text stays neutral: the lock screen of a shared phone shows no gift (audit 2.2, PRIVATE_CHANNELS). */
export function surpriseReminderNotice(surpriseId: string, title: string, daysLeft: number): Notice {
  if (daysLeft <= 0) {
    return { channel: 'gifts', title: `« ${title} » : c'est le jour J`, body: 'Une fois les cadeaux donnés, confirme « C\'est offert ».', surpriseId, tag: `surprise-${surpriseId}` };
  }
  const when = daysLeft === 1 ? 'demain' : `dans ${daysLeft} jours`;
  return {
    channel: 'gifts', title: `« ${title} » ${when}`, body: 'Regarde les propositions et valide ta participation.', surpriseId, tag: `surprise-${surpriseId}`,
  };
}

/**
 * The reminders of the day. Returns who got one, so the birthday reminders of the same task do not
 * send a second notification to them today (one a day at most).
 */
export async function sendSurpriseReminders(db: Firestore, send: Sender, now: Date): Promise<Set<string>> {
  const today = parisDay(now);
  const told = new Set<string>();
  const surprises = await db.collection('surprises').where('status', '==', 'preparing').get();
  if (surprises.empty) return told;
  const audience = await giftAudience(db);
  for (const s of surprises.docs) {
    const date = s.get('date');
    if (typeof date !== 'string') continue;
    const daysLeft = daysUntil(date, today);
    const kind = reminderKind(daysLeft);
    if (!kind) continue;
    const beneficiaryId = s.get('beneficiaryId');
    let ids = (await activeMemberIds(db, s.id, s.get('memberIds'), true)).filter((id) => id !== beneficiaryId && !told.has(id));
    if (kind === 'before') {
      const rows = await s.ref.collection('members').get();
      const validated = new Set(rows.docs.filter((d) => d.get('validated') === true).map((d) => d.id));
      ids = ids.filter((id) => !validated.has(id));
    }
    const recipients = audience(ids);
    if (recipients.length === 0) continue;
    await notify(db, send, recipients, surpriseReminderNotice(s.id, String(s.get('title') ?? ''), daysLeft), now);
    for (const id of recipients) told.add(id);
  }
  return told;
}

// --- Purge (décision du 25/11): 30 days after « C'est offert », in the existing daily task --------------

/** The photos of the offered wishes were kept for a cancel: they go with the surprise. */
export type PhotoRemover = (path: string) => Promise<void>;

export async function purgeOfferedSurprises(db: Firestore, now: Date, removePhoto: PhotoRemover): Promise<number> {
  const limit = new Date(now.getTime() - PURGE_DAYS * 86_400_000);
  const due = await db.collection('surprises').where('status', '==', 'offered').where('offeredAt', '<', limit).get();
  for (const s of due.docs) {
    const copies = await s.ref.collection('offered').get();
    for (const c of copies.docs) {
      const photo = (c.get('data') as Record<string, unknown> | undefined)?.photo;
      if (typeof photo === 'string' && photo.startsWith('giftLists/')) {
        await removePhoto(photo).catch((e) => console.error(`purge: photo ${photo}`, e));
      }
    }
    await closeOpenSurprise(db, s.id);
    await db.collection('offeredSurprises').doc(s.id).delete();
    await db.recursiveDelete(s.ref);
  }
  return due.size;
}

// --- A removed member (récit 4): the surprises for them go, they leave the others ----------------------

export async function deleteMemberSurprises(db: Firestore, memberId: string): Promise<void> {
  const forThem = await db.collection('surprises').where('beneficiaryId', '==', memberId).get();
  for (const s of forThem.docs) {
    await closeOpenSurprise(db, s.id);
    await db.collection('offeredSurprises').doc(s.id).delete();
    await db.recursiveDelete(s.ref);
  }
  await db.recursiveDelete(db.collection('giftHistory').doc(memberId));
  const inThem = await db.collection('surprises').where('memberIds', 'array-contains', memberId).get();
  for (const s of inThem.docs) {
    await s.ref.update({ memberIds: FieldValue.arrayRemove(memberId) });
    await s.ref.collection('members').doc(memberId).delete();
    const [proposals, takes] = await Promise.all([s.ref.collection('proposals').get(), s.ref.collection('takes').get()]);
    for (const p of proposals.docs) {
      const rating = p.ref.collection('ratings').doc(memberId);
      if ((await rating.get()).exists) {
        await rating.delete();
        await updateRatingAverage(db, s.id, p.id);
      }
    }
    for (const t of takes.docs) {
      // Their gift is released for the others; their part leaves the joint gift.
      if (t.get('ownerId') === memberId) {
        await db.recursiveDelete(t.ref);
        continue;
      }
      const share = t.ref.collection('shares').doc(memberId);
      if ((await share.get()).exists) {
        await share.delete();
        await updateTakeTotal(db, s.id, t.id);
      }
    }
  }
}
