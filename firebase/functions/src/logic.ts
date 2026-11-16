// Pure logic of the notifications: who gets what, and the French texts. No Firebase here, so
// everything is tested without the emulator (test/logic.test.ts).
//
// The texts are written here rather than in the app, since the server sends them. They follow the
// app's wording: "tu" (never "vous"), the words of the PRD ("Je viens", "Peut-être", "rappel la
// veille"), times as "10 h 00" and "Journée" for an all-day event, as in the planning.

export type Profile = 'adult' | 'childWithApp' | 'childWithoutApp';

export interface Member {
  id: string;
  firstName: string;
  profile: Profile;
  /** Missing on very old documents: such a member is then in no household's scope. */
  householdId?: string | null;
  parentIds: string[];
  uid: string | null;
}

export interface EventData {
  title: string;
  date: string; // AAAA-MM-JJ
  time: string | null; // HH:MM
  place: string | null;
  note: string | null;
  targetType: string;
  targetMemberId: string | null;
  householdId: string | null;
}

export type RsvpStatus = 'yes' | 'maybe' | 'no';

/** The ids of NotificationChannels in the app. `events` is the « Planning » channel. */
// Each module adds its id here and its channel in the app, in the same pull request.
export type Channel = 'events' | 'reminders' | 'gifts' | 'helpRequests' | 'goodNews' | 'trips' | 'comments' | 'weekly' | 'admin' | 'souvenirs' | 'tree';

export interface Notice {
  /**
   * Android notification channel, created by the app (core/notifications/NotificationChannels.kt,
   * PRD récit 6 quinquies): one per kind, so each can be turned off in the phone's settings.
   */
  channel: Channel;
  title: string;
  body: string;
  /** What a tap opens: an event of the planning, a gift list (V2), a trip (V3) or the question of the week. */
  eventId?: string;
  listId?: string;
  tripId?: string;
  weeklyQuestionId?: string;
  /** A new souvenir (récit 34): the app opens the member's own page. */
  souvenirKey?: string;
  /** A poll to answer or its result (récits 2 bis et 2 ter). */
  pollId?: string;
}

export type ChangeKind = 'added' | 'edited' | 'deleted';

const NBSP = ' ';
const DAYS = ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'];
const MONTHS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre',
  'octobre', 'novembre', 'décembre'];

/** "10 h 00", with non-breaking spaces, as in the app. */
export function formatTime(time: string): string {
  const [h, m] = time.split(':');
  return `${Number(h)}${NBSP}h${NBSP}${m}`;
}

/** "samedi 20 décembre". */
export function formatDay(day: string): string {
  const [y, m, d] = day.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return `${DAYS[date.getUTCDay()]} ${d} ${MONTHS[m - 1]}`;
}

/** "samedi 20 décembre à 10 h 00", or the day alone for an all-day event. */
export function formatWhen(e: Pick<EventData, 'date' | 'time'>): string {
  return e.time ? `${formatDay(e.date)} à ${formatTime(e.time)}` : formatDay(e.date);
}

/** Today's date in Paris, as AAAA-MM-JJ. */
export function parisDay(now: Date): string {
  // en-CA writes dates as AAAA-MM-JJ.
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Paris', year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(now);
}

export function addDays(day: string, n: number): string {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
}

/** Only what the family sees counts: a change of updatedAt alone notifies nobody. */
export function hasVisibleChange(before: EventData, after: EventData): boolean {
  const keys: (keyof EventData)[] = ['title', 'date', 'time', 'place', 'note', 'targetType', 'targetMemberId', 'householdId'];
  return keys.some((k) => (before[k] ?? null) !== (after[k] ?? null));
}

/**
 * The one place that says who an event concerns (PRD, cartes AVIS-7 et AVIS-8), the same rule as
 * `concerns` in the app (core/model/Audience.kt). A future « cercle » only changes this function:
 * never test « tous les membres » elsewhere. An event concerns [me] when it is for the whole
 * family, or when its target is in my scope: me, a member or a child of my household, my
 * household itself, or a child I am a responsible adult for (even in another household).
 */
export function concerns(
  event: Pick<EventData, 'targetType' | 'targetMemberId' | 'householdId'>, me: Member, members: Member[],
): boolean {
  switch (event.targetType) {
    case 'family':
      return true;
    case 'household':
      return !!me.householdId && event.householdId === me.householdId;
    case 'self':
    case 'child': {
      if (!event.targetMemberId) return false;
      if (event.targetMemberId === me.id) return true;
      const target = members.find((m) => m.id === event.targetMemberId);
      if (!target) return false;
      return (!!me.householdId && target.householdId === me.householdId) || target.parentIds.includes(me.id);
    }
    default:
      return false;
  }
}

