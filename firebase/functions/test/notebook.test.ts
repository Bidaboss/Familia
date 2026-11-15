// Mon carnet de cadeaux (récit 9 bis): reservations and parts taken up in the private notebook,
// against the Firestore emulator (npm run test:functions in firebase/). Fictional family only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import {
  deleteMemberNotebook, notebookEntryId, syncNotebookEntry, syncNotebookItem, takenUpFields, TakenUp, yearOf,
} from '../src/notebook';

describe('notebook logic', () => {
  const taken: TakenUp = {
    source: 'claim', listId: 'l', itemId: 'i', personId: 'ines', personName: 'Inès',
    occasion: null, year: null, title: 'Un livre', url: null, price: 15,
  };

  test('the occasion of « Mes envies » is the one the member chose', () => {
    assert.equal(takenUpFields(taken, { occasion: 'birthday', year: 2027 }).occasion, 'birthday');
    assert.equal(takenUpFields(taken, { occasion: 'birthday', year: 2027 }).year, 2027);
    assert.equal(takenUpFields(taken, undefined).occasion, null);
    // A list with an occasion always wins.
    assert.equal(takenUpFields({ ...taken, occasion: 'christmas', year: 2026 }, { occasion: 'birthday', year: 2027 }).occasion, 'christmas');
  });

  test('the year of a date', () => {
    assert.equal(yearOf('2026-12-25'), 2026);
    assert.equal(yearOf(null), null);
    assert.equal(yearOf('Noël'), null);
  });
});

assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');

const app = initializeApp({ projectId: 'demo-familia' }, 'notebook-test');
const db = getFirestore(app);
after(() => deleteApp(app));

const LIST = 'own1';
const ITEM = 'i1';
const entryOf = (memberId: string) => db.doc(`notebooks/${memberId}/entries/${notebookEntryId(LIST, ITEM)}`);

describe('notebook functions', () => {
  beforeEach(async () => {
    await db.recursiveDelete(db.collection('giftLists'));
    await db.recursiveDelete(db.collection('notebooks'));
    await db.doc('members/ines').set({ firstName: 'Inès' });
    await db.doc(`giftLists/${LIST}`).set({
      kind: 'own', title: 'Noël', occasion: 'christmas', date: '2026-12-25', beneficiaryId: 'ines',
      viewerIds: ['ines', 'camille', 'jules'], creatorId: 'ines', creatorName: 'Inès', revealed: false,
    });
    await db.doc(`giftLists/${LIST}/items/${ITEM}`).set({ title: 'Une machine à pain', url: 'https://example.com/pain', price: 120 });
  });

  test('« Je le prends » puts the gift in the notebook, with its whole price, and a release takes it out', async () => {
    await db.doc(`giftLists/${LIST}/claims/${ITEM}`).set({ type: 'solo', holderId: 'camille', holderName: 'Camille' });
    await syncNotebookEntry(db, LIST, ITEM, 'camille');
    const entry = await entryOf('camille').get();
    assert.equal(entry.get('source'), 'claim');
    assert.equal(entry.get('personName'), 'Inès');
    assert.equal(entry.get('occasion'), 'christmas');
    assert.equal(entry.get('year'), 2026);
    assert.equal(entry.get('price'), 120);
    assert.equal(entry.get('status'), 'idea');

    await db.doc(`giftLists/${LIST}/claims/${ITEM}`).delete();
    await syncNotebookEntry(db, LIST, ITEM, 'camille');
    assert.equal((await entryOf('camille').get()).exists, false);
  });

  test('a joint gift: each member has their own part, and what they follow is kept', async () => {
    await db.doc(`giftLists/${LIST}/claims/${ITEM}`).set({ type: 'shared', holderId: 'camille', holderName: 'Camille' });
    await syncNotebookEntry(db, LIST, ITEM, 'camille');
    // The responsible has no part yet: no price.
    assert.equal((await entryOf('camille').get()).get('price'), null);

    await db.doc(`giftLists/${LIST}/claims/${ITEM}/shares/jules`).set({ amount: 30, memberName: 'Jules', received: false });
    await syncNotebookEntry(db, LIST, ITEM, 'jules');
    assert.equal((await entryOf('jules').get()).get('source'), 'share');
    assert.equal((await entryOf('jules').get()).get('price'), 30);

    await entryOf('jules').update({ status: 'bought' });
    await db.doc(`giftLists/${LIST}/claims/${ITEM}/shares/jules`).update({ amount: 35 });
    await syncNotebookEntry(db, LIST, ITEM, 'jules');
    const entry = await entryOf('jules').get();
    assert.equal(entry.get('price'), 35);
    assert.equal(entry.get('status'), 'bought');

    // Leaving the joint gift takes it out; someone with nothing on the item has no entry.
    await db.doc(`giftLists/${LIST}/claims/${ITEM}/shares/jules`).delete();
    await syncNotebookEntry(db, LIST, ITEM, 'jules');
    assert.equal((await entryOf('jules').get()).exists, false);
    await syncNotebookEntry(db, LIST, ITEM, 'ines');
    assert.equal((await entryOf('ines').get()).exists, false);
  });

  test('a renamed item is renamed in the notebooks of who reserved it', async () => {
    await db.doc(`giftLists/${LIST}/claims/${ITEM}`).set({ type: 'solo', holderId: 'camille', holderName: 'Camille' });
    await syncNotebookEntry(db, LIST, ITEM, 'camille');
    await db.doc(`giftLists/${LIST}/items/${ITEM}`).update({ title: 'Une machine à pain sans gluten' });
    await syncNotebookItem(db, LIST, ITEM);
    assert.equal((await entryOf('camille').get()).get('title'), 'Une machine à pain sans gluten');
  });

  test('a removed member loses their notebook', async () => {
    await db.doc('notebooks/camille/entries/e1').set({ source: 'idea', title: 'Une écharpe' });
    await db.doc('notebooks/camille/budgets/b1').set({ amount: 50 });
    await deleteMemberNotebook(db, 'camille');
    assert.equal((await db.collection('notebooks/camille/entries').get()).size, 0);
    assert.equal((await db.collection('notebooks/camille/budgets').get()).size, 0);
  });
});
