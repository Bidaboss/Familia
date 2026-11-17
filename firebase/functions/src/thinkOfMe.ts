// « Pensez à moi » (PRD, récit 43, idée 4 de l'audit engagement). A member notes an important
// moment (moments/{id}: an exam, a match, a first day). On the day, at 10 h, the family with the app
// is invited to send an encouragement; each one (momentCheers/{momentId}-{authorId}) tells the
// person, with the names of all who wrote. Never a count, never who did not write, no reminder
// the next day. A parent notes it for a child; from 15 years old the teenager accepts it first
// (status 'pending' until then), or notes it themselves.

import type { Firestore } from 'firebase-admin/firestore';
import { parisDay, type Notice } from './logic';
import { featureOn, notify, type Sender } from './store';
import { familyMembers } from './audience';

export const THINK_OF_ME = 'thinkOfMe';

export interface MomentCheer {
  momentId: string;
  forMemberId: string;
  authorId: string;
  authorName: string;
  text: string;
}

export function invitationNotice(firstName: string, title: string, memberId: string): Notice {
  return {
    channel: 'thinkOfMe',
    title: `Pensez à ${firstName} aujourd'hui`,
    body: `« ${title} » : envoie-lui un encouragement.`,
    thinkOfMeOf: memberId,
  };
}

/** « Inès et Noé pensent à toi. »: names, never a number. */
export function cheersNotice(memberId: string, momentId: string, authors: string[]): Notice {
  const names = authors.length <= 1
    ? authors.join('')
    : `${authors.slice(0, -1).join(', ')} et ${authors[authors.length - 1]}`;
  return {
    channel: 'thinkOfMe',
    title: 'Pensez à moi',
    body: authors.length <= 1 ? `${names} pense à toi.` : `${names} pensent à toi.`,
    thinkOfMeOf: memberId,
    tag: `thinkOfMe-${momentId}`,
    // Written for me: it reaches me even at « Presque rien » (récit 6 quinquies).
    direct: true,
  };
}

/**
 * From the task of 10 h: each accepted moment of the day is started once (startedAt, which opens
 * the encouragements for 24 h in the rules), and the family with the app, not the person, is
 * invited. Returns the moments started.
 */
export async function startMoments(db: Firestore, send: Sender, now: Date): Promise<string[]> {
  if (!(await featureOn(db, THINK_OF_ME))) return [];
  const today = parisDay(now);
  const moments = await db.collection('moments').where('day', '==', today).get();
  const todo = moments.docs.filter((m) => m.get('status') === 'accepted' && !m.get('startedAt'));
  if (todo.length === 0) return [];
  const members = await familyMembers(db);
  const started: string[] = [];
  for (const moment of todo) {
    const forMemberId = String(moment.get('forMemberId'));
    const person = members.find((m) => m.id === forMemberId);
    if (!person) continue;
    try {
      // Only if nobody started it meanwhile: the update time must not have moved.
      await moment.ref.update({ startedAt: now }, { lastUpdateTime: moment.updateTime });
    } catch (e) {
      if ((e as { code?: number }).code === 9) continue; // Started by another run.
      throw e;
    }
    started.push(moment.id);
    const withApp = members
      .filter((m) => m.id !== forMemberId && m.get('profile') !== 'childWithoutApp')
      .map((m) => m.id);
    const firstName = String(person.get('firstName') ?? '');
    await notify(db, send, withApp, invitationNotice(firstName, String(moment.get('title') ?? ''), forMemberId));
  }
  return started;
}

/** A new encouragement: the person is told, with the names of all who wrote for that moment. */
export async function onMomentCheerCreated(db: Firestore, send: Sender, cheer: MomentCheer): Promise<boolean> {
  if (!(await featureOn(db, THINK_OF_ME))) return false;
  const person = await db.doc(`members/${cheer.forMemberId}`).get();
  if (!person.exists || person.get('profile') === 'childWithoutApp') return false;
  const cheers = await db.collection('momentCheers').where('momentId', '==', cheer.momentId).get();
  const authors = cheers.docs
    .sort((a, b) => a.createTime.toMillis() - b.createTime.toMillis())
    .map((c) => String(c.get('authorName') ?? ''));
  await notify(db, send, [cheer.forMemberId], cheersNotice(cheer.forMemberId, cheer.momentId, authors));
  return true;
}

/** A moment removed (taken back, refused) takes its encouragements with it. */
export async function deleteCheersOf(db: Firestore, momentId: string): Promise<void> {
  const cheers = await db.collection('momentCheers').where('momentId', '==', momentId).get();
  await Promise.all(cheers.docs.map((c) => c.ref.delete()));
}

/** A member removed takes their moments, and the encouragements they wrote or received. */
export async function deleteMomentsOf(db: Firestore, memberId: string): Promise<void> {
  for (const field of ['forMemberId', 'authorId']) {
    const moments = await db.collection('moments').where(field, '==', memberId).get();
    await Promise.all(moments.docs.map((m) => m.ref.delete()));
    const cheers = await db.collection('momentCheers').where(field, '==', memberId).get();
    await Promise.all(cheers.docs.map((c) => c.ref.delete()));
  }
}
