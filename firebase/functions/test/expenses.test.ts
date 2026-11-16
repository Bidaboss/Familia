// Les comptes du voyage (PRD rév. 193, récit 16 bis): the book goes with its trip, against the
// Firestore emulator (npm run test:functions in firebase/). Fictional family only.
import { after, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { deleteExpenseBook, expenseBookId, historyEntry, writeExpenseHistory } from '../src/expenses';

describe('the accounts of a trip', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'expenses-test');
  const db = getFirestore(app);
  after(async () => { await deleteApp(app); });

  test('one book per container, removed with everything under it, the others kept', async () => {
    assert.equal(expenseBookId('trip', 'tx1'), 'trip_tx1');
    const book = db.doc('expenseBooks/trip_tx1');
    await book.set({ containerType: 'trip', containerId: 'tx1' });
    await book.collection('expenses').doc('e1').set({ title: 'Courses' });
    await book.collection('expenses').doc('e1').collection('history').doc('h1').set({ action: 'created' });
    await book.collection('settlements').doc('s1').set({ amountCents: 1000 });
    await db.doc('expenseBooks/trip_tx2').set({ containerType: 'trip', containerId: 'tx2' });

    await deleteExpenseBook(db, 'trip', 'tx1');

    assert.equal((await book.get()).exists, false);
    assert.equal((await book.collection('expenses').doc('e1').get()).exists, false);
    assert.equal((await book.collection('expenses').doc('e1').collection('history').doc('h1').get()).exists, false);
    assert.equal((await book.collection('settlements').doc('s1').get()).exists, false);
    assert.equal((await db.doc('expenseBooks/trip_tx2').get()).exists, true);
  });

  test('the history says who did what, and which fields changed', () => {
    const camille = { id: 'm1', firstName: 'Camille' };
    const first = { title: 'Courses', amountCents: 6000, date: '2027-07-12', householdIds: ['h1', 'h2'] };
    assert.deepEqual(historyEntry(undefined, first, camille), {
      action: 'created', byId: 'm1', byName: 'Camille', title: 'Courses', amountCents: 6000, changed: [],
    });
    const edited = historyEntry(first, { ...first, amountCents: 5000, householdIds: ['h1'] }, camille);
    assert.equal(edited?.action, 'edited');
    assert.deepEqual(edited?.changed, ['amountCents', 'householdIds']);
    assert.equal(historyEntry(first, { ...first, updatedAt: 'later' } as never, camille), null);
    assert.equal(historyEntry(first, undefined, null)?.action, 'deleted');
    assert.equal(historyEntry(first, undefined, null)?.byId, null);
  });

  test('the history is written under the expense, never once the book is gone', async () => {
    const entry = historyEntry(undefined, { title: 'Péage', amountCents: 1200 }, { id: 'm1', firstName: 'Camille' })!;
    await db.doc('expenseBooks/trip_tx3').set({ containerType: 'trip', containerId: 'tx3' });
    await writeExpenseHistory(db, 'trip_tx3', 'e1', entry);
    const lines = await db.collection('expenseBooks/trip_tx3/expenses/e1/history').get();
    assert.equal(lines.size, 1);
    assert.equal(lines.docs[0].get('title'), 'Péage');
    await writeExpenseHistory(db, 'trip_tx4', 'e1', entry);
    assert.equal((await db.collection('expenseBooks/trip_tx4/expenses/e1/history').get()).size, 0);
  });
});
