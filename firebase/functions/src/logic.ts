// Pure logic of the notifications: who gets what, and the French texts. No Firebase here, so
// everything is tested without the emulator (test/logic.test.ts).

export type Profile = 'adult' | 'childWithApp' | 'childWithoutApp';

export interface Member {
  id: string;
  firstName: string;
  profile: Profile;
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

export interface Notice {
  /** Android notification channel, created by the app (lot 8b). */
  channel: 'events' | 'reminders';
  title: string;
  body: string;
  eventId: string;
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
 * PRD, récit 6: everyone with the app is told, except the member who made the change. Events
 * already past notify nobody (cleaning up old events should not wake the family).
 */
export function changeRecipients(members: Member[], actorId: string | null, event: EventData, today: string): string[] {
  if (event.date < today) return [];
  return members
    .filter((m) => m.profile !== 'childWithoutApp' && m.id !== actorId)
    .map((m) => m.id);
}

export function changeNotice(kind: ChangeKind, eventId: string, event: EventData, actorName: string | null): Notice {
  const who = actorName ?? 'Un membre de la famille';
  const title = { added: 'Nouvel événement', edited: 'Événement modifié', deleted: 'Événement annulé' }[kind];
  const body = kind === 'deleted'
    ? `${who} a supprimé « ${event.title} » du ${formatDay(event.date)}.`
    : `${who} a ${kind === 'added' ? 'ajouté' : 'modifié'} « ${event.title} », ${formatWhen(event)}.`;
  return { channel: 'events', title, body, eventId };
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
  const details = [event.time ? formatTime(event.time) : 'Toute la journée', event.place].filter(Boolean).join(' · ');
  return [...recipients.entries()]
    .filter(([id]) => !remindersOff.has(id))
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([recipientId, children]) => {
      const selfComing = answers.has(recipientId) && answers.get(recipientId) !== 'no';
      const forChildren = children.length > 0 && !selfComing ? ` (pour ${joinNames(children)})` : '';
      const withChildren = children.length > 0 && selfComing ? ` (avec ${joinNames(children)})` : '';
      return {
        recipientId,
        notice: { channel: 'reminders', title: `Demain : ${event.title}${forChildren}${withChildren}`, body: details, eventId },
      };
    });
}

function joinNames(names: string[]): string {
  const sorted = [...names].sort((a, b) => a.localeCompare(b, 'fr'));
  return sorted.length <= 1 ? sorted.join('') : `${sorted.slice(0, -1).join(', ')} et ${sorted[sorted.length - 1]}`;
}