/**
 * PRD, récit 6 and carte AVIS-7: each member with the app is told of what concerns them
 * ([concerns]), never of their own change. An edit or a deletion also reaches those who answered
 * « Je viens » or « Peut-être » ([answered]), whatever the target. Events already past notify
 * nobody (cleaning up old events should not wake the family).
 */
export function changeRecipients(
  members: Member[], actorId: string | null, event: EventData, today: string, answered: Set<string> = new Set(),
): string[] {
  if (event.date < today) return [];
  return members
    .filter((m) => m.profile !== 'childWithoutApp' && m.id !== actorId)
    .filter((m) => concerns(event, m, members) || answered.has(m.id))
    .map((m) => m.id);
}

/** PRD, récit 30: « Ajouter à mon agenda » makes a copy, so a change reminds to update it. */
export const CALENDAR_HINT = {
  edited: ' Si tu l\'as ajouté à ton agenda, mets-le à jour depuis Familia.',
  deleted: ' Si tu l\'avais ajouté à ton agenda, pense à l\'en retirer.',
};

/** [calendarHint]: the module « Ajouter à mon agenda » is on for the family (config/app). */
export function changeNotice(
  kind: ChangeKind, eventId: string, event: EventData, actorName: string | null, calendarHint = false,
): Notice {
  const who = actorName ?? 'Un membre de la famille';
  const title = { added: 'Nouvel événement', edited: 'Événement modifié', deleted: 'Événement annulé' }[kind];
  const body = kind === 'deleted'
    ? `${who} a supprimé « ${event.title} » du ${formatDay(event.date)}.`
    : `${who} a ${kind === 'added' ? 'ajouté' : 'modifié'} « ${event.title} », ${formatWhen(event)}.`;
  const hint = calendarHint && kind !== 'added' ? CALENDAR_HINT[kind] : '';
  return { channel: 'events', title, body: body + hint, eventId };
}

export interface Reminder {
  recipientId: string;
  notice: Notice;
}

/**
 * PRD, récit 6 bis: the evening before, each member who said "je viens" or "peut-être" is
 * reminded. A child without the app is reminded through their responsible adults. One reminder
 * per person and event, even when several of their children are coming. [remindersOff] holds the
 * members who turned the reminder off in their settings.
 */
export function reminders(
  members: Member[],
  eventId: string,
  event: EventData,
  answers: Map<string, RsvpStatus>,
  remindersOff: Set<string>,
  /** « Qui apporte quoi » (récit 4 bis): what each member took, by member id. */
  bringing: Map<string, string[]> = new Map(),
): Reminder[] {
  const byId = new Map(members.map((m) => [m.id, m]));
  // recipient -> names of the children without the app they are reminded for
  const recipients = new Map<string, string[]>();
  for (const [memberId, status] of answers) {
    if (status === 'no') continue;
    const member = byId.get(memberId);
    if (!member) continue; // a member who has left the family
    if (member.profile === 'childWithoutApp') {
      for (const parentId of member.parentIds) {
        if (!byId.has(parentId)) continue;
        recipients.set(parentId, [...(recipients.get(parentId) ?? []), member.firstName]);
      }
    } else if (!recipients.has(memberId)) {
      recipients.set(memberId, []);
    }
  }
  // Whoever took something is reminded of it, even without answering « Je viens ».
  for (const takerId of bringing.keys()) {
    if (byId.has(takerId) && !recipients.has(takerId) && answers.get(takerId) !== 'no') recipients.set(takerId, []);
  }
  const details = [event.time ? formatTime(event.time) : 'Journée', event.place].filter(Boolean).join(' · ');
  return [...recipients.entries()]
    .filter(([id]) => !remindersOff.has(id))
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([recipientId, children]) => {
      const selfComing = answers.has(recipientId) && answers.get(recipientId) !== 'no';
      const forChildren = children.length > 0 && !selfComing ? ` (pour ${joinNames(children)})` : '';
      const withChildren = children.length > 0 && selfComing ? ` (avec ${joinNames(children)})` : '';
      const items = bringing.get(recipientId) ?? [];
      const body = items.length > 0 ? `${details}\nTu apportes : ${items.join(', ')}` : details;
      return {
        recipientId,
        notice: { channel: 'reminders', title: `Demain : ${event.title}${forChildren}${withChildren}`, body, eventId },
      };
    });
}

function joinNames(names: string[]): string {
  const sorted = [...names].sort((a, b) => a.localeCompare(b, 'fr'));
  return sorted.length <= 1 ? sorted.join('') : `${sorted.slice(0, -1).join(', ')} et ${sorted[sorted.length - 1]}`;
}
