// « Rejoignez-moi » (PRD, récit 33): a member sends a place, their position of the moment or a
// place they chose, to the members they ticked. The recipients are told at once (it is aimed at
// them, so it is never grouped nor cut, carte AVIS-14). Firestore deletes it 24 h later (TTL).
// Data model: docs/data-model.md.

import { Firestore } from 'firebase-admin/firestore';
import { Notice } from './logic';
import { featureOn, notify, Sender } from './store';

export interface JoinMeData {
  authorId: string;
  authorName: string;
  recipientIds: string[];
  name: string | null;
  address: string | null;
  lat: number;
  lng: number;
  note: string | null;
}

/** « Camille : rejoins-moi » / « Plage des Sables · 1 promenade de la Mer » then the note. */
export function joinMeNotice(shareId: string, share: JoinMeData): Notice {
  const where = [share.name, share.address].filter((s): s is string => !!s).join(' · ') || 'Sa position du moment';
  const body = share.note ? `${where}\n${share.note}` : where;
  return { channel: 'joinMe', title: `${share.authorName} : rejoins-moi`, body, joinMeId: shareId };
}

/**
 * Who is told. Once the module is switched on for the family (config/app.features.places), every
 * recipient; before that, the admin principal only, so a try never reaches the family.
 */
export async function onJoinMeCreated(db: Firestore, send: Sender, shareId: string, share: JoinMeData): Promise<number> {
  let to = share.recipientIds.filter((id) => id !== share.authorId);
  if (!(await featureOn(db, 'places'))) {
    const owner = (await db.doc('config/family').get()).get('ownerMemberId');
    to = to.filter((id) => typeof owner === 'string' && id === owner);
  }
  return to.length === 0 ? 0 : notify(db, send, to, joinMeNotice(shareId, share));
}

/** A member removed from the family: the places they sent go at once. */
export async function deleteJoinMeOf(db: Firestore, memberId: string): Promise<void> {
  const sent = await db.collection('joinMe').where('authorId', '==', memberId).get();
  if (sent.empty) return;
  const batch = db.batch();
  sent.docs.forEach((d) => batch.delete(d.ref));
  await batch.commit();
}
