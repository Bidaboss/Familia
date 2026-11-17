// The single audience (PRD, cercles étape 1): pure rules, then against the Firestore emulator.
// Fictional data only: « Famille A » and « Famille B ».
import { after, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { circlesOf, familyMembers, inAudience, MAIN_CIRCLE, withApp } from '../src/audience';

describe('the circles of a member or a content', () => {
  test('naming none, or nothing usable, is the circle « principal »', () => {
    assert.deepEqual(circlesOf(undefined), [MAIN_CIRCLE]);
    assert.deepEqual(circlesOf({}), [MAIN_CIRCLE]);
    assert.deepEqual(circlesOf({ circleIds: [] }), [MAIN_CIRCLE]);
    assert.deepEqual(circlesOf({ circleIds: 'familleA' }), [MAIN_CIRCLE]);
    assert.deepEqual(circlesOf({ circleIds: ['', 3] }), [MAIN_CIRCLE]);
    assert.deepEqual(circlesOf({ circleIds: ['familleA', 'familleB'] }), ['familleA', 'familleB']);
  });

  test('with the circles off, everyone reaches everyone, as before', () => {
    assert.equal(inAudience({ circleIds: ['familleA'] }, { circleIds: ['familleB'] }, false), true);
    assert.equal(inAudience({}, {}, false), true);
  });

  test('with them on, only a shared circle; a content of no scope is for the whole family', () => {
    assert.equal(inAudience({ circleIds: ['familleA'] }, { circleIds: ['familleB'] }, true), false);
    assert.equal(inAudience({ circleIds: ['familleA', 'familleB'] }, { circleIds: ['familleB'] }, true), true);
    assert.equal(inAudience({}, {}, true), true);
    assert.equal(inAudience({ circleIds: ['familleA'] }, {}, true), false);
    assert.equal(inAudience({ circleIds: ['familleA'] }, null, true), true);
  });
});

describe('against the emulator', () => {
  const app = initializeApp({ projectId: 'demo-familia' }, 'audience-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));

  beforeEach(async () => {
    await db.recursiveDelete(db.collection('members'));
    await db.doc('members/a1').set({ firstName: 'A1', profile: 'adult' });
    await db.doc('members/a2').set({ firstName: 'A2', profile: 'childWithoutApp' });
    await db.doc('members/b1').set({ firstName: 'B1', profile: 'adult', circleIds: ['familleB'] });
    await db.doc('config/app').set({ features: {} });
  });

  test('today, the whole family, whatever the content', async () => {
    assert.deepEqual((await familyMembers(db)).map((m) => m.id).sort(), ['a1', 'a2', 'b1']);
    assert.deepEqual((await familyMembers(db, { circleIds: ['familleB'] })).map((m) => m.id).sort(), ['a1', 'a2', 'b1']);
    assert.deepEqual(withApp(await familyMembers(db)).sort(), ['a1', 'b1']);
  });

  test('with the switch « circles » on, the members of the circles of the content', async () => {
    await db.doc('config/app').set({ features: { circles: true } });
    assert.deepEqual((await familyMembers(db, { circleIds: ['familleB'] })).map((m) => m.id), ['b1']);
    assert.deepEqual((await familyMembers(db, {})).map((m) => m.id).sort(), ['a1', 'a2']);
    assert.deepEqual((await familyMembers(db)).map((m) => m.id).sort(), ['a1', 'a2', 'b1']);
  });
});
