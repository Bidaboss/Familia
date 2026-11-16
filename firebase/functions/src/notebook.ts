// Mon carnet de cadeaux (PRD, récit 9 bis): a member's reservations and parts of joint gifts are
// taken up in their private notebook, notebooks/{memberId}/entries, and leave it when released.
//
// One entry per member and item, with the id gift_{listId}_{itemId}. The member follows it (state,
// occasion, note); the server keeps what comes from the list (title, link, price, person).

import type { Firestore } from 'firebase-admin/firestore';
import { FieldValue } from 'firebase-admin/firestore';
import { parisDay } from './logic';
import { claimOccasion, wasRevealed } from './wishesReveal';

export type NotebookSource = 'claim' | 'share';

/** What the list says about a gift that a member reserved or takes part in. */
export interface TakenUp {
  source: NotebookSource;
  listId: string;
  itemId: string;
  personId: string;
  personName: string;
  occasion: string | null;
  year: number | null;
  title: string;
  url: string | null;
  price: number | null;
}

export function notebookEntryId(listId: string, itemId: string): string {
  return `gift_${listId}_${itemId}`;
}

/**
 * The fields the server writes. The occasion of a gift of « Mes envies » is the one of its
 * reservation (rév. 186), which the holder changes from the notebook; [current], the stored entry,
 * only fills in when neither the list nor the reservation has one.
 */
export function takenUpFields(t: TakenUp, current: { occasion?: unknown; year?: unknown } | undefined): Record<string, unknown> {
  const listHasOccasion = t.occasion !== null;
  return {
    source: t.source,
    listId: t.listId,
    itemId: t.itemId,
    personId: t.personId,
    personName: t.personName,
    occasion: listHasOccasion ? t.occasion : (current?.occasion ?? null),
    year: listHasOccasion ? t.year : (current?.year ?? null),
    title: t.title,
    url: t.url,
    price: t.price,
  };
}

/** The year of an occasion date « AAAA-MM-JJ », or null. */
export function yearOf(date: unknown): number | null {
  if (typeof date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(date)) return null;
  return Number(date.slice(0, 4));
}

/** What [memberId] has on this item: their reservation, their part, or nothing. */
async function takenUpBy(db: Firestore, listId: string, itemId: string, memberId: string): Promise<TakenUp | null> {
  const listRef = db.collection('giftLists').doc(listId);
  const claimRef = listRef.collection('claims').doc(itemId);
  const [list, item, claim, share] = await Promise.all([
    listRef.get(), listRef.collection('items').doc(itemId).get(), claimRef.get(), claimRef.collection('shares').doc(memberId).get(),
  ]);
  if (!list.exists || !item.exists || !claim.exists) return null;
  const solo = claim.get('type') === 'solo';
  const holds = claim.get('holderId') === memberId;
  if (solo ? !holds : !(holds || share.exists)) return null;
  const beneficiaryId = String(list.get('beneficiaryId') ?? '');
  const beneficiary = await db.collection('members').doc(beneficiaryId).get();
  const price = item.get('price');
  // « Mes envies » and the « Idées de la famille »: the occasion of the reservation, the same for its holder and every part (rév. 186).
  const wish = list.get('kind') === 'wishes' || list.get('kind') === 'ideas'
    ? claimOccasion(claim.data() ?? {}, (beneficiary.get('birthDate') as string | null | undefined) ?? null, parisDay(new Date()))
    : null;
  const amount = share.exists ? Number(share.get('amount')) : NaN;
  return {
    source: solo ? 'claim' : 'share',
    listId,
    itemId,
    personId: beneficiaryId,
    personName: String(beneficiary.get('firstName') ?? list.get('creatorName') ?? ''),
    occasion: wish ? wish.occasion : ((list.get('occasion') as string | null) ?? null),
    year: wish ? wish.year : yearOf(list.get('date')),
    title: String(item.get('title') ?? ''),
    url: (item.get('url') as string | null) ?? null,
    // Alone, the whole price; in a joint gift, the member's own part (none yet for a responsible).
    price: solo ? (typeof price === 'number' ? price : null) : (Number.isFinite(amount) ? amount : null),
  };
}

/** Brings the notebook entry of [memberId] for this item in line with the list. */
export async function syncNotebookEntry(db: Firestore, listId: string, itemId: string, memberId: string): Promise<void> {
  if (!memberId) return;
  const ref = db.collection('notebooks').doc(memberId).collection('entries').doc(notebookEntryId(listId, itemId));
  const [taken, current] = await Promise.all([takenUpBy(db, listId, itemId, memberId), ref.get()]);
  if (!taken) {
    // A revealed wish stays in the notebook of its givers: it was given, not released.
    if (current.exists && !(await wasRevealed(db, listId, itemId))) await ref.delete();
    return;
  }
  const fields = takenUpFields(taken, current.exists ? current.data() : undefined);
  if (current.exists) {
    const unchanged = Object.entries(fields).every(([key, value]) => current.get(key) === value);
    if (!unchanged) await ref.update({ ...fields, updatedAt: FieldValue.serverTimestamp() });
    return;
  }
  await ref.set({
    ...fields, note: null, status: 'idea', createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
  });
}

/** After a change of the item itself: everyone who reserved it or takes part sees the new title or price. */
export async function syncNotebookItem(db: Firestore, listId: string, itemId: string): Promise<void> {
  const claimRef = db.collection('giftLists').doc(listId).collection('claims').doc(itemId);
  const [claim, shares] = await Promise.all([claimRef.get(), claimRef.collection('shares').get()]);
  if (!claim.exists) return;
  const ids = new Set<string>([String(claim.get('holderId') ?? ''), ...shares.docs.map((s) => s.id)]);
  for (const id of ids) await syncNotebookEntry(db, listId, itemId, id);
}

/** A removed member: their notebook goes with them. */
export async function deleteMemberNotebook(db: Firestore, memberId: string): Promise<void> {
  await db.recursiveDelete(db.collection('notebooks').doc(memberId));
}
