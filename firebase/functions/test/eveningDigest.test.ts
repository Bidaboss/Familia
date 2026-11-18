import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { sendDigests } from '../src/digest';
import { Notice } from '../src/logic';
import { deliver, deleteQuietQueueOf, eveningKind, isEveningWait, prefsFrom, sendEveningHeld } from '../src/prefs';
import { Sender } from '../src/store';

// Résumé du soir des notifications (récit 70, DESIGN-31).

const comment: Notice = { channel: 'comments', title: 'Sam a commenté « Repas »', body: 'Super' };
const reaction: Notice = { channel: 'comments', title: 'Inès a réagi à ta Bonne nouvelle', body: 'Code !', reaction: true } as Notice;
const question: Notice = { channel: 'weekly', title: 'La question de la semaine', body: 'Ton plus beau voyage ?' };
const souvenir: Notice = { channel: 'souvenirs', title: 'Nouveau souvenir', body: 'Il t\'attend sur ta fiche.' };
const event: Notice = { channel: 'events', title: 'Nouvel événement', body: 'Galette' };
const news: Notice = { channel: 'goodNews', title: 'Bonne nouvelle', body: 'Code !' };
const help: Notice = { channel: 'helpRequests', title: 'Un coup de main', body: 'Déménagement' };
const joinMe: Notice = { channel: 'joinMe', title: 'Noé : rejoins-moi', body: 'Au parc' };

describe('the résumé du soir, without the emulator', () => {
  test('the small news wait; the planning, the coups de main, the good news and what aims at me never', () => {
    assert.equal(eveningKind(reaction), 'reactions');
    assert.equal(eveningKind(comment), 'comments');
    assert.equal(eveningKind(question), 'weekly');
    assert.equal(eveningKind(souvenir), 'souvenirs');
    assert.equal(eveningKind({ channel: 'thinkOfMe' }), 'souvenirs');
    assert.equal(eveningKind({ channel: 'recap' }), 'souvenirs');
    for (const n of [event, news, help, joinMe, { channel: 'reminders' }, { channel: 'admin' }, { channel: 'capsules' }, { channel: 'feedback' }] as Notice[]) {
      assert.equal(eveningKind(n), null, n.channel);
    }
    assert.equal(eveningKind({ ...comment, direct: true } as Notice), null);
  });

  test('every kind waits by default; false keeps one immediate', () => {
    assert.deepEqual([...prefsFrom(undefined).evening].sort(), ['comments', 'reactions', 'souvenirs', 'weekly']);
    assert.deepEqual([...prefsFrom({ eveningComments: false, eveningWeekly: 'non' }).evening].sort(), ['reactions', 'souvenirs', 'weekly']);
  });

  test('the wait runs from 8 h to 19 h in Paris, summer and winter', () => {
    assert.equal(isEveningWait(new Date('2026-11-23T06:59:00Z')), false); // 7 h 59.
    assert.equal(isEveningWait(new Date('2026-11-23T07:00:00Z')), true); // 8 h.
    assert.equal(isEveningWait(new Date('2026-11-23T17:59:00Z')), true); // 18 h 59.
    assert.equal(isEveningWait(new Date('2026-11-23T18:00:00Z')), false); // 19 h.
    assert.equal(isEveningWait(new Date('2027-07-01T16:30:00Z')), true); // 18 h 30 in summer.
    assert.equal(isEveningWait(new Date('2027-07-01T17:00:00Z')), false); // 19 h in summer.
  });
});

