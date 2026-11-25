// Coups de main (PRD, récits 13 à 18): who is told what, and when. The pure part decides the
// recipients and the French texts; the store part reads Firestore and sends. Only the people
// concerned are told (AVIS-7): never the whole family.

import type { Firestore } from 'firebase-admin/firestore';
import { addDays, formatDay, formatTime, parisDay, type Channel } from './logic';
import { newlyNamed, notePreview, noteMentionTitle } from './mentions';
import { deliver } from './prefs';

export interface HelpRequestData {
  kind: 'childcare' | 'ride' | 'other';
  title: string;
  date: string; // AAAA-MM-JJ
  startTime: string | null; // HH:MM
  endTime: string | null;
  place: string | null;
  authorId: string;
  authorName: string;
  forMemberId: string;
  notifyIds: string[];
  status: 'open' | 'taken' | 'cancelled';
  volunteerId: string | null;
  volunteerName: string | null;
  /** The volunteer's short word (récit 16, spec rév. 8), absent on older requests. */
  volunteerNote?: string | null;
  declinedBy: string | null;
  /** « Pas dispo » (présentation R1 bis) from people warned among several: nobody is told. */
  unavailableIds?: string[];
  thankedAt: unknown | null;
  note?: string | null;
  /** The members named « @Prénom » in the note (l'admin, 19/11), all among notifyIds. */
  mentionIds?: string[];
}

export interface HelpMember {
  id: string;
  firstName: string;
  profile: string;
  parentIds: string[];
}

export interface HelpNotice {
  /** `helpRequests` (Entraide); the reminder of the evening before stays on `reminders`. */
  channel: Channel;
  title: string;
  body: string;
  helpRequestId: string;
  /** To the volunteer, about the coup de main they took: never muted (récit 6 quinquies). */
  direct?: boolean;
}

export interface HelpMessage {
  recipientIds: string[];
  notice: HelpNotice;
}

/** "mardi 2 décembre de 14 h 00 à 18 h 00", "mardi 2 décembre dès 9 h 00" or the day alone. */
export function helpWhen(r: Pick<HelpRequestData, 'date' | 'startTime' | 'endTime'>): string {
  const day = formatDay(r.date);
  if (r.startTime && r.endTime) return `${day} de ${formatTime(r.startTime)} à ${formatTime(r.endTime)}`;
  if (r.startTime) return `${day} dès ${formatTime(r.startTime)}`;
  if (r.endTime) return `${day} jusqu'à ${formatTime(r.endTime)}`;
  return day;
}

/** The author, plus the responsible adults of a helped child: those told when someone helps. */
function askers(r: HelpRequestData, members: HelpMember[]): string[] {
  const helped = members.find((m) => m.id === r.forMemberId);
  const parents = helped && helped.profile !== 'adult' ? helped.parentIds : [];
  return [r.authorId, ...parents];
}

function unique(ids: string[], except: (string | null | undefined)[], members: HelpMember[]): string[] {
  const known = new Set(members.map((m) => m.id));
  const skip = new Set(except.filter(Boolean));
  return [...new Set(ids)].filter((id) => known.has(id) && !skip.has(id)).sort();
}

function message(id: string, recipientIds: string[], title: string, body: string, direct = false): HelpMessage[] {
  return recipientIds.length === 0 ? [] : [{ recipientIds, notice: { channel: 'helpRequests', title, body, helpRequestId: id, ...(direct ? { direct } : {}) } }];
}

function askedNotice(id: string, r: HelpRequestData, recipients: string[], members: HelpMember[]): HelpMessage[] {
  const helped = r.forMemberId !== r.authorId ? members.find((m) => m.id === r.forMemberId)?.firstName : undefined;
  const forWhom = helped ? ` pour ${helped}` : '';
  return message(id, recipients, 'Coup de main demandé', `${r.authorName} demande de l'aide${forWhom} : « ${r.title} », ${helpWhen(r)}.`);
}

