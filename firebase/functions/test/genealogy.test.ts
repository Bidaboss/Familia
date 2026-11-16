// Arbre généalogique (PRD, récit 40): someone who leaves the tree takes their links with them,
// against the Firestore emulator (npm run test:functions in firebase/). Fictional family only.
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { deleteLinksOf, removeMemberFromTree } from '../src/genealogy';

describe('the family tree, against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'genealogy-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('kinLinks'));
    await db.recursiveDelete(db.collection('kinWords'));
    const batch = db.batch();
    const link = (kind: string, aId: string, bId: string) =>
      batch.set(db.doc(`kinLinks/${kind}_${aId}_${bId}`), { kind, aId, bId, updatedBy: 'camille', updatedAt: new Date(0) });
    link('parent', 'jeanne', 'camille');
    link('parent', 'camille', 'noe');
    link('partner', 'camille', 'ines');
    link('parent', 'ines', 'noe');
    batch.set(db.doc('kinWords/camille'), { words: { jeanne: 'Mamie' }, updatedAt: new Date(0) });
    batch.set(db.doc('kinWords/noe'), { words: { ines: 'bonus-maman' }, updatedAt: new Date(0) });
    await batch.commit();
  });

  const remaining = async () => (await db.collection('kinLinks').get()).docs.map((d) => d.id).sort();

  test('a relative removed from the tree takes their links with them', async () => {
    assert.equal(await deleteLinksOf(db, 'jeanne'), 1);
    assert.deepEqual(await remaining(), ['parent_camille_noe', 'parent_ines_noe', 'partner_camille_ines']);
  });

  test('a member who leaves: their links as parent, child or partner, and their own words', async () => {
    await removeMemberFromTree(db, 'camille');
    assert.deepEqual(await remaining(), ['parent_ines_noe']);
    assert.equal((await db.doc('kinWords/camille').get()).exists, false);
    assert.equal((await db.doc('kinWords/noe').get()).exists, true);
  });
});
