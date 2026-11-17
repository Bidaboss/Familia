// Bons plans et bonnes adresses (PRD, récit 45, PRODUIT-60): the summary of the new bons plans,
// at most once a day, and the cleanup of the ended ones. A bonne adresse never sends anything.
// Both run in the task of 18 h of the coups de main: no task of their own.

import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import type { Notice } from './logic';
import { deliver } from './prefs';
import type { Sender } from './store';

export interface GoodDealData {
  kind: 'deal' | 'address';
  title: string;
  authorId: string;
  authorName: string;
  endsAt: Timestamp | null;
  createdAt: Timestamp;
}

export interface NewDeal {
  title: string;
  authorId: string;
  authorName: string;
}

/** An ended bon plan stays 30 days in Firestore (récit 45), then the server deletes it. */
export const KEEP_ENDED_DAYS = 30;
const DAY_MS = 24 * 60 * 60 * 1000;
const MAX_BODY = 180;

/**
 * The summary one member gets for [deals], the new bons plans of the day that are not theirs:
 * one bon plan is named with its author, several are counted and listed. Null when none.
 */
export function goodDealsNotice(deals: NewDeal[]): Notice | null {
  if (deals.length === 0) return null;
  const body = deals.length === 1
    ? `${deals[0].authorName} : ${deals[0].title}`
    : deals.map((d) => d.title).join(' · ');
  return {
    channel: 'helpRequests',
    title: deals.length === 1 ? 'Nouveau bon plan' : `${deals.length} nouveaux bons plans`,
    body: body.length > MAX_BODY ? `${body.slice(0, MAX_BODY - 1)}…` : body,
    goodDeals: 'list',
    // A new summary replaces the one of the day before on the phone.
    tag: 'goodDeals',
  };
}

/**
 * Who may get the summary. Once the module is switched on for the family in Administration
 * (config/app.features.goodDeals), every member with the app; before that, the admin principal
 * only, the one member the app shows the module to. When the circles arrive, this is the one
 * place to plug the audience function.
 */
async function goodDealsAudience(db: Firestore): Promise<string[]> {
  const config = await db.doc('config/app').get();
  const members = (await db.collection('members').get()).docs
    .filter((m) => m.get('profile') !== 'childWithoutApp')
    .map((m) => m.id);
  if (config.get('features.goodDeals') === true) return members;
  const owner = (await db.doc('config/family').get()).get('ownerMemberId');
  return members.filter((id) => id === owner);
}

/**
 * The summary of the bons plans published in the last 24 hours and not yet ended: each member is
 * told of those of the others, through deliver() (their settings, the quiet hours). Returns the
 * number of members told.
 */
export async function sendGoodDealsSummary(db: Firestore, send: Sender, now: Date): Promise<number> {
  const since = Timestamp.fromMillis(now.getTime() - DAY_MS);
  const snapshot = await db.collection('goodDeals').where('createdAt', '>', since).get();
  const deals = snapshot.docs
    .map((d) => d.data() as GoodDealData)
    .filter((d) => d.kind === 'deal' && d.endsAt !== null && d.endsAt.toMillis() > now.getTime())
    .sort((a, b) => a.createdAt.toMillis() - b.createdAt.toMillis());
  if (deals.length === 0) return 0;

  let told = 0;
  for (const memberId of await goodDealsAudience(db)) {
    const notice = goodDealsNotice(deals.filter((d) => d.authorId !== memberId));
    if (!notice) continue;
    await deliver(db, send, [memberId], notice, now);
    told++;
  }
  return told;
}

/** Deletes the bons plans ended more than 30 days ago; their photos go with onGoodDealDeleted. */
export async function deleteEndedGoodDeals(db: Firestore, now: Date): Promise<number> {
  const limit = Timestamp.fromMillis(now.getTime() - KEEP_ENDED_DAYS * DAY_MS);
  const ended = await db.collection('goodDeals').where('endsAt', '<', limit).get();
  for (const deal of ended.docs) await deal.ref.delete();
  return ended.size;
}
