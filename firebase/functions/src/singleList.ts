// « Une seule liste d'envies par personne » (PRD rév. 209, récit 9 ter, carte PRODUIT-58): once
// the switch config/app.features.singleList is turned on, the lists of an occasion still open are
// copied into the two permanent lists of their beneficiary. Nothing is deleted: the old lists stay
// as they are until they are removed after the 21st, so turning the switch back off loses nothing.
//
// - An item the beneficiary sees goes to « Mes envies » (wishes-{id}), tagged with the occasion of
//   its list when it is Christmas or a birthday.
// - An item hidden from them (a surprise list, or a surprise idea on their own list) goes to the
//   « Idées de la famille » (ideas-{id}).
// - Its reservation, the parts of a joint gift and the notebook entries of the givers follow it.
//
// - A list is copied only when every member who reads the target list already reads it (its
//   viewerIds): a copy never shows a gift to someone its creator kept it from. Any other list is
//   left as it is, logged and marked skipped in migrations/singleList/lists/{listId}.
// - The day after the date of the list stays the day of the reveal (revealAfter on the reservation).
// - A list copied is marked copied there too: the morning reveal of the old lists leaves it alone,
//   so its beneficiary is never told twice about the same gift.
//
// Idempotent: each item is copied in one batch together with its mark, migrations/singleList/items/
// {listId}_{itemId}; a marked item is never copied again, even after a deletion of its copy, so
// turning the switch on twice makes no duplicate.

import type { DocumentReference, DocumentSnapshot, Firestore } from 'firebase-admin/firestore';
import { FieldValue } from 'firebase-admin/firestore';
import { notebookEntryId } from './notebook';

export type SingleListTarget = 'wishes' | 'ideas';

/** Where an item of an occasion list goes: « Mes envies » when its beneficiary sees it, else the ideas of the family. */
export function singleListTarget(listKind: unknown, visibleToBeneficiary: unknown): SingleListTarget {
  return listKind === 'surprise' || visibleToBeneficiary === false ? 'ideas' : 'wishes';
}

/** The occasion an item keeps from its list: only Christmas and a birthday have one in « Mes envies ». */
export function keptOccasion(listOccasion: unknown): 'christmas' | 'birthday' | null {
  return listOccasion === 'christmas' || listOccasion === 'birthday' ? listOccasion : null;
}

/** The id of the copy: stable, so that a mark lost with its batch is never needed to avoid a second copy. */
export function copiedItemId(listId: string, itemId: string): string {
  return `from-${listId}-${itemId}`;
}

const MARKS = 'migrations/singleList/items';
export const LIST_MARKS = 'migrations/singleList/lists';

/**
 * The members who would read the copy of a list of [beneficiaryId] but not the list itself
 * ([viewerIds]). « Mes envies » and the ideas are both read by the whole family but the person
 * (who, in « Mes envies », only gets what they already saw); a child without the app reads nothing.
 */
export function widenedAudience(beneficiaryId: string, viewerIds: unknown, members: { id: string; profile?: unknown }[]): string[] {
  const viewers = new Set(Array.isArray(viewerIds) ? viewerIds.map(String) : []);
  return members
    .filter((m) => m.profile !== 'childWithoutApp' && m.id !== beneficiaryId && !viewers.has(m.id))
    .map((m) => m.id);
}

/** Copies every item still to copy. Returns the number of items copied. Does nothing while the switch is off. */
export async function migrateToSingleList(db: Firestore): Promise<number> {
  const config = await db.doc('config/app').get();
  if (config.get('features.singleList') !== true) return 0;
  const lists = await db.collection('giftLists').where('kind', 'in', ['own', 'surprise']).get();
  const members = (await db.collection('members').get()).docs.map((m) => ({ id: m.id, profile: m.get('profile') }));
  let copied = 0;
  for (const list of lists.docs) {
    // A revealed list is past: its gifts were given, they belong to its own history.
    if (list.get('revealed') === true) continue;
    const beneficiaryId = String(list.get('beneficiaryId') ?? '');
    if (!beneficiaryId) continue;
    const listMark = db.doc(`${LIST_MARKS}/${list.id}`);
    const items = await list.ref.collection('items').get();
    const widened = widenedAudience(beneficiaryId, list.get('viewerIds'), members);
    if (widened.length > 0) {
      console.warn(`singleList: ${list.id} left as it is, ${widened.length} member(s) would see it`);
      await listMark.set({ status: 'skipped', reason: 'audience', missing: widened.length, at: FieldValue.serverTimestamp() });
      continue;
    }
    let failed = false;
    for (const item of items.docs) {
      try {
        if (await copyItem(db, list, item, beneficiaryId)) copied++;
      } catch (e) {
        // One item that fails never holds back the others; the next run tries it again.
        failed = true;
        console.error(`singleList: ${list.id}/${item.id}`, e);
      }
    }
    if (!failed) await listMark.set({ status: 'copied', at: FieldValue.serverTimestamp() });
  }
  return copied;
}

