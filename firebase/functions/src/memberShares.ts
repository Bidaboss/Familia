// Retirer un membre (récit 114, lot A2; décision de l'admin du 25/11 16 h 28): TOUT ce qu'il a
// partagé avec la famille part avec lui, sans choix. The exact list:
//
//   recipes/{id} they wrote            → with their photos, comments and files (onRecipeDeleted)
//   recipes/*/comments they wrote
//   events/*/photos they added         → and their files events/{eventId}/{memberId}/
//   events/*/comments they wrote
//   trips/*/photos they added          → and their files trips/{tripId}/{memberId}/
//   trips/*/words they wrote           (« Le mot de l'étape »), with their comments and mutes
//   trips/*/words/*/comments they wrote under the words of the others, and their mute there
//   trips/*/films they made            → their files go with them (onTripFilmDeleted)
//   goodNews/{id} they posted          → with comments, reactions and files (onGoodNewsDeleted)
//   goodNews/*/comments they wrote     → the count of the news follows (onGoodNewsCommentRemoved)
//   goodDeals/{id} they posted         → with its files (onGoodDealDeleted)
//   whoIsIt/{id} they posted           → with its answer, guesses and files (onWhoIsItDeleted)
//   goodNews/{memberId}/, whoIsIt/{memberId}/ in Storage (a file sent without its post)
//   memberPhotos/{memberId}          (their profile photo)
//
// Already removed elsewhere when a member goes: rsvps, bags, trip documents and voice notes
// (deleteMemberData), wishes and notebook (onMemberDeletedGifts), surprises and their parts
// (deleteMemberSurprises), birthday cards, moments, souvenirs, answers, votes, tree, their card.
// Capsules keep their rule (EXPERT-1): they still open, the admin principal decides on the rest.
// The events, trips, polls and coups de main they created stay: they belong to the family agenda.

import type { DocumentReference, Firestore } from 'firebase-admin/firestore';

/** Removes the files under a Storage folder (prefix ends with « / »). */
export type FolderRemover = (prefix: string) => Promise<void>;

export interface SharesRemoved {
  documents: number;
  folders: number;
}

/** Where the author of a document of each collection is named. */
const AUTHOR = 'authorId';

async function authoredIn(db: Firestore, path: string, memberId: string): Promise<DocumentReference[]> {
  const found = await db.collection(path).where(AUTHOR, '==', memberId).select().get();
  return found.docs.map((d) => d.ref);
}

/**
 * Deletes everything [memberId] shared with the family (list above). Each part is tried even if
 * another fails; the first failure is raised at the end, so the trigger is logged.
 */
export async function deleteSharedBy(db: Firestore, memberId: string, removeFolder: FolderRemover): Promise<SharesRemoved> {
  const result: SharesRemoved = { documents: 0, folders: 0 };
  const failures: unknown[] = [];
  const attempt = async (work: () => Promise<void>) => {
    try {
      await work();
    } catch (e) {
      failures.push(e);
    }
  };
  const remove = async (refs: DocumentReference[]) => {
    for (const ref of refs) {
      await ref.delete();
      result.documents++;
    }
  };
  const folder = async (prefix: string) => {
    await removeFolder(prefix);
    result.folders++;
  };

  // Top-level things they posted: each takes its own sub-collections and files through its trigger.
  for (const collection of ['recipes', 'goodNews', 'goodDeals', 'whoIsIt']) {
    await attempt(async () => remove(await authoredIn(db, collection, memberId)));
  }

  // Their files, even one sent without its post.
  await attempt(() => folder(`goodNews/${memberId}/`));
  await attempt(() => folder(`whoIsIt/${memberId}/`));

  // What they wrote or added under the things of the others.
  for (const parent of ['recipes', 'goodNews']) {
    await attempt(async () => {
      for (const p of (await db.collection(parent).select().get()).docs) {
        await remove(await authoredIn(db, `${parent}/${p.id}/comments`, memberId));
      }
    });
  }
  await attempt(async () => {
    for (const e of (await db.collection('events').select().get()).docs) {
      await remove(await authoredIn(db, `events/${e.id}/photos`, memberId));
      await remove(await authoredIn(db, `events/${e.id}/comments`, memberId));
      await folder(`events/${e.id}/${memberId}/`);
    }
  });
  await attempt(async () => {
    for (const t of (await db.collection('trips').select().get()).docs) {
      await remove(await authoredIn(db, `trips/${t.id}/photos`, memberId));
      // A word takes its comments and mutes with it: its id is the step's, so they would come
      // back under the next word written on that step (B1 du renfort sur #651).
      for (const word of (await db.collection(`trips/${t.id}/words`).select('authorId').get()).docs) {
        if (word.get('authorId') === memberId) {
          await db.recursiveDelete(word.ref);
          result.documents++;
          continue;
        }
        await remove(await authoredIn(db, `trips/${t.id}/words/${word.id}/comments`, memberId));
        const mute = word.ref.collection('commentMutes').doc(memberId);
        if ((await mute.get()).exists) await remove([mute]);
      }
      await remove(await authoredIn(db, `trips/${t.id}/films`, memberId));
      await folder(`trips/${t.id}/${memberId}/`);
    }
  });

  await attempt(async () => {
    const photo = db.collection('memberPhotos').doc(memberId);
    if ((await photo.get()).exists) await remove([photo]);
  });

  console.log(`sharesRemoved ${memberId}: ${result.documents} documents, ${result.folders} folders`);
  if (failures.length > 0) throw failures[0];
  return result;
}
