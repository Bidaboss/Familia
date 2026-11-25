// La bascule vers les surprises: the old reservations and participations are wiped once, the
// wishes stay. Against the Firestore emulator (npm run test:functions in firebase/). Fictional family.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { RESET_DOC, resetOldReservationsOnce } from '../src/surpriseSwitch';

assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');

const app = initializeApp({ projectId: 'demo-familia' }, 'surprise-switch-test');
const db = getFirestore(app);
after(() => deleteApp(app));

describe('the switch to surprises', () => {
  beforeEach(async () => {
    await db.recursiveDelete(db.collection('giftLists'));
    await db.recursiveDelete(db.collection('migrations'));
    const batch = db.batch();
    batch.set(db.doc('giftLists/wishes-camille'), { kind: 'wishes', beneficiaryId: 'camille' });
    batch.set(db.doc('giftLists/wishes-camille/items/i1'), { title: 'Un livre' });
    batch.set(db.doc('giftLists/wishes-camille/items/i2'), { title: 'Un pull', alreadyBought: true });
    batch.set(db.doc('giftLists/wishes-camille/claims/i1'), { type: 'shared', holderId: 'ines' });
    batch.set(db.doc('giftLists/wishes-camille/claims/i1/shares/jade'), { amount: 5 });
    batch.set(db.doc('giftLists/wishes-camille/items/i2/contributions/ines'), { amount: 10 });
    batch.set(db.doc('giftLists/wishes-camille/items/i2/contributors/ines'), { firstName: 'Inès' });
    batch.set(db.doc('giftLists/wishes-camille/received/old'), { title: 'Déjà reçu' });
    batch.set(db.doc('giftLists/wishes-ines'), { kind: 'wishes', beneficiaryId: 'ines' });
    batch.set(db.doc('giftLists/wishes-ines/items/i3'), { title: 'Un vélo' });
    await batch.commit();
  });

  test('reservations, parts and participations wiped, the wishes and the archive stay', async () => {
    const result = await resetOldReservationsOnce(db);
    assert.deepEqual(result, { lists: 1, claims: 1, contributions: 1 });
    assert.equal((await db.collection('giftLists/wishes-camille/claims').get()).size, 0);
    assert.equal((await db.collection('giftLists/wishes-camille/claims/i1/shares').get()).size, 0);
    assert.equal((await db.collection('giftLists/wishes-camille/items/i2/contributions').get()).size, 0);
    assert.equal((await db.collection('giftLists/wishes-camille/items/i2/contributors').get()).size, 0);
    assert.equal((await db.collection('giftLists/wishes-camille/items').get()).size, 2);
    assert.equal((await db.doc('giftLists/wishes-camille/received/old').get()).exists, true);
    assert.equal((await db.doc(RESET_DOC).get()).get('status'), 'done');
  });

  test('only once: a reservation made after it is never wiped', async () => {
    await resetOldReservationsOnce(db);
    await db.doc('giftLists/wishes-ines/claims/i3').set({ type: 'solo', holderId: 'camille' });
    assert.equal(await resetOldReservationsOnce(db), null);
    assert.equal((await db.doc('giftLists/wishes-ines/claims/i3').get()).exists, true);
  });

  test('two runs at once: one wipes, the other does nothing', async () => {
    const results = await Promise.all([resetOldReservationsOnce(db), resetOldReservationsOnce(db)]);
    assert.equal(results.filter((r) => r !== null).length, 1);
  });
});
