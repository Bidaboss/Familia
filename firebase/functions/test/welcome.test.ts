import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { Sender } from '../src/store';
import { isTestMember } from '../src/testHousehold';
import { deleteWelcomeOf, isFirstSignIn, sendQueuedWelcomes, welcomeGroupNotice, welcomeMember, welcomeNotice } from '../src/welcome';

describe('la bienvenue, without the emulator', () => {
  test('only the first link of a Google account counts', () => {
    assert.equal(isFirstSignIn({ uid: null }, { uid: 'u1' }), true);
    assert.equal(isFirstSignIn({}, { uid: 'u1' }), true);
    assert.equal(isFirstSignIn({ uid: 'u1' }, { uid: 'u1' }), false);
    assert.equal(isFirstSignIn({ uid: 'u1' }, { uid: 'u2' }), false);
    assert.equal(isFirstSignIn({ uid: null }, { uid: '' }), false);
  });

  test('it names the newcomer and opens their page', () => {
    const n = welcomeNotice('Noé', 'noe');
    assert.equal(n.title, 'Noé vient d\'arriver');
    assert.equal(n.body, 'Dis-lui bonjour !');
    assert.equal(n.channel, 'welcome');
    assert.equal(n.welcomeOf, 'noe');
  });
});

describe('la bienvenue against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'welcome-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const now = new Date('2026-11-25T10:00:00Z');

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    for (const c of ['members', 'households', 'welcomes', 'welcomeQueue', 'presentationQueue']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    batch.set(db.doc('config/app'), { features: { welcome: true } });
    batch.set(db.doc('households/h1'), { name: 'Foyer 1' });
    batch.set(db.doc('households/hTest'), { name: 'Foyer de test', isTest: true });
    const people: [string, string, string, string | null][] = [
      ['camille', 'Camille', 'adult', 'h1'], ['ines', 'Inès', 'adult', 'h1'], ['lou', 'Lou', 'childWithoutApp', 'h1'],
      ['noe', 'Noé', 'adult', 'h1'], ['essai', 'Essai', 'adult', 'hTest'], ['sansfoyer', 'Sam', 'adult', null],
    ];
    for (const [id, firstName, profile, householdId] of people) {
      batch.set(db.doc(`members/${id}`), { firstName, profile, householdId, parentIds: [] });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: now });
    }
    await batch.commit();
  });

  test('the family is told once, but not the newcomer nor a child without the app', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await welcomeMember(db, send, 'noe', now), 4);
    assert.deepEqual(sent.map((s) => s.token), ['token-camille', 'token-essai', 'token-ines', 'token-sansfoyer']);
    assert.equal(sent[0].notice.title, 'Noé vient d\'arriver');
    assert.equal(await welcomeMember(db, send, 'noe', now), null);
    assert.equal(sent.length, 4);
  });

  test('never for a profile of the foyer de test, nor without a foyer', async () => {
    assert.equal(await isTestMember(db, 'essai'), true);
    assert.equal(await isTestMember(db, 'noe'), false);
    assert.equal(await isTestMember(db, 'sansfoyer'), false);
    assert.equal(await isTestMember(db, 'nobody'), false);
    const { sent, send } = fakeSender();
    assert.equal(await welcomeMember(db, send, 'essai', now), null);
    assert.equal(await welcomeMember(db, send, 'sansfoyer', now), null);
    assert.equal(sent.length, 0);
    assert.equal((await db.doc('welcomes/essai').get()).exists, false);
  });

  test('at most one bienvenue every 30 min, the others told together and each newcomer of the others only', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await welcomeMember(db, send, 'camille', now), 4);
    sent.length = 0;
    const soon = new Date(now.getTime() + 10 * 60 * 1000);
    assert.equal(await welcomeMember(db, send, 'ines', soon), 0);
    assert.equal(await welcomeMember(db, send, 'noe', soon), 0);
    assert.equal(sent.length, 0);
    assert.equal(await sendQueuedWelcomes(db, send, new Date(now.getTime() + 20 * 60 * 1000)), 0);
    const later = new Date(now.getTime() + 30 * 60 * 1000);
    assert.equal(await sendQueuedWelcomes(db, send, later), 5);
    const to = (token: string) => sent.find((s) => s.token === token)?.notice.title;
    assert.equal(to('token-camille'), "Inès et Noé viennent d'arriver");
    assert.equal(to('token-essai'), "Inès et Noé viennent d'arriver");
    assert.equal(to('token-ines'), "Noé vient d'arriver");
    assert.equal(to('token-noe'), "Inès vient d'arriver");
    assert.equal(to('token-lou'), undefined);
    // Sent once; the next arrival waits again for 30 min.
    assert.equal(await sendQueuedWelcomes(db, send, later), 0);
    assert.equal(await welcomeMember(db, send, 'sansfoyer', later), null);
  });

  test('module off: nothing is sent, and the welcome stays for later', async () => {
    await db.doc('config/app').set({ features: { welcome: false } });
    const { sent, send } = fakeSender();
    assert.equal(await welcomeMember(db, send, 'noe', now), null);
    assert.equal(sent.length, 0);
    assert.equal((await db.doc('welcomes/noe').get()).exists, false);
  });

  test('a member removed takes their welcome with them', async () => {
    const { send } = fakeSender();
    await welcomeMember(db, send, 'noe', now);
    assert.equal((await db.doc('welcomes/noe').get()).exists, true);
    await deleteWelcomeOf(db, 'noe');
    await deleteWelcomeOf(db, 'noe');
    assert.equal((await db.doc('welcomes/noe').get()).exists, false);
  });
});

describe('la bienvenue grouped, without the emulator', () => {
  test('names, then « et 3 autres », and one name as before', () => {
    assert.equal(welcomeGroupNotice(['Inès', 'Noé'], ['ines', 'noe']).title, "Inès et Noé viennent d'arriver");
    assert.equal(welcomeGroupNotice(['Inès', 'Noé', 'Sam'], ['ines', 'noe', 'sam']).title, "Inès, Noé et Sam viennent d'arriver");
    const four = welcomeGroupNotice(['Inès', 'Noé', 'Sam', 'Jules'], ['ines', 'noe', 'sam', 'jules']);
    assert.equal(four.title, "Inès et 3 autres viennent d'arriver");
    assert.equal(four.body, 'Dis-leur bonjour !');
    assert.equal(four.welcomeOf, undefined);
    const one = welcomeGroupNotice(['Noé'], ['noe']);
    assert.equal(one.title, "Noé vient d'arriver");
    assert.equal(one.welcomeOf, 'noe');
  });
});
