// Les comptes du voyage (PRD rév. 193, récit 16 bis): the book goes with its trip, against the
// Firestore emulator (npm run test:functions in firebase/). Fictional family only.
import { after, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { deleteExpenseBook, expenseBookId } from '../src/expenses';

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
});
