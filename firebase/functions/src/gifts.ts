// Listes et cadeaux (PRD, récits 9 to 10 bis): the reveal the day after the occasion, the
// birthday reminders at D-21 and D-14, the warnings to whoever reserved an item that its
// beneficiary changed, and the cleanup of what a deleted list or item leaves behind.
//
// The beneficiary of a list never gets any notification about their gifts (récit 10).

import type { DocumentSnapshot, Firestore } from 'firebase-admin/firestore';
import { addDays, formatDay, Notice, parisDay, Profile } from './logic';
import { notify, Sender } from './store';

export interface GiftMember {
  id: string;
  firstName: string;
  profile: Profile;
  parentIds: string[];
  birthDate: string | null; // AAAA-MM-JJ
}

export interface GiftItemData {
  title: string;
  url: string | null;
  price: number | null;
  note: string | null;
  authorId: string;
  visibleToBeneficiary: boolean;
}

// --- Pure logic -------------------------------------------------------------------------------

/** The next birthday on or after [today]; 29 February is celebrated on 28 February in other years. */
export function nextBirthday(birthDate: string, today: string): string {
  const [, m, d] = birthDate.split('-').map(Number);
  const year = Number(today.slice(0, 4));
  const on = (y: number) => {
    const last = new Date(Date.UTC(y, m, 0)).getUTCDate();
    return `${y}-${String(m).padStart(2, '0')}-${String(Math.min(d, last)).padStart(2, '0')}`;
  };
  const thisYear = on(year);
  return thisYear >= today ? thisYear : on(year + 1);
}

/** Members whose birthday is exactly [days] days after [today]. */
export function birthdaysIn(members: GiftMember[], today: string, days: number): GiftMember[] {
  const target = addDays(today, days);
  return members.filter((m) => m.birthDate && m.birthDate <= today && nextBirthday(m.birthDate, today) === target);
}

/** PRD, récit 10: the adults are reminded, never the member whose birthday it is. */
export function birthdayRecipients(members: GiftMember[], birthdayId: string): string[] {
  return members.filter((m) => m.profile === 'adult' && m.id !== birthdayId).map((m) => m.id);
}

export function birthdayNotice(member: GiftMember, birthday: string, second: boolean, wishesListId: string | null): Notice {
  return {
    channel: 'gifts',
    title: second
      ? `Anniversaire de ${member.firstName} dans 2 semaines`
      : `Anniversaire de ${member.firstName} dans 3 semaines`,
    body: second
      ? `Rien n'est encore réservé pour le ${formatDay(birthday)}. Regarde ses envies, ou prépare une liste surprise.`
      : `C'est le ${formatDay(birthday)}. Regarde ses envies, ou prépare une liste surprise « Anniversaire de ${member.firstName} ».`,
    ...(wishesListId ? { listId: wishesListId } : {}),
  };
}

/** A list is revealed the day after its occasion. « Mes envies » has no date and is never revealed this way. */
export function isDueForReveal(list: { kind?: string; date?: string | null; revealed?: boolean }, today: string): boolean {
  return list.kind !== 'wishes' && list.revealed !== true && typeof list.date === 'string' && list.date < today;
}

export function revealNotice(listId: string, title: string): Notice {
  return {
    channel: 'gifts',
    title: `« ${title} » : découvre tes cadeaux`,
    body: 'L\'occasion est passée : tu peux voir qui t\'a offert quoi, pour remercier.',
    listId,
  };
}

/** Only what the reservers see counts: a change of updatedAt alone warns nobody. */
export function itemChanged(before: GiftItemData, after: GiftItemData): boolean {
  const keys: (keyof GiftItemData)[] = ['title', 'url', 'price', 'note'];
  return keys.some((k) => (before[k] ?? null) !== (after[k] ?? null));
}

export function itemNotice(kind: 'edited' | 'deleted', listId: string, beneficiaryName: string, title: string): Notice {
  return kind === 'deleted'
    ? {
      channel: 'gifts',
      title: 'Réservation annulée',
      body: `${beneficiaryName} a retiré « ${title} » de sa liste. Ta réservation est annulée.`,
      listId,
    }
    : {
      channel: 'gifts',
      title: 'Article modifié',
      body: `${beneficiaryName} a modifié « ${title} », que tu as réservé. Jette un œil avant de l'acheter.`,
      listId,
    };
}

// --- Firestore --------------------------------------------------------------------------------

export async function loadGiftMembers(db: Firestore): Promise<GiftMember[]> {
  const snapshot = await db.collection('members').get();
  return snapshot.docs.map((doc) => ({
    id: doc.id,
    firstName: String(doc.get('firstName') ?? ''),
    profile: doc.get('profile'),
    parentIds: (doc.get('parentIds') as string[] | undefined) ?? [],
    birthDate: (doc.get('birthDate') as string | null | undefined) ?? null,
  }));
}

