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

/** A new poll: everyone asked is told, except its author. */
export async function onPollCreated(db: Firestore, send: Sender, pollId: string, poll: PollData): Promise<number> {
  const asked = poll.askedIds.filter((id) => id !== poll.authorId);
  return notify(db, send, asked, newPollNotice(pollId, poll));
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
  if (after.kind === 'choice') {
    const votes = await votesOf(db, pollId);
    await notify(db, send, after.viewerIds.filter((id) => id !== actorId), resultNotice(pollId, after, votes));
  } else if (after.chosen == null && actorId == null) {
    await notify(db, send, [after.authorId], chooseDateNotice(pollId, after));
  }
}

/** From the task every 30 minutes: closes the polls whose end date has passed. */
export async function closeDuePolls(db: Firestore, now: Date): Promise<number> {
  const due = await db.collection('polls').where('closesAt', '<=', Timestamp.fromDate(now)).get();
  let closed = 0;
  for (const doc of due.docs) {
    if (doc.get('closed') === true) continue;
    await doc.ref.update({ closed: true, updatedAt: Timestamp.now() });
    closed++;
  }
  return closed;
}

export async function deletePollVotes(db: Firestore, pollId: string): Promise<void> {
  await db.recursiveDelete(db.collection('polls').doc(pollId).collection('votes'));
}

/** A member removed from the family: their votes go too. */
export async function deleteVotesOf(db: Firestore, memberId: string): Promise<void> {
  const votes = await db.collectionGroup('votes').where('memberId', '==', memberId).get();
  for (const v of votes.docs) await v.ref.delete();
}