/**
 * What a write of helpRequests/{id} tells whom. [actorId] is the member who wrote it, when known.
 * A request already past tells nobody, except a « Merci ».
 */
export function helpMessages(
  id: string,
  before: HelpRequestData | undefined,
  after: HelpRequestData | undefined,
  actorId: string | null,
  members: HelpMember[],
  today: string,
): HelpMessage[] {
  if (!after) return [];
  // Récit 17: « Merci » once the day has come, to the volunteer.
  if (before && !before.thankedAt && after.thankedAt && after.volunteerId) {
    return message(id, unique([after.volunteerId], [], members), `${after.authorName} te dit merci !`, `Merci pour « ${after.title} ».`, true);
  }
  if (after.date < today) return [];
  if (!before) return askedNotice(id, after, unique(after.notifyIds, [after.authorId, actorId], members), members);

  // Récit 17: a cancellation tells the volunteer, if there is one.
  if (before.status !== 'cancelled' && after.status === 'cancelled') {
    return message(
      id, unique([before.volunteerId ?? ''], [actorId], members), 'Coup de main annulé',
      `${after.authorName} a annulé « ${after.title} », ${helpWhen(after)}. Tu n'as plus à t'en occuper.`, true,
    );
  }
  // Récit 16: « [prénom] s'occupe de [titre] » to the author, and the parents of a child.
  if (before.status === 'open' && after.status === 'taken' && after.volunteerName) {
    return message(
      id, unique(askers(after, members), [after.volunteerId], members), `${after.volunteerName} s'occupe de « ${after.title} »`,
      after.volunteerNote?.trim() ? `${after.volunteerName} : ${after.volunteerNote.trim()}` : `C'est réglé pour ${helpWhen(after)}.`,
    );
  }
  // Récit 16: the volunteer withdraws; the request is open again for everyone warned.
  if (before.status === 'taken' && after.status === 'open') {
    return message(
      id, unique([...askers(after, members), ...after.notifyIds], [before.volunteerId, actorId], members),
      'Coup de main de nouveau ouvert',
      `${before.volunteerName ?? 'Le volontaire'} ne peut plus s'occuper de « ${after.title} », ${helpWhen(after)}.`,
    );
  }
  // Carte PRODUIT-22: « Je ne peux pas » from the only person warned.
  if (!before.declinedBy && after.declinedBy) {
    const name = members.find((m) => m.id === after.declinedBy)?.firstName ?? 'La personne prévenue';
    return message(
      id, unique([after.authorId], [], members), `${name} ne peut pas`,
      `Pour « ${after.title} », ${helpWhen(after)}. Ouvre la demande pour prévenir d'autres personnes.`,
    );
  }
  const messages: HelpMessage[] = [];
  // Widening (récit 18 or after « Je ne peux pas »): the new people are told, like at creation.
  const added = after.notifyIds.filter((m) => !before.notifyIds.includes(m));
  if (after.status === 'open') messages.push(...askedNotice(id, after, unique(added, [after.authorId, actorId], members), members));
  // Récit 17: a taken request changes day or time; the volunteer keeps it and is told.
  const moved = before.date !== after.date || before.startTime !== after.startTime || before.endTime !== after.endTime
    || before.place !== after.place;
  if (after.status === 'taken' && moved && after.volunteerId) {
    messages.push(...message(
      id, unique([after.volunteerId], [actorId], members), 'Coup de main modifié',
      `« ${after.title} » est maintenant ${helpWhen(after)}${after.place ? `, ${after.place}` : ''}.`, true,
    ));
  }
  return messages;
}

/** Récit 18: the evening before, the author of a request still open is reminded. */
export function stillOpenReminder(id: string, r: HelpRequestData, members: HelpMember[]): HelpMessage[] {
  if (r.status !== 'open') return [];
  return message(
    id, unique([r.authorId], [], members), `Personne pour « ${r.title} »`,
    'Ta demande de demain est toujours ouverte. Ouvre-la pour prévenir d\'autres personnes.',
  ).map((m) => ({ ...m, notice: { ...m.notice, channel: 'reminders' as const } }));
}

