import assert from 'node:assert/strict';
import { after, beforeEach, describe, test } from 'node:test';
import { deleteApp, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { Notice } from '../src/logic';
import { allows, burstNotices, capBursts, cappable, deliver, heldByPresentation, isPresenting, isQuietHour, morningNotices, prefsFrom, presentationNotices, presentingNow, sendBurstHeld, sendHeldNotices, sendPresentationHeld } from '../src/prefs';
import { Sender } from '../src/store';

describe('my notification settings, without the emulator', () => {
  const planning: Notice = { channel: 'events', title: 'Nouvel événement', body: 'Galette' };
  const news: Notice = { channel: 'goodNews', title: 'Bonne nouvelle', body: 'Code !' };
  const joinMe: Notice = { channel: 'joinMe', title: 'Noé : rejoins-moi', body: 'Au parc' };

  test('everything by default; « L\'essentiel » and « Presque rien » are switches set at once', () => {
    assert.equal(allows(prefsFrom(undefined), news), true);
    const essential = prefsFrom({ planning: true, reminders: true, gifts: false, goodNews: false, comments: false, helpRequests: false, trips: false, weekly: false });
    assert.equal(allows(essential, planning), true);
    assert.equal(allows(essential, { channel: 'reminders' }), true);
    assert.equal(allows(essential, news), false);
    const minimal = prefsFrom({ planning: false, reminders: false, gifts: false, goodNews: false, comments: false, helpRequests: false, trips: false, weekly: false });
    assert.equal(allows(minimal, planning), false);
    assert.equal(allows(minimal, joinMe), true);
    assert.equal(allows(minimal, { ...news, direct: true }), true);
  });

  test('a family turned off stays off at any level, and what aims at me never goes', () => {
    const p = prefsFrom({ planning: false, weekly: false, joinMe: false });
    assert.equal(allows(p, planning), false);
    assert.equal(allows(p, { channel: 'recap' }), false); // Rendez-vous de la semaine.
    assert.equal(allows(p, joinMe), true);
    assert.equal(allows(p, { channel: 'admin' }), true);
    assert.equal(allows(prefsFrom({ trips: 'non' }), { channel: 'trips' }), true);
  });

  test('the quiet hours run from 22 h to 8 h in Paris, summer and winter', () => {
    assert.equal(isQuietHour(new Date('2026-11-23T21:00:00Z')), true); // 22 h in winter.
    assert.equal(isQuietHour(new Date('2026-11-23T20:59:00Z')), false);
    assert.equal(isQuietHour(new Date('2026-11-24T06:59:00Z')), true); // 7 h 59.
    assert.equal(isQuietHour(new Date('2026-11-24T07:00:00Z')), false);
    assert.equal(isQuietHour(new Date('2027-07-01T06:30:00Z')), false); // 8 h 30 in summer.
  });

  test('the morning groups the night by channel, so a surprise keeps its own', () => {
    const gift: Notice = { channel: 'gifts', title: 'Cadeau réservé', body: '…' };
    const out = morningNotices([news, { ...news, title: 'Bonne nouvelle de Noé' }, gift]);
    assert.equal(out.length, 2);
    assert.equal(out[0].title, 'Cette nuit dans Familia');
    assert.equal(out[0].body, '2 notifications : Bonne nouvelle · Bonne nouvelle de Noé');
    assert.equal(out[0].channel, 'goodNews');
    assert.deepEqual(out[1], gift);
  });
});

describe('the mode présentation, without the emulator', () => {
  const welcome = (name: string): Notice => ({ channel: 'welcome', title: `${name} vient d'arriver`, body: 'Dis-lui bonjour !' });

  test('it holds the arrivals, the reactions, the gifts, the capsules, the coups de main, the comments and the good news; never joinMe, retours or admin', () => {
    assert.equal(heldByPresentation(welcome('Inès')), true);
    assert.equal(heldByPresentation({ channel: 'comments', reaction: true }), true);
    // Thirteen people trying the app at the same table: what they post waits for the summary.
    assert.equal(heldByPresentation({ channel: 'comments' }), true);
    assert.equal(heldByPresentation({ channel: 'helpRequests' }), true);
    assert.equal(heldByPresentation({ channel: 'helpRequests', direct: true }), true);
    assert.equal(heldByPresentation({ channel: 'goodNews' }), true);
    // The photo of the meal posted by the admin principal rings at once; nothing else is let through.
    assert.equal(heldByPresentation({ channel: 'goodNews', fromOwner: true }), false);
    assert.equal(heldByPresentation({ channel: 'gifts', fromOwner: true }), true);
    assert.equal(heldByPresentation({ channel: 'gifts', direct: true }), true);
    assert.equal(heldByPresentation({ channel: 'capsules' }), true);
    assert.equal(heldByPresentation({ channel: 'joinMe', reaction: true }), false);
    assert.equal(heldByPresentation({ channel: 'feedback' }), false);
    assert.equal(heldByPresentation({ channel: 'admin' }), false);
    assert.equal(heldByPresentation({ channel: 'events' }), false);
  });

  test('on until its end, or until turned off when it has none', () => {
    const now = new Date('2026-11-21T12:00:00Z');
    assert.equal(isPresenting({ on: true, until: null }, now), true);
    assert.equal(isPresenting({ on: true, until: new Date('2026-11-21T14:00:00Z') }, now), true);
    assert.equal(isPresenting({ on: true, until: now }, now), false);
    assert.equal(isPresenting({ on: false, until: null }, now), false);
  });

  test('the summary names who arrived, the rest in one notice, a surprise apart', () => {
    const gift: Notice = { channel: 'gifts', title: 'Cadeau réservé', body: '…' };
    const out = presentationNotices([welcome('Inès'), welcome('Sam'), welcome('Noé'), welcome('Lou'), welcome('Jules'), gift]);
    assert.equal(out.length, 2);
    assert.equal(out[0].title, '5 personnes ont rejoint Familia');
    assert.equal(out[0].body, 'Inès, Sam, Noé et 2 autres. Dis-leur bonjour !');
    assert.equal(out[0].channel, 'welcome');
    assert.deepEqual(out[1], gift);
    assert.deepEqual(presentationNotices([welcome('Inès')]), [welcome('Inès')]);
    const reactions = presentationNotices([{ channel: 'comments', title: 'Inès a réagi', body: 'a' }, { channel: 'comments', title: 'Sam a réagi', body: 'b' }]);
    assert.equal(reactions[0].title, 'Pendant la présentation');
    // Avalanche du 21: the good news, the reactions and the coups de main are one notice, not three.
    const mixed = presentationNotices([
      { channel: 'goodNews', title: 'Bonne nouvelle de Inès', body: 'a' }, { channel: 'comments', title: 'Sam a réagi', body: 'b' },
      { channel: 'helpRequests', title: 'Coup de main', body: 'c' }, { channel: 'goodNews', title: 'Bonne nouvelle de Noé', body: 'd' }, gift,
    ]);
    assert.deepEqual(mixed.map((n) => n.channel), ['goodNews', 'gifts']);
    assert.equal(mixed[0].body, '4 notifications : Bonne nouvelle de Inès · Sam a réagi · Coup de main · Bonne nouvelle de Noé');
    // A grouped bienvenue held by the mode counts each of its names.
    const grouped = presentationNotices([{ ...welcome('Inès'), title: "Inès et Sam viennent d'arriver", welcomeNames: ['Inès', 'Sam'] } as Notice, welcome('Noé')]);
    assert.equal(grouped[0].title, '3 personnes ont rejoint Familia');
  });
});

describe('the avalanche du 21, without the emulator', () => {
  test('what waited is one notice; the cards of the day keep the last, a surprise its own channel', () => {
    const out = burstNotices<Notice>([
      { channel: 'goodNews', title: 'Bonne nouvelle de Inès', body: 'a' },
      { channel: 'birthdays', title: 'Joyeux anniversaire !', body: 'Inès t\'a envoyé une carte de vœux.', tag: 'cards' },
      { channel: 'gifts', title: 'Cadeau réservé', body: '…' },
      { channel: 'goodNews', title: 'Bonne nouvelle de Noé', body: 'b' },
      { channel: 'birthdays', title: 'Joyeux anniversaire !', body: 'Inès et Noé t\'ont envoyé une carte de vœux.', tag: 'cards' },
    ]);
    assert.equal(out.length, 2);
    assert.equal(out[0].title, 'Dans Familia');
    assert.equal(out[0].channel, 'goodNews');
    assert.equal(out[0].body, '3 notifications : Bonne nouvelle de Inès · Bonne nouvelle de Noé · Joyeux anniversaire !');
    assert.equal(out[1].channel, 'gifts');
    const cards = burstNotices<Notice>([
      { channel: 'birthdays', title: 'Joyeux anniversaire !', body: 'Inès', tag: 'cards' },
      { channel: 'birthdays', title: 'Joyeux anniversaire !', body: 'Inès et Noé', tag: 'cards' },
    ]);
    assert.deepEqual(cards.map((n) => n.body), ['Inès et Noé']);
    // A turn to take is never lost in the group: it comes first and the group says so.
    const turn = burstNotices<Notice>([
      { channel: 'goodNews', title: 'Bonne nouvelle de Inès', body: 'a' },
      { channel: 'helpRequests', title: "C'est ton tour", body: 'Poubelles', direct: true, turnId: 't1' },
    ]);
    assert.equal(turn[0].body, "Une demande attend ta réponse. 2 notifications : C'est ton tour · Bonne nouvelle de Inès");
    // And it stays aimed at me, so the night does not hold it until 8 h (note C de #295).
    assert.equal(turn[0].direct, true);
    assert.equal(burstNotices<Notice>([{ channel: 'goodNews', title: 'a', body: 'a' }, { channel: 'goodNews', title: 'b', body: 'b' }])[0].direct, undefined);
    assert.equal(cappable({ channel: 'goodNews' }), true);
    assert.equal(cappable({ channel: 'birthdays', direct: true }), true);
    for (const never of [{ channel: 'joinMe' }, { channel: 'reminders', direct: true }, { channel: 'feedback' }, { channel: 'admin' }, { channel: 'goodNews', fromOwner: true }] as const) {
      assert.equal(cappable(never), false);
    }
  });
});

describe('my notification settings against the emulator', () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, 'Start these tests with the emulator: npm run test:functions');
  const app = initializeApp({ projectId: 'demo-familia' }, 'prefs-test');
  const db = getFirestore(app);
  after(() => deleteApp(app));
  const night = new Date('2026-11-23T22:30:00Z'); // 23 h 30 in Paris.
  const morning = new Date('2026-11-24T07:00:00Z'); // 8 h.

  function fakeSender() {
    const sent: { token: string; notice: Notice }[] = [];
    const send: Sender = async (token, notice) => {
      sent.push({ token, notice });
      return 'ok';
    };
    return { sent, send };
  }

  beforeEach(async () => {
    for (const c of ['members', 'quietQueue', 'presentationQueue', 'config', 'burstQueue', 'notifyLog']) await db.recursiveDelete(db.collection(c));
    const batch = db.batch();
    for (const id of ['camille', 'noe', 'lou']) {
      batch.set(db.doc(`members/${id}`), { firstName: id, profile: 'adult', parentIds: [] });
      batch.set(db.doc(`members/${id}/devices/token-${id}`), { updatedAt: night });
    }
    batch.set(db.doc('members/noe/settings/notifications'), { planning: false, reminders: false, gifts: false, goodNews: false, comments: false, helpRequests: false, trips: false, weekly: false });
    batch.set(db.doc('members/lou/settings/notifications'), { quietHours: true });
    await batch.commit();
  });

  const news: Notice = { channel: 'goodNews', title: 'Bonne nouvelle', body: 'Code !' };

  test('a muted notice is not sent at all; the night is held for 8 h, then sent once', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await deliver(db, send, ['camille', 'noe', 'lou'], news, night), 1);
    assert.deepEqual(sent.map((s) => s.token), ['token-camille']);
    await deliver(db, send, ['lou'], { ...news, title: 'Bonne nouvelle de Noé' }, night);
    assert.equal((await db.doc('quietQueue/lou').get()).get('items').length, 2);

    assert.equal(await sendHeldNotices(db, send, night), 0); // Still the night.
    assert.equal(await sendHeldNotices(db, send, morning), 1);
    assert.equal(sent[1].token, 'token-lou');
    assert.equal(sent[1].notice.body, '2 notifications : Bonne nouvelle · Bonne nouvelle de Noé');
    assert.equal((await db.doc('quietQueue/lou').get()).exists, false);
    assert.equal(await sendHeldNotices(db, send, morning), 0);
  });

  test('a morning send that reaches no phone is kept for the next run, for a day at most', async () => {
    await deliver(db, fakeSender().send, ['lou'], news, night);
    let down = true;
    const sent: string[] = [];
    const flaky: Sender = async (token) => {
      if (down) return 'error';
      sent.push(token);
      return 'ok';
    };
    assert.equal(await sendHeldNotices(db, flaky, morning), 0);
    assert.equal((await db.doc('quietQueue/lou').get()).get('items').length, 1);
    down = false;
    assert.equal(await sendHeldNotices(db, flaky, new Date('2026-11-24T07:30:00Z')), 1);
    assert.deepEqual(sent, ['token-lou']);
    assert.equal((await db.doc('quietQueue/lou').get()).exists, false);

    await deliver(db, fakeSender().send, ['lou'], news, night);
    down = true;
    assert.equal(await sendHeldNotices(db, flaky, new Date('2026-11-25T08:00:00Z')), 0); // More than a day later.
    assert.equal((await db.doc('quietQueue/lou').get()).exists, false);
  });

  test('at night, what aims at me is not held', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await deliver(db, send, ['lou'], { ...news, direct: true } as Notice, night), 1);
    assert.equal(await deliver(db, send, ['lou'], { channel: 'joinMe', title: 'Rejoins-moi', body: 'Au parc' }, night), 1);
    assert.equal(sent.length, 2);
    assert.equal((await db.doc('quietQueue/lou').get()).exists, false);
  });

  test('in the day, the quiet hours change nothing, and what aims at me reaches « Presque rien »', async () => {
    const { sent, send } = fakeSender();
    assert.equal(await deliver(db, send, ['noe', 'lou'], { ...news, direct: true } as Notice, morning), 2);
    assert.deepEqual(sent.map((s) => s.token).sort(), ['token-lou', 'token-noe']);
  });
  test('the mode présentation holds the arrivals, lets the rest go, then sends one summary', async () => {
    const { sent, send } = fakeSender();
    const noon = new Date('2026-11-21T11:00:00Z');
    await db.doc('config/presentation').set({ on: true, until: null });
    const welcome = (name: string): Notice => ({ channel: 'welcome', title: `${name} vient d'arriver`, body: 'Dis-lui bonjour !' });
    assert.equal(await deliver(db, send, ['camille', 'lou'], welcome('Inès'), noon), 0);
    assert.equal(await deliver(db, send, ['camille', 'lou'], welcome('Sam'), noon), 0);
    assert.equal(await deliver(db, send, ['camille'], { channel: 'joinMe', title: 'Rejoins-moi', body: 'Au parc' }, noon), 1);
    assert.equal(await deliver(db, send, ['camille'], { channel: 'events', title: 'Nouvel événement', body: 'Galette' }, noon), 1);
    assert.equal((await db.doc('presentationQueue/camille').get()).get('items').length, 2);
    assert.equal(sent.length, 2);

    await db.doc('config/presentation').set({ on: false }, { merge: true });
    assert.equal(await sendPresentationHeld(db, send, noon), 2);
    assert.equal(sent[2].notice.title, '2 personnes ont rejoint Familia');
    assert.equal(sent[2].notice.body, 'Inès et Sam. Dis-leur bonjour !');
    assert.equal((await db.doc('presentationQueue/camille').get()).exists, false);
    assert.equal(await sendPresentationHeld(db, send, noon), 0);
  });

  test('the end of a mode présentation turns it off at the next send, and the night keeps its summary', async () => {
    const { sent, send } = fakeSender();
    await db.doc('config/presentation').set({ on: true, until: new Date('2026-11-23T22:00:00Z') });
    const welcome: Notice = { channel: 'welcome', title: "Inès vient d'arriver", body: 'Dis-lui bonjour !' };
    assert.equal(await deliver(db, send, ['lou'], welcome, new Date('2026-11-23T21:30:00Z')), 0);
    assert.equal(await presentingNow(db, night), false);
    assert.equal((await db.doc('config/presentation').get()).get('on'), false);
    // Lou has the quiet hours: at 23 h 30 the summary waits for 8 h.
    assert.equal(await sendPresentationHeld(db, send, night), 0);
    assert.equal(sent.length, 0);
    assert.equal((await db.doc('quietQueue/lou').get()).get('items').length, 1);
  });
  test('a summary of the mode présentation that reaches no phone waits in the morning queue', async () => {
    await db.doc('config/presentation').set({ on: true, until: null });
    const noon = new Date('2026-11-21T11:00:00Z');
    await deliver(db, fakeSender().send, ['camille'], { channel: 'welcome', title: "Inès vient d'arriver", body: 'Dis-lui bonjour !' }, noon);
    await db.doc('config/presentation').set({ on: false }, { merge: true });
    const down: Sender = async () => 'error';
    assert.equal(await sendPresentationHeld(db, down, noon), 0);
    assert.equal((await db.doc('presentationQueue/camille').get()).exists, false);
    assert.equal((await db.doc('quietQueue/camille').get()).get('items').length, 1);
    const { sent, send } = fakeSender();
    assert.equal(await sendHeldNotices(db, send, new Date('2026-11-21T11:30:00Z')), 1);
    assert.equal(sent[0].notice.title, "Inès vient d'arriver");
  });
  test('no phone rings more than 3 times an hour: the rest goes out grouped, never « Rejoins-moi »', async () => {
    capBursts(true);
    try {
      const { sent, send } = fakeSender();
      const noon = new Date('2026-11-21T11:00:00Z');
      for (const name of ['Inès', 'Noé', 'Sam', 'Jules', 'Lou']) {
        await deliver(db, send, ['camille'], { ...news, title: `Bonne nouvelle de ${name}` }, noon);
      }
      assert.equal(sent.length, 3);
      assert.equal((await db.doc('burstQueue/camille').get()).get('items').length, 2);
      assert.equal(await deliver(db, send, ['camille'], { channel: 'joinMe', title: 'Noé : rejoins-moi', body: 'Au parc' } as Notice, noon), 1);
      // Still 3 in the hour: the grouped notice waits.
      assert.equal(await sendBurstHeld(db, send, new Date('2026-11-21T11:30:00Z')), 0);
      assert.equal(await sendBurstHeld(db, send, new Date('2026-11-21T12:01:00Z')), 1);
      assert.equal(sent[4].notice.title, 'Dans Familia');
      assert.equal(sent[4].notice.body, '2 notifications : Bonne nouvelle de Jules · Bonne nouvelle de Lou');
      assert.equal((await db.doc('burstQueue/camille').get()).exists, false);
      // The grouped notice took a place: two more go at once, the third waits.
      for (const name of ['A', 'B', 'C']) await deliver(db, send, ['camille'], { ...news, title: name }, new Date('2026-11-21T12:02:00Z'));
      assert.equal(sent.length, 7);
    } finally {
      capBursts(false);
    }
  });

  test('what waited past the cap waits for the summary during the mode présentation', async () => {
    capBursts(true);
    try {
      const { sent, send } = fakeSender();
      const noon = new Date('2026-11-21T11:00:00Z');
      const planning: Notice = { channel: 'events', title: 'Nouvel événement', body: 'Galette' };
      for (let i = 0; i < 4; i++) await deliver(db, send, ['camille'], { ...planning, title: `É${i}` }, noon);
      for (let i = 0; i < 2; i++) await deliver(db, send, ['camille'], { ...news, title: `N${i}` }, noon);
      assert.equal(sent.length, 3);
      await db.doc('config/presentation').set({ on: true, until: null });
      assert.equal(await sendBurstHeld(db, send, new Date('2026-11-21T12:01:00Z')), 0);
      assert.equal((await db.doc('presentationQueue/camille').get()).get('items')[0].title, 'Dans Familia');
    } finally {
      capBursts(false);
    }
  });
});
