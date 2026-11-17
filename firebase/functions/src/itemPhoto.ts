// The photo of a wish or an idea (récit 44) in Storage: « {base}.jpg » and « {base}-thumb.jpg »,
// where the item names {base} in its field photo. The server copies it where the item goes
// (PRD rév. 237): to « On m'a offert » at the reveal, and to the single list (récit 9 ter), before
// the old item is removed, since the removal deletes its photo (onGiftItemWritten).
import { getStorage } from 'firebase-admin/storage';

/** Copies the photo [from] to [to] (bases, without « .jpg »); true when the photo itself was copied. */
export type PhotoCopier = (from: string, to: string) => Promise<boolean>;

/** The copier of Storage. Without its thumbnail, a photo still goes: the app falls back on the photo. */
export const storageCopier: PhotoCopier = async (from, to) => {
  const bucket = getStorage().bucket();
  try {
    await bucket.file(`${from}.jpg`).copy(bucket.file(`${to}.jpg`));
  } catch (e) {
    console.warn(`itemPhoto: ${from} not copied`, e);
    return false;
  }
  await bucket.file(`${from}-thumb.jpg`).copy(bucket.file(`${to}-thumb.jpg`)).catch(() => undefined);
  return true;
};

/** Removes exactly « {base}.jpg » and « {base}-thumb.jpg », never another file that starts the same way. */
export async function deleteItemPhoto(base: string): Promise<void> {
  const bucket = getStorage().bucket();
  await Promise.all([
    bucket.file(`${base}.jpg`).delete({ ignoreNotFound: true }),
    bucket.file(`${base}-thumb.jpg`).delete({ ignoreNotFound: true }),
  ]);
}

/**
 * Where the photo of an item copied to [targetListId] goes: same author and name, in the folder its
 * new visibility asks (isValidItemPhoto in firestore.rules), so the app can still keep or change it.
 * Null for a path that is not the photo of an item.
 */
export function copiedItemPhoto(photo: unknown, targetListId: string, visibleToBeneficiary: boolean): string | null {
  if (typeof photo !== 'string') return null;
  const match = /^giftLists\/[^/]+\/(?:shared|hidden)\/([^/]+)\/([A-Za-z0-9]{1,40})$/.exec(photo);
  if (!match) return null;
  return `giftLists/${targetListId}/${visibleToBeneficiary ? 'shared' : 'hidden'}/${match[1]}/${match[2]}`;
}

/** The photo of a wish in « On m'a offert »: giftLists/{wishesId}/received/{receivedId}/photo (storage.rules). */
export function receivedPhoto(listId: string, receivedId: string): string {
  return `giftLists/${listId}/received/${receivedId}/photo`;
}