// --- Firestore -------------------------------------------------------------------------------------

export type HelpSender = (token: string, notice: HelpNotice) => Promise<'ok' | 'unregistered' | 'error'>;

export async function loadHelpMembers(db: Firestore): Promise<(HelpMember & { uid: string | null })[]> {
  const snapshot = await db.collection('members').get();
  return snapshot.docs.map((doc) => ({
    id: doc.id,
    firstName: String(doc.get('firstName') ?? ''),
    profile: String(doc.get('profile') ?? ''),
    parentIds: (doc.get('parentIds') as string[] | undefined) ?? [],
    uid: (doc.get('uid') as string | null | undefined) ?? null,
  }));
}

async function sendAll(db: Firestore, send: HelpSender, messages: HelpMessage[]): Promise<number> {
  let sent = 0;
  for (const { recipientIds, notice } of messages) sent += await deliver(db, send, recipientIds, notice);
  return sent;
}

/** A request was created, changed or deleted by the member behind [actorUid]. */
export async function onHelpRequestWritten(
  db: Firestore, send: HelpSender, id: string, before: HelpRequestData | undefined, after: HelpRequestData | undefined,
  actorUid: string | undefined, now: Date,
): Promise<number> {
  if (!after) return 0;
  const members = await loadHelpMembers(db);
  const actorId = actorUid ? members.find((m) => m.uid === actorUid)?.id ?? null : null;
  return sendAll(db, send, withHelpMentions(id, before, after, actorId, members, helpMessages(id, before, after, actorId, members, parisDay(now))));
}

/**
 * « @Prénom » in the note (l'admin, 19/11): who is newly named, among those warned, hears « X t'a
 * nommé dans « … » » directly, first, and is left out of the other messages of this write. Never
 * on a cancelled request.
 */
export function withHelpMentions(
  id: string, before: HelpRequestData | undefined, after: HelpRequestData, actorId: string | null, members: HelpMember[], messages: HelpMessage[],
): HelpMessage[] {
  if (after.status === 'cancelled') return messages;
  const warned = new Set(after.notifyIds.filter((m) => members.some((x) => x.id === m && x.profile !== 'childWithoutApp')));
  const named = newlyNamed(before, after, actorId, warned);
  if (named.length === 0) return messages;
  const actor = members.find((m) => m.id === actorId)?.firstName ?? after.authorName;
  const others = messages
    .map((m) => ({ ...m, recipientIds: m.recipientIds.filter((r) => !named.includes(r)) }))
    .filter((m) => m.recipientIds.length > 0);
  return [...message(id, named, noteMentionTitle(actor, after.title), notePreview(after.note), true), ...others];
}

/** Récit 18, every evening: tomorrow's requests still without a volunteer. */
export async function sendStillOpenReminders(db: Firestore, send: HelpSender, now: Date): Promise<number> {
  const tomorrow = addDays(parisDay(now), 1);
  const requests = await db.collection('helpRequests').where('date', '==', tomorrow).where('status', '==', 'open').get();
  if (requests.empty) return 0;
  const members = await loadHelpMembers(db);
  const messages = requests.docs.flatMap((doc) => stillOpenReminder(doc.id, doc.data() as HelpRequestData, members));
  return sendAll(db, send, messages);
}

/**
 * Spec, cas limites: a volunteer removed from the family. Their upcoming requests are open again,
 * which tells the author through [onHelpRequestWritten].
 */
export async function reopenRequestsOf(db: Firestore, memberId: string, now: Date): Promise<number> {
  const today = parisDay(now);
  const taken = await db.collection('helpRequests').where('volunteerId', '==', memberId).get();
  const upcoming = taken.docs.filter((doc) => doc.get('status') === 'taken' && String(doc.get('date')) >= today);
  for (const doc of upcoming) {
    await doc.ref.update({ status: 'open', volunteerId: null, volunteerName: null, volunteerNote: null, updatedAt: new Date() });
  }
  return upcoming.length;
}