async function copyItem(db: Firestore, list: DocumentSnapshot, item: DocumentSnapshot, beneficiaryId: string): Promise<boolean> {
  const mark = db.doc(`${MARKS}/${list.id}_${item.id}`);
  if ((await mark.get()).exists) return false;
  const target = singleListTarget(list.get('kind'), item.get('visibleToBeneficiary'));
  const targetList = db.collection('giftLists').doc(`${target}-${beneficiaryId}`);
  await ensureList(targetList, target, beneficiaryId, list);
  const newId = copiedItemId(list.id, item.id);
  const occasion = keptOccasion(list.get('occasion'));
  const claim = await list.ref.collection('claims').doc(item.id).get();
  const shares = claim.exists ? await claim.ref.collection('shares').get() : null;

  const batch = db.batch();
  batch.set(targetList.collection('items').doc(newId), {
    ...item.data(),
    occasion,
    visibleToBeneficiary: target === 'wishes',
  });
  if (claim.exists) {
    // The occasion of the list becomes the one of the reservation, and its date stays the day of
    // the reveal: a surprise for a dinner after the birthday is never revealed before the dinner.
    const date = list.get('date');
    batch.set(targetList.collection('claims').doc(newId), {
      ...claim.data(), occasion, ...(typeof date === 'string' ? { revealAfter: date } : {}),
    });
    for (const share of shares?.docs ?? []) batch.set(targetList.collection('claims').doc(newId).collection('shares').doc(share.id), share.data());
    // The notebooks of the givers keep their state and note; the old entry says where it went.
    const givers = new Set([String(claim.get('holderId') ?? ''), ...(shares?.docs.map((s) => s.id) ?? [])]);
    for (const giver of givers) {
      if (!giver) continue;
      const entries = db.collection('notebooks').doc(giver).collection('entries');
      const old = await entries.doc(notebookEntryId(list.id, item.id)).get();
      if (!old.exists) continue;
      const movedTo = notebookEntryId(targetList.id, newId);
      batch.set(entries.doc(movedTo), { ...old.data(), listId: targetList.id, itemId: newId, updatedAt: FieldValue.serverTimestamp() });
      batch.update(old.ref, { movedTo });
    }
  }
  batch.set(mark, { listId: list.id, itemId: item.id, target: targetList.id, copiedId: newId, copiedAt: FieldValue.serverTimestamp() });
  await batch.commit();
  return true;
}

/** Creates the permanent list of [beneficiaryId] when nobody started it yet. */
async function ensureList(ref: DocumentReference, kind: SingleListTarget, beneficiaryId: string, from: DocumentSnapshot): Promise<void> {
  if ((await ref.get()).exists) return;
  // « Mes envies » is kept by its beneficiary; the ideas by the one who prepared the surprise.
  const creatorId = kind === 'wishes' ? beneficiaryId : String(from.get('creatorId') ?? '');
  const creatorName = kind === 'wishes'
    ? String((await ref.firestore.doc(`members/${beneficiaryId}`).get()).get('firstName') ?? '')
    : String(from.get('creatorName') ?? '');
  await ref.create({
    kind, title: kind === 'wishes' ? 'Mes envies' : 'Idées de la famille', occasion: null, date: null,
    beneficiaryId, viewerIds: [], creatorId, creatorName, revealed: false,
    createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
  }).catch((e: { code?: number }) => {
    // Created between the read and the create (6, ALREADY_EXISTS): nothing to do.
    if (e?.code !== 6) throw e;
  });
}
