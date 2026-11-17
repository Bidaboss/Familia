// The single audience of Familia (PRD, « Plus tard : élargir la famille par cercles », étape 1).
// Every function that sends to « toute la famille » or reads « tous les membres » goes through here,
// never through members/ directly, so that the circles touch one place only.
//
// Étape 1: no change at all. A content without circleIds is in the circle « principal », as is a
// member, so everyone still reaches everyone. The circles only narrow it once the switch
// « circles » (config/app.features.circles) is on, at étape 2.

import type { Firestore, QueryDocumentSnapshot } from 'firebase-admin/firestore';

/** The circle of every household and content written before the circles. */
export const MAIN_CIRCLE = 'principal';

/** Anything that may name its circles: a member, a household, a content. */
export interface InCircles { circleIds?: unknown }

/** The circles of [item]: « principal » when it names none. */
export function circlesOf(item: InCircles | undefined | null): string[] {
  const ids = item?.circleIds;
  const named = Array.isArray(ids) ? ids.filter((id): id is string => typeof id === 'string' && id.length > 0) : [];
  return named.length > 0 ? named : [MAIN_CIRCLE];
}

/**
 * Whether [member] is in the audience of [content]. With the circles off, always: the whole family.
 * With them on, when they share a circle. A content with no scope (null) is for the whole family.
 */
export function inAudience(member: InCircles, content: InCircles | null, circlesOn: boolean): boolean {
  if (!circlesOn || content === null) return true;
  const mine = new Set(circlesOf(member));
  return circlesOf(content).some((id) => mine.has(id));
}

/**
 * The member documents in the audience of [content], or of the whole family when it is null:
 * the only read of « tous les membres » of the functions.
 */
export async function familyMembers(db: Firestore, content: InCircles | null = null): Promise<QueryDocumentSnapshot[]> {
  const snapshot = await db.collection('members').get();
  if (content === null) return snapshot.docs;
  const circlesOn = (await db.doc('config/app').get()).get('features.circles') === true;
  return snapshot.docs.filter((doc) => inAudience(doc.data(), content, circlesOn));
}

/** The ids of those of [docs] who have the app: a child without the app has no phone to tell. */
export function withApp(docs: QueryDocumentSnapshot[]): string[] {
  return docs.filter((m) => m.get('profile') !== 'childWithoutApp').map((m) => m.id);
}
