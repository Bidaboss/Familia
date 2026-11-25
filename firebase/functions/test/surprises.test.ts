// Cadeaux et Surprise: what the server keeps (average of the ratings, total of the parts), against the
// Firestore emulator (npm run test:functions in firebase/). Fictional family only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { Sender } from '../src/store';
import { sendBirthdayGiftReminders } from '../src/gifts';
import {
  cancelSurpriseOffered, daysUntil, onSurpriseDateChanged, onSurpriseMemberDeclined, onWishChangedForSurprises, deleteMemberSurprises, isCancelAsked, deleteProposalRatings, deleteSurpriseContent, deleteTakeShares, isNewlyOffered, notebookHolders,
  offeredGift, onSurpriseOffered, purgeOfferedSurprises, reminderKind, sendSurpriseReminders, surpriseEntryId, surpriseReminderNotice,
  syncSurpriseNotebook, updateRatingAverage, updateTakeTotal, validAmount, validStars,
} from '../src/surprises';

describe('logic', () => {
  test('the reminders: D-14, D-7, D-3, the day before, then every day from the day itself', () => {
    assert.equal(daysUntil('2026-12-25', '2026-12-11'), 14);
    assert.equal(daysUntil('2026-12-25', '2026-12-27'), -2);
    assert.equal(daysUntil('2027-03-28', '2027-03-27'), 1); // Summer time starts in between.
    assert.deepEqual([15, 14, 8, 7, 3, 2, 1, 0, -1, -30].map(reminderKind), [null, 'before', null, 'before', 'before', null, 'before', 'due', 'due', 'due']);
    assert.match(surpriseReminderNotice('s1', 'Noël de Camille', 1).title, /demain/);
    assert.match(surpriseReminderNotice('s1', 'Noël de Camille', 0).body, /C'est offert/);
    assert.equal(surpriseReminderNotice('s1', 'Noël de Camille', 7).surpriseId, 's1');
  });

  test('who has a gift in their notebook, alone or in a joint gift', () => {
    assert.deepEqual([...notebookHolders({ kind: 'solo', ownerId: 'ines' }, 30, [])], [['ines', { source: 'claim', price: 30 }]]);
    assert.deepEqual([...notebookHolders({ kind: 'shared', ownerId: 'ines' }, 30, [{ id: 'jade', amount: 10 }, { id: 'x', amount: 2.5 }])],
      [['ines', { source: 'share', price: null }], ['jade', { source: 'share', price: 10 }]]);
    assert.equal(notebookHolders(undefined, 30, []).size, 0);
  });

  test('« C\'est offert » only once, and only what can be offered', () => {
    assert.equal(isNewlyOffered({ status: 'preparing' }, { status: 'offered' }), true);
    assert.equal(isNewlyOffered({ status: 'offered' }, { status: 'offered' }), false);
    assert.equal(offeredGift('p', { kind: 'wish', removed: true }), null);
    assert.equal(offeredGift('p', { kind: 'other' }), null);
    assert.equal(offeredGift('wish-i1', { kind: 'wish', wishItemId: 'i1', title: 'Un livre' })?.wishItemId, 'i1');
    assert.equal(offeredGift('idea-e1', { kind: 'idea', authorId: 'ines', notebookEntryId: 'e1' })?.notebookEntryId, 'e1');
  });

  test('a rating counts from 1 to 5 whole stars, a part in whole euros above 0', () => {
    assert.deepEqual([0, 1, 3, 5, 6, 2.5, '4', null].map(validStars), [null, 1, 3, 5, null, null, 4, null]);
    assert.deepEqual([0, -5, 10, 12.5, '20', undefined].map(validAmount), [null, null, 10, null, 20, null]);
  });
});

assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');

const app = initializeApp({ projectId: 'demo-familia' }, 'surprises-test');
const db = getFirestore(app);
after(() => deleteApp(app));

const surprise = {
  beneficiaryId: 'camille', title: 'Noël de Camille', occasion: 'christmas', date: '2026-12-25',
  memberIds: ['ines', 'jade'], creatorId: 'ines', creatorName: 'Inès', status: 'preparing',
  createdAt: new Date(), updatedAt: new Date(),
};

function fakeSender() {
  const sent: { token: string; notice: Notice }[] = [];
  const send: Sender = async (token, notice) => {
    sent.push({ token, notice });
    return 'ok';
  };
  return { sent, send };
}

const people: Record<string, { firstName: string; profile: string; birthDate: string | null; parentIds: string[] }> = {
  camille: { firstName: 'Camille', profile: 'adult', birthDate: '1990-12-09', parentIds: [] },
  ines: { firstName: 'Inès', profile: 'adult', birthDate: '1988-05-02', parentIds: [] },
  jade: { firstName: 'Jade', profile: 'childWithApp', birthDate: '2015-07-03', parentIds: ['ines'] },
  hugo: { firstName: 'Hugo', profile: 'adult', birthDate: '1985-01-20', parentIds: [] },
};

describe('functions', () => {
  beforeEach(async () => {
    for (const c of ['surprises', 'openSurprises', 'offeredSurprises', 'config', 'members', 'notebooks', 'giftHistory', 'giftLists', 'quietQueue']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    for (const [id, m] of Object.entries(people)) {
      batch.set(db.doc(`members/${id}`), { ...m, email: null, householdId: 'h1', isAdmin: false, uid: null });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: new Date() });
    }
    batch.set(db.doc('config/app'), { features: { gifts: true } });
    batch.set(db.doc('surprises/s1'), surprise);
    batch.set(db.doc('openSurprises/s1'), { beneficiaryId: 'camille', title: 'Noël de Camille' });
    batch.set(db.doc('surprises/s1/proposals/p1'), { kind: 'wish', title: 'Un livre', removed: false });
    batch.set(db.doc('surprises/s1/takes/p1'), { kind: 'shared', ownerId: 'ines', boughtBy: null, goal: 60 });
    await batch.commit();
  });

  test('the average of a proposal: sum and count, the wrong ratings left out', async () => {
    const ratings = db.collection('surprises/s1/proposals/p1/ratings');
    await ratings.doc('ines').set({ stars: 4 });
    await ratings.doc('jade').set({ stars: 5 });
    await ratings.doc('pirate').set({ stars: 9 });
    await updateRatingAverage(db, 's1', 'p1');
    let p = await db.doc('surprises/s1/proposals/p1').get();
    assert.equal(p.get('ratingSum'), null); // 2 ratings: the sum would tell them.
    assert.equal(p.get('ratingCount'), 2);
    await ratings.doc('hugo').set({ stars: 3 });
    await updateRatingAverage(db, 's1', 'p1');
    p = await db.doc('surprises/s1/proposals/p1').get();
    assert.equal(p.get('ratingSum'), 12);
    assert.equal(p.get('ratingCount'), 3);
    await ratings.doc('jade').set({ stars: 2 }); // Changed her mind.
    await updateRatingAverage(db, 's1', 'p1');
    p = await db.doc('surprises/s1/proposals/p1').get();
    assert.equal(p.get('ratingSum'), 9);
    await ratings.doc('ines').delete();
    await updateRatingAverage(db, 's1', 'p1');
    p = await db.doc('surprises/s1/proposals/p1').get();
    assert.equal(p.get('ratingSum'), null);
    assert.equal(p.get('ratingCount'), 2);
    await updateRatingAverage(db, 's1', 'missing'); // A removed proposal: nothing to keep.
    assert.equal((await db.doc('surprises/s1/proposals/missing').get()).exists, false);
  });

  test('ratings in the same second: the count has them all', async () => {
    const ratings = db.collection('surprises/s1/proposals/p1/ratings');
    const given: Record<string, number> = { a: 1, b: 2, c: 3, d: 4, e: 5 };
    await Promise.all(Object.entries(given).map(async ([id, stars]) => {
      await ratings.doc(id).set({ stars });
      await updateRatingAverage(db, 's1', 'p1');
    }));
    await updateRatingAverage(db, 's1', 'p1');
    const p = await db.doc('surprises/s1/proposals/p1').get();
    assert.equal(p.get('ratingSum'), 15);
    assert.equal(p.get('ratingCount'), 5);
  });

  test('the total of a joint gift, in whole euros, over the goal allowed', async () => {
    const shares = db.collection('surprises/s1/takes/p1/shares');
    await shares.doc('ines').set({ amount: 40 });
    await shares.doc('jade').set({ amount: 30 });
    await shares.doc('odd').set({ amount: 12.5 });
    await updateTakeTotal(db, 's1', 'p1');
    let t = await db.doc('surprises/s1/takes/p1').get();
    assert.equal(t.get('sharesTotal'), 70); // Above the goal of 60: kept, the app warns.
    assert.equal(t.get('sharesCount'), 2);
    await shares.doc('jade').delete();
    await updateTakeTotal(db, 's1', 'p1');
    t = await db.doc('surprises/s1/takes/p1').get();
    assert.equal(t.get('sharesTotal'), 40);
    assert.equal(t.get('sharesCount'), 1);
    await updateTakeTotal(db, 's1', 'missing');
    assert.equal((await db.doc('surprises/s1/takes/missing').get()).exists, false);
  });

  test('parts in the same second: the total counts them all', async () => {
    const shares = db.collection('surprises/s1/takes/p1/shares');
    const amounts: Record<string, number> = { p1: 10, p2: 20, p3: 30, p4: 40 };
    await Promise.all(Object.entries(amounts).map(async ([id, amount]) => {
      await shares.doc(id).set({ amount });
      await updateTakeTotal(db, 's1', 'p1');
    }));
    await updateTakeTotal(db, 's1', 'p1');
    const t = await db.doc('surprises/s1/takes/p1').get();
    assert.equal(t.get('sharesTotal'), 100);
    assert.equal(t.get('sharesCount'), 4);
  });

  test('a removed proposal, a released gift, a removed surprise leave nothing behind', async () => {
    await db.doc('surprises/s1/proposals/p1/ratings/ines').set({ stars: 4 });
    await db.doc('surprises/s1/takes/p1/shares/ines').set({ amount: 20 });
    await db.doc('surprises/s1/members/ines').set({ validated: false });
    await deleteProposalRatings(db, 's1', 'p1');
    assert.equal((await db.collection('surprises/s1/proposals/p1/ratings').get()).size, 0);
    await deleteTakeShares(db, 's1', 'p1');
    assert.equal((await db.collection('surprises/s1/takes/p1/shares').get()).size, 0);
    await db.doc('surprises/s1/proposals/p2/ratings/jade').set({ stars: 3 });
    await db.doc('surprises/s1').delete();
    await deleteSurpriseContent(db, 's1');
    for (const c of ['members', 'proposals', 'takes']) {
      assert.equal((await db.collection(`surprises/s1/${c}`).get()).size, 0, c);
    }
    assert.equal((await db.collection('surprises/s1/proposals/p2/ratings').get()).size, 0);
    assert.equal((await db.doc('openSurprises/s1').get()).exists, false);
  });

  test('a gift taken alone, then shared, then released: the notebooks follow (récit 9 bis)', async () => {
    await db.doc('giftLists/wishes-camille/items/i1').set({ title: 'Un livre rouge', url: null, price: 25 });
    await db.doc('surprises/s1/proposals/wish-i1').set({ kind: 'wish', wishItemId: 'i1', title: 'Un livre', removed: false });
    await db.doc('surprises/s1/takes/wish-i1').set({ kind: 'solo', ownerId: 'ines', boughtBy: null, goal: null });
    await syncSurpriseNotebook(db, 's1', 'wish-i1');
    const entry = (who: string) => db.doc(`notebooks/${who}/entries/${surpriseEntryId('s1', 'wish-i1')}`).get();
    let e = await entry('ines');
    assert.equal(e.get('source'), 'claim');
    assert.equal(e.get('title'), 'Un livre rouge'); // The live wish, not the copy.
    assert.equal(e.get('price'), 25);
    assert.equal(e.get('personId'), 'camille');
    assert.equal(e.get('year'), 2026);

    await db.doc('surprises/s1/takes/wish-i1').set({ kind: 'shared', ownerId: 'ines', boughtBy: null, goal: 60 });
    await db.doc('surprises/s1/takes/wish-i1/shares/jade').set({ amount: 15 });
    await syncSurpriseNotebook(db, 's1', 'wish-i1');
    assert.equal((await entry('ines')).get('source'), 'share');
    assert.equal((await entry('ines')).get('price'), null);
    assert.equal((await entry('jade')).get('price'), 15);

    await db.doc('surprises/s1/takes/wish-i1/shares/jade').delete();
    await syncSurpriseNotebook(db, 's1', 'wish-i1');
    assert.equal((await entry('jade')).exists, false);
    await db.recursiveDelete(db.doc('surprises/s1/takes/wish-i1'));
    await syncSurpriseNotebook(db, 's1', 'wish-i1');
    assert.equal((await entry('ines')).exists, false);
    assert.equal((await db.doc('surprises/s1/notebook/wish-i1').get()).exists, false);
  });

  test('« C\'est offert »: history, wish and idea out of their lists, notebooks « Offert », the others told', async () => {
    const { sent, send } = fakeSender();
    await db.recursiveDelete(db.doc('surprises/s1/takes/p1'));
    await db.doc('surprises/s1').update({ memberIds: ['ines', 'jade', 'hugo'] });
    await db.doc('surprises/s1/members/hugo').set({ declined: true });
    await db.doc('giftLists/wishes-camille/items/i1').set({ title: 'Un livre', url: null, price: 25, photo: 'giftLists/wishes-camille/i1' });
    await db.doc('notebooks/jade/entries/e1').set({ source: 'idea', title: 'Un pull', personId: 'camille', status: 'idea' });
    await db.doc('surprises/s1/proposals/wish-i1').set({ kind: 'wish', wishItemId: 'i1', title: 'Un livre', removed: false });
    await db.doc('surprises/s1/proposals/idea-e1').set({ kind: 'idea', authorId: 'jade', notebookEntryId: 'e1', title: 'Un pull', removed: false });
    await db.doc('surprises/s1/proposals/wish-i2').set({ kind: 'wish', wishItemId: 'i2', title: 'Pas pris', removed: false });
    await db.doc('surprises/s1/takes/wish-i1').set({ kind: 'solo', ownerId: 'ines', boughtBy: 'ines', goal: null });
    await db.doc('surprises/s1/takes/idea-e1').set({ kind: 'solo', ownerId: 'jade', boughtBy: null, goal: null });
    await syncSurpriseNotebook(db, 's1', 'wish-i1');

    assert.equal(await onSurpriseOffered(db, send, 's1'), 0); // Not offered yet: nothing.
    await db.doc('surprises/s1').update({ status: 'offered', offeredAt: new Date(), offeredBy: 'ines' });
    assert.equal(await onSurpriseOffered(db, send, 's1'), 2);
    assert.equal(await onSurpriseOffered(db, send, 's1'), 0); // A retry: nothing twice.
    assert.equal((await db.doc('openSurprises/s1').get()).exists, false);

    const history = await db.collection('giftHistory/camille/items').get();
    assert.deepEqual(history.docs.map((d) => d.get('title')).sort(), ['Un livre', 'Un pull']);
    assert.equal(history.docs[0].get('occasion'), 'christmas');
    assert.equal((await db.doc('giftLists/wishes-camille/items/i1').get()).exists, false);
    assert.equal((await db.doc('notebooks/jade/entries/e1').get()).exists, false);
    const copy = await db.doc('surprises/s1/offered/wish-i1').get();
    assert.equal(copy.get('path'), 'giftLists/wishes-camille/items/i1');
    assert.equal(copy.get('data').photo, 'giftLists/wishes-camille/i1');
    assert.equal((await db.doc(`notebooks/ines/entries/${surpriseEntryId('s1', 'wish-i1')}`).get()).get('status'), 'given');
    // Jade only: not Inès who confirmed, not Hugo who declined, never Camille.
    assert.deepEqual(sent.map((s) => s.token), ['token-jade']);
    assert.match(sent[0].notice.body, /Inès a confirmé : les cadeaux de Camille sont offerts/);
  });

  test('« Annuler C\'est offert » by the admin: everything back as it was, once, the members told', async () => {
    const { sent, send } = fakeSender();
    await db.doc('config/family').set({ ownerMemberId: 'hugo' });
    await db.recursiveDelete(db.doc('surprises/s1/takes/p1'));
    await db.doc('surprises/s1').update({ memberIds: ['ines', 'jade', 'hugo'] });
    await db.doc('giftLists/wishes-camille/items/i1').set({ title: 'Un livre', url: null, price: 25, photo: 'giftLists/wishes-camille/i1' });
    await db.doc('notebooks/jade/entries/e1').set({ source: 'idea', title: 'Un pull', personId: 'camille', status: 'idea' });
    await db.doc('surprises/s1/proposals/wish-i1').set({ kind: 'wish', wishItemId: 'i1', title: 'Un livre', removed: false });
    await db.doc('surprises/s1/proposals/idea-e1').set({ kind: 'idea', authorId: 'jade', notebookEntryId: 'e1', title: 'Un pull', removed: false });
    await db.doc('surprises/s1/takes/wish-i1').set({ kind: 'solo', ownerId: 'ines', boughtBy: 'ines', goal: null });
    await db.doc('surprises/s1/takes/idea-e1').set({ kind: 'solo', ownerId: 'jade', boughtBy: null, goal: null });
    await syncSurpriseNotebook(db, 's1', 'wish-i1');
    const ines = db.doc(`notebooks/ines/entries/${surpriseEntryId('s1', 'wish-i1')}`);
    await ines.update({ status: 'wrapped' });
    await db.doc('giftHistory/camille/items/old').set({ title: 'Un vieux cadeau', surpriseId: 'other' });

    assert.equal(await cancelSurpriseOffered(db, send, 's1'), false); // Not offered: nothing.
    await db.doc('surprises/s1').update({ status: 'offered', offeredAt: new Date(), offeredBy: 'ines' });
    await onSurpriseOffered(db, send, 's1');
    const listed = await db.doc('offeredSurprises/s1').get();
    assert.deepEqual([listed.get('title'), listed.get('beneficiaryId'), listed.get('beneficiaryName'), listed.get('cancelAsked')], ['Noël de Camille', 'camille', 'Camille', false]);
    assert.equal(isCancelAsked({ cancelAsked: false }, { cancelAsked: true }), true);
    assert.equal(isCancelAsked({ cancelAsked: true }, { cancelAsked: true }), false);
    sent.length = 0;

    assert.equal(await cancelSurpriseOffered(db, send, 's1'), true);
    assert.equal(await cancelSurpriseOffered(db, send, 's1'), false); // A retry: nothing twice.
    const s = await db.doc('surprises/s1').get();
    assert.equal(s.get('status'), 'preparing');
    assert.equal(s.get('offeredAt'), undefined);
    assert.equal(s.get('offeredHandledAt'), undefined);
    assert.equal((await db.doc('giftLists/wishes-camille/items/i1').get()).get('photo'), 'giftLists/wishes-camille/i1');
    assert.equal((await db.doc('notebooks/jade/entries/e1').get()).get('title'), 'Un pull');
    assert.equal((await db.collection('surprises/s1/offered').get()).size, 0);
    assert.deepEqual((await db.collection('giftHistory/camille/items').get()).docs.map((d) => d.id), ['old']);
    assert.equal((await ines.get()).get('status'), 'wrapped');
    assert.equal((await db.doc('openSurprises/s1').get()).get('title'), 'Noël de Camille');
    assert.equal((await db.doc('offeredSurprises/s1').get()).exists, false);
    // Inès and Jade; not Hugo, the admin who cancelled, never Camille.
    assert.deepEqual(sent.map((x) => x.token).sort(), ['token-ines', 'token-jade']);
    assert.match(sent[0].notice.title, /à confirmer de nouveau/);

    // A run cut after the surprise went back: the next one finishes the steps, nobody told twice.
    await db.doc('offeredSurprises/s1').set({ title: 'Noël de Camille', beneficiaryId: 'camille', cancelAsked: true });
    await db.doc('surprises/s1/offered/wish-i9').set({ path: 'giftLists/wishes-camille/items/i9', data: { title: 'Un disque' } });
    sent.length = 0;
    assert.equal(await cancelSurpriseOffered(db, send, 's1'), true);
    assert.equal((await db.doc('giftLists/wishes-camille/items/i9').get()).get('title'), 'Un disque');
    assert.equal((await db.doc('offeredSurprises/s1').get()).exists, false);
    assert.equal(sent.length, 0);

    // Offered again later: handled again, from scratch.
    await db.doc('surprises/s1').update({ status: 'offered', offeredAt: new Date(), offeredBy: 'jade' });
    assert.equal(await onSurpriseOffered(db, send, 's1'), 2);
    assert.equal((await db.doc('giftLists/wishes-camille/items/i1').get()).exists, false);
  });

  test('the other news: a member who withdraws, a new date, a changed wish', async () => {
    const { sent, send } = fakeSender();
    await db.doc('surprises/s1').update({ memberIds: ['ines', 'jade', 'hugo'] });
    // « Je ne participe pas » by Jade: Inès and Hugo learn it, once.
    assert.equal(await onSurpriseMemberDeclined(db, send, 's1', 'jade', { validated: false }, { declined: true }), 2);
    assert.deepEqual(sent.map((x) => x.token).sort(), ['token-hugo', 'token-ines']);
    assert.match(sent[0].notice.body, /Jade ne participe pas/);
    assert.equal(await onSurpriseMemberDeclined(db, send, 's1', 'jade', { declined: true }, { declined: true }), 0);
    await db.doc('surprises/s1/members/jade').set({ declined: true });
    sent.length = 0;

    // The creator moves the date: the others still in it, not Jade who declined.
    const before = (await db.doc('surprises/s1').get()).data();
    assert.equal(await onSurpriseDateChanged(db, send, 's1', before, { ...before, date: '2026-12-24' }), 1);
    assert.deepEqual(sent.map((x) => x.token), ['token-hugo']);
    assert.match(sent[0].notice.body, /24\/12\/2026/);
    assert.equal(await onSurpriseDateChanged(db, send, 's1', before, { ...before, title: 'Autre' }), 0);
    sent.length = 0;

    // Camille changes a wish proposed in the surprise: the proposal follows, the members learn it.
    await db.doc('surprises/s1/proposals/wish-i1').set({ kind: 'wish', wishItemId: 'i1', title: 'Un livre', price: 20, url: null, removed: false });
    const wish = { title: 'Un livre', price: 20, url: null };
    assert.equal(await onWishChangedForSurprises(db, send, 'wishes-camille', 'i1', wish, { ...wish, price: 20 }), 0);
    assert.equal(await onWishChangedForSurprises(db, send, 'wishes-camille', 'i2', wish, { ...wish, title: 'Pas proposé' }), 0);
    assert.equal(await onWishChangedForSurprises(db, send, 'wishes-camille', 'i1', wish, { ...wish, title: 'Un grand livre', price: 30 }), 2);
    const p = await db.doc('surprises/s1/proposals/wish-i1').get();
    assert.deepEqual([p.get('title'), p.get('price')], ['Un grand livre', 30]);
    assert.match(sent[0].notice.body, /Une envie a changé : « Un grand livre »/);
    assert.equal(sent[0].notice.tag, 'surprise-s1-wish-i1');
    // A second edit within 10 minutes: the proposal follows, nobody is rung again.
    sent.length = 0;
    const t0 = new Date();
    assert.equal(await onWishChangedForSurprises(db, send, 'wishes-camille', 'i1', wish, { ...wish, title: 'Un très grand livre' }, new Date(t0.getTime() + 60_000)), 0);
    assert.equal((await db.doc('surprises/s1/proposals/wish-i1').get()).get('title'), 'Un très grand livre');
    assert.equal(await onWishChangedForSurprises(db, send, 'wishes-camille', 'i1', wish, { ...wish, title: 'Un autre livre' }, new Date(t0.getTime() + 11 * 60_000)), 2);
    assert.ok(!sent.some((x) => x.token === 'token-camille'));
  });

  test('the reminders: to who has not validated before the day, to all from the day, muted never, beneficiary never', async () => {
    const { sent, send } = fakeSender();
    await db.doc('surprises/s1').update({ memberIds: ['ines', 'jade', 'hugo', 'camille'] });
    await db.doc('surprises/s1/members/ines').set({ validated: true });
    await db.doc('surprises/s1/members/hugo').set({ muted: true });
    let told = await sendSurpriseReminders(db, send, new Date('2026-12-10T09:00:00Z')); // D-15.
    assert.equal(sent.length, 0);
    told = await sendSurpriseReminders(db, send, new Date('2026-12-18T09:00:00Z')); // D-7.
    assert.deepEqual(sent.map((s) => s.token), ['token-jade']);
    assert.deepEqual([...told], ['jade']);
    sent.length = 0;
    await sendSurpriseReminders(db, send, new Date('2026-12-26T09:00:00Z')); // The day after: still to confirm.
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-ines', 'token-jade']);
    sent.length = 0;
    await db.doc('surprises/s1').update({ status: 'offered', offeredAt: new Date(), offeredBy: 'ines' });
    await sendSurpriseReminders(db, send, new Date('2026-12-27T09:00:00Z'));
    assert.equal(sent.length, 0);
  });

  test('one notification a day: a surprise reminder holds back the birthday one', async () => {
    const { sent, send } = fakeSender();
    // Camille's birthday on 9 December: D-14 on 25 November, nothing reserved in the old lists.
    await db.doc('surprises/s1').update({ date: '2026-12-09', occasion: 'birthday' });
    const told = await sendSurpriseReminders(db, send, new Date('2026-11-25T09:00:00Z'));
    assert.deepEqual([...told].sort(), ['ines', 'jade']);
    sent.length = 0;
    await sendBirthdayGiftReminders(db, send, new Date('2026-11-25T09:00:00Z'), told);
    // Hugo, not in the surprise, still gets the birthday reminder; Inès does not get a second one.
    assert.deepEqual(sent.map((s) => s.token), ['token-hugo']);
  });

  test('the purge: 30 days after « C\'est offert », the history stays', async () => {
    await db.doc('surprises/s1').update({ status: 'offered', offeredAt: new Date('2026-12-25T12:00:00Z'), offeredBy: 'ines' });
    await db.doc('surprises/s1/offered/wish-i1').set({ path: 'giftLists/wishes-camille/items/i1', data: { photo: 'giftLists/wishes-camille/i1' } });
    await db.doc('giftHistory/camille/items/s1_wish-i1').set({ title: 'Un livre' });
    const removed: string[] = [];
    const remove = async (path: string) => { removed.push(path); };
    assert.equal(await purgeOfferedSurprises(db, new Date('2027-01-20T12:00:00Z'), remove), 0);
    assert.equal(await purgeOfferedSurprises(db, new Date('2027-01-25T13:00:00Z'), remove), 1);
    assert.equal((await db.doc('surprises/s1').get()).exists, false);
    assert.equal((await db.doc('openSurprises/s1').get()).exists, false);
    assert.equal((await db.doc('surprises/s1/proposals/p1').get()).exists, false);
    assert.deepEqual(removed, ['giftLists/wishes-camille/i1']);
    assert.equal((await db.doc('giftHistory/camille/items/s1_wish-i1').get()).exists, true);
  });

  test('a removed member: the surprises for them go, they leave the others with their rating and part', async () => {
    await db.doc('surprises/s2').set({ ...surprise, beneficiaryId: 'ines', memberIds: ['camille'] });
    await db.doc('giftHistory/ines/items/x').set({ title: 'Un vieux cadeau' });
    await db.doc('surprises/s1/members/jade').set({ validated: true });
    await db.doc('surprises/s1/proposals/p1/ratings/jade').set({ stars: 5 });
    await db.doc('surprises/s1/proposals/p1/ratings/ines').set({ stars: 3 });
    await db.doc('surprises/s1/takes/p1/shares/jade').set({ amount: 10 });
    await db.doc('surprises/s1/takes/p1/shares/camille').set({ amount: 20 });
    await db.doc('surprises/s1/takes/p2').set({ kind: 'solo', ownerId: 'jade', boughtBy: null, goal: null });
    await deleteMemberSurprises(db, 'jade');
    const s1 = await db.doc('surprises/s1').get();
    assert.deepEqual(s1.get('memberIds'), ['ines']);
    assert.equal((await db.doc('surprises/s1/members/jade').get()).exists, false);
    assert.equal((await db.doc('surprises/s1/proposals/p1').get()).get('ratingCount'), 1);
    assert.equal((await db.doc('surprises/s1/takes/p1').get()).get('sharesTotal'), 20);
    assert.equal((await db.doc('surprises/s1/takes/p2').get()).exists, false);
    await db.doc('openSurprises/s2').set({ beneficiaryId: 'ines' });
    await deleteMemberSurprises(db, 'ines');
    assert.equal((await db.doc('surprises/s2').get()).exists, false);
    assert.equal((await db.doc('openSurprises/s2').get()).exists, false);
    assert.equal((await db.collection('giftHistory/ines/items').get()).size, 0);
  });
});
