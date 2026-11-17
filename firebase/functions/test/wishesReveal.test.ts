// « Mes envies » révélées (récit 10 bis): the day after the occasion, the taken wishes go to
// « On m'a offert », against the Firestore emulator (npm run test:functions in firebase/).
// Fictional family only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { deleteGiftListContent, GiftMember, onGiftItemChanged } from '../src/gifts';
import { Notice } from '../src/logic';
import { notebookEntryId, syncNotebookEntry } from '../src/notebook';
import { Sender } from '../src/store';
import { Timestamp } from 'firebase-admin/firestore';
import { claimOccasion, defaultWishOccasion, giverNames, occasionDay, receivedPath, revealWishes, thankGivers, wasMarkedReceived, wasRevealed } from '../src/wishesReveal';

const ines: GiftMember = { id: 'ines', firstName: 'Inès', profile: 'adult', parentIds: [], birthDate: '1990-03-14' };
const camille: GiftMember = { id: 'camille', firstName: 'Camille', profile: 'adult', parentIds: [], birthDate: '1988-12-09' };
const jules: GiftMember = { id: 'jules', firstName: 'Jules', profile: 'adult', parentIds: [], birthDate: null };

describe('wishes reveal logic', () => {
  test('by default, the next occasion: the birthday or Christmas, whichever comes first', () => {
    assert.deepEqual(defaultWishOccasion('1990-03-14', '2026-11-16'), { occasion: 'christmas', year: 2026 });
    assert.deepEqual(defaultWishOccasion('1990-12-01', '2026-11-16'), { occasion: 'birthday', year: 2026 });
    assert.deepEqual(defaultWishOccasion('1990-03-14', '2026-12-26'), { occasion: 'birthday', year: 2027 });
    assert.deepEqual(defaultWishOccasion(null, '2026-12-26'), { occasion: 'christmas', year: 2027 });
    // The day itself still counts.
    assert.deepEqual(defaultWishOccasion('1990-03-14', '2027-03-14'), { occasion: 'birthday', year: 2027 });
  });

  test('the occasion of a reservation: the chosen one after the day it was made, or the default', () => {
    const made = Timestamp.fromDate(new Date('2026-11-20T10:00:00Z'));
    assert.deepEqual(claimOccasion({ occasion: 'christmas', createdAt: made }, '1990-12-01', '2026-11-16'), { occasion: 'christmas', year: 2026 });
    assert.deepEqual(claimOccasion({ occasion: 'birthday', createdAt: made }, '1990-03-14', '2026-11-16'), { occasion: 'birthday', year: 2027 });
    assert.deepEqual(claimOccasion({ createdAt: made }, '1990-12-01', '2026-11-16'), { occasion: 'birthday', year: 2026 });
    // Without a date of birth, a birthday cannot be chosen: Christmas.
    assert.deepEqual(claimOccasion({ occasion: 'birthday', createdAt: made }, null, '2026-11-16'), { occasion: 'christmas', year: 2026 });
    // Without a date (the rules always set one): the default from the day of the check, which
    // keeps the gift hidden rather than reveal it too soon.
    assert.deepEqual(claimOccasion({}, '1990-12-01', '2026-11-16'), { occasion: 'birthday', year: 2026 });
    assert.deepEqual(claimOccasion({ occasion: 'christmas' }, '1990-12-01', '2026-12-26'), { occasion: 'christmas', year: 2027 });
    assert.equal(occasionDay({ occasion: 'birthday', year: 2027 }, '1990-03-14'), '2027-03-14');
    assert.equal(occasionDay({ occasion: 'christmas', year: 2026 }, null), '2026-12-25');
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
// Inès's birthday is 14 March: the reveal runs on the 15th, at 7 a.m. in Paris.
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
    await list().collection('claims').doc('book').set({
      type: 'solo', holderId: 'camille', holderName: 'Camille', occasion: 'birthday', createdAt: Timestamp.fromDate(new Date('2027-01-05T10:00:00Z')),
    });
    // Made in January: by default, for the birthday of 14 March.
    await list().collection('claims').doc('scarf').set({
      type: 'shared', holderId: 'jules', holderName: 'Jules', createdAt: Timestamp.fromDate(new Date('2027-01-10T10:00:00Z')),
    });
    await list().collection('claims').doc('scarf').collection('shares').doc('jules').set({ amount: 10 });
    await list().collection('claims').doc('scarf').collection('shares').doc('camille').set({ amount: 15 });
  });

  test('with « Plus de photos », the photo of the wish is copied to « On m\'a offert » before the removal (rév. 237)', async () => {
    await db.doc('config/app').set({ features: { gifts: true, wishesReveal: true, morePhotos: true } });
    await list().collection('items').doc('book').update({ photo: `giftLists/${LIST}/shared/ines/p1` });
    const copies: [string, string][] = [];
    const copy = async (from: string, to: string) => { copies.push([from, to]); return true; };
    assert.equal(await revealWishes(db, fakeSender().send, DAY_AFTER, copy), 2);
    assert.deepEqual(copies, [[`giftLists/${LIST}/shared/ines/p1`, `giftLists/${LIST}/received/book/photo`]]);
    const book = await list().collection('received').doc('book').get();
    assert.equal(book.get('photo'), `giftLists/${LIST}/received/book/photo`);
    // The scarf had no photo: its archive has none.
    assert.equal((await list().collection('received').doc('scarf').get()).get('photo'), undefined);
  });

  test('a photo that could not be copied, or the switch off, leaves the archive without it', async () => {
    await list().collection('items').doc('book').update({ photo: `giftLists/${LIST}/shared/ines/p1` });
    const copies: string[] = [];
    assert.equal(await revealWishes(db, fakeSender().send, DAY_AFTER, async (from) => { copies.push(from); return true; }), 2);
    assert.deepEqual(copies, []);
    assert.equal((await list().collection('received').doc('book').get()).get('photo'), undefined);

    await db.recursiveDelete(list().collection('received'));
    await db.doc('config/app').set({ features: { gifts: true, wishesReveal: true, morePhotos: true } });
    await list().collection('items').doc('lamp').set({ title: 'Une lampe', url: null, price: 30, authorId: 'ines', visibleToBeneficiary: true, photo: `giftLists/${LIST}/shared/ines/p2` });
    await list().collection('claims').doc('lamp').set({ type: 'solo', holderId: 'camille', holderName: 'Camille', occasion: 'birthday', createdAt: Timestamp.fromDate(new Date('2027-01-05T10:00:00Z')) });
    assert.equal(await revealWishes(db, fakeSender().send, DAY_AFTER, async () => false), 1);
    const lamp = await list().collection('received').doc('lamp').get();
    assert.equal(lamp.get('title'), 'Une lampe');
    assert.equal(lamp.get('photo'), undefined);
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

  test('a gift meant for Christmas stays hidden after the birthday, and is revealed after Christmas', async () => {
    await list().collection('claims').doc('book').update({ occasion: 'christmas' });
    assert.equal(await revealWishes(db, fakeSender().send, DAY_AFTER), 1);
    assert.equal((await list().collection('items').doc('book').get()).exists, true);
    assert.equal((await list().collection('claims').doc('book').get()).exists, true);
    assert.equal(await revealWishes(db, fakeSender().send, new Date('2027-12-26T06:00:00Z')), 1);
    assert.equal((await list().collection('received').doc('book').get()).get('occasion'), 'christmas');
  });

  test('a reservation whose occasion passed while the switch was off is revealed the next morning', async () => {
    await db.doc('config/app').set({ features: { gifts: true, wishesReveal: false } });
    assert.equal(await revealWishes(db, fakeSender().send, DAY_AFTER), 0);
    await db.doc('config/app').set({ features: { gifts: true, wishesReveal: true } });
    assert.equal(await revealWishes(db, fakeSender().send, new Date('2027-04-02T06:00:00Z')), 2);
  });

  test('the notebook of the holder and of each part shows the occasion of the reservation', async () => {
    await list().collection('claims').doc('book').update({ occasion: 'christmas' });
    await syncNotebookEntry(db, LIST, 'book', 'camille');
    const entry = await db.doc(`notebooks/camille/entries/${notebookEntryId(LIST, 'book')}`).get();
    assert.equal(entry.get('occasion'), 'christmas');
    assert.equal(entry.get('year'), 2027);
    await syncNotebookEntry(db, LIST, 'scarf', 'camille');
    const part = await db.doc(`notebooks/camille/entries/${notebookEntryId(LIST, 'scarf')}`).get();
    assert.equal(part.get('occasion'), 'birthday');
    assert.equal(part.get('year'), 2027);
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

  test('« Tu l\'as offert ? » asks each holder whose notebook does not say « Offert » yet', async () => {
    for (const id of ['camille', 'jules']) await db.doc(`members/${id}/devices/token-${id}`).set({});
    await db.doc(`notebooks/camille/entries/${notebookEntryId(LIST, 'book')}`).set({ status: 'given' });
    const { sent, send } = fakeSender();
    await revealWishes(db, send, DAY_AFTER);
    const asks = sent.filter((s) => s.notice.title === 'Tu l\'as offert ?');
    // Camille already said « Offert »; Jules, responsible of the joint gift, is asked; never Inès.
    assert.deepEqual(asks.map((s) => s.token), ['token-jules']);
    assert.match(asks[0].notice.body, /Une écharpe/);
    assert.match(asks[0].notice.body, /Inès/);
  });

  test('a wish the person marked « Reçue » cancels its reservation, and says why', async () => {
    await db.doc('members/camille/devices/token-camille').set({});
    await list().collection('claims').doc('lamp').set({ type: 'solo', holderId: 'camille', holderName: 'Camille', createdAt: Timestamp.now() });
    const before = (await list().collection('items').doc('lamp').get()).data() as never;
    // What the app writes in one batch: the archive, without a giver, and the removal.
    await list().collection('received').doc('lamp').set({ title: 'Une lampe', givers: [], giverIds: [], self: true });
    await list().collection('items').doc('lamp').delete();
    assert.equal(await wasRevealed(db, LIST, 'lamp'), false);
    assert.equal(await wasMarkedReceived(db, LIST, 'lamp'), true);
    const { sent, send } = fakeSender();
    assert.equal(await onGiftItemChanged(db, send, LIST, 'lamp', before, undefined), 1);
    assert.deepEqual(sent.map((s) => s.token), ['token-camille']);
    assert.match(sent[0].notice.body, /a déjà reçu « Une lampe »/);
    assert.equal((await list().collection('claims').doc('lamp').get()).exists, false);
  });

  test('a removed list takes its « On m\'a offert » with it', async () => {
    await revealWishes(db, fakeSender().send, DAY_AFTER);
    assert.equal((await list().collection('received').get()).size, 2);
    await deleteGiftListContent(db, LIST);
    assert.equal((await list().collection('received').get()).size, 0);
  });

  test('« Merci » tells every giver once, never the beneficiary', async () => {
    await revealWishes(db, fakeSender().send, DAY_AFTER);
    const scarf = list().collection('received').doc('scarf');
    assert.deepEqual((await scarf.get()).get('giverIds'), ['jules', 'camille']);
    await db.doc('members/jules/devices/token-jules').set({});
    await db.doc('members/camille/devices/token-camille').set({});
    const before = (await scarf.get()).data();
    await scarf.update({ thanked: true });
    const after = (await scarf.get()).data();
    const { sent, send } = fakeSender();
    assert.equal(await thankGivers(db, send, LIST, before, after), 2);
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-camille', 'token-jules']);
    assert.equal(sent[0].notice.title, 'Inès te dit merci !');
    assert.equal(sent[0].notice.body, 'Merci pour « Une écharpe ».');
    // Already thanked: nothing more.
    assert.equal(await thankGivers(db, send, LIST, after, after), 0);
    assert.equal(sent.length, 2);
  });

  test('a child without the app has nothing revealed', async () => {
    await db.doc('members/ines').update({ profile: 'childWithoutApp' });
    assert.equal(await revealWishes(db, fakeSender().send, DAY_AFTER), 0);
    assert.equal((await list().collection('items').get()).size, 3);
  });
});

describe('ideas of the family reveal (récit 9 ter)', () => {
  const IDEAS = 'ideas-ines';
  const ideas = () => db.collection('giftLists').doc(IDEAS);

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('giftLists'));
    await db.recursiveDelete(db.collection('notebooks'));
    await db.recursiveDelete(db.collection('members'));
    await db.doc('config/app').set({ features: { gifts: true, singleList: true } });
    for (const m of [ines, camille, jules]) await db.doc(`members/${m.id}`).set(member(m));
    await db.doc('members/ines/devices/token-ines').set({});
    // Inès never wrote a wish: no « Mes envies » yet.
    await ideas().set({
      kind: 'ideas', title: 'Idées de la famille', occasion: null, date: null, beneficiaryId: 'ines',
      viewerIds: [], creatorId: 'camille', creatorName: 'Camille', revealed: false,
    });
    const idea = (title: string) => ({ title, url: null, price: 20, authorId: 'camille', visibleToBeneficiary: false });
    await ideas().collection('items').doc('tea').set(idea('Un thé'));
    await ideas().collection('items').doc('mug').set(idea('Une tasse'));
    await ideas().collection('claims').doc('tea').set({
      type: 'solo', holderId: 'camille', holderName: 'Camille', occasion: 'birthday', createdAt: Timestamp.fromDate(new Date('2027-01-05T10:00:00Z')),
    });
  });

  test('where a revealed item is archived', () => {
    assert.deepEqual(receivedPath('wishes-ines', 'book'), { listId: 'wishes-ines', receivedId: 'book' });
    assert.deepEqual(receivedPath('ideas-ines', 'tea'), { listId: 'wishes-ines', receivedId: 'idea-tea' });
    assert.equal(receivedPath('noel', 'tea'), null);
  });

  test('a taken idea goes to « On m\'a offert » of « Mes envies », made for it; the list of ideas stays', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await revealWishes(db, send, DAY_AFTER), 1);
    const wishes = await list().get();
    assert.equal(wishes.get('kind'), 'wishes');
    assert.equal(wishes.get('beneficiaryId'), 'ines');
    assert.deepEqual(wishes.get('viewerIds'), []);
    const tea = await list().collection('received').doc('idea-tea').get();
    assert.equal(tea.get('title'), 'Un thé');
    assert.deepEqual(tea.get('givers'), ['Camille']);
    assert.equal(tea.get('occasion'), 'birthday');
    assert.deepEqual((await ideas().collection('items').get()).docs.map((d) => d.id), ['mug']);
    assert.equal((await ideas().collection('claims').get()).size, 0);
    assert.equal((await ideas().get()).exists, true);
    assert.deepEqual(sent.map((s) => s.token), ['token-ines']);
    assert.equal(sent[0].notice.listId, LIST);
    // Its removal is a gift, not a cancelled reservation.
    assert.equal(await wasRevealed(db, IDEAS, 'tea'), true);
    assert.equal(await wasRevealed(db, IDEAS, 'mug'), false);
  });

  test('an existing « Mes envies » is kept as it is', async () => {
    await list().set({
      kind: 'wishes', title: 'Mes envies', occasion: null, date: null, beneficiaryId: 'ines',
      viewerIds: [], creatorId: 'ines', creatorName: 'Inès', revealed: false,
    });
    await list().collection('items').doc('book').set({ title: 'Un livre', authorId: 'ines', visibleToBeneficiary: true });
    assert.equal(await revealWishes(db, fakeSender().send, DAY_AFTER), 1);
    assert.equal((await list().get()).get('creatorId'), 'ines');
    assert.equal((await list().collection('items').doc('book').get()).exists, true);
  });

  test('without the single list, the ideas are not revealed', async () => {
    await db.doc('config/app').set({ features: { gifts: true, wishesReveal: true } });
    assert.equal(await revealWishes(db, fakeSender().send, DAY_AFTER), 0);
    assert.equal((await ideas().collection('claims').doc('tea').get()).exists, true);
    assert.equal((await list().get()).exists, false);
  });

  test('the notebook of the giver follows the occasion of the reservation, and keeps the gift once revealed', async () => {
    await syncNotebookEntry(db, IDEAS, 'tea', 'camille');
    const entry = db.doc(`notebooks/camille/entries/${notebookEntryId(IDEAS, 'tea')}`);
    assert.equal((await entry.get()).get('occasion'), 'birthday');
    assert.equal((await entry.get()).get('year'), 2027);
    await revealWishes(db, fakeSender().send, DAY_AFTER);
    await syncNotebookEntry(db, IDEAS, 'tea', 'camille');
    assert.equal((await entry.get()).exists, true);
  });
});
