// « Mes envies » révélées (PRD, récit 10 bis, carte PRODUIT-20, rév. 183 and 186). « Mes envies »
// has no date: each reservation carries its occasion (the next birthday or the next Christmas
// of its owner, by default whichever comes first), and the day after it, at 7 a.m., the wish is revealed. It leaves the list for giftLists/{listId}/received/{itemId},
// the archive « On m'a offert », with the first names of the givers and never their amounts. A
// wish released before the day is not revealed; a wish nobody took stays as it is.
//
// Called by revealGifts (index.ts), after the lists. Behind the module switch config/app.features.wishesReveal, on top of the gifts module.

import type { Firestore } from 'firebase-admin/firestore';
import { FieldValue } from 'firebase-admin/firestore';
import { giftAudience, GiftMember, loadGiftMembers, nextBirthday } from './gifts';
import { Notice, parisDay } from './logic';
import { notify, Sender } from './store';

export type RevealOccasion = 'birthday' | 'christmas';

/** The occasion a reservation of « Mes envies » is meant for (PRD rév. 186). */
export interface WishOccasion {
  occasion: RevealOccasion;
  year: number;
}

/** The day of [o], AAAA-MM-JJ; null for a birthday without a birth date. */
export function occasionDay(o: WishOccasion, birthDate: string | null): string | null {
  if (o.occasion === 'christmas') return `${o.year}-12-25`;
  return birthDate ? nextBirthday(birthDate, `${o.year}-01-01`) : null;
}

/** By default, the next occasion of the beneficiary on [day]: their birthday or Christmas, whichever comes first. */
export function defaultWishOccasion(birthDate: string | null, day: string): WishOccasion {
  const christmas = nextChristmas(day);
  if (!birthDate) return christmas;
  const birthday = nextBirthday(birthDate, day);
  return birthday < `${christmas.year}-12-25` ? { occasion: 'birthday', year: Number(birthday.slice(0, 4)) } : christmas;
}

/** The next Christmas on or after [day]. */
function nextChristmas(day: string): WishOccasion {
  const year = Number(day.slice(0, 4));
  return { occasion: 'christmas', year: day <= `${year}-12-25` ? year : year + 1 };
}

/**
 * The occasion of a reservation (PRD rév. 186): the next birthday or the next Christmas after the
 * day it was made, as its holder chose in `occasion`; without a choice, whichever comes first
 * (reservations made before rév. 186 have none). [fallbackDay] stands for a missing date.
 */
export function claimOccasion(claim: { occasion?: unknown; createdAt?: unknown }, birthDate: string | null, fallbackDay: string): WishOccasion {
  const created = claim.createdAt as { toDate?: () => Date } | undefined;
  const day = typeof created?.toDate === 'function' ? parisDay(created.toDate()) : fallbackDay;
  if (claim.occasion === 'christmas') return nextChristmas(day);
  if (claim.occasion === 'birthday' && birthDate) return { occasion: 'birthday', year: Number(nextBirthday(birthDate, day).slice(0, 4)) };
  return defaultWishOccasion(birthDate, day);
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

/**
 * Every morning: each reservation whose occasion was yesterday, or before (a day the switch was
 * off), is revealed. A gift meant for Christmas stays hidden after an earlier birthday. Returns
 * the number revealed.
 */
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
    const count = await revealWishesOf(db, member, members, today);
    revealed += count;
    if (count > 0 && audience([member.id]).length > 0) await notify(db, send, [member.id], wishesRevealNotice(`wishes-${member.id}`, count));
  }
  return revealed;
}

/** Moves each taken wish of [member] whose occasion is past to the archive, then removes it with its reservation. */
export async function revealWishesOf(db: Firestore, member: GiftMember, members: GiftMember[], today: string): Promise<number> {
  const list = db.collection('giftLists').doc(`wishes-${member.id}`);
  const claims = await list.collection('claims').get();
  let count = 0;
  for (const claim of claims.docs) {
    const meant = claimOccasion(claim.data(), member.birthDate, today);
    const day = occasionDay(meant, member.birthDate);
    if (!day || day >= today) continue;
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
      occasion: meant.occasion,
      year: meant.year,
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
