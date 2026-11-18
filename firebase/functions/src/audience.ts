// The single audience of Familia (PRD, « Plus tard : élargir la famille par cercles »). Every
// function that sends to « toute la famille » or reads « tous les membres » goes through here, never
// through members/ directly, so that the circles touch one place only. Today the circles do not
// exist: the audience is the whole family. Same names as the étape 1 of the circles (PR #223), so
// that it plugs in here.

import type { Firestore, QueryDocumentSnapshot } from 'firebase-admin/firestore';

/** The member documents of the whole family: the only read of « tous les membres » of a sender. */
export async function familyMembers(db: Firestore): Promise<QueryDocumentSnapshot[]> {
  return (await db.collection('members').get()).docs;
}

/** The ids of those of [docs] who have the app: a child without the app has no phone to tell. */
export function withApp(docs: QueryDocumentSnapshot[]): string[] {
  return docs.filter((m) => m.get('profile') !== 'childWithoutApp').map((m) => m.id);
}
