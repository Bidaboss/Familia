// Album d'un événement (PRD, récit 41): the day after an event, those who said « Je viens » get a
// soft invitation to add their photos, inside the résumé of the day (AVIS-14), never alone. Only
// with both modules on (eventAlbums, digest). A member who already put a photo in, a child without
// the app, or one who turned off the résumé (settings `weekly`) gets nothing.
//
// albumInvites/{day}: written once per Paris day, so the 30-minute task queues each event once;
// the mark of the day before is then removed. If anything fails after the mark, it is taken off
// so the next run tries again: queueDigest uses arrayUnion, so nobody is queued twice.

import type { Firestore } from 'firebase-admin/firestore';
import { queueDigest } from './digest';
import { addDays, parisDay } from './logic';
import { featureOn } from './store';

const ALREADY_EXISTS = 6;

/** Queues the invitations for yesterday's events, once a day. Returns how many members were queued. */
export async function queueAlbumInvitations(db: Firestore, now: Date): Promise<number> {
  if (!(await featureOn(db, 'eventAlbums')) || !(await featureOn(db, 'digest'))) return 0;
  const today = parisDay(now);
  const yesterday = addDays(today, -1);
  const claimed = await db.collection('albumInvites').doc(today).create({ day: yesterday, createdAt: now })
    .then(() => true, (e: { code?: number }) => {
      if (e.code === ALREADY_EXISTS) return false;
      throw e;
    });
  if (!claimed) return 0;
  try {
    const queued = await inviteFor(db, yesterday, now);
    // Only the mark of today is needed: the one of yesterday goes.
    await db.collection('albumInvites').doc(yesterday).delete();
    return queued;
  } catch (e) {
    await db.collection('albumInvites').doc(today).delete().catch(() => undefined);
    throw e;
  }
}

async function inviteFor(db: Firestore, yesterday: string, now: Date): Promise<number> {
  // A single-field equality: no composite index.
  const events = await db.collection('events').where('date', '==', yesterday).get();
  let queued = 0;
  for (const event of events.docs) {
    const [rsvps, photos] = await Promise.all([
      event.ref.collection('rsvps').where('status', '==', 'yes').get(),
      event.ref.collection('photos').get(),
    ]);
    const posted = new Set(photos.docs.map((p) => String(p.get('authorId') ?? '')));
    const invited: string[] = [];
    for (const rsvp of rsvps.docs) {
      if (posted.has(rsvp.id)) continue;
      const member = await db.collection('members').doc(rsvp.id).get();
      if (!member.exists || member.get('profile') === 'childWithoutApp') continue;
      const settings = await member.ref.collection('settings').doc('notifications').get();
      if (settings.get('weekly') === false) continue;
      invited.push(rsvp.id);
    }
    if (invited.length > 0) await queueDigest(db, invited, { kind: 'album', eventId: event.id }, now);
    queued += invited.length;
  }
  return queued;
}
