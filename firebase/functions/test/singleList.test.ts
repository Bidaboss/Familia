// « Liste unique » (récit 9 ter): the open lists of an occasion are copied into « Mes envies » and
// the ideas of the family, without deleting anything, against the Firestore emulator
// (npm run test:functions in firebase/). Fictional family only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { notebookEntryId } from '../src/notebook';
import { copiedItemId, keptOccasion, migrateToSingleList, singleListTarget } from '../src/singleList';

describe('single list logic', () => {
  test('what the beneficiary sees goes to their wishes, the rest to the ideas', () => {
    assert.equal(singleListTarget('own', true), 'wishes');
    assert.equal(singleListTarget('own', false), 'ideas');
    assert.equal(singleListTarget('surprise', true), 'ideas');
    assert.equal(singleListTarget('surprise', false), 'ideas');
  });

  test('only Christmas and a birthday are kept as the occasion of a wish', () => {
    assert.equal(keptOccasion('christmas'), 'christmas');
    assert.equal(keptOccasion('birthday'), 'birthday');
    assert.equal(keptOccasion('birth'), null);
    assert.equal(keptOccasion('other'), null);
    assert.equal(keptOccasion(null), null);
  });
});

assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');

const app = initializeApp({ projectId: 'demo-familia' }, 'single-list-test');
const db = getFirestore(app);
after(() => deleteApp(app));

const lists = db.collection('giftLists');
const made = Timestamp.fromDate(new Date('2026-11-10T10:00:00Z'));

function list(kind: string, beneficiaryId: string, occasion: string | null, extra: Record<string, unknown> = {}) {
  return {
    kind, title: `Liste ${kind}`, occasion, date: '2026-12-25', beneficiaryId, viewerIds: ['camille', 'ines'],
    creatorId: 'camille', creatorName: 'Camille', revealed: false, ...extra,
  };
}

function item(title: string, visibleToBeneficiary: boolean, authorId = 'ines') {
  return { title, url: null, price: 20, note: null, authorId, authorName: 'Auteur', visibleToBeneficiary, createdAt: made, updatedAt: made };
}

