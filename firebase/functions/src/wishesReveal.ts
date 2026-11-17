// « Mes envies » révélées (PRD, récit 10 bis, carte PRODUIT-20, rév. 183 and 186). « Mes envies »
// has no date: each reservation carries its occasion (the next birthday or the next Christmas
// of its owner, by default whichever comes first), and the day after it, at 7 a.m., the wish is revealed. It leaves the list for giftLists/{listId}/received/{itemId},
// the archive « On m'a offert », with the first names of the givers and never their amounts. A
// wish released before the day is not revealed; a wish nobody took stays as it is.
//
// Called by revealGifts (index.ts), after the lists. Behind the module switch config/app.features.wishesReveal, on top of the gifts module.
//
// « Idées de la famille » (récit 9 ter, behind config/app.features.singleList): the ideas taken
// for a member are revealed the same way, into the same « On m'a offert » of their « Mes envies »,
// as received/idea-{itemId}. The idea list itself stays: it is permanent, like « Mes envies ».

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
  if (config.get('features.gifts') !== true) return 0;
  const singleList = config.get('features.singleList') === true;
  // With the single list, the reveal is what tells the beneficiary: it goes with it.
  const sources: RevealSource[] = [
    ...(config.get('features.wishesReveal') === true || singleList ? ['wishes' as const] : []),
    ...(singleList ? ['ideas' as const] : []),
  ];
  if (sources.length === 0) return 0;
  const today = parisDay(now);
  const members = await loadGiftMembers(db);
  const audience = await giftAudience(db);
  let revealed = 0;
  for (const member of members) {
    // A child without the app: their adults already see everything.
    if (member.profile === 'childWithoutApp') continue;
    let count = 0;
    for (const source of sources) count += await revealWishesOf(db, member, members, today, source);
    revealed += count;
    if (count > 0 && audience([member.id]).length > 0) await notify(db, send, [member.id], wishesRevealNotice(`wishes-${member.id}`, count));
  }
  return revealed;
}

/** The list a reveal reads: « Mes envies », or the « Idées de la famille » for the member (récit 9 ter). */
export type RevealSource = 'wishes' | 'ideas';

/** Where a revealed item of [listId] is archived: the « On m'a offert » of « Mes envies »; null for any other list. */
export function receivedPath(listId: string, itemId: string): { listId: string; receivedId: string } | null {
  if (listId.startsWith('wishes-')) return { listId, receivedId: itemId };
  if (listId.startsWith('ideas-')) return { listId: `wishes-${listId.slice('ideas-'.length)}`, receivedId: `idea-${itemId}` };
  return null;
}

/**
 * Moves each taken wish (or idea, [source]) of [member] whose occasion is past to the archive of
 * « Mes envies », then removes it with its reservation.
 */
export async function revealWishesOf(
  db: Firestore, member: GiftMember, members: GiftMember[], today: string, source: RevealSource = 'wishes',
): Promise<number> {
  const list = db.collection('giftLists').doc(`${source}-${member.id}`);
  const wishes = db.collection('giftLists').doc(`wishes-${member.id}`);
  const claims = await list.collection('claims').get();
  // The archive lives under « Mes envies »: a member who never wrote a wish still gets one, empty.
  if (source === 'ideas' && !claims.empty && !(await wishes.get()).exists) {
    await wishes.create({
      kind: 'wishes', title: 'Mes envies', occasion: null, date: null, beneficiaryId: member.id, viewerIds: [],
      creatorId: member.id, creatorName: member.firstName, revealed: false,
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
    }).catch((e: { code?: number }) => {
      // Written between the read and the create (6, ALREADY_EXISTS): nothing to do.
      if (e?.code !== 6) throw e;
    });
  }
  let count = 0;
  for (const claim of claims.docs) {
    const meant = claimOccasion(claim.data(), member.birthDate, today);
    // A reservation copied from a list of an occasion (récit 9 ter) keeps the date of that list.
    const after = claim.get('revealAfter');
    const day = typeof after === 'string' ? after : occasionDay(meant, member.birthDate);
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
    const archive = receivedPath(list.id, claim.id)!;
    batch.set(wishes.collection('received').doc(archive.receivedId), {
      title: String(item.get('title') ?? ''),
      url: (item.get('url') as string | null) ?? null,
      imageUrl: (item.get('imageUrl') as string | null) ?? null,
      // Who to thank (rév. 189): ids, read by the server only to send the « Merci ».
      giverIds: [...new Set([String(claim.get('holderId') ?? ''), ...(joint ? shares.docs.map((s) => s.id) : [])])].filter((id) => id.length > 0),
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

/** « X te dit merci ! » to each giver, once: `thanked` only ever goes from false to true. */
export function thankNotice(beneficiaryName: string, title: string): Notice {
  return { channel: 'gifts', title: `${beneficiaryName} te dit merci !`, body: `Merci pour « ${title} ».` };
}

/**
 * « Merci » on « On m'a offert » (PRD rév. 189): every giver of the wish is told, the responsible
 * and each part of a joint gift. Returns the number of notifications sent.
 */
export async function thankGivers(
  db: Firestore, send: Sender, listId: string,
  before: { thanked?: unknown } | undefined, after: { thanked?: unknown; giverIds?: unknown; title?: unknown } | undefined,
): Promise<number> {
  if (!after || after.thanked !== true || before?.thanked === true) return 0;
  const list = await db.collection('giftLists').doc(listId).get();
  const beneficiaryId = String(list.get('beneficiaryId') ?? '');
  const members = await loadGiftMembers(db);
  const name = members.find((m) => m.id === beneficiaryId)?.firstName ?? '';
  if (!name) return 0;
  const ids = (Array.isArray(after.giverIds) ? after.giverIds : []).filter(
    (id): id is string => typeof id === 'string' && id !== beneficiaryId && members.some((m) => m.id === id),
  );
  const recipients = (await giftAudience(db))(ids);
  if (recipients.length === 0) return 0;
  return notify(db, send, recipients, thankNotice(name, String(after.title ?? '')));
}

/** True once the wish was revealed: its removal is not a cancelled reservation. */
export async function wasRevealed(db: Firestore, listId: string, itemId: string): Promise<boolean> {
  const archive = receivedPath(listId, itemId);
  if (!archive) return false;
  return (await db.collection('giftLists').doc(archive.listId).collection('received').doc(archive.receivedId).get()).exists;
}