describe('the résumé du soir against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'evening-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const day = new Date('2026-11-23T13:00:00Z'); // 14 h in Paris.
  const beforeSeven = new Date('2026-11-23T17:30:00Z'); // 18 h 30.
  const seven = new Date('2026-11-23T18:00:00Z'); // 19 h.
  const night = new Date('2026-11-23T22:30:00Z'); // 23 h 30.

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  async function turn(on: boolean) {
    await db.doc('config/app').set({ features: { eveningDigest: on } }, { merge: true });
  }

  beforeEach(async () => {
    for (const c of ['members', 'quietQueue', 'eveningQueue', 'presentationQueue', 'config', 'burstQueue', 'notifyLog', 'engagementDigests']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    for (const id of ['camille', 'lou']) {
      batch.set(db.doc(`members/${id}`), { firstName: id, profile: 'adult', parentIds: [] });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: day });
    }
    // Camille wants the comments at once and sleeps without « Calme la nuit »; Lou keeps the defaults.
    batch.set(db.doc('members/camille/settings/notifications'), { eveningComments: false, quietHours: false });
    await batch.commit();
  });

  test('switched off, nothing changes: everything goes at once', async () => {
    const { sent, send } = fakeSender();
    for (const n of [comment, reaction, question, souvenir]) assert.equal(await deliver(db, send, ['lou'], n, day), 1);
    assert.equal(sent.length, 4);
    assert.equal((await db.doc('eveningQueue/lou').get()).exists, false);
    assert.equal(await sendEveningHeld(db, send, seven), 0);
  });

  test('switched on, the small news of the day go out at 19 h as one notice, the rest at once', async () => {
    await turn(true);
    const { sent, send } = fakeSender();
    for (const n of [comment, reaction, question, souvenir]) assert.equal(await deliver(db, send, ['lou'], n, day), 0);
    for (const n of [event, news, help, joinMe, { ...comment, direct: true } as Notice]) assert.equal(await deliver(db, send, ['lou'], n, day), 1);
    assert.equal(sent.length, 5);
    assert.equal((await db.doc('eveningQueue/lou').get()).get('items').length, 4);

    assert.equal(await sendEveningHeld(db, send, beforeSeven), 0);
    assert.equal(await sendEveningHeld(db, send, seven), 1);
    assert.equal(sent[5].token, 'token-lou');
    assert.equal(sent[5].notice.title, 'Ce soir dans Familia');
    assert.match(sent[5].notice.body, /^4 notifications : /);
    assert.equal((await db.doc('eveningQueue/lou').get()).exists, false);
    assert.equal(await sendEveningHeld(db, send, new Date('2026-11-23T18:30:00Z')), 0); // Sent once.

    // After 19 h, a small news goes at once.
    assert.equal(await deliver(db, send, ['lou'], comment, new Date('2026-11-23T19:00:00Z')), 1);
  });

  test('each one may keep a kind immediate', async () => {
    await turn(true);
    const { sent, send } = fakeSender();
    assert.equal(await deliver(db, send, ['camille', 'lou'], comment, day), 1);
    assert.deepEqual(sent.map((s) => s.token), ['token-camille']);
    assert.equal(await deliver(db, send, ['camille'], reaction, day), 0);
    assert.equal((await db.doc('eveningQueue/camille').get()).get('items').length, 1);
  });

  test('« Calme la nuit » comes first, and what is left at night waits for 8 h', async () => {
    await turn(true);
    const { sent, send } = fakeSender();
    assert.equal(await deliver(db, send, ['lou'], comment, night), 0);
    assert.equal((await db.doc('quietQueue/lou').get()).get('items').length, 1);
    assert.equal((await db.doc('eveningQueue/lou').get()).exists, false);

    // A résumé that could not leave at 19 h: at night it joins the morning for Lou, rings for Camille.
    await deliver(db, send, ['camille', 'lou'], reaction, day);
    assert.equal(await sendEveningHeld(db, send, night), 1);
    assert.deepEqual(sent.map((s) => s.token), ['token-camille']);
    assert.equal((await db.doc('quietQueue/lou').get()).get('items').length, 2);
    assert.equal((await db.doc('eveningQueue/lou').get()).exists, false);
  });

  test('a résumé that reaches no phone is tried again, and leaves with the member', async () => {
    await turn(true);
    await deliver(db, fakeSender().send, ['lou'], comment, day);
    const down: Sender = async () => 'error';
    assert.equal(await sendEveningHeld(db, down, seven), 0);
    assert.equal((await db.doc('eveningQueue/lou').get()).get('items').length, 1);
    const { sent, send } = fakeSender();
    assert.equal(await sendEveningHeld(db, send, new Date('2026-11-23T18:30:00Z')), 1);
    assert.equal(sent.length, 1);

    await deliver(db, send, ['lou'], comment, day);
    await deleteQuietQueueOf(db, 'lou');
    assert.equal((await db.doc('eveningQueue/lou').get()).exists, false);
  });

  test('the résumé of the day waits for 19 h too', async () => {
    const { sent, send } = fakeSender();
    const waiting = { pending: true, souvenirs: ['premier-pas'], challengeDone: null, weekly: null, albums: [] };
    await db.doc('engagementDigests/lou').set(waiting);
    await turn(true);
    assert.equal(await sendDigests(db, send, beforeSeven), 0);
    assert.equal(await sendDigests(db, send, seven), 1);
    assert.equal(sent[0].notice.title, 'Aujourd\'hui dans Familia');

    await turn(false);
    await db.doc('engagementDigests/camille').set(waiting);
    assert.equal(await sendDigests(db, send, beforeSeven), 1); // Off: from 18 h, as before.
  });
});
