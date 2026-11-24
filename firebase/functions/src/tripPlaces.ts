// Places programmed under a destination of a trip (récit 103): trips/{tripId}/places/{placeId}.
// A deleted destination takes its accommodations, restaurants and spots with it, so nothing stays
// readable for nothing. A deleted trip takes them all with its recursive delete (trips.ts).

import type { Firestore } from 'firebase-admin/firestore';

/** Deletes the places of [stepId]; the places of the other destinations stay. Returns how many went. */
export async function deleteStepPlaces(db: Firestore, tripId: string, stepId: string): Promise<number> {
  const places = await db.collection('trips').doc(tripId).collection('places').where('stepId', '==', stepId).get();
  if (places.empty) return 0;
  const writer = db.bulkWriter();
  places.docs.forEach((d) => writer.delete(d.ref));
  await writer.close();
  return places.size;
}
