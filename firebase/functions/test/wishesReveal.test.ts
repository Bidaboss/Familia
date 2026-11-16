// « Mes envies » révélées (récit 10 bis): the day after the occasion, the taken wishes go to
// « On m'a offert », against the Firestore emulator (npm run test:functions in firebase/).
// Fictional family only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { GiftMember, onGiftItemChanged } from '../src/gifts';
import { Notice } from '../src/logic';
import { notebookEntryId, syncNotebookEntry } from '../src/notebook';
import { Sender } from '../src/store';
import { giverNames, revealWishes, wishesRevealOccasion } from '../src/wishesReveal';

const ines: GiftMember = { id: 'ines', firstName: 'Inès', profile: 'adult', parentIds: [], birthDate: '1990-03-14' };
const camille: GiftMember = { id: 'camille', firstName: 'Camille', profile: 'adult', parentIds: [], birthDate: '1988-12-09' };
const jules: GiftMember = { id: 'jules', firstName: 'Jules', profile: 'adult', parentIds: [], birthDate: null };

describe('wishes reveal logic', () => {
  test('the day after a birthday, or after Christmas', () => {
    assert.equal(wishesRevealOccasion('1990-03-14', '2027-03-15'), 'birthday');
    assert.equal(wishesRevealOccasion('1990-03-14', '2027-03-14'), null);
    assert.equal(wishesRevealOccasion('1990-03-14', '2026-12-26'), 'christmas');
    assert.equal(wishesRevealOccasion(null, '2026-12-26'), 'christmas');
    assert.equal(wishesRevealOccasion(null, '2026-12-25'), null);
    // Born on 28 February: the day after is 1 March, even in a leap year.
    assert.equal(wishesRevealOccasion('2000-02-28', '2028-02-29'), 'birthday');
  });

  test('the givers, the responsible first, each once', () => {
    const members = [ines, camille, jules];
    assert.deepEqual(giverNames('camille', [], members, 'Camille'), ['Camille']);
    assert.deepEqual(giverNames('camille', ['camille', 'jules'], members, 'Camille'), ['Camille', 'Jules']);
    // A member who left the family: the name kept on the reservation.
    assert.deepEqual(giverNames('gone', ['jules'], members, 'Léon'), ['Léon', 'Jules']);
  });
});

assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');

const app = initializeApp({ projectId: 'demo-familia' }, 'wishes-reveal-test');
const db = getFirestore(app);
after(() => deleteApp(app));

function fakeSender() {
  const sent: { token: string; notice: Notice }[] = [];
  const send: Sender = async (token, notice) => {
    sent.push({ token, notice });
    return 'ok';
  };
  return { sent, send };
}

function member(m: GiftMember) {
  return {
    firstName: m.firstName, email: null, birthDate: m.birthDate, householdId: 'h1', profile: m.profile,
    isAdmin: false, parentIds: m.parentIds, uid: null,
  };
}

const LIST = 'wishes-ines';
const list = () => db.collection('giftLists').doc(LIST);
// Inès's birthday is 14 March: the reveal runs on the 15th, at 8 a.m. in Paris.
const DAY_AFTER = new Date('2027-03-15T07:00:00Z');

describe('wishes reveal', () => {
  beforeEach(async () => {
    await db.recursiveDelete(db.collection('giftLists'));
    await db.recursiveDelete(db.collection('notebooks'));
    await db.recursiveDelete(db.collection('members'));
    await db.doc('config/app').set({ features: { gifts: true, wishesReveal: true } });
    for (const m of [ines, camille, jules]) await db.doc(`members/${m.id}`).set(member(m));
    await db.doc('members/ines/devices/token-ines').set({});
    await list().set({
      kind: 'wishes', title: '', occasion: null, date: null, beneficiaryId: 'ines',
      viewerIds: ['ines', 'camille', 'jules'], creatorId: 'ines', creatorName: 'Inès', revealed: false,
    });
    const item = (title: string) => ({ title, url: 'https://example.com/' + title.length, price: 30, authorId: 'ines', visibleToBeneficiary: true });
    await list().collection('items').doc('book').set(item('Un livre'));
    await list().collection('items').doc('scarf').set(item('Une écharpe'));
    await list().collection('items').doc('lamp').set(item('Une lampe'));
    await list().collection('claims').doc('book').set({ type: 'solo', holderId: 'camille', holderName: 'Camille' });
    await list().collection('claims').doc('scarf').set({ type: 'shared', holderId: 'jules', holderName: 'Jules' });
    await list().collection('claims').doc('scarf').collection('shares').doc('jules').set({ amount: 10 });
    await list().collection('claims').doc('scarf').collection('shares').doc('camille').set({ amount: 15 });
  });

  test('each taken wish goes to « On m\'a offert », with the givers and never the amounts', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await revealWishes(db, send, DAY_AFTER), 2);
    const book = await list().collection('received').doc('book').get();
    assert.equal(book.get('title'), 'Un livre');
    assert.deepEqual(book.get('givers'), ['Camille']);
    assert.equal(book.get('joint'), false);
    assert.equal(book.get('occasion'), 'birthday');
    assert.equal(book.get('year'), 2027);
    assert.equal(book.get('thanked'), false);
    const scarf = await list().collection('received').doc('scarf').get();
    assert.deepEqual(scarf.get('givers'), ['Jules', 'Camille']);
    assert.equal(scarf.get('joint'), true);
    assert.equal(JSON.stringify(scarf.data()).includes('amount'), false);
    // The taken wishes leave the list with their reservation; the lamp nobody took stays.
    assert.deepEqual((await list().collection('items').get()).docs.map((d) => d.id), ['lamp']);
    assert.equal((await list().collection('claims').get()).size, 0);
    assert.equal((await list().collection('claims').doc('scarf').collection('shares').get()).size, 0);
    // Only Inès is told.
    assert.deepEqual(sent.map((s) => s.token), ['token-ines']);
    assert.equal(sent[0].notice.listId, LIST);
  });

  test('nothing the day of the occasion, nor without the switch', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await revealWishes(db, send, new Date('2027-03-14T07:00:00Z')), 0);
    await db.doc('config/app').set({ features: { gifts: true, wishesReveal: false } });
    assert.equal(await revealWishes(db, send, DAY_AFTER), 0);
    assert.equal((await list().collection('items').get()).size, 3);
    assert.equal(sent.length, 0);
  });

  test('the givers keep the gift in their notebook, and nobody reads « Réservation annulée »', async () => {
    const entry = db.doc(`notebooks/camille/entries/${notebookEntryId(LIST, 'book')}`);
    await syncNotebookEntry(db, LIST, 'book', 'camille');
    assert.equal((await entry.get()).exists, true);
    const before = (await list().collection('items').doc('book').get()).data() as never;
    await revealWishes(db, fakeSender().send, DAY_AFTER);
    // What the triggers do once the claim and the item are gone.
    await syncNotebookEntry(db, LIST, 'book', 'camille');
    assert.equal((await entry.get()).exists, true);
    const { sent, send } = fakeSender();
    await db.doc('members/camille/devices/token-camille').set({});
    assert.equal(await onGiftItemChanged(db, send, LIST, 'book', before, undefined), 0);
    assert.equal(sent.length, 0);
  });

  test('a child without the app has nothing revealed', async () => {
    await db.doc('members/ines').update({ profile: 'childWithoutApp' });
    assert.equal(await revealWishes(db, fakeSender().send, DAY_AFTER), 0);
    assert.equal((await list().collection('items').get()).size, 3);
  });
});
