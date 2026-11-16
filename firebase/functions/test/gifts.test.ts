// Listes et cadeaux: logic, and the functions against the Firestore emulator (npm run test:functions
// in firebase/). Fictional family only: no real names or dates.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { Sender } from '../src/store';
import {
  birthdayRecipients, birthdaysIn, deleteClaimShares, deleteGiftListContent, deleteMemberGiftLists, GiftMember, isDueForReveal,
  nextBirthday, onGiftItemChanged, revealGiftLists, sendBirthdayGiftReminders, updateSharesTotal,
} from '../src/gifts';

const members: GiftMember[] = [
  { id: 'camille', firstName: 'Camille', profile: 'adult', parentIds: [], birthDate: '1990-12-09' },
  { id: 'ines', firstName: 'Inès', profile: 'adult', parentIds: [], birthDate: '1988-02-29' },
  { id: 'jade', firstName: 'Jade', profile: 'childWithApp', parentIds: ['ines'], birthDate: '2015-12-03' },
  { id: 'noe', firstName: 'Noé', profile: 'childWithoutApp', parentIds: ['camille'], birthDate: null },
];

describe('logic', () => {
  test('the next birthday, including 29 February', () => {
    assert.equal(nextBirthday('1990-12-09', '2026-11-18'), '2026-12-09');
    assert.equal(nextBirthday('1990-03-01', '2026-11-18'), '2027-03-01');
    assert.equal(nextBirthday('1988-02-29', '2026-11-18'), '2027-02-28');
    assert.equal(nextBirthday('1988-02-29', '2027-11-18'), '2028-02-29');
  });

  test('D-21 finds the right members, and the adults but the birthday member are told', () => {
    assert.deepEqual(birthdaysIn(members, '2026-11-18', 21).map((m) => m.id), ['camille']);
    assert.deepEqual(birthdaysIn(members, '2026-11-12', 21).map((m) => m.id), ['jade']);
    assert.deepEqual(birthdayRecipients(members, 'camille'), ['ines']);
    // A child's birthday: their adults are told, the child is not.
    assert.deepEqual(birthdayRecipients(members, 'jade'), ['camille', 'ines']);
  });

  test('a list is revealed the day after its occasion, never « Mes envies »', () => {
    assert.equal(isDueForReveal({ kind: 'own', date: '2026-12-25', revealed: false }, '2026-12-25'), false);
    assert.equal(isDueForReveal({ kind: 'own', date: '2026-12-25', revealed: false }, '2026-12-26'), true);
    assert.equal(isDueForReveal({ kind: 'surprise', date: '2026-12-25', revealed: true }, '2026-12-27'), false);
    assert.equal(isDueForReveal({ kind: 'wishes', date: null, revealed: false }, '2026-12-27'), false);
  });
});

assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');

const app = initializeApp({ projectId: 'demo-familia' }, 'gifts-test');
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

function list(beneficiaryId: string, date: string | null, kind = 'own') {
  return {
    kind, title: `Liste de ${beneficiaryId}`, occasion: date ? 'christmas' : null, date, beneficiaryId,
    viewerIds: [beneficiaryId, 'ines'], creatorId: beneficiaryId, creatorName: beneficiaryId, revealed: false,
    createdAt: new Date(), updatedAt: new Date(),
  };
}

const item = {
  title: 'Un livre', url: null, price: 15, note: null, authorId: 'camille', authorName: 'Camille',
  visibleToBeneficiary: true, createdAt: new Date(), updatedAt: new Date(),
};

