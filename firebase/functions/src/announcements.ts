// Annonces de l'admin (PRD, récit 69, cartes PRODUIT-111 et 112): the admin principal sends a
// title and a message to the members they ticked, from Administration. One way only, no answer.
// - Nobody can turn it off: the channel `admin` belongs to no family of the settings, and it is
//   `direct`, so neither the settings nor the cap of the hour (prefs.ts) hold it.
// - From 22 h to 8 h it waits for 8 h in quietQueue, for everyone, unless the admin ticked
//   « Urgent »: then it rings at once.
// The app writes announcements/{id}; this trigger sends it and writes back `delivery`. Data model:
// docs/data-model.md.

import { FieldValue, type Firestore } from 'firebase-admin/firestore';
import { familyMembers, withApp } from './audience';
import { Notice } from './logic';
import { isQuietHour } from './prefs';
import { featureOn, notify, Sender } from './store';

export interface AnnouncementData {
  title: string;
  text: string;
  urgent: boolean;
  recipientIds: string[];
  authorId: string;
  authorName: string;
}

/** How it went: `sent` at once, `morning` at 8 h, `refused` (module off). */
export type Delivery = 'sent' | 'morning' | 'refused';

const MAX_BODY = 180;

/** The title of the admin, then the message on the phone, cut like the other notices. */
export function announcementNotice(id: string, a: AnnouncementData): Notice {
  const line = a.text.replace(/\s+/g, ' ').trim();
  const body = line.length <= MAX_BODY ? line : `${line.slice(0, MAX_BODY - 1).trimEnd()}…`;
  return { channel: 'admin', title: a.title.trim(), body, announcementId: id, direct: true };
}

/** Those ticked who are in the family and have the app, never the author. */
export function announcementRecipients(a: AnnouncementData, family: string[]): string[] {
  const ticked = new Set(a.recipientIds);
  return family.filter((id) => ticked.has(id) && id !== a.authorId);
}

/** Sends [a] and records how it went on announcements/{id}. */
export async function onAnnouncementCreated(db: Firestore, send: Sender, id: string, a: AnnouncementData, now: Date): Promise<Delivery> {
  const ref = db.collection('announcements').doc(id);
  const owner = (await db.doc('config/family').get()).get('ownerMemberId');
  let delivery: Delivery;
  if (owner !== a.authorId || !(await featureOn(db, 'adminAnnouncements'))) {
    delivery = 'refused';
  } else {
    const to = announcementRecipients(a, withApp(await familyMembers(db)));
    const notice = announcementNotice(id, a);
    if (isQuietHour(now) && !a.urgent) {
      // Not urgent at night: everyone gets it at 8 h, in the same queue as the rest of the night.
      const item = { ...JSON.parse(JSON.stringify(notice)), heldAt: now.toISOString() };
      const batch = db.batch();
      for (const memberId of to) batch.set(db.collection('quietQueue').doc(memberId), { items: FieldValue.arrayUnion(item) }, { merge: true });
      await batch.commit();
      delivery = 'morning';
    } else {
      await notify(db, send, to, notice, now);
      delivery = 'sent';
    }
  }
  await ref.set({ delivery }, { merge: true });
  return delivery;
}

/** A member removed from the family: what they sent goes (only the admin principal sends). */
export async function deleteAnnouncementsOf(db: Firestore, memberId: string): Promise<void> {
  const sent = await db.collection('announcements').where('authorId', '==', memberId).get();
  if (sent.empty) return;
  const batch = db.batch();
  sent.docs.forEach((d) => batch.delete(d.ref));
  await batch.commit();
}
