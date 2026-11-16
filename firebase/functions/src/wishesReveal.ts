// « Mes envies » révélées (PRD, récit 10 bis, carte PRODUIT-20, rév. 183). « Mes envies » has no
// date: the day after the next occasion of its owner (their birthday, or Christmas), at 7 a.m.,
// each wish someone took is revealed. It leaves the list for giftLists/{listId}/received/{itemId},
// the archive « On m'a offert », with the first names of the givers and never their amounts. A
// wish released before the day is not revealed; a wish nobody took stays as it is.
//
// Called by revealGifts (index.ts), after the lists. Behind the module switch config/app.features.wishesReveal, on top of the gifts module.

import type { Firestore } from 'firebase-admin/firestore';
import { FieldValue } from 'firebase-admin/firestore';
import { giftAudience, GiftMember, loadGiftMembers, nextBirthday } from './gifts';
import { addDays, Notice, parisDay } from './logic';
import { notify, Sender } from './store';

export type RevealOccasion = 'birthday' | 'christmas';

/** The occasion [today] is the day after, for a member born on [birthDate], or null. */
export function wishesRevealOccasion(birthDate: string | null, today: string): RevealOccasion | null {
  const yesterday = addDays(today, -1);
  if (birthDate && birthDate <= yesterday && nextBirthday(birthDate, yesterday) === yesterday) return 'birthday';
  if (yesterday.endsWith('-12-25')) return 'christmas';
  return null;
}

/** The givers as the beneficiary reads them: the responsible first, then each part, once each. */
export function giverNames(holderId: string, shareIds: string[], members: GiftMember[], holderName: string): string[] {
  const name = (id: string) => members.find((m) => m.id === id)?.firstName ?? (id === holderId ? holderName : '');
  return [...new Set([holderId, ...shareIds])].map(name).filter((n) => n.length > 0);
}

export function wishesRevealNotice(listId: string, count: number): Notice {
  return {
    channel: 'gifts',
    title: count > 1 ? `Découvre qui t'a offert tes ${count} envies` : 'Découvre qui t\'a offert ton envie',
    body: 'Tes envies réservées sont dans « On m\'a offert », avec les prénoms de ceux qui les ont prises.',
    listId,
  };
}

/** Every morning: the wishes of whoever had an occasion yesterday. Returns the number revealed. */
export async function revealWishes(db: Firestore, send: Sender, now: Date): Promise<number> {
  const config = await db.doc('config/app').get();
  if (config.get('features.gifts') !== true || config.get('features.wishesReveal') !== true) return 0;
  const today = parisDay(now);
  const members = await loadGiftMembers(db);
  const audience = await giftAudience(db);
  let revealed = 0;
  for (const member of members) {
    // A child without the app: their adults already see everything.
    if (member.profile === 'childWithoutApp') continue;
    const occasion = wishesRevealOccasion(member.birthDate, today);
    if (!occasion) continue;
    const count = await revealWishesOf(db, member.id, members, occasion, Number(addDays(today, -1).slice(0, 4)));
    revealed += count;
    if (count > 0 && audience([member.id]).length > 0) await notify(db, send, [member.id], wishesRevealNotice(`wishes-${member.id}`, count));
  }
  return revealed;
}

/** Moves each taken wish of [memberId] to the archive, then removes it with its reservation. */
export async function revealWishesOf(
  db: Firestore, memberId: string, members: GiftMember[], occasion: RevealOccasion, year: number,
): Promise<number> {
  const list = db.collection('giftLists').doc(`wishes-${memberId}`);
  const claims = await list.collection('claims').get();
  let count = 0;
  for (const claim of claims.docs) {
    const item = await list.collection('items').doc(claim.id).get();
    if (!item.exists) {
      await db.recursiveDelete(claim.ref);
      continue;
    }
    const shares = await claim.ref.collection('shares').get();
    const joint = claim.get('type') === 'shared';
    // The archive, the reservation and the item in one batch: a failure leaves the wish as it
    // was, never in both places. The triggers of the removal read the archive to keep the
    // notebooks of the givers and to warn nobody (see gifts.ts and notebook.ts); the parts go
    // after it, for the same reason.
    const batch = db.batch();
    batch.set(list.collection('received').doc(claim.id), {
      title: String(item.get('title') ?? ''),
      url: (item.get('url') as string | null) ?? null,
      imageUrl: (item.get('imageUrl') as string | null) ?? null,
      givers: giverNames(String(claim.get('holderId') ?? ''), joint ? shares.docs.map((s) => s.id) : [], members, String(claim.get('holderName') ?? '')),
      joint,
      occasion,
      year,
      thanked: false,
      revealedAt: FieldValue.serverTimestamp(),
    });
    batch.delete(claim.ref);
    batch.delete(item.ref);
    await batch.commit();
    await db.recursiveDelete(claim.ref.collection('shares'));
    count++;
  }
  return count;
}

/** True once the wish was revealed: its removal is not a cancelled reservation. */
export async function wasRevealed(db: Firestore, listId: string, itemId: string): Promise<boolean> {
  if (!listId.startsWith('wishes-')) return false;
  return (await db.collection('giftLists').doc(listId).collection('received').doc(itemId).get()).exists;
}
