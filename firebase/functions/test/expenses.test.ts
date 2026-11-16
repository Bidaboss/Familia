// Les comptes du voyage (PRD rév. 193, récit 16 bis): the book goes with its trip, against the
// Firestore emulator (npm run test:functions in firebase/). Fictional family only.
import { after, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { deleteExpenseBook, expenseBookId, expenseNotice, formatEuroCents, historyEntry, sendExpenseNotices, writeExpenseHistory } from '../src/expenses';
import type { Notice } from '../src/logic';
import type { Sender } from '../src/store';

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
    await db.doc('expenseNotices/trip_tx1').set({ day: '2027-07-12' });
    await db.doc('expenseNotices/trip_tx2').set({ day: '2027-07-12' });

    await deleteExpenseBook(db, 'trip', 'tx1');

    assert.equal((await book.get()).exists, false);
    assert.equal((await book.collection('expenses').doc('e1').get()).exists, false);
    assert.equal((await book.collection('expenses').doc('e1').collection('history').doc('h1').get()).exists, false);
    assert.equal((await book.collection('settlements').doc('s1').get()).exists, false);
    assert.equal((await db.doc('expenseBooks/trip_tx2').get()).exists, true);
    assert.equal((await db.doc('expenseNotices/trip_tx1').get()).exists, false);
    assert.equal((await db.doc('expenseNotices/trip_tx2').get()).exists, true);

    // The book of an event (PRODUIT-57) goes the same way, and never the trip's of the same id.
    assert.equal(expenseBookId('event', 'tx2'), 'event_tx2');
    await db.doc('expenseBooks/event_tx2').set({ containerType: 'event', containerId: 'tx2' });
    await db.doc('expenseBooks/event_tx2/expenses/e1').set({ title: 'Fromage' });
    await deleteExpenseBook(db, 'event', 'tx2');
    assert.equal((await db.doc('expenseBooks/event_tx2').get()).exists, false);
    assert.equal((await db.doc('expenseBooks/event_tx2/expenses/e1').get()).exists, false);
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
    await db.doc('trips/tx3').set({ title: 'Voyage tx3' });
    await db.doc('expenseBooks/trip_tx3').set({ containerType: 'trip', containerId: 'tx3' });
    await writeExpenseHistory(db, 'trip_tx3', 'e1', entry);
    const lines = await db.collection('expenseBooks/trip_tx3/expenses/e1/history').get();
    assert.equal(lines.size, 1);
    assert.equal(lines.docs[0].get('title'), 'Péage');
    await writeExpenseHistory(db, 'trip_tx4', 'e1', entry);
    assert.equal((await db.collection('expenseBooks/trip_tx4/expenses/e1/history').get()).size, 0);
  });

  test('deleting a trip leaves nothing under its book, history included', async () => {
    // The trip is deleted first; the removal of each expense then fires the history, which must write nothing.
    const book = db.doc('expenseBooks/trip_tx5');
    await book.set({ containerType: 'trip', containerId: 'tx5' });
    await book.collection('expenses').doc('e1').set({ title: 'Courses', amountCents: 3000 });
    const deleted = historyEntry({ title: 'Courses', amountCents: 3000 }, undefined, null)!;
    await deleteExpenseBook(db, 'trip', 'tx5');
    await writeExpenseHistory(db, 'trip_tx5', 'e1', deleted);
    // Even while the book is still there, a book whose trip is gone takes no history.
    await book.set({ containerType: 'trip', containerId: 'tx5' });
    await writeExpenseHistory(db, 'trip_tx5', 'e1', deleted);
    await deleteExpenseBook(db, 'trip', 'tx5');
    assert.equal((await book.listCollections()).length, 0);
    assert.equal((await db.collection('expenseBooks/trip_tx5/expenses/e1/history').get()).size, 0);
  });

  test('the notice of the day groups what the others added, and says nothing of one\'s own', () => {
    assert.equal(formatEuroCents(12500), '125,00\u00A0€');
    assert.equal(formatEuroCents(5), '0,05\u00A0€');
    const added = [{ authorId: 'm1', amountCents: 6000 }, { authorId: 'm2', amountCents: 2550 }, { authorId: 'm2', amountCents: 1000 }];
    assert.deepEqual(expenseNotice({ type: 'trip', id: 'tn1' }, 'Corse', 'm1', added), {
      channel: 'trips', title: 'Comptes · Corse', body: '2 nouvelles dépenses, 35,50\u00A0€. Le solde est à jour dans l\'appli.', tripId: 'tn1',
    });
    assert.equal(expenseNotice({ type: 'trip', id: 'tn1' }, 'Corse', 'm2', added)?.body.startsWith('Une nouvelle dépense'), true);
    assert.equal(expenseNotice({ type: 'trip', id: 'tn1' }, 'Corse', 'm1', [{ authorId: 'm1', amountCents: 100 }]), null);
    // An event's notice opens the event (PRODUIT-57).
    assert.deepEqual(expenseNotice({ type: 'event', id: 'ev1' }, 'Raclette', 'm1', added), {
      channel: 'events', title: 'Comptes · Raclette', body: '2 nouvelles dépenses, 35,50\u00A0€. Le solde est à jour dans l\'appli.', eventId: 'ev1',
    });
  });

  test('once a day from 20 h, to the adult participants only, and to the admin alone while the module is off', async () => {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => { sent.push({ token, notice }); return 'ok'; };
    for (const c of ['members', 'expenseNotices', 'trips']) await db.recursiveDelete(db.collection(c));
    await db.recursiveDelete(db.collection('expenseBooks'));
    // 21 h in Paris on 12/07/2027; the expenses were added that afternoon.
    const evening = new Date('2027-07-12T19:00:00Z');
    const afternoon = new Date('2027-07-12T13:00:00Z');
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { expenses: false } });
    batch.set(db.doc('config/family'), { ownerMemberId: 'n-admin' });
    for (const [id, profile] of [['n-admin', 'adult'], ['n-adult', 'adult'], ['n-child', 'child'], ['n-away', 'adult']] as const) {
      batch.set(db.doc(`members/${id}`), { firstName: id, profile, parentIds: [] });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: evening });
    }
    batch.set(db.doc('trips/tn1'), { title: 'Corse', participantIds: ['n-admin', 'n-adult', 'n-child'] });
    batch.set(db.doc('expenseBooks/trip_tn1'), { containerType: 'trip', containerId: 'tn1', closed: false });
    batch.set(db.doc('expenseBooks/trip_tn1/expenses/e1'), { authorId: 'n-adult', amountCents: 4000, createdAt: afternoon });
    await batch.commit();

    assert.equal(await sendExpenseNotices(db, send, new Date('2027-07-12T17:00:00Z')), 0); // 19 h in Paris.
    assert.equal(await sendExpenseNotices(db, send, evening), 1);
    assert.deepEqual(sent.map((s) => s.token), ['token-n-admin']);
    assert.equal(await sendExpenseNotices(db, send, new Date('2027-07-12T20:30:00Z')), 0); // Once a day.

    // The next evening, with the module on: only what was added since, to every adult participant but its author.
    await db.doc('config/app').set({ features: { expenses: true } });
    await db.doc('expenseBooks/trip_tn1/expenses/e2').set({ authorId: 'n-admin', amountCents: 1500, createdAt: new Date('2027-07-13T10:00:00Z') });
    sent.length = 0;
    assert.equal(await sendExpenseNotices(db, send, new Date('2027-07-13T19:00:00Z')), 1);
    assert.deepEqual(sent.map((s) => s.token), ['token-n-adult']);
    assert.match(sent[0].notice.body, /^Une nouvelle dépense, 15,00/);
  });

  test('an event tells the adults who come and its author, never « Peut-être »', async () => {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => { sent.push({ token, notice }); return 'ok'; };
    for (const c of ['members', 'expenseNotices', 'events']) await db.recursiveDelete(db.collection(c));
    await db.recursiveDelete(db.collection('expenseBooks'));
    const evening = new Date('2027-07-12T19:00:00Z');
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { expenses: true } });
    for (const [id, profile] of [['v-author', 'adult'], ['v-yes', 'adult'], ['v-maybe', 'adult'], ['v-child', 'child'], ['v-payer', 'adult']] as const) {
      batch.set(db.doc(`members/${id}`), { firstName: id, profile, parentIds: [] });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: evening });
    }
    batch.set(db.doc('events/ev1'), { title: 'Raclette', authorId: 'v-author' });
    for (const [id, status] of [['v-yes', 'yes'], ['v-maybe', 'maybe'], ['v-child', 'yes'], ['v-payer', 'yes']]) {
      batch.set(db.doc(`events/ev1/rsvps/${id}`), { status });
    }
    batch.set(db.doc('expenseBooks/event_ev1'), { containerType: 'event', containerId: 'ev1', closed: false });
    batch.set(db.doc('expenseBooks/event_ev1/expenses/e1'), { authorId: 'v-payer', amountCents: 3000, createdAt: new Date('2027-07-12T13:00:00Z') });
    await batch.commit();

    assert.equal(await sendExpenseNotices(db, send, evening), 2);
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-v-author', 'token-v-yes']);
    assert.equal(sent[0].notice.eventId, 'ev1');
    assert.equal(sent[0].notice.title, 'Comptes · Raclette');
  });
});

