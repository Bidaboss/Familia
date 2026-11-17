// Sondages (PRD, récits 2 bis et 2 ter): who is told of a new poll, its closing at the end date,
// its result, and the event made from the date chosen. Data model: docs/data-model.md.

import { Firestore, Timestamp } from 'firebase-admin/firestore';
import { formatDay, formatTime, Notice } from './logic';
import { notify, Sender } from './store';

export interface PollData {
  kind: 'date' | 'choice';
  question: string;
  options: string[];
  multiple: boolean;
  askedIds: string[];
  viewerIds: string[];
  closed: boolean;
  chosen: number | null;
  createdEventId: string | null;
  /** The event it was asked from (récit 2 quater), or null. */
  eventId?: string | null;
  authorId: string;
  authorName: string;
}

export interface VoteData {
  memberId: string;
  answers?: ('yes' | 'maybe' | 'no')[];
  choices?: number[];
  votedBy: string;
}

/** « 2026-12-20 12:30 » → « dimanche 20 décembre à 12 h 30 ». */
export function formatOption(option: string): string {
  const [day, time] = option.split(' ');
  return time ? `${formatDay(day)} à ${formatTime(time)}` : formatDay(day);
}

export function newPollNotice(pollId: string, poll: PollData): Notice {
  const body = poll.kind === 'date'
    ? `${poll.authorName} propose des dates pour « ${poll.question} ». Dis quand tu es disponible.`
    : `${poll.authorName} te demande ton avis : « ${poll.question} »`;
  return { channel: 'events', title: 'Nouveau sondage', body, pollId };
}

/** The options with the most votes, in their order; empty when nobody voted. */
export function winners(poll: PollData, votes: VoteData[]): number[] {
  const counts = poll.options.map(() => 0);
  for (const v of votes) for (const c of v.choices ?? []) if (c >= 0 && c < counts.length) counts[c]++;
  const best = Math.max(...counts);
  return best === 0 ? [] : counts.flatMap((n, i) => (n === best ? [i] : []));
}

export function resultNotice(pollId: string, poll: PollData, votes: VoteData[]): Notice {
  const won = winners(poll, votes);
  const count = votes.filter((v) => (v.choices ?? []).includes(won[0])).length;
  const voices = `${count} voix`;
  let body: string;
  if (won.length === 0) body = `Personne n'a voté pour « ${poll.question} ».`;
  else if (won.length === 1) body = `« ${poll.question} » : ${poll.options[won[0]]} l'emporte (${voices}).`;
  else body = `« ${poll.question} » : égalité entre ${won.map((i) => poll.options[i]).join(' et ')} (${voices} chacun).`;
  return { channel: 'events', title: 'Résultat du sondage', body, pollId };
}

export function chooseDateNotice(pollId: string, poll: PollData): Notice {
  return {
    channel: 'events',
    title: 'Sondage terminé',
    body: `Les réponses pour « ${poll.question} » sont là : choisis la date.`,
    pollId,
  };
}

/**
 * Who may get a poll notification. Once the module is switched on for the family in Administration
 * (config/app.features.polls), everyone; before that, the admin principal only, the one member the
 * app already shows the module to, so a try never reaches the family (relecture de la #81).
 */
export async function pollAudience(db: Firestore): Promise<(ids: string[]) => string[]> {
  const config = await db.doc('config/app').get();
  if (config.get('features.polls') === true) return (ids) => ids;
  const owner = (await db.doc('config/family').get()).get('ownerMemberId');
  return (ids) => ids.filter((id) => typeof owner === 'string' && id === owner);
}

/** A new poll: everyone asked is told, except its author. */
export async function onPollCreated(db: Firestore, send: Sender, pollId: string, poll: PollData): Promise<number> {
  const audience = await pollAudience(db);
  const asked = audience(poll.askedIds.filter((id) => id !== poll.authorId));
  return asked.length === 0 ? 0 : notify(db, send, asked, newPollNotice(pollId, poll));
}

async function votesOf(db: Firestore, pollId: string): Promise<VoteData[]> {
  return (await db.collection('polls').doc(pollId).collection('votes').get()).docs.map((d) => d.data() as VoteData);
}

/**
 * A poll was closed. A free poll: the result goes to everyone who sees it, except the member who
 * closed it. A date poll closed by its end date: its author is asked to choose. A date chosen:
 * those who said yes to it are signed up to the event the app made (« Je viens »).
 */
export async function onPollClosed(
  db: Firestore, send: Sender, pollId: string, before: PollData, after: PollData, actorId: string | null,
): Promise<void> {
  if (after.kind === 'date' && before.chosen == null && after.chosen != null && after.createdEventId) {
    const votes = await votesOf(db, pollId);
    const batch = db.batch();
    for (const v of votes) {
      if (v.answers?.[after.chosen] !== 'yes') continue;
      batch.set(db.collection('events').doc(after.createdEventId).collection('rsvps').doc(v.memberId), {
        status: 'yes', answeredBy: v.votedBy, updatedAt: Timestamp.now(),
      });
    }
    await batch.commit();
    return;
  }
  if (before.closed || !after.closed) return;
  // Closed because its event was deleted: nobody is told of a result that no longer matters.
  if (after.eventId && !(await db.collection('events').doc(after.eventId).get()).exists) return;
  const audience = await pollAudience(db);
  if (after.kind === 'choice') {
    const to = audience(after.viewerIds.filter((id) => id !== actorId));
    if (to.length === 0) return;
    const votes = await votesOf(db, pollId);
    await notify(db, send, to, resultNotice(pollId, after, votes));
  } else if (after.chosen == null && actorId == null) {
    const to = audience([after.authorId]);
    if (to.length > 0) await notify(db, send, to, chooseDateNotice(pollId, after));
  }
}

/**
 * From the task every 30 minutes: closes the polls whose end date has passed. Only the open ones
 * are read (composite index closed + closesAt), so the read does not grow with old polls.
 */
export async function closeDuePolls(db: Firestore, now: Date): Promise<number> {
  const due = await db.collection('polls')
    .where('closed', '==', false)
    .where('closesAt', '<=', Timestamp.fromDate(now))
    .get();
  for (const doc of due.docs) await doc.ref.update({ closed: true, updatedAt: Timestamp.now() });
  return due.size;
}

/**
 * An event deleted (récit 2 quater): its polls are closed, not deleted, so that what was answered
 * stays readable. Returns how many were closed.
 */
export async function closeEventPolls(db: Firestore, eventId: string): Promise<number> {
  const open = await db.collection('polls').where('eventId', '==', eventId).where('closed', '==', false).get();
  for (const doc of open.docs) await doc.ref.update({ closed: true, updatedAt: Timestamp.now() });
  return open.size;
}

export async function deletePollVotes(db: Firestore, pollId: string): Promise<void> {
  await db.recursiveDelete(db.collection('polls').doc(pollId).collection('votes'));
}

/** A member removed from the family: their votes go too. */
export async function deleteVotesOf(db: Firestore, memberId: string): Promise<void> {
  const votes = await db.collectionGroup('votes').where('memberId', '==', memberId).get();
  for (const v of votes.docs) await v.ref.delete();
}