/** Every morning: lists whose occasion was yesterday or before are revealed, and their beneficiary told. */
export async function revealGiftLists(db: Firestore, send: Sender, now: Date): Promise<number> {
  const today = parisDay(now);
  const lists = await db.collection('giftLists').where('revealed', '==', false).get();
  const due = lists.docs.filter((doc) => isDueForReveal(doc.data(), today));
  if (due.length === 0) return 0;
  const members = await loadGiftMembers(db);
  for (const list of due) {
    await list.ref.update({ revealed: true });
    const beneficiary = members.find((m) => m.id === list.get('beneficiaryId'));
    if (beneficiary && beneficiary.profile !== 'childWithoutApp') {
      await notify(db, send, [beneficiary.id], revealNotice(list.id, String(list.get('title') ?? '')));
    }
  }
  return due.length;
}

/** PRD, récit 10: at D-21 every adult but the birthday member is reminded; at D-14 again if nothing is reserved. */
export async function sendBirthdayGiftReminders(db: Firestore, send: Sender, now: Date): Promise<number> {
  const today = parisDay(now);
  const members = await loadGiftMembers(db);
  let sent = 0;
  for (const [days, second] of [[21, false], [14, true]] as const) {
    for (const member of birthdaysIn(members, today, days)) {
      if (second && (await hasReservation(db, member.id))) continue;
      const wishes = await db.doc(`giftLists/wishes-${member.id}`).get();
      const notice = birthdayNotice(member, addDays(today, days), second, wishes.exists ? wishes.id : null);
      sent += await notify(db, send, birthdayRecipients(members, member.id), notice);
    }
  }
  return sent;
}

/** True when any item of the current lists of [memberId] is reserved. */
async function hasReservation(db: Firestore, memberId: string): Promise<boolean> {
  const lists = await db.collection('giftLists').where('beneficiaryId', '==', memberId).where('revealed', '==', false).get();
  for (const list of lists.docs) {
    const claims = await list.ref.collection('claims').limit(1).get();
    if (!claims.empty) return true;
  }
  return false;
}

/** A deleted list takes its items, reservations and parts with it. */
export async function deleteGiftListContent(db: Firestore, listId: string): Promise<void> {
  const list = db.collection('giftLists').doc(listId);
  await db.recursiveDelete(list.collection('items'));
  await db.recursiveDelete(list.collection('claims'));
}

/** The parts of a released joint gift go with it. */
export async function deleteClaimShares(db: Firestore, listId: string, itemId: string): Promise<void> {
  await db.recursiveDelete(db.collection('giftLists').doc(listId).collection('claims').doc(itemId).collection('shares'));
}

/** Who reserved the item, or took part in its joint gift. */
async function reservers(db: Firestore, listId: string, itemId: string): Promise<{ claim: DocumentSnapshot; ids: string[] }> {
  const claim = await db.collection('giftLists').doc(listId).collection('claims').doc(itemId).get();
  if (!claim.exists) return { claim, ids: [] };
  const shares = await claim.ref.collection('shares').get();
  const ids = new Set<string>([String(claim.get('holderId')), ...shares.docs.map((s) => s.id)]);
  return { claim, ids: [...ids] };
}

/**
 * The beneficiary's side may change or remove a reserved item (a refusal would give the surprise
 * away, see firestore.rules): whoever reserved it is warned. A removed item also loses its
 * reservation and parts.
 */
export async function onGiftItemChanged(
  db: Firestore, send: Sender, listId: string, itemId: string, before: GiftItemData | undefined, after: GiftItemData | undefined,
): Promise<number> {
  if (!before) return 0;
  if (after && !itemChanged(before, after)) return 0;
  const { claim, ids } = await reservers(db, listId, itemId);
  let sent = 0;
  if (ids.length > 0) {
    const list = await db.collection('giftLists').doc(listId).get();
    const beneficiaryId = String(list.get('beneficiaryId') ?? '');
    const members = await loadGiftMembers(db);
    const name = members.find((m) => m.id === beneficiaryId)?.firstName ?? 'Le bénéficiaire';
    // Only the reservers learn it, never the beneficiary, nor the author of the change.
    const recipients = ids.filter((id) => id !== beneficiaryId && id !== before.authorId);
    const title = (after ?? before).title;
    if (recipients.length > 0) sent = await notify(db, send, recipients, itemNotice(after ? 'edited' : 'deleted', listId, name, title));
  }
  if (!after && claim.exists) await db.recursiveDelete(claim.ref);
  return sent;
}

/** A removed member: the lists made for them go too. */
export async function deleteMemberGiftLists(db: Firestore, memberId: string): Promise<void> {
  const lists = await db.collection('giftLists').where('beneficiaryId', '==', memberId).get();
  for (const list of lists.docs) await db.recursiveDelete(list.ref);
}
