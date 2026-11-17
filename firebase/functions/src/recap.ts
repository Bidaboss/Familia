// Le récap du dimanche (PRD, récit 36) : à 18 h le dimanche, « La semaine de la famille » ouvre un
// écran avec les photos de la semaine, les anniversaires des 14 prochains jours, l'état du défi et
// les voyages en cours. One a week, behind the module `recap`; each member can turn it off
// (settings/notifications.recap). The screen reads the family's data itself: the notice only says
// what is in it.
//
// The last Sunday of the month, with the module `monthSlideshow` on, the notice becomes the one of
// the slideshow of the month (récit 42): the app then opens the slideshow instead of the week.
//
// weeklyRecaps/{AAAA-MM-JJ} (the Sunday) marks a week as told; only the functions use it.

import type { Firestore } from 'firebase-admin/firestore';
import { nextBirthday } from './gifts';
import { addDays, parisDay, type Notice } from './logic';
import { featureOn, notify, type Sender } from './store';
import { familyMembers } from './audience';

/** What the notice of the week says, read once for the whole family. */
export interface WeekNews {
  photos: number;
  birthdays: number;
  /** The gauge of the challenge in progress in %, or null (none, achieved, or the tree is off). */
  challengePercent: number | null;
  challengeDone: boolean;
  trips: number;
}

const MONTHS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];

/** True when [sunday] (AAAA-MM-JJ) is the last Sunday of its month. */
export function isLastSunday(sunday: string): boolean {
  return addDays(sunday, 7).slice(0, 7) !== sunday.slice(0, 7);
}

/** The notice of the last Sunday of the month (récit 42), in place of the week. */
export function slideshowNotice(sunday: string, news: WeekNews): Notice {
  const month = MONTHS[Number(sunday.slice(5, 7)) - 1];
  const challenge = news.challengeDone ? ' et le défi réussi' : news.challengePercent !== null ? ' et le défi' : '';
  return { channel: 'recap', title: `Le diaporama de ${month}`, body: `Les photos du mois${challenge}, en un film à regarder.`, recapWeek: sunday };
}

export function recapNotice(sunday: string, news: WeekNews): Notice {
  const parts: string[] = [];
  if (news.photos > 0) parts.push(news.photos === 1 ? '1 photo' : `${news.photos} photos`);
  if (news.birthdays > 0) parts.push(news.birthdays === 1 ? '1 anniversaire à venir' : `${news.birthdays} anniversaires à venir`);
  if (news.challengeDone) parts.push('défi réussi');
  else if (news.challengePercent !== null) parts.push(`défi à ${news.challengePercent} %`);
  if (news.trips > 0) parts.push(news.trips === 1 ? '1 voyage en cours' : `${news.trips} voyages en cours`);
  const body = parts.length > 0
    ? parts.join(', ').replace(/^./, (c) => c.toUpperCase()) + '.'
    : 'Les nouvelles de la famille, en un coup d\'œil.';
  return { channel: 'recap', title: 'La semaine de la famille', body, recapWeek: sunday };
}

/** Sunday, in Paris. */
export function isSunday(now: Date): boolean {
  return new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Paris', weekday: 'short' }).format(now) === 'Sun';
}

/** The week as read once for the whole family; the trips keep who may see them (readerIds). */
export interface Week {
  family: Omit<WeekNews, 'trips'>;
  tripReaders: string[][];
}

export async function loadWeek(db: Firestore, now: Date): Promise<Week> {
  const today = parisDay(now);
  const since = new Date(now.getTime() - 7 * 24 * 3600 * 1000);
  const [news, members, config, challenge, trips] = await Promise.all([
    db.collection('goodNews').where('createdAt', '>=', since).get(),
    familyMembers(db),
    db.doc('config/app').get(),
    db.doc(`challenges/${today.slice(0, 7)}`).get(),
    db.collection('trips').where('endDate', '>=', today).get(),
  ]);
  const photos = news.docs.reduce((n, d) => n + ((d.get('photoIds') as string[] | undefined)?.length ?? 0), 0);
  const horizon = addDays(today, 14);
  const birthdays = members.filter((m) => {
    const birthDate = m.get('birthDate') as string | null | undefined;
    if (!birthDate || birthDate > today) return false;
    const next = nextBirthday(birthDate, today);
    return next >= today && next <= horizon;
  }).length;
  const treeOn = config.get('features.tree') === true;
  const target = Number(challenge.get('target') ?? 0);
  const done = treeOn && challenge.exists && Boolean(challenge.get('achievedAt'));
  const percent = treeOn && challenge.exists && !done && target > 0
    ? Math.floor((100 * Number(challenge.get('progress') ?? 0)) / target) : null;
  const tripReaders = trips.docs
    .filter((t) => String(t.get('startDate') ?? '') <= today)
    .map((t) => (t.get('readerIds') as string[] | undefined) ?? []);
  return { family: { photos, birthdays, challengePercent: percent, challengeDone: done }, tripReaders };
}

/** What [memberId] is told: a trip counts only if the member may see it (a surprise stays hidden). */
export function newsFor(week: Week, memberId: string): WeekNews {
  return { ...week.family, trips: week.tripReaders.filter((r) => r.includes(memberId)).length };
}

export async function weekNews(db: Firestore, now: Date, memberId: string): Promise<WeekNews> {
  return newsFor(await loadWeek(db, now), memberId);
}

/**
 * On Sunday, from the daily task of 18 h: tells each member with the app, once a week, except
 * those who turned `recap` off. Returns how many members were told, or null when nothing is due.
 */
export async function sendWeeklyRecap(db: Firestore, send: Sender, now: Date): Promise<number | null> {
  if (!isSunday(now) || !(await featureOn(db, 'recap'))) return null;
  const sunday = parisDay(now);
  try {
    await db.collection('weeklyRecaps').doc(sunday).create({ sentAt: now, expireAt: new Date(`${addDays(sunday, 40)}T00:00:00Z`) });
  } catch (e) {
    if ((e as { code?: number }).code === 6) return null; // Told this week.
    throw e;
  }
  const members = await familyMembers(db);
  const withApp = members.filter((m) => m.get('profile') !== 'childWithoutApp').map((m) => m.id);
  if (withApp.length === 0) return 0;
  const settings = await db.getAll(...withApp.map((id) => db.doc(`members/${id}/settings/notifications`)));
  const muted = new Set(settings.filter((s) => s.exists && s.get('recap') === false).map((s) => s.ref.parent.parent!.id));
  const told = withApp.filter((id) => !muted.has(id));
  // One notice per member, sent in groups of members who are told the same thing.
  const week = await loadWeek(db, now);
  const slideshow = isLastSunday(sunday) && (await featureOn(db, 'monthSlideshow'));
  const groups = new Map<string, { notice: Notice; ids: string[] }>();
  for (const id of told) {
    const notice = (slideshow ? slideshowNotice : recapNotice)(sunday, newsFor(week, id));
    const group = groups.get(notice.body) ?? { notice, ids: [] };
    group.ids.push(id);
    groups.set(notice.body, group);
  }
  for (const { notice, ids } of groups.values()) await notify(db, send, ids, notice);
  return told.length;
}