describe('single list migration', () => {
  beforeEach(async () => {
    for (const c of ['giftLists', 'notebooks', 'members', 'migrations']) await db.recursiveDelete(db.collection(c));
    await db.doc('config/app').set({ features: { gifts: true, singleList: true } });
    await db.doc('members/ines').set({ firstName: 'Inès', profile: 'adult', householdId: 'h1', parentIds: [] });
    // Inès's own Christmas list: a wish she wrote, and a surprise idea Camille hid in it.
    await lists.doc('noel-ines').set(list('own', 'ines', 'christmas'));
    await lists.doc('noel-ines').collection('items').doc('book').set(item('Un livre', true));
    await lists.doc('noel-ines').collection('items').doc('watch').set(item('Une montre', false, 'camille'));
    await lists.doc('noel-ines').collection('claims').doc('book').set({ type: 'shared', holderId: 'camille', holderName: 'Camille', createdAt: made });
    await lists.doc('noel-ines').collection('claims').doc('book').collection('shares').doc('jules').set({ amount: 10, memberName: 'Jules', received: false });
    await db.doc(`notebooks/camille/entries/${notebookEntryId('noel-ines', 'book')}`).set({
      source: 'claim', listId: 'noel-ines', itemId: 'book', personId: 'ines', personName: 'Inès', occasion: 'christmas', year: 2026,
      title: 'Un livre', url: null, price: 20, note: 'Caché au grenier', status: 'bought', createdAt: made, updatedAt: made,
    });
    // A surprise list for her birthday, by Camille.
    await lists.doc('surprise-ines').set(list('surprise', 'ines', 'birthday', { viewerIds: ['camille'] }));
    await lists.doc('surprise-ines').collection('items').doc('scarf').set(item('Une écharpe', false, 'camille'));
    // A list already revealed: its gifts belong to its history.
    await lists.doc('old-ines').set(list('own', 'ines', 'christmas', { revealed: true }));
    await lists.doc('old-ines').collection('items').doc('lamp').set(item('Une lampe', true));
  });

  test('each open item is copied where it belongs, and nothing is deleted', async () => {
    assert.equal(await migrateToSingleList(db), 3);
    const wishes = lists.doc('wishes-ines');
    assert.equal((await wishes.get()).get('kind'), 'wishes');
    assert.equal((await wishes.get()).get('creatorId'), 'ines');
    assert.equal((await wishes.get()).get('creatorName'), 'Inès');
    const book = await wishes.collection('items').doc(copiedItemId('noel-ines', 'book')).get();
    assert.equal(book.get('title'), 'Un livre');
    assert.equal(book.get('occasion'), 'christmas');
    assert.equal(book.get('visibleToBeneficiary'), true);
    const ideas = lists.doc('ideas-ines');
    assert.equal((await ideas.get()).get('kind'), 'ideas');
    assert.equal((await ideas.get()).get('creatorId'), 'camille');
    assert.deepEqual((await ideas.collection('items').get()).docs.map((d) => d.id).sort(),
      [copiedItemId('noel-ines', 'watch'), copiedItemId('surprise-ines', 'scarf')].sort());
    const scarf = await ideas.collection('items').doc(copiedItemId('surprise-ines', 'scarf')).get();
    assert.equal(scarf.get('occasion'), 'birthday');
    assert.equal(scarf.get('visibleToBeneficiary'), false);
    // The revealed list is left out; the old lists keep everything.
    assert.equal((await wishes.collection('items').get()).size, 1);
    assert.equal((await lists.doc('noel-ines').collection('items').get()).size, 2);
    assert.equal((await lists.doc('noel-ines').collection('claims').doc('book').get()).exists, true);
    assert.equal((await lists.doc('surprise-ines').collection('items').get()).size, 1);
  });

  test('the reservation, its parts and the notebook of the giver follow the item', async () => {
    await migrateToSingleList(db);
    const id = copiedItemId('noel-ines', 'book');
    const claim = await lists.doc('wishes-ines').collection('claims').doc(id).get();
    assert.equal(claim.get('holderId'), 'camille');
    assert.equal(claim.get('occasion'), 'christmas');
    assert.equal(claim.get('createdAt').toMillis(), made.toMillis());
    assert.equal((await claim.ref.collection('shares').doc('jules').get()).get('amount'), 10);
    const entry = await db.doc(`notebooks/camille/entries/${notebookEntryId('wishes-ines', id)}`).get();
    assert.equal(entry.get('status'), 'bought');
    assert.equal(entry.get('note'), 'Caché au grenier');
    assert.equal(entry.get('listId'), 'wishes-ines');
    assert.equal(entry.get('itemId'), id);
    // The old entry stays, and says where it went: the app shows only one.
    const old = await db.doc(`notebooks/camille/entries/${notebookEntryId('noel-ines', 'book')}`).get();
    assert.equal(old.get('movedTo'), notebookEntryId('wishes-ines', id));
  });

  test('turned on twice, nothing is copied twice, even a copy deleted since', async () => {
    assert.equal(await migrateToSingleList(db), 3);
    await lists.doc('wishes-ines').collection('items').doc(copiedItemId('noel-ines', 'book')).delete();
    assert.equal(await migrateToSingleList(db), 0);
    assert.equal((await lists.doc('wishes-ines').collection('items').get()).size, 0);
    assert.equal((await lists.doc('ideas-ines').collection('items').get()).size, 2);
    // A new item added to an old list in the meantime is copied the next time.
    await lists.doc('noel-ines').collection('items').doc('mug').set(item('Une tasse', true));
    assert.equal(await migrateToSingleList(db), 1);
  });

  test('an existing « Mes envies » is kept as it is', async () => {
    await lists.doc('wishes-ines').set({ ...list('wishes', 'ines', null), title: 'Mes envies', date: null, viewerIds: [], creatorId: 'ines', creatorName: 'Inès' });
    await lists.doc('wishes-ines').collection('items').doc('tea').set(item('Un thé', true));
    await migrateToSingleList(db);
    assert.equal((await lists.doc('wishes-ines').collection('items').get()).size, 2);
    assert.equal((await lists.doc('wishes-ines').get()).get('title'), 'Mes envies');
  });

  test('nothing while the switch is off', async () => {
    await db.doc('config/app').set({ features: { gifts: true } });
    assert.equal(await migrateToSingleList(db), 0);
    assert.equal((await lists.doc('wishes-ines').get()).exists, false);
    assert.equal((await db.collection('migrations').doc('singleList').collection('items').get()).size, 0);
  });
});
