// Vœux d'anniversaire (PRD, récit 36, avis EXPERT n° 11). On the day, at 10 h, Familia publishes a
// Bonne nouvelle « Joyeux anniversaire » for the person (goodNews/birthday-{year}-{memberId}) and
// tells the family « C'est l'anniversaire de Léa, envoie-lui tes vœux ». Each wish is a card
// (birthdayCards/{year}-{forMemberId}-{authorId}); the rules accept one only while that Bonne
// nouvelle exists. The person is told of each card, the notices of the day replacing each other.
// Never a count, never who did not send one, and no reminder.

import type { Firestore } from 'firebase-admin/firestore';
import { nextBirthday } from './gifts';
import { parisDay, type Notice } from './logic';
import { featureOn, notify, type Sender } from './store';

export const BIRTHDAY_WISHES = 'birthdayWishes';

export interface BirthdayCard {
  forMemberId: string;
  authorId: string;
  authorName: string;
  text: string;
  year: number;
}

export function birthdayNewsId(year: number, memberId: string): string {
  return `birthday-${year}-${memberId}`;
}

/** The members whose birthday is [today]: a birth date in the future (a typing mistake) gives none. */
export function birthdaysOn(members: { id: string; birthDate?: string | null }[], today: string): string[] {
  return members
    .filter((m) => !!m.birthDate && m.birthDate < today && nextBirthday(m.birthDate, today) === today)
    .map((m) => m.id);
}

export function invitationNotice(firstName: string, memberId: string): Notice {
  return {
    channel: 'birthdays',
    title: `C'est l'anniversaire de ${firstName}`,
    body: 'Envoie-lui tes vœux.',
    birthdayOf: memberId,
  };
}

/** « Paméla, Vincent et Ophélie t'ont envoyé une carte de vœux. »: names, never a number. */
export function cardsNotice(memberId: string, year: number, authors: string[]): Notice {
  const names = authors.length <= 1
    ? authors.join('')
    : `${authors.slice(0, -1).join(', ')} et ${authors[authors.length - 1]}`;
  return {
    channel: 'birthdays',
    title: 'Joyeux anniversaire !',
    body: authors.length <= 1 ? `${names} t'a envoyé une carte de vœux.` : `${names} t'ont envoyé une carte de vœux.`,
    birthdayOf: memberId,
    tag: `birthdayCards-${year}`,
  };
}

async function mutedOf(db: Firestore, ids: string[]): Promise<Set<string>> {
  if (ids.length === 0) return new Set();
  const settings = await db.getAll(...ids.map((id) => db.doc(`members/${id}/settings/notifications`)));
  return new Set(settings.filter((s) => s.exists && s.get(BIRTHDAY_WISHES) === false).map((s) => s.ref.parent.parent!.id));
}

/**
 * From the task of 10 h: for each birthday of the day, the Bonne nouvelle, once, then the family
 * with the app (not the person, not those who turned `birthdayWishes` off) is invited. Returns the
 * members celebrated.
 */
export async function startBirthdays(db: Firestore, send: Sender, now: Date): Promise<string[]> {
  if (!(await featureOn(db, BIRTHDAY_WISHES))) return [];
  const today = parisDay(now);
  const year = Number(today.slice(0, 4));
  const members = await db.collection('members').get();
  const all = members.docs.map((m) => ({ id: m.id, birthDate: m.get('birthDate') as string | null | undefined }));
  const celebrated: string[] = [];
  for (const id of birthdaysOn(all, today)) {
    const member = members.docs.find((m) => m.id === id)!;
    const firstName = String(member.get('firstName') ?? '');
    try {
      await db.collection('goodNews').doc(birthdayNewsId(year, id)).create({
        text: `🎂 Joyeux anniversaire ${firstName} !`,
        photoIds: [], authorId: 'familia', authorName: 'Familia', forMemberId: id,
        householdId: (member.get('householdId') as string | null | undefined) ?? null,
        mentionIds: [], reactions: {}, hiddenByIds: [], birthdayOf: id, createdAt: now, updatedAt: now,
      });
    } catch (e) {
      if ((e as { code?: number }).code === 6) continue; // Started before today.
      throw e;
    }
    celebrated.push(id);
    const withApp = members.docs.filter((m) => m.id !== id && m.get('profile') !== 'childWithoutApp').map((m) => m.id);
    const muted = await mutedOf(db, withApp);
    await notify(db, send, withApp.filter((m) => !muted.has(m)), invitationNotice(firstName, id));
  }
  return celebrated;
}

/** A new card: the person is told, with the names of all who wrote that day, in one notice. */
export async function onBirthdayCardCreated(db: Firestore, send: Sender, card: BirthdayCard): Promise<boolean> {
  if (!(await featureOn(db, BIRTHDAY_WISHES))) return false;
  const person = await db.doc(`members/${card.forMemberId}`).get();
  if (!person.exists || person.get('profile') === 'childWithoutApp') return false;
  if ((await mutedOf(db, [card.forMemberId])).has(card.forMemberId)) return false;
  const cards = await db.collection('birthdayCards')
    .where('forMemberId', '==', card.forMemberId).where('year', '==', card.year).get();
  const authors = cards.docs
    .sort((a, b) => a.createTime.toMillis() - b.createTime.toMillis())
    .map((c) => String(c.get('authorName') ?? ''));
  await notify(db, send, [card.forMemberId], cardsNotice(card.forMemberId, card.year, authors));
  return true;
}

/** A member who leaves takes the cards they wrote and received with them (carte EXPERT-1). */
export async function deleteBirthdayCardsOf(db: Firestore, memberId: string): Promise<void> {
  for (const field of ['authorId', 'forMemberId']) {
    const cards = await db.collection('birthdayCards').where(field, '==', memberId).get();
    await Promise.all(cards.docs.map((c) => c.ref.delete()));
  }
}