describe('functions', () => {
  beforeEach(async () => {
    await db.recursiveDelete(db.collection('members'));
    await db.recursiveDelete(db.collection('giftLists'));
    const batch = db.batch();
    for (const m of members) {
      batch.set(db.doc(`members/${m.id}`), member(m));
      batch.set(db.doc(`members/${m.id}/devices/token-${m.id}`), { updatedAt: new Date() });
    }
    batch.set(db.doc('giftLists/noel'), list('camille', '2026-12-25'));
    batch.set(db.doc('giftLists/noel/items/i1'), item);
    batch.set(db.doc('giftLists/noel/claims/i1'), { type: 'shared', holderId: 'ines', holderName: 'Inès', createdAt: new Date() });
    batch.set(db.doc('giftLists/noel/claims/i1/shares/jade'), { amount: 5, memberName: 'Jade', received: false, updatedAt: new Date() });
    batch.set(db.doc('giftLists/wishes-camille'), list('camille', null, 'wishes'));
    batch.set(db.doc('config/app'), { features: { gifts: true } });
    await batch.commit();
  });

  test('the morning after the occasion the list is revealed and its beneficiary told', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await revealGiftLists(db, send, new Date('2026-12-25T08:00:00Z')), 0);
    assert.equal(await revealGiftLists(db, send, new Date('2026-12-26T06:00:00Z')), 1);
    assert.equal((await db.doc('giftLists/noel').get()).get('revealed'), true);
    assert.equal((await db.doc('giftLists/wishes-camille').get()).get('revealed'), false);
    assert.deepEqual(sent.map((s) => s.token), ['token-camille']);
    assert.equal(sent[0].notice.listId, 'noel');
  });

  test('D-21: every adult but the birthday member; D-14 only when nothing is reserved', async () => {
    const { sent, send } = fakeSender();
    await sendBirthdayGiftReminders(db, send, new Date('2026-11-18T09:00:00Z')); // Camille, 9 December.
    assert.deepEqual(sent.map((s) => s.token), ['token-ines']);
    assert.equal(sent[0].notice.listId, 'wishes-camille');

    sent.length = 0;
    await sendBirthdayGiftReminders(db, send, new Date('2026-11-25T09:00:00Z')); // D-14, the book is reserved.
    assert.equal(sent.length, 0);
    await db.recursiveDelete(db.doc('giftLists/noel/claims/i1'));
    await sendBirthdayGiftReminders(db, send, new Date('2026-11-25T09:00:00Z'));
    assert.deepEqual(sent.map((s) => s.token), ['token-ines']);
    assert.match(sent[0].notice.title, /2 semaines/);
  });

  test('with the single list, the reminder speaks of an idea, never of a surprise list (récit 9 ter)', async () => {
    const { sent, send } = fakeSender();
    await sendBirthdayGiftReminders(db, send, new Date('2026-11-18T09:00:00Z'));
    assert.match(sent[0].notice.body, /liste surprise/);
    sent.length = 0;
    await db.doc('config/app').set({ features: { gifts: true, singleList: true } });
    await sendBirthdayGiftReminders(db, send, new Date('2026-11-18T09:00:00Z'));
    assert.equal(sent[0].notice.body.includes('surprise'), false);
    assert.match(sent[0].notice.body, /ajoute une idée pour Camille/);
  });

  test('a list of ideas has no date and is never revealed as a list', () => {
    assert.equal(isDueForReveal({ kind: 'ideas', date: null, revealed: false }, '2026-12-26'), false);
    assert.equal(isDueForReveal({ kind: 'ideas', date: '2026-12-25', revealed: false }, '2026-12-26'), false);
  });

  test('while the gifts module is switched off, only the admin principal is notified', async () => {
    const { sent, send } = fakeSender();
    await db.doc('config/app').set({ features: { gifts: false } });
    await db.doc('config/family').delete();
    assert.equal(await sendBirthdayGiftReminders(db, send, new Date('2026-11-18T09:00:00Z')), 0);
    assert.equal(await revealGiftLists(db, send, new Date('2026-12-26T06:00:00Z')), 1); // Still revealed.
    assert.equal(sent.length, 0);

    await db.doc('config/app').delete();
    await db.doc('config/family').set({ ownerMemberId: 'ines' });
    await sendBirthdayGiftReminders(db, send, new Date('2026-11-18T09:00:00Z'));
    assert.deepEqual(sent.map((s) => s.token), ['token-ines']);
    await db.doc('config/family').delete();
  });

  test('the reservation keeps the total and the number of parts for the participants', async () => {
    await db.doc('giftLists/noel/claims/i1/shares/ines').set({ amount: 12.5, memberName: 'Inès', received: false, updatedAt: new Date() });
    await updateSharesTotal(db, 'noel', 'i1');
    let claim = await db.doc('giftLists/noel/claims/i1').get();
    assert.equal(claim.get('sharesTotal'), 17.5);
    assert.equal(claim.get('sharesCount'), 2);
    await db.doc('giftLists/noel/claims/i1/shares/jade').delete();
    await updateSharesTotal(db, 'noel', 'i1');
    claim = await db.doc('giftLists/noel/claims/i1').get();
    assert.equal(claim.get('sharesTotal'), 12.5);
    assert.equal(claim.get('sharesCount'), 1);
    await updateSharesTotal(db, 'noel', 'missing'); // A released gift: nothing to keep.
    assert.equal((await db.doc('giftLists/noel/claims/missing').get()).exists, false);
  });

  test('the beneficiary removes a reserved item: the reservers are warned, the reservation goes', async () => {
    const { sent, send } = fakeSender();
    await db.doc('giftLists/noel/items/i1').delete();
    await onGiftItemChanged(db, send, 'noel', 'i1', item, undefined);
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-ines', 'token-jade']);
    assert.match(sent[0].notice.body, /Camille a retiré « Un livre »/);
    assert.equal((await db.doc('giftLists/noel/claims/i1').get()).exists, false);
    assert.equal((await db.doc('giftLists/noel/claims/i1/shares/jade').get()).exists, false);
  });

  test('an edit warns the reservers only when something visible changed', async () => {
    const { sent, send } = fakeSender();
    await onGiftItemChanged(db, send, 'noel', 'i1', item, { ...item });
    assert.equal(sent.length, 0);
    await onGiftItemChanged(db, send, 'noel', 'i1', item, { ...item, note: 'En bleu' });
    assert.equal(sent.length, 2);
    assert.equal(sent.some((s) => s.token === 'token-camille'), false); // Never the beneficiary.
  });

  test('a deleted list, a released joint gift and a removed member leave nothing behind', async () => {
    await deleteClaimShares(db, 'noel', 'i1');
    assert.equal((await db.doc('giftLists/noel/claims/i1/shares/jade').get()).exists, false);
    await deleteGiftListContent(db, 'noel');
    assert.equal((await db.collection('giftLists/noel/items').get()).size, 0);
    assert.equal((await db.collection('giftLists/noel/claims').get()).size, 0);
    await deleteMemberGiftLists(db, 'camille');
    assert.equal((await db.doc('giftLists/wishes-camille').get()).exists, false);
  });
});
